package com.example.analysis

import com.example.chess.BoardState
import com.example.chess.Move
import com.example.chess.OpeningContinuation
import com.example.chess.OpeningInfo
import com.example.chess.PieceColor
import com.example.database.AppSettingsEntity

enum class MoveClassification(
  val label: String,
  val symbol: String,
  val badgeColorHex: Long,
  val isMistakeOrWorse: Boolean,
) {
  BRILLIANT("Brilliant", "!!", 0xFF06B6D4, false),
  BEST("Best", "★", 0xFF16A34A, false),
  EXCELLENT("Excellent", "!", 0xFF22C55E, false),
  GOOD("Good", "✓", 0xFF65A30D, false),
  BOOK("Book", "📖", 0xFFA16207, false),
  INACCURACY("Inaccuracy", "?!", 0xFFEAB308, true),
  MISTAKE("Mistake", "?", 0xFFF97316, true),
  MISSED_OPPORTUNITY("Missed Opportunity", "⊘", 0xFFEA580C, true),
  BLUNDER("Blunder", "??", 0xFFDC2626, true),
}

data class CandidateMoveInfo(
  val move: Move,
  val san: String,
  val uci: String,
  val evalCpForWhite: Int,
  val mateInForWhite: Int? = null,
  val pvSanList: List<String> = emptyList(),
)

data class ThreatInfo(
  val move: Move,
  val san: String,
  val explanation: String,
  val evalSwingCp: Int,
)

data class EndgameInfo(
  val isEndgame: Boolean,
  val category: String,
  val summary: String,
  val advice: String,
)

data class MoveAnalysisEntry(
  val plyIndex: Int,
  val san: String,
  val uci: String,
  val move: Move,
  val mover: PieceColor,
  val evalBeforeCpForWhite: Int,
  val evalAfterCpForWhite: Int,
  val evalDeltaForMoverCp: Int,
  val mateInAfter: Int? = null,
  val classification: MoveClassification,
  val bestMoveSan: String? = null,
  val bestMoveUci: String? = null,
  val isCriticalMoment: Boolean = false,
  val explanation: String,
  val pvSanList: List<String> = emptyList(),
  val fenAfter: String = "",
  val moveAccuracyPercent: Double = 100.0,
)

data class PositionAnalysisSnapshot(
  val evalCpForWhite: Int = 0,
  val mateInForWhite: Int? = null,
  val bestMove: Move? = null,
  val bestMoveSan: String? = null,
  val threatInfo: ThreatInfo? = null,
  val pvSanList: List<String> = emptyList(),
  val candidateMoves: List<CandidateMoveInfo> = emptyList(),
  val tacticalSuggestions: List<String> = emptyList(),
  val endgameInfo: EndgameInfo? = null,
  val openingInfo: OpeningInfo? = null,
  val openingContinuations: List<OpeningContinuation> = emptyList(),
  val isCriticalPosition: Boolean = false,
  val criticalReason: String? = null,
  val isAnalyzing: Boolean = false,
  val depthReached: Int = 0,
)

data class SideFeedbackCounts(
  val totalMoves: Int = 0,
  val brilliant: Int = 0,
  val best: Int = 0,
  val excellent: Int = 0,
  val good: Int = 0,
  val book: Int = 0,
  val inaccuracy: Int = 0,
  val mistake: Int = 0,
  val missedOpportunity: Int = 0,
  val blunder: Int = 0,
  val averageCpLoss: Int = 0,
) {
  val accuracyAndSolidCount: Int get() = brilliant + best + excellent + good + book
  val errorsCount: Int get() = inaccuracy + mistake + missedOpportunity + blunder
}

