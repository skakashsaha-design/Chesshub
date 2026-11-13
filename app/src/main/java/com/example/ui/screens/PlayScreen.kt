package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ActiveGameUiState
import com.example.GameMode
import com.example.R
import com.example.bots.BotCatalog
import com.example.chess.BotProfile
import com.example.chess.GameResult
import com.example.chess.Opponent
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.TerminationReason
import com.example.database.AppSettingsEntity
import com.example.database.GameSummaryRow
import com.example.engine.StockfishEngineStatus
import com.example.offline.ChessClockTracker
import com.example.offline.TimeControl
import com.example.ui.board.BoardThemeOption
import com.example.ui.board.ChessBoardView
import com.example.ui.board.PieceStyleOption

@Composable
fun PlayScreen(
  gameState: ActiveGameUiState,
  settings: AppSettingsEntity,
  engineStatus: StockfishEngineStatus = StockfishEngineStatus(),
  historyGames: List<GameSummaryRow>,
  historyQuery: String,
  historySort: String,
  onSquareTap: (Int) -> Unit,
  onPromotionSelected: (PieceType) -> Unit,
  onDismissPromotion: () -> Unit,
  onUndo: () -> Unit,
  onFlip: () -> Unit,
  onResign: () -> Unit,
  onOfferDraw: () -> Unit,
  onAcceptDraw: () -> Unit,
  onDeclineDraw: () -> Unit,
  onRestart: () -> Unit,
  onSaveGame: () -> Unit,
  onRequestHint: () -> Unit,
  onReplayStep: (Int) -> Unit,
  onExitReplay: () -> Unit,
  onStartNewMatch: (GameMode, Opponent, Opponent, TimeControl) -> Unit,
  onExportCurrentPgn: () -> String,
  onSharePgn: (String) -> Unit,
  onImportPgn: (String) -> Boolean,
  onHistoryQueryChange: (String) -> Unit,
  onHistorySortChange: (String) -> Unit,
  onLoadMoreHistory: () -> Unit,
  onReplaySavedGame: (Long) -> Unit,
  onDeleteSavedGame: (Long) -> Unit,
  onMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  var showNewGameDialog by remember { mutableStateOf(false) }
  var showHistoryDialog by remember { mutableStateOf(false) }
  var showPgnDialog by remember { mutableStateOf(false) }

  val topColor = if (gameState.isFlipped) PieceColor.WHITE else PieceColor.BLACK
  val bottomColor = if (gameState.isFlipped) PieceColor.BLACK else PieceColor.WHITE

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 10.dp, vertical = 6.dp)
      .testTag("play_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Top bar: Opening badge + History + New Game
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        val openingText = gameState.opening?.let { "${it.eco} • ${it.name}" }
          ?: if (gameState.replayIndex != null) stringResource(R.string.play_replay_mode)
          else gameState.timeControl.label
        Text(
          text = openingText,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (gameState.isBotThinking) {
          Text(
            text = stringResource(R.string.play_bot_thinking),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        } else if (gameState.pendingConfirmMove != null) {
          Text(
            text = stringResource(R.string.play_confirm_move_prompt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
      Row {
        IconButton(onClick = { showHistoryDialog = true }, modifier = Modifier.testTag("play_history_btn")) {
          Icon(Icons.Filled.History, contentDescription = stringResource(R.string.play_history))
        }
        FilledNewGameButton(onClick = { showNewGameDialog = true })
      }
    }

    // Optional training evaluation bar
    if (gameState.trainingMode) {
      val mateIn = gameState.evalMateIn
      val whiteAdv = when {
        mateIn != null && mateIn > 0 -> 0.96f
        mateIn != null && mateIn < 0 -> 0.04f
        else -> ((gameState.evalCpForWhite / 800f) + 0.5f).coerceIn(0.05f, 0.95f)
      }
      val evalLabel = when {
        mateIn != null && mateIn > 0 -> "M$mateIn"
        mateIn != null && mateIn < 0 -> "-M${kotlin.math.abs(mateIn)}"
        else -> "%+.1f".format(gameState.evalCpForWhite / 100f)
      }
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 580.dp)
          .padding(vertical = 2.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LinearProgressIndicator(
            progress = { whiteAdv },
            modifier = Modifier.weight(1f).height(6.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = evalLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("training_eval_label"),
          )
        }
        val summarySuffix = engineStatus.lastInfoSummary.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
        Text(
          text = "${engineStatus.backend.label}$summarySuffix",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Top player strip
    PlayerClockBar(
      opponent = if (topColor == PieceColor.WHITE) gameState.whiteOpponent else gameState.blackOpponent,
      timeMs = if (topColor == PieceColor.WHITE) gameState.whiteTimeMs else gameState.blackTimeMs,
      isUnlimited = gameState.timeControl.isUnlimited || gameState.trainingMode,
      isActiveTurn = gameState.boardState.turn == topColor && gameState.result == GameResult.IN_PROGRESS,
      isWhite = topColor == PieceColor.WHITE,
      modifier = Modifier.widthIn(max = 580.dp),
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Responsive Chessboard — gets maximum screen space
    ChessBoardView(
      boardState = gameState.boardState,
      selectedSquare = gameState.selectedSquare,
      legalMoves = gameState.legalMoves,
      lastMoveFrom = gameState.lastMoveFrom,
      lastMoveTo = gameState.lastMoveTo,
      hintMove = gameState.hintMove,
      isFlipped = gameState.isFlipped,
      boardTheme = BoardThemeOption.fromKey(settings.boardTheme),
      pieceStyle = PieceStyleOption.fromKey(settings.pieceStyle),
      showCoordinates = settings.showCoordinates,
      showLegalMoves = settings.showLegalMoves,
      animateMoves = settings.animateMoves && !settings.reduceAnimations,
      onSquareTap = onSquareTap,
      modifier = Modifier
        .weight(1f, fill = false)
        .widthIn(max = 540.dp),
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Bottom player strip
    PlayerClockBar(
      opponent = if (bottomColor == PieceColor.WHITE) gameState.whiteOpponent else gameState.blackOpponent,
      timeMs = if (bottomColor == PieceColor.WHITE) gameState.whiteTimeMs else gameState.blackTimeMs,
      isUnlimited = gameState.timeControl.isUnlimited || gameState.trainingMode,
      isActiveTurn = gameState.boardState.turn == bottomColor && gameState.result == GameResult.IN_PROGRESS,
      isWhite = bottomColor == PieceColor.WHITE,
      modifier = Modifier.widthIn(max = 580.dp),
    )

    // Result banner if finished
    if (gameState.result != GameResult.IN_PROGRESS) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 580.dp)
          .padding(top = 4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
      ) {
        val resLabel = when (gameState.result) {
          GameResult.WHITE_WIN -> stringResource(R.string.play_white_wins)
          GameResult.BLACK_WIN -> stringResource(R.string.play_black_wins)
          GameResult.DRAW -> stringResource(R.string.play_draw_result)
          GameResult.IN_PROGRESS -> ""
        }
        val termLabel = when (gameState.termination) {
          TerminationReason.CHECKMATE -> stringResource(R.string.play_checkmate)
          TerminationReason.STALEMATE -> stringResource(R.string.play_stalemate)
          TerminationReason.THREEFOLD_REPETITION -> stringResource(R.string.play_draw_threefold)
          TerminationReason.FIFTY_MOVE_RULE -> stringResource(R.string.play_draw_fifty)
          TerminationReason.INSUFFICIENT_MATERIAL -> stringResource(R.string.play_draw_insufficient)
          TerminationReason.RESIGNATION -> stringResource(R.string.play_resigned)
          TerminationReason.DRAW_AGREEMENT -> stringResource(R.string.play_draw_agreement)
          TerminationReason.TIMEOUT -> stringResource(R.string.play_timeout)
          TerminationReason.NONE -> ""
        }
        Text(
          text = if (termLabel.isNotBlank()) "$resLabel • $termLabel" else resLabel,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }

    // Move strip (or Replay controls)
    if (gameState.replayIndex != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 580.dp)
          .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        IconButton(onClick = { onReplayStep((gameState.replayIndex - 1).coerceAtLeast(0)) }) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous move")
        }
        Text(
          text = "Move ${gameState.replayIndex} / ${gameState.moves.size}",
          style = MaterialTheme.typography.labelMedium,
        )
        IconButton(onClick = { onReplayStep(gameState.replayIndex + 1) }) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next move")
        }
        OutlinedButton(onClick = onExitReplay) {
          Text(stringResource(R.string.play_exit_replay))
        }
      }
    } else if (gameState.moves.isNotEmpty()) {
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 580.dp)
          .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        itemsIndexed(gameState.moves) { idx, rm ->
          val prefix = if (idx % 2 == 0) "${idx / 2 + 1}." else ""
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { onReplayStep(idx + 1) },
          ) {
            Text(
              text = "$prefix${rm.san}",
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }
    }

    // Action Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (gameState.mode != GameMode.ONLINE) {
        IconButton(
          onClick = onUndo,
          enabled = gameState.moves.isNotEmpty() && !gameState.isBotThinking,
          modifier = Modifier.testTag("play_undo_btn"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.play_undo))
        }
      }
      IconButton(onClick = onFlip, modifier = Modifier.testTag("play_flip_btn")) {
        Icon(Icons.Filled.Autorenew, contentDescription = stringResource(R.string.play_rotate))
      }
      if (gameState.trainingMode) {
        IconButton(onClick = onRequestHint) {
          Icon(Icons.Filled.Lightbulb, contentDescription = stringResource(R.string.puzzles_hint))
        }
      } else if (gameState.result == GameResult.IN_PROGRESS && gameState.mode != GameMode.BOT_VS_BOT) {
        IconButton(onClick = onOfferDraw) {
          Icon(Icons.Filled.Handshake, contentDescription = stringResource(R.string.play_draw))
        }
        IconButton(onClick = onResign, modifier = Modifier.testTag("play_resign_btn")) {
          Icon(Icons.Filled.Flag, contentDescription = stringResource(R.string.play_resign))
        }
      }
      IconButton(onClick = onSaveGame) {
        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.play_save))
      }
      IconButton(onClick = { showPgnDialog = true }) {
        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.play_pgn))
      }
      IconButton(onClick = onRestart) {
        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.play_restart))
      }
    }
  }

  // Promotion Picker Dialog
  if (gameState.pendingPromotionFrom != null) {
    AlertDialog(
      onDismissRequest = onDismissPromotion,
      title = { Text(stringResource(R.string.play_promote_title)) },
      text = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { pt ->
            OutlinedButton(onClick = { onPromotionSelected(pt) }) {
              Text(pt.symbol.toString(), style = MaterialTheme.typography.titleLarge)
            }
          }
        }
      },
      confirmButton = {},
    )
  }

  // Draw Offer Dialog (for Human vs Human)
  if (gameState.drawOfferPendingFrom != null) {
    AlertDialog(
      onDismissRequest = onDeclineDraw,
      title = { Text(stringResource(R.string.play_draw_offer_title)) },
      text = { Text(stringResource(R.string.play_draw_offer_msg)) },
      confirmButton = {
        Button(onClick = onAcceptDraw) {
          Text(stringResource(R.string.play_accept))
        }
      },
      dismissButton = {
        TextButton(onClick = onDeclineDraw) {
          Text(stringResource(R.string.play_decline))
        }
      },
    )
  }

  // PGN Dialog (View, Copy, Share, Import)
  if (showPgnDialog) {
    val pgnText = remember(gameState.moves, gameState.result) { onExportCurrentPgn() }
    AlertDialog(
      onDismissRequest = { showPgnDialog = false },
      title = { Text(stringResource(R.string.play_pgn)) },
      text = {
        OutlinedTextField(
          value = pgnText,
          onValueChange = {},
          readOnly = true,
          maxLines = 8,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onSharePgn(pgnText)
            showPgnDialog = false
          },
        ) {
          Text(stringResource(R.string.history_share_pgn))
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cb?.setPrimaryClip(ClipData.newPlainText("ChessHub PGN", pgnText))
            onMessage(context.getString(R.string.history_pgn_copied))
            showPgnDialog = false
          },
        ) {
          Text("Copy PGN")
        }
      },
    )
  }

  if (showHistoryDialog) {
    GameHistoryDialog(
      games = historyGames,
      query = historyQuery,
      sortBy = historySort,
      onQueryChange = onHistoryQueryChange,
      onSortChange = onHistorySortChange,
      onLoadMore = onLoadMoreHistory,
      onReplay = onReplaySavedGame,
      onDelete = onDeleteSavedGame,
      onImportPgn = onImportPgn,
      onDismiss = { showHistoryDialog = false },
    )
  }

  if (showNewGameDialog) {
    NewGameSetupDialog(
      onStart = { mode, w, b, tc ->
        showNewGameDialog = false
        onStartNewMatch(mode, w, b, tc)
      },
      onDismiss = { showNewGameDialog = false },
    )
  }
}

