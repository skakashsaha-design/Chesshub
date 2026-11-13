package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.PuzzleUiState
import com.example.R
import com.example.chess.PieceColor
import com.example.database.AppSettingsEntity
import com.example.database.PuzzleProgressEntity
import com.example.puzzles.PuzzleCatalog
import com.example.ui.board.BoardThemeOption
import com.example.ui.board.ChessBoardView
import com.example.ui.board.PieceStyleOption

@Composable
fun PuzzlesScreen(
  puzzleState: PuzzleUiState,
  settings: AppSettingsEntity,
  progressList: List<PuzzleProgressEntity>,
  onSquareTap: (Int) -> Unit,
  onHint: () -> Unit,
  onRetry: () -> Unit,
  onRandomPuzzle: () -> Unit,
  onSelectPuzzle: (String) -> Unit,
) {
  var showHistory by remember { mutableStateOf(false) }
  val solvedCount = remember(progressList) { progressList.count { it.solved } }
  val progressMap = remember(progressList) { progressList.associateBy { it.puzzleId } }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag("puzzles_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(stringResource(R.string.puzzles_title), style = MaterialTheme.typography.titleLarge)
        Text(
          text = "${puzzleState.puzzle.title} • ${puzzleState.puzzle.theme} (${puzzleState.puzzle.rating})",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = { showHistory = true }) {
        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.puzzles_history))
      }
    }

    // Stats strip
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp)
        .padding(vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      PuzzleStatChip(stringResource(R.string.puzzles_rating), settings.puzzleRating.toString(), Modifier.weight(1f))
      PuzzleStatChip(stringResource(R.string.puzzles_streak), settings.puzzleStreak.toString(), Modifier.weight(1f))
      PuzzleStatChip(stringResource(R.string.puzzles_solved), "$solvedCount/${PuzzleCatalog.puzzles.size}", Modifier.weight(1f))
    }

    val turnText = if (puzzleState.boardState.turn == PieceColor.WHITE) {
      stringResource(R.string.play_white_turn)
    } else {
      stringResource(R.string.play_black_turn)
    }
    Text(
      text = turnText,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(bottom = 4.dp),
    )

    ChessBoardView(
      boardState = puzzleState.boardState,
      selectedSquare = puzzleState.selectedSquare,
      legalMoves = puzzleState.legalMoves,
      lastMoveFrom = puzzleState.lastMoveFrom,
      lastMoveTo = puzzleState.lastMoveTo,
      isFlipped = puzzleState.boardState.turn == PieceColor.BLACK && puzzleState.stepIndex == 0,
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

    if (puzzleState.statusMessage != null) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 580.dp),
        color = if (puzzleState.isSolved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
      ) {
        Text(
          text = puzzleState.statusMessage,
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
    }

    if (puzzleState.showHint) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 580.dp),
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
      Spacer(modifier = Modifier.height(4.dp))
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(onClick = onHint, modifier = Modifier.weight(1f)) {
        Icon(Icons.Filled.Lightbulb, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.puzzles_hint))
      }
      OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
        Icon(Icons.Filled.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.puzzles_retry))
      }
      Button(onClick = onRandomPuzzle, modifier = Modifier.weight(1f).testTag("puzzles_next_btn")) {
        Icon(if (puzzleState.isSolved) Icons.Filled.NavigateNext else Icons.Filled.Casino, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (puzzleState.isSolved) stringResource(R.string.puzzles_next) else stringResource(R.string.puzzles_random))
      }
    }
  }

  if (showHistory) {
    AlertDialog(
      onDismissRequest = { showHistory = false },
      title = { Text(stringResource(R.string.puzzles_history)) },
      text = {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(PuzzleCatalog.puzzles, key = { it.id }) { pz ->
            val st = progressMap[pz.id]
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  onSelectPuzzle(pz.id)
                  showHistory = false
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
                  Text("${pz.theme} • ${pz.rating}", style = MaterialTheme.typography.labelSmall)
                }
                if (st?.solved == true) {
                  Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Solved",
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showHistory = false }) {
          Text(stringResource(R.string.close))
        }
      },
    )
  }
}

@Composable
private fun PuzzleStatChip(label: String, value: String, modifier: Modifier = Modifier) {
  Card(modifier = modifier) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
      Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
  }
}
