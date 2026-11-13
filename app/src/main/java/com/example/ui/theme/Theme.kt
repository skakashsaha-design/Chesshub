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

enum class AppAccentOption(
  val key: String,
  val displayName: String,
  val lightPrimary: Color,
  val lightContainer: Color,
  val darkPrimary: Color,
  val darkContainer: Color,
) {
  EMERALD(
    key = "EMERALD",
    displayName = "Emerald",
    lightPrimary = EmeraldPrimary,
    lightContainer = Color(0xFFD2F4E3),
    darkPrimary = EmeraldPrimaryDark,
    darkContainer = Color(0xFF153D29),
  ),
  COBALT(
    key = "COBALT",
    displayName = "Cobalt",
    lightPrimary = Color(0xFF2563EB),
    lightContainer = Color(0xFFDBEAFE),
    darkPrimary = Color(0xFF60A5FA),
    darkContainer = Color(0xFF1E3A5F),
  ),
  AMBER(
    key = "AMBER",
    displayName = "Amber Gold",
    lightPrimary = Color(0xFFB45309),
    lightContainer = Color(0xFFFEF3C7),
    darkPrimary = Color(0xFFFBBF24),
    darkContainer = Color(0xFF452D08),
  ),
  CRIMSON(
    key = "CRIMSON",
    displayName = "Crimson",
    lightPrimary = Color(0xFFBE123C),
    lightContainer = Color(0xFFFFE4E6),
    darkPrimary = Color(0xFFFB7185),
    darkContainer = Color(0xFF4C1121),
  ),
  VIOLET(
    key = "VIOLET",
    displayName = "Royal Violet",
    lightPrimary = Color(0xFF6D28D9),
    lightContainer = Color(0xFFEDE9FE),
    darkPrimary = Color(0xFFA78BFA),
    darkContainer = Color(0xFF2E1E59),
  );

  companion object {
    fun fromKey(key: String): AppAccentOption =
      entries.firstOrNull { it.key == key } ?: EMERALD
  }
}

val ChessHubShapes = Shapes(
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(12.dp),
  large = RoundedCornerShape(16.dp),
)

@Composable
fun ChessHubTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  accentKey: String = "EMERALD",
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val accent = AppAccentOption.fromKey(accentKey)

  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> darkColorScheme(
      primary = accent.darkPrimary,
      onPrimary = Color(0xFF071612),
      primaryContainer = accent.darkContainer,
      onPrimaryContainer = Color(0xFFEAF5FF),
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
    else -> lightColorScheme(
      primary = accent.lightPrimary,
      onPrimary = Color.White,
      primaryContainer = accent.lightContainer,
      onPrimaryContainer = Color(0xFF0A221A),
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
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = ChessHubTypography,
    shapes = ChessHubShapes,
    content = content,
  )
}
