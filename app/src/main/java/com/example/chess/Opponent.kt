package com.example.chess

enum class BotDifficulty(val label: String) {
  BEGINNER("Beginner"),
  EASY("Easy"),
  NORMAL("Normal"),
  ADVANCED("Advanced"),
  EXPERT("Expert"),
  MASTER("Master"),
  CUSTOM("Custom"),
}

enum class BotPlayingStyle(val label: String, val summary: String) {
  BALANCED("Balanced", "All-round tactical and positional play"),
  AGGRESSIVE("Aggressive", "Favors attacks, checks, and open kingside lines"),
  SOLID("Solid", "Prioritizes king safety, structure, and clean trades"),
  TACTICAL("Tactical", "Hunts forks, pins, sacrifices, and sharp complications"),
  POSITIONAL("Positional", "Seeks outposts, center control, and space"),
  ENDGAME_SPECIALIST("Endgame", "Steers toward favorable simplified endings"),
}

data class BotProfile(
  val id: String,
  val name: String,
  val rating: Int,
  val difficulty: BotDifficulty,
  val style: String,
  val maxDepth: Int,
  val maxThinkTimeMs: Long,
  val randomnessCp: Int,
  val emblemColorHex: Long,
  val iconType: String,
  val playingStyle: BotPlayingStyle = BotPlayingStyle.BALANCED,
  val secondaryColorHex: Long = 0xFF1E293B,
  val nationality: String = "Global",
  val favoriteOpening: String = "Italian Game (C50)",
  val catchphrase: String = "Every move tells a story.",
  val bio: String = "Club-calibrated engine sparring partner built for fair progression.",
)

/**
 * Common opponent abstraction shared across Human vs Human,
 * Human vs Bot, Bot vs Bot, and Online vs Player modes.
 */
sealed interface Opponent {
  val displayName: String

  data class Human(override val displayName: String) : Opponent
  data class Bot(val profile: BotProfile) : Opponent {
    override val displayName: String get() = profile.name
  }
  data class OnlinePlayer(
    val playerId: String,
    override val displayName: String,
    val rating: Int?,
  ) : Opponent
}
