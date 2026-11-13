package com.example.engine

import com.example.chess.BoardState
import com.example.chess.ChessRules
import com.example.chess.Move
import com.example.chess.PieceCode
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.SquareUtil
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.ensureActive

data class SearchResult(
  val bestMove: Move,
  val scoreCp: Int,
  val depthReached: Int,
  val nodesVisited: Int,
)

/**
 * Lightweight, UI-independent Alpha-Beta + Quiescence chess engine.
 * Designed for 1 GB RAM Android devices:
 * - Fixed compact primitive-array transposition table
 * - Time & depth bounded iterative deepening
 * - Explicit memory release after moves when Low Memory Mode is active
 */
class ChessEngine {

  private var ttKeys: LongArray = LongArray(0)
  private var ttScores: IntArray = IntArray(0)
  private var ttDepths: ByteArray = ByteArray(0)
  private var ttFlags: ByteArray = ByteArray(0)
  private var ttMoves: IntArray = IntArray(0) // packed from | (to << 6)
  private var ttMask: Int = 0

  private var nodes = 0
  private var deadlineNs = 0L
  private var aborted = false

  private fun ensureTtCapacity(sizePowerOfTwo: Int) {
    if (ttKeys.size != sizePowerOfTwo) {
      ttKeys = LongArray(sizePowerOfTwo)
      ttScores = IntArray(sizePowerOfTwo)
      ttDepths = ByteArray(sizePowerOfTwo)
      ttFlags = ByteArray(sizePowerOfTwo)
      ttMoves = IntArray(sizePowerOfTwo)
      ttMask = sizePowerOfTwo - 1
    }
  }

  fun releaseMemory() {
    ttKeys = LongArray(0)
    ttScores = IntArray(0)
    ttDepths = ByteArray(0)
    ttFlags = ByteArray(0)
    ttMoves = IntArray(0)
    ttMask = 0
  }

  suspend fun findBestMove(
    state: BoardState,
    budget: SearchBudget,
    randomnessCp: Int = 0,
    recentHashes: Map<String, Int> = emptyMap(),
  ): SearchResult? {
    val legal = ChessRules.legalMoves(state)
    if (legal.isEmpty()) return null
    if (legal.size == 1) {
      return SearchResult(legal[0], evaluateStatic(state), 1, 1)
    }

    // Ultra-shallow beginner mode
    if (budget.maxDepth <= 1 && randomnessCp >= 180) {
      val scored = legal.map { m ->
        val next = ChessRules.applyMove(state, m)
        val eval = -evaluateStatic(next) + Random.nextInt(-randomnessCp, randomnessCp + 1)
        m to eval
      }.sortedByDescending { it.second }
      val chosen = scored.first().first
      if (budget.releaseAfterMove) releaseMemory()
      return SearchResult(chosen, scored.first().second, 1, legal.size)
    }

    ensureTtCapacity(budget.ttPowerOfTwoSize)
    nodes = 0
    aborted = false
    deadlineNs = System.nanoTime() + budget.maxTimeMs * 1_000_000L

    var bestMove = legal[0]
    var bestScore = 0
    var depthReached = 0

    for (depth in 1..budget.maxDepth) {
      coroutineContext.ensureActive()
      val ordered = orderMoves(state, legal, getTtPackedMove(state.zobristHash))
      var alpha = -MATE_SCORE
      val beta = MATE_SCORE
      var iterBestMove = ordered[0]
      var iterBestScore = -MATE_SCORE

      val moveScores = if (depth == budget.maxDepth && randomnessCp > 0) {
        ArrayList<Pair<Move, Int>>(ordered.size)
      } else {
        null
      }

      for (move in ordered) {
        if (checkTimeout()) break
        val next = ChessRules.applyMove(state, move)
        // Discourage accidental threefold repetition when ahead
        val repKey = ChessRules.repetitionKey(next)
        val repPenalty = if ((recentHashes[repKey] ?: 0) >= 2) -40 else 0

        val score = -negamax(next, depth - 1, -beta, -alpha, ply = 1) + repPenalty
        if (aborted) break

        moveScores?.add(move to score)
        if (score > iterBestScore) {
          iterBestScore = score
          iterBestMove = move
        }
        if (score > alpha) alpha = score
      }

      if (!aborted) {
        bestMove = iterBestMove
        bestScore = iterBestScore
        depthReached = depth

        if (moveScores != null && randomnessCp > 0 && moveScores.size > 1) {
          val threshold = iterBestScore - randomnessCp
          val candidates = moveScores.filter { it.second >= threshold }
          if (candidates.isNotEmpty()) {
            bestMove = candidates[Random.nextInt(candidates.size)].first
          }
        }
        if (abs(bestScore) >= MATE_THRESHOLD) break
      } else {
        break
      }
    }

    if (budget.releaseAfterMove) {
      releaseMemory()
    }
    return SearchResult(bestMove, bestScore, depthReached.coerceAtLeast(1), nodes)
  }

