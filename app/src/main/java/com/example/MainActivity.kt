package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bots.BotCatalog
import com.example.chess.Opponent
import com.example.music.RepeatMode
import com.example.offline.TimeControl
import com.example.puzzles.PuzzlesViewModel
import com.example.settings.LocaleHelper
import com.example.ui.screens.AnalysisSuiteScreen
import com.example.ui.screens.BotsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.OnlineScreen
import com.example.ui.screens.PlayScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.PuzzlesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ChessHubTheme

class MainActivity : ComponentActivity() {

  private val viewModel: ChessHubViewModel by viewModels()
  private val puzzlesViewModel: PuzzlesViewModel by viewModels()

  override fun attachBaseContext(newBase: Context) {
    val prefs = newBase.getSharedPreferences("chesshub_boot_prefs", Context.MODE_PRIVATE)
    val lang = prefs.getString("language_code", "en") ?: "en"
    super.attachBaseContext(LocaleHelper.wrapContext(newBase, lang))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val settings by viewModel.settings.collectAsStateWithLifecycle()
      val darkTheme = when (settings.themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
      }

      ChessHubTheme(
        darkTheme = darkTheme,
        accentKey = settings.appAccent,
      ) {
        ChessHubAppRoot(
          viewModel = viewModel,
          puzzlesViewModel = puzzlesViewModel,
          onApplyLanguage = { code ->
            getSharedPreferences("chesshub_boot_prefs", Context.MODE_PRIVATE)
              .edit()
              .putString("language_code", code)
              .apply()
            viewModel.updateSettings { it.copy(languageCode = code) }
            recreate()
          },
          onSharePgn = { pgnText ->
            val shareIntent = viewModel.createSharePgnIntent(pgnText)
            if (shareIntent != null) {
              startActivity(android.content.Intent.createChooser(shareIntent, "Share PGN"))
            } else {
              viewModel.postMessage("Unable to export PGN file")
            }
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessHubAppRoot(
  viewModel: ChessHubViewModel,
  puzzlesViewModel: PuzzlesViewModel,
  onApplyLanguage: (String) -> Unit,
  onSharePgn: (String) -> Unit,
) {
  val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val gameState by viewModel.gameState.collectAsStateWithLifecycle()
  val recentGames by viewModel.recentGames.collectAsStateWithLifecycle()
  val unfinishedGame by viewModel.latestUnfinishedGame.collectAsStateWithLifecycle()
  val historyGames by viewModel.historyGames.collectAsStateWithLifecycle()
  val historyQuery by viewModel.historyQuery.collectAsStateWithLifecycle()
  val historySort by viewModel.historySort.collectAsStateWithLifecycle()
  val botStats by viewModel.botStats.collectAsStateWithLifecycle()
  val tournamentProgress by viewModel.tournamentProgress.collectAsStateWithLifecycle()
  val lichessProfile by viewModel.lichessProfile.collectAsStateWithLifecycle()
  val chessComProfile by viewModel.chessComProfile.collectAsStateWithLifecycle()
  val onlineState by viewModel.onlineClient.state.collectAsStateWithLifecycle()
  val musicState by viewModel.musicController.state.collectAsStateWithLifecycle()
  val engineStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
  val analysisSession by viewModel.analysisSession.collectAsStateWithLifecycle()
  val evaluatedBadges by viewModel.evaluatedBadges.collectAsStateWithLifecycle()
  val publicFeed by viewModel.publicFeed.collectAsStateWithLifecycle()
  val onlineChatMessages by viewModel.onlineChatMessages.collectAsStateWithLifecycle()
  val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  var showMoreSheet by remember { mutableStateOf(false) }
  var analysisInitialTab by remember { mutableIntStateOf(0) }
  var onlineInitialTab by remember { mutableIntStateOf(0) }
  var botsInitialTab by remember { mutableIntStateOf(0) }

  LaunchedEffect(snackbarMessage) {
    val msg = snackbarMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearSnackbar()
  }

  // Back button from any secondary section navigates cleanly to HOME
  BackHandler(enabled = currentSection != AppSection.HOME) {
    viewModel.navigateTo(AppSection.HOME)
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 680.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!isExpanded) {
          NavigationBar {
            NavigationBarItem(
              selected = currentSection == AppSection.HOME,
              onClick = { viewModel.navigateTo(AppSection.HOME) },
              icon = { Icon(Icons.Filled.Home, contentDescription = null) },
              label = { Text(stringResource(R.string.nav_home), maxLines = 1) },
              modifier = Modifier.testTag("nav_home"),
            )
            NavigationBarItem(
              selected = currentSection == AppSection.PLAY,
              onClick = { viewModel.navigateTo(AppSection.PLAY) },
              icon = { Icon(Icons.Filled.GridView, contentDescription = null) },
              label = { Text(stringResource(R.string.nav_play), maxLines = 1) },
              modifier = Modifier.testTag("nav_play"),
            )
            NavigationBarItem(
              selected = currentSection == AppSection.BOTS,
              onClick = {
                botsInitialTab = 0
                viewModel.navigateTo(AppSection.BOTS)
              },
              icon = { Icon(Icons.Filled.SmartToy, contentDescription = null) },
              label = { Text(stringResource(R.string.nav_bots), maxLines = 1) },
              modifier = Modifier.testTag("nav_bots"),
            )
            NavigationBarItem(
              selected = currentSection == AppSection.PUZZLES,
              onClick = { viewModel.navigateTo(AppSection.PUZZLES) },
              icon = { Icon(Icons.Filled.Extension, contentDescription = null) },
              label = { Text(stringResource(R.string.nav_puzzles), maxLines = 1) },
              modifier = Modifier.testTag("nav_puzzles"),
            )
            NavigationBarItem(
              selected = currentSection in setOf(
                AppSection.ANALYSIS,
                AppSection.ONLINE,
                AppSection.PROFILES,
                AppSection.MUSIC,
                AppSection.SETTINGS,
              ),
              onClick = { showMoreSheet = true },
              icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
              label = { Text(stringResource(R.string.nav_more), maxLines = 1) },
              modifier = Modifier.testTag("nav_more"),
            )
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (isExpanded) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            SectionItem(AppSection.HOME, Icons.Filled.Home, stringResource(R.string.nav_home), currentSection, viewModel::navigateTo)
            SectionItem(AppSection.PLAY, Icons.Filled.GridView, stringResource(R.string.nav_play), currentSection, viewModel::navigateTo)
            SectionItem(AppSection.BOTS, Icons.Filled.SmartToy, stringResource(R.string.nav_bots), currentSection) {
              botsInitialTab = 0
              viewModel.navigateTo(it)
            }
            SectionItem(AppSection.ANALYSIS, Icons.Filled.Analytics, stringResource(R.string.nav_analysis), currentSection) {
              analysisInitialTab = 0
              viewModel.navigateTo(it)
            }
            SectionItem(AppSection.ONLINE, Icons.Filled.Public, stringResource(R.string.nav_online), currentSection) {
              onlineInitialTab = 0
              viewModel.navigateTo(it)
            }
            SectionItem(AppSection.PUZZLES, Icons.Filled.Extension, stringResource(R.string.nav_puzzles), currentSection, viewModel::navigateTo)
            SectionItem(AppSection.PROFILES, Icons.Filled.Person, stringResource(R.string.nav_profiles), currentSection, viewModel::navigateTo)
            SectionItem(AppSection.MUSIC, Icons.Filled.MusicNote, stringResource(R.string.nav_music), currentSection, viewModel::navigateTo)
            SectionItem(AppSection.SETTINGS, Icons.Filled.Settings, stringResource(R.string.nav_settings), currentSection, viewModel::navigateTo)
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          when (currentSection) {
            AppSection.HOME -> HomeScreen(
              settings = settings,
              unfinishedGame = unfinishedGame,
              recentGames = recentGames,
              lichessProfile = lichessProfile,
              chessComProfile = chessComProfile,
              musicState = musicState,
              evaluatedBadges = evaluatedBadges,
              onQuickPlay = { viewModel.startQuickPlay() },
              onStartLocalTwoPlayer = {
                viewModel.startNewGame(
                  mode = GameMode.HUMAN_VS_HUMAN,
                  white = Opponent.Human("Player 1"),
                  black = Opponent.Human("Player 2"),
                  timeControl = TimeControl.presets[6],
                )
              },
              onContinueGame = { viewModel.resumeUnfinishedGame(it) },
              onReplayGame = { viewModel.loadGameForReplay(it) },
              onNavigate = { sec ->
                if (sec == AppSection.ANALYSIS) analysisInitialTab = 0
                if (sec == AppSection.ONLINE) onlineInitialTab = 0
                if (sec == AppSection.BOTS) botsInitialTab = 0
                viewModel.navigateTo(sec)
              },
              onToggleMusic = { viewModel.musicController.togglePlayPause() },
              onOpenBookPractice = {
                analysisInitialTab = 2
                viewModel.navigateTo(AppSection.ANALYSIS)
              },
              onOpenWatchLive = {
                onlineInitialTab = 1
                viewModel.navigateTo(AppSection.ONLINE)
              },
              onOpenNews = {
                onlineInitialTab = 3
                viewModel.navigateTo(AppSection.ONLINE)
              },
              onOpenTournaments = {
                botsInitialTab = 2
                viewModel.navigateTo(AppSection.BOTS)
              },
              onOpenBadges = { viewModel.navigateTo(AppSection.PROFILES) },
            )

            AppSection.PLAY -> PlayScreen(
              gameState = gameState,
              settings = settings,
              engineStatus = engineStatus,
              historyGames = historyGames,
              historyQuery = historyQuery,
              historySort = historySort,
              onlineChatMessages = onlineChatMessages,
              onSquareTap = viewModel::onSquareTapped,
              onPromotionSelected = viewModel::onPromotionSelected,
              onDismissPromotion = viewModel::dismissPromotion,
              onUndo = viewModel::undoMove,
              onFlip = viewModel::flipBoard,
              onResign = viewModel::resignGame,
              onOfferDraw = viewModel::offerDraw,
              onAcceptDraw = viewModel::acceptDraw,
              onDeclineDraw = viewModel::declineDraw,
              onRestart = viewModel::restartGame,
              onSaveGame = viewModel::saveCurrentGameManually,
              onRequestHint = viewModel::requestTrainingHint,
              onReplayStep = viewModel::setReplayStep,
              onExitReplay = viewModel::exitReplayMode,
              onStartNewMatch = { mode, w, b, tc ->
                viewModel.startNewGame(mode, w, b, tc, navigateToPlay = false)
              },
              onStartCustomMatch = { mode, w, b, tc, variant, seed, handicapPiece, handicapFromWhite, customFen ->
                viewModel.startCustomGame(
                  mode = mode,
                  white = w,
                  black = b,
                  timeControl = tc,
                  variant = variant,
                  chess960Seed = seed,
                  handicapPiece = handicapPiece,
                  handicapFromWhite = handicapFromWhite,
                  customFen = customFen,
                  navigateToPlay = false,
                )
              },
              onExportCurrentPgn = viewModel::exportCurrentGamePgn,
              onSharePgn = onSharePgn,
              onImportPgn = viewModel::importPgnToDatabase,
              onHistoryQueryChange = viewModel::updateHistoryQuery,
              onHistorySortChange = viewModel::updateHistorySort,
              onLoadMoreHistory = viewModel::loadMoreHistory,
              onReplaySavedGame = viewModel::loadGameForReplay,
              onDeleteSavedGame = viewModel::deleteGame,
              onMessage = viewModel::postMessage,
              onDismissBotDialogue = viewModel::dismissBotDialogue,
              onSwitchBot = {
                botsInitialTab = 0
                viewModel.navigateTo(AppSection.BOTS)
              },
              onOpenAnalysis = { openReviewTab ->
                analysisInitialTab = if (openReviewTab) 1 else 0
                viewModel.openAnalysisForCurrentGame(autoRunReview = openReviewTab)
              },
              onImportFen = { fen -> viewModel.loadFenIntoPlay(fen) },
              onSendOnlineChat = viewModel::sendOnlineChat,
            )

            AppSection.BOTS -> BotsScreen(
              botStats = botStats,
              tournamentProgress = tournamentProgress,
              engineStatus = engineStatus,
              settings = settings,
              initialTab = botsInitialTab,
              onPlayVsBot = { bot, training ->
                viewModel.startNewGame(
                  mode = if (training) GameMode.BOT_TRAINING else GameMode.HUMAN_VS_BOT,
                  white = Opponent.Human(settings.playerName.ifBlank { "ChessHub Player" }),
                  black = Opponent.Bot(bot),
                  timeControl = if (training) TimeControl.presets.last() else TimeControl.presets[6],
                  trainingMode = training,
                )
              },
              onPlayVsBotWithColor = { bot, training, playWhiteChoice ->
                val playWhite = playWhiteChoice ?: kotlin.random.Random.nextBoolean()
                val human = Opponent.Human(settings.playerName.ifBlank { "ChessHub Player" })
                val botOpp = Opponent.Bot(bot)
                viewModel.startNewGame(
                  mode = if (training) GameMode.BOT_TRAINING else GameMode.HUMAN_VS_BOT,
                  white = if (playWhite) human else botOpp,
                  black = if (playWhite) botOpp else human,
                  timeControl = if (training) TimeControl.presets.last() else TimeControl.presets[6],
                  trainingMode = training,
                )
              },
              onWatchBotVsBot = { whiteBot, blackBot ->
                viewModel.startNewGame(
                  mode = GameMode.BOT_VS_BOT,
                  white = Opponent.Bot(whiteBot),
                  black = Opponent.Bot(blackBot),
                  timeControl = TimeControl.presets[4],
                )
              },
              onStartTournament = viewModel::startFourBotTournament,
              onCancelTournament = viewModel::cancelTournament,
              onStartCustomTournament = viewModel::startBotTournament,
            )

            AppSection.ONLINE -> OnlineScreen(
              settings = settings,
              connectionState = onlineState,
              onSaveServerUrl = viewModel::saveOnlineServerUrl,
              onConnect = viewModel::connectOnline,
              onDisconnect = { viewModel.onlineClient.disconnect() },
              onRequestMatch = viewModel::requestOnlineMatch,
              publicFeed = publicFeed,
              onlineChatMessages = onlineChatMessages,
              onSendChat = viewModel::sendOnlineChat,
              onRefreshTv = viewModel::refreshTvChannels,
              onWatchPublicGame = viewModel::watchPublicGame,
              onRefreshTournaments = viewModel::refreshLiveTournaments,
              onRefreshNews = viewModel::refreshChessNews,
              onOpenOfflineTournaments = {
                botsInitialTab = 2
                viewModel.navigateTo(AppSection.BOTS)
              },
              initialTab = onlineInitialTab,
            )

            AppSection.PUZZLES -> PuzzlesScreen(
              viewModel = puzzlesViewModel,
              settingsOverride = settings,
            )

            AppSection.PROFILES -> ProfilesScreen(
              settings = settings,
              lichessProfile = lichessProfile,
              chessComProfile = chessComProfile,
              evaluatedBadges = evaluatedBadges,
              publicFeed = publicFeed,
              onUpdatePlayerName = { name ->
                val clean = name.trim().take(24)
                if (clean.isNotEmpty()) {
                  viewModel.updateSettings { it.copy(playerName = clean) }
                  viewModel.postMessage("Profile name updated")
                }
              },
              onLinkLichess = viewModel::linkAndRefreshLichess,
              onRefreshLichess = { viewModel.refreshLichess() },
              onUnlinkLichess = viewModel::unlinkLichess,
              onLinkChessCom = viewModel::linkAndRefreshChessCom,
              onRefreshChessCom = { viewModel.refreshChessCom() },
              onUnlinkChessCom = viewModel::unlinkChessCom,
              onReplayPublicGame = { g ->
                viewModel.loadPgnTextForReplay(g.pgn, g.white, g.black)
              },
              onScoutPlayer = viewModel::scoutOtherPlayer,
            )

            AppSection.MUSIC -> MusicScreen(
              musicState = musicState,
              onScanDeviceAudio = viewModel.musicController::scanDeviceAudio,
              onPickAudioUris = viewModel.musicController::addPickedUris,
              onPlayIndex = viewModel.musicController::playAtIndex,
              onTogglePlayPause = viewModel.musicController::togglePlayPause,
              onNext = viewModel.musicController::next,
              onPrevious = viewModel.musicController::previous,
              onSeek = viewModel.musicController::seekTo,
              onVolumeChange = { v ->
                viewModel.musicController.setVolume(v)
                viewModel.updateSettings { it.copy(musicVolume = v) }
              },
              onToggleShuffle = {
                viewModel.musicController.toggleShuffle()
                val next = !musicState.shuffle
                viewModel.updateSettings { it.copy(musicShuffle = next) }
              },
              onCycleRepeat = {
                val nextMode: RepeatMode = viewModel.musicController.cycleRepeat()
                viewModel.updateSettings { it.copy(musicRepeatMode = nextMode.name) }
              },
              onPermissionDeniedMessage = viewModel::postMessage,
            )

            AppSection.ANALYSIS -> AnalysisSuiteScreen(
              session = analysisSession,
              settings = settings,
              initialTab = analysisInitialTab,
              onSquareTap = viewModel::onAnalysisSquareTapped,
              onUndoMove = viewModel::undoAnalysisMove,
              onStepPly = viewModel::stepAnalysisPly,
              onFlipBoard = viewModel::flipAnalysisBoard,
              onPlaySuggestedMove = viewModel::playAnalysisMove,
              onPlayBookMove = viewModel::playAnalysisBookMove,
              onRunGameReview = viewModel::runGameReview,
              onCancelGameReview = viewModel::cancelGameReview,
              onLoadDrill = viewModel::loadTrainerDrill,
              onTogglePracticeMode = viewModel::toggleAnalysisPracticeMode,
              onLoadFenToAnalysis = { fen -> viewModel.loadFenIntoAnalysis(fen) },
              onPlayFenVsBot = { fen, bot -> viewModel.loadFenIntoPlay(fen, bot) },
              onMessage = viewModel::postMessage,
              onStartBookPractice = viewModel::startBookPractice,
              onSetDepthMode = viewModel::setAnalysisDepthMode,
            )

            AppSection.SETTINGS -> SettingsScreen(
              settings = settings,
              memoryProfile = viewModel.memoryProfile,
              onUpdateSettings = viewModel::updateSettings,
              onLanguageChanged = onApplyLanguage,
              onImportPgn = viewModel::importPgnToDatabase,
              onExportAllPgn = viewModel::exportAllPgns,
              onSharePgn = onSharePgn,
              onClearCache = viewModel::clearProfileCache,
              onDeleteAllData = viewModel::deleteAllLocalData,
              onNavigate = viewModel::navigateTo,
            )
          }
        }
      }
    }
  }

  if (showMoreSheet) {
    ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
      Column(modifier = Modifier.padding(bottom = 24.dp)) {
        MoreSheetItem(Icons.Filled.Analytics, stringResource(R.string.nav_analysis)) {
          analysisInitialTab = 0
          showMoreSheet = false
          viewModel.navigateTo(AppSection.ANALYSIS)
        }
        MoreSheetItem(Icons.Filled.Public, stringResource(R.string.nav_online)) {
          onlineInitialTab = 0
          showMoreSheet = false
          viewModel.navigateTo(AppSection.ONLINE)
        }
        MoreSheetItem(Icons.Filled.Person, stringResource(R.string.nav_profiles)) {
          showMoreSheet = false
          viewModel.navigateTo(AppSection.PROFILES)
        }
        MoreSheetItem(Icons.Filled.MusicNote, stringResource(R.string.nav_music)) {
          showMoreSheet = false
          viewModel.navigateTo(AppSection.MUSIC)
        }
        MoreSheetItem(Icons.Filled.Settings, stringResource(R.string.nav_settings)) {
          showMoreSheet = false
          viewModel.navigateTo(AppSection.SETTINGS)
        }
      }
    }
  }
}

@Composable
private fun SectionItem(
  section: AppSection,
  icon: ImageVector,
  label: String,
  current: AppSection,
  onSelect: (AppSection) -> Unit,
) {
  NavigationRailItem(
    selected = current == section,
    onClick = { onSelect(section) },
    icon = { Icon(icon, contentDescription = label) },
    label = { Text(label, maxLines = 1) },
  )
}

@Composable
private fun MoreSheetItem(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  ListItem(
    headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
    leadingContent = { Icon(icon, contentDescription = null) },
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("more_${label.lowercase()}"),
  )
}
