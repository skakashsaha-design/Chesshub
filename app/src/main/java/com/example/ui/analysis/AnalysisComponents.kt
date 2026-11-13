package com.example.ui.analysis

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.analysis.AnalysisPolicy
import com.example.analysis.GameReviewSummary
import com.example.analysis.MoveAnalysisEntry
import com.example.analysis.MoveClassification
import com.example.analysis.PositionAnalysisSnapshot
import com.example.chess.Move
import com.example.database.AppSettingsEntity
import com.example.ui.board.BoardArrow
import kotlin.math.abs
import kotlin.math.roundToInt

fun buildBoardArrows(
  snapshot: PositionAnalysisSnapshot,
  settings: AppSettingsEntity,
): List<BoardArrow> {
  if (!settings.enableEngineAnalysis) return emptyList()
  val arrows = ArrayList<BoardArrow>(2)
  if (AnalysisPolicy.showThreatArrow(settings)) {
    snapshot.threatInfo?.move?.let { tm ->
      arrows.add(BoardArrow(tm.from, tm.to, Color(0xCCEF4444), strokeRatio = 0.12f))
    }
  }
  if (AnalysisPolicy.showSuggestionArrow(settings)) {
    snapshot.bestMove?.let { bm ->
      arrows.add(BoardArrow(bm.from, bm.to, Color(0xCC10B981), strokeRatio = 0.135f))
    }
  }
  return arrows
}

fun formatEvalScore(evalCpForWhite: Int, mateIn: Int?): String {
  if (mateIn != null) {
    return if (mateIn > 0) "M$mateIn" else "-M${abs(mateIn)}"
  }
  return "%+.1f".format(evalCpForWhite / 100f)
}

@Composable
fun EvaluationBarStrip(
  evalCpForWhite: Int,
  mateIn: Int?,
  isAnalyzing: Boolean,
  modifier: Modifier = Modifier,
) {
  val rawFraction = when {
    mateIn != null && mateIn > 0 -> 0.96f
    mateIn != null && mateIn < 0 -> 0.04f
    else -> ((evalCpForWhite / 900f) + 0.5f).coerceIn(0.05f, 0.95f)
  }
  val whiteFraction by animateFloatAsState(targetValue = rawFraction, label = "EvalBarWhiteRatio")
  val evalLabel = formatEvalScore(evalCpForWhite, mateIn)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .testTag("eval_bar_strip"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier = Modifier
        .weight(1f)
        .height(14.dp)
        .clip(RoundedCornerShape(7.dp))
        .background(Color(0xFF1E293B)),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(whiteFraction)
          .fillMaxHeight()
          .background(Color(0xFFF8FAFC)),
      )
    }
    Surface(
      color = if (evalCpForWhite >= 0) Color(0xFFF1F5F9) else Color(0xFF1E293B),
      shape = RoundedCornerShape(6.dp),
    ) {
      Text(
        text = if (isAnalyzing) "$evalLabel…" else evalLabel,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (evalCpForWhite >= 0) Color(0xFF0F172A) else Color(0xFFF8FAFC),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
      )
    }
  }
}

