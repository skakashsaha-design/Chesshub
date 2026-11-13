package com.example.chess

enum class PieceColor {
  WHITE,
  BLACK;

  fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val symbol: Char, val valueCp: Int) {
  PAWN('P', 100),
  KNIGHT('N', 320),
  BISHOP('B', 330),
  ROOK('R', 500),
  QUEEN('Q', 900),
  KING('K', 20000);

  companion object {
    fun fromChar(c: Char): PieceType? = when (c.uppercaseChar()) {
      'P' -> PAWN
      'N' -> KNIGHT
      'B' -> BISHOP
      'R' -> ROOK
      'Q' -> QUEEN
      'K' -> KING
      else -> null
    }
  }
}

object PieceCode {
  const val EMPTY = 0
  const val WP = 1
  const val WN = 2
  const val WB = 3
  const val WR = 4
  const val WQ = 5
  const val WK = 6

  const val BP = -1
  const val BN = -2
  const val BB = -3
  const val BR = -4
  const val BQ = -5
  const val BK = -6

  fun colorOf(code: Int): PieceColor? = when {
    code > 0 -> PieceColor.WHITE
    code < 0 -> PieceColor.BLACK
    else -> null
  }

  fun typeOf(code: Int): PieceType? = when (kotlin.math.abs(code)) {
    1 -> PieceType.PAWN
    2 -> PieceType.KNIGHT
    3 -> PieceType.BISHOP
    4 -> PieceType.ROOK
    5 -> PieceType.QUEEN
    6 -> PieceType.KING
    else -> null
  }

  fun make(color: PieceColor, type: PieceType): Int {
    val base = when (type) {
      PieceType.PAWN -> 1
      PieceType.KNIGHT -> 2
      PieceType.BISHOP -> 3
      PieceType.ROOK -> 4
      PieceType.QUEEN -> 5
      PieceType.KING -> 6
    }
    return if (color == PieceColor.WHITE) base else -base
  }

  fun toFenChar(code: Int): Char = when (code) {
    WP -> 'P'
    WN -> 'N'
    WB -> 'B'
    WR -> 'R'
    WQ -> 'Q'
    WK -> 'K'
    BP -> 'p'
    BN -> 'n'
    BB -> 'b'
    BR -> 'r'
    BQ -> 'q'
    BK -> 'k'
    else -> ' '
  }

  fun fromFenChar(c: Char): Int = when (c) {
    'P' -> WP
    'N' -> WN
    'B' -> WB
    'R' -> WR
    'Q' -> WQ
    'K' -> WK
    'p' -> BP
    'n' -> BN
    'b' -> BB
    'r' -> BR
    'q' -> BQ
    'k' -> BK
    else -> EMPTY
  }
}

object CastlingMask {
  const val NONE = 0
  const val WHITE_KING = 1
  const val WHITE_QUEEN = 2
  const val BLACK_KING = 4
  const val BLACK_QUEEN = 8
  const val ALL = WHITE_KING or WHITE_QUEEN or BLACK_KING or BLACK_QUEEN
}

data class Move(
  val from: Int,
  val to: Int,
  val promotion: PieceType? = null,
  val isEnPassant: Boolean = false,
  val isCastleKingside: Boolean = false,
  val isCastleQueenside: Boolean = false,
  val capturedPiece: Int = PieceCode.EMPTY,
) {
  fun toUci(): String {
    val f = SquareUtil.toAlgebraic(from)
    val t = SquareUtil.toAlgebraic(to)
    val p = promotion?.symbol?.lowercaseChar()?.toString().orEmpty()
    return "$f$t$p"
  }
}

object SquareUtil {
  fun of(file: Int, rank: Int): Int = rank * 8 + file
  fun fileOf(sq: Int): Int = sq and 7
  fun rankOf(sq: Int): Int = sq ushr 3

  fun toAlgebraic(sq: Int): String {
    if (sq !in 0..63) return "-"
    val f = ('a'.code + fileOf(sq)).toChar()
    val r = ('1'.code + rankOf(sq)).toChar()
    return "$f$r"
  }

  fun fromAlgebraic(str: String): Int {
    if (str.length < 2) return -1
    val f = str[0].lowercaseChar() - 'a'
    val r = str[1] - '1'
    if (f !in 0..7 || r !in 0..7) return -1
    return of(f, r)
  }
}

enum class GameResult(val pgnValue: String) {
  IN_PROGRESS("*"),
  WHITE_WIN("1-0"),
  BLACK_WIN("0-1"),
  DRAW("1/2-1/2"),
}

enum class TerminationReason {
  NONE,
  CHECKMATE,
  STALEMATE,
  THREEFOLD_REPETITION,
  FIFTY_MOVE_RULE,
  INSUFFICIENT_MATERIAL,
  RESIGNATION,
  DRAW_AGREEMENT,
  TIMEOUT,
}

data class BoardState(
  val board: IntArray,
  val turn: PieceColor = PieceColor.WHITE,
  val castlingMask: Int = CastlingMask.ALL,
  val enPassantSq: Int = -1,
  val halfMoveClock: Int = 0,
  val fullMoveNumber: Int = 1,
  val zobristHash: Long = 0L,
) {
  fun copyBoard(): IntArray = board.copyOf()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is BoardState) return false
    return turn == other.turn &&
      castlingMask == other.castlingMask &&
      enPassantSq == other.enPassantSq &&
      halfMoveClock == other.halfMoveClock &&
      fullMoveNumber == other.fullMoveNumber &&
      board.contentEquals(other.board)
  }

  override fun hashCode(): Int {
    var result = board.contentHashCode()
    result = 31 * result + turn.hashCode()
    result = 31 * result + castlingMask
    result = 31 * result + enPassantSq
    return result
  }
}

data class RecordedMove(
  val move: Move,
  val san: String,
  val uci: String,
  val fenAfter: String,
)
