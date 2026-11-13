package com.example.engine

import android.content.Context
import com.example.chess.BoardState
import com.example.chess.BotDifficulty
import com.example.chess.BotProfile
import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.Move
import com.example.chess.PieceColor
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class EngineBackendKind(val label: String) {
  STOCKFISH_UCI("Stockfish UCI"),
  BUILTIN_KOTLIN("Built-in Kotlin (Stockfish UCI Ready)"),
}

data class PositionEvaluation(
  val scoreCpForWhite: Int,
  val mateInMoves: Int? = null,
  val bestMoveUci: String? = null,
  val depthReached: Int = 1,
  val nodesVisited: Int = 0,
  val backend: EngineBackendKind = EngineBackendKind.BUILTIN_KOTLIN,
)

data class EngineMoveEvaluation(
  val bestMove: Move,
  val evaluation: PositionEvaluation,
)

data class StockfishEngineStatus(
  val backend: EngineBackendKind = EngineBackendKind.BUILTIN_KOTLIN,
  val binaryPath: String? = null,
  val isReady: Boolean = true,
  val lowMemoryHashMb: Int = 8,
  val lastInfoSummary: String = "",
)

data class ParsedUciInfo(
  val depth: Int? = null,
  val nodes: Int? = null,
  val scoreCpForSideToMove: Int? = null,
  val mateForSideToMove: Int? = null,
  val pvFirstMove: String? = null,
)

object UciLineParser {

  fun parseInfoLine(line: String): ParsedUciInfo? {
    if (!line.startsWith("info ")) return null
    val tokens = line.trim().split(Regex("\\s+"))
    var depth: Int? = null
    var nodes: Int? = null
    var cp: Int? = null
    var mate: Int? = null
    var pvFirst: String? = null

    var i = 1
    while (i < tokens.size) {
      when (tokens[i]) {
        "depth" -> {
          depth = tokens.getOrNull(i + 1)?.toIntOrNull()
          i += 2
        }
        "nodes" -> {
          nodes = tokens.getOrNull(i + 1)?.toIntOrNull()
          i += 2
        }
        "score" -> {
          val kind = tokens.getOrNull(i + 1)
          val value = tokens.getOrNull(i + 2)?.toIntOrNull()
          if (kind == "cp" && value != null) {
            cp = value
            i += 3
          } else if (kind == "mate" && value != null) {
            mate = value
            i += 3
          } else {
            i += 1
          }
        }
        "pv" -> {
          pvFirst = tokens.getOrNull(i + 1)
          break
        }
        else -> i++
      }
    }
    return ParsedUciInfo(depth, nodes, cp, mate, pvFirst)
  }

  fun parseBestMoveLine(line: String): String? {
    if (!line.startsWith("bestmove ")) return null
    val token = line.removePrefix("bestmove ").trim().substringBefore(' ')
    if (token.isBlank() || token == "(none)") return null
    return token
  }
}

/**
 * Lightweight Stockfish UCI wrapper with automatic runtime detection of a pre-bundled
 * `libstockfish.so` native binary and seamless fallback to the pure-Kotlin ChessEngine.
 *
 * Strictly Play-policy compliant: never downloads or dynamically loads external binaries.
 * Optimized for 1 GB RAM / Android 8.1+ (Threads=1, Hash=8-16 MB, Clear Hash on low memory).
 */
class StockfishUciWrapper(context: Context) {

  private val appContext = context.applicationContext
  private val fallbackEngine = ChessEngine()
  private val uciMutex = Mutex()

  private var process: Process? = null
  private var writer: BufferedWriter? = null
  private var reader: BufferedReader? = null
  private var activeHashMb: Int = 8

  private val detectedBinary: File? = locateBundledStockfishBinary()

  private val _status = MutableStateFlow(
    StockfishEngineStatus(
      backend = if (detectedBinary != null) EngineBackendKind.STOCKFISH_UCI else EngineBackendKind.BUILTIN_KOTLIN,
      binaryPath = detectedBinary?.absolutePath,
      isReady = true,
      lowMemoryHashMb = 8,
    )
  )
  val status: StateFlow<StockfishEngineStatus> = _status.asStateFlow()

  private fun locateBundledStockfishBinary(): File? {
    val nativeDir = appContext.applicationInfo?.nativeLibraryDir ?: return null
    val candidate = File(nativeDir, "libstockfish.so")
    return if (candidate.exists() && candidate.canExecute()) candidate else null
  }

