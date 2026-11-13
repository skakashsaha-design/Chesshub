package com.example.chess

object ChessNotation {

  fun toFen(state: BoardState): String {
    val sb = StringBuilder(80)
    for (r in 7 downTo 0) {
      var empty = 0
      for (f in 0..7) {
        val p = state.board[SquareUtil.of(f, r)]
        if (p == PieceCode.EMPTY) {
          empty++
        } else {
          if (empty > 0) {
            sb.append(empty)
            empty = 0
          }
          sb.append(PieceCode.toFenChar(p))
        }
      }
      if (empty > 0) sb.append(empty)
      if (r > 0) sb.append('/')
    }
    sb.append(if (state.turn == PieceColor.WHITE) " w " else " b ")

    val castle = buildString {
      if ((state.castlingMask and CastlingMask.WHITE_KING) != 0) append('K')
      if ((state.castlingMask and CastlingMask.WHITE_QUEEN) != 0) append('Q')
      if ((state.castlingMask and CastlingMask.BLACK_KING) != 0) append('k')
      if ((state.castlingMask and CastlingMask.BLACK_QUEEN) != 0) append('q')
    }
    sb.append(castle.ifEmpty { "-" })
    sb.append(' ')
    sb.append(if (state.enPassantSq in 0..63) SquareUtil.toAlgebraic(state.enPassantSq) else "-")
    sb.append(' ')
    sb.append(state.halfMoveClock)
    sb.append(' ')
    sb.append(state.fullMoveNumber)
    return sb.toString()
  }

  fun fromFen(fen: String): BoardState? {
    val parts = fen.trim().split(Regex("\\s+"))
    if (parts.size < 4) return null
    val board = IntArray(64)
    val rows = parts[0].split('/')
    if (rows.size != 8) return null

    for (idx in 0 until 8) {
      val rank = 7 - idx
      var file = 0
      for (ch in rows[idx]) {
        if (ch in '1'..'8') {
          file += (ch - '0')
        } else {
          if (file > 7) return null
          val code = PieceCode.fromFenChar(ch)
          if (code == PieceCode.EMPTY) return null
          board[SquareUtil.of(file, rank)] = code
          file++
        }
      }
      if (file != 8) return null
    }

    val turn = when (parts[1]) {
      "w" -> PieceColor.WHITE
      "b" -> PieceColor.BLACK
      else -> return null
    }

    var castle = CastlingMask.NONE
    if (parts[2] != "-") {
      for (c in parts[2]) {
        when (c) {
          'K' -> castle = castle or CastlingMask.WHITE_KING
          'Q' -> castle = castle or CastlingMask.WHITE_QUEEN
          'k' -> castle = castle or CastlingMask.BLACK_KING
          'q' -> castle = castle or CastlingMask.BLACK_QUEEN
        }
      }
    }

    val epSq = if (parts[3] == "-") -1 else SquareUtil.fromAlgebraic(parts[3])
    val halfMove = parts.getOrNull(4)?.toIntOrNull() ?: 0
    val fullMove = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val hash = ChessRules.computeHash(board, turn, castle, epSq)

    return BoardState(
      board = board,
      turn = turn,
      castlingMask = castle,
      enPassantSq = epSq,
      halfMoveClock = halfMove,
      fullMoveNumber = fullMove,
      zobristHash = hash,
    )
  }

