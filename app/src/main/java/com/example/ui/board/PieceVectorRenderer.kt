package com.example.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.chess.PieceCode
import com.example.chess.PieceColor
import com.example.chess.PieceType

/**
 * Pure vector Canvas renderer for chess pieces.
 * Supports CLASSIC, MODERN, and MINIMAL styles with zero image/bitmap overhead.
 */
object PieceVectorRenderer {

  fun DrawScope.drawPiece(
    pieceCode: Int,
    topLeft: Offset,
    squareSize: Float,
    style: PieceStyleOption,
  ) {
    val color = PieceCode.colorOf(pieceCode) ?: return
    val type = PieceCode.typeOf(pieceCode) ?: return

    val fill = when (style) {
      PieceStyleOption.ROYAL_GOLD ->
        if (color == PieceColor.WHITE) Color(0xFFFFF8E7) else Color(0xFF1A1E29)
      PieceStyleOption.HIGH_CONTRAST ->
        if (color == PieceColor.WHITE) Color(0xFFFFFFFF) else Color(0xFF0B0E14)
      PieceStyleOption.OUTLINE ->
        if (color == PieceColor.WHITE) Color(0x55FFFFFF) else Color(0x88111827)
      else ->
        if (color == PieceColor.WHITE) Color(0xFFFDFBF7) else Color(0xFF1F242D)
    }
    val stroke = when (style) {
      PieceStyleOption.ROYAL_GOLD ->
        if (color == PieceColor.WHITE) Color(0xFF9A6B16) else Color(0xFFE9C46A)
      PieceStyleOption.HIGH_CONTRAST ->
        if (color == PieceColor.WHITE) Color(0xFF000000) else Color(0xFFF8FAFC)
      PieceStyleOption.OUTLINE ->
        if (color == PieceColor.WHITE) Color(0xFF1E293B) else Color(0xFFF1F5F9)
      else ->
        if (color == PieceColor.WHITE) Color(0xFF1F242D) else Color(0xFFE2E8F0)
    }
    val detail = when (style) {
      PieceStyleOption.ROYAL_GOLD ->
        if (color == PieceColor.WHITE) Color(0xFFB48124) else Color(0xFFF6D98B)
      PieceStyleOption.HIGH_CONTRAST ->
        if (color == PieceColor.WHITE) Color(0xFF0F172A) else Color(0xFFFFFFFF)
      else ->
        if (color == PieceColor.WHITE) Color(0xFF334155) else Color(0xFFCBD5E1)
    }
    val strokeFactor = when (style) {
      PieceStyleOption.MODERN -> 0.036f
      PieceStyleOption.HIGH_CONTRAST, PieceStyleOption.OUTLINE -> 0.062f
      else -> 0.044f
    }
    val strokeW = (squareSize * strokeFactor).coerceAtLeast(1.2f)

    if (style == PieceStyleOption.MINIMAL) {
      drawMinimalPiece(type, color, topLeft, squareSize, fill, stroke, detail, strokeW)
      return
    }

    val pad = squareSize * 0.12f
    val s = squareSize - pad * 2f
    val ox = topLeft.x + pad
    val oy = topLeft.y + pad
    val showDetails = style == PieceStyleOption.CLASSIC ||
      style == PieceStyleOption.ROYAL_GOLD ||
      style == PieceStyleOption.HIGH_CONTRAST

    when (type) {
      PieceType.PAWN -> drawPawn(ox, oy, s, fill, stroke, strokeW, showDetails)
      PieceType.ROOK -> drawRook(ox, oy, s, fill, stroke, detail, strokeW, showDetails)
      PieceType.KNIGHT -> drawKnight(ox, oy, s, fill, stroke, detail, strokeW, showDetails)
      PieceType.BISHOP -> drawBishop(ox, oy, s, fill, stroke, detail, strokeW, showDetails)
      PieceType.QUEEN -> drawQueen(ox, oy, s, fill, stroke, detail, strokeW, showDetails)
      PieceType.KING -> drawKing(ox, oy, s, fill, stroke, detail, strokeW, showDetails)
    }
  }

