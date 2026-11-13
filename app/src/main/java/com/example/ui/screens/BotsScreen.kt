package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.GameMode
import com.example.R
import com.example.bots.BotCatalog
import com.example.bots.TournamentProgress
import com.example.chess.BotProfile
import com.example.chess.Opponent
import com.example.database.BotStatsEntity
import com.example.engine.StockfishEngineStatus
import com.example.offline.TimeControl
import kotlin.math.roundToInt

@Composable
fun BotsScreen(
  botStats: List<BotStatsEntity>,
  tournamentProgress: TournamentProgress?,
  engineStatus: StockfishEngineStatus = StockfishEngineStatus(),
  onPlayVsBot: (BotProfile, Boolean) -> Unit,
  onWatchBotVsBot: (BotProfile, BotProfile) -> Unit,
  onStartTournament: () -> Unit,
  onCancelTournament: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val statsById = remember(botStats) { botStats.associateBy { it.botId } }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .testTag("bots_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 600.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
      Text(stringResource(R.string.bots_title), style = MaterialTheme.typography.headlineMedium)
      Text(
        stringResource(R.string.bots_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      val infoTail = engineStatus.lastInfoSummary.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
      Text(
        text = "Engine: ${engineStatus.backend.label} • ${engineStatus.lowMemoryHashMb} MB hash$infoTail",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("bots_engine_status"),
      )
    }

    TabRow(
      selectedTabIndex = selectedTab,
      modifier = Modifier.widthIn(max = 600.dp),
    ) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text(stringResource(R.string.bots_tab_roster)) },
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text(stringResource(R.string.bots_tab_training)) },
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        text = { Text(stringResource(R.string.bots_tab_tournament)) },
      )
    }

    when (selectedTab) {
      0 -> BotRosterTab(
        statsById = statsById,
        onChallenge = { bot -> onPlayVsBot(bot, false) },
      )
      1 -> BotTrainingTab(
        onStartTraining = { bot -> onPlayVsBot(bot, true) },
      )
      2 -> BotTournamentTab(
        tournamentProgress = tournamentProgress,
        onWatchBotVsBot = onWatchBotVsBot,
        onStartTournament = onStartTournament,
        onCancelTournament = onCancelTournament,
      )
    }
  }
}

@Composable
private fun BotRosterTab(
  statsById: Map<String, BotStatsEntity>,
  onChallenge: (BotProfile) -> Unit,
) {
  var customDepth by remember { mutableFloatStateOf(2f) }
  var customRandom by remember { mutableFloatStateOf(40f) }
  var customTime by remember { mutableFloatStateOf(500f) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    items(BotCatalog.presets, key = { it.id }) { bot ->
      val st = statsById[bot.id]
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(Color(bot.emblemColorHex).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.SmartToy,
              contentDescription = null,
              tint = Color(bot.emblemColorHex),
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(bot.name, style = MaterialTheme.typography.titleMedium)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "${bot.difficulty.label} • ${bot.rating}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Text(
              text = bot.style,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (st != null && st.gamesPlayed > 0) {
              Text(
                text = "Record: ${st.playerWins}W - ${st.draws}D - ${st.playerLosses}L",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { onChallenge(bot) },
            modifier = Modifier.testTag("challenge_${bot.id}"),
          ) {
            Text(stringResource(R.string.bots_play_now))
          }
        }
      }
    }

    // Custom Difficulty Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Custom Bot", style = MaterialTheme.typography.titleMedium)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text("${stringResource(R.string.bots_custom_depth)}: ${customDepth.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
          Slider(value = customDepth, onValueChange = { customDepth = it }, valueRange = 1f..5f, steps = 3)
          Text("${stringResource(R.string.bots_custom_randomness)}: ${customRandom.roundToInt()} cp", style = MaterialTheme.typography.bodyMedium)
          Slider(value = customRandom, onValueChange = { customRandom = it }, valueRange = 0f..250f)
          Text("${stringResource(R.string.bots_custom_time)}: ${customTime.roundToInt()} ms", style = MaterialTheme.typography.bodyMedium)
          Slider(value = customTime, onValueChange = { customTime = it }, valueRange = 200f..2000f)

          Button(
            onClick = {
              onChallenge(
                BotCatalog.customBot(
                  depth = customDepth.roundToInt(),
                  randomnessCp = customRandom.roundToInt(),
                  thinkTimeMs = customTime.roundToInt().toLong(),
                )
              )
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(R.string.bots_play_now))
          }
        }
      }
    }
  }
}

@Composable
private fun BotTrainingTab(
  onStartTraining: (BotProfile) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Filled.Psychology,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = stringResource(R.string.bots_training_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }
    items(BotCatalog.presets, key = { "train_${it.id}" }) { bot ->
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("${bot.name} (${bot.rating})", style = MaterialTheme.typography.titleMedium)
            Text(bot.difficulty.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          OutlinedButton(onClick = { onStartTraining(bot) }) {
            Text("Train")
          }
        }
      }
    }
  }
}

@Composable
private fun BotTournamentTab(
  tournamentProgress: TournamentProgress?,
  onWatchBotVsBot: (BotProfile, BotProfile) -> Unit,
  onStartTournament: () -> Unit,
  onCancelTournament: () -> Unit,
) {
  var whiteBot by remember { mutableStateOf(BotCatalog.presets[1]) }
  var blackBot by remember { mutableStateOf(BotCatalog.presets[2]) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.play_mode_bvb), style = MaterialTheme.typography.titleMedium)
          Text(
            text = "${whiteBot.name} (${whiteBot.rating}) vs ${blackBot.name} (${blackBot.rating})",
            style = MaterialTheme.typography.bodyMedium,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = {
                val nextIdx = (BotCatalog.presets.indexOf(whiteBot) + 1) % BotCatalog.presets.size
                whiteBot = BotCatalog.presets[nextIdx]
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("White: ${whiteBot.name}")
            }
            OutlinedButton(
              onClick = {
                val nextIdx = (BotCatalog.presets.indexOf(blackBot) + 1) % BotCatalog.presets.size
                blackBot = BotCatalog.presets[nextIdx]
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Black: ${blackBot.name}")
            }
          }
          Button(
            onClick = { onWatchBotVsBot(whiteBot, blackBot) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(R.string.bots_start_bvb))
          }
        }
      }
    }

    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.bots_tab_tournament), style = MaterialTheme.typography.titleMedium)
          }

          if (tournamentProgress?.isRunning == true) {
            Text("Playing: ${tournamentProgress.currentPairing}", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancelTournament, modifier = Modifier.fillMaxWidth()) {
              Text(stringResource(R.string.cancel))
            }
          } else {
            Button(onClick = onStartTournament, modifier = Modifier.fillMaxWidth()) {
              Text(stringResource(R.string.bots_start_tournament))
            }
          }

          if (tournamentProgress != null && tournamentProgress.standings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            tournamentProgress.standings.forEachIndexed { idx, row ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("${idx + 1}. ${row.bot.name}", fontWeight = FontWeight.SemiBold)
                Text("${row.points} pts (${row.wins}W ${row.draws}D ${row.losses}L)", style = MaterialTheme.typography.labelMedium)
              }
            }
            if (tournamentProgress.completedMatches.isNotEmpty()) {
              Spacer(modifier = Modifier.height(6.dp))
              tournamentProgress.completedMatches.forEach { m ->
                Text(
                  text = "${m.whiteName} vs ${m.blackName}: ${m.result.pgnValue} (${m.movesCount}m)",
                  style = MaterialTheme.typography.labelSmall,
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
