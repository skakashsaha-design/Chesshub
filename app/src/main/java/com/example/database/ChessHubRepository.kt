package com.example.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.pow
import kotlin.math.roundToInt

class ChessHubRepository(
  private val db: ChessHubDatabase,
  private val defaultLowMemory: Boolean,
) {
  private val gameDao = db.gameDao()
  private val settingsDao = db.settingsDao()
  private val profileDao = db.cachedProfileDao()
  private val puzzleDao = db.puzzleProgressDao()
  private val botStatsDao = db.botStatsDao()

  val settingsFlow: Flow<AppSettingsEntity> = settingsDao.observeSettings().map {
    it ?: AppSettingsEntity(lowMemoryMode = defaultLowMemory)
  }

  val recentGamesFlow: Flow<List<GameSummaryRow>> = gameDao.observeRecentGames(limit = 10)
  val latestUnfinishedGameFlow: Flow<GameEntity?> = gameDao.observeLatestUnfinishedGame()
  val puzzleProgressFlow: Flow<List<PuzzleProgressEntity>> = puzzleDao.observeAll()
  val botStatsFlow: Flow<List<BotStatsEntity>> = botStatsDao.observeAll()

  fun searchGames(query: String, sortBy: String, limit: Int): Flow<List<GameSummaryRow>> =
    gameDao.searchGames(query.trim(), sortBy, limit)

  fun observeCachedProfile(platform: String): Flow<CachedProfileEntity?> =
    profileDao.observeByPlatform(platform)

  suspend fun ensureInitializedSettings(): AppSettingsEntity {
    val existing = settingsDao.getSettings()
    if (existing != null) return existing
    val initial = AppSettingsEntity(lowMemoryMode = defaultLowMemory)
    settingsDao.upsert(initial)
    return initial
  }

  suspend fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity): AppSettingsEntity {
    val current = ensureInitializedSettings()
    val updated = transform(current)
    settingsDao.upsert(updated)
    return updated
  }

  suspend fun saveGame(game: GameEntity): Long = gameDao.upsert(game)

  suspend fun getGameById(id: Long): GameEntity? = gameDao.getById(id)

  suspend fun deleteGame(id: Long) = gameDao.deleteById(id)

  suspend fun exportAllPgns(): String = gameDao.getAllPgns().joinToString("\n\n")

  suspend fun recordBotMatchOutcome(
    botId: String,
    botRating: Int,
    playerScore: Double, // 1.0 win, 0.5 draw, 0.0 loss
  ) {
    val curStats = botStatsDao.getById(botId) ?: BotStatsEntity(botId = botId)
    val nextStats = curStats.copy(
      gamesPlayed = curStats.gamesPlayed + 1,
      playerWins = curStats.playerWins + if (playerScore == 1.0) 1 else 0,
      playerLosses = curStats.playerLosses + if (playerScore == 0.0) 1 else 0,
      draws = curStats.draws + if (playerScore == 0.5) 1 else 0,
    )
    botStatsDao.upsert(nextStats)

    updateSettings { cur ->
      val expected = 1.0 / (1.0 + 10.0.pow((botRating - cur.localRating) / 400.0))
      val delta = (24.0 * (playerScore - expected)).roundToInt()
      cur.copy(
        localRating = (cur.localRating + delta).coerceIn(400, 2800),
        localWins = cur.localWins + if (playerScore == 1.0) 1 else 0,
        localLosses = cur.localLosses + if (playerScore == 0.0) 1 else 0,
        localDraws = cur.localDraws + if (playerScore == 0.5) 1 else 0,
      )
    }
  }

  suspend fun recordHumanMatchOutcome(result: String) {
    updateSettings { cur ->
      when (result) {
        "1-0" -> cur.copy(localWins = cur.localWins + 1)
        "0-1" -> cur.copy(localLosses = cur.localLosses + 1)
        "1/2-1/2" -> cur.copy(localDraws = cur.localDraws + 1)
        else -> cur
      }
    }
  }

  suspend fun recordPuzzleOutcome(puzzleId: String, puzzleRating: Int, solvedCleanly: Boolean) {
    val prev = puzzleDao.getById(puzzleId)
    puzzleDao.upsert(
      PuzzleProgressEntity(
        puzzleId = puzzleId,
        solved = solvedCleanly || (prev?.solved == true),
        failedAttempts = (prev?.failedAttempts ?: 0) + if (solvedCleanly) 0 else 1,
        completedAtMillis = System.currentTimeMillis(),
      )
    )
    updateSettings { cur ->
      val expected = 1.0 / (1.0 + 10.0.pow((puzzleRating - cur.puzzleRating) / 400.0))
      val score = if (solvedCleanly) 1.0 else 0.0
      val delta = (20.0 * (score - expected)).roundToInt()
      cur.copy(
        puzzleRating = (cur.puzzleRating + delta).coerceIn(500, 2800),
        puzzleStreak = if (solvedCleanly) cur.puzzleStreak + 1 else 0,
      )
    }
  }

  suspend fun saveCachedProfile(profile: CachedProfileEntity) = profileDao.upsert(profile)

  suspend fun deleteCachedProfile(platform: String) = profileDao.deleteByPlatform(platform)

  suspend fun clearProfileCache() = profileDao.deleteAll()

  suspend fun deleteAllLocalData() {
    gameDao.deleteAll()
    profileDao.deleteAll()
    puzzleDao.deleteAll()
    botStatsDao.deleteAll()
    settingsDao.upsert(AppSettingsEntity(lowMemoryMode = defaultLowMemory))
  }
}
