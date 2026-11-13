package com.example.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.BoardState
import com.example.chess.ChessRules
import com.example.chess.Move
import com.example.chess.PieceCode
import com.example.chess.SquareUtil
import com.example.ui.board.PieceVectorRenderer.drawPiece
import kotlin.math.floor

@Composable
fun ChessBoardView(
  boardState: BoardState,
  selectedSquare: Int?,
  legalMoves: List<Move>,
  lastMoveFrom: Int?,
  lastMoveTo: Int?,
  hintMove: Move? = null,
  isFlipped: Boolean = false,
  boardTheme: BoardThemeOption = BoardThemeOption.WALNUT,
  pieceStyle: PieceStyleOption = PieceStyleOption.CLASSIC,
  showCoordinates: Boolean = true,
  showLegalMoves: Boolean = true,
  animateMoves: Boolean = true,
  onSquareTap: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  val moveProgress = remember { Animatable(1f) }

  LaunchedEffect(lastMoveFrom, lastMoveTo, animateMoves) {
    if (animateMoves && lastMoveFrom != null && lastMoveTo != null) {
      moveProgress.snapTo(0f)
      moveProgress.animateTo(1f, animationSpec = tween(durationMillis = 140))
    } else {
      moveProgress.snapTo(1f)
    }
  }

  val kingInCheckSq = remember(boardState) {
    if (ChessRules.isInCheck(boardState)) {
      ChessRules.findKingSquare(boardState.board, boardState.turn)
    } else {
      -1
    }
  }

  val targetMovesForSelected = remember(selectedSquare, legalMoves) {
    if (selectedSquare == null) emptyMap()
    else legalMoves.filter { it.from == selectedSquare }.associateBy { it.to }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(1f)
      .clip(RoundedCornerShape(10.dp))
      .testTag("chess_board")
      .semantics { contentDescription = "Interactive chessboard" },
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(isFlipped, onSquareTap) {
          detectTapGestures { offset ->
            val sqSize = size.width / 8f
            val col = floor(offset.x / sqSize).toInt().coerceIn(0, 7)
            val row = floor(offset.y / sqSize).toInt().coerceIn(0, 7)
            val file = if (isFlipped) 7 - col else col
            val rank = if (isFlipped) row else 7 - row
            onSquareTap(SquareUtil.of(file, rank))
          }
        },
    ) {
      val sqSize = size.width / 8f
      val progress = moveProgress.value

      // 1. Draw squares & highlights
      for (row in 0..7) {
        for (col in 0..7) {
          val file = if (isFlipped) 7 - col else col
          val rank = if (isFlipped) row else 7 - row
          val sq = SquareUtil.of(file, rank)
          val isLight = (file + rank) % 2 != 0
          val topLeft = Offset(col * sqSize, row * sqSize)

          drawRect(
            color = if (isLight) boardTheme.lightSquare else boardTheme.darkSquare,
            topLeft = topLeft,
            size = Size(sqSize, sqSize),
          )

          if (sq == lastMoveFrom || sq == lastMoveTo) {
            drawRect(color = boardTheme.lastMoveTint, topLeft = topLeft, size = Size(sqSize, sqSize))
          }
          if (sq == selectedSquare) {
            drawRect(color = boardTheme.selectedTint, topLeft = topLeft, size = Size(sqSize, sqSize))
          }
          if (hintMove != null && (sq == hintMove.from || sq == hintMove.to)) {
            drawRect(color = Color(0x6610B981), topLeft = topLeft, size = Size(sqSize, sqSize))
          }
          if (sq == kingInCheckSq) {
            val center = Offset(topLeft.x + sqSize / 2f, topLeft.y + sqSize / 2f)
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(Color(0xDDF43F5E), Color(0x00F43F5E)),
                center = center,
                radius = sqSize * 0.56f,
              ),
              radius = sqSize * 0.56f,
              center = center,
            )
          }

          // Coordinates on edge squares
          if (showCoordinates) {
            val coordColor = if (isLight) boardTheme.coordLight else boardTheme.coordDark
            val coordStyle = TextStyle(
              color = coordColor,
              fontSize = (sqSize * 0.20f).coerceIn(8f, 12f).sp,
              fontWeight = FontWeight.SemiBold,
            )
            if (col == 0) {
              val rankLabel = ('1'.code + rank).toChar().toString()
              drawText(
                textMeasurer = textMeasurer,
                text = rankLabel,
                topLeft = Offset(topLeft.x + sqSize * 0.06f, topLeft.y + sqSize * 0.04f),
                style = coordStyle,
              )
            }
            if (row == 7) {
              val fileLabel = ('a'.code + file).toChar().toString()
              val measured = textMeasurer.measure(fileLabel, coordStyle)
              drawText(
                textMeasurer = textMeasurer,
                text = fileLabel,
                topLeft = Offset(
                  topLeft.x + sqSize - measured.size.width - sqSize * 0.06f,
                  topLeft.y + sqSize - measured.size.height - sqSize * 0.04f,
                ),
                style = coordStyle,
              )
            }
          }
        }
      }

      // 2. Draw pieces (animating the latest moved piece cleanly)
      for (sq in 0 until 64) {
        val piece = boardState.board[sq]
        if (piece == PieceCode.EMPTY) continue

        val destPos = squareTopLeft(sq, sqSize, isFlipped)
        val drawPos = if (sq == lastMoveTo && lastMoveFrom != null && progress < 1f) {
          val fromPos = squareTopLeft(lastMoveFrom, sqSize, isFlipped)
          Offset(
            x = fromPos.x + (destPos.x - fromPos.x) * progress,
            y = fromPos.y + (destPos.y - fromPos.y) * progress,
          )
        } else {
          destPos
        }
        drawPiece(piece, drawPos, sqSize, pieceStyle)
      }

      // 3. Draw legal move indicators on top of squares/pieces
      if (showLegalMoves && targetMovesForSelected.isNotEmpty()) {
        for ((destSq, move) in targetMovesForSelected) {
          val tl = squareTopLeft(destSq, sqSize, isFlipped)
          val center = Offset(tl.x + sqSize / 2f, tl.y + sqSize / 2f)
          val isCapture = move.capturedPiece != PieceCode.EMPTY || move.isEnPassant
          if (isCapture) {
            drawCircle(
              color = Color(0x8816A34A),
              radius = sqSize * 0.42f,
              center = center,
              style = Stroke(width = (sqSize * 0.09f).coerceAtLeast(3f)),
            )
          } else {
            drawCircle(
              color = Color(0x8816A34A),
              radius = sqSize * 0.16f,
              center = center,
            )
          }
        }
      }
    }
  }
}

private fun squareTopLeft(sq: Int, sqSize: Float, isFlipped: Boolean): Offset {
  val file = SquareUtil.fileOf(sq)
  val rank = SquareUtil.rankOf(sq)
  val col = if (isFlipped) 7 - file else file
  val row = if (isFlipped) rank else 7 - rank
  return Offset(col * sqSize, row * sqSize)
}