  suspend fun findBotMove(
    state: BoardState,
    bot: BotProfile,
    budget: SearchBudget,
    recentHashes: Map<String, Int> = emptyMap(),
  ): EngineMoveEvaluation? {
    val binary = detectedBinary
    if (binary != null && bot.randomnessCp < 150) {
      val uciResult = runStockfishUciSearch(
        binary = binary,
        state = state,
        budget = budget,
        skillLevel = mapBotToSkillLevel(bot),
        limitElo = if (bot.difficulty != BotDifficulty.MASTER) bot.rating.coerceIn(600, 2600) else null,
      )
      if (uciResult != null) return uciResult
    }

    val fallback = withContext(Dispatchers.Default) {
      fallbackEngine.findBestMove(
        state = state,
        budget = budget,
        randomnessCp = bot.randomnessCp,
        recentHashes = recentHashes,
      )
    } ?: return null

    val evalForWhite = if (state.turn == PieceColor.WHITE) fallback.scoreCp else -fallback.scoreCp
    val eval = PositionEvaluation(
      scoreCpForWhite = evalForWhite,
      mateInMoves = null,
      bestMoveUci = fallback.bestMove.toUci(),
      depthReached = fallback.depthReached,
      nodesVisited = fallback.nodesVisited,
      backend = EngineBackendKind.BUILTIN_KOTLIN,
    )
    _status.value = _status.value.copy(
      backend = EngineBackendKind.BUILTIN_KOTLIN,
      lastInfoSummary = "d${fallback.depthReached} • ${fallback.nodesVisited} nodes • ${formatEval(eval)}",
    )
    return EngineMoveEvaluation(fallback.bestMove, eval)
  }

  suspend fun evaluatePosition(
    state: BoardState,
    budget: SearchBudget,
  ): PositionEvaluation {
    val binary = detectedBinary
    if (binary != null) {
      val uciResult = runStockfishUciSearch(
        binary = binary,
        state = state,
        budget = budget,
        skillLevel = 20,
        limitElo = null,
      )
      if (uciResult != null) return uciResult.evaluation
    }

    val best = withContext(Dispatchers.Default) {
      fallbackEngine.findBestMove(state, budget, randomnessCp = 0)
    }
    val scoreForWhite = if (best != null) {
      if (state.turn == PieceColor.WHITE) best.scoreCp else -best.scoreCp
    } else {
      fallbackEngine.evaluatePositionForWhite(state)
    }
    val eval = PositionEvaluation(
      scoreCpForWhite = scoreForWhite,
      mateInMoves = null,
      bestMoveUci = best?.bestMove?.toUci(),
      depthReached = best?.depthReached ?: 1,
      nodesVisited = best?.nodesVisited ?: 1,
      backend = EngineBackendKind.BUILTIN_KOTLIN,
    )
    _status.value = _status.value.copy(
      lastInfoSummary = "d${eval.depthReached} • ${formatEval(eval)}",
    )
    return eval
  }

  fun evaluateStaticForWhite(state: BoardState): Int = fallbackEngine.evaluatePositionForWhite(state)

  suspend fun releaseSearchMemory() {
    fallbackEngine.releaseMemory()
    uciMutex.withLock {
      if (process != null) {
        runCatching {
          sendCommand("setoption name Clear Hash")
          sendCommand("isready")
          readUntil("readyok", 400L)
        }
      }
    }
  }

  fun stopSearch() {
    runCatching {
      writer?.apply {
        write("stop\n")
        flush()
      }
    }
  }

  fun shutdown() {
    fallbackEngine.releaseMemory()
    runCatching {
      writer?.apply {
        write("quit\n")
        flush()
      }
    }
    runCatching { writer?.close() }
    runCatching { reader?.close() }
    runCatching { process?.destroy() }
    writer = null
    reader = null
    process = null
  }