data class GameReviewSummary(
  val moveEntries: List<MoveAnalysisEntry> = emptyList(),
  val whiteAccuracyPercent: Double = 0.0,
  val blackAccuracyPercent: Double = 0.0,
  val whiteCounts: SideFeedbackCounts = SideFeedbackCounts(),
  val blackCounts: SideFeedbackCounts = SideFeedbackCounts(),
  val whiteOpeningAcc: Double? = null,
  val whiteMiddlegameAcc: Double? = null,
  val whiteEndgameAcc: Double? = null,
  val blackOpeningAcc: Double? = null,
  val blackMiddlegameAcc: Double? = null,
  val blackEndgameAcc: Double? = null,
  val evalSeriesForWhite: List<Int> = emptyList(),
  val criticalPlyIndices: List<Int> = emptyList(),
  val summaryNote: String = "",
  val isRunning: Boolean = false,
  val progressFraction: Float = 0f,
)

data class TrainerDrill(
  val id: String,
  val title: String,
  val category: String,
  val difficulty: String,
  val fen: String,
  val goal: String,
)

data class BookPracticeSession(
  val repertoire: com.example.chess.BookRepertoire,
  val stepIndex: Int = 0,
  val completed: Boolean = false,
  val mistakesCount: Int = 0,
  val statusMessage: String = "",
)

data class AnalysisSessionState(
  val boardState: BoardState,
  val historyStates: List<BoardState>,
  val historyMoves: List<com.example.chess.RecordedMove>,
  val currentPly: Int = 0,
  val selectedSquare: Int? = null,
  val legalMoves: List<Move> = emptyList(),
  val isFlipped: Boolean = false,
  val snapshot: PositionAnalysisSnapshot = PositionAnalysisSnapshot(),
  val lastMoveFeedback: MoveAnalysisEntry? = null,
  val reviewSummary: GameReviewSummary = GameReviewSummary(),
  val practiceActive: Boolean = false,
  val practicePlayerColor: PieceColor = PieceColor.WHITE,
  val practiceCoachPrompt: String? = null,
  val practiceRetryCount: Int = 0,
  val activeDrill: TrainerDrill? = null,
  val bookPractice: BookPracticeSession? = null,
  val analysisDepthMode: String = "Balanced", // Fast, Balanced, Deep
)

/**
 * Central policy translating the 13 individual ON/OFF Settings toggles into exact
 * computation and visibility rules so disabled features perform zero engine work.
 */
object AnalysisPolicy {

  fun shouldRunLiveEngine(settings: AppSettingsEntity): Boolean {
    if (!settings.enableEngineAnalysis) return false
    return settings.showEvalBar ||
      settings.showThreatArrow ||
      settings.showSuggestionArrow ||
      settings.showEngineLine ||
      settings.showCandidateMoves ||
      settings.showTacticalSuggestions ||
      settings.enableCriticalMoments ||
      settings.showMoveFeedback ||
      settings.showBotMoveFeedback
  }

  fun needPv(settings: AppSettingsEntity): Boolean =
    settings.enableEngineAnalysis && settings.showEngineLine

  fun needCandidates(settings: AppSettingsEntity): Boolean =
    settings.enableEngineAnalysis && settings.showCandidateMoves

  fun needThreat(settings: AppSettingsEntity): Boolean =
    settings.enableEngineAnalysis && (settings.showThreatArrow || settings.showTacticalSuggestions)

  fun showEvalBar(settings: AppSettingsEntity): Boolean =
    settings.enableEngineAnalysis && settings.showEvalBar

  fun showSuggestionArrow(settings: AppSettingsEntity): Boolean =
    settings.enableEngineAnalysis && settings.showSuggestionArrow

  fun showThreatArrow(settings: AppSettingsEntity): Boolean =
    settings.enableEngineAnalysis && settings.showThreatArrow

  fun showMoveFeedbackForGame(settings: AppSettingsEntity, involvesBot: Boolean): Boolean {
    if (!settings.showMoveFeedback) return false
    return if (involvesBot) settings.showBotMoveFeedback else true
  }

  fun shouldComputeMoveFeedback(settings: AppSettingsEntity, involvesBot: Boolean): Boolean {
    if (!settings.enableEngineAnalysis) return false
    return showMoveFeedbackForGame(settings, involvesBot) || settings.enableCriticalMoments
  }
}
