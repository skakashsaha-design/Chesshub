package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.analysis.AnalysisPolicy
import com.example.analysis.AnalysisSessionState
import com.example.analysis.MoveClassification
import com.example.analysis.MoveFeedbackAnalyzer
import com.example.analysis.TrainerDrill
import com.example.chess.BotProfile
import com.example.chess.ChessNotation
import com.example.chess.Move
import com.example.database.AppSettingsEntity
import com.example.ui.analysis.BoardEditorTab
import com.example.ui.analysis.EnginePvAndCandidatesCard
import com.example.ui.analysis.EvaluationBarStrip
import com.example.ui.analysis.EvaluationGraphCard
import com.example.ui.analysis.MoveFeedbackBanner
import com.example.ui.analysis.OpeningExplorerCard
import com.example.ui.analysis.TacticalAndEndgameCard
import com.example.ui.analysis.buildBoardArrows
import com.example.ui.board.BoardThemeOption
import com.example.ui.board.ChessBoardView
import com.example.ui.board.PieceStyleOption

@Composable
fun AnalysisSuiteScreen(
  session: AnalysisSessionState,
  settings: AppSettingsEntity,
  initialTab: Int = 0,
  onSquareTap: (Int) -> Unit,
  onUndoMove: () -> Unit,
  onStepPly: (Int) -> Unit,
  onFlipBoard: () -> Unit,
  onPlaySuggestedMove: (Move) -> Unit,
  onPlayBookMove: (String) -> Unit,
  onRunGameReview: () -> Unit,
  onCancelGameReview: () -> Unit,
  onLoadDrill: (TrainerDrill) -> Unit,
  onTogglePracticeMode: (Boolean) -> Unit,
  onLoadFenToAnalysis: (String) -> Boolean,
  onPlayFenVsBot: (String, BotProfile?) -> Boolean,
  onMessage: (String) -> Unit,
) {
  var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .testTag("analysis_suite_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    TabRow(
      selectedTabIndex = selectedTab,
      modifier = Modifier.widthIn(max = 600.dp),
    ) {
      Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Analyze") })
      Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Review") })
      Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Trainer") })
      Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Editor") })
    }

    when (selectedTab) {
      0 -> LiveAnalyzeTab(
        session = session,
        settings = settings,
        onSquareTap = onSquareTap,
        onUndoMove = onUndoMove,
        onStepPly = onStepPly,
        onFlipBoard = onFlipBoard,
        onPlaySuggestedMove = onPlaySuggestedMove,
        onPlayBookMove = onPlayBookMove,
        onLoadFenToAnalysis = onLoadFenToAnalysis,
        onMessage = onMessage,
      )
      1 -> GameReviewTab(
        session = session,
        settings = settings,
        onStepPly = { ply ->
          onStepPly(ply)
          selectedTab = 0
        },
        onRunGameReview = onRunGameReview,
        onCancelGameReview = onCancelGameReview,
      )
      2 -> TrainerAndPracticeTab(
        session = session,
        settings = settings,
        onSquareTap = onSquareTap,
        onUndoMove = onUndoMove,
        onLoadDrill = onLoadDrill,
        onTogglePracticeMode = onTogglePracticeMode,
        onPlaySuggestedMove = onPlaySuggestedMove,
      )
      3 -> BoardEditorTab(
        settings = settings,
        initialFen = ChessNotation.toFen(session.boardState),
        onAnalyzeFen = { fen ->
          val ok = onLoadFenToAnalysis(fen)
          if (ok) selectedTab = 0
          ok
        },
        onPlayFenVsBot = onPlayFenVsBot,
        onMessage = onMessage,
      )
    }
  }
}