  private fun DrawScope.drawMinimalPiece(
    type: PieceType,
    color: PieceColor,
    topLeft: Offset,
    sq: Float,
    fill: Color,
    stroke: Color,
    detail: Color,
    strokeW: Float,
  ) {
    val cx = topLeft.x + sq * 0.5f
    val cy = topLeft.y + sq * 0.5f
    val r = sq * 0.36f
    drawCircle(color = fill, radius = r, center = Offset(cx, cy))
    drawCircle(color = stroke, radius = r, center = Offset(cx, cy), style = Stroke(width = strokeW * 1.1f))
    val innerR = r * 0.56f
    when (type) {
      PieceType.PAWN -> drawCircle(color = detail, radius = innerR * 0.45f, center = Offset(cx, cy))
      PieceType.ROOK -> drawRect(
        color = detail,
        topLeft = Offset(cx - innerR * 0.65f, cy - innerR * 0.65f),
        size = Size(innerR * 1.3f, innerR * 1.3f),
        style = Stroke(width = strokeW),
      )
      PieceType.BISHOP -> {
        val p = Path().apply {
          moveTo(cx, cy - innerR * 0.85f)
          lineTo(cx + innerR * 0.7f, cy + innerR * 0.65f)
          lineTo(cx - innerR * 0.7f, cy + innerR * 0.65f)
          close()
        }
        drawPath(p, color = detail, style = Stroke(width = strokeW, join = StrokeJoin.Round))
      }
      PieceType.KNIGHT -> {
        val p = Path().apply {
          moveTo(cx - innerR * 0.55f, cy + innerR * 0.65f)
          lineTo(cx - innerR * 0.35f, cy - innerR * 0.35f)
          lineTo(cx + innerR * 0.55f, cy - innerR * 0.65f)
          lineTo(cx + innerR * 0.3f, cy + innerR * 0.65f)
          close()
        }
        drawPath(p, color = detail, style = Stroke(width = strokeW, join = StrokeJoin.Round))
      }
      PieceType.QUEEN -> {
        val p = Path().apply {
          moveTo(cx, cy - innerR * 0.85f)
          lineTo(cx + innerR * 0.8f, cy)
          lineTo(cx, cy + innerR * 0.85f)
          lineTo(cx - innerR * 0.8f, cy)
          close()
        }
        drawPath(p, color = if (color == PieceColor.WHITE) Color(0xFFD4A853) else detail, style = Fill)
      }
      PieceType.KING -> {
        drawLine(detail, Offset(cx, cy - innerR * 0.85f), Offset(cx, cy + innerR * 0.85f), strokeWidth = strokeW * 1.3f, cap = StrokeCap.Round)
        drawLine(detail, Offset(cx - innerR * 0.7f, cy - innerR * 0.1f), Offset(cx + innerR * 0.7f, cy - innerR * 0.1f), strokeWidth = strokeW * 1.3f, cap = StrokeCap.Round)
      }
    }
  }

  private fun DrawScope.drawPawn(
    ox: Float, oy: Float, s: Float, fill: Color, stroke: Color, sw: Float, classic: Boolean,
  ) {
    val body = Path().apply {
      moveTo(ox + s * 0.22f, oy + s * 0.92f)
      lineTo(ox + s * 0.78f, oy + s * 0.92f)
      lineTo(ox + s * 0.72f, oy + s * 0.78f)
      lineTo(ox + s * 0.58f, oy + s * 0.44f)
      lineTo(ox + s * 0.42f, oy + s * 0.44f)
      lineTo(ox + s * 0.28f, oy + s * 0.78f)
      close()
    }
    drawPath(body, fill)
    drawPath(body, stroke, style = Stroke(sw, join = StrokeJoin.Round))
    drawCircle(fill, radius = s * 0.17f, center = Offset(ox + s * 0.5f, oy + s * 0.29f))
    drawCircle(stroke, radius = s * 0.17f, center = Offset(ox + s * 0.5f, oy + s * 0.29f), style = Stroke(sw))
    if (classic) {
      drawLine(stroke, Offset(ox + s * 0.34f, oy + s * 0.46f), Offset(ox + s * 0.66f, oy + s * 0.46f), sw, StrokeCap.Round)
    }
  }

