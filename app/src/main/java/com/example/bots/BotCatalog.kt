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
      style = "First Steps • Playful learner, misses simple forks",
      maxDepth = 1,
      maxThinkTimeMs = 150L,
      randomnessCp = 420,
      emblemColorHex = 0xFF94A3B8,
      secondaryColorHex = 0xFF38BDF8,
      iconType = "spark",
      playingStyle = BotPlayingStyle.BALANCED,
      nationality = "Skyline Club",
      favoriteOpening = "Italian Game (C50)",
      catchphrase = "Every grandmaster started at 200!",
      bio = "Friendly academy mascot who loves open positions and lets beginners spot tactics safely.",
    ),
    BotProfile(
      id = "bot_pawnling",
      name = "Pawnling",
      rating = 400,
      difficulty = BotDifficulty.BEGINNER,
      style = "Apprentice • Eager pawn pushes & bold attacks",
      maxDepth = 1,
      maxThinkTimeMs = 180L,
      randomnessCp = 320,
      emblemColorHex = 0xFF60A5FA,
      secondaryColorHex = 0xFF1D4ED8,
      iconType = "pawn",
      playingStyle = BotPlayingStyle.AGGRESSIVE,
      nationality = "Oslo Youth",
      favoriteOpening = "Danish Gambit (C21)",
      catchphrase = "Pawns are the soul of chess—and mine sprint forward!",
      bio = "Fearless young prodigy who launches gambit pushes early, even when defense is thin.",
    ),
    BotProfile(
      id = "bot_nova",
      name = "Nova",
      rating = 650,
      difficulty = BotDifficulty.BEGINNER,
      style = "Casual Tactician • Flashy checks & gambit surprises",
      maxDepth = 1,
      maxThinkTimeMs = 200L,
      randomnessCp = 240,
      emblemColorHex = 0xFF38BDF8,
      secondaryColorHex = 0xFFA855F7,
      iconType = "nova",
      playingStyle = BotPlayingStyle.TACTICAL,
      nationality = "Tokyo Cyber Café",
      favoriteOpening = "Fried Liver Attack (C57)",
      catchphrase = "Keep an eye on f7—chaos is my comfort zone.",
      bio = "Speed-chess fan who trades solidity for creative checks and sudden tactical sparks.",
    ),
    BotProfile(
      id = "bot_rook",
      name = "Rook",
      rating = 950,
      difficulty = BotDifficulty.EASY,
      style = "Solid Defender • Straightforward trades & open files",
      maxDepth = 2,
      maxThinkTimeMs = 300L,
      randomnessCp = 120,
      emblemColorHex = 0xFF34D399,
      secondaryColorHex = 0xFF065F46,
      iconType = "rook",
      playingStyle = BotPlayingStyle.SOLID,
      nationality = "Prague Fortress",
      favoriteOpening = "London System (D00)",
      catchphrase = "Steady files win long battles.",
      bio = "Disciplined positional defender who castles early, protects every pawn, and simplifies ahead.",
    ),
    BotProfile(
      id = "bot_bishop",
      name = "Bishop",
      rating = 1250,
      difficulty = BotDifficulty.NORMAL,
      style = "Diagonal Tactician • Punishes hanging pieces cleanly",
      maxDepth = 2,
      maxThinkTimeMs = 450L,
      randomnessCp = 55,
      emblemColorHex = 0xFFFBBF24,
      secondaryColorHex = 0xFFB45309,
      iconType = "bishop",
      playingStyle = BotPlayingStyle.TACTICAL,
      nationality = "Vienna Conservatory",
      favoriteOpening = "Evans Gambit (C51)",
      catchphrase = "Watch the long diagonals—two bishops slice a board in half.",
      bio = "Classic tournament tactician specializing in pins, skewers, and sharp gambit counterplay.",
    ),
    BotProfile(
      id = "bot_knight",
      name = "Knight",
      rating = 1550,
      difficulty = BotDifficulty.ADVANCED,
      style = "Fork Specialist • Relentless outposts & tactical vision",
      maxDepth = 3,
      maxThinkTimeMs = 700L,
      randomnessCp = 25,
      emblemColorHex = 0xFFA78BFA,
      secondaryColorHex = 0xFF5B21B6,
      iconType = "knight",
      playingStyle = BotPlayingStyle.AGGRESSIVE,
      nationality = "Budapest Academy",
      favoriteOpening = "Budapest Gambit (A52)",
      catchphrase = "You can block a bishop, but a knight jumps the wall.",
      bio = "Daring attacker famous for royal forks, smothered-mate motifs, and energetic gambit traps.",
    ),
    BotProfile(
      id = "bot_titan",
      name = "Titan",
      rating = 1850,
      difficulty = BotDifficulty.EXPERT,
      style = "Positional Crusher • Deep space advantage & squeeze",
      maxDepth = 4,
      maxThinkTimeMs = 1100L,
      randomnessCp = 8,
      emblemColorHex = 0xFFF97316,
      secondaryColorHex = 0xFF9A3412,
      iconType = "shield",
      playingStyle = BotPlayingStyle.POSITIONAL,
      nationality = "Reykjavik League",
      favoriteOpening = "Queen's Gambit Declined (D50)",
      catchphrase = "Small weaknesses accumulate into decisive weight.",
      bio = "Patient candidate-master engine that suffocates counterplay and exploits backward pawns.",
    ),
    BotProfile(
      id = "bot_master",
      name = "Master",
      rating = 2150,
      difficulty = BotDifficulty.MASTER,
      style = "Endgame Virtuoso • Ruthless conversion & king activity",
      maxDepth = 4,
      maxThinkTimeMs = 1500L,
      randomnessCp = 0,
      emblemColorHex = 0xFFE11D48,
      secondaryColorHex = 0xFF881337,
      iconType = "master",
      playingStyle = BotPlayingStyle.ENDGAME_SPECIALIST,
      nationality = "Zurich Masters",
      favoriteOpening = "Ruy Lopez: Berlin Defense (C65)",
      catchphrase = "Trade the queens, and let's test your endgame opposition.",
      bio = "National Master coach built to sharpen opposition, lucena bridges, and clinical conversions.",
    ),
    BotProfile(
      id = "bot_grandmaster",
      name = "Grandmaster",
      rating = 2450,
      difficulty = BotDifficulty.MASTER,
      style = "Elite GM • Universal prophylaxis & theoretical mastery",
      maxDepth = 5,
      maxThinkTimeMs = 1900L,
      randomnessCp = 0,
      emblemColorHex = 0xFFEAB308,
      secondaryColorHex = 0xFF713F12,
      iconType = "crown",
      playingStyle = BotPlayingStyle.POSITIONAL,
      nationality = "Saint Louis Elite",
      favoriteOpening = "Sicilian Defense: Najdorf (B90)",
      catchphrase = "Stop my plan before it starts, or I control the board.",
      bio = "Grandmaster-strength sparring bot blending deep opening theory with ironclad prophylaxis.",
    ),
    BotProfile(
      id = "bot_apex",
      name = "Apex Max",
      rating = 2850,
      difficulty = BotDifficulty.MASTER,
      style = "Super-GM Engine • Relentless 2850 tactical precision",
      maxDepth = 5,
      maxThinkTimeMs = 2200L,
      randomnessCp = 0,
      emblemColorHex = 0xFFEF4444,
      secondaryColorHex = 0xFF450A0A,
      iconType = "apex",
      playingStyle = BotPlayingStyle.TACTICAL,
      nationality = "Deep Grid Core",
      favoriteOpening = "King's Indian Defense (E60)",
      catchphrase = "One loose tempo and the evaluation drops like stone.",
      bio = "Super-GM neural-style alpha-beta engine that punishes every tactical nuance instantly.",
    ),
    BotProfile(
      id = "bot_omega",
      name = "Omega 3200",
      rating = 3200,
      difficulty = BotDifficulty.MASTER,
      style = "Full-Strength Core • Peak 3200 Elo calculation ceiling",
      maxDepth = 6,
      maxThinkTimeMs = 2500L,
      randomnessCp = 0,
      emblemColorHex = 0xFFDC2626,
      secondaryColorHex = 0xFF0F172A,
      iconType = "omega",
      playingStyle = BotPlayingStyle.BALANCED,
      nationality = "Silicon Apex",
      favoriteOpening = "Grünfeld Defense (D85)",
      catchphrase = "Zero guesswork. Pure depth across 64 squares.",
      bio = "ChessHub's flagship 3200 Elo engine engineered for grandmasters and deep analysis sparring.",
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
      secondaryColorHex = 0xFF0F766E,
      iconType = "tune",
      playingStyle = playingStyle,
      nationality = "Custom Lab",
      favoriteOpening = "Dynamic Adaptation",
      catchphrase = "Built to your exact Elo & style specification.",
      bio = "Custom-tuned engine calibrated to $elo Elo with a ${playingStyle.label} personality.",
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
      secondaryColorHex = 0xFF0F766E,
      iconType = "tune",
      playingStyle = playingStyle,
      nationality = "Custom Lab",
      favoriteOpening = "Custom Line",
      catchphrase = "Tailored calculation profile ready.",
      bio = "Custom depth-$clampedDepth engine with ${playingStyle.label} heuristics.",
    )
  }

  fun findById(id: String): BotProfile = presets.firstOrNull { it.id == id } ?: presets[3]
}