@Composable
private fun LiveAnalyzeTab(
  session: AnalysisSessionState,
  settings: AppSettingsEntity,
  onSquareTap: (Int) -> Unit,
  onUndoMove: () -> Unit,
  onStepPly: (Int) -> Unit,
  onFlipBoard: () -> Unit,
  onPlaySuggestedMove: (Move) -> Unit,
  onPlayBookMove: (String) -> Unit,
  onLoadFenToAnalysis: (String) -> Boolean,
  onMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  var showFenDialog by remember { mutableStateOf(false) }
  var fenText by remember(session.boardState) { mutableStateOf(ChessNotation.toFen(session.boardState)) }
  val arrows = remember(session.snapshot, settings) { buildBoardArrows(session.snapshot, settings) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (AnalysisPolicy.showEvalBar(settings)) {
      item {
        EvaluationBarStrip(
          evalCpForWhite = session.snapshot.evalCpForWhite,
          mateIn = session.snapshot.mateInForWhite,
          isAnalyzing = session.snapshot.isAnalyzing,
          modifier = Modifier.widthIn(max = 580.dp),
        )
      }
    }

    item {
      val lastMove = if (session.currentPly > 0) session.historyMoves[session.currentPly - 1].move else null
      ChessBoardView(
        boardState = session.boardState,
        selectedSquare = session.selectedSquare,
        legalMoves = session.legalMoves,
        lastMoveFrom = lastMove?.from,
        lastMoveTo = lastMove?.to,
        arrows = arrows,
        isFlipped = session.isFlipped,
        boardTheme = BoardThemeOption.fromKey(settings.boardTheme),
        pieceStyle = PieceStyleOption.fromKey(settings.pieceStyle),
        showCoordinates = settings.showCoordinates,
        showLegalMoves = settings.showLegalMoves,
        animateMoves = settings.animateMoves && !settings.reduceAnimations,
        onSquareTap = onSquareTap,
        modifier = Modifier.widthIn(max = 480.dp),
      )
    }

    // Navigation & FEN bar
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        IconButton(onClick = { onStepPly(session.currentPly - 1) }, enabled = session.currentPly > 0) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous ply")
        }
        Text("Ply ${session.currentPly}/${session.historyMoves.size}", style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = { onStepPly(session.currentPly + 1) }, enabled = session.currentPly < session.historyMoves.size) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next ply")
        }
        IconButton(onClick = onUndoMove, enabled = session.currentPly > 0) {
          Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
        }
        IconButton(onClick = onFlipBoard) {
          Icon(Icons.Filled.Autorenew, contentDescription = "Flip board")
        }
        OutlinedButton(onClick = { showFenDialog = true }) {
          Text("FEN")
        }
      }
    }

    // Move Feedback for current move
    val fb = session.lastMoveFeedback
    if (settings.showMoveFeedback && fb != null) {
      item {
        MoveFeedbackBanner(
          entry = fb,
          showRecommendedMove = settings.enableEngineAnalysis && (settings.showSuggestionArrow || settings.showEngineLine),
          showCriticalBadge = settings.enableCriticalMoments,
          modifier = Modifier.widthIn(max = 580.dp),
        )
      }
    }

    if (!settings.enableEngineAnalysis) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp),
        ) {
          Text(
            text = "Engine Analysis is OFF in Settings (saving CPU/RAM).",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp),
          )
        }
      }
    }

    item {
      EnginePvAndCandidatesCard(
        snapshot = session.snapshot,
        settings = settings,
        onPlayMove = onPlaySuggestedMove,
        modifier = Modifier.widthIn(max = 580.dp),
      )
    }

    item {
      TacticalAndEndgameCard(
        snapshot = session.snapshot,
        settings = settings,
        modifier = Modifier.widthIn(max = 580.dp),
      )
    }

    if (settings.showOpeningInfo) {
      item {
        OpeningExplorerCard(
          snapshot = session.snapshot,
          onPlayBookMove = onPlayBookMove,
          modifier = Modifier.widthIn(max = 580.dp),
        )
      }
    }
  }

  if (showFenDialog) {
    AlertDialog(
      onDismissRequest = { showFenDialog = false },
      title = { Text("FEN Import & Export") },
      text = {
        OutlinedTextField(
          value = fenText,
          onValueChange = { fenText = it },
          label = { Text("Position FEN") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (onLoadFenToAnalysis(fenText)) showFenDialog = false
          },
        ) {
          Text("Import")
        }
      },
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              cb?.setPrimaryClip(ClipData.newPlainText("FEN", fenText))
              onMessage("FEN copied")
              showFenDialog = false
            },
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy")
          }
          TextButton(onClick = { showFenDialog = false }) { Text("Close") }
        }
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameReviewTab(
  session: AnalysisSessionState,
  settings: AppSettingsEntity,
  onStepPly: (Int) -> Unit,
  onRunGameReview: () -> Unit,
  onCancelGameReview: () -> Unit,
) {
  val review = session.reviewSummary

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("game_review_tab"),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Card(modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Game Review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          if (!settings.enableGameAnalysis) {
            Text(
              text = "Game Analysis is currently OFF in Settings. Turn it ON to analyze full games.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else if (review.isRunning) {
            LinearProgressIndicator(progress = { review.progressFraction }, modifier = Modifier.fillMaxWidth())
            Text("Analyzing moves… ${(review.progressFraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = onCancelGameReview) { Text("Cancel Review") }
          } else {
            Button(
              onClick = onRunGameReview,
              enabled = session.historyMoves.isNotEmpty(),
              modifier = Modifier.fillMaxWidth().testTag("run_game_review_btn"),
            ) {
              Icon(Icons.Filled.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (review.moveEntries.isEmpty()) "Run Game Review (${session.historyMoves.size} plies)" else "Re-run Game Review")
            }
          }
        }
      }
    }

    if (settings.enableGameAnalysis && review.moveEntries.isNotEmpty()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          AccuracyCard("White Accuracy", review.whiteAccuracyPercent, Modifier.weight(1f))
          AccuracyCard("Black Accuracy", review.blackAccuracyPercent, Modifier.weight(1f))
        }
      }

      if (settings.showEvalGraph) {
        item {
          EvaluationGraphCard(
            evalSeriesForWhite = review.evalSeriesForWhite,
            selectedPly = session.currentPly,
            criticalPlyIndices = review.criticalPlyIndices,
            enableCriticalMoments = settings.enableCriticalMoments,
            onSelectPly = onStepPly,
            modifier = Modifier.widthIn(max = 580.dp),
          )
        }
      }

      if (settings.enableCriticalMoments && review.criticalPlyIndices.isNotEmpty()) {
        item {
          Card(modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Critical Moments", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                review.criticalPlyIndices.forEach { ply ->
                  val entry = review.moveEntries.getOrNull(ply - 1) ?: return@forEach
                  AssistChip(
                    onClick = { onStepPly(ply) },
                    label = { Text("${(ply + 1) / 2}${if (ply % 2 == 1) "." else "..."}${entry.san} (${entry.classification.symbol})") },
                  )
                }
              }
            }
          }
        }
      }

      if (settings.showMoveFeedback) {
        items(review.moveEntries, key = { it.plyIndex }) { entry ->
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 580.dp)
              .clickable { onStepPly(entry.plyIndex) },
            shape = MaterialTheme.shapes.small,
            color = if (session.currentPly == entry.plyIndex) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Surface(color = Color(entry.classification.badgeColorHex), shape = MaterialTheme.shapes.small) {
                Text(
                  text = entry.classification.symbol,
                  color = Color.White,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${(entry.plyIndex + 1) / 2}${if (entry.plyIndex % 2 == 1) "." else "..."} ${entry.san} • ${entry.classification.label}",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = entry.explanation,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AccuracyCard(title: String, accuracyPercent: Double, modifier: Modifier = Modifier) {
  Card(modifier = modifier) {
    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "%.1f%%".format(accuracyPercent),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun TrainerAndPracticeTab(
  session: AnalysisSessionState,
  settings: AppSettingsEntity,
  onSquareTap: (Int) -> Unit,
  onUndoMove: () -> Unit,
  onLoadDrill: (TrainerDrill) -> Unit,
  onTogglePracticeMode: (Boolean) -> Unit,
  onPlaySuggestedMove: (Move) -> Unit,
) {
  val arrows = remember(session.snapshot, settings) { buildBoardArrows(session.snapshot, settings) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("trainer_practice_tab"),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Chess Trainer & Practice Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          }
          Text(
            text = session.practiceCoachPrompt ?: "Choose a drill below or activate Practice Mode to play moves with instant coach feedback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = session.practiceActive,
              onClick = { onTogglePracticeMode(!session.practiceActive) },
              label = { Text(if (session.practiceActive) "Practice: ON" else "Start Practice Here") },
            )
            OutlinedButton(onClick = onUndoMove, enabled = session.currentPly > 0) {
              Text("Retry / Undo")
            }
          }
        }
      }
    }

    if (AnalysisPolicy.showEvalBar(settings)) {
      item {
        EvaluationBarStrip(
          evalCpForWhite = session.snapshot.evalCpForWhite,
          mateIn = session.snapshot.mateInForWhite,
          isAnalyzing = session.snapshot.isAnalyzing,
          modifier = Modifier.widthIn(max = 580.dp),
        )
      }
    }

    item {
      val lastMove = if (session.currentPly > 0) session.historyMoves[session.currentPly - 1].move else null
      ChessBoardView(
        boardState = session.boardState,
        selectedSquare = session.selectedSquare,
        legalMoves = session.legalMoves,
        lastMoveFrom = lastMove?.from,
        lastMoveTo = lastMove?.to,
        arrows = arrows,
        isFlipped = session.isFlipped,
        boardTheme = BoardThemeOption.fromKey(settings.boardTheme),
        pieceStyle = PieceStyleOption.fromKey(settings.pieceStyle),
        showCoordinates = settings.showCoordinates,
        showLegalMoves = settings.showLegalMoves,
        animateMoves = settings.animateMoves && !settings.reduceAnimations,
        onSquareTap = onSquareTap,
        modifier = Modifier.widthIn(max = 460.dp),
      )
    }

    val fb = session.lastMoveFeedback
    if (settings.showMoveFeedback && fb != null) {
      item {
        MoveFeedbackBanner(
          entry = fb,
          showRecommendedMove = settings.enableEngineAnalysis && (settings.showSuggestionArrow || settings.showEngineLine),
          showCriticalBadge = settings.enableCriticalMoments,
          modifier = Modifier.widthIn(max = 580.dp),
        )
      }
    }

    item {
      EnginePvAndCandidatesCard(
        snapshot = session.snapshot,
        settings = settings,
        onPlayMove = onPlaySuggestedMove,
        modifier = Modifier.widthIn(max = 580.dp),
      )
    }

    item {
      Text(
        text = "Curated Trainer Drills",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp),
      )
    }

    items(MoveFeedbackAnalyzer.trainerDrills, key = { it.id }) { drill ->
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 580.dp)
          .clickable { onLoadDrill(drill) },
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Column(modifier = Modifier.weight(1f)) {
            Text("${drill.title} • ${drill.difficulty}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${drill.category}: ${drill.goal}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          OutlinedButton(onClick = { onLoadDrill(drill) }) {
            Text("Drill")
          }
        }
      }
    }
  }
}
