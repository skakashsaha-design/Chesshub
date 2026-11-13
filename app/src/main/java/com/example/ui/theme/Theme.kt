package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
  primary = EmeraldPrimaryDark,
  onPrimary = Color(0xFF072114),
  primaryContainer = Color(0xFF153D29),
  onPrimaryContainer = Color(0xFFB7F4D5),
  secondary = GoldAccentDark,
  onSecondary = Color(0xFF281D00),
  secondaryContainer = Color(0xFF3A2E14),
  onSecondaryContainer = Color(0xFFFBE2A2),
  tertiary = SlateSecondary,
  onTertiary = Color.White,
  background = ObsidianBg,
  onBackground = TextOnDark,
  surface = ObsidianSurface,
  onSurface = TextOnDark,
  surfaceVariant = ObsidianSurfaceVariant,
  onSurfaceVariant = TextSecondaryDark,
  outline = ObsidianOutline,
  error = CoralDangerDark,
  onError = Color(0xFF2B0707),
)

private val LightColorScheme = lightColorScheme(
  primary = EmeraldPrimary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFD2F4E3),
  onPrimaryContainer = Color(0xFF0A3320),
  secondary = GoldAccent,
  onSecondary = Color(0xFF241A00),
  secondaryContainer = Color(0xFFF6E7C1),
  onSecondaryContainer = Color(0xFF332500),
  tertiary = SlateSecondary,
  onTertiary = Color.White,
  background = IvoryBg,
  onBackground = TextOnLight,
  surface = IvorySurface,
  onSurface = TextOnLight,
  surfaceVariant = IvorySurfaceVariant,
  onSurfaceVariant = TextSecondaryLight,
  outline = IvoryOutline,
  error = CoralDanger,
  onError = Color.White,
)

val ChessHubShapes = Shapes(
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(12.dp),
  large = RoundedCornerShape(16.dp),
)

@Composable
fun ChessHubTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = ChessHubTypography,
    shapes = ChessHubShapes,
    content = content,
  )
}