  private suspend fun runStockfishUciSearch(
    binary: File,
    state: BoardState,
    budget: SearchBudget,
    skillLevel: Int,
    limitElo: Int?,
  ): EngineMoveEvaluation? = uciMutex.withLock {
    withContext(Dispatchers.IO) {
      try {
        val desiredHashMb = if (budget.releaseAfterMove) 8 else 16
        if (!ensureProcessStarted(binary, desiredHashMb)) return@withContext null

        sendCommand("setoption name Skill Level value ${skillLevel.coerceIn(0, 20)}")
        if (limitElo != null) {
          sendCommand("setoption name UCI_LimitStrength value true")
          sendCommand("setoption name UCI_Elo value ${limitElo.coerceIn(1320, 3190)}")
        } else {
          sendCommand("setoption name UCI_LimitStrength value false")
        }
        sendCommand("isready")
        if (!readUntil("readyok", 1000L)) return@withContext null

        val fen = ChessNotation.toFen(state)
        sendCommand("position fen $fen")
        sendCommand("go depth ${budget.maxDepth} movetime ${budget.maxTimeMs}")

        var latestInfo = ParsedUciInfo()
        var bestMoveUci: String? = null
        val waitBudgetMs = budget.maxTimeMs + 1200L

        withTimeoutOrNull(waitBudgetMs) {
          val r = reader ?: return@withTimeoutOrNull
          while (true) {
            val line = r.readLine() ?: break
            UciLineParser.parseInfoLine(line)?.let { parsed ->
              latestInfo = ParsedUciInfo(
                depth = parsed.depth ?: latestInfo.depth,
                nodes = parsed.nodes ?: latestInfo.nodes,
                scoreCpForSideToMove = parsed.scoreCpForSideToMove ?: latestInfo.scoreCpForSideToMove,
                mateForSideToMove = parsed.mateForSideToMove ?: latestInfo.mateForSideToMove,
                pvFirstMove = parsed.pvFirstMove ?: latestInfo.pvFirstMove,
              )
            }
            val bm = UciLineParser.parseBestMoveLine(line)
            if (bm != null) {
              bestMoveUci = bm
              break
            }
          }
        }

        if (bestMoveUci == null) {
          sendCommand("stop")
          return@withContext null
        }

        val move = ChessNotation.parseUciMove(state, bestMoveUci!!) ?: return@withContext null
        val sign = if (state.turn == PieceColor.WHITE) 1 else -1
        val cpWhite = (latestInfo.scoreCpForSideToMove ?: 0) * sign
        val mateWhite = latestInfo.mateForSideToMove?.let { it * sign }

        val eval = PositionEvaluation(
          scoreCpForWhite = if (mateWhite != null) (if (mateWhite > 0) 10_000 else -10_000) else cpWhite,
          mateInMoves = mateWhite,
          bestMoveUci = bestMoveUci,
          depthReached = latestInfo.depth ?: budget.maxDepth,
          nodesVisited = latestInfo.nodes ?: 0,
          backend = EngineBackendKind.STOCKFISH_UCI,
        )

        _status.value = StockfishEngineStatus(
          backend = EngineBackendKind.STOCKFISH_UCI,
          binaryPath = binary.absolutePath,
          isReady = true,
          lowMemoryHashMb = desiredHashMb,
          lastInfoSummary = "d${eval.depthReached} • ${formatEval(eval)}",
        )

        if (budget.releaseAfterMove) {
          sendCommand("setoption name Clear Hash")
        }

        EngineMoveEvaluation(move, eval)
      } catch (_: Throwable) {
        shutdown()
        null
      }
    }
  }

  private fun ensureProcessStarted(binary: File, hashMb: Int): Boolean {
    if (process != null && activeHashMb == hashMb) return true
    if (process != null) shutdown()

    val pb = ProcessBuilder(binary.absolutePath)
    pb.redirectErrorStream(true)
    val proc = pb.start()
    process = proc
    writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
    reader = BufferedReader(InputStreamReader(proc.inputStream))
    activeHashMb = hashMb

    sendCommand("uci")
    if (!readUntil("uciok", 1500L)) {
      shutdown()
      return false
    }
    sendCommand("setoption name Threads value 1")
    sendCommand("setoption name Hash value $hashMb")
    return true
  }

  private fun sendCommand(cmd: String) {
    writer?.apply {
      write(cmd)
      write("\n")
      flush()
    }
  }

  private fun readUntil(expectedToken: String, timeoutMs: Long): Boolean {
    val r = reader ?: return false
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val line = r.readLine() ?: return false
      if (line.contains(expectedToken)) return true
    }
    return false
  }

  private fun mapBotToSkillLevel(bot: BotProfile): Int = when (bot.difficulty) {
    BotDifficulty.BEGINNER -> 1
    BotDifficulty.EASY -> 4
    BotDifficulty.NORMAL -> 8
    BotDifficulty.ADVANCED -> 12
    BotDifficulty.EXPERT -> 16
    BotDifficulty.MASTER -> 20
    BotDifficulty.CUSTOM -> ((bot.maxDepth * 4) - (bot.randomnessCp / 35)).coerceIn(0, 20)
  }

  companion object {
    fun formatEval(eval: PositionEvaluation): String {
      val mate = eval.mateInMoves
      if (mate != null) {
        return if (mate > 0) "M$mate" else "-M${kotlin.math.abs(mate)}"
      }
      return "%+.1f".format(eval.scoreCpForWhite / 100f)
    }
  }
}
