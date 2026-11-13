package com.example.analysis

import com.example.chess.BoardState
import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.Move
import com.example.chess.PieceCode
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.SquareUtil
import kotlin.math.abs
import kotlin.math.exp

/**
 * Pure-Kotlin move classification, explanation, tactical motif detector,
 * endgame analyzer, accuracy calculator, and position validator.
 * Zero heavy allocations so it runs smoothly on Android 8.1 & 1 GB RAM.
 */
object MoveFeedbackAnalyzer {

  fun winProbabilityPercent(cpForSide: Int): Double {
    val clamped = cpForSide.coerceIn(-4000, 4000)
    return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * clamped)) - 1.0)
  }

  fun moveAccuracyPercent(winProbBefore: Double, winProbAfter: Double): Double {
    val diff = (winProbBefore - winProbAfter).coerceAtLeast(0.0)
    val raw = 103.1668 * exp(-0.04354 * diff) - 3.1669
    return raw.coerceIn(0.0, 100.0)
  }

  fun evaluateMoveFeedback(
    plyIndex: Int,
    stateBefore: BoardState,
    move: Move,
    san: String,
    stateAfter: BoardState,
    evalBeforeCpForWhite: Int,
    evalAfterCpForWhite: Int,
    mateInAfterForWhite: Int?,
    bestMoveInBefore: Move?,
    bestMoveSan: String?,
    bestEvalBeforeForMoverCp: Int,
    secondBestEvalBeforeForMoverCp: Int?,
    isBookMove: Boolean,
    pvSanList: List<String>,
    enableCriticalMoments: Boolean,
  ): MoveAnalysisEntry {
    val mover = stateBefore.turn
    val sign = if (mover == PieceColor.WHITE) 1 else -1

    val evalBeforeForMover = evalBeforeCpForWhite * sign
    val evalAfterForMover = evalAfterCpForWhite * sign
    val deltaForMover = evalAfterForMover - evalBeforeForMover

    val winBefore = winProbabilityPercent(bestEvalBeforeForMoverCp)
    val winAfter = winProbabilityPercent(evalAfterForMover)
    val cpLoss = (bestEvalBeforeForMoverCp - evalAfterForMover).coerceAtLeast(0)
    val winDrop = (winBefore - winAfter).coerceAtLeast(0.0)
    val playedMatchesBest = bestMoveInBefore != null &&
      bestMoveInBefore.from == move.from &&
      bestMoveInBefore.to == move.to &&
      bestMoveInBefore.promotion == move.promotion

    val movingType = PieceCode.typeOf(stateBefore.board[move.from]) ?: PieceType.PAWN
    val isSacrifice = isSoundTacticalSacrifice(stateBefore, move, stateAfter, movingType, evalAfterForMover, evalBeforeForMover)
    val missedWinningShot = !playedMatchesBest &&
      bestEvalBeforeForMoverCp >= 180 &&
      bestEvalBeforeForMoverCp - evalBeforeForMover >= 140 &&
      cpLoss >= 120

    val classification = when {
      isBookMove -> MoveClassification.BOOK
      isSacrifice && (playedMatchesBest || cpLoss <= 20) -> MoveClassification.BRILLIANT
      missedWinningShot && cpLoss < 360 -> MoveClassification.MISSED_OPPORTUNITY
      playedMatchesBest || cpLoss <= 12 -> MoveClassification.BEST
      cpLoss <= 45 || winDrop <= 2.5 -> MoveClassification.EXCELLENT
      cpLoss <= 95 || winDrop <= 5.5 -> MoveClassification.GOOD
      cpLoss <= 190 || winDrop <= 11.5 -> MoveClassification.INACCURACY
      cpLoss <= 340 || winDrop <= 21.0 -> MoveClassification.MISTAKE
      else -> MoveClassification.BLUNDER
    }

    val evalSwing = abs(evalAfterCpForWhite - evalBeforeCpForWhite)
    val sharpDecisionGap = secondBestEvalBeforeForMoverCp != null &&
      abs(bestEvalBeforeForMoverCp - secondBestEvalBeforeForMoverCp) >= 150
    val isCritical = enableCriticalMoments && (
      evalSwing >= 150 ||
        classification == MoveClassification.BRILLIANT ||
        classification == MoveClassification.BLUNDER ||
        classification == MoveClassification.MISSED_OPPORTUNITY ||
        (sharpDecisionGap && !isBookMove)
      )

    val explanation = buildSimpleExplanation(
      classification = classification,
      san = san,
      stateBefore = stateBefore,
      move = move,
      stateAfter = stateAfter,
      bestMoveSan = bestMoveSan,
      cpLoss = cpLoss,
      deltaForMover = deltaForMover,
      mateInAfter = mateInAfterForWhite?.let { it * sign },
    )

    return MoveAnalysisEntry(
      plyIndex = plyIndex,
      san = san,
      uci = move.toUci(),
      move = move,
      mover = mover,
      evalBeforeCpForWhite = evalBeforeCpForWhite,
      evalAfterCpForWhite = evalAfterCpForWhite,
      evalDeltaForMoverCp = deltaForMover,
      mateInAfter = mateInAfterForWhite,
      classification = classification,
      bestMoveSan = if (playedMatchesBest) null else bestMoveSan,
      bestMoveUci = if (playedMatchesBest) null else bestMoveInBefore?.toUci(),
      isCriticalMoment = isCritical,
      explanation = explanation,
      pvSanList = pvSanList,
      fenAfter = ChessNotation.toFen(stateAfter),
      moveAccuracyPercent = if (isBookMove) 100.0 else moveAccuracyPercent(winBefore, winAfter),
    )
  }

  private fun isSoundTacticalSacrifice(
    stateBefore: BoardState,
    move: Move,
    stateAfter: BoardState,
    movingType: PieceType,
    evalAfterForMover: Int,
    evalBeforeForMover: Int,
  ): Boolean {
    if (movingType == PieceType.PAWN || movingType == PieceType.KING) return false
    if (evalBeforeForMover > 750) return false
    if (evalAfterForMover < 40) return false
    val destAttacked = ChessRules.isSquareAttacked(stateAfter.board, move.to, stateBefore.turn.opposite())
    if (!destAttacked) return false
    val capturedValue = PieceCode.typeOf(move.capturedPiece)?.valueCp ?: 0
    return movingType.valueCp - capturedValue >= 180
  }

  fun buildSimpleExplanation(
    classification: MoveClassification,
    san: String,
    stateBefore: BoardState,
    move: Move,
    stateAfter: BoardState,
    bestMoveSan: String?,
    cpLoss: Int,
    deltaForMover: Int,
    mateInAfter: Int?,
  ): String {
    val givesCheck = ChessRules.isInCheck(stateAfter)
    val isCapture = move.capturedPiece != PieceCode.EMPTY || move.isEnPassant
    val altHint = bestMoveSan?.let { " Stronger was $it." } ?: ""

    if (mateInAfter != null && mateInAfter > 0 && classification.isGoodOrNeutral()) {
      return "$san launches a forced mate in $mateInAfter moves."
    }
    if (mateInAfter != null && mateInAfter < 0) {
      return "$san allows a forced checkmate for your opponent!$altHint"
    }

    return when (classification) {
      MoveClassification.BOOK ->
        "$san follows established opening theory and develops cleanly."
      MoveClassification.BRILLIANT ->
        "$san is a strong tactical sacrifice — giving up material temporarily to seize a decisive initiative."
      MoveClassification.BEST -> when {
        givesCheck && isCapture -> "$san captures with check, keeping full control of the position."
        givesCheck -> "$san delivers an accurate check that disrupts opponent coordination."
        move.isCastleKingside || move.isCastleQueenside -> "$san safely castles your king and connects your rooks."
        isCapture -> "$san wins material cleanly while maintaining the strongest continuation."
        else -> "$san is the engine's top move, improving piece activity and control."
      }
      MoveClassification.EXCELLENT ->
        "$san is very accurate (${deltaText(deltaForMover)}) and keeps your position healthy."
      MoveClassification.GOOD ->
        "$san is a solid, playable move with only minor concessions.${if (bestMoveSan != null) " $bestMoveSan was slightly sharper." else ""}"
      MoveClassification.INACCURACY ->
        "$san loses a small edge (-${"%.1f".format(cpLoss / 100f)}) by slowing development or easing pressure.$altHint"
      MoveClassification.MISSED_OPPORTUNITY ->
        "$san misses a clear tactical chance to win material or tighten the attack.$altHint"
      MoveClassification.MISTAKE -> {
        val leftHanging = isMovedPieceUnsafe(stateAfter, move.to, stateBefore.turn)
        if (leftHanging) {
          "$san places the piece on a vulnerable square (-${"%.1f".format(cpLoss / 100f)}).$altHint"
        } else {
          "$san concedes significant initiative or material (-${"%.1f".format(cpLoss / 100f)}).$altHint"
        }
      }
      MoveClassification.BLUNDER -> {
        val leftHanging = isMovedPieceUnsafe(stateAfter, move.to, stateBefore.turn)
        if (leftHanging) {
          "$san drops material onto an attacked square (-${"%.1f".format(cpLoss / 100f)}).$altHint"
        } else {
          "$san severely damages your position (-${"%.1f".format(cpLoss / 100f)}), giving the opponent a winning swing.$altHint"
        }
      }
    }
  }

  private fun MoveClassification.isGoodOrNeutral(): Boolean = !isMistakeOrWorse

  private fun deltaText(cp: Int): String {
    val sign = if (cp >= 0) "+" else ""
    return "$sign${"%.2f".format(cp / 100f)}"
  }

  private fun isMovedPieceUnsafe(stateAfter: BoardState, sq: Int, moverColor: PieceColor): Boolean {
    val piece = stateAfter.board[sq]
    if (piece == PieceCode.EMPTY) return false
    val opponent = moverColor.opposite()
    return ChessRules.isSquareAttacked(stateAfter.board, sq, opponent) &&
      !ChessRules.isSquareAttacked(stateAfter.board, sq, moverColor)
  }

  fun analyzeEndgame(state: BoardState): EndgameInfo {
    var whiteNonKing = 0
    var blackNonKing = 0
    var whitePawns = 0
    var blackPawns = 0
    var rooks = 0
    var queens = 0
    var minors = 0
    var passedPawnsCount = 0

    for (sq in 0 until 64) {
      val p = state.board[sq]
      if (p == PieceCode.EMPTY) continue
      val type = PieceCode.typeOf(p) ?: continue
      val isWhite = p > 0
      when (type) {
        PieceType.KING -> Unit
        PieceType.PAWN -> {
          if (isWhite) {
            whitePawns++
            whiteNonKing += 100
            if (isPassedPawn(state.board, sq, PieceColor.WHITE)) passedPawnsCount++
          } else {
            blackPawns++
            blackNonKing += 100
            if (isPassedPawn(state.board, sq, PieceColor.BLACK)) passedPawnsCount++
          }
        }
        PieceType.KNIGHT, PieceType.BISHOP -> {
          minors++
          if (isWhite) whiteNonKing += type.valueCp else blackNonKing += type.valueCp
        }
        PieceType.ROOK -> {
          rooks++
          if (isWhite) whiteNonKing += 500 else blackNonKing += 500
        }
        PieceType.QUEEN -> {
          queens++
          if (isWhite) whiteNonKing += 900 else blackNonKing += 900
        }
      }
    }

    val isEndgame = (queens == 0 && maxOf(whiteNonKing, blackNonKing) <= 1300) ||
      (whiteNonKing <= 1100 && blackNonKing <= 1100)

    if (!isEndgame) {
      return EndgameInfo(
        isEndgame = false,
        category = if (state.fullMoveNumber <= 10) "Opening" else "Middlegame",
        summary = "Full material middlegame battle — prioritize king safety and active pieces.",
        advice = "Improve your worst-placed piece before opening lines.",
      )
    }

    val category = when {
      queens > 0 -> "Queen Endgame"
      rooks > 0 && minors == 0 -> "Rook Endgame"
      rooks == 0 && minors > 0 -> "Minor Piece Endgame"
      rooks == 0 && minors == 0 -> "King & Pawn Endgame"
      else -> "Complex Endgame"
    }

    val advice = when (category) {
      "King & Pawn Endgame" ->
        "Bring your king toward the center, seize opposition, and create an outside passed pawn."
      "Rook Endgame" ->
        "Keep rooks active behind passed pawns (both yours and your opponent's) and cut off the enemy king."
      "Minor Piece Endgame" ->
        "Target fixed pawns on the same color as your bishop or place knights on unassailable outposts."
      "Queen Endgame" ->
        "Centralize your queen, push passed pawns carefully, and guard against perpetual checks."
      else ->
        "Activate your king into the center and simplify into a clearly winning pawn race if ahead."
    }

    val passedNote = if (passedPawnsCount > 0) " • $passedPawnsCount passed pawn(s)" else ""
    return EndgameInfo(
      isEndgame = true,
      category = category,
      summary = "$category (${whitePawns}v$blackPawns pawns$passedNote)",
      advice = advice,
    )
  }

  private fun isPassedPawn(board: IntArray, sq: Int, color: PieceColor): Boolean {
    val file = SquareUtil.fileOf(sq)
    val rank = SquareUtil.rankOf(sq)
    val enemyPawn = if (color == PieceColor.WHITE) PieceCode.BP else PieceCode.WP
    val rankRange = if (color == PieceColor.WHITE) (rank + 1)..7 else 0 until rank
    for (r in rankRange) {
      for (f in (file - 1).coerceAtLeast(0)..(file + 1).coerceAtMost(7)) {
        if (board[SquareUtil.of(f, r)] == enemyPawn) return false
      }
    }
    return true
  }

  fun detectTacticalSuggestions(
    state: BoardState,
    threat: ThreatInfo?,
    bestMove: Move?,
  ): List<String> {
    val tips = ArrayList<String>(4)
    val us = state.turn
    val them = us.opposite()

    if (ChessRules.isInCheck(state)) {
      tips.add("Your king is in check — block, capture the checker, or step to safety.")
    }
    if (threat != null) {
      tips.add("Threat alert: opponent threatens ${threat.san} (${threat.explanation}).")
    }

    // Check for undefended enemy pieces (excluding pawns/king)
    for (sq in 0 until 64) {
      val p = state.board[sq]
      if (p == PieceCode.EMPTY || PieceCode.colorOf(p) != them) continue
      val type = PieceCode.typeOf(p) ?: continue
      if (type != PieceType.KING && type != PieceType.PAWN) {
        val defendedByEnemy = ChessRules.isSquareAttacked(state.board, sq, them)
        val attackedByUs = ChessRules.isSquareAttacked(state.board, sq, us)
        if (!defendedByEnemy) {
          val sqName = SquareUtil.toAlgebraic(sq)
          if (attackedByUs) {
            tips.add("Target: opponent's ${type.name.lowercase()} on $sqName is undefended and attacked!")
          } else {
            tips.add("Loose piece: opponent's ${type.name.lowercase()} on $sqName is undefended — look for a fork or double attack.")
          }
          break
        }
      }
    }

    // Check if our own high-value piece is hanging
    for (sq in 0 until 64) {
      val p = state.board[sq]
      if (p == PieceCode.EMPTY || PieceCode.colorOf(p) != us) continue
      val type = PieceCode.typeOf(p) ?: continue
      if (type != PieceType.KING && type != PieceType.PAWN) {
        val attacked = ChessRules.isSquareAttacked(state.board, sq, them)
        val defended = ChessRules.isSquareAttacked(state.board, sq, us)
        if (attacked && !defended) {
          tips.add("Guard your undefended ${type.name.lowercase()} on ${SquareUtil.toAlgebraic(sq)}.")
          break
        }
      }
    }

    if (bestMove != null && tips.size < 3) {
      val san = ChessNotation.toSan(state, bestMove)
      val next = ChessRules.applyMove(state, bestMove)
      when {
        ChessRules.isInCheck(next) -> tips.add("Suggested check: $san puts pressure directly on the enemy king.")
        bestMove.capturedPiece != PieceCode.EMPTY -> tips.add("Tactical capture: $san wins active material.")
      }
    }

    return tips.distinct().take(4)
  }

  fun validateCustomPosition(state: BoardState): String? {
    var whiteKings = 0
    var blackKings = 0
    var wkSq = -1
    var bkSq = -1
    for (sq in 0 until 64) {
      val p = state.board[sq]
      if (p == PieceCode.WK) {
        whiteKings++
        wkSq = sq
      } else if (p == PieceCode.BK) {
        blackKings++
        bkSq = sq
      } else if (p == PieceCode.WP || p == PieceCode.BP) {
        val r = SquareUtil.rankOf(sq)
        if (r == 0 || r == 7) {
          return "Pawns cannot stand on rank 1 or rank 8."
        }
      }
    }
    if (whiteKings != 1 || blackKings != 1) {
      return "Board must contain exactly one White King and one Black King."
    }
    val df = abs(SquareUtil.fileOf(wkSq) - SquareUtil.fileOf(bkSq))
    val dr = abs(SquareUtil.rankOf(wkSq) - SquareUtil.rankOf(bkSq))
    if (df <= 1 && dr <= 1) {
      return "Kings cannot stand on adjacent squares."
    }
    val nonMover = state.turn.opposite()
    if (ChessRules.isInCheck(state, nonMover)) {
      return "The side not to move cannot already be in check."
    }
    return null
  }

  val trainerDrills: List<TrainerDrill> = listOf(
    TrainerDrill(
      id = "drill_italian",
      title = "Italian Center Break",
      category = "Opening Practice",
      difficulty = "1000 Elo",
      fen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R w KQkq - 2 4",
      goal = "Strike in the center with d4 and learn active piece development.",
    ),
    TrainerDrill(
      id = "drill_fork",
      title = "Royal Knight Fork",
      category = "Tactical Drill",
      difficulty = "1200 Elo",
      fen = "r2qkb1r/ppp2ppp/2n5/3np1N1/2B5/8/PPPP1PPP/RNBQK2R w KQkq - 0 6",
      goal = "Exploit f7 weakness and practice forcing tactical combinations.",
    ),
    TrainerDrill(
      id = "drill_pin",
      title = "Absolute Pin Pressure",
      category = "Tactical Drill",
      difficulty = "1350 Elo",
      fen = "r1bq1rk1/ppp2ppp/2np1n2/2b1p1B1/2B1P3/2NP1N2/PPP2PPP/R2Q1RK1 w - - 4 7",
      goal = "Pile pressure on the pinned knight with Nd5.",
    ),
    TrainerDrill(
      id = "drill_kp_opposition",
      title = "King & Pawn Opposition",
      category = "Endgame Practice",
      difficulty = "1100 Elo",
      fen = "8/8/4k3/8/4P3/4K3/8/8 w - - 0 1",
      goal = "Use king opposition to escort the pawn cleanly to promotion.",
    ),
    TrainerDrill(
      id = "drill_lucena",
      title = "Rook Endgame Bridge",
      category = "Endgame Practice",
      difficulty = "1600 Elo",
      fen = "1K1k4/1P6/8/8/8/8/r7/4R3 w - - 0 1",
      goal = "Shield the king from checks and promote the 7th-rank pawn.",
    ),
    TrainerDrill(
      id = "drill_minority",
      title = "Queen's Gambit Structure",
      category = "Positional Trainer",
      difficulty = "1500 Elo",
      fen = "r2q1rk1/pp2bppp/2n1bn2/3p4/3P4/2NBPN2/PP3PPP/R1BQ1RK1 w - - 0 10",
      goal = "Practice IQP/Carlsbad plans with active bishop and outpost control.",
    ),
  )
}