@Composable
fun MoveFeedbackBanner(
  entry: MoveAnalysisEntry,
  showRecommendedMove: Boolean,
  showCriticalBadge: Boolean,
  modifier: Modifier = Modifier,
) {
  val badgeColor = Color(entry.classification.badgeColorHex)
  val deltaPawns = entry.evalDeltaForMoverCp / 100f
  val deltaText = "%+.2f".format(deltaPawns)

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("move_feedback_banner"),
    colors = CardDefaults.cardColors(
      containerColor = badgeColor.copy(alpha = 0.12f),
    ),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(color = badgeColor, shape = RoundedCornerShape(6.dp)) {
            Text(
              text = "${entry.san} ${entry.classification.symbol} ${entry.classification.label}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
          if (showCriticalBadge && entry.isCriticalMoment) {
            Surface(color = Color(0xFFF59E0B), shape = RoundedCornerShape(6.dp)) {
              Text(
                text = "Critical",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }
        Text(
          text = "Δ $deltaText (${formatEvalScore(entry.evalAfterCpForWhite, entry.mateInAfter)})",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = entry.explanation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (showRecommendedMove && !entry.bestMoveSan.isNullOrBlank()) {
        Text(
          text = "Engine recommended: ${entry.bestMoveSan}",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
fun EvaluationGraphCard(
  evalSeriesForWhite: List<Int>,
  selectedPly: Int,
  criticalPlyIndices: List<Int>,
  enableCriticalMoments: Boolean,
  onSelectPly: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (evalSeriesForWhite.size < 2) return
  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("eval_graph_card"),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Evaluation Graph", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
          text = "Tap graph to jump to move",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      val primaryColor = MaterialTheme.colorScheme.primary
      val criticalSet = criticalPlyIndices.toSet()
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF111827))
          .pointerInput(evalSeriesForWhite.size) {
            detectTapGestures { offset ->
              val ratio = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
              val idx = (ratio * (evalSeriesForWhite.size - 1)).roundToInt()
                .coerceIn(0, evalSeriesForWhite.lastIndex)
              onSelectPly(idx)
            }
          },
      ) {
        val midY = size.height / 2f
        val maxCp = 600f
        drawLine(
          color = Color.White.copy(alpha = 0.28f),
          start = Offset(0f, midY),
          end = Offset(size.width, midY),
          strokeWidth = 1.5f,
        )
        val stepX = size.width / (evalSeriesForWhite.size - 1).coerceAtLeast(1)
        val linePath = Path()
        val fillPath = Path()
        val points = evalSeriesForWhite.mapIndexed { idx, cp ->
          val norm = (cp.coerceIn(-600, 600) / maxCp)
          val x = idx * stepX
          val y = midY - norm * (size.height * 0.44f)
          Offset(x, y)
        }
        points.forEachIndexed { idx, pt ->
          if (idx == 0) {
            linePath.moveTo(pt.x, pt.y)
            fillPath.moveTo(pt.x, midY)
            fillPath.lineTo(pt.x, pt.y)
          } else {
            linePath.lineTo(pt.x, pt.y)
            fillPath.lineTo(pt.x, pt.y)
          }
        }
        fillPath.lineTo(points.last().x, midY)
        fillPath.close()

        drawPath(path = fillPath, color = Color.White.copy(alpha = 0.18f))
        drawPath(path = linePath, color = Color(0xFF38BDF8), style = Stroke(width = 3f))

        if (enableCriticalMoments) {
          for (cPly in criticalSet) {
            if (cPly in points.indices) {
              drawCircle(color = Color(0xFFF59E0B), radius = 5.5f, center = points[cPly])
            }
          }
        }
        val selIdx = selectedPly.coerceIn(0, points.lastIndex)
        drawLine(
          color = primaryColor,
          start = Offset(points[selIdx].x, 0f),
          end = Offset(points[selIdx].x, size.height),
          strokeWidth = 2f,
        )
        drawCircle(color = primaryColor, radius = 6f, center = points[selIdx])
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnginePvAndCandidatesCard(
  snapshot: PositionAnalysisSnapshot,
  settings: AppSettingsEntity,
  onPlayMove: ((Move) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val showPv = AnalysisPolicy.needPv(settings) && snapshot.pvSanList.isNotEmpty()
  val showCand = AnalysisPolicy.needCandidates(settings) && snapshot.candidateMoves.isNotEmpty()
  if (!showPv && !showCand) return

  Card(modifier = modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (showPv) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
          Text(
            text = "Engine Line (d${snapshot.depthReached}): ${snapshot.pvSanList.joinToString(" ")}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (showCand) {
        Text("Candidate Moves", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          snapshot.candidateMoves.forEachIndexed { idx, cand ->
            val scoreLabel = formatEvalScore(cand.evalCpForWhite, cand.mateInForWhite)
            AssistChip(
              onClick = { onPlayMove?.invoke(cand.move) },
              enabled = onPlayMove != null,
              label = {
                Text("${idx + 1}. ${cand.san} ($scoreLabel)")
              },
            )
          }
        }
      }
    }
  }
}

@Composable
fun TacticalAndEndgameCard(
  snapshot: PositionAnalysisSnapshot,
  settings: AppSettingsEntity,
  modifier: Modifier = Modifier,
) {
  val showTactics = settings.showTacticalSuggestions &&
    (snapshot.tacticalSuggestions.isNotEmpty() || snapshot.threatInfo != null)
  val showCritical = settings.enableCriticalMoments && snapshot.isCriticalPosition && !snapshot.criticalReason.isNullOrBlank()
  val showEndgame = snapshot.endgameInfo != null && (snapshot.endgameInfo.isEndgame || showTactics)
  if (!showTactics && !showCritical && !showEndgame) return

  Card(modifier = modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (showCritical) {
        Surface(color = Color(0x33F59E0B), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
            Text(
              text = "Critical Moment: ${snapshot.criticalReason}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      if (showTactics) {
        snapshot.threatInfo?.let { th ->
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Text(th.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
        }
        snapshot.tacticalSuggestions.forEach { tip ->
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(tip, style = MaterialTheme.typography.bodySmall)
          }
        }
      }

      snapshot.endgameInfo?.takeIf { showEndgame }?.let { eg ->
        Text(
          text = if (eg.isEndgame) "${eg.summary} — ${eg.advice}" else "${eg.category}: ${eg.advice}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpeningExplorerCard(
  snapshot: PositionAnalysisSnapshot,
  onPlayBookMove: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val info = snapshot.openingInfo
  val conts = snapshot.openingContinuations
  if (info == null && conts.isEmpty()) return

  Card(modifier = modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
          Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
          Text(
            text = info?.let { "${it.eco} • ${it.name}" } ?: "Opening Explorer",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
        }
        if (info?.isGambit == true) {
          Surface(color = Color(0xFFEF4444).copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
            Text(
              text = "GAMBIT",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFEF4444),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }
      if (info != null && info.planSummary.isNotBlank()) {
        Text(
          text = info.planSummary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (conts.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          conts.take(6).forEach { cont ->
            AssistChip(
              onClick = { onPlayBookMove?.invoke(cont.nextSan) },
              enabled = onPlayBookMove != null,
              label = { Text("${cont.nextSan} (${cont.eco})") },
            )
          }
        }
      } else if (info != null) {
        Text(
          text = info.movesLine,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun PlayerVsOpponentComparisonCard(
  review: GameReviewSummary,
  whiteLabel: String = "White",
  blackLabel: String = "Black",
  modifier: Modifier = Modifier,
) {
  if (review.moveEntries.isEmpty()) return
  val rows = listOf(
    Triple(MoveClassification.BRILLIANT, review.whiteCounts.brilliant, review.blackCounts.brilliant),
    Triple(MoveClassification.BEST, review.whiteCounts.best, review.blackCounts.best),
    Triple(MoveClassification.EXCELLENT, review.whiteCounts.excellent, review.blackCounts.excellent),
    Triple(MoveClassification.GOOD, review.whiteCounts.good, review.blackCounts.good),
    Triple(MoveClassification.BOOK, review.whiteCounts.book, review.blackCounts.book),
    Triple(MoveClassification.INACCURACY, review.whiteCounts.inaccuracy, review.blackCounts.inaccuracy),
    Triple(MoveClassification.MISTAKE, review.whiteCounts.mistake, review.blackCounts.mistake),
    Triple(MoveClassification.MISSED_OPPORTUNITY, review.whiteCounts.missedOpportunity, review.blackCounts.missedOpportunity),
    Triple(MoveClassification.BLUNDER, review.whiteCounts.blunder, review.blackCounts.blunder),
  )

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("player_vs_opponent_comparison_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = "Player vs Opponent Comparison",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(whiteLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          text = blackLabel,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
          textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
      }

      rows.forEach { (cls, wVal, bVal) ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = wVal.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (wVal > 0) Color(cls.badgeColorHex) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
          )
          Surface(
            color = Color(cls.badgeColorHex).copy(alpha = 0.14f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text(
              text = "${cls.symbol} ${cls.label}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = Color(cls.badgeColorHex),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
          }
          Text(
            text = bVal.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (bVal > 0) Color(cls.badgeColorHex) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))
      ComparisonMetricRow("Avg CP Loss (ACPL)", "${review.whiteCounts.averageCpLoss}", "${review.blackCounts.averageCpLoss}")
      ComparisonMetricRow(
        "Opening Accuracy",
        review.whiteOpeningAcc?.let { "%.1f%%".format(it) } ?: "—",
        review.blackOpeningAcc?.let { "%.1f%%".format(it) } ?: "—",
      )
      ComparisonMetricRow(
        "Middlegame Accuracy",
        review.whiteMiddlegameAcc?.let { "%.1f%%".format(it) } ?: "—",
        review.blackMiddlegameAcc?.let { "%.1f%%".format(it) } ?: "—",
      )
      ComparisonMetricRow(
        "Endgame Accuracy",
        review.whiteEndgameAcc?.let { "%.1f%%".format(it) } ?: "—",
        review.blackEndgameAcc?.let { "%.1f%%".format(it) } ?: "—",
      )
    }
  }
}

@Composable
private fun ComparisonMetricRow(label: String, whiteVal: String, blackVal: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(whiteVal, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(blackVal, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
  }
}