  fun evaluatePositionForWhite(state: BoardState): Int {
    val staticEval = evaluateStatic(state)
    return if (state.turn == PieceColor.WHITE) staticEval else -staticEval
  }

  private fun checkTimeout(): Boolean {
    if ((nodes and 255) == 0 && System.nanoTime() >= deadlineNs) {
      aborted = true
    }
    return aborted
  }

  private fun negamax(
    state: BoardState,
    depth: Int,
    alphaInit: Int,
    beta: Int,
    ply: Int,
  ): Int {
    nodes++
    if (checkTimeout()) return 0

    var alpha = alphaInit
    val origAlpha = alpha

    // Probe TT
    if (ttMask > 0) {
      val idx = (state.zobristHash and ttMask.toLong()).toInt()
      if (ttKeys[idx] == state.zobristHash && ttDepths[idx] >= depth) {
        val ttScore = ttScores[idx]
        when (ttFlags[idx].toInt()) {
          TT_EXACT -> return ttScore
          TT_LOWER -> if (ttScore >= beta) return ttScore
          TT_UPPER -> if (ttScore <= alpha) return ttScore
        }
      }
    }

    if (state.halfMoveClock >= 100 || ChessRules.hasInsufficientMaterial(state.board)) {
      return 0
    }

    val legal = ChessRules.legalMoves(state)
    if (legal.isEmpty()) {
      return if (ChessRules.isInCheck(state)) -MATE_SCORE + ply else 0
    }
    if (depth <= 0) {
      return quiescence(state, alpha, beta, qPly = 0)
    }

    val ttPacked = getTtPackedMove(state.zobristHash)
    val ordered = orderMoves(state, legal, ttPacked)
    var bestScore = -MATE_SCORE
    var bestMove = ordered[0]

    for (move in ordered) {
      val next = ChessRules.applyMove(state, move)
      val score = -negamax(next, depth - 1, -beta, -alpha, ply + 1)
      if (aborted) return 0
      if (score > bestScore) {
        bestScore = score
        bestMove = move
      }
      if (score > alpha) alpha = score
      if (alpha >= beta) break
    }

    if (ttMask > 0 && !aborted) {
      val idx = (state.zobristHash and ttMask.toLong()).toInt()
      ttKeys[idx] = state.zobristHash
      ttScores[idx] = bestScore
      ttDepths[idx] = depth.toByte()
      ttFlags[idx] = when {
        bestScore <= origAlpha -> TT_UPPER
        bestScore >= beta -> TT_LOWER
        else -> TT_EXACT
      }.toByte()
      ttMoves[idx] = packMove(bestMove)
    }

    return bestScore
  }

  private fun quiescence(state: BoardState, alphaInit: Int, beta: Int, qPly: Int): Int {
    nodes++
    if (checkTimeout()) return 0

    var alpha = alphaInit
    val standPat = evaluateStatic(state)
    if (standPat >= beta) return beta
    if (alpha < standPat) alpha = standPat
    if (qPly >= 3) return alpha

    val captures = ChessRules.legalMoves(state).filter {
      it.capturedPiece != PieceCode.EMPTY || it.promotion != null
    }.sortedByDescending { moveScoreGuess(state, it, 0) }

    for (cap in captures) {
      val next = ChessRules.applyMove(state, cap)
      val score = -quiescence(next, -beta, -alpha, qPly + 1)
      if (aborted) return 0
      if (score >= beta) return beta
      if (score > alpha) alpha = score
    }
    return alpha
  }

  private fun getTtPackedMove(hash: Long): Int {
    if (ttMask <= 0) return 0
    val idx = (hash and ttMask.toLong()).toInt()
    return if (ttKeys[idx] == hash) ttMoves[idx] else 0
  }

  private fun packMove(m: Move): Int = (m.from and 63) or ((m.to and 63) shl 6) or 0x10000

  private fun orderMoves(state: BoardState, moves: List<Move>, ttPacked: Int): List<Move> {
    return moves.sortedByDescending { moveScoreGuess(state, it, ttPacked) }
  }

  private fun moveScoreGuess(state: BoardState, m: Move, ttPacked: Int): Int {
    if (ttPacked != 0 && packMove(m) == ttPacked) return 1_000_000
    var score = 0
    if (m.capturedPiece != PieceCode.EMPTY) {
      val victim = PieceCode.typeOf(m.capturedPiece)?.valueCp ?: 100
      val attacker = PieceCode.typeOf(state.board[m.from])?.valueCp ?: 100
      score += 10_000 + victim * 10 - attacker
    }
    if (m.promotion != null) {
      score += 8_000 + m.promotion.valueCp
    }
    if (m.isCastleKingside || m.isCastleQueenside) {
      score += 300
    }
    return score
  }

