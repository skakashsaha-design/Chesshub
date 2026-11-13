package com.example

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bots.BotCatalog
import com.example.bots.BotTournamentEngine
import com.example.bots.TournamentProgress
import com.example.chess.BoardState
import com.example.chess.BotProfile
import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.GameResult
import com.example.chess.Move
import com.example.chess.OpeningBook
import com.example.chess.Opponent
import com.example.chess.PgnCodec
import com.example.chess.PieceCode
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.RecordedMove
import com.example.chess.TerminationReason
import com.example.chesscom.ChessComRepository
import com.example.database.AppSettingsEntity
import com.example.database.BotStatsEntity
import com.example.database.CachedProfileEntity
import com.example.database.ChessHubDatabase
import com.example.database.ChessHubRepository
import com.example.database.GameEntity
import com.example.database.GameSummaryRow
import com.example.database.PuzzleProgressEntity
import com.example.engine.DeviceMemoryAdvisor
import com.example.engine.MemoryProfile
import com.example.engine.StockfishEngineService
import com.example.engine.StockfishEngineStatus
import com.example.engine.StockfishUciWrapper
import com.example.lichess.ApiFetchOutcome
import com.example.lichess.LichessRepository
import com.example.lichess.PublicRecentGame
import com.example.music.MusicController
import com.example.music.RepeatMode
import com.example.offline.ChessClockTracker
import com.example.offline.SoundAndHaptics
import com.example.offline.TimeControl
import com.example.online.OnlineMatchRequest
import com.example.online.OnlineMultiplayerClient
import com.example.puzzles.ChessPuzzle
import com.example.puzzles.PuzzleCatalog
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChessHubViewModel(application: Application) : AndroidViewModel(application) {

  val memoryProfile: MemoryProfile = DeviceMemoryAdvisor.inspect(application)
  private val db = ChessHubDatabase.getInstance(application)
  private val repository = ChessHubRepository(db, memoryProfile.recommendLowMemoryMode)
  private val fallbackEngineWrapper = StockfishUciWrapper(application)
  private var engineService: StockfishEngineService? = null
  private var isEngineServiceBound = false
  private val soundHaptics = SoundAndHaptics(application)
  private val lichessRepo = LichessRepository()
  private val chessComRepo = ChessComRepository()
  val onlineClient = OnlineMultiplayerClient()
  val musicController = MusicController(application)

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val publicGamesType = Types.newParameterizedType(List::class.java, PublicRecentGame::class.java)
  private val publicGamesAdapter = moshi.adapter<List<PublicRecentGame>>(publicGamesType)

  private val clockTracker = ChessClockTracker(TimeControl.presets[6])
  private val repetitionCounts = HashMap<String, Int>()
  private var botThinkJob: Job? = null
  private var clockTickJob: Job? = null
  private var tournamentJob: Job? = null

  private val _currentSection = MutableStateFlow(AppSection.HOME)
  val currentSection: StateFlow<AppSection> = _currentSection.asStateFlow()

  val settings: StateFlow<AppSettingsEntity> = repository.settingsFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = AppSettingsEntity(lowMemoryMode = memoryProfile.recommendLowMemoryMode),
    )

  val recentGames: StateFlow<List<GameSummaryRow>> = repository.recentGamesFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val latestUnfinishedGame: StateFlow<GameEntity?> = repository.latestUnfinishedGameFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val puzzleProgress: StateFlow<List<PuzzleProgressEntity>> = repository.puzzleProgressFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val botStats: StateFlow<List<BotStatsEntity>> = repository.botStatsFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _historyQuery = MutableStateFlow("")
  val historyQuery: StateFlow<String> = _historyQuery.asStateFlow()

  private val _historySort = MutableStateFlow("NEWEST")
  val historySort: StateFlow<String> = _historySort.asStateFlow()

  private val _historyLimit = MutableStateFlow(40)

  val historyGames: StateFlow<List<GameSummaryRow>> = combine(
    _historyQuery,
    _historySort,
    _historyLimit,
  ) { q, s, l -> Triple(q, s, l) }
    .let { paramsFlow ->
      MutableStateFlow<List<GameSummaryRow>>(emptyList()).also { out ->
        viewModelScope.launch {
          paramsFlow.collect { (q, s, l) ->
            repository.searchGames(q, s, l).collect { rows ->
              out.value = rows
            }
          }
        }
      }
    }

  private val _gameState = MutableStateFlow(ActiveGameUiState())
  val gameState: StateFlow<ActiveGameUiState> = _gameState.asStateFlow()

  private val _puzzleState = MutableStateFlow(createPuzzleUiState(PuzzleCatalog.puzzles.first()))
  val puzzleState: StateFlow<PuzzleUiState> = _puzzleState.asStateFlow()

  private val _lichessProfile = MutableStateFlow(PlatformProfileUiState())
  val lichessProfile: StateFlow<PlatformProfileUiState> = _lichessProfile.asStateFlow()

  private val _chessComProfile = MutableStateFlow(PlatformProfileUiState())
  val chessComProfile: StateFlow<PlatformProfileUiState> = _chessComProfile.asStateFlow()

  private val _tournamentProgress = MutableStateFlow<TournamentProgress?>(null)
  val tournamentProgress: StateFlow<TournamentProgress?> = _tournamentProgress.asStateFlow()

  private val _engineStatus = MutableStateFlow(fallbackEngineWrapper.status.value)
  val engineStatus: StateFlow<StockfishEngineStatus> = _engineStatus.asStateFlow()

  private var evalJob: Job? = null

  private val engineServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      val svc = (binder as? StockfishEngineService.LocalBinder)?.getService() ?: return
      engineService = svc
      isEngineServiceBound = true
      viewModelScope.launch {
        svc.engineStatus.collect { st -> _engineStatus.value = st }
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      engineService = null
      isEngineServiceBound = false
    }
  }

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  init {
    bindEngineService()
    viewModelScope.launch {
      val s = repository.ensureInitializedSettings()
      onlineClient.updateServerConfiguration(s.onlineServerUrl)
      musicController.applySavedPrefs(
        volume = s.musicVolume,
        shuffle = s.musicShuffle,
        repeatMode = runCatching { RepeatMode.valueOf(s.musicRepeatMode) }.getOrDefault(RepeatMode.OFF),
      )
      if (s.lichessUsername.isNotBlank()) {
        loadCachedProfile("LICHESS")
      }
      if (s.chessComUsername.isNotBlank()) {
        loadCachedProfile("CHESSCOM")
      }
    }
    startNewGame(
      mode = GameMode.HUMAN_VS_BOT,
      white = Opponent.Human("You"),
      black = Opponent.Bot(BotCatalog.presets[1]),
      timeControl = TimeControl.presets[6],
      navigateToPlay = false,
    )
  }

  fun navigateTo(section: AppSection) {
    _currentSection.value = section
  }

  fun clearSnackbar() {
    _snackbarMessage.value = null
  }

  fun postMessage(msg: String) {
    _snackbarMessage.value = msg
  }

  // region Game Lifecycle & Moves

  fun startQuickPlay() {
    startNewGame(
      mode = GameMode.HUMAN_VS_BOT,
      white = Opponent.Human("You"),
      black = Opponent.Bot(BotCatalog.presets[2]),
      timeControl = TimeControl.presets[4], // 5+0
      navigateToPlay = true,
    )
  }

  fun startNewGame(
    mode: GameMode,
    white: Opponent,
    black: Opponent,
    timeControl: TimeControl,
    trainingMode: Boolean = false,
    navigateToPlay: Boolean = true,
    initialBoard: BoardState = ChessRules.initialBoardState(),
    initialMoves: List<RecordedMove> = emptyList(),
  ) {
    botThinkJob?.cancel()
    clockTickJob?.cancel()
    repetitionCounts.clear()
    repetitionCounts[ChessRules.repetitionKey(initialBoard)] = 1

    clockTracker.reset(timeControl)
    if (!timeControl.isUnlimited && mode != GameMode.BOT_TRAINING) {
      clockTracker.start(initialBoard.turn)
    }

    val flipped = white is Opponent.Bot && black is Opponent.Human
    _gameState.value = ActiveGameUiState(
      gameId = null,
      mode = mode,
      whiteOpponent = white,
      blackOpponent = black,
      boardState = initialBoard,
      moves = initialMoves,
      stateHistory = listOf(initialBoard),
      legalMoves = ChessRules.legalMoves(initialBoard),
      isFlipped = flipped,
      timeControl = timeControl,
      whiteTimeMs = timeControl.initialMillis,
      blackTimeMs = timeControl.initialMillis,
      opening = OpeningBook.recognize(initialMoves.map { it.san }),
      trainingMode = trainingMode,
      evalCpForWhite = if (trainingMode) {
        engineService?.evaluateStaticForWhite(initialBoard)
          ?: fallbackEngineWrapper.evaluateStaticForWhite(initialBoard)
      } else 0,
      evalMateIn = null,
      startedAtMillis = System.currentTimeMillis(),
    )

    if (navigateToPlay) {
      _currentSection.value = AppSection.PLAY
    }
    startClockTickerIfNeeded()
    maybeTriggerBotTurn()
  }

  fun onSquareTapped(square: Int) {
    val g = _gameState.value
    if (g.replayIndex != null || g.result != GameResult.IN_PROGRESS || g.isBotThinking) return
    val currentTurnOpponent = if (g.boardState.turn == PieceColor.WHITE) g.whiteOpponent else g.blackOpponent
    if (currentTurnOpponent !is Opponent.Human) return

    // Handle move confirmation if enabled
    val pending = g.pendingConfirmMove
    if (pending != null) {
      if (pending.to == square) {
        executeMove(pending)
        return
      } else {
        _gameState.update { it.copy(pendingConfirmMove = null) }
      }
    }

    val selected = g.selectedSquare
    if (selected != null) {
      val matchingMoves = g.legalMoves.filter { it.from == selected && it.to == square }
      if (matchingMoves.isNotEmpty()) {
        if (matchingMoves.size > 1 && matchingMoves.any { it.promotion != null }) {
          _gameState.update {
            it.copy(pendingPromotionFrom = selected, pendingPromotionTo = square)
          }
          return
        }
        val move = matchingMoves.first()
        if (settings.value.confirmMoves) {
          _gameState.update { it.copy(pendingConfirmMove = move) }
          return
        }
        executeMove(move)
        return
      }
    }

    val pieceAtSquare = g.boardState.board[square]
    if (pieceAtSquare != PieceCode.EMPTY && PieceCode.colorOf(pieceAtSquare) == g.boardState.turn) {
      soundHaptics.playUiClick(settings.value)
      _gameState.update { it.copy(selectedSquare = square, pendingConfirmMove = null) }
    } else {
      _gameState.update { it.copy(selectedSquare = null, pendingConfirmMove = null) }
    }
  }

  fun onPromotionSelected(pieceType: PieceType) {
    val g = _gameState.value
    val from = g.pendingPromotionFrom ?: return
    val to = g.pendingPromotionTo ?: return
    val move = g.legalMoves.firstOrNull { it.from == from && it.to == to && it.promotion == pieceType }
    _gameState.update { it.copy(pendingPromotionFrom = null, pendingPromotionTo = null) }
    if (move != null) executeMove(move)
  }

  fun dismissPromotion() {
    _gameState.update { it.copy(pendingPromotionFrom = null, pendingPromotionTo = null) }
  }

  private fun executeMove(move: Move) {
    val g = _gameState.value
    if (g.result != GameResult.IN_PROGRESS) return
    val mover = g.boardState.turn
    val san = ChessNotation.toSan(g.boardState, move, g.legalMoves)
    val nextState = ChessRules.applyMove(g.boardState, move)
    val fenAfter = ChessNotation.toFen(nextState)

    val repKey = ChessRules.repetitionKey(nextState)
    repetitionCounts[repKey] = (repetitionCounts[repKey] ?: 0) + 1

    val newMoves = g.moves + RecordedMove(move, san, move.toUci(), fenAfter)
    val newHistory = g.stateHistory + nextState
    val (res, reason) = ChessRules.evaluateTerminalStatus(nextState, repetitionCounts)
    val opening = OpeningBook.recognize(newMoves.map { it.san })

    if (!g.timeControl.isUnlimited && !g.trainingMode) {
      clockTracker.onMoveCompleted(mover)
      if (res != GameResult.IN_PROGRESS) clockTracker.pause()
    }
    val (wMs, bMs) = clockTracker.snapshot()

    val s = settings.value
    val isCheck = ChessRules.isInCheck(nextState)
    when {
      res != GameResult.IN_PROGRESS -> {
        soundHaptics.playGameEndSound(s)
        soundHaptics.hapticTap(s, strong = true)
      }
      isCheck -> {
        soundHaptics.playCheckSound(s)
        soundHaptics.hapticTap(s, strong = true)
      }
      move.capturedPiece != PieceCode.EMPTY || move.isEnPassant -> {
        soundHaptics.playCaptureSound(s)
        soundHaptics.hapticTap(s)
      }
      else -> {
        soundHaptics.playMoveSound(s)
        soundHaptics.hapticTap(s)
      }
    }

    val nextLegal = if (res == GameResult.IN_PROGRESS) ChessRules.legalMoves(nextState) else emptyList()
    val staticEvalCp = if (g.trainingMode) {
      engineService?.evaluateStaticForWhite(nextState)
        ?: fallbackEngineWrapper.evaluateStaticForWhite(nextState)
    } else g.evalCpForWhite

    _gameState.update {
      it.copy(
        boardState = nextState,
        moves = newMoves,
        stateHistory = newHistory,
        legalMoves = nextLegal,
        selectedSquare = null,
        pendingConfirmMove = null,
        lastMoveFrom = move.from,
        lastMoveTo = move.to,
        whiteTimeMs = wMs,
        blackTimeMs = bMs,
        result = res,
        termination = reason,
        opening = opening ?: it.opening,
        drawOfferPendingFrom = null,
        evalCpForWhite = staticEvalCp,
        evalMateIn = null,
        hintMove = null,
      )
    }

    if (g.trainingMode && res == GameResult.IN_PROGRESS) {
      refreshPositionEvaluation(nextState)
    }

    if (res != GameResult.IN_PROGRESS) {
      onGameFinished(res, reason)
    } else {
      persistAutosaveIfAppropriate()
      maybeTriggerBotTurn()
    }
  }

  private fun bindEngineService() {
    val ctx = getApplication<Application>()
    runCatching {
      val intent = Intent(ctx, StockfishEngineService::class.java)
      ctx.bindService(intent, engineServiceConnection, Context.BIND_AUTO_CREATE)
    }
  }

  private fun refreshPositionEvaluation(state: BoardState) {
    evalJob?.cancel()
    evalJob = viewModelScope.launch {
      val curSettings = settings.value
      val budget = DeviceMemoryAdvisor.computeSearchBudget(
        requestedDepth = 3,
        requestedTimeMs = 350L,
        lowMemoryMode = curSettings.lowMemoryMode,
        batterySavingMode = curSettings.batterySavingMode,
        isLowRamHardware = memoryProfile.isLowRamHardware,
      )
      val eval = engineService?.evaluatePosition(state, budget)
        ?: fallbackEngineWrapper.evaluatePosition(state, budget)
      if (_gameState.value.boardState == state) {
        _gameState.update {
          it.copy(
            evalCpForWhite = eval.scoreCpForWhite,
            evalMateIn = eval.mateInMoves,
          )
        }
      }
    }
  }

  private fun maybeTriggerBotTurn() {
    val g = _gameState.value
    if (g.result != GameResult.IN_PROGRESS || g.replayIndex != null) return
    val turnOpp = if (g.boardState.turn == PieceColor.WHITE) g.whiteOpponent else g.blackOpponent
    if (turnOpp !is Opponent.Bot) return

    botThinkJob?.cancel()
    botThinkJob = viewModelScope.launch {
      _gameState.update { it.copy(isBotThinking = true) }
      // Small breathing delay in Bot vs Bot so user can follow moves smoothly
      if (g.mode == GameMode.BOT_VS_BOT) {
        delay(320L)
      }
      val curSettings = settings.value
      val budget = DeviceMemoryAdvisor.computeSearchBudget(
        requestedDepth = turnOpp.profile.maxDepth,
        requestedTimeMs = turnOpp.profile.maxThinkTimeMs,
        lowMemoryMode = curSettings.lowMemoryMode,
        batterySavingMode = curSettings.batterySavingMode,
        isLowRamHardware = memoryProfile.isLowRamHardware,
      )
      val repsSnapshot = HashMap(repetitionCounts)
      val svc = engineService
      val searchRes = if (svc != null) {
        svc.computeBotMove(g.boardState, turnOpp.profile, budget, repsSnapshot)
      } else {
        fallbackEngineWrapper.findBotMove(g.boardState, turnOpp.profile, budget, repsSnapshot)
      }
      _gameState.update { it.copy(isBotThinking = false) }
      if (searchRes != null && _gameState.value.boardState == g.boardState &&
        _gameState.value.result == GameResult.IN_PROGRESS
      ) {
        executeMove(searchRes.bestMove)
      }
    }
  }

  private fun startClockTickerIfNeeded() {
    clockTickJob?.cancel()
    val g = _gameState.value
    if (g.timeControl.isUnlimited || g.trainingMode) return
    clockTickJob = viewModelScope.launch {
      while (isActive) {
        delay(250L)
        val cur = _gameState.value
        if (cur.result != GameResult.IN_PROGRESS || cur.replayIndex != null) break
        val (w, b) = clockTracker.snapshot()
        val timedOut = clockTracker.timedOutSide()
        if (timedOut != null) {
          val res = if (timedOut == PieceColor.WHITE) GameResult.BLACK_WIN else GameResult.WHITE_WIN
          _gameState.update {
            it.copy(
              whiteTimeMs = w,
              blackTimeMs = b,
              result = res,
              termination = TerminationReason.TIMEOUT,
              isBotThinking = false,
            )
          }
          botThinkJob?.cancel()
          soundHaptics.playGameEndSound(settings.value)
          onGameFinished(res, TerminationReason.TIMEOUT)
          break
        } else {
          _gameState.update { it.copy(whiteTimeMs = w, blackTimeMs = b) }
        }
      }
    }
  }

  fun undoMove() {
    val g = _gameState.value
    if (g.mode == GameMode.ONLINE || g.replayIndex != null) return
    if (g.moves.isEmpty() || g.isBotThinking) return

    val stepsToUndo = if (g.mode == GameMode.HUMAN_VS_BOT && g.moves.size >= 2 &&
      g.result == GameResult.IN_PROGRESS
    ) 2 else 1

    val newMoves = g.moves.dropLast(stepsToUndo)
    val newHistory = g.stateHistory.dropLast(stepsToUndo)
    val targetState = newHistory.last()

    repetitionCounts.clear()
    newHistory.forEach { st ->
      val key = ChessRules.repetitionKey(st)
      repetitionCounts[key] = (repetitionCounts[key] ?: 0) + 1
    }

    _gameState.update {
      it.copy(
        boardState = targetState,
        moves = newMoves,
        stateHistory = newHistory,
        legalMoves = ChessRules.legalMoves(targetState),
        selectedSquare = null,
        pendingConfirmMove = null,
        lastMoveFrom = newMoves.lastOrNull()?.move?.from,
        lastMoveTo = newMoves.lastOrNull()?.move?.to,
        result = GameResult.IN_PROGRESS,
        termination = TerminationReason.NONE,
        opening = OpeningBook.recognize(newMoves.map { m -> m.san }),
        hintMove = null,
      )
    }
  }

  fun requestTrainingHint() {
    val g = _gameState.value
    if (g.result != GameResult.IN_PROGRESS) return
    viewModelScope.launch {
      val budget = DeviceMemoryAdvisor.computeSearchBudget(
        requestedDepth = 3,
        requestedTimeMs = 500L,
        lowMemoryMode = settings.value.lowMemoryMode,
        batterySavingMode = false,
        isLowRamHardware = memoryProfile.isLowRamHardware,
      )
      val eval = engineService?.evaluatePosition(g.boardState, budget)
        ?: fallbackEngineWrapper.evaluatePosition(g.boardState, budget)
      val bestMove = eval.bestMoveUci?.let { ChessNotation.parseUciMove(g.boardState, it) }
      if (bestMove != null) {
        _gameState.update {
          it.copy(
            hintMove = bestMove,
            evalCpForWhite = eval.scoreCpForWhite,
            evalMateIn = eval.mateInMoves,
          )
        }
      }
    }
  }

  fun flipBoard() {
    _gameState.update { it.copy(isFlipped = !it.isFlipped) }
  }

  fun resignGame() {
    val g = _gameState.value
    if (g.result != GameResult.IN_PROGRESS) return
    botThinkJob?.cancel()
    clockTracker.pause()
    val loserColor = when (g.mode) {
      GameMode.HUMAN_VS_BOT, GameMode.BOT_TRAINING ->
        if (g.whiteOpponent is Opponent.Human) PieceColor.WHITE else PieceColor.BLACK
      else -> g.boardState.turn
    }
    val res = if (loserColor == PieceColor.WHITE) GameResult.BLACK_WIN else GameResult.WHITE_WIN
    _gameState.update {
      it.copy(result = res, termination = TerminationReason.RESIGNATION, isBotThinking = false)
    }
    soundHaptics.playGameEndSound(settings.value)
    onGameFinished(res, TerminationReason.RESIGNATION)
  }

  fun offerDraw() {
    val g = _gameState.value
    if (g.result != GameResult.IN_PROGRESS) return
    if (g.mode == GameMode.HUMAN_VS_BOT || g.mode == GameMode.BOT_TRAINING) {
      val evalForWhite = engineService?.evaluateStaticForWhite(g.boardState)
        ?: fallbackEngineWrapper.evaluateStaticForWhite(g.boardState)
      val humanIsWhite = g.whiteOpponent is Opponent.Human
      val botAdvantage = if (humanIsWhite) -evalForWhite else evalForWhite
      if (botAdvantage <= 40 && g.moves.size >= 10) {
        acceptDraw()
      } else {
        postMessage("Bot declined the draw offer.")
      }
    } else if (g.mode == GameMode.HUMAN_VS_HUMAN) {
      _gameState.update { it.copy(drawOfferPendingFrom = g.boardState.turn) }
    }
  }

  fun acceptDraw() {
    botThinkJob?.cancel()
    clockTracker.pause()
    _gameState.update {
      it.copy(
        result = GameResult.DRAW,
        termination = TerminationReason.DRAW_AGREEMENT,
        drawOfferPendingFrom = null,
        isBotThinking = false,
      )
    }
    soundHaptics.playGameEndSound(settings.value)
    onGameFinished(GameResult.DRAW, TerminationReason.DRAW_AGREEMENT)
  }

  fun declineDraw() {
    _gameState.update { it.copy(drawOfferPendingFrom = null) }
  }

  fun restartGame() {
    val g = _gameState.value
    startNewGame(
      mode = g.mode,
      white = g.whiteOpponent,
      black = g.blackOpponent,
      timeControl = g.timeControl,
      trainingMode = g.trainingMode,
      navigateToPlay = false,
    )
  }

  private fun onGameFinished(res: GameResult, reason: TerminationReason) {
    val g = _gameState.value
    viewModelScope.launch {
      saveCurrentGameToDb(isFinished = true)
      if (g.mode == GameMode.HUMAN_VS_BOT) {
        val humanWhite = g.whiteOpponent is Opponent.Human
        val bot = (if (humanWhite) g.blackOpponent else g.whiteOpponent) as? Opponent.Bot
        if (bot != null) {
          val score = when (res) {
            GameResult.DRAW -> 0.5
            GameResult.WHITE_WIN -> if (humanWhite) 1.0 else 0.0
            GameResult.BLACK_WIN -> if (!humanWhite) 1.0 else 0.0
            GameResult.IN_PROGRESS -> return@launch
          }
          repository.recordBotMatchOutcome(bot.profile.id, bot.profile.rating, score)
        }
      } else if (g.mode == GameMode.HUMAN_VS_HUMAN) {
        repository.recordHumanMatchOutcome(res.pgnValue)
      }
    }
  }

  private fun persistAutosaveIfAppropriate() {
    val g = _gameState.value
    if (g.moves.isEmpty() || g.mode == GameMode.BOT_VS_BOT) return
    viewModelScope.launch {
      saveCurrentGameToDb(isFinished = false)
    }
  }

  fun saveCurrentGameManually() {
    viewModelScope.launch {
      saveCurrentGameToDb(isFinished = _gameState.value.result != GameResult.IN_PROGRESS)
      postMessage(getApplication<Application>().getString(R.string.play_game_saved))
    }
  }

  private suspend fun saveCurrentGameToDb(isFinished: Boolean) {
    val g = _gameState.value
    val pgn = PgnCodec.export(
      whiteName = g.whiteOpponent.displayName,
      blackName = g.blackOpponent.displayName,
      result = g.result,
      dateMillis = g.startedAtMillis,
      timeControl = g.timeControl.label,
      eco = g.opening?.eco,
      openingName = g.opening?.name,
      moves = g.moves,
    )
    val durationSec = ((System.currentTimeMillis() - g.startedAtMillis) / 1000L).toInt().coerceAtLeast(1)
    val savedId = repository.saveGame(
      GameEntity(
        id = g.gameId ?: 0L,
        whiteName = g.whiteOpponent.displayName,
        blackName = g.blackOpponent.displayName,
        result = g.result.pgnValue,
        termination = g.termination.name,
        dateMillis = g.startedAtMillis,
        timeControlLabel = g.timeControl.label,
        pgn = pgn,
        fen = ChessNotation.toFen(g.boardState),
        movesCount = (g.moves.size + 1) / 2,
        durationSeconds = durationSec,
        gameType = g.mode.name,
        ecoCode = g.opening?.eco,
        openingName = g.opening?.name,
        isFinished = isFinished,
        updatedAtMillis = System.currentTimeMillis(),
      )
    )
    if (g.gameId == null) {
      _gameState.update { it.copy(gameId = savedId) }
    }
  }

  fun resumeUnfinishedGame(game: GameEntity) {
    val parsed = PgnCodec.parse(game.pgn).getOrNull()
    val startBoard = ChessRules.initialBoardState()
    val history = ArrayList<BoardState>().apply { add(startBoard) }
    var cur = startBoard
    parsed?.moves?.forEach { rm ->
      cur = ChessRules.applyMove(cur, rm.move)
      history.add(cur)
    }
    val tc = TimeControl.presets.firstOrNull { it.label == game.timeControlLabel } ?: TimeControl.presets[6]
    val mode = runCatching { GameMode.valueOf(game.gameType) }.getOrDefault(GameMode.HUMAN_VS_HUMAN)
    startNewGame(
      mode = mode,
      white = Opponent.Human(game.whiteName),
      black = if (mode == GameMode.HUMAN_VS_BOT) {
        Opponent.Bot(BotCatalog.presets.firstOrNull { it.name == game.blackName } ?: BotCatalog.presets[1])
      } else Opponent.Human(game.blackName),
      timeControl = tc,
      navigateToPlay = true,
      initialBoard = cur,
      initialMoves = parsed?.moves ?: emptyList(),
    )
    _gameState.update {
      it.copy(
        gameId = game.id,
        stateHistory = history,
        lastMoveFrom = parsed?.moves?.lastOrNull()?.move?.from,
        lastMoveTo = parsed?.moves?.lastOrNull()?.move?.to,
      )
    }
  }

  fun loadGameForReplay(gameId: Long) {
    viewModelScope.launch {
      val entity = repository.getGameById(gameId) ?: return@launch
      loadPgnTextForReplay(entity.pgn, entity.whiteName, entity.blackName, entity.id)
    }
  }

  fun loadPgnTextForReplay(
    pgnText: String,
    fallbackWhite: String = "White",
    fallbackBlack: String = "Black",
    existingId: Long? = null,
  ) {
    val parsed = PgnCodec.parse(pgnText).getOrElse {
      postMessage(getApplication<Application>().getString(R.string.history_pgn_invalid))
      return
    }
    botThinkJob?.cancel()
    clockTickJob?.cancel()
    val start = ChessRules.initialBoardState()
    val history = ArrayList<BoardState>(parsed.moves.size + 1).apply { add(start) }
    var cur = start
    parsed.moves.forEach { rm ->
      cur = ChessRules.applyMove(cur, rm.move)
      history.add(cur)
    }
    val white = parsed.tags["White"] ?: fallbackWhite
    val black = parsed.tags["Black"] ?: fallbackBlack
    _gameState.value = ActiveGameUiState(
      gameId = existingId,
      mode = GameMode.HUMAN_VS_HUMAN,
      whiteOpponent = Opponent.Human(white),
      blackOpponent = Opponent.Human(black),
      boardState = history.last(),
      moves = parsed.moves,
      stateHistory = history,
      legalMoves = emptyList(),
      lastMoveFrom = parsed.moves.lastOrNull()?.move?.from,
      lastMoveTo = parsed.moves.lastOrNull()?.move?.to,
      result = parsed.result,
      opening = parsed.opening,
      replayIndex = parsed.moves.size,
    )
    _currentSection.value = AppSection.PLAY
  }

  fun setReplayStep(step: Int) {
    val g = _gameState.value
    val clamped = step.coerceIn(0, g.stateHistory.lastIndex)
    val lastMove = if (clamped > 0) g.moves[clamped - 1].move else null
    _gameState.update {
      it.copy(
        replayIndex = clamped,
        boardState = g.stateHistory[clamped],
        lastMoveFrom = lastMove?.from,
        lastMoveTo = lastMove?.to,
      )
    }
  }

  fun exitReplayMode() {
    val g = _gameState.value
    val lastState = g.stateHistory.last()
    val lastMove = g.moves.lastOrNull()?.move
    _gameState.update {
      it.copy(
        replayIndex = null,
        boardState = lastState,
        legalMoves = if (it.result == GameResult.IN_PROGRESS) ChessRules.legalMoves(lastState) else emptyList(),
        lastMoveFrom = lastMove?.from,
        lastMoveTo = lastMove?.to,
      )
    }
  }

  fun exportCurrentGamePgn(): String {
    val g = _gameState.value
    return PgnCodec.export(
      whiteName = g.whiteOpponent.displayName,
      blackName = g.blackOpponent.displayName,
      result = g.result,
      dateMillis = g.startedAtMillis,
      timeControl = g.timeControl.label,
      eco = g.opening?.eco,
      openingName = g.opening?.name,
      moves = g.moves,
    )
  }

  fun importPgnToDatabase(rawPgn: String): Boolean {
    val parsed = PgnCodec.parse(rawPgn).getOrElse {
      postMessage(getApplication<Application>().getString(R.string.history_pgn_invalid))
      return false
    }
    viewModelScope.launch {
      val white = parsed.tags["White"] ?: "White"
      val black = parsed.tags["Black"] ?: "Black"
      val tc = parsed.tags["TimeControl"] ?: "Imported"
      repository.saveGame(
        GameEntity(
          whiteName = white,
          blackName = black,
          result = parsed.result.pgnValue,
          termination = "IMPORTED",
          dateMillis = System.currentTimeMillis(),
          timeControlLabel = tc,
          pgn = rawPgn.trim(),
          fen = ChessNotation.toFen(parsed.finalState),
          movesCount = (parsed.moves.size + 1) / 2,
          durationSeconds = 0,
          gameType = "IMPORTED",
          ecoCode = parsed.opening?.eco ?: parsed.tags["ECO"],
          openingName = parsed.opening?.name ?: parsed.tags["Opening"],
          isFinished = true,
        )
      )
      postMessage(getApplication<Application>().getString(R.string.history_pgn_imported))
    }
    return true
  }

  fun createSharePgnIntent(pgn: String, fileName: String = "chesshub_game.pgn"): Intent? {
    val ctx = getApplication<Application>()
    return runCatching {
      val dir = File(ctx.cacheDir, "pgn").apply { mkdirs() }
      val file = File(dir, fileName)
      file.writeText(pgn)
      val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
      Intent(Intent.ACTION_SEND).apply {
        type = "application/x-chess-pgn"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, pgn)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }.getOrNull()
  }

  // endregion

  // region History & Tournaments

  fun updateHistoryQuery(query: String) {
    _historyQuery.value = query
  }

  fun updateHistorySort(sort: String) {
    _historySort.value = sort
  }

  fun loadMoreHistory() {
    _historyLimit.update { it + 40 }
  }

  fun deleteGame(id: Long) {
    viewModelScope.launch { repository.deleteGame(id) }
  }

  fun startFourBotTournament() {
    if (tournamentJob?.isActive == true) return
    tournamentJob = viewModelScope.launch {
      val participants = BotCatalog.presets.take(4)
      val s = settings.value
      BotTournamentEngine.runRoundRobin(
        participants = participants,
        lowMemoryMode = s.lowMemoryMode,
        isLowRamHardware = memoryProfile.isLowRamHardware,
      ) { progress ->
        _tournamentProgress.value = progress
      }
    }
  }

  fun cancelTournament() {
    tournamentJob?.cancel()
    _tournamentProgress.update { it?.copy(isRunning = false) }
  }

  // endregion

  // region Puzzles

  private fun createPuzzleUiState(puzzle: ChessPuzzle): PuzzleUiState {
    val board = ChessNotation.fromFen(puzzle.fen) ?: ChessRules.initialBoardState()
    return PuzzleUiState(
      puzzle = puzzle,
      boardState = board,
      stepIndex = 0,
      legalMoves = ChessRules.legalMoves(board),
    )
  }

  fun loadRandomPuzzle() {
    val next = PuzzleCatalog.randomPuzzle(_puzzleState.value.puzzle.id)
    _puzzleState.value = createPuzzleUiState(next)
  }

  fun loadPuzzleById(id: String) {
    val found = PuzzleCatalog.puzzles.firstOrNull { it.id == id } ?: return
    _puzzleState.value = createPuzzleUiState(found)
  }

  fun retryCurrentPuzzle() {
    _puzzleState.value = createPuzzleUiState(_puzzleState.value.puzzle)
  }

  fun revealPuzzleHint() {
    _puzzleState.update { it.copy(showHint = true) }
  }

  fun onPuzzleSquareTapped(square: Int) {
    val p = _puzzleState.value
    if (p.isSolved) return
    val sel = p.selectedSquare
    if (sel != null) {
      val candidates = p.legalMoves.filter { it.from == sel && it.to == square }
      if (candidates.isNotEmpty()) {
        val expectedUci = p.puzzle.solutionUci.getOrNull(p.stepIndex) ?: return
        val matched = candidates.firstOrNull { it.toUci() == expectedUci }
        if (matched != null) {
          applyCorrectPuzzleMove(matched)
        } else {
          soundHaptics.hapticTap(settings.value, strong = true)
          _puzzleState.update {
            it.copy(
              selectedSquare = null,
              hadMistake = true,
              statusMessage = getApplication<Application>().getString(R.string.puzzles_wrong),
            )
          }
        }
        return
      }
    }

    val piece = p.boardState.board[square]
    if (piece != PieceCode.EMPTY && PieceCode.colorOf(piece) == p.boardState.turn) {
      _puzzleState.update { it.copy(selectedSquare = square) }
    } else {
      _puzzleState.update { it.copy(selectedSquare = null) }
    }
  }

  private fun applyCorrectPuzzleMove(move: Move) {
    val p = _puzzleState.value
    var nextBoard = ChessRules.applyMove(p.boardState, move)
    var nextStep = p.stepIndex + 1
    var lastFrom = move.from
    var lastTo = move.to

    // Play opponent forced reply if present
    if (nextStep < p.puzzle.solutionUci.size) {
      val replyUci = p.puzzle.solutionUci[nextStep]
      val replyMove = ChessNotation.parseUciMove(nextBoard, replyUci)
      if (replyMove != null) {
        nextBoard = ChessRules.applyMove(nextBoard, replyMove)
        lastFrom = replyMove.from
        lastTo = replyMove.to
        nextStep++
      }
    }

    val solved = nextStep >= p.puzzle.solutionUci.size
    val s = settings.value
    if (solved) {
      soundHaptics.playGameEndSound(s)
      soundHaptics.hapticTap(s, strong = true)
      viewModelScope.launch {
        repository.recordPuzzleOutcome(p.puzzle.id, p.puzzle.rating, !p.hadMistake)
      }
    } else {
      soundHaptics.playMoveSound(s)
    }

    _puzzleState.update {
      it.copy(
        boardState = nextBoard,
        stepIndex = nextStep,
        selectedSquare = null,
        legalMoves = if (solved) emptyList() else ChessRules.legalMoves(nextBoard),
        lastMoveFrom = lastFrom,
        lastMoveTo = lastTo,
        isSolved = solved,
        statusMessage = if (solved) {
          getApplication<Application>().getString(R.string.puzzles_solved_msg)
        } else {
          getApplication<Application>().getString(R.string.puzzles_correct)
        },
      )
    }
  }

  // endregion

  // region Lichess & Chess.com Profiles

  private suspend fun loadCachedProfile(platform: String) {
    val cached = db.cachedProfileDao().getByPlatform(platform) ?: return
    val games = runCatching { publicGamesAdapter.fromJson(cached.recentGamesJson) }.getOrNull().orEmpty()
    val ui = PlatformProfileUiState(
      username = cached.username,
      title = cached.title,
      ratingsSummary = cached.ratingsSummary,
      totalGames = cached.totalGames,
      wins = cached.wins,
      losses = cached.losses,
      draws = cached.draws,
      profileUrl = cached.profileUrl,
      recentGames = games,
      isCachedOffline = true,
    )
    if (platform == "LICHESS") _lichessProfile.value = ui else _chessComProfile.value = ui
  }

  fun linkAndRefreshLichess(username: String) {
    val clean = username.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      repository.updateSettings { it.copy(lichessUsername = clean) }
      refreshLichess(clean)
    }
  }

  fun refreshLichess(explicitUsername: String? = null) {
    val user = explicitUsername ?: settings.value.lichessUsername
    if (user.isBlank()) return
    viewModelScope.launch {
      _lichessProfile.update { it.copy(isLoading = true, username = user, errorMessage = null) }
      when (val res = lichessRepo.fetchProfileAndGames(user)) {
        is ApiFetchOutcome.Success -> {
          val d = res.data
          val gamesJson = runCatching { publicGamesAdapter.toJson(d.recentGames) }.getOrDefault("[]")
          repository.saveCachedProfile(
            CachedProfileEntity(
              platform = "LICHESS",
              username = d.username,
              title = d.title,
              ratingsSummary = d.ratingsSummary,
              totalGames = d.totalGames,
              wins = d.wins,
              losses = d.losses,
              draws = d.draws,
              recentGamesJson = gamesJson,
              profileUrl = d.profileUrl,
            )
          )
          _lichessProfile.value = PlatformProfileUiState(
            isLoading = false,
            username = d.username,
            title = d.title,
            ratingsSummary = d.ratingsSummary,
            totalGames = d.totalGames,
            wins = d.wins,
            losses = d.losses,
            draws = d.draws,
            profileUrl = d.profileUrl,
            recentGames = d.recentGames,
            isCachedOffline = false,
          )
        }
        is ApiFetchOutcome.Failure -> {
          loadCachedProfile("LICHESS")
          _lichessProfile.update { it.copy(isLoading = false, username = user, errorMessage = res.message) }
        }
      }
    }
  }

  fun unlinkLichess() {
    viewModelScope.launch {
      repository.updateSettings { it.copy(lichessUsername = "") }
      repository.deleteCachedProfile("LICHESS")
      _lichessProfile.value = PlatformProfileUiState()
    }
  }

  fun linkAndRefreshChessCom(username: String) {
    val clean = username.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      repository.updateSettings { it.copy(chessComUsername = clean) }
      refreshChessCom(clean)
    }
  }

  fun refreshChessCom(explicitUsername: String? = null) {
    val user = explicitUsername ?: settings.value.chessComUsername
    if (user.isBlank()) return
    viewModelScope.launch {
      _chessComProfile.update { it.copy(isLoading = true, username = user, errorMessage = null) }
      when (val res = chessComRepo.fetchProfileAndGames(user)) {
        is ApiFetchOutcome.Success -> {
          val d = res.data
          val gamesJson = runCatching { publicGamesAdapter.toJson(d.recentGames) }.getOrDefault("[]")
          repository.saveCachedProfile(
            CachedProfileEntity(
              platform = "CHESSCOM",
              username = d.username,
              title = d.title,
              ratingsSummary = d.ratingsSummary,
              totalGames = d.totalGames,
              wins = d.wins,
              losses = d.losses,
              draws = d.draws,
              recentGamesJson = gamesJson,
              profileUrl = d.profileUrl,
            )
          )
          _chessComProfile.value = PlatformProfileUiState(
            isLoading = false,
            username = d.username,
            title = d.title,
            ratingsSummary = d.ratingsSummary,
            totalGames = d.totalGames,
            wins = d.wins,
            losses = d.losses,
            draws = d.draws,
            profileUrl = d.profileUrl,
            recentGames = d.recentGames,
            isCachedOffline = false,
          )
        }
        is ApiFetchOutcome.Failure -> {
          loadCachedProfile("CHESSCOM")
          _chessComProfile.update { it.copy(isLoading = false, username = user, errorMessage = res.message) }
        }
      }
    }
  }

  fun unlinkChessCom() {
    viewModelScope.launch {
      repository.updateSettings { it.copy(chessComUsername = "") }
      repository.deleteCachedProfile("CHESSCOM")
      _chessComProfile.value = PlatformProfileUiState()
    }
  }

  // endregion

  // region Online Multiplayer & Settings

  fun saveOnlineServerUrl(url: String) {
    viewModelScope.launch {
      repository.updateSettings { it.copy(onlineServerUrl = url.trim()) }
      onlineClient.updateServerConfiguration(url)
    }
  }

  fun connectOnline(handle: String, sessionToken: String) {
    viewModelScope.launch {
      repository.updateSettings { it.copy(onlineHandle = handle.trim()) }
      onlineClient.connect(handle.trim(), sessionToken.trim())
    }
  }

  fun requestOnlineMatch(request: OnlineMatchRequest) {
    onlineClient.requestMatch(request)
  }

  fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
    viewModelScope.launch {
      repository.updateSettings(transform)
    }
  }

  suspend fun exportAllPgns(): String = repository.exportAllPgns()

  fun clearProfileCache() {
    viewModelScope.launch {
      repository.clearProfileCache()
      _lichessProfile.update { it.copy(recentGames = emptyList(), ratingsSummary = "") }
      _chessComProfile.update { it.copy(recentGames = emptyList(), ratingsSummary = "") }
      postMessage("Profile cache cleared")
    }
  }

  fun deleteAllLocalData() {
    viewModelScope.launch {
      repository.deleteAllLocalData()
      _lichessProfile.value = PlatformProfileUiState()
      _chessComProfile.value = PlatformProfileUiState()
      postMessage("All local data deleted")
    }
  }

  override fun onCleared() {
    botThinkJob?.cancel()
    clockTickJob?.cancel()
    tournamentJob?.cancel()
    evalJob?.cancel()
    fallbackEngineWrapper.shutdown()
    if (isEngineServiceBound) {
      runCatching { getApplication<Application>().unbindService(engineServiceConnection) }
      isEngineServiceBound = false
    }
    onlineClient.disconnect()
    musicController.release()
    super.onCleared()
  }
}
