package com.example.chess

import kotlin.math.abs

/**
 * Pure Kotlin, UI-independent chess rules engine.
 * Supports complete standard FIDE rules:
 * legal moves, check, checkmate, stalemate, castling, en passant,
 * promotion, threefold repetition, 50-move rule, and insufficient material.
 */
object ChessRules {

  private val KNIGHT_OFFSETS = arrayOf(
    intArrayOf(-2, -1), intArrayOf(-2, 1),
    intArrayOf(-1, -2), intArrayOf(-1, 2),
    intArrayOf(1, -2), intArrayOf(1, 2),
    intArrayOf(2, -1), intArrayOf(2, 1),
  )

  private val KING_OFFSETS = arrayOf(
    intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1),
    intArrayOf(0, -1), intArrayOf(0, 1),
    intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1),
  )

  private val BISHOP_DIRS = arrayOf(
    intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1),
  )

  private val ROOK_DIRS = arrayOf(
    intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1),
  )

  // Deterministic Zobrist keys (lightweight 64-bit)
  private val PIECE_KEYS = Array(13) { LongArray(64) }
  private val CASTLE_KEYS = LongArray(16)
  private val EP_KEYS = LongArray(8)
  private val SIDE_KEY: Long

  init {
    var seed = 0x9E3779B97F4A7C15UL.toLong()
    fun nextRand(): Long {
      seed = seed xor (seed shl 13)
      seed = seed xor (seed ushr 7)
      seed = seed xor (seed shl 17)
      return seed
    }
    for (p in 0 until 13) {
      for (sq in 0 until 64) {
        PIECE_KEYS[p][sq] = nextRand()
      }
    }
    for (c in 0 until 16) CASTLE_KEYS[c] = nextRand()
    for (f in 0 until 8) EP_KEYS[f] = nextRand()
    SIDE_KEY = nextRand()
  }

  private fun pieceKeyIndex(code: Int): Int = code + 6

  fun computeHash(
    board: IntArray,
    turn: PieceColor,
    castlingMask: Int,
    enPassantSq: Int,
  ): Long {
    var h = 0L
    for (sq in 0 until 64) {
      val p = board[sq]
      if (p != PieceCode.EMPTY) {
        h = h xor PIECE_KEYS[pieceKeyIndex(p)][sq]
      }
    }
    if (turn == PieceColor.BLACK) h = h xor SIDE_KEY
    h = h xor CASTLE_KEYS[castlingMask and 0xF]
    if (enPassantSq in 0..63) {
      h = h xor EP_KEYS[SquareUtil.fileOf(enPassantSq)]
    }
    return h
  }

  fun initialBoardState(): BoardState {
    val b = IntArray(64)
    val whiteBack = intArrayOf(
      PieceCode.WR, PieceCode.WN, PieceCode.WB, PieceCode.WQ,
      PieceCode.WK, PieceCode.WB, PieceCode.WN, PieceCode.WR,
    )
    val blackBack = intArrayOf(
      PieceCode.BR, PieceCode.BN, PieceCode.BB, PieceCode.BQ,
      PieceCode.BK, PieceCode.BB, PieceCode.BN, PieceCode.BR,
    )
    for (f in 0..7) {
      b[SquareUtil.of(f, 0)] = whiteBack[f]
      b[SquareUtil.of(f, 1)] = PieceCode.WP
      b[SquareUtil.of(f, 6)] = PieceCode.BP
      b[SquareUtil.of(f, 7)] = blackBack[f]
    }
    val hash = computeHash(b, PieceColor.WHITE, CastlingMask.ALL, -1)
    return BoardState(
      board = b,
      turn = PieceColor.WHITE,
      castlingMask = CastlingMask.ALL,
      enPassantSq = -1,
      halfMoveClock = 0,
      fullMoveNumber = 1,
      zobristHash = hash,
    )
  }

  fun findKingSquare(board: IntArray, color: PieceColor): Int {
    val target = if (color == PieceColor.WHITE) PieceCode.WK else PieceCode.BK
    for (sq in 0 until 64) {
      if (board[sq] == target) return sq
    }
    return -1
  }

  fun isSquareAttacked(board: IntArray, sq: Int, byColor: PieceColor): Boolean {
    val file = SquareUtil.fileOf(sq)
    val rank = SquareUtil.rankOf(sq)

    // Pawn attacks
    val attackerPawn = if (byColor == PieceColor.WHITE) PieceCode.WP else PieceCode.BP
    val pawnSourceRank = if (byColor == PieceColor.WHITE) rank - 1 else rank + 1
    if (pawnSourceRank in 0..7) {
      if (file - 1 >= 0 && board[SquareUtil.of(file - 1, pawnSourceRank)] == attackerPawn) return true
      if (file + 1 <= 7 && board[SquareUtil.of(file + 1, pawnSourceRank)] == attackerPawn) return true
    }

    // Knight attacks
    val attackerKnight = if (byColor == PieceColor.WHITE) PieceCode.WN else PieceCode.BN
    for (off in KNIGHT_OFFSETS) {
      val nf = file + off[0]
      val nr = rank + off[1]
      if (nf in 0..7 && nr in 0..7 && board[SquareUtil.of(nf, nr)] == attackerKnight) return true
    }

    // King attacks
    val attackerKing = if (byColor == PieceColor.WHITE) PieceCode.WK else PieceCode.BK
    for (off in KING_OFFSETS) {
      val nf = file + off[0]
      val nr = rank + off[1]
      if (nf in 0..7 && nr in 0..7 && board[SquareUtil.of(nf, nr)] == attackerKing) return true
    }

    // Diagonal sliders (Bishop / Queen)
    val attackerBishop = if (byColor == PieceColor.WHITE) PieceCode.WB else PieceCode.BB
    val attackerQueen = if (byColor == PieceColor.WHITE) PieceCode.WQ else PieceCode.BQ
    for (dir in BISHOP_DIRS) {
      var nf = file + dir[0]
      var nr = rank + dir[1]
      while (nf in 0..7 && nr in 0..7) {
        val p = board[SquareUtil.of(nf, nr)]
        if (p != PieceCode.EMPTY) {
          if (p == attackerBishop || p == attackerQueen) return true
          break
        }
        nf += dir[0]
        nr += dir[1]
      }
    }

    // Orthogonal sliders (Rook / Queen)
    val attackerRook = if (byColor == PieceColor.WHITE) PieceCode.WR else PieceCode.BR
    for (dir in ROOK_DIRS) {
      var nf = file + dir[0]
      var nr = rank + dir[1]
      while (nf in 0..7 && nr in 0..7) {
        val p = board[SquareUtil.of(nf, nr)]
        if (p != PieceCode.EMPTY) {
          if (p == attackerRook || p == attackerQueen) return true
          break
        }
        nf += dir[0]
        nr += dir[1]
      }
    }

    return false
  }

  fun isInCheck(state: BoardState, color: PieceColor = state.turn): Boolean {
    val kingSq = findKingSquare(state.board, color)
    if (kingSq == -1) return false
    return isSquareAttacked(state.board, kingSq, color.opposite())
  }

  fun legalMoves(state: BoardState): List<Move> {
    val result = ArrayList<Move>(40)
    val us = state.turn
    val them = us.opposite()
    val board = state.board

    for (sq in 0 until 64) {
      val piece = board[sq]
      if (piece == PieceCode.EMPTY || PieceCode.colorOf(piece) != us) continue
      val type = PieceCode.typeOf(piece) ?: continue
      val file = SquareUtil.fileOf(sq)
      val rank = SquareUtil.rankOf(sq)

      when (type) {
        PieceType.PAWN -> {
          val dir = if (us == PieceColor.WHITE) 1 else -1
          val startRank = if (us == PieceColor.WHITE) 1 else 6
          val promoRank = if (us == PieceColor.WHITE) 7 else 0
          val nextRank = rank + dir

          if (nextRank in 0..7) {
            val oneStep = SquareUtil.of(file, nextRank)
            if (board[oneStep] == PieceCode.EMPTY) {
              if (nextRank == promoRank) {
                addPromotions(result, sq, oneStep, PieceCode.EMPTY)
              } else {
                result.add(Move(sq, oneStep))
                if (rank == startRank) {
                  val twoStep = SquareUtil.of(file, rank + 2 * dir)
                  if (board[twoStep] == PieceCode.EMPTY) {
                    result.add(Move(sq, twoStep))
                  }
                }
              }
            }

            // Captures
            for (df in intArrayOf(-1, 1)) {
              val cf = file + df
              if (cf !in 0..7) continue
              val targetSq = SquareUtil.of(cf, nextRank)
              val targetPiece = board[targetSq]
              if (targetPiece != PieceCode.EMPTY && PieceCode.colorOf(targetPiece) == them) {
                if (nextRank == promoRank) {
                  addPromotions(result, sq, targetSq, targetPiece)
                } else {
                  result.add(Move(sq, targetSq, capturedPiece = targetPiece))
                }
              } else if (targetSq == state.enPassantSq) {
                val epCapturedSq = SquareUtil.of(cf, rank)
                val epCapturedPiece = board[epCapturedSq]
                if (epCapturedPiece == PieceCode.make(them, PieceType.PAWN)) {
                  result.add(
                    Move(
                      from = sq,
                      to = targetSq,
                      isEnPassant = true,
                      capturedPiece = epCapturedPiece,
                    )
                  )
                }
              }
            }
          }
        }

        PieceType.KNIGHT -> {
          for (off in KNIGHT_OFFSETS) {
            val nf = file + off[0]
            val nr = rank + off[1]
            if (nf in 0..7 && nr in 0..7) {
              val targetSq = SquareUtil.of(nf, nr)
              val target = board[targetSq]
              if (target == PieceCode.EMPTY || PieceCode.colorOf(target) == them) {
                result.add(Move(sq, targetSq, capturedPiece = target))
              }
            }
          }
        }

        PieceType.BISHOP -> addSlidingMoves(result, board, sq, file, rank, BISHOP_DIRS, them)
        PieceType.ROOK -> addSlidingMoves(result, board, sq, file, rank, ROOK_DIRS, them)
        PieceType.QUEEN -> {
          addSlidingMoves(result, board, sq, file, rank, BISHOP_DIRS, them)
          addSlidingMoves(result, board, sq, file, rank, ROOK_DIRS, them)
        }

        PieceType.KING -> {
          for (off in KING_OFFSETS) {
            val nf = file + off[0]
            val nr = rank + off[1]
            if (nf in 0..7 && nr in 0..7) {
              val targetSq = SquareUtil.of(nf, nr)
              val target = board[targetSq]
              if (target == PieceCode.EMPTY || PieceCode.colorOf(target) == them) {
                result.add(Move(sq, targetSq, capturedPiece = target))
              }
            }
          }
          // Castling
          if (us == PieceColor.WHITE && sq == 4) {
            if ((state.castlingMask and CastlingMask.WHITE_KING) != 0 &&
              board[5] == PieceCode.EMPTY && board[6] == PieceCode.EMPTY &&
              board[7] == PieceCode.WR &&
              !isSquareAttacked(board, 4, them) &&
              !isSquareAttacked(board, 5, them) &&
              !isSquareAttacked(board, 6, them)
            ) {
              result.add(Move(4, 6, isCastleKingside = true))
            }
            if ((state.castlingMask and CastlingMask.WHITE_QUEEN) != 0 &&
              board[3] == PieceCode.EMPTY && board[2] == PieceCode.EMPTY && board[1] == PieceCode.EMPTY &&
              board[0] == PieceCode.WR &&
              !isSquareAttacked(board, 4, them) &&
              !isSquareAttacked(board, 3, them) &&
              !isSquareAttacked(board, 2, them)
            ) {
              result.add(Move(4, 2, isCastleQueenside = true))
            }
          } else if (us == PieceColor.BLACK && sq == 60) {
            if ((state.castlingMask and CastlingMask.BLACK_KING) != 0 &&
              board[61] == PieceCode.EMPTY && board[62] == PieceCode.EMPTY &&
              board[63] == PieceCode.BR &&
              !isSquareAttacked(board, 60, them) &&
              !isSquareAttacked(board, 61, them) &&
              !isSquareAttacked(board, 62, them)
            ) {
              result.add(Move(60, 62, isCastleKingside = true))
            }
            if ((state.castlingMask and CastlingMask.BLACK_QUEEN) != 0 &&
              board[59] == PieceCode.EMPTY && board[58] == PieceCode.EMPTY && board[57] == PieceCode.EMPTY &&
              board[56] == PieceCode.BR &&
              !isSquareAttacked(board, 60, them) &&
              !isSquareAttacked(board, 59, them) &&
              !isSquareAttacked(board, 58, them)
            ) {
              result.add(Move(60, 58, isCastleQueenside = true))
            }
          }
        }
      }
    }

    // Filter out moves leaving own king in check
    val legal = ArrayList<Move>(result.size)
    val tempBoard = board.copyOf()
    for (m in result) {
      applyMoveInPlace(tempBoard, m, us)
      val kingSq = findKingSquare(tempBoard, us)
      val safe = kingSq != -1 && !isSquareAttacked(tempBoard, kingSq, them)
      // Restore tempBoard fast by copying back modified squares or board
      System.arraycopy(board, 0, tempBoard, 0, 64)
      if (safe) legal.add(m)
    }
    return legal
  }

  private fun addPromotions(list: MutableList<Move>, from: Int, to: Int, captured: Int) {
    list.add(Move(from, to, promotion = PieceType.QUEEN, capturedPiece = captured))
    list.add(Move(from, to, promotion = PieceType.ROOK, capturedPiece = captured))
    list.add(Move(from, to, promotion = PieceType.BISHOP, capturedPiece = captured))
    list.add(Move(from, to, promotion = PieceType.KNIGHT, capturedPiece = captured))
  }

  private fun addSlidingMoves(
    list: MutableList<Move>,
    board: IntArray,
    from: Int,
    file: Int,
    rank: Int,
    dirs: Array<IntArray>,
    enemy: PieceColor,
  ) {
    for (dir in dirs) {
      var nf = file + dir[0]
      var nr = rank + dir[1]
      while (nf in 0..7 && nr in 0..7) {
        val targetSq = SquareUtil.of(nf, nr)
        val target = board[targetSq]
        if (target == PieceCode.EMPTY) {
          list.add(Move(from, targetSq))
        } else {
          if (PieceCode.colorOf(target) == enemy) {
            list.add(Move(from, targetSq, capturedPiece = target))
          }
          break
        }
        nf += dir[0]
        nr += dir[1]
      }
    }
  }

  private fun applyMoveInPlace(board: IntArray, move: Move, mover: PieceColor) {
    val movingPiece = board[move.from]
    board[move.from] = PieceCode.EMPTY
    if (move.isEnPassant) {
      val epSq = SquareUtil.of(SquareUtil.fileOf(move.to), SquareUtil.rankOf(move.from))
      board[epSq] = PieceCode.EMPTY
    }
    if (move.isCastleKingside) {
      if (mover == PieceColor.WHITE) {
        board[7] = PieceCode.EMPTY
        board[5] = PieceCode.WR
      } else {
        board[63] = PieceCode.EMPTY
        board[61] = PieceCode.BR
      }
    } else if (move.isCastleQueenside) {
      if (mover == PieceColor.WHITE) {
        board[0] = PieceCode.EMPTY
        board[3] = PieceCode.WR
      } else {
        board[56] = PieceCode.EMPTY
        board[59] = PieceCode.BR
      }
    }
    board[move.to] = if (move.promotion != null) {
      PieceCode.make(mover, move.promotion)
    } else {
      movingPiece
    }
  }

  fun applyMove(state: BoardState, move: Move): BoardState {
    val nextBoard = state.board.copyOf()
    val mover = state.turn
    val movingPiece = nextBoard[move.from]
    val movingType = PieceCode.typeOf(movingPiece)
    val isCapture = move.capturedPiece != PieceCode.EMPTY || move.isEnPassant

    applyMoveInPlace(nextBoard, move, mover)

    // Update castling mask
    var newCastle = state.castlingMask
    if (movingType == PieceType.KING) {
      newCastle = if (mover == PieceColor.WHITE) {
        newCastle and (CastlingMask.WHITE_KING or CastlingMask.WHITE_QUEEN).inv()
      } else {
        newCastle and (CastlingMask.BLACK_KING or CastlingMask.BLACK_QUEEN).inv()
      }
    }
    when (move.from) {
      0 -> newCastle = newCastle and CastlingMask.WHITE_QUEEN.inv()
      7 -> newCastle = newCastle and CastlingMask.WHITE_KING.inv()
      56 -> newCastle = newCastle and CastlingMask.BLACK_QUEEN.inv()
      63 -> newCastle = newCastle and CastlingMask.BLACK_KING.inv()
    }
    when (move.to) {
      0 -> newCastle = newCastle and CastlingMask.WHITE_QUEEN.inv()
      7 -> newCastle = newCastle and CastlingMask.WHITE_KING.inv()
      56 -> newCastle = newCastle and CastlingMask.BLACK_QUEEN.inv()
      63 -> newCastle = newCastle and CastlingMask.BLACK_KING.inv()
    }

    // Update en passant square
    val newEp = if (movingType == PieceType.PAWN && abs(SquareUtil.rankOf(move.to) - SquareUtil.rankOf(move.from)) == 2) {
      val midRank = (SquareUtil.rankOf(move.from) + SquareUtil.rankOf(move.to)) / 2
      SquareUtil.of(SquareUtil.fileOf(move.from), midRank)
    } else {
      -1
    }

    val newHalfMove = if (movingType == PieceType.PAWN || isCapture) 0 else state.halfMoveClock + 1
    val newFullMove = if (mover == PieceColor.BLACK) state.fullMoveNumber + 1 else state.fullMoveNumber
    val nextTurn = mover.opposite()
    val hash = computeHash(nextBoard, nextTurn, newCastle, newEp)

    return BoardState(
      board = nextBoard,
      turn = nextTurn,
      castlingMask = newCastle,
      enPassantSq = newEp,
      halfMoveClock = newHalfMove,
      fullMoveNumber = newFullMove,
      zobristHash = hash,
    )
  }

  /**
   * Canonical repetition key (excludes halfmove/fullmove clocks).
   */
  fun repetitionKey(state: BoardState): String {
    val sb = StringBuilder(72)
    for (i in 0 until 64) {
      val p = state.board[i]
      sb.append(if (p == PieceCode.EMPTY) '.' else PieceCode.toFenChar(p))
    }
    sb.append(if (state.turn == PieceColor.WHITE) 'w' else 'b')
    sb.append(state.castlingMask)
    sb.append(':')
    sb.append(state.enPassantSq)
    return sb.toString()
  }

  fun hasInsufficientMaterial(board: IntArray): Boolean {
    var whiteBishops = 0
    var blackBishops = 0
    var whiteKnights = 0
    var blackKnights = 0
    var whiteBishopColor = -1
    var blackBishopColor = -1

    for (sq in 0 until 64) {
      when (board[sq]) {
        PieceCode.EMPTY, PieceCode.WK, PieceCode.BK -> Unit
        PieceCode.WP, PieceCode.BP, PieceCode.WR, PieceCode.BR, PieceCode.WQ, PieceCode.BQ -> return false
        PieceCode.WB -> {
          whiteBishops++
          whiteBishopColor = (SquareUtil.fileOf(sq) + SquareUtil.rankOf(sq)) and 1
        }
        PieceCode.BB -> {
          blackBishops++
          blackBishopColor = (SquareUtil.fileOf(sq) + SquareUtil.rankOf(sq)) and 1
        }
        PieceCode.WN -> whiteKnights++
        PieceCode.BN -> blackKnights++
      }
    }

    val whiteMinors = whiteBishops + whiteKnights
    val blackMinors = blackBishops + blackKnights

    if (whiteMinors == 0 && blackMinors == 0) return true // K vs K
    if (whiteMinors <= 1 && blackMinors == 0) return true // K+B/N vs K
    if (blackMinors <= 1 && whiteMinors == 0) return true // K vs K+B/N
    if (whiteBishops == 1 && blackBishops == 1 && whiteKnights == 0 && blackKnights == 0) {
      return whiteBishopColor == blackBishopColor // K+B vs K+B on same color squares
    }
    return false
  }

  fun evaluateTerminalStatus(
    state: BoardState,
    repetitionCounts: Map<String, Int>,
  ): Pair<GameResult, TerminationReason> {
    val legal = legalMoves(state)
    if (legal.isEmpty()) {
      return if (isInCheck(state)) {
        val winner = if (state.turn == PieceColor.WHITE) GameResult.BLACK_WIN else GameResult.WHITE_WIN
        winner to TerminationReason.CHECKMATE
      } else {
        GameResult.DRAW to TerminationReason.STALEMATE
      }
    }
    if (state.halfMoveClock >= 100) {
      return GameResult.DRAW to TerminationReason.FIFTY_MOVE_RULE
    }
    val key = repetitionKey(state)
    if ((repetitionCounts[key] ?: 0) >= 3) {
      return GameResult.DRAW to TerminationReason.THREEFOLD_REPETITION
    }
    if (hasInsufficientMaterial(state.board)) {
      return GameResult.DRAW to TerminationReason.INSUFFICIENT_MATERIAL
    }
    return GameResult.IN_PROGRESS to TerminationReason.NONE
  }
}
