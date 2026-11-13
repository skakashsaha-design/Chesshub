package com.example.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "games",
  indices = [
    Index(value = ["dateMillis"]),
    Index(value = ["isFinished"]),
  ],
)
data class GameEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val whiteName: String,
  val blackName: String,
  val result: String, // "1-0", "0-1", "1/2-1/2", "*"
  val termination: String,
  val dateMillis: Long,
  val timeControlLabel: String,
  val pgn: String,
  val fen: String,
  val movesCount: Int,
  val durationSeconds: Int,
  val gameType: String, // HUMAN_VS_HUMAN, HUMAN_VS_BOT, BOT_VS_BOT, ONLINE
  val ecoCode: String? = null,
  val openingName: String? = null,
  val isFinished: Boolean = true,
  val updatedAtMillis: Long = System.currentTimeMillis(),
)

/**
 * Lightweight projection for history lists so thousands of games do not load full PGN blobs into RAM.
 */
data class GameSummaryRow(
  val id: Long,
  val whiteName: String,
  val blackName: String,
  val result: String,
  val termination: String,
  val dateMillis: Long,
  val timeControlLabel: String,
  val movesCount: Int,
  val durationSeconds: Int,
  val gameType: String,
  val ecoCode: String?,
  val openingName: String?,
  val isFinished: Boolean,
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
  @PrimaryKey val id: Int = 1,
  val themeMode: String = "SYSTEM", // LIGHT, DARK, SYSTEM
  val appAccent: String = "EMERALD", // EMERALD, COBALT, AMBER, CRIMSON, VIOLET
  val boardTheme: String = "WALNUT", // WALNUT, SLATE, TOURNAMENT, NORDIC, OBSIDIAN, ROSEWOOD, OCEAN, SANDSTONE
  val pieceStyle: String = "CLASSIC", // CLASSIC, MODERN, MINIMAL, ROYAL_GOLD, HIGH_CONTRAST, OUTLINE
  val showCoordinates: Boolean = true,
  val animateMoves: Boolean = true,
  val confirmMoves: Boolean = false,
  val showLegalMoves: Boolean = true,
  val hapticFeedback: Boolean = true,
  val soundMove: Boolean = true,
  val soundCapture: Boolean = true,
  val soundCheck: Boolean = true,
  val soundGameEnd: Boolean = true,
  val soundUi: Boolean = true,
  val musicEnabled: Boolean = true,
  val lowMemoryMode: Boolean = false,
  val reduceAnimations: Boolean = false,
  val batterySavingMode: Boolean = false,
  val languageCode: String = "en", // en, bn
  val playerName: String = "ChessHub Player",
  val lichessUsername: String = "",
  val chessComUsername: String = "",
  val localRating: Int = 1200,
  val localWins: Int = 0,
  val localLosses: Int = 0,
  val localDraws: Int = 0,
  val puzzleRating: Int = 1200,
  val puzzleStreak: Int = 0,
  val onlineServerUrl: String = "",
  val onlineHandle: String = "",
  val musicVolume: Float = 0.8f,
  val musicShuffle: Boolean = false,
  val musicRepeatMode: String = "OFF", // OFF, ALL, ONE
  // Individual Analysis & Feedback ON/OFF Controls
  val showEvalBar: Boolean = true,
  val showThreatArrow: Boolean = true,
  val showSuggestionArrow: Boolean = true,
  val showEngineLine: Boolean = true,
  val showMoveFeedback: Boolean = true,
  val showBotMoveFeedback: Boolean = true,
  val enableGameAnalysis: Boolean = true,
  val showEvalGraph: Boolean = true,
  val showCandidateMoves: Boolean = true,
  val showTacticalSuggestions: Boolean = true,
  val enableCriticalMoments: Boolean = true,
  val showOpeningInfo: Boolean = true,
  val enableEngineAnalysis: Boolean = true,
)

@Entity(tableName = "cached_profiles")
data class CachedProfileEntity(
  @PrimaryKey val platform: String, // "LICHESS" or "CHESSCOM"
  val username: String,
  val title: String? = null,
  val ratingsSummary: String = "",
  val totalGames: Int = 0,
  val wins: Int = 0,
  val losses: Int = 0,
  val draws: Int = 0,
  val recentGamesJson: String = "[]",
  val profileUrl: String = "",
  val fetchedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "puzzle_progress")
data class PuzzleProgressEntity(
  @PrimaryKey val puzzleId: String,
  val solved: Boolean,
  val failedAttempts: Int = 0,
  val completedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "bot_stats")
data class BotStatsEntity(
  @PrimaryKey val botId: String,
  val gamesPlayed: Int = 0,
  val playerWins: Int = 0,
  val playerLosses: Int = 0,
  val draws: Int = 0,
)
