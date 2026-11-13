package com.example.ui.board

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateOffset
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.SquareUtil
import com.example.ui.board.PieceVectorRenderer.drawPiece
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

private enum class MovePhase {
  ORIGIN,
  DESTINATION,
}

@Immutable
private data class MoveAnimationKey(
  val fromSq: Int?,
  val toSq: Int?,
  val fullMoveNumber: Int,
  val turn: PieceColor,
)

@Immutable
internal data class CastleRookPath(
  val fromSq: Int,
  val toSq: Int,
)

@Immutable
data class BoardArrow(
  val fromSq: Int,
  val toSq: Int,
  val color: Color,
  val strokeRatio: Float = 0.13f,
)

@Composable
fun ChessBoardView(
  boardState: BoardState,
  selectedSquare: Int?,
  legalMoves: List<Move>,
  lastMoveFrom: Int?,
  lastMoveTo: Int?,
  hintMove: Move? = null,
  arrows: List<BoardArrow> = emptyList(),
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

  val moveKey = remember(lastMoveFrom, lastMoveTo, boardState.fullMoveNumber, boardState.turn) {
    MoveAnimationKey(
      fromSq = lastMoveFrom,
      toSq = lastMoveTo,
      fullMoveNumber = boardState.fullMoveNumber,
      turn = boardState.turn,
    )
  }

  val castleRookPath = remember(lastMoveFrom, lastMoveTo, boardState) {
    resolveCastleRookPath(lastMoveFrom, lastMoveTo, boardState)
  }

  val shouldAnimateCurrentMove = animateMoves && lastMoveFrom != null && lastMoveTo != null
  val moveTransitionState = remember(moveKey, animateMoves) {
    MutableTransitionState(
      if (shouldAnimateCurrentMove) MovePhase.ORIGIN else MovePhase.DESTINATION,
    ).apply {
      targetState = MovePhase.DESTINATION
    }
  }
  val moveTransition = rememberTransition(
    transitionState = moveTransitionState,
    label = "ChessPieceMoveTransition",
  )

  val destGrid = remember(lastMoveTo) {
    if (lastMoveTo != null) squareGridOffset(lastMoveTo) else Offset.Zero
  }
  val originGrid = remember(lastMoveFrom, destGrid) {
    if (lastMoveFrom != null) squareGridOffset(lastMoveFrom) else destGrid
  }

  val animatedPieceGridPos by moveTransition.animateOffset(
    transitionSpec = {
      spring(
        dampingRatio = 0.86f,
        stiffness = 460f,
      )
    },
    label = "MovingPieceGridPosition",
  ) { phase ->
    if (phase == MovePhase.ORIGIN) originGrid else destGrid
  }

  val rookDestGrid = remember(castleRookPath) {
    castleRookPath?.let { squareGridOffset(it.toSq) } ?: Offset.Zero
  }
  val rookOriginGrid = remember(castleRookPath, rookDestGrid) {
    castleRookPath?.let { squareGridOffset(it.fromSq) } ?: rookDestGrid
  }

  val animatedRookGridPos by moveTransition.animateOffset(
    transitionSpec = {
      spring(
        dampingRatio = 0.88f,
        stiffness = 430f,
      )
    },
    label = "CastlingRookGridPosition",
  ) { phase ->
    if (phase == MovePhase.ORIGIN) rookOriginGrid else rookDestGrid
  }

  val moveProgress by moveTransition.animateFloat(
    transitionSpec = {
      spring(
        dampingRatio = 0.88f,
        stiffness = 460f,
      )
    },
    label = "MoveFlightProgress",
  ) { phase ->
    if (phase == MovePhase.ORIGIN) 0f else 1f
  }

  val selectionTransitionState = remember(selectedSquare, animateMoves) {
    MutableTransitionState(
      if (animateMoves && selectedSquare != null) false else (selectedSquare != null),
    ).apply {
      targetState = selectedSquare != null
    }
  }
  val selectionTransition = rememberTransition(
    transitionState = selectionTransitionState,
    label = "SquareSelectionTransition",
  )
  val selectionEmphasis by selectionTransition.animateFloat(
    transitionSpec = {
      spring(
        dampingRatio = 0.72f,
        stiffness = 560f,
      )
    },
    label = "SelectionEmphasis",
  ) { active ->
    if (active) 1f else 0f
  }

  val kingInCheckSq = remember(boardState) {
    if (ChessRules.isInCheck(boardState)) {
      ChessRules.findKingSquare(boardState.board, boardState.turn)
    } else {
      -1
    }
  }

  val targetMovesForSelected = remember(selectedSquare, legalMoves) {
    legalTargetMovesForSquare(selectedSquare, legalMoves)
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
      val clampedProgress = moveProgress.coerceIn(0f, 1f)
      val arcFactor = if (shouldAnimateCurrentMove) sin(clampedProgress * PI).toFloat().coerceAtLeast(0f) else 0f
      val liftScale = 1f + 0.10f * arcFactor
      val shadowAlpha = 0.22f * arcFactor
      val lastMoveTintScale = if (shouldAnimateCurrentMove) 0.35f + 0.65f * clampedProgress else 1f
      val selectionScaleBoost = if (animateMoves) 1f + 0.06f * selectionEmphasis else 1f

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
            drawRect(
              color = boardTheme.lastMoveTint.copy(alpha = boardTheme.lastMoveTint.alpha * lastMoveTintScale),
              topLeft = topLeft,
              size = Size(sqSize, sqSize),
            )
          }
          if (sq == selectedSquare) {
            drawRect(
              color = boardTheme.selectedTint.copy(alpha = boardTheme.selectedTint.alpha * selectionEmphasis.coerceAtLeast(0.4f)),
              topLeft = topLeft,
              size = Size(sqSize, sqSize),
            )
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

      // 2. Draw stationary pieces first so moving pieces always render cleanly on top
      for (sq in 0 until 64) {
        if (sq == lastMoveTo || sq == castleRookPath?.toSq) continue
        val piece = boardState.board[sq]
        if (piece == PieceCode.EMPTY) continue

        val drawPos = squareTopLeft(sq, sqSize, isFlipped)
        if (sq == selectedSquare && selectionScaleBoost > 1.001f) {
          val center = Offset(drawPos.x + sqSize / 2f, drawPos.y + sqSize / 2f)
          withTransform({
            scale(scaleX = selectionScaleBoost, scaleY = selectionScaleBoost, pivot = center)
          }) {
            drawPiece(piece, drawPos, sqSize, pieceStyle)
          }
        } else {
          drawPiece(piece, drawPos, sqSize, pieceStyle)
        }
      }

      // 3. Draw castled rook in sync with the king if castling just occurred
      if (castleRookPath != null) {
        val rookPiece = boardState.board[castleRookPath.toSq]
        if (rookPiece != PieceCode.EMPTY) {
          val rookPos = if (shouldAnimateCurrentMove) {
            gridOffsetToTopLeft(animatedRookGridPos, sqSize, isFlipped)
          } else {
            squareTopLeft(castleRookPath.toSq, sqSize, isFlipped)
          }
          drawPiece(rookPiece, rookPos, sqSize, pieceStyle)
        }
      }

      // 4. Draw latest moved piece on top with spring placement & gentle flight lift
      if (lastMoveTo != null) {
        val movedPiece = boardState.board[lastMoveTo]
        if (movedPiece != PieceCode.EMPTY) {
          val drawPos = if (shouldAnimateCurrentMove) {
            gridOffsetToTopLeft(animatedPieceGridPos, sqSize, isFlipped)
          } else {
            squareTopLeft(lastMoveTo, sqSize, isFlipped)
          }
          val center = Offset(drawPos.x + sqSize / 2f, drawPos.y + sqSize / 2f)
          if (shadowAlpha > 0.01f) {
            drawCircle(
              color = Color.Black.copy(alpha = shadowAlpha),
              radius = sqSize * 0.34f,
              center = Offset(center.x, center.y + sqSize * 0.08f),
            )
          }
          val activeScale = when {
            shouldAnimateCurrentMove && liftScale > 1.001f -> liftScale
            lastMoveTo == selectedSquare && selectionScaleBoost > 1.001f -> selectionScaleBoost
            else -> 1f
          }
          if (activeScale > 1.001f) {
            withTransform({
              scale(scaleX = activeScale, scaleY = activeScale, pivot = center)
            }) {
              drawPiece(movedPiece, drawPos, sqSize, pieceStyle)
            }
          } else {
            drawPiece(movedPiece, drawPos, sqSize, pieceStyle)
          }
        }
      }

      // 5. Draw valid move circles reacting to check constraints (plus constraint ring when pinned/blocked)
      if (showLegalMoves && selectedSquare != null) {
        val isInCheck = kingInCheckSq != -1
        val indicatorScale = if (animateMoves) 0.55f + 0.45f * selectionEmphasis.coerceIn(0f, 1f) else 1f
        val indicatorAlpha = if (animateMoves) 0.35f + 0.65f * selectionEmphasis.coerceIn(0f, 1f) else 1f

        if (targetMovesForSelected.isNotEmpty()) {
          val quietColor = if (isInCheck) {
            Color(0xFFD97706).copy(alpha = 0.85f * indicatorAlpha)
          } else {
            Color(0xFF16A34A).copy(alpha = 0.62f * indicatorAlpha)
          }
          val quietHaloColor = if (isInCheck) {
            Color(0xFFFDE68A).copy(alpha = 0.42f * indicatorAlpha)
          } else {
            Color(0xFF16A34A).copy(alpha = 0.22f * indicatorAlpha)
          }
          val captureColor = if (isInCheck) {
            Color(0xFFEA580C).copy(alpha = 0.88f * indicatorAlpha)
          } else {
            Color(0xFF16A34A).copy(alpha = 0.72f * indicatorAlpha)
          }

          for ((destSq, move) in targetMovesForSelected) {
            val tl = squareTopLeft(destSq, sqSize, isFlipped)
            val center = Offset(tl.x + sqSize / 2f, tl.y + sqSize / 2f)
            val isCapture = move.capturedPiece != PieceCode.EMPTY || move.isEnPassant
            if (isCapture) {
              drawCircle(
                color = captureColor,
                radius = sqSize * 0.41f * indicatorScale,
                center = center,
                style = Stroke(width = (sqSize * 0.095f).coerceAtLeast(3f)),
              )
            } else {
              drawCircle(
                color = quietHaloColor,
                radius = sqSize * 0.22f * indicatorScale,
                center = center,
              )
              drawCircle(
                color = quietColor,
                radius = sqSize * 0.155f * indicatorScale,
                center = center,
              )
              if (isInCheck) {
                drawCircle(
                  color = Color(0xFF10B981).copy(alpha = 0.9f * indicatorAlpha),
                  radius = sqSize * 0.07f * indicatorScale,
                  center = center,
                )
              }
            }
          }
        } else if (selectedSquare in 0..63 &&
          PieceCode.colorOf(boardState.board[selectedSquare]) == boardState.turn
        ) {
          // Selected piece has zero legal moves (pinned or unable to resolve check)
          val tl = squareTopLeft(selectedSquare, sqSize, isFlipped)
          val center = Offset(tl.x + sqSize / 2f, tl.y + sqSize / 2f)
          drawCircle(
            color = Color(0xCCF43F5E).copy(alpha = 0.78f * indicatorAlpha),
            radius = sqSize * 0.43f * indicatorScale,
            center = center,
            style = Stroke(width = (sqSize * 0.075f).coerceAtLeast(2.5f)),
          )
        }
      }

      // 6. Draw Suggestion & Threat Arrows
      if (arrows.isNotEmpty()) {
        for (arrow in arrows) {
          if (arrow.fromSq !in 0..63 || arrow.toSq !in 0..63 || arrow.fromSq == arrow.toSq) continue
          val fromTl = squareTopLeft(arrow.fromSq, sqSize, isFlipped)
          val toTl = squareTopLeft(arrow.toSq, sqSize, isFlipped)
          val start = Offset(fromTl.x + sqSize / 2f, fromTl.y + sqSize / 2f)
          val end = Offset(toTl.x + sqSize / 2f, toTl.y + sqSize / 2f)
          val dx = end.x - start.x
          val dy = end.y - start.y
          val dist = hypot(dx, dy).coerceAtLeast(1f)
          val angle = atan2(dy, dx)
          val headLen = sqSize * 0.34f
          val shaftEnd = Offset(
            x = end.x - cos(angle) * (headLen * 0.65f),
            y = end.y - sin(angle) * (headLen * 0.65f),
          )
          val ux = dx / dist
          val uy = dy / dist
          val adjStart = Offset(start.x + ux * (sqSize * 0.14f), start.y + uy * (sqSize * 0.14f))
          drawLine(
            color = arrow.color,
            start = adjStart,
            end = shaftEnd,
            strokeWidth = (sqSize * arrow.strokeRatio).coerceAtLeast(4f),
            cap = StrokeCap.Round,
          )
          val wingAngle = 0.56f
          val leftWing = Offset(
            x = end.x - cos(angle - wingAngle) * headLen,
            y = end.y - sin(angle - wingAngle) * headLen,
          )
          val rightWing = Offset(
            x = end.x - cos(angle + wingAngle) * headLen,
            y = end.y - sin(angle + wingAngle) * headLen,
          )
          val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(end.x, end.y)
            lineTo(leftWing.x, leftWing.y)
            lineTo(rightWing.x, rightWing.y)
            close()
          }
          drawPath(path = path, color = arrow.color)
        }
      }
    }
  }
}

