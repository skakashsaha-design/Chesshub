package com.example

import com.example.chess.BoardState
import com.example.chess.ChessRules
import com.example.chess.GameResult
import com.example.chess.Move
import com.example.chess.OpeningInfo
import com.example.chess.Opponent
import com.example.chess.PieceColor
import com.example.chess.RecordedMove
import com.example.chess.TerminationReason
import com.example.offline.TimeControl
import com.example.puzzles.ChessPuzzle
import com.example.puzzles.PuzzleCatalog

enum class AppSection {
  HOME,
  PLAY,
  BOTS,
  ONLINE,
  PUZZLES,
  PROFILES,
  MUSIC,
  SETTINGS,
}

enum class GameMode {
  HUMAN_VS_HUMAN,
  HUMAN_VS_BOT,
  BOT_VS_BOT,
  BOT_TRAINING,
  ONLINE,
}

data class ActiveGameUiState(
  val gameId: Long? = null,
  val mode: GameMode = GameMode.HUMAN_VS_BOT,
  val whiteOpponent: Opponent = Opponent.Human("You"),
  val blackOpponent: Opponent = Opponent.Human("Player 2"),
  val boardState: BoardState = ChessRules.initialBoardState(),
  val moves: List<RecordedMove> = emptyList(),
  val stateHistory: List<BoardState> = listOf(ChessRules.initialBoardState()),
  val legalMoves: List<Move> = ChessRules.legalMoves(ChessRules.initialBoardState()),
  val selectedSquare: Int? = null,
  val pendingConfirmMove: Move? = null,
  val pendingPromotionFrom: Int? = null,
  val pendingPromotionTo: Int? = null,
  val lastMoveFrom: Int? = null,
  val lastMoveTo: Int? = null,
  val isFlipped: Boolean = false,
  val timeControl: TimeControl = TimeControl.presets[6], // 10+0
  val whiteTimeMs: Long = 10 * 60_000L,
  val blackTimeMs: Long = 10 * 60_000L,
  val isBotThinking: Boolean = false,
  val result: GameResult = GameResult.IN_PROGRESS,
  val termination: TerminationReason = TerminationReason.NONE,
  val opening: OpeningInfo? = null,
  val drawOfferPendingFrom: PieceColor? = null,
  val trainingMode: Boolean = false,
  val evalCpForWhite: Int = 0,
  val evalMateIn: Int? = null,
  val hintMove: Move? = null,
  val replayIndex: Int? = null, // null = live play, 0..moves.size = replay step
  val startedAtMillis: Long = System.currentTimeMillis(),
)

data class PuzzleUiState(
  val puzzle: ChessPuzzle = PuzzleCatalog.puzzles.first(),
  val boardState: BoardState = ChessRules.initialBoardState(),
  val stepIndex: Int = 0,
  val selectedSquare: Int? = null,
  val legalMoves: List<Move> = emptyList(),
  val lastMoveFrom: Int? = null,
  val lastMoveTo: Int? = null,
  val showHint: Boolean = false,
  val statusMessage: String? = null,
  val isSolved: Boolean = false,
  val hadMistake: Boolean = false,
)

data class PlatformProfileUiState(
  val isLoading: Boolean = false,
  val username: String = "",
  val title: String? = null,
  val ratingsSummary: String = "",
  val totalGames: Int = 0,
  val wins: Int = 0,
  val losses: Int = 0,
  val draws: Int = 0,
  val profileUrl: String = "",
  val recentGames: List<com.example.lichess.PublicRecentGame> = emptyList(),
  val isCachedOffline: Boolean = false,
  val errorMessage: String? = null,
)
