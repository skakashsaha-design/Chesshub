package com.example.analysis

import com.example.chess.BoardState
import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.Move
import com.example.chess.OpeningBook
import com.example.chess.PieceColor
import com.example.chess.RecordedMove
import com.example.database.AppSettingsEntity
import com.example.engine.DeviceMemoryAdvisor
import com.example.engine.EngineAnalysisOutput
import com.example.engine.MemoryProfile
import com.example.engine.SearchBudget
import kotlin.math.abs
import kotlinx.coroutines.yield

/**
 * Coordinates engine-backed position snapshots, move-by-move grading, and full Game Review
 * while strictly skipping disabled calculations according to the 13 Settings toggles.
 */
object AnalysisCoordinator {

  suspend fun buildPositionSnapshot(
    state: BoardState,
    sanMovesSoFar: List<String>,
    settings: AppSettingsEntity,
    memoryProfile: MemoryProfile,
    requestedDepth: Int = 3,
    requestedTimeMs: Long = 380L,
    runDetailed: suspend (BoardState, SearchBudget, Boolean, Boolean, Boolean) -> EngineAnalysisOutput,
  ): Pair<PositionAnalysisSnapshot, EngineAnalysisOutput?> {
    val openingInfo = if (settings.showOpeningInfo) OpeningBook.recognize(sanMovesSoFar) else null
    val continuations = if (settings.showOpeningInfo) OpeningBook.exploreContinuations(sanMovesSoFar) else emptyList()
    val endgameInfo = MoveFeedbackAnalyzer.analyzeEndgame(state)

    if (!AnalysisPolicy.shouldRunLiveEngine(settings)) {
      return PositionAnalysisSnapshot(
        openingInfo = openingInfo,
        openingContinuations = continuations,
        endgameInfo = endgameInfo,
        isAnalyzing = false,
      ) to null
    }

    val budget = DeviceMemoryAdvisor.computeSearchBudget(
      requestedDepth = requestedDepth,
      requestedTimeMs = requestedTimeMs,
      lowMemoryMode = settings.lowMemoryMode,
      batterySavingMode = settings.batterySavingMode,
      isLowRamHardware = memoryProfile.isLowRamHardware,
    )

    val needPv = AnalysisPolicy.needPv(settings)
    val needCandidates = AnalysisPolicy.needCandidates(settings)
    val needThreat = AnalysisPolicy.needThreat(settings)

    val out = runDetailed(state, budget, needPv, needCandidates, needThreat)
    val sign = if (state.turn == PieceColor.WHITE) 1 else -1
    val whiteCp = out.scoreCpForSide * sign
    val mateWhite = out.mateInForSide?.let { it * sign }

    val bestMoveSan = out.bestMove?.let { ChessNotation.toSan(state, it) }
    val pvSan = if (needPv) pvMovesToSanList(state, out.pvMoves) else emptyList()
    val candidates = if (needCandidates) {
      val legal = ChessRules.legalMoves(state)
      out.candidates.map { c ->
        CandidateMoveInfo(
          move = c.move,
          san = ChessNotation.toSan(state, c.move, legal),
          uci = c.move.toUci(),
          evalCpForWhite = c.scoreCpForSide * sign,
          mateInForWhite = c.mateInForSide?.let { it * sign },
          pvSanList = if (needPv) pvMovesToSanList(state, c.pvMoves) else emptyList(),
        )
      }
    } else emptyList()

    val threatInfo = if (needThreat && out.threatMove != null) {
      val oppState = state.copy(turn = state.turn.opposite(), enPassantSq = -1)
      val threatSan = ChessNotation.toSan(oppState, out.threatMove)
      val exp = when {
        ChessRules.isInCheck(ChessRules.applyMove(oppState, out.threatMove)) ->
          "Opponent threatens a forcing check ($threatSan)"
        out.threatMove.capturedPiece != 0 ->
          "Opponent threatens to capture material with $threatSan"
        else ->
          "Opponent threatens an immediate initiative gain with $threatSan"
      }
      ThreatInfo(
        move = out.threatMove,
        san = threatSan,
        explanation = exp,
        evalSwingCp = out.threatSwingCp,
      )
    } else null

    val tacticalTips = if (settings.showTacticalSuggestions) {
      MoveFeedbackAnalyzer.detectTacticalSuggestions(state, threatInfo, out.bestMove)
    } else emptyList()

    val firstTwoGap = if (candidates.size >= 2) {
      abs(candidates[0].evalCpForWhite - candidates[1].evalCpForWhite)
    } else 0
    val isCriticalPos = settings.enableCriticalMoments &&
      (firstTwoGap >= 140 || (threatInfo != null && threatInfo.evalSwingCp >= 160))
    val criticalReason = if (isCriticalPos) {
      when {
        threatInfo != null && threatInfo.evalSwingCp >= 160 ->
          "Critical defense needed against ${threatInfo.san}"
        firstTwoGap >= 140 && bestMoveSan != null ->
          "Sharp turning point — $bestMoveSan stands far above alternatives"
        else -> "High tactical tension"
      }
    } else null

    return PositionAnalysisSnapshot(
      evalCpForWhite = whiteCp,
      mateInForWhite = mateWhite,
      bestMove = out.bestMove,
      bestMoveSan = bestMoveSan,
      threatInfo = threatInfo,
      pvSanList = pvSan,
      candidateMoves = candidates,
      tacticalSuggestions = tacticalTips,
      endgameInfo = endgameInfo,
      openingInfo = openingInfo,
      openingContinuations = continuations,
      isCriticalPosition = isCriticalPos,
      criticalReason = criticalReason,
      isAnalyzing = false,
      depthReached = out.depthReached,
    ) to out
  }

