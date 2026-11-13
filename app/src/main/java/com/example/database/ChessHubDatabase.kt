package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    GameEntity::class,
    AppSettingsEntity::class,
    CachedProfileEntity::class,
    PuzzleProgressEntity::class,
    BotStatsEntity::class,
  ],
  version = 4,
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN appAccent TEXT NOT NULL DEFAULT 'EMERALD'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN playerName TEXT NOT NULL DEFAULT 'ChessHub Player'")
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        val columns = listOf(
          "showEvalBar",
          "showThreatArrow",
          "showSuggestionArrow",
          "showEngineLine",
          "showMoveFeedback",
          "showBotMoveFeedback",
          "enableGameAnalysis",
          "showEvalGraph",
          "showCandidateMoves",
          "showTacticalSuggestions",
          "enableCriticalMoments",
          "showOpeningInfo",
          "enableEngineAnalysis",
        )
        columns.forEach { col ->
          db.execSQL("ALTER TABLE app_settings ADD COLUMN $col INTEGER NOT NULL DEFAULT 1")
        }
      }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN unlockedBadgesCsv TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN bookDrillsCompleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN gameReviewsCompleted INTEGER NOT NULL DEFAULT 0")
      }
    }

    fun getInstance(context: Context): ChessHubDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          ChessHubDatabase::class.java,
          "chesshub_local.db",
        )
          .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
