package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.PuzzleUiState
import com.example.R
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.database.AppSettingsEntity
import com.example.database.PuzzleProgressEntity
import com.example.puzzles.PuzzleCatalog
import com.example.puzzles.PuzzlesViewModel
import com.example.ui.board.BoardThemeOption
import com.example.ui.board.ChessBoardView
import com.example.ui.board.PieceStyleOption

@Composable
fun PuzzlesScreen(
  viewModel: PuzzlesViewModel,
  settingsOverride: AppSettingsEntity? = null,
) {
  val puzzleState by viewModel.puzzleState.collectAsStateWithLifecycle()
  val vmSettings by viewModel.settings.collectAsStateWithLifecycle()
  val progressList by viewModel.progressList.collectAsStateWithLifecycle()
  val settings = settingsOverride ?: vmSettings

  PuzzlesScreenContent(
    puzzleState = puzzleState,
    settings = settings,
    progressList = progressList,
    themeFilters = viewModel.themeFilters,
    onSquareTap = viewModel::onSquareTapped,
    onPromotionSelected = viewModel::onPromotionSelected,
    onDismissPromotion = viewModel::dismissPromotion,
    onHint = viewModel::revealHint,
    onRetry = viewModel::retryCurrentPuzzle,
    onNextOrRandomPuzzle = viewModel::loadNextOrRandomPuzzle,
    onSelectPuzzle = viewModel::loadPuzzleById,
    onSelectThemeFilter = viewModel::selectThemeFilter,
  )
}