@Composable
private fun FilledNewGameButton(onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.testTag("play_new_game_btn"),
  ) {
    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(4.dp))
    Text(stringResource(R.string.play_new_game))
  }
}

@Composable
private fun PlayerClockBar(
  opponent: Opponent,
  timeMs: Long,
  isUnlimited: Boolean,
  isActiveTurn: Boolean,
  isWhite: Boolean,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = if (isActiveTurn) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
      } else {
        MaterialTheme.colorScheme.surface
      },
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Box(
          modifier = Modifier
            .size(12.dp)
            .background(
              color = if (isWhite) androidx.compose.ui.graphics.Color(0xFFFDFBF7)
              else androidx.compose.ui.graphics.Color(0xFF1F242D),
              shape = MaterialTheme.shapes.small,
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = opponent.displayName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (opponent is Opponent.Bot) {
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "(${opponent.profile.rating})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Surface(
        color = if (isActiveTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
      ) {
        Text(
          text = ChessClockTracker.formatMillis(timeMs, isUnlimited),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = if (isActiveTurn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGameSetupDialog(
  onStart: (GameMode, Opponent, Opponent, TimeControl) -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedMode by remember { mutableStateOf(GameMode.HUMAN_VS_BOT) }
  var selectedBot: BotProfile by remember { mutableStateOf(BotCatalog.presets[2]) }
  var selectedColor by remember { mutableIntStateOf(0) } // 0=White, 1=Black, 2=Random
  var selectedTc by remember { mutableStateOf(TimeControl.presets[6]) } // 10+0
  var useCustomTc by remember { mutableStateOf(false) }
  var customMin by remember { mutableStateOf("10") }
  var customInc by remember { mutableStateOf("5") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.play_new_game)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(stringResource(R.string.play_opponent_type), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = selectedMode == GameMode.HUMAN_VS_BOT,
            onClick = { selectedMode = GameMode.HUMAN_VS_BOT },
            label = { Text(stringResource(R.string.play_mode_hvb)) },
          )
          FilterChip(
            selected = selectedMode == GameMode.HUMAN_VS_HUMAN,
            onClick = { selectedMode = GameMode.HUMAN_VS_HUMAN },
            label = { Text(stringResource(R.string.play_mode_hvh)) },
          )
          FilterChip(
            selected = selectedMode == GameMode.BOT_VS_BOT,
            onClick = { selectedMode = GameMode.BOT_VS_BOT },
            label = { Text(stringResource(R.string.play_mode_bvb)) },
          )
        }

        if (selectedMode == GameMode.HUMAN_VS_BOT) {
          Text(stringResource(R.string.nav_bots), style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotCatalog.presets.forEach { bot ->
              FilterChip(
                selected = selectedBot.id == bot.id,
                onClick = { selectedBot = bot },
                label = { Text("${bot.name} (${bot.rating})") },
              )
            }
          }
          Text(stringResource(R.string.play_your_color), style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = selectedColor == 0,
              onClick = { selectedColor = 0 },
              label = { Text(stringResource(R.string.play_color_white)) },
            )
            FilterChip(
              selected = selectedColor == 1,
              onClick = { selectedColor = 1 },
              label = { Text(stringResource(R.string.play_color_black)) },
            )
            FilterChip(
              selected = selectedColor == 2,
              onClick = { selectedColor = 2 },
              label = { Text(stringResource(R.string.play_color_random)) },
            )
          }
        }

        Text(stringResource(R.string.play_time_control), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          TimeControl.presets.forEach { tc ->
            FilterChip(
              selected = !useCustomTc && selectedTc.id == tc.id,
              onClick = {
                useCustomTc = false
                selectedTc = tc
              },
              label = { Text(tc.label) },
            )
          }
          FilterChip(
            selected = useCustomTc,
            onClick = { useCustomTc = true },
            label = { Text(stringResource(R.string.play_custom_time)) },
          )
        }

        if (useCustomTc) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = customMin,
              onValueChange = { customMin = it.filter(Char::isDigit) },
              label = { Text(stringResource(R.string.play_minutes)) },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = customInc,
              onValueChange = { customInc = it.filter(Char::isDigit) },
              label = { Text(stringResource(R.string.play_increment_sec)) },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val tc = if (useCustomTc) {
            TimeControl.custom(customMin.toIntOrNull() ?: 10, customInc.toIntOrNull() ?: 0)
          } else {
            selectedTc
          }
          when (selectedMode) {
            GameMode.HUMAN_VS_HUMAN -> onStart(
              GameMode.HUMAN_VS_HUMAN,
              Opponent.Human("Player 1"),
              Opponent.Human("Player 2"),
              tc,
            )
            GameMode.BOT_VS_BOT -> onStart(
              GameMode.BOT_VS_BOT,
              Opponent.Bot(BotCatalog.presets[2]),
              Opponent.Bot(BotCatalog.presets[3]),
              tc,
            )
            else -> {
              val playWhite = when (selectedColor) {
                0 -> true
                1 -> false
                else -> kotlin.random.Random.nextBoolean()
              }
              val w = if (playWhite) Opponent.Human("You") else Opponent.Bot(selectedBot)
              val b = if (playWhite) Opponent.Bot(selectedBot) else Opponent.Human("You")
              onStart(GameMode.HUMAN_VS_BOT, w, b, tc)
            }
          }
        },
      ) {
        Text(stringResource(R.string.play_start_match))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
