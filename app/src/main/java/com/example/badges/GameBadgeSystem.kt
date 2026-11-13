package com.example.badges

import com.example.database.AppSettingsEntity
import com.example.database.BotStatsEntity

data class GameBadge(
  val id: String,
  val title: String,
  val description: String,
  val tierColorHex: Long,
  val category: String,
)

data class EvaluatedBadge(
  val badge: GameBadge,
  val unlocked: Boolean,
)

object GameBadgeSystem {

  val allBadges: List<GameBadge> = listOf(
    GameBadge("first_win", "First Victory", "Win your first chess match", 0xFF22C55E, "Milestones"),
    GameBadge("ten_games", "Dedicated Player", "Complete 10+ local or bot games", 0xFF38BDF8, "Milestones"),
    GameBadge("giant_slayer", "Giant Slayer", "Reach 1400+ Elo or defeat a 1500+ bot", 0xFFA855F7, "Skill"),
    GameBadge("master_challenger", "Master Challenger", "Win a match against a Master/GM bot (2150+)", 0xFFEAB308, "Skill"),
    GameBadge("gambit_daredevil", "Gambit Daredevil", "Play an official Gambit opening in a live game", 0xFFEF4444, "Openings"),
    GameBadge("book_scholar", "Book Scholar", "Complete 3+ opening or gambit book practices", 0xFF06B6D4, "Openings"),
    GameBadge("tactician", "Tactical Vision", "Reach 5+ puzzle streak or solve tactical drills", 0xFFF97316, "Tactics"),
    GameBadge("brilliant_mind", "Brilliant Mind", "Find a Brilliant (!!) tactical sacrifice", 0xFF14B8A6, "Tactics"),
    GameBadge("game_analyst", "Deep Analyst", "Run full Game Review & check accuracy stats", 0xFF6366F1, "Analysis"),
    GameBadge("custom_architect", "Game Architect", "Launch a Chess960, Odds, or Custom FEN game", 0xFFEC4899, "Custom"),
    GameBadge("tournament_victor", "Tournament Pro", "Play or run a Bot Round-Robin Tournament", 0xFFD97706, "Arena"),
    GameBadge("speed_demon", "Speed Demon", "Complete a Bullet or Blitz match", 0xFFF43F5E, "Arena"),
  )

  fun parseCsv(csv: String): Set<String> =
    csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

  fun toCsv(ids: Set<String>): String = ids.sorted().joinToString(",")

  fun evaluate(
    settings: AppSettingsEntity,
    botStats: List<BotStatsEntity>,
  ): List<EvaluatedBadge> {
    val persisted = parseCsv(settings.unlockedBadgesCsv).toMutableSet()
    val totalGames = settings.localWins + settings.localLosses + settings.localDraws
    if (settings.localWins >= 1) persisted.add("first_win")
    if (totalGames >= 10) persisted.add("ten_games")
    if (settings.localRating >= 1400 || botStats.any {
        it.playerWins > 0 && it.botId in setOf("bot_knight", "bot_titan", "bot_master", "bot_grandmaster", "bot_apex", "bot_omega")
      }
    ) {
      persisted.add("giant_slayer")
    }
    if (botStats.any {
        it.playerWins > 0 && it.botId in setOf("bot_master", "bot_grandmaster", "bot_apex", "bot_omega")
      }
    ) {
      persisted.add("master_challenger")
    }
    if (settings.bookDrillsCompleted >= 3) persisted.add("book_scholar")
    if (settings.puzzleStreak >= 5 || settings.puzzleRating >= 1350) persisted.add("tactician")
    if (settings.gameReviewsCompleted >= 1) persisted.add("game_analyst")

    return allBadges.map { b -> EvaluatedBadge(b, b.id in persisted) }
  }
}
