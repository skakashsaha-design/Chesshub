package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

  @Query(
    """
    SELECT id, whiteName, blackName, result, termination, dateMillis, timeControlLabel,
           movesCount, durationSeconds, gameType, ecoCode, openingName, isFinished
    FROM games
    WHERE isFinished = 1
    ORDER BY dateMillis DESC
    LIMIT :limit
    """
  )
  fun observeRecentGames(limit: Int): Flow<List<GameSummaryRow>>

  @Query(
    """
    SELECT id, whiteName, blackName, result, termination, dateMillis, timeControlLabel,
           movesCount, durationSeconds, gameType, ecoCode, openingName, isFinished
    FROM games
    WHERE (:query = '' OR whiteName LIKE '%' || :query || '%'
           OR blackName LIKE '%' || :query || '%'
           OR COALESCE(openingName, '') LIKE '%' || :query || '%'
           OR COALESCE(ecoCode, '') LIKE '%' || :query || '%')
    ORDER BY
      CASE WHEN :sortBy = 'OLDEST' THEN dateMillis END ASC,
      CASE WHEN :sortBy = 'LONGEST' THEN movesCount END DESC,
      CASE WHEN :sortBy = 'NEWEST' THEN dateMillis END DESC
    LIMIT :limit
    """
  )
  fun searchGames(query: String, sortBy: String, limit: Int): Flow<List<GameSummaryRow>>

  @Query("SELECT * FROM games WHERE isFinished = 0 ORDER BY updatedAtMillis DESC LIMIT 1")
  fun observeLatestUnfinishedGame(): Flow<GameEntity?>

  @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): GameEntity?

  @Query("SELECT pgn FROM games ORDER BY dateMillis DESC")
  suspend fun getAllPgns(): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(game: GameEntity): Long

  @Query("DELETE FROM games WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM games")
  suspend fun deleteAll()
}

@Dao
interface SettingsDao {
  @Query("SELECT * FROM app_settings WHERE id = 1")
  fun observeSettings(): Flow<AppSettingsEntity?>

  @Query("SELECT * FROM app_settings WHERE id = 1")
  suspend fun getSettings(): AppSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(settings: AppSettingsEntity)
}

@Dao
interface CachedProfileDao {
  @Query("SELECT * FROM cached_profiles WHERE platform = :platform LIMIT 1")
  fun observeByPlatform(platform: String): Flow<CachedProfileEntity?>

  @Query("SELECT * FROM cached_profiles WHERE platform = :platform LIMIT 1")
  suspend fun getByPlatform(platform: String): CachedProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(profile: CachedProfileEntity)

  @Query("DELETE FROM cached_profiles WHERE platform = :platform")
  suspend fun deleteByPlatform(platform: String)

  @Query("DELETE FROM cached_profiles")
  suspend fun deleteAll()
}

@Dao
interface PuzzleProgressDao {
  @Query("SELECT * FROM puzzle_progress ORDER BY completedAtMillis DESC")
  fun observeAll(): Flow<List<PuzzleProgressEntity>>

  @Query("SELECT * FROM puzzle_progress WHERE puzzleId = :id LIMIT 1")
  suspend fun getById(id: String): PuzzleProgressEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(progress: PuzzleProgressEntity)

  @Query("DELETE FROM puzzle_progress")
  suspend fun deleteAll()
}

@Dao
interface BotStatsDao {
  @Query("SELECT * FROM bot_stats")
  fun observeAll(): Flow<List<BotStatsEntity>>

  @Query("SELECT * FROM bot_stats WHERE botId = :botId LIMIT 1")
  suspend fun getById(botId: String): BotStatsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(stats: BotStatsEntity)

  @Query("DELETE FROM bot_stats")
  suspend fun deleteAll()
}
