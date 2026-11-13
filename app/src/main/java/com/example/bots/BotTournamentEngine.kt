package com.example.bots

import com.example.chess.BotProfile
import com.example.chess.ChessRules
import com.example.chess.GameResult
import com.example.engine.ChessEngine
import com.example.engine.DeviceMemoryAdvisor
import com.example.offline.TimeCategory
import com.example.offline.TimeControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class TournamentStanding(
  val bot: BotProfile,
  val played: Int = 0,
  val wins: Int = 0,
  val draws: Int = 0,
  val losses: Int = 0,
) {
  val points: Double get() = wins + draws * 0.5
}

data class TournamentMatchSummary(
  val whiteName: String,
  val blackName: String,
  val result: GameResult,
  val movesCount: Int,
  val timeCategoryLabel: String = "Rapid",
)

data class TournamentProgress(
  val standings: List<TournamentStanding>,
  val completedMatches: List<TournamentMatchSummary>,
  val isRunning: Boolean,
  val currentPairing: String,
  val tournamentTitle: String = "4-Bot Round Robin",
  val timeControl: TimeControl = TimeControl.presets[4],
)

/**
 * Runs compact offline Bot vs Bot tournaments across Bullet, Blitz, Rapid, and Classical
 * budgets safely off the UI thread.
 */
object BotTournamentEngine {

  suspend fun runRoundRobin(
    participants: List<BotProfile>,
    timeControl: TimeControl = TimeControl.presets[4], // 5+0 Blitz default
    tournamentTitle: String = "${timeControl.category.label} ${timeControl.label} Round Robin",
    lowMemoryMode: Boolean,
    isLowRamHardware: Boolean,
    onProgress: (TournamentProgress) -> Unit,
  ) = withContext(Dispatchers.Default) {
    val map = participants.associate { it.id to TournamentStanding(it) }.toMutableMap()
    val matches = ArrayList<TournamentMatchSummary>()
    val engine = ChessEngine()

    val pairings = ArrayList<Pair<BotProfile, BotProfile>>()
    for (i in participants.indices) {
      for (j in i + 1 until participants.size) {
        pairings.add(participants[i] to participants[j])
      }
    }

    val perMoveBudgetCapMs = when (timeControl.category) {
      TimeCategory.BULLET -> 90L
      TimeCategory.BLITZ -> 180L
      TimeCategory.RAPID -> 320L
      TimeCategory.CLASSICAL, TimeCategory.UNLIMITED -> 420L
    }
    val depthCap = when (timeControl.category) {
      TimeCategory.BULLET -> 2
      else -> 3
    }

    for ((whiteBot, blackBot) in pairings) {
      coroutineContext.ensureActive()
      onProgress(
        TournamentProgress(
          standings = map.values.sortedByDescending { it.points },
          completedMatches = matches.toList(),
          isRunning = true,
          currentPairing = "${whiteBot.name} vs ${blackBot.name}",
          tournamentTitle = tournamentTitle,
          timeControl = timeControl,
        )
      )

      var state = ChessRules.initialBoardState()
      val reps = HashMap<String, Int>()
      reps[ChessRules.repetitionKey(state)] = 1
      var plies = 0
      var result = GameResult.IN_PROGRESS

      while (result == GameResult.IN_PROGRESS && plies < 140) {
        coroutineContext.ensureActive()
        val activeBot = if (state.turn.name == "WHITE") whiteBot else blackBot
        val budget = DeviceMemoryAdvisor.computeSearchBudget(
          requestedDepth = activeBot.maxDepth.coerceAtMost(depthCap),
          requestedTimeMs = activeBot.maxThinkTimeMs.coerceAtMost(perMoveBudgetCapMs),
          lowMemoryMode = lowMemoryMode,
          batterySavingMode = true,
          isLowRamHardware = isLowRamHardware,
        )
        val search = engine.findBestMove(
          state = state,
          budget = budget,
          randomnessCp = activeBot.randomnessCp,
          recentHashes = reps,
          playingStyle = activeBot.playingStyle,
        ) ?: break
        state = ChessRules.applyMove(state, search.bestMove)
        plies++
        val key = ChessRules.repetitionKey(state)
        reps[key] = (reps[key] ?: 0) + 1
        result = ChessRules.evaluateTerminalStatus(state, reps).first
      }

      if (result == GameResult.IN_PROGRESS) {
        val eval = engine.evaluatePositionForWhite(state)
        result = when {
          eval > 180 -> GameResult.WHITE_WIN
          eval < -180 -> GameResult.BLACK_WIN
          else -> GameResult.DRAW
        }
      }
      engine.releaseMemory()

      val wCur = map[whiteBot.id]!!
      val bCur = map[blackBot.id]!!
      when (result) {
        GameResult.WHITE_WIN -> {
          map[whiteBot.id] = wCur.copy(played = wCur.played + 1, wins = wCur.wins + 1)
          map[blackBot.id] = bCur.copy(played = bCur.played + 1, losses = bCur.losses + 1)
        }
        GameResult.BLACK_WIN -> {
          map[whiteBot.id] = wCur.copy(played = wCur.played + 1, losses = wCur.losses + 1)
          map[blackBot.id] = bCur.copy(played = bCur.played + 1, wins = bCur.wins + 1)
        }
        else -> {
          map[whiteBot.id] = wCur.copy(played = wCur.played + 1, draws = wCur.draws + 1)
          map[blackBot.id] = bCur.copy(played = bCur.played + 1, draws = bCur.draws + 1)
        }
      }
      matches.add(
        TournamentMatchSummary(
          whiteName = whiteBot.name,
          blackName = blackBot.name,
          result = result,
          movesCount = (plies + 1) / 2,
          timeCategoryLabel = "${timeControl.category.label} ${timeControl.label}",
        )
      )
    }

    onProgress(
      TournamentProgress(
        standings = map.values.sortedByDescending { it.points },
        completedMatches = matches,
        isRunning = false,
        currentPairing = "",
        tournamentTitle = tournamentTitle,
        timeControl = timeControl,
      )
    )
  }
}
