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
  NORDIC(
    key = "NORDIC",
    displayName = "Nordic",
    lightSquare = Color(0xFFE4ECF4),
    darkSquare = Color(0xFF4C6A88),
    lastMoveTint = Color(0x66F59E0B),
    selectedTint = Color(0x7710B981),
    coordLight = Color(0xFF334D66),
    coordDark = Color(0xFFF1F5F9),
  );

  companion object {
    fun fromKey(key: String): BoardThemeOption =
      entries.firstOrNull { it.key == key } ?: WALNUT
  }
}

enum class PieceStyleOption(val key: String, val displayName: String) {
  CLASSIC("CLASSIC", "Classic"),
  MODERN("MODERN", "Modern"),
  MINIMAL("MINIMAL", "Minimal");

  companion object {
    fun fromKey(key: String): PieceStyleOption =
      entries.firstOrNull { it.key == key } ?: CLASSIC
  }
}