  suspend fun reviewFullGame(
    historyStates: List<BoardState>,
    moves: List<RecordedMove>,
    settings: AppSettingsEntity,
    memoryProfile: MemoryProfile,
    runDetailed: suspend (BoardState, SearchBudget, Boolean, Boolean, Boolean) -> EngineAnalysisOutput,
    onProgress: (GameReviewSummary) -> Unit,
  ): GameReviewSummary {
    if (!settings.enableGameAnalysis || moves.isEmpty() || historyStates.size <= moves.size) {
      return GameReviewSummary(
        summaryNote = if (!settings.enableGameAnalysis) "Game Analysis is turned OFF in Settings." else "No moves to review.",
        isRunning = false,
        progressFraction = 1f,
      )
    }

    val reviewDepth = if (settings.lowMemoryMode || memoryProfile.isLowRamHardware) 2 else 3
    val reviewTimeMs = if (settings.lowMemoryMode || memoryProfile.isLowRamHardware) 110L else 190L
    val budget = DeviceMemoryAdvisor.computeSearchBudget(
      requestedDepth = reviewDepth,
      requestedTimeMs = reviewTimeMs,
      lowMemoryMode = settings.lowMemoryMode,
      batterySavingMode = settings.batterySavingMode,
      isLowRamHardware = memoryProfile.isLowRamHardware,
    )

    val entries = ArrayList<MoveAnalysisEntry>(moves.size)
    val evalSeries = ArrayList<Int>(moves.size + 1)
    val criticalIndices = ArrayList<Int>()
    val sanPrefix = ArrayList<String>(moves.size)

    var cachedBeforeOut = runDetailed(historyStates[0], budget, false, true, false)
    val startSign = if (historyStates[0].turn == PieceColor.WHITE) 1 else -1
    evalSeries.add(cachedBeforeOut.scoreCpForSide * startSign)

    for (idx in moves.indices) {
      yield()
      val stateBefore = historyStates[idx]
      val rm = moves[idx]
      val stateAfter = historyStates[idx + 1]
      sanPrefix.add(rm.san)

      val signBefore = if (stateBefore.turn == PieceColor.WHITE) 1 else -1
      val evalBeforeWhite = cachedBeforeOut.scoreCpForSide * signBefore
      val bestMoveBefore = cachedBeforeOut.bestMove
      val bestSan = bestMoveBefore?.let { ChessNotation.toSan(stateBefore, it) }
      val secondBestForMover = cachedBeforeOut.candidates.getOrNull(1)?.scoreCpForSide

      val afterOut = runDetailed(stateAfter, budget, settings.showEngineLine, true, false)
      val signAfter = if (stateAfter.turn == PieceColor.WHITE) 1 else -1
      val evalAfterWhite = afterOut.scoreCpForSide * signAfter
      val mateAfterWhite = afterOut.mateInForSide?.let { it * signAfter }
      val isBook = OpeningBook.isKnownBookPrefix(sanPrefix)
      val pvSan = if (settings.showEngineLine) pvMovesToSanList(stateAfter, afterOut.pvMoves) else emptyList()

      val entry = MoveFeedbackAnalyzer.evaluateMoveFeedback(
        plyIndex = idx + 1,
        stateBefore = stateBefore,
        move = rm.move,
        san = rm.san,
        stateAfter = stateAfter,
        evalBeforeCpForWhite = evalBeforeWhite,
        evalAfterCpForWhite = evalAfterWhite,
        mateInAfterForWhite = mateAfterWhite,
        bestMoveInBefore = bestMoveBefore,
        bestMoveSan = bestSan,
        bestEvalBeforeForMoverCp = cachedBeforeOut.scoreCpForSide,
        secondBestEvalBeforeForMoverCp = secondBestForMover,
        isBookMove = isBook,
        pvSanList = pvSan,
        enableCriticalMoments = settings.enableCriticalMoments,
      )

      entries.add(entry)
      evalSeries.add(evalAfterWhite)
      if (entry.isCriticalMoment) criticalIndices.add(idx + 1)

      cachedBeforeOut = afterOut

      if ((idx + 1) % 3 == 0 || idx == moves.lastIndex) {
        onProgress(
          buildSummary(
            entries = entries.toList(),
            evalSeries = evalSeries.toList(),
            criticalIndices = criticalIndices.toList(),
            isRunning = idx < moves.lastIndex,
            progress = (idx + 1).toFloat() / moves.size.toFloat(),
          )
        )
      }
    }

    return buildSummary(
      entries = entries,
      evalSeries = evalSeries,
      criticalIndices = criticalIndices,
      isRunning = false,
      progress = 1f,
    )
  }