  fun toSan(
    state: BoardState,
    move: Move,
    legalMovesInState: List<Move> = ChessRules.legalMoves(state),
  ): String {
    if (move.isCastleKingside) {
      val next = ChessRules.applyMove(state, move)
      val suffix = checkSuffix(next)
      return "O-O$suffix"
    }
    if (move.isCastleQueenside) {
      val next = ChessRules.applyMove(state, move)
      val suffix = checkSuffix(next)
      return "O-O-O$suffix"
    }

    val movingPiece = state.board[move.from]
    val type = PieceCode.typeOf(movingPiece) ?: return move.toUci()
    val isCapture = move.capturedPiece != PieceCode.EMPTY || move.isEnPassant
    val sb = StringBuilder()

    if (type == PieceType.PAWN) {
      if (isCapture) {
        sb.append(('a'.code + SquareUtil.fileOf(move.from)).toChar())
        sb.append('x')
      }
      sb.append(SquareUtil.toAlgebraic(move.to))
      if (move.promotion != null) {
        sb.append('=')
        sb.append(move.promotion.symbol)
      }
    } else {
      sb.append(type.symbol)
      // Disambiguation
      val conflicts = legalMovesInState.filter { other ->
        other.from != move.from &&
          other.to == move.to &&
          PieceCode.typeOf(state.board[other.from]) == type
      }
      if (conflicts.isNotEmpty()) {
        val sameFile = conflicts.any { SquareUtil.fileOf(it.from) == SquareUtil.fileOf(move.from) }
        val sameRank = conflicts.any { SquareUtil.rankOf(it.from) == SquareUtil.rankOf(move.from) }
        if (!sameFile) {
          sb.append(('a'.code + SquareUtil.fileOf(move.from)).toChar())
        } else if (!sameRank) {
          sb.append(('1'.code + SquareUtil.rankOf(move.from)).toChar())
        } else {
          sb.append(SquareUtil.toAlgebraic(move.from))
        }
      }
      if (isCapture) sb.append('x')
      sb.append(SquareUtil.toAlgebraic(move.to))
    }

    val next = ChessRules.applyMove(state, move)
    sb.append(checkSuffix(next))
    return sb.toString()
  }

  private fun checkSuffix(nextState: BoardState): String {
    if (!ChessRules.isInCheck(nextState)) return ""
    return if (ChessRules.legalMoves(nextState).isEmpty()) "#" else "+"
  }

  fun parseSanMove(state: BoardState, rawSan: String): Move? {
    var san = rawSan.trim()
      .replace("0-0-0", "O-O-O")
      .replace("0-0", "O-O")
      .trimEnd('+', '#', '!', '?')

    val legal = ChessRules.legalMoves(state)
    if (san == "O-O") return legal.firstOrNull { it.isCastleKingside }
    if (san == "O-O-O") return legal.firstOrNull { it.isCastleQueenside }

    var promo: PieceType? = null
    val eqIdx = san.indexOf('=')
    if (eqIdx != -1 && eqIdx + 1 < san.length) {
      promo = PieceType.fromChar(san[eqIdx + 1])
      san = san.substring(0, eqIdx)
    } else if (san.length >= 3 && san.last().uppercaseChar() in charArrayOf('Q', 'R', 'B', 'N') &&
      san[san.length - 2] in '1'..'8'
    ) {
      promo = PieceType.fromChar(san.last())
      san = san.dropLast(1)
    }

    san = san.replace("x", "")
    if (san.length < 2) return null

    val destStr = san.takeLast(2)
    val destSq = SquareUtil.fromAlgebraic(destStr)
    if (destSq !in 0..63) return null

    val prefix = san.dropLast(2)
    val pieceType = if (prefix.isNotEmpty() && prefix[0] in charArrayOf('K', 'Q', 'R', 'B', 'N')) {
      PieceType.fromChar(prefix[0])!!
    } else {
      PieceType.PAWN
    }

    val disambig = if (pieceType != PieceType.PAWN && prefix.isNotEmpty()) {
      prefix.drop(1)
    } else {
      prefix
    }

    var reqFile: Int? = null
    var reqRank: Int? = null
    for (ch in disambig) {
      when (ch) {
        in 'a'..'h' -> reqFile = ch - 'a'
        in '1'..'8' -> reqRank = ch - '1'
      }
    }

    return legal.firstOrNull { m ->
      val pType = PieceCode.typeOf(state.board[m.from])
      pType == pieceType &&
        m.to == destSq &&
        m.promotion == promo &&
        (reqFile == null || SquareUtil.fileOf(m.from) == reqFile) &&
        (reqRank == null || SquareUtil.rankOf(m.from) == reqRank)
    }
  }

  fun parseUciMove(state: BoardState, uci: String): Move? {
    if (uci.length < 4) return null
    val from = SquareUtil.fromAlgebraic(uci.substring(0, 2))
    val to = SquareUtil.fromAlgebraic(uci.substring(2, 4))
    val promo = if (uci.length >= 5) PieceType.fromChar(uci[4]) else null
    return ChessRules.legalMoves(state).firstOrNull {
      it.from == from && it.to == to && it.promotion == promo
    }
  }
}