  private fun DrawScope.drawRook(
    ox: Float, oy: Float, s: Float, fill: Color, stroke: Color, detail: Color, sw: Float, classic: Boolean,
  ) {
    val path = Path().apply {
      moveTo(ox + s * 0.18f, oy + s * 0.92f)
      lineTo(ox + s * 0.82f, oy + s * 0.92f)
      lineTo(ox + s * 0.76f, oy + s * 0.78f)
      lineTo(ox + s * 0.70f, oy + s * 0.35f)
      lineTo(ox + s * 0.78f, oy + s * 0.27f)
      lineTo(ox + s * 0.78f, oy + s * 0.10f)
      lineTo(ox + s * 0.64f, oy + s * 0.10f)
      lineTo(ox + s * 0.64f, oy + s * 0.20f)
      lineTo(ox + s * 0.55f, oy + s * 0.20f)
      lineTo(ox + s * 0.55f, oy + s * 0.10f)
      lineTo(ox + s * 0.45f, oy + s * 0.10f)
      lineTo(ox + s * 0.45f, oy + s * 0.20f)
      lineTo(ox + s * 0.36f, oy + s * 0.20f)
      lineTo(ox + s * 0.36f, oy + s * 0.10f)
      lineTo(ox + s * 0.22f, oy + s * 0.10f)
      lineTo(ox + s * 0.22f, oy + s * 0.27f)
      lineTo(ox + s * 0.30f, oy + s * 0.35f)
      lineTo(ox + s * 0.24f, oy + s * 0.78f)
      close()
    }
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(sw, join = StrokeJoin.Round))
    if (classic) {
      drawLine(detail, Offset(ox + s * 0.28f, oy + s * 0.78f), Offset(ox + s * 0.72f, oy + s * 0.78f), sw * 0.9f)
      drawLine(detail, Offset(ox + s * 0.32f, oy + s * 0.35f), Offset(ox + s * 0.68f, oy + s * 0.35f), sw * 0.9f)
    }
  }

  private fun DrawScope.drawKnight(
    ox: Float, oy: Float, s: Float, fill: Color, stroke: Color, detail: Color, sw: Float, classic: Boolean,
  ) {
    val path = Path().apply {
      moveTo(ox + s * 0.20f, oy + s * 0.92f)
      lineTo(ox + s * 0.80f, oy + s * 0.92f)
      lineTo(ox + s * 0.76f, oy + s * 0.76f)
      cubicTo(
        ox + s * 0.78f, oy + s * 0.42f,
        ox + s * 0.68f, oy + s * 0.18f,
        ox + s * 0.46f, oy + s * 0.14f,
      )
      lineTo(ox + s * 0.38f, oy + s * 0.08f)
      lineTo(ox + s * 0.35f, oy + s * 0.20f)
      lineTo(ox + s * 0.16f, oy + s * 0.42f)
      lineTo(ox + s * 0.24f, oy + s * 0.54f)
      lineTo(ox + s * 0.46f, oy + s * 0.45f)
      cubicTo(
        ox + s * 0.44f, oy + s * 0.60f,
        ox + s * 0.32f, oy + s * 0.68f,
        ox + s * 0.24f, oy + s * 0.78f,
      )
      close()
    }
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(sw, join = StrokeJoin.Round))
    drawCircle(detail, radius = s * 0.038f, center = Offset(ox + s * 0.36f, oy + s * 0.30f))
    if (classic) {
      drawLine(detail, Offset(ox + s * 0.26f, oy + s * 0.80f), Offset(ox + s * 0.74f, oy + s * 0.80f), sw * 0.85f)
    }
  }

  private fun DrawScope.drawBishop(
    ox: Float, oy: Float, s: Float, fill: Color, stroke: Color, detail: Color, sw: Float, classic: Boolean,
  ) {
    val path = Path().apply {
      moveTo(ox + s * 0.20f, oy + s * 0.92f)
      lineTo(ox + s * 0.80f, oy + s * 0.92f)
      lineTo(ox + s * 0.72f, oy + s * 0.78f)
      cubicTo(
        ox + s * 0.72f, oy + s * 0.48f,
        ox + s * 0.64f, oy + s * 0.26f,
        ox + s * 0.50f, oy + s * 0.16f,
      )
      cubicTo(
        ox + s * 0.36f, oy + s * 0.26f,
        ox + s * 0.28f, oy + s * 0.48f,
        ox + s * 0.28f, oy + s * 0.78f,
      )
      close()
    }
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(sw, join = StrokeJoin.Round))
    drawCircle(fill, radius = s * 0.065f, center = Offset(ox + s * 0.50f, oy + s * 0.11f))
    drawCircle(stroke, radius = s * 0.065f, center = Offset(ox + s * 0.50f, oy + s * 0.11f), style = Stroke(sw))
    if (classic) {
      drawLine(detail, Offset(ox + s * 0.42f, oy + s * 0.42f), Offset(ox + s * 0.58f, oy + s * 0.56f), sw, StrokeCap.Round)
      drawLine(detail, Offset(ox + s * 0.50f, oy + s * 0.38f), Offset(ox + s * 0.50f, oy + s * 0.60f), sw, StrokeCap.Round)
    }
  }

  private fun DrawScope.drawQueen(
    ox: Float, oy: Float, s: Float, fill: Color, stroke: Color, detail: Color, sw: Float, classic: Boolean,
  ) {
    val path = Path().apply {
      moveTo(ox + s * 0.20f, oy + s * 0.92f)
      lineTo(ox + s * 0.80f, oy + s * 0.92f)
      lineTo(ox + s * 0.75f, oy + s * 0.76f)
      lineTo(ox + s * 0.86f, oy + s * 0.26f)
      lineTo(ox + s * 0.66f, oy + s * 0.46f)
      lineTo(ox + s * 0.63f, oy + s * 0.18f)
      lineTo(ox + s * 0.50f, oy + s * 0.42f)
      lineTo(ox + s * 0.37f, oy + s * 0.18f)
      lineTo(ox + s * 0.34f, oy + s * 0.46f)
      lineTo(ox + s * 0.14f, oy + s * 0.26f)
      lineTo(ox + s * 0.25f, oy + s * 0.76f)
      close()
    }
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(sw, join = StrokeJoin.Round))
    val tips = listOf(
      Offset(ox + s * 0.14f, oy + s * 0.23f),
      Offset(ox + s * 0.37f, oy + s * 0.15f),
      Offset(ox + s * 0.63f, oy + s * 0.15f),
      Offset(ox + s * 0.86f, oy + s * 0.23f),
    )
    tips.forEach { t ->
      drawCircle(fill, radius = s * 0.045f, center = t)
      drawCircle(stroke, radius = s * 0.045f, center = t, style = Stroke(sw * 0.85f))
    }
    if (classic) {
      drawLine(detail, Offset(ox + s * 0.26f, oy + s * 0.76f), Offset(ox + s * 0.74f, oy + s * 0.76f), sw)
    }
  }

  private fun DrawScope.drawKing(
    ox: Float, oy: Float, s: Float, fill: Color, stroke: Color, detail: Color, sw: Float, classic: Boolean,
  ) {
    // Cross
    drawLine(stroke, Offset(ox + s * 0.50f, oy + s * 0.06f), Offset(ox + s * 0.50f, oy + s * 0.26f), sw * 1.3f, StrokeCap.Round)
    drawLine(stroke, Offset(ox + s * 0.40f, oy + s * 0.14f), Offset(ox + s * 0.60f, oy + s * 0.14f), sw * 1.3f, StrokeCap.Round)

    val crown = Path().apply {
      moveTo(ox + s * 0.22f, oy + s * 0.92f)
      lineTo(ox + s * 0.78f, oy + s * 0.92f)
      lineTo(ox + s * 0.74f, oy + s * 0.76f)
      cubicTo(
        ox + s * 0.90f, oy + s * 0.44f,
        ox + s * 0.76f, oy + s * 0.26f,
        ox + s * 0.50f, oy + s * 0.36f,
      )
      cubicTo(
        ox + s * 0.24f, oy + s * 0.26f,
        ox + s * 0.10f, oy + s * 0.44f,
        ox + s * 0.26f, oy + s * 0.76f,
      )
      close()
    }
    drawPath(crown, fill)
    drawPath(crown, stroke, style = Stroke(sw, join = StrokeJoin.Round))
    if (classic) {
      drawArc(
        color = detail,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(ox + s * 0.30f, oy + s * 0.68f),
        size = Size(s * 0.40f, s * 0.18f),
        style = Stroke(sw * 0.9f),
      )
    }
  }
}
