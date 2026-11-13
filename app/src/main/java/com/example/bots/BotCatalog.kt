package com.example.bots

import com.example.chess.BotDifficulty
import com.example.chess.BotProfile

object BotCatalog {

  val presets: List<BotProfile> = listOf(
    BotProfile(
      id = "bot_nova",
      name = "Nova",
      rating = 650,
      difficulty = BotDifficulty.BEGINNER,
      style = "Casual Explorer • Frequent inaccuracies, friendly pace",
      maxDepth = 1,
      maxThinkTimeMs = 200L,
      randomnessCp = 240,
      emblemColorHex = 0xFF38BDF8,
      iconType = "spark",
    ),
    BotProfile(
      id = "bot_rook",
      name = "Rook",
      rating = 950,
      difficulty = BotDifficulty.EASY,
      style = "Solid Defender • Prefers straightforward trades",
      maxDepth = 2,
      maxThinkTimeMs = 300L,
      randomnessCp = 120,
      emblemColorHex = 0xFF34D399,
      iconType = "rook",
    ),
    BotProfile(
      id = "bot_bishop",
      name = "Bishop",
      rating = 1250,
      difficulty = BotDifficulty.NORMAL,
      style = "Diagonal Tactician • Punishes hanging pieces",
      maxDepth = 2,
      maxThinkTimeMs = 450L,
      randomnessCp = 55,
      emblemColorHex = 0xFFFBBF24,
      iconType = "bishop",
    ),
    BotProfile(
      id = "bot_knight",
      name = "Knight",
      rating = 1550,
      difficulty = BotDifficulty.ADVANCED,
      style = "Fork Specialist • Sharp tactical vision",
      maxDepth = 3,
      maxThinkTimeMs = 700L,
      randomnessCp = 25,
      emblemColorHex = 0xFFA78BFA,
      iconType = "knight",
    ),
    BotProfile(
      id = "bot_titan",
      name = "Titan",
      rating = 1850,
      difficulty = BotDifficulty.EXPERT,
      style = "Positional Crusher • Consistent pressure",
      maxDepth = 4,
      maxThinkTimeMs = 1200L,
      randomnessCp = 8,
      emblemColorHex = 0xFFF97316,
      iconType = "shield",
    ),
    BotProfile(
      id = "bot_master",
      name = "Master",
      rating = 2100,
      difficulty = BotDifficulty.MASTER,
      style = "Grand Strategist • Maximum offline strength",
      maxDepth = 5,
      maxThinkTimeMs = 2000L,
      randomnessCp = 0,
      emblemColorHex = 0xFFE11D48,
      iconType = "crown",
    ),
  )

  fun customBot(depth: Int, randomnessCp: Int, thinkTimeMs: Long): BotProfile {
    val estimatedRating = (600 + depth * 280 - (randomnessCp * 1.5f).toInt()).coerceIn(500, 2200)
    return BotProfile(
      id = "bot_custom",
      name = "Custom Bot",
      rating = estimatedRating,
      difficulty = BotDifficulty.CUSTOM,
      style = "Depth $depth • Margin ${randomnessCp}cp • ${thinkTimeMs}ms",
      maxDepth = depth.coerceIn(1, 5),
      maxThinkTimeMs = thinkTimeMs.coerceIn(150L, 2500L),
      randomnessCp = randomnessCp.coerceIn(0, 300),
      emblemColorHex = 0xFF2DD4BF,
      iconType = "tune",
    )
  }

  fun findById(id: String): BotProfile = presets.firstOrNull { it.id == id } ?: presets[1]
}
