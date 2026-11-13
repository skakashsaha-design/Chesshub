package com.example.bots

import com.example.chess.BotDifficulty
import com.example.chess.BotPlayingStyle
import com.example.chess.BotProfile
import kotlin.math.roundToInt

object BotCatalog {

  const val MIN_ELO = 200
  const val MAX_ELO = 3200

  val presets: List<BotProfile> = listOf(
    BotProfile(
      id = "bot_spark",
      name = "Spark",
      rating = 200,
      difficulty = BotDifficulty.BEGINNER,
      style = "First Steps • Very forgiving, overlooks simple tactics",
      maxDepth = 1,
      maxThinkTimeMs = 150L,
      randomnessCp = 420,
      emblemColorHex = 0xFF94A3B8,
      iconType = "spark",
      playingStyle = BotPlayingStyle.BALANCED,
    ),
    BotProfile(
      id = "bot_pawnling",
      name = "Pawnling",
      rating = 400,
      difficulty = BotDifficulty.BEGINNER,
      style = "Apprentice • Basic captures, playful aggressive pushes",
      maxDepth = 1,
      maxThinkTimeMs = 180L,
      randomnessCp = 320,
      emblemColorHex = 0xFF60A5FA,
      iconType = "pawn",
      playingStyle = BotPlayingStyle.AGGRESSIVE,
    ),
    BotProfile(
      id = "bot_nova",
      name = "Nova",
      rating = 650,
      difficulty = BotDifficulty.BEGINNER,
      style = "Casual Explorer • Friendly pace, occasional blunders",
      maxDepth = 1,
      maxThinkTimeMs = 200L,
      randomnessCp = 240,
      emblemColorHex = 0xFF38BDF8,
      iconType = "spark",
      playingStyle = BotPlayingStyle.TACTICAL,
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
      playingStyle = BotPlayingStyle.SOLID,
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
      playingStyle = BotPlayingStyle.TACTICAL,
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
      playingStyle = BotPlayingStyle.AGGRESSIVE,
    ),
    BotProfile(
      id = "bot_titan",
      name = "Titan",
      rating = 1850,
      difficulty = BotDifficulty.EXPERT,
      style = "Positional Crusher • Consistent center pressure",
      maxDepth = 4,
      maxThinkTimeMs = 1100L,
      randomnessCp = 8,
      emblemColorHex = 0xFFF97316,
      iconType = "shield",
      playingStyle = BotPlayingStyle.POSITIONAL,
    ),
    BotProfile(
      id = "bot_master",
      name = "Master",
      rating = 2150,
      difficulty = BotDifficulty.MASTER,
      style = "National Strategist • Deep endgame technique",
      maxDepth = 4,
      maxThinkTimeMs = 1500L,
      randomnessCp = 0,
      emblemColorHex = 0xFFE11D48,
      iconType = "crown",
      playingStyle = BotPlayingStyle.ENDGAME_SPECIALIST,
    ),
    BotProfile(
      id = "bot_grandmaster",
      name = "Grandmaster",
      rating = 2450,
      difficulty = BotDifficulty.MASTER,
      style = "Elite GM • Balanced calculation & clean conversions",
      maxDepth = 5,
      maxThinkTimeMs = 1900L,
      randomnessCp = 0,
      emblemColorHex = 0xFFEAB308,
      iconType = "crown",
      playingStyle = BotPlayingStyle.POSITIONAL,
    ),
    BotProfile(
      id = "bot_apex",
      name = "Apex Max",
      rating = 2850,
      difficulty = BotDifficulty.MASTER,
      style = "Super-GM Engine • Relentless tactical precision",
      maxDepth = 5,
      maxThinkTimeMs = 2200L,
      randomnessCp = 0,
      emblemColorHex = 0xFFEF4444,
      iconType = "crown",
      playingStyle = BotPlayingStyle.TACTICAL,
    ),
    BotProfile(
      id = "bot_omega",
      name = "Omega 3200",
      rating = 3200,
      difficulty = BotDifficulty.MASTER,
      style = "Full-Strength Engine • Peak 3200 Elo depth",
      maxDepth = 6,
      maxThinkTimeMs = 2500L,
      randomnessCp = 0,
      emblemColorHex = 0xFFDC2626,
      iconType = "crown",
      playingStyle = BotPlayingStyle.BALANCED,
    ),
  )

  fun botForTargetElo(
    targetElo: Int,
    thinkTimeOverrideMs: Long? = null,
    playingStyle: BotPlayingStyle = BotPlayingStyle.BALANCED,
  ): BotProfile {
    val elo = targetElo.coerceIn(MIN_ELO, MAX_ELO)
    val depth = when {
      elo < 700 -> 1
      elo < 1300 -> 2
      elo < 1700 -> 3
      elo < 2150 -> 4
      elo < 2700 -> 5
      else -> 6
    }
    val randomnessCp = when {
      elo >= 2100 -> 0
      elo >= 1700 -> ((2100 - elo) / 22f).roundToInt()
      elo >= 1100 -> (18 + (1700 - elo) * 0.16f).roundToInt()
      else -> (115 + (1100 - elo) * 0.36f).roundToInt()
    }.coerceIn(0, 450)
    val timeMs = (thinkTimeOverrideMs ?: when {
      elo < 700 -> 180L
      elo < 1300 -> 400L
      elo < 1800 -> 800L
      elo < 2400 -> 1500L
      elo < 2900 -> 2100L
      else -> 2500L
    }).coerceIn(150L, 2500L)

    return BotProfile(
      id = "bot_custom",
      name = "Custom $elo",
      rating = elo,
      difficulty = BotDifficulty.CUSTOM,
      style = "Target $elo Elo • ${playingStyle.label} • Depth $depth",
      maxDepth = depth,
      maxThinkTimeMs = timeMs,
      randomnessCp = randomnessCp,
      emblemColorHex = 0xFF2DD4BF,
      iconType = "tune",
      playingStyle = playingStyle,
    )
  }

  fun customBot(
    depth: Int,
    randomnessCp: Int,
    thinkTimeMs: Long,
    playingStyle: BotPlayingStyle = BotPlayingStyle.BALANCED,
  ): BotProfile {
    val clampedDepth = depth.coerceIn(1, 6)
    val clampedRand = randomnessCp.coerceIn(0, 450)
    val estimatedRating = (300 + clampedDepth * 470 - (clampedRand * 1.4f).toInt()).coerceIn(MIN_ELO, MAX_ELO)
    return BotProfile(
      id = "bot_custom",
      name = "Custom $estimatedRating",
      rating = estimatedRating,
      difficulty = BotDifficulty.CUSTOM,
      style = "${playingStyle.label} • Depth $clampedDepth • Margin ${clampedRand}cp • ${thinkTimeMs}ms",
      maxDepth = clampedDepth,
      maxThinkTimeMs = thinkTimeMs.coerceIn(150L, 2500L),
      randomnessCp = clampedRand,
      emblemColorHex = 0xFF2DD4BF,
      iconType = "tune",
      playingStyle = playingStyle,
    )
  }

  fun findById(id: String): BotProfile = presets.firstOrNull { it.id == id } ?: presets[3]
}