internal fun legalTargetMovesForSquare(
  selectedSquare: Int?,
  legalMoves: List<Move>,
): Map<Int, Move> {
  if (selectedSquare == null || selectedSquare !in 0..63) return emptyMap()
  return legalMoves.filter { it.from == selectedSquare }.associateBy { it.to }
}

internal fun resolveCastleRookPath(
  lastMoveFrom: Int?,
  lastMoveTo: Int?,
  boardState: BoardState,
): CastleRookPath? {
  if (lastMoveFrom == null || lastMoveTo == null) return null
  if (lastMoveTo !in 0..63 || lastMoveFrom !in 0..63) return null
  val movedPiece = boardState.board[lastMoveTo]
  if (PieceCode.typeOf(movedPiece) != PieceType.KING) return null
  val fromRank = SquareUtil.rankOf(lastMoveFrom)
  val toRank = SquareUtil.rankOf(lastMoveTo)
  if (fromRank != toRank) return null
  return when (SquareUtil.fileOf(lastMoveTo) - SquareUtil.fileOf(lastMoveFrom)) {
    2 -> CastleRookPath(
      fromSq = SquareUtil.of(7, fromRank),
      toSq = SquareUtil.of(5, fromRank),
    )
    -2 -> CastleRookPath(
      fromSq = SquareUtil.of(0, fromRank),
      toSq = SquareUtil.of(3, fromRank),
    )
    else -> null
  }
}

internal fun squareGridOffset(sq: Int): Offset =
  Offset(SquareUtil.fileOf(sq).toFloat(), SquareUtil.rankOf(sq).toFloat())

internal fun gridOffsetToTopLeft(gridPos: Offset, sqSize: Float, isFlipped: Boolean): Offset {
  val col = if (isFlipped) 7f - gridPos.x else gridPos.x
  val row = if (isFlipped) gridPos.y else 7f - gridPos.y
  return Offset(col * sqSize, row * sqSize)
}

private fun squareTopLeft(sq: Int, sqSize: Float, isFlipped: Boolean): Offset {
  val file = SquareUtil.fileOf(sq)
  val rank = SquareUtil.rankOf(sq)
  val col = if (isFlipped) 7 - file else file
  val row = if (isFlipped) rank else 7 - rank
  return Offset(col * sqSize, row * sqSize)
}
