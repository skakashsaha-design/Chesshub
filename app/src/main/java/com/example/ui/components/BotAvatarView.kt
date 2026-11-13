package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.BotProfile

/**
 * Custom lightweight Canvas avatar / PFP renderer giving every bot its own
 * unmistakable geometric portrait, gradient aura, and chess-piece motif.
 */
@Composable
fun BotAvatarView(
  bot: BotProfile,
  size: Dp = 48.dp,
  modifier: Modifier = Modifier,
) {
  val primary = Color(bot.emblemColorHex)
  val secondary = Color(bot.secondaryColorHex)
  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.matchParentSize()) {
      val r = this.size.minDimension / 2f
      val center = Offset(this.size.width / 2f, this.size.height / 2f)

      // Outer gradient aura medallion
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(primary.copy(alpha = 0.92f), secondary.copy(alpha = 0.95f)),
          center = Offset(center.x - r * 0.25f, center.y - r * 0.25f),
          radius = r * 1.25f,
        ),
        radius = r,
        center = center,
      )
      // Outer metallic accent ring
      drawCircle(
        color = Color.White.copy(alpha = 0.38f),
        radius = r * 0.92f,
        center = center,
        style = Stroke(width = r * 0.09f),
      )

      // Unique inner geometric pattern by bot iconType
      when (bot.iconType) {
        "spark", "nova" -> {
          val star = Path().apply {
            val outer = r * 0.54f
            val inner = r * 0.2f
            for (i in 0 until 8) {
              val ang = Math.toRadians((i * 45.0) - 90.0)
              val rad = if (i % 2 == 0) outer else inner
              val px = center.x + (rad * kotlin.math.cos(ang)).toFloat()
              val py = center.y + (rad * kotlin.math.sin(ang)).toFloat()
              if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
          }
          drawPath(star, color = Color.White.copy(alpha = 0.88f))
        }
        "pawn" -> {
          drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.24f, center = Offset(center.x, center.y - r * 0.18f))
          val base = Path().apply {
            moveTo(center.x - r * 0.34f, center.y + r * 0.42f)
            lineTo(center.x + r * 0.34f, center.y + r * 0.42f)
            lineTo(center.x + r * 0.14f, center.y - r * 0.02f)
            lineTo(center.x - r * 0.14f, center.y - r * 0.02f)
            close()
          }
          drawPath(base, color = Color.White.copy(alpha = 0.9f))
        }
        "rook" -> {
          val tower = Path().apply {
            moveTo(center.x - r * 0.32f, center.y + r * 0.42f)
            lineTo(center.x + r * 0.32f, center.y + r * 0.42f)
            lineTo(center.x + r * 0.26f, center.y - r * 0.24f)
            lineTo(center.x - r * 0.26f, center.y - r * 0.24f)
            close()
          }
          drawPath(tower, color = Color.White.copy(alpha = 0.9f))
          drawLine(Color.White, Offset(center.x - r * 0.32f, center.y - r * 0.3f), Offset(center.x + r * 0.32f, center.y - r * 0.3f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
        }
        "bishop" -> {
          val mitre = Path().apply {
            moveTo(center.x, center.y - r * 0.46f)
            lineTo(center.x + r * 0.3f, center.y + r * 0.24f)
            lineTo(center.x, center.y + r * 0.42f)
            lineTo(center.x - r * 0.3f, center.y + r * 0.24f)
            close()
          }
          drawPath(mitre, color = Color.White.copy(alpha = 0.9f))
        }
        "knight" -> {
          val head = Path().apply {
            moveTo(center.x - r * 0.28f, center.y + r * 0.42f)
            lineTo(center.x - r * 0.24f, center.y - r * 0.22f)
            lineTo(center.x + r * 0.04f, center.y - r * 0.46f)
            lineTo(center.x + r * 0.34f, center.y - r * 0.14f)
            lineTo(center.x + r * 0.12f, center.y + r * 0.04f)
            lineTo(center.x + r * 0.28f, center.y + r * 0.42f)
            close()
          }
          drawPath(head, color = Color.White.copy(alpha = 0.9f))
        }
        "shield" -> {
          val shield = Path().apply {
            moveTo(center.x - r * 0.36f, center.y - r * 0.32f)
            lineTo(center.x + r * 0.36f, center.y - r * 0.32f)
            lineTo(center.x + r * 0.32f, center.y + r * 0.08f)
            lineTo(center.x, center.y + r * 0.46f)
            lineTo(center.x - r * 0.32f, center.y + r * 0.08f)
            close()
          }
          drawPath(shield, color = Color.White.copy(alpha = 0.9f))
        }
        "crown", "master", "apex", "omega" -> {
          val crown = Path().apply {
            moveTo(center.x - r * 0.4f, center.y + r * 0.3f)
            lineTo(center.x - r * 0.44f, center.y - r * 0.2f)
            lineTo(center.x - r * 0.18f, center.y + r * 0.04f)
            lineTo(center.x, center.y - r * 0.36f)
            lineTo(center.x + r * 0.18f, center.y + r * 0.04f)
            lineTo(center.x + r * 0.44f, center.y - r * 0.2f)
            lineTo(center.x + r * 0.4f, center.y + r * 0.3f)
            close()
          }
          drawPath(crown, color = Color.White.copy(alpha = 0.94f))
        }
        else -> {
          // Custom bot hexagon core
          val hex = Path().apply {
            for (i in 0 until 6) {
              val ang = Math.toRadians(i * 60.0)
              val px = center.x + (r * 0.45f * kotlin.math.cos(ang)).toFloat()
              val py = center.y + (r * 0.45f * kotlin.math.sin(ang)).toFloat()
              if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
          }
          drawPath(hex, color = Color.White.copy(alpha = 0.85f), style = Stroke(width = r * 0.12f))
        }
      }
    }
    if (bot.id == "bot_omega") {
      Text(
        text = "Ω",
        fontSize = (size.value * 0.22f).sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(bot.emblemColorHex),
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}
