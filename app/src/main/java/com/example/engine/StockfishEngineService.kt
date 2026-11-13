package com.example.engine

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.example.chess.BoardState
import com.example.chess.BotProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Bound Kotlin Service hosting the Stockfish UCI chess engine wrapper.
 * Runs engine search and position evaluation off the UI thread and manages
 * memory cleanup when the app is constrained or unbound.
 */
class StockfishEngineService : Service() {

  inner class LocalBinder : Binder() {
    fun getService(): StockfishEngineService = this@StockfishEngineService
  }

  private val binder = LocalBinder()
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private lateinit var wrapper: StockfishUciWrapper

  val engineStatus: StateFlow<StockfishEngineStatus>
    get() = wrapper.status

  override fun onCreate() {
    super.onCreate()
    wrapper = StockfishUciWrapper(applicationContext)
  }

  override fun onBind(intent: Intent?): IBinder = binder

  suspend fun computeBotMove(
    state: BoardState,
    bot: BotProfile,
    budget: SearchBudget,
    recentHashes: Map<String, Int> = emptyMap(),
  ): EngineMoveEvaluation? {
    return wrapper.findBotMove(
      state = state,
      bot = bot,
      budget = budget,
      recentHashes = recentHashes,
    )
  }

  suspend fun evaluatePosition(
    state: BoardState,
    budget: SearchBudget,
  ): PositionEvaluation {
    return wrapper.evaluatePosition(state, budget)
  }

  suspend fun analyzeDetailed(
    state: BoardState,
    budget: SearchBudget,
    includePv: Boolean,
    includeCandidates: Boolean,
    includeThreat: Boolean,
    candidateCount: Int = 3,
  ): EngineAnalysisOutput {
    return wrapper.analyzeDetailed(
      state = state,
      budget = budget,
      includePv = includePv,
      includeCandidates = includeCandidates,
      includeThreat = includeThreat,
      candidateCount = candidateCount,
    )
  }

  fun evaluateStaticForWhite(state: BoardState): Int =
    wrapper.evaluateStaticForWhite(state)

  fun releaseSearchMemory() {
    serviceScope.launch {
      wrapper.releaseSearchMemory()
    }
  }

  fun stopCurrentSearch() {
    wrapper.stopSearch()
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= TRIM_MEMORY_RUNNING_LOW) {
      releaseSearchMemory()
    }
  }

  override fun onDestroy() {
    wrapper.shutdown()
    serviceScope.cancel()
    super.onDestroy()
  }
}
