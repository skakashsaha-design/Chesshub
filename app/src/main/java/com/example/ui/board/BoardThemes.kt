package com.example.ui.board

import androidx.compose.ui.graphics.Color

enum class BoardThemeOption(
  val key: String,
  val displayName: String,
  val lightSquare: Color,
  val darkSquare: Color,
  val lastMoveTint: Color,
  val selectedTint: Color,
  val coordLight: Color,
  val coordDark: Color,
) {
  WALNUT(
    key = "WALNUT",
    displayName = "Walnut",
    lightSquare = Color(0xFFECD9B9),
    darkSquare = Color(0xFFAE7C52),
    lastMoveTint = Color(0x66EAB308),
    selectedTint = Color(0x772E9E6B),
    coordLight = Color(0xFF7A5230),
    coordDark = Color(0xFFF5E6CF),
  ),
  TOURNAMENT(
    key = "TOURNAMENT",
    displayName = "Tournament",
    lightSquare = Color(0xFFEEEED2),
    darkSquare = Color(0xFF769656),
    lastMoveTint = Color(0x66FACC15),
    selectedTint = Color(0x7716A34A),
    coordLight = Color(0xFF4B6435),
    coordDark = Color(0xFFF5F5E4),
  ),
  SLATE(
    key = "SLATE",
    displayName = "Slate",
    lightSquare = Color(0xFFD6DCE5),
    darkSquare = Color(0xFF66758A),
    lastMoveTint = Color(0x6638BDF8),
    selectedTint = Color(0x7722C55E),
    coordLight = Color(0xFF475569),
    coordDark = Color(0xFFE2E8F0),
  ),
  NORDIC(
    key = "NORDIC",
    displayName = "Nordic",
    lightSquare = Color(0xFFE4ECF4),
    darkSquare = Color(0xFF4C6A88),
    lastMoveTint = Color(0x66F59E0B),
    selectedTint = Color(0x7710B981),
    coordLight = Color(0xFF334D66),
    coordDark = Color(0xFFF1F5F9),
  ),
  OBSIDIAN(
    key = "OBSIDIAN",
    displayName = "Obsidian Gold",
    lightSquare = Color(0xFF39414E),
    darkSquare = Color(0xFF1E242E),
    lastMoveTint = Color(0x66D4A853),
    selectedTint = Color(0x772E9E6B),
    coordLight = Color(0xFFCBD5E1),
    coordDark = Color(0xFFE9C46A),
  ),
  ROSEWOOD(
    key = "ROSEWOOD",
    displayName = "Rosewood",
    lightSquare = Color(0xFFF3DFD2),
    darkSquare = Color(0xFF8C4446),
    lastMoveTint = Color(0x66FBBF24),
    selectedTint = Color(0x7734D399),
    coordLight = Color(0xFF6B2D2F),
    coordDark = Color(0xFFFAF0EB),
  ),
  OCEAN(
    key = "OCEAN",
    displayName = "Ocean Teal",
    lightSquare = Color(0xFFD9ECEF),
    darkSquare = Color(0xFF3A7D8C),
    lastMoveTint = Color(0x66F59E0B),
    selectedTint = Color(0x7714B8A6),
    coordLight = Color(0xFF235663),
    coordDark = Color(0xFFF0FAFB),
  ),
  SANDSTONE(
    key = "SANDSTONE",
    displayName = "Sandstone",
    lightSquare = Color(0xFFF4EAD5),
    darkSquare = Color(0xFFC29B61),
    lastMoveTint = Color(0x66EA580C),
    selectedTint = Color(0x7716A34A),
    coordLight = Color(0xFF7D5D31),
    coordDark = Color(0xFFFDF8EE),
  );

  companion object {
    fun fromKey(key: String): BoardThemeOption =
      entries.firstOrNull { it.key == key } ?: WALNUT
  }
}

enum class PieceStyleOption(val key: String, val displayName: String) {
  CLASSIC("CLASSIC", "Classic"),
  MODERN("MODERN", "Modern"),
  MINIMAL("MINIMAL", "Minimal"),
  ROYAL_GOLD("ROYAL_GOLD", "Royal Gold"),
  HIGH_CONTRAST("HIGH_CONTRAST", "Bold Contrast"),
  OUTLINE("OUTLINE", "Outline");

  companion object {
    fun fromKey(key: String): PieceStyleOption =
      entries.firstOrNull { it.key == key } ?: CLASSIC
  }
}