@Composable
fun PuzzlesScreenContent(
  puzzleState: PuzzleUiState,
  settings: AppSettingsEntity,
  progressList: List<PuzzleProgressEntity>,
  themeFilters: List<String>,
  onSquareTap: (Int) -> Unit,
  onPromotionSelected: (PieceType) -> Unit,
  onDismissPromotion: () -> Unit,
  onHint: () -> Unit,
  onRetry: () -> Unit,
  onNextOrRandomPuzzle: () -> Unit,
  onSelectPuzzle: (String) -> Unit,
  onSelectThemeFilter: (String) -> Unit,
) {
  var showHistoryDialog by remember { mutableStateOf(false) }
  val solvedCount = remember(progressList) { progressList.count { it.solved } }
  val progressMap = remember(progressList) { progressList.associateBy { it.puzzleId } }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag("puzzles_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Header: Puzzle title, motif, rating & history drawer
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.puzzles_title),
          style = MaterialTheme.typography.titleLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${puzzleState.puzzle.title} • ${puzzleState.puzzle.theme} (${puzzleState.puzzle.rating})",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(
        onClick = { showHistoryDialog = true },
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("puzzles_history_btn"),
      ) {
        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.puzzles_history))
      }
    }

    // Theme filter chips
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp)
        .padding(top = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      contentPadding = PaddingValues(vertical = 2.dp),
    ) {
      items(themeFilters, key = { it }) { filter ->
        val label = if (filter == "All") stringResource(R.string.puzzles_theme_all) else filter
        FilterChip(
          selected = puzzleState.selectedThemeFilter == filter,
          onClick = { onSelectThemeFilter(filter) },
          label = { Text(label, maxLines = 1) },
        )
      }
    }

    // Compact 4-metric stats strip (Rating, Streak, Solved, Attempts)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp)
        .padding(vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      PuzzleStatChip(
        label = stringResource(R.string.puzzles_rating),
        value = settings.puzzleRating.toString(),
        modifier = Modifier.weight(1f),
      )
      PuzzleStatChip(
        label = stringResource(R.string.puzzles_streak),
        value = settings.puzzleStreak.toString(),
        modifier = Modifier.weight(1f),
      )
      PuzzleStatChip(
        label = stringResource(R.string.puzzles_solved),
        value = "$solvedCount/${PuzzleCatalog.puzzles.size}",
        modifier = Modifier.weight(1f),
      )
      PuzzleStatChip(
        label = stringResource(R.string.puzzles_attempts),
        value = puzzleState.attemptsOnCurrent.toString(),
        modifier = Modifier.weight(1f).testTag("puzzles_attempts_chip"),
      )
    }

    // Side-to-move & step progress indicator
    val turnText = if (puzzleState.boardState.turn == PieceColor.WHITE) {
      stringResource(R.string.play_white_turn)
    } else {
      stringResource(R.string.play_black_turn)
    }
    val currentMoveNumber = ((puzzleState.stepIndex / 2) + 1).coerceAtMost(puzzleState.totalPlayerMoves)
    val stepText = stringResource(
      R.string.puzzles_step_progress,
      currentMoveNumber,
      puzzleState.totalPlayerMoves,
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp)
        .padding(bottom = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = turnText,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = stepText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Interactive Tactical ChessBoard
    ChessBoardView(
      boardState = puzzleState.boardState,
      selectedSquare = puzzleState.selectedSquare,
      legalMoves = puzzleState.legalMoves,
      lastMoveFrom = puzzleState.lastMoveFrom,
      lastMoveTo = puzzleState.lastMoveTo,
      hintMove = puzzleState.hintMove,
      isFlipped = puzzleState.playerColor == PieceColor.BLACK,
      boardTheme = BoardThemeOption.fromKey(settings.boardTheme),
      pieceStyle = PieceStyleOption.fromKey(settings.pieceStyle),
      showCoordinates = settings.showCoordinates,
      showLegalMoves = settings.showLegalMoves,
      animateMoves = settings.animateMoves && !settings.reduceAnimations,
      onSquareTap = onSquareTap,
      modifier = Modifier
        .weight(1f, fill = false)
        .widthIn(max = 520.dp),
    )

    Spacer(modifier = Modifier.height(6.dp))

    // Animated user attempt feedback banner
    AnimatedVisibility(
      visible = puzzleState.statusMessage != null,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      puzzleState.statusMessage?.let { msg ->
        val containerColor = when {
          puzzleState.isSolved -> MaterialTheme.colorScheme.primaryContainer
          puzzleState.hadMistake -> MaterialTheme.colorScheme.errorContainer
          else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val textColor = when {
          puzzleState.isSolved -> MaterialTheme.colorScheme.onPrimaryContainer
          puzzleState.hadMistake -> MaterialTheme.colorScheme.onErrorContainer
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 580.dp)
            .padding(bottom = 4.dp)
            .testTag("puzzles_status_banner"),
          color = containerColor,
          shape = MaterialTheme.shapes.small,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = msg,
              style = MaterialTheme.typography.labelLarge,
              color = textColor,
              modifier = Modifier.weight(1f),
            )
            if (puzzleState.wrongAttemptsOnCurrent > 0) {
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.puzzles_mistakes_count, puzzleState.wrongAttemptsOnCurrent),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
              )
            }
          }
        }
      }
    }

    // Animated hint banner
    AnimatedVisibility(
      visible = puzzleState.showHint,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 580.dp)
          .padding(bottom = 4.dp)
          .testTag("puzzles_hint_banner"),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
      ) {
        Text(
          text = puzzleState.puzzle.hint,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }

    // Bottom Action Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = onHint,
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 48.dp)
          .testTag("puzzles_hint_btn"),
      ) {
        Icon(Icons.Filled.Lightbulb, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.puzzles_hint), maxLines = 1)
      }
      OutlinedButton(
        onClick = onRetry,
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 48.dp)
          .testTag("puzzles_retry_btn"),
      ) {
        Icon(Icons.Filled.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.puzzles_retry), maxLines = 1)
      }
      Button(
        onClick = onNextOrRandomPuzzle,
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 48.dp)
          .testTag("puzzles_next_btn"),
      ) {
        Icon(
          if (puzzleState.isSolved) Icons.AutoMirrored.Filled.NavigateNext else Icons.Filled.Casino,
          contentDescription = null,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (puzzleState.isSolved) stringResource(R.string.puzzles_next) else stringResource(R.string.puzzles_random),
          maxLines = 1,
        )
      }
    }
  }

  // Promotion Dialog for Promotion / Underpromotion tactical puzzles
  if (puzzleState.pendingPromotionFrom != null && puzzleState.pendingPromotionTo != null) {
    AlertDialog(
      onDismissRequest = onDismissPromotion,
      title = { Text(stringResource(R.string.play_promote_title)) },
      text = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { pt ->
            val label = when (pt) {
              PieceType.QUEEN -> "Q"
              PieceType.ROOK -> "R"
              PieceType.BISHOP -> "B"
              PieceType.KNIGHT -> "N"
              else -> ""
            }
            OutlinedButton(
              onClick = { onPromotionSelected(pt) },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("puzzle_promote_${pt.name.lowercase()}"),
            ) {
              Text(label, fontWeight = FontWeight.Bold)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = onDismissPromotion) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  // Catalog & Session Attempts History Dialog
  if (showHistoryDialog) {
    var selectedTab by remember { mutableIntStateOf(0) }
    AlertDialog(
      onDismissRequest = { showHistoryDialog = false },
      title = { Text(stringResource(R.string.puzzles_history)) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          TabRow(selectedTabIndex = selectedTab) {
            Tab(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              text = { Text(stringResource(R.string.puzzles_catalog_tab)) },
            )
            Tab(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              text = { Text("${stringResource(R.string.puzzles_attempts_tab)} (${puzzleState.sessionAttempts.size})") },
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          if (selectedTab == 0) {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              items(PuzzleCatalog.puzzles, key = { it.id }) { pz ->
                val st = progressMap[pz.id]
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      onSelectPuzzle(pz.id)
                      showHistoryDialog = false
                    },
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(pz.title, style = MaterialTheme.typography.labelLarge)
                      val failsText = st?.takeIf { it.failedAttempts > 0 }?.let {
                        " • ${it.failedAttempts}×"
                      } ?: ""
                      Text("${pz.theme} • ${pz.rating}$failsText", style = MaterialTheme.typography.labelSmall)
                    }
                    if (st?.solved == true) {
                      Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.puzzles_solved),
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
              }
            }
          } else {
            if (puzzleState.sessionAttempts.isEmpty()) {
              Text(
                text = stringResource(R.string.puzzles_no_attempts_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
              )
            } else {
              LazyColumn(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                items(puzzleState.sessionAttempts, key = { "${it.puzzleId}_${it.timestampMillis}" }) { attempt ->
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        onSelectPuzzle(attempt.puzzleId)
                        showHistoryDialog = false
                      },
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(attempt.title, style = MaterialTheme.typography.labelLarge)
                        Text(
                          text = "${attempt.theme} • ${attempt.attemptsCount} tries (${attempt.wrongCount} wrong)",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Icon(
                        imageVector = if (attempt.solved) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = if (attempt.solved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showHistoryDialog = false }) {
          Text(stringResource(R.string.close))
        }
      },
    )
  }
}

@Composable
private fun PuzzleStatChip(label: String, value: String, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
    }
  }
}