  private fun evaluateStatic(state: BoardState): Int {
    var whiteScore = 0
    var blackScore = 0
    val board = state.board

    for (sq in 0 until 64) {
      val p = board[sq]
      if (p == PieceCode.EMPTY) continue
      val file = SquareUtil.fileOf(sq)
      val rank = SquareUtil.rankOf(sq)
      val type = PieceCode.typeOf(p) ?: continue
      val isWhite = p > 0
      val pstRank = if (isWhite) rank else 7 - rank
      val pstIdx = pstRank * 8 + file

      val pstBonus = when (type) {
        PieceType.PAWN -> PAWN_PST[pstIdx]
        PieceType.KNIGHT -> KNIGHT_PST[pstIdx]
        PieceType.BISHOP -> BISHOP_PST[pstIdx]
        PieceType.ROOK -> ROOK_PST[pstIdx]
        PieceType.QUEEN -> QUEEN_PST[pstIdx]
        PieceType.KING -> KING_MID_PST[pstIdx]
      }
      val total = type.valueCp + pstBonus
      if (isWhite) whiteScore += total else blackScore += total
    }

    val diff = whiteScore - blackScore
    return if (state.turn == PieceColor.WHITE) diff else -diff
  }

  companion object {
    private const val MATE_SCORE = 100_000
    private const val MATE_THRESHOLD = 90_000
    private const val TT_EXACT = 0
    private const val TT_LOWER = 1
    private const val TT_UPPER = 2

    // Rank 1 at index 0..7 up to Rank 8 at index 56..63 (from White's perspective)
    private val PAWN_PST = intArrayOf(
      0, 0, 0, 0, 0, 0, 0, 0,
      5, 10, 10, -20, -20, 10, 10, 5,
      5, -5, -10, 0, 0, -10, -5, 5,
      0, 0, 0, 22, 22, 0, 0, 0,
      5, 5, 10, 25, 25, 10, 5, 5,
      10, 10, 20, 30, 30, 20, 10, 10,
      50, 50, 50, 50, 50, 50, 50, 50,
      0, 0, 0, 0, 0, 0, 0, 0,
    )

    private val KNIGHT_PST = intArrayOf(
      -50, -40, -30, -30, -30, -30, -40, -50,
      -40, -20, 0, 5, 5, 0, -20, -40,
      -30, 5, 10, 15, 15, 10, 5, -30,
      -30, 0, 15, 20, 20, 15, 0, -30,
      -30, 5, 15, 20, 20, 15, 5, -30,
      -30, 0, 10, 15, 15, 10, 0, -30,
      -40, -20, 0, 0, 0, 0, -20, -40,
      -50, -40, -30, -30, -30, -30, -40, -50,
    )

    private val BISHOP_PST = intArrayOf(
      -20, -10, -10, -10, -10, -10, -10, -20,
      -10, 5, 0, 0, 0, 0, 5, -10,
      -10, 10, 10, 10, 10, 10, 10, -10,
      -10, 0, 10, 10, 10, 10, 0, -10,
      -10, 5, 5, 10, 10, 5, 5, -10,
      -10, 0, 5, 10, 10, 5, 0, -10,
      -10, 0, 0, 0, 0, 0, 0, -10,
      -20, -10, -10, -10, -10, -10, -10, -20,
    )

    private val ROOK_PST = intArrayOf(
      0, 0, 0, 5, 5, 0, 0, 0,
      -5, 0, 0, 0, 0, 0, 0, -5,
      -5, 0, 0, 0, 0, 0, 0, -5,
      -5, 0, 0, 0, 0, 0, 0, -5,
      -5, 0, 0, 0, 0, 0, 0, -5,
      -5, 0, 0, 0, 0, 0, 0, -5,
      5, 10, 10, 10, 10, 10, 10, 5,
      0, 0, 0, 0, 0, 0, 0, 0,
    )

    private val QUEEN_PST = intArrayOf(
      -20, -10, -10, -5, -5, -10, -10, -20,
      -10, 0, 5, 0, 0, 0, 0, -10,
      -10, 5, 5, 5, 5, 5, 0, -10,
      0, 0, 5, 5, 5, 5, 0, -5,
      -5, 0, 5, 5, 5, 5, 0, -5,
      -10, 0, 5, 5, 5, 5, 0, -10,
      -10, 0, 0, 0, 0, 0, 0, -10,
      -20, -10, -10, -5, -5, -10, -10, -20,
    )

    private val KING_MID_PST = intArrayOf(
      20, 30, 10, 0, 0, 10, 30, 20,
      20, 20, 0, 0, 0, 0, 20, 20,
      -10, -20, -20, -20, -20, -20, -20, -10,
      -20, -30, -30, -40, -40, -30, -30, -20,
      -30, -40, -40, -50, -50, -40, -40, -30,
      -30, -40, -40, -50, -50, -40, -40, -30,
      -30, -40, -40, -50, -50, -40, -40, -30,
      -30, -40, -40, -50, -50, -40, -40, -30,
    )
  }
}
