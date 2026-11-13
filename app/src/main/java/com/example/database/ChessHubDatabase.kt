package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    GameEntity::class,
    AppSettingsEntity::class,
    CachedProfileEntity::class,
    PuzzleProgressEntity::class,
    BotStatsEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class ChessHubDatabase : RoomDatabase() {
  abstract fun gameDao(): GameDao
  abstract fun settingsDao(): SettingsDao
  abstract fun cachedProfileDao(): CachedProfileDao
  abstract fun puzzleProgressDao(): PuzzleProgressDao
  abstract fun botStatsDao(): BotStatsDao

  companion object {
    @Volatile
    private var INSTANCE: ChessHubDatabase? = null

    fun getInstance(context: Context): ChessHubDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          ChessHubDatabase::class.java,
          "chesshub_local.db",
        ).build().also { INSTANCE = it }
      }
    }
  }
}