  private fun buildSummary(
    entries: List<MoveAnalysisEntry>,
    evalSeries: List<Int>,
    criticalIndices: List<Int>,
    isRunning: Boolean,
    progress: Float,
  ): GameReviewSummary {
    val whiteMoves = entries.filter { it.mover == PieceColor.WHITE }
    val blackMoves = entries.filter { it.mover == PieceColor.BLACK }
    val whiteAcc = if (whiteMoves.isEmpty()) 100.0 else whiteMoves.map { it.moveAccuracyPercent }.average()
    val blackAcc = if (blackMoves.isEmpty()) 100.0 else blackMoves.map { it.moveAccuracyPercent }.average()

    val blunders = entries.count { it.classification == MoveClassification.BLUNDER }
    val mistakes = entries.count { it.classification == MoveClassification.MISTAKE }
    val brilliants = entries.count { it.classification == MoveClassification.BRILLIANT }
    val note = buildString {
      append("White %.1f%% • Black %.1f%% accuracy".format(whiteAcc, blackAcc))
      if (brilliants > 0) append(" • $brilliants Brilliant")
      if (mistakes > 0) append(" • $mistakes Mistake(s)")
      if (blunders > 0) append(" • $blunders Blunder(s)")
    }

    return GameReviewSummary(
      moveEntries = entries,
      whiteAccuracyPercent = whiteAcc,
      blackAccuracyPercent = blackAcc,
      evalSeriesForWhite = evalSeries,
      criticalPlyIndices = criticalIndices,
      summaryNote = note,
      isRunning = isRunning,
      progressFraction = progress,
    )
  }

  fun pvMovesToSanList(startState: BoardState, pvMoves: List<Move>): List<String> {
    if (pvMoves.isEmpty()) return emptyList()
    val result = ArrayList<String>(pvMoves.size)
    var cur = startState
    for (m in pvMoves) {
      val legal = ChessRules.legalMoves(cur)
      val matched = legal.firstOrNull { it.from == m.from && it.to == m.to && it.promotion == m.promotion }
        ?: legal.firstOrNull { it.from == m.from && it.to == m.to }
        ?: break
      result.add(ChessNotation.toSan(cur, matched, legal))
      cur = ChessRules.applyMove(cur, matched)
    }
    return result
  }
}
