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
import com.example.analysis.AnalysisCoordinator
import com.example.analysis.AnalysisPolicy
import com.example.analysis.AnalysisSessionState
import com.example.analysis.BookPracticeSession
import com.example.analysis.GameReviewSummary
import com.example.analysis.MoveAnalysisEntry
import com.example.analysis.MoveClassification
import com.example.analysis.MoveFeedbackAnalyzer
import com.example.analysis.PositionAnalysisSnapshot
import com.example.analysis.TrainerDrill
import com.example.badges.EvaluatedBadge
import com.example.badges.GameBadgeSystem
import com.example.bots.BotCatalog
import com.example.bots.BotDialogueEngine
import com.example.bots.BotTournamentEngine
import com.example.bots.TournamentProgress
import com.example.chess.BoardState
import com.example.chess.BookRepertoire
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
import com.example.engine.EngineAnalysisOutput
import com.example.engine.MemoryProfile
import com.example.engine.SearchBudget
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
import com.example.offline.TimeCategory
import com.example.offline.TimeControl
import com.example.online.OnlineChatMessage
import com.example.online.OnlineMatchRequest
import com.example.online.OnlineMultiplayerClient
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

  private val _lichessProfile = MutableStateFlow(PlatformProfileUiState())
  val lichessProfile: StateFlow<PlatformProfileUiState> = _lichessProfile.asStateFlow()

  private val _chessComProfile = MutableStateFlow(PlatformProfileUiState())
  val chessComProfile: StateFlow<PlatformProfileUiState> = _chessComProfile.asStateFlow()

  private val _tournamentProgress = MutableStateFlow<TournamentProgress?>(null)
  val tournamentProgress: StateFlow<TournamentProgress?> = _tournamentProgress.asStateFlow()

  private val _publicFeed = MutableStateFlow(PublicFeedUiState())
  val publicFeed: StateFlow<PublicFeedUiState> = _publicFeed.asStateFlow()

  val onlineChatMessages: StateFlow<List<OnlineChatMessage>> = onlineClient.chatMessages

  val evaluatedBadges: StateFlow<List<EvaluatedBadge>> = combine(settings, botStats) { s, stats ->
    GameBadgeSystem.evaluate(s, stats)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = GameBadgeSystem.evaluate(AppSettingsEntity(), emptyList()),
  )

  private val _engineStatus = MutableStateFlow(fallbackEngineWrapper.status.value)
  val engineStatus: StateFlow<StockfishEngineStatus> = _engineStatus.asStateFlow()

  private val _analysisSession = MutableStateFlow(
    AnalysisSessionState(
      boardState = ChessRules.initialBoardState(),
      historyStates = listOf(ChessRules.initialBoardState()),
      historyMoves = emptyList(),
      legalMoves = ChessRules.legalMoves(ChessRules.initialBoardState()),
    )
  )
  val analysisSession: StateFlow<AnalysisSessionState> = _analysisSession.asStateFlow()

  private var evalJob: Job? = null
  private var analysisEvalJob: Job? = null
  private var gameReviewJob: Job? = null

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
    val defaultPlayerName = settings.value.playerName.ifBlank { "ChessHub Player" }
    startNewGame(
      mode = GameMode.HUMAN_VS_BOT,
      white = Opponent.Human(defaultPlayerName),
      black = Opponent.Bot(BotCatalog.presets[3]),
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
    val playerName = settings.value.playerName.ifBlank { "ChessHub Player" }
    startNewGame(
      mode = GameMode.HUMAN_VS_BOT,
      white = Opponent.Human(playerName),
      black = Opponent.Bot(BotCatalog.presets[4]),
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
    customStartFen: String? = null,
    variantLabel: String = "Standard",
  ) {
    botThinkJob?.cancel()
    clockTickJob?.cancel()
    evalJob?.cancel()
    repetitionCounts.clear()
    repetitionCounts[ChessRules.repetitionKey(initialBoard)] = 1

    clockTracker.reset(timeControl)
    if (!timeControl.isUnlimited && mode != GameMode.BOT_TRAINING) {
      clockTracker.start(initialBoard.turn)
    }

    val flipped = white is Opponent.Bot && black is Opponent.Human
    val primaryBot = (black as? Opponent.Bot)?.profile ?: (white as? Opponent.Bot)?.profile
    val initialDialogue = primaryBot?.let {
      BotDialogueEngine.onGameStart(it, botPlaysWhite = white is Opponent.Bot)
    }
    val curSettings = settings.value
    val opening = if (curSettings.showOpeningInfo) OpeningBook.recognize(initialMoves.map { it.san }) else null
    val staticEval = if (AnalysisPolicy.shouldRunLiveEngine(curSettings) || trainingMode) {
      engineService?.evaluateStaticForWhite(initialBoard)
        ?: fallbackEngineWrapper.evaluateStaticForWhite(initialBoard)
    } else 0

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
      opening = opening,
      trainingMode = trainingMode,
      evalCpForWhite = staticEval,
      evalMateIn = null,
      startedAtMillis = System.currentTimeMillis(),
      botDialogue = initialDialogue,
      botDialogueHistory = listOfNotNull(initialDialogue),
      customStartFen = customStartFen,
      chessVariantLabel = variantLabel,
      lastDialogueOpeningKey = opening?.let { "${it.eco}:${it.name}" },
    )

    if (navigateToPlay) {
      _currentSection.value = AppSection.PLAY
    }
    startClockTickerIfNeeded()
    refreshLivePlayAnalysis(
      stateBeforeMove = null,
      playedMove = null,
      playedSan = null,
      preMoveSnapshot = null,
      nextState = initialBoard,
      sanMoves = initialMoves.map { it.san },
    )
    maybeTriggerBotTurn()
  }

  fun startCustomGame(
    mode: GameMode,
    white: Opponent,
    black: Opponent,
    timeControl: TimeControl,
    variant: String,
    chess960Seed: Int = kotlin.random.Random.nextInt(960),
    handicapPiece: PieceType? = null,
    handicapFromWhite: Boolean = true,
    customFen: String? = null,
    navigateToPlay: Boolean = true,
  ) {
    val startBoard = when (variant) {
      "Chess960" -> ChessRules.chess960BoardState(chess960Seed)
      "Handicap" -> ChessRules.handicapBoardState(handicapPiece ?: PieceType.QUEEN, handicapFromWhite)
      "Custom FEN" -> {
        val parsed = customFen?.let { ChessNotation.fromFen(it) }
        if (parsed == null) {
          postMessage("Invalid custom FEN; loaded standard board.")
          ChessRules.initialBoardState()
        } else {
          val err = MoveFeedbackAnalyzer.validateCustomPosition(parsed)
          if (err != null) {
            postMessage(err)
            return
          }
          parsed
        }
      }
      else -> ChessRules.initialBoardState()
    }
    if (variant != "Standard") {
      unlockBadge("custom_architect")
    }
    val fenUsed = if (variant == "Standard") null else ChessNotation.toFen(startBoard)
    startNewGame(
      mode = mode,
      white = white,
      black = black,
      timeControl = timeControl,
      navigateToPlay = navigateToPlay,
      initialBoard = startBoard,
      customStartFen = fenUsed,
      variantLabel = if (variant == "Chess960") "Chess960 #$chess960Seed" else variant,
    )
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

    val activeBotOpp = (if (mover == PieceColor.WHITE) g.whiteOpponent else g.blackOpponent) as? Opponent.Bot
    val otherBotOpp = (if (mover == PieceColor.WHITE) g.blackOpponent else g.whiteOpponent) as? Opponent.Bot
    val speakingBot = activeBotOpp ?: otherBotOpp
    val openingKey = opening?.let { "${it.eco}:${it.name}" }
    val openingChanged = opening != null && openingKey != g.lastDialogueOpeningKey && s.showOpeningInfo
    if (openingChanged && opening?.isGambit == true) {
      unlockBadge("gambit_daredevil")
    }
    val openingDialogue = if (openingChanged && opening != null) {
      BotDialogueEngine.onOpeningDetected(speakingBot?.profile, opening)
    } else null

    val moveDialogue = if (res != GameResult.IN_PROGRESS && speakingBot != null) {
      val botPlayedWhite = g.whiteOpponent is Opponent.Bot &&
        (g.whiteOpponent as Opponent.Bot).profile.id == speakingBot.profile.id
      BotDialogueEngine.onGameFinished(speakingBot.profile, res, reason, botPlayedWhite)
    } else if (openingDialogue != null) {
      openingDialogue
    } else if (speakingBot != null) {
      BotDialogueEngine.onMovePlayed(
        bot = speakingBot.profile,
        move = move,
        byBot = activeBotOpp != null,
        isCheck = isCheck,
        moveNumber = nextState.fullMoveNumber,
      )
    } else null

    val previousState = g.boardState
    val preSnapshot = g.liveAnalysis

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
        opening = if (s.showOpeningInfo) (opening ?: it.opening) else null,
        lastDialogueOpeningKey = openingKey ?: it.lastDialogueOpeningKey,
        drawOfferPendingFrom = null,
        evalCpForWhite = staticEvalCp,
        evalMateIn = null,
        hintMove = null,
        botDialogue = moveDialogue ?: it.botDialogue,
        botDialogueHistory = if (moveDialogue != null) {
          (it.botDialogueHistory + moveDialogue).takeLast(16)
        } else it.botDialogueHistory,
      )
    }

    refreshLivePlayAnalysis(
      stateBeforeMove = previousState,
      playedMove = move,
      playedSan = san,
      preMoveSnapshot = preSnapshot,
      nextState = nextState,
      sanMoves = newMoves.map { it.san },
    )

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

  private suspend fun runEngineDetailed(
    state: BoardState,
    budget: SearchBudget,
    includePv: Boolean,
    includeCandidates: Boolean,
    includeThreat: Boolean,
  ): EngineAnalysisOutput {
    return engineService?.analyzeDetailed(
      state = state,
      budget = budget,
      includePv = includePv,
      includeCandidates = includeCandidates,
      includeThreat = includeThreat,
    ) ?: fallbackEngineWrapper.analyzeDetailed(
      state = state,
      budget = budget,
      includePv = includePv,
      includeCandidates = includeCandidates,
      includeThreat = includeThreat,
    )
  }

  private fun refreshLivePlayAnalysis(
    stateBeforeMove: BoardState?,
    playedMove: Move?,
    playedSan: String?,
    preMoveSnapshot: PositionAnalysisSnapshot?,
    nextState: BoardState,
    sanMoves: List<String>,
  ) {
    evalJob?.cancel()
    val curSettings = settings.value
    val gSnapshot = _gameState.value
    val involvesBot = gSnapshot.whiteOpponent is Opponent.Bot || gSnapshot.blackOpponent is Opponent.Bot
    val needEngine = AnalysisPolicy.shouldRunLiveEngine(curSettings) || gSnapshot.trainingMode

    if (!needEngine) {
      val minimal = PositionAnalysisSnapshot(
        openingInfo = if (curSettings.showOpeningInfo) OpeningBook.recognize(sanMoves) else null,
        openingContinuations = if (curSettings.showOpeningInfo) OpeningBook.exploreContinuations(sanMoves) else emptyList(),
        endgameInfo = MoveFeedbackAnalyzer.analyzeEndgame(nextState),
      )
      _gameState.update {
        it.copy(
          liveAnalysis = minimal,
          lastMoveFeedback = null,
        )
      }
      return
    }

    evalJob = viewModelScope.launch {
      _gameState.update { it.copy(liveAnalysis = it.liveAnalysis.copy(isAnalyzing = true)) }
      val depth = if (curSettings.lowMemoryMode || memoryProfile.isLowRamHardware) 2 else 3
      val timeMs = if (curSettings.lowMemoryMode || memoryProfile.isLowRamHardware) 220L else 360L

      val (snapshot, afterOut) = AnalysisCoordinator.buildPositionSnapshot(
        state = nextState,
        sanMovesSoFar = sanMoves,
        settings = curSettings,
        memoryProfile = memoryProfile,
        requestedDepth = depth,
        requestedTimeMs = timeMs,
        runDetailed = ::runEngineDetailed,
      )

      var feedbackEntry: MoveAnalysisEntry? = null
      if (stateBeforeMove != null && playedMove != null && playedSan != null && afterOut != null &&
        AnalysisPolicy.shouldComputeMoveFeedback(curSettings, involvesBot)
      ) {
        val beforeSign = if (stateBeforeMove.turn == PieceColor.WHITE) 1 else -1
        val preBestMove = preMoveSnapshot?.bestMove
        val preBestSan = preMoveSnapshot?.bestMoveSan
        val evalBeforeWhite = preMoveSnapshot?.evalCpForWhite
          ?: (engineService?.evaluateStaticForWhite(stateBeforeMove)
            ?: fallbackEngineWrapper.evaluateStaticForWhite(stateBeforeMove))
        val bestEvalBeforeForMover = evalBeforeWhite * beforeSign
        val secondBestForMover = preMoveSnapshot?.candidateMoves?.getOrNull(1)?.evalCpForWhite?.times(beforeSign)
        val isBook = OpeningBook.isKnownBookPrefix(sanMoves)

        feedbackEntry = MoveFeedbackAnalyzer.evaluateMoveFeedback(
          plyIndex = sanMoves.size,
          stateBefore = stateBeforeMove,
          move = playedMove,
          san = playedSan,
          stateAfter = nextState,
          evalBeforeCpForWhite = evalBeforeWhite,
          evalAfterCpForWhite = snapshot.evalCpForWhite,
          mateInAfterForWhite = snapshot.mateInForWhite,
          bestMoveInBefore = preBestMove,
          bestMoveSan = preBestSan,
          bestEvalBeforeForMoverCp = bestEvalBeforeForMover,
          secondBestEvalBeforeForMoverCp = secondBestForMover,
          isBookMove = isBook,
          pvSanList = snapshot.pvSanList,
          enableCriticalMoments = curSettings.enableCriticalMoments,
        )
      }

      if (feedbackEntry?.classification == MoveClassification.BRILLIANT) {
        unlockBadge("brilliant_mind")
      }

      if (_gameState.value.boardState == nextState) {
        _gameState.update { cur ->
          cur.copy(
            evalCpForWhite = snapshot.evalCpForWhite,
            evalMateIn = snapshot.mateInForWhite,
            liveAnalysis = snapshot,
            lastMoveFeedback = if (AnalysisPolicy.showMoveFeedbackForGame(curSettings, involvesBot)) {
              feedbackEntry
            } else null,
            moveFeedbackHistory = if (feedbackEntry != null) {
              cur.moveFeedbackHistory + feedbackEntry
            } else cur.moveFeedbackHistory,
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

    val newFeedbackHistory = g.moveFeedbackHistory.take(newMoves.size)
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
        opening = if (settings.value.showOpeningInfo) OpeningBook.recognize(newMoves.map { m -> m.san }) else null,
        hintMove = null,
        moveFeedbackHistory = newFeedbackHistory,
        lastMoveFeedback = newFeedbackHistory.lastOrNull(),
      )
    }
    refreshLivePlayAnalysis(null, null, null, null, targetState, newMoves.map { it.san })
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
        val botOpp = (if (humanIsWhite) g.blackOpponent else g.whiteOpponent) as? Opponent.Bot
        val declineLine = botOpp?.let { BotDialogueEngine.onDrawDeclined(it.profile) }
          ?: "Bot declined the draw offer."
        _gameState.update {
          it.copy(
            botDialogue = declineLine,
            botDialogueHistory = (it.botDialogueHistory + declineLine).takeLast(12),
          )
        }
        postMessage(declineLine)
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

  fun dismissBotDialogue() {
    _gameState.update { it.copy(botDialogue = null) }
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
    if (g.timeControl.category == TimeCategory.BULLET || g.timeControl.category == TimeCategory.BLITZ) {
      unlockBadge("speed_demon")
    }
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
      opening = if (settings.value.showOpeningInfo) parsed.opening else null,
      replayIndex = parsed.moves.size,
    )
    _currentSection.value = AppSection.PLAY
    refreshLivePlayAnalysis(null, null, null, null, history.last(), parsed.moves.map { it.san })
  }

  fun setReplayStep(step: Int) {
    val g = _gameState.value
    val clamped = step.coerceIn(0, g.stateHistory.lastIndex)
    val lastMove = if (clamped > 0) g.moves[clamped - 1].move else null
    val entry = if (clamped > 0) g.moveFeedbackHistory.getOrNull(clamped - 1) else null
    val involvesBot = g.whiteOpponent is Opponent.Bot || g.blackOpponent is Opponent.Bot
    val showFeedback = AnalysisPolicy.showMoveFeedbackForGame(settings.value, involvesBot)
    val targetBoard = g.stateHistory[clamped]
    _gameState.update {
      it.copy(
        replayIndex = clamped,
        boardState = targetBoard,
        lastMoveFrom = lastMove?.from,
        lastMoveTo = lastMove?.to,
        lastMoveFeedback = if (showFeedback) entry else null,
      )
    }
    refreshLivePlayAnalysis(null, null, null, null, targetBoard, g.moves.take(clamped).map { it.san })
  }

  fun exitReplayMode() {
    val g = _gameState.value
    val lastState = g.stateHistory.last()
    val lastMove = g.moves.lastOrNull()?.move
    val involvesBot = g.whiteOpponent is Opponent.Bot || g.blackOpponent is Opponent.Bot
    val showFeedback = AnalysisPolicy.showMoveFeedbackForGame(settings.value, involvesBot)
    _gameState.update {
      it.copy(
        replayIndex = null,
        boardState = lastState,
        legalMoves = if (it.result == GameResult.IN_PROGRESS) ChessRules.legalMoves(lastState) else emptyList(),
        lastMoveFrom = lastMove?.from,
        lastMoveTo = lastMove?.to,
        lastMoveFeedback = if (showFeedback) g.moveFeedbackHistory.lastOrNull() else null,
      )
    }
    refreshLivePlayAnalysis(null, null, null, null, lastState, g.moves.map { it.san })
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
    startBotTournament(TimeControl.presets[4], "All-Stars")
  }

  fun startBotTournament(
    timeControl: TimeControl = TimeControl.presets[4],
    tierKey: String = "All-Stars",
  ) {
    if (tournamentJob?.isActive == true) return
    tournamentJob = viewModelScope.launch {
      val all = BotCatalog.presets
      val participants = when (tierKey) {
        "Beginners" -> all.take(4)
        "Club" -> all.slice(3..6)
        "Masters" -> all.takeLast(4)
        else -> listOf(all[1], all[4], all[7], all.last()).distinctBy { it.id }
      }
      val s = settings.value
      BotTournamentEngine.runRoundRobin(
        participants = participants,
        timeControl = timeControl,
        tournamentTitle = "${timeControl.category.label} ${timeControl.label} • $tierKey",
        lowMemoryMode = s.lowMemoryMode,
        isLowRamHardware = memoryProfile.isLowRamHardware,
      ) { progress ->
        _tournamentProgress.value = progress
        if (!progress.isRunning && progress.completedMatches.isNotEmpty()) {
          unlockBadge("tournament_victor")
        }
      }
    }
  }

  fun cancelTournament() {
    tournamentJob?.cancel()
    _tournamentProgress.update { it?.copy(isRunning = false) }
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

  fun sendOnlineChat(text: String) {
    val active = onlineClient.state.value as? com.example.online.OnlineConnectionState.ActiveOnlineGame
    val sent = onlineClient.sendChatMessage(active?.gameId, text)
    if (!sent) {
      postMessage("Connect to an online server first to chat.")
    }
  }

  fun refreshTvChannels() {
    viewModelScope.launch {
      _publicFeed.update { it.copy(isLoadingTv = true, tvError = null) }
      when (val res = lichessRepo.fetchTvChannels()) {
        is ApiFetchOutcome.Success -> _publicFeed.update {
          it.copy(tvChannels = res.data, isLoadingTv = false, tvError = null)
        }
        is ApiFetchOutcome.Failure -> _publicFeed.update {
          it.copy(isLoadingTv = false, tvError = res.message)
        }
      }
    }
  }

  fun watchPublicGame(gameId: String, label: String = "Live Game") {
    viewModelScope.launch {
      postMessage("Loading $label...")
      when (val res = lichessRepo.fetchPublicGamePgn(gameId)) {
        is ApiFetchOutcome.Success -> loadPgnTextForReplay(res.data, "White", "Black")
        is ApiFetchOutcome.Failure -> postMessage(res.message)
      }
    }
  }

  fun refreshLiveTournaments() {
    viewModelScope.launch {
      _publicFeed.update { it.copy(isLoadingTournaments = true, tournamentsError = null) }
      when (val res = lichessRepo.fetchLiveTournaments()) {
        is ApiFetchOutcome.Success -> _publicFeed.update {
          it.copy(liveTournaments = res.data, isLoadingTournaments = false, tournamentsError = null)
        }
        is ApiFetchOutcome.Failure -> _publicFeed.update {
          it.copy(isLoadingTournaments = false, tournamentsError = res.message)
        }
      }
    }
  }

  fun refreshChessNews() {
    viewModelScope.launch {
      _publicFeed.update { it.copy(isLoadingNews = true, newsError = null) }
      when (val res = lichessRepo.fetchChessNewsBroadcasts()) {
        is ApiFetchOutcome.Success -> _publicFeed.update {
          it.copy(newsItems = res.data, isLoadingNews = false, newsError = null)
        }
        is ApiFetchOutcome.Failure -> _publicFeed.update {
          it.copy(isLoadingNews = false, newsError = res.message)
        }
      }
    }
  }

  fun scoutOtherPlayer(platform: String, rawUsername: String) {
    val clean = rawUsername.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      _publicFeed.update {
        it.copy(
          scoutPlatform = platform,
          scoutProfile = PlatformProfileUiState(isLoading = true, username = clean),
        )
      }
      if (platform == "LICHESS") {
        when (val res = lichessRepo.fetchProfileAndGames(clean)) {
          is ApiFetchOutcome.Success -> {
            val d = res.data
            _publicFeed.update {
              it.copy(
                scoutProfile = PlatformProfileUiState(
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
                )
              )
            }
          }
          is ApiFetchOutcome.Failure -> _publicFeed.update {
            it.copy(scoutProfile = PlatformProfileUiState(isLoading = false, username = clean, errorMessage = res.message))
          }
        }
      } else {
        when (val res = chessComRepo.fetchProfileAndGames(clean)) {
          is ApiFetchOutcome.Success -> {
            val d = res.data
            _publicFeed.update {
              it.copy(
                scoutProfile = PlatformProfileUiState(
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
                )
              )
            }
          }
          is ApiFetchOutcome.Failure -> _publicFeed.update {
            it.copy(scoutProfile = PlatformProfileUiState(isLoading = false, username = clean, errorMessage = res.message))
          }
        }
      }
    }
  }

  private fun unlockBadge(badgeId: String) {
    viewModelScope.launch {
      val current = GameBadgeSystem.parseCsv(settings.value.unlockedBadgesCsv)
      if (badgeId !in current) {
        val updatedCsv = GameBadgeSystem.toCsv(current + badgeId)
        repository.updateSettings { it.copy(unlockedBadgesCsv = updatedCsv) }
        val badgeTitle = GameBadgeSystem.allBadges.firstOrNull { it.id == badgeId }?.title ?: badgeId
        postMessage("Badge Unlocked: $badgeTitle")
      }
    }
  }

  fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
    viewModelScope.launch {
      val updated = repository.updateSettings(transform)
      if (!AnalysisPolicy.shouldRunLiveEngine(updated)) {
        evalJob?.cancel()
        analysisEvalJob?.cancel()
        engineService?.releaseSearchMemory()
        fallbackEngineWrapper.releaseSearchMemory()
        _gameState.update { state ->
          state.copy(
            opening = if (updated.showOpeningInfo) OpeningBook.recognize(state.moves.map { it.san }) else null,
            liveAnalysis = PositionAnalysisSnapshot(
              openingInfo = if (updated.showOpeningInfo) state.liveAnalysis.openingInfo else null,
              openingContinuations = if (updated.showOpeningInfo) state.liveAnalysis.openingContinuations else emptyList(),
            ),
            lastMoveFeedback = null,
          )
        }
      } else {
        val g = _gameState.value
        _gameState.update { state ->
          state.copy(
            opening = if (updated.showOpeningInfo) OpeningBook.recognize(state.moves.map { it.san }) else null,
          )
        }
        refreshLivePlayAnalysis(null, null, null, null, g.boardState, g.moves.map { it.san })
        if (_currentSection.value == AppSection.ANALYSIS) {
          refreshAnalysisSnapshot()
        }
      }
    }
  }

  // region FEN, Analysis Studio, Game Review, Chess Trainer & Practice Mode

  fun openAnalysisForCurrentGame(autoRunReview: Boolean = false) {
    val g = _gameState.value
    val startReplay = g.replayIndex ?: g.stateHistory.lastIndex
    val boardAtPly = g.stateHistory[startReplay.coerceIn(0, g.stateHistory.lastIndex)]
    _analysisSession.value = AnalysisSessionState(
      boardState = boardAtPly,
      historyStates = g.stateHistory,
      historyMoves = g.moves,
      currentPly = startReplay,
      legalMoves = ChessRules.legalMoves(boardAtPly),
      isFlipped = g.isFlipped,
      practiceActive = false,
      practicePlayerColor = if (g.whiteOpponent is Opponent.Bot && g.blackOpponent is Opponent.Human) {
        PieceColor.BLACK
      } else PieceColor.WHITE,
    )
    _currentSection.value = AppSection.ANALYSIS
    refreshAnalysisSnapshot()
    if (autoRunReview && settings.value.enableGameAnalysis && g.moves.isNotEmpty()) {
      runGameReview()
    }
  }

  fun loadFenIntoPlay(rawFen: String, vsBot: BotProfile? = null): Boolean {
    val parsed = ChessNotation.fromFen(rawFen)
    if (parsed == null) {
      postMessage("Invalid FEN string.")
      return false
    }
    val err = MoveFeedbackAnalyzer.validateCustomPosition(parsed)
    if (err != null) {
      postMessage(err)
      return false
    }
    val playerName = settings.value.playerName.ifBlank { "ChessHub Player" }
    val wOpp = if (vsBot != null && parsed.turn == PieceColor.BLACK) Opponent.Bot(vsBot) else Opponent.Human(playerName)
    val bOpp = if (vsBot != null && parsed.turn == PieceColor.WHITE) Opponent.Bot(vsBot) else {
      if (vsBot != null) Opponent.Human(playerName) else Opponent.Human("Player 2")
    }
    unlockBadge("custom_architect")
    startNewGame(
      mode = if (vsBot != null) GameMode.HUMAN_VS_BOT else GameMode.HUMAN_VS_HUMAN,
      white = wOpp,
      black = bOpp,
      timeControl = TimeControl.presets.last(),
      trainingMode = vsBot != null,
      navigateToPlay = true,
      initialBoard = parsed,
      customStartFen = rawFen.trim(),
      variantLabel = "Custom FEN",
    )
    return true
  }

  fun loadFenIntoAnalysis(rawFen: String, asPractice: Boolean = false, drill: TrainerDrill? = null): Boolean {
    val parsed = ChessNotation.fromFen(rawFen)
    if (parsed == null) {
      postMessage("Invalid FEN string.")
      return false
    }
    val err = MoveFeedbackAnalyzer.validateCustomPosition(parsed)
    if (err != null) {
      postMessage(err)
      return false
    }
    gameReviewJob?.cancel()
    _analysisSession.value = AnalysisSessionState(
      boardState = parsed,
      historyStates = listOf(parsed),
      historyMoves = emptyList(),
      currentPly = 0,
      legalMoves = ChessRules.legalMoves(parsed),
      practiceActive = asPractice,
      practicePlayerColor = parsed.turn,
      practiceCoachPrompt = drill?.goal ?: if (asPractice) "Find solid moves — the coach will flag mistakes and let you retry." else null,
      activeDrill = drill,
    )
    _currentSection.value = AppSection.ANALYSIS
    refreshAnalysisSnapshot()
    return true
  }

  fun loadTrainerDrill(drill: TrainerDrill) {
    loadFenIntoAnalysis(drill.fen, asPractice = true, drill = drill)
  }

  fun startBookPractice(repertoire: BookRepertoire) {
    gameReviewJob?.cancel()
    val start = ChessRules.initialBoardState()
    var cur = start
    val states = mutableListOf(start)
    val moves = mutableListOf<RecordedMove>()
    var stepIdx = 0
    // If practicing as Black, auto-play White's 1st book move
    if (repertoire.practiceAs == PieceColor.BLACK && repertoire.movesTokens.isNotEmpty()) {
      val firstSan = repertoire.movesTokens[0]
      val legal = ChessRules.legalMoves(cur)
      val m = ChessNotation.parseSanMove(cur, firstSan)
      if (m != null) {
        val next = ChessRules.applyMove(cur, m)
        moves.add(RecordedMove(m, firstSan, m.toUci(), ChessNotation.toFen(next)))
        states.add(next)
        cur = next
        stepIdx = 1
      }
    }
    val nextExpected = repertoire.movesTokens.getOrNull(stepIdx)
    val statusMsg = if (nextExpected != null) {
      "Book Practice (${repertoire.eco}): ${repertoire.name}. Find your ${repertoire.practiceAs.name.lowercase()} book move (${stepIdx + 1}/${repertoire.movesTokens.size})."
    } else "Repertoire ready!"

    _analysisSession.value = AnalysisSessionState(
      boardState = cur,
      historyStates = states,
      historyMoves = moves,
      currentPly = moves.size,
      legalMoves = ChessRules.legalMoves(cur),
      isFlipped = repertoire.practiceAs == PieceColor.BLACK,
      practiceActive = false,
      practicePlayerColor = repertoire.practiceAs,
      practiceCoachPrompt = statusMsg,
      bookPractice = BookPracticeSession(
        repertoire = repertoire,
        stepIndex = stepIdx,
        completed = false,
        mistakesCount = 0,
        statusMessage = statusMsg,
      ),
      analysisDepthMode = _analysisSession.value.analysisDepthMode,
    )
    _currentSection.value = AppSection.ANALYSIS
    refreshAnalysisSnapshot()
  }

  fun setAnalysisDepthMode(mode: String) {
    _analysisSession.update { it.copy(analysisDepthMode = mode) }
    refreshAnalysisSnapshot()
  }

  fun toggleAnalysisPracticeMode(active: Boolean) {
    _analysisSession.update {
      it.copy(
        practiceActive = active,
        bookPractice = null,
        practicePlayerColor = it.boardState.turn,
        practiceCoachPrompt = if (active) "Practice active: play moves and get instant coach feedback." else null,
        practiceRetryCount = 0,
      )
    }
  }

  fun flipAnalysisBoard() {
    _analysisSession.update { it.copy(isFlipped = !it.isFlipped) }
  }

  fun stepAnalysisPly(targetPly: Int) {
    val s = _analysisSession.value
    val clamped = targetPly.coerceIn(0, s.historyStates.lastIndex)
    val targetBoard = s.historyStates[clamped]
    val entryAtPly = if (clamped > 0) s.reviewSummary.moveEntries.getOrNull(clamped - 1) else null
    val updatedBp = s.bookPractice?.let { bp ->
      val idx = clamped.coerceAtMost(bp.repertoire.movesTokens.size)
      bp.copy(
        stepIndex = idx,
        completed = idx >= bp.repertoire.movesTokens.size,
      )
    }
    _analysisSession.update {
      it.copy(
        currentPly = clamped,
        boardState = targetBoard,
        legalMoves = ChessRules.legalMoves(targetBoard),
        selectedSquare = null,
        lastMoveFeedback = entryAtPly,
        bookPractice = updatedBp,
      )
    }
    refreshAnalysisSnapshot()
  }

  fun onAnalysisSquareTapped(square: Int) {
    val s = _analysisSession.value
    val selected = s.selectedSquare
    if (selected != null) {
      val candidate = s.legalMoves.firstOrNull { it.from == selected && it.to == square }
      if (candidate != null) {
        playAnalysisMove(candidate)
        return
      }
    }
    val piece = s.boardState.board[square]
    if (piece != PieceCode.EMPTY && PieceCode.colorOf(piece) == s.boardState.turn) {
      soundHaptics.playUiClick(settings.value)
      _analysisSession.update { it.copy(selectedSquare = square) }
    } else {
      _analysisSession.update { it.copy(selectedSquare = null) }
    }
  }

  fun playAnalysisBookMove(san: String) {
    val s = _analysisSession.value
    val move = ChessNotation.parseSanMove(s.boardState, san) ?: return
    playAnalysisMove(move)
  }

  fun playAnalysisMove(move: Move) {
    val sessionBefore = _analysisSession.value
    val stateBefore = sessionBefore.boardState
    val legalBefore = sessionBefore.legalMoves
    val san = ChessNotation.toSan(stateBefore, move, legalBefore)
    val nextState = ChessRules.applyMove(stateBefore, move)
    val fenAfter = ChessNotation.toFen(nextState)

    val isCheck = ChessRules.isInCheck(nextState)
    val curSettings = settings.value
    when {
      ChessRules.legalMoves(nextState).isEmpty() -> soundHaptics.playGameEndSound(curSettings)
      isCheck -> soundHaptics.playCheckSound(curSettings)
      move.capturedPiece != PieceCode.EMPTY || move.isEnPassant -> soundHaptics.playCaptureSound(curSettings)
      else -> soundHaptics.playMoveSound(curSettings)
    }

    val bp = sessionBefore.bookPractice
    if (bp != null && !bp.completed && stateBefore.turn == bp.repertoire.practiceAs) {
      val expectedSan = bp.repertoire.movesTokens.getOrNull(bp.stepIndex)
      val normalizedSan = san.trimEnd('+', '#')
      val normalizedExpected = expectedSan?.trimEnd('+', '#')
      if (normalizedExpected != null && normalizedSan != normalizedExpected) {
        val errMsg = "Not the book line ($san). Try $expectedSan — tap Retry / Undo."
        _analysisSession.update {
          it.copy(
            selectedSquare = null,
            practiceCoachPrompt = errMsg,
            bookPractice = bp.copy(mistakesCount = bp.mistakesCount + 1, statusMessage = errMsg),
          )
        }
        return
      }
    }

    val truncatedStates = sessionBefore.historyStates.take(sessionBefore.currentPly + 1) + nextState
    val truncatedMoves = sessionBefore.historyMoves.take(sessionBefore.currentPly) + RecordedMove(move, san, move.toUci(), fenAfter)

    var updatedBp = bp
    var updatedCoachPrompt = sessionBefore.practiceCoachPrompt
    var autoReplyBookSan: String? = null
    if (bp != null && !bp.completed) {
      val nextIdx = bp.stepIndex + 1
      if (nextIdx >= bp.repertoire.movesTokens.size) {
        val doneMsg = "Book Line Mastered: ${bp.repertoire.name}! (${bp.mistakesCount} mistake(s))"
        updatedBp = bp.copy(stepIndex = nextIdx, completed = true, statusMessage = doneMsg)
        updatedCoachPrompt = doneMsg
        viewModelScope.launch {
          val updatedSettings = repository.updateSettings {
            it.copy(bookDrillsCompleted = it.bookDrillsCompleted + 1)
          }
          if (updatedSettings.bookDrillsCompleted >= 3) {
            unlockBadge("book_scholar")
          }
        }
      } else {
        if (nextState.turn != bp.repertoire.practiceAs) {
          autoReplyBookSan = bp.repertoire.movesTokens[nextIdx]
        }
        val msg = "Book move $san! Continue ${bp.repertoire.name} (${nextIdx}/${bp.repertoire.movesTokens.size})."
        updatedBp = bp.copy(stepIndex = nextIdx, statusMessage = msg)
        updatedCoachPrompt = msg
      }
    }

    _analysisSession.update {
      it.copy(
        boardState = nextState,
        historyStates = truncatedStates,
        historyMoves = truncatedMoves,
        currentPly = truncatedMoves.size,
        selectedSquare = null,
        legalMoves = ChessRules.legalMoves(nextState),
        bookPractice = updatedBp,
        practiceCoachPrompt = updatedCoachPrompt,
      )
    }

    if (autoReplyBookSan != null) {
      viewModelScope.launch {
        delay(320L)
        if (_analysisSession.value.boardState == nextState) {
          val replyMove = ChessNotation.parseSanMove(nextState, autoReplyBookSan)
          if (replyMove != null) playAnalysisMove(replyMove)
        }
      }
      return
    }

    refreshAnalysisSnapshot(
      stateBefore = stateBefore,
      playedMove = move,
      playedSan = san,
      preSnapshot = sessionBefore.snapshot,
    )
  }

  private fun refreshAnalysisSnapshot(
    stateBefore: BoardState? = null,
    playedMove: Move? = null,
    playedSan: String? = null,
    preSnapshot: PositionAnalysisSnapshot? = null,
  ) {
    analysisEvalJob?.cancel()
    analysisEvalJob = viewModelScope.launch {
      val s = _analysisSession.value
      val curSettings = settings.value
      val sanList = s.historyMoves.take(s.currentPly).map { it.san }
      _analysisSession.update { it.copy(snapshot = it.snapshot.copy(isAnalyzing = AnalysisPolicy.shouldRunLiveEngine(curSettings))) }

      val constrained = curSettings.lowMemoryMode || memoryProfile.isLowRamHardware
      val reqDepth = when (s.analysisDepthMode) {
        "Fast" -> 2
        "Deep" -> if (constrained) 3 else 4
        else -> if (constrained) 2 else 3
      }
      val reqTime = when (s.analysisDepthMode) {
        "Fast" -> 160L
        "Deep" -> if (constrained) 320L else 620L
        else -> if (constrained) 240L else 420L
      }

      val (snapshot, afterOut) = AnalysisCoordinator.buildPositionSnapshot(
        state = s.boardState,
        sanMovesSoFar = sanList,
        settings = curSettings,
        memoryProfile = memoryProfile,
        requestedDepth = reqDepth,
        requestedTimeMs = reqTime,
        runDetailed = ::runEngineDetailed,
      )

      var feedback: MoveAnalysisEntry? = _analysisSession.value.lastMoveFeedback
      var practiceCoachMsg = _analysisSession.value.practiceCoachPrompt
      var practiceRetry = _analysisSession.value.practiceRetryCount

      if (stateBefore != null && playedMove != null && playedSan != null && afterOut != null && curSettings.showMoveFeedback) {
        val beforeSign = if (stateBefore.turn == PieceColor.WHITE) 1 else -1
        val evalBeforeWhite = preSnapshot?.evalCpForWhite ?: fallbackEngineWrapper.evaluateStaticForWhite(stateBefore)
        feedback = MoveFeedbackAnalyzer.evaluateMoveFeedback(
          plyIndex = s.currentPly,
          stateBefore = stateBefore,
          move = playedMove,
          san = playedSan,
          stateAfter = s.boardState,
          evalBeforeCpForWhite = evalBeforeWhite,
          evalAfterCpForWhite = snapshot.evalCpForWhite,
          mateInAfterForWhite = snapshot.mateInForWhite,
          bestMoveInBefore = preSnapshot?.bestMove,
          bestMoveSan = preSnapshot?.bestMoveSan,
          bestEvalBeforeForMoverCp = evalBeforeWhite * beforeSign,
          secondBestEvalBeforeForMoverCp = preSnapshot?.candidateMoves?.getOrNull(1)?.evalCpForWhite?.times(beforeSign),
          isBookMove = OpeningBook.isKnownBookPrefix(sanList),
          pvSanList = snapshot.pvSanList,
          enableCriticalMoments = curSettings.enableCriticalMoments,
        )
        if (feedback.classification == MoveClassification.BRILLIANT) {
          unlockBadge("brilliant_mind")
        }

        if (_analysisSession.value.practiceActive && stateBefore.turn == _analysisSession.value.practicePlayerColor) {
          if (feedback.classification.isMistakeOrWorse) {
            practiceRetry++
            practiceCoachMsg = "${feedback.classification.label}! ${feedback.explanation} Tap Undo or keep exploring."
          } else {
            practiceCoachMsg = "${feedback.classification.label}! ${feedback.explanation}"
            // Auto-reply as practice opponent if it's now opponent's turn and game continues
            val replyMove = snapshot.bestMove
            if (replyMove != null && s.boardState.turn != _analysisSession.value.practicePlayerColor) {
              _analysisSession.update {
                it.copy(
                  snapshot = snapshot,
                  lastMoveFeedback = feedback,
                  practiceCoachPrompt = practiceCoachMsg,
                  practiceRetryCount = practiceRetry,
                )
              }
              delay(380L)
              if (_analysisSession.value.boardState == s.boardState && _analysisSession.value.practiceActive) {
                playAnalysisMove(replyMove)
              }
              return@launch
            }
          }
        }
      }

      if (_analysisSession.value.boardState == s.boardState) {
        _analysisSession.update {
          it.copy(
            snapshot = snapshot,
            lastMoveFeedback = if (curSettings.showMoveFeedback) feedback else null,
            practiceCoachPrompt = practiceCoachMsg,
            practiceRetryCount = practiceRetry,
          )
        }
      }
    }
  }

  fun undoAnalysisMove() {
    val s = _analysisSession.value
    if (s.currentPly <= 0) return
    stepAnalysisPly(s.currentPly - 1)
  }

  fun runGameReview() {
    val curSettings = settings.value
    if (!curSettings.enableGameAnalysis) {
      postMessage("Enable Game Analysis in Settings to run full review.")
      return
    }
    val s = _analysisSession.value
    if (s.historyMoves.isEmpty()) {
      postMessage("Play or load a game with moves first.")
      return
    }
    gameReviewJob?.cancel()
    gameReviewJob = viewModelScope.launch {
      _analysisSession.update {
        it.copy(reviewSummary = it.reviewSummary.copy(isRunning = true, progressFraction = 0.05f))
      }
      val summary = AnalysisCoordinator.reviewFullGame(
        historyStates = s.historyStates,
        moves = s.historyMoves,
        settings = curSettings,
        memoryProfile = memoryProfile,
        depthMode = s.analysisDepthMode,
        runDetailed = ::runEngineDetailed,
        onProgress = { partial ->
          _analysisSession.update { it.copy(reviewSummary = partial) }
        },
      )
      _analysisSession.update { it.copy(reviewSummary = summary) }
      repository.updateSettings { it.copy(gameReviewsCompleted = it.gameReviewsCompleted + 1) }
      unlockBadge("game_analyst")
      if (summary.whiteCounts.brilliant + summary.blackCounts.brilliant > 0) {
        unlockBadge("brilliant_mind")
      }
      engineService?.releaseSearchMemory()
      fallbackEngineWrapper.releaseSearchMemory()
    }
  }

  fun cancelGameReview() {
    gameReviewJob?.cancel()
    _analysisSession.update { it.copy(reviewSummary = it.reviewSummary.copy(isRunning = false)) }
  }

  // endregion

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
    analysisEvalJob?.cancel()
    gameReviewJob?.cancel()
    fallbackEngineWrapper.shutdown()
    if (isEngineServiceBound) {
      runCatching { getApplication<Application>().unbindService(engineServiceConnection) }
      isEngineServiceBound = false
    }
    onlineClient.disconnect()
    musicController.release()
    soundHaptics.release()
    super.onCleared()
  }
}
