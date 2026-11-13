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
