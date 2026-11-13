package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.bots.BotCatalog
import com.example.bots.BotDialogueEngine
import com.example.bots.TournamentProgress
import com.example.chess.BotPlayingStyle
import com.example.chess.BotProfile
import com.example.chess.RankSystem
import com.example.database.AppSettingsEntity
import com.example.database.BotStatsEntity
import com.example.engine.StockfishEngineStatus
import kotlin.math.roundToInt

@Composable
fun BotsScreen(
  botStats: List<BotStatsEntity>,
  tournamentProgress: TournamentProgress?,
  engineStatus: StockfishEngineStatus = StockfishEngineStatus(),
  settings: AppSettingsEntity = AppSettingsEntity(),
  onPlayVsBot: (BotProfile, Boolean) -> Unit,
  onWatchBotVsBot: (BotProfile, BotProfile) -> Unit,
  onStartTournament: () -> Unit,
  onCancelTournament: () -> Unit,
  onPlayVsBotWithColor: ((BotProfile, Boolean, Boolean?) -> Unit)? = null,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var dialogBot by remember { mutableStateOf<BotProfile?>(null) }
  val statsById = remember(botStats) { botStats.associateBy { it.botId } }
  val myRank = remember(settings.localRating) { RankSystem.rankForElo(settings.localRating) }

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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.bots_title), style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "${stringResource(R.string.bots_subtitle)} (${BotCatalog.MIN_ELO}–${BotCatalog.MAX_ELO} Elo)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = MaterialTheme.shapes.small,
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.End,
          ) {
            Text(
              text = "${myRank.badge} • ${settings.localRating}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text = myRank.title,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
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
        onInspectBot = { bot -> dialogBot = bot },
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

    dialogBot?.let { bot ->
      BotChallengeDialog(
        bot = bot,
        stats = statsById[bot.id],
        onStartMatch = { playWhite, training ->
          dialogBot = null
          if (onPlayVsBotWithColor != null) {
            onPlayVsBotWithColor(bot, training, playWhite)
          } else {
            onPlayVsBot(bot, training)
          }
        },
        onDismiss = { dialogBot = null },
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BotRosterTab(
  statsById: Map<String, BotStatsEntity>,
  onChallenge: (BotProfile) -> Unit,
  onInspectBot: (BotProfile) -> Unit,
) {
  var targetElo by remember { mutableFloatStateOf(1200f) }
  var selectedStyle by remember { mutableStateOf(BotPlayingStyle.BALANCED) }
  val previewCustomBot = remember(targetElo.roundToInt(), selectedStyle) {
    BotCatalog.botForTargetElo(targetElo.roundToInt(), playingStyle = selectedStyle)
  }
  var customDepth by remember(previewCustomBot) { mutableFloatStateOf(previewCustomBot.maxDepth.toFloat()) }
  var customRandom by remember(previewCustomBot) { mutableFloatStateOf(previewCustomBot.randomnessCp.toFloat()) }
  var customTime by remember(previewCustomBot) { mutableFloatStateOf(previewCustomBot.maxThinkTimeMs.toFloat()) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    items(BotCatalog.presets, key = { it.id }) { bot ->
      val st = statsById[bot.id]
      val botRank = remember(bot.rating) { RankSystem.rankForElo(bot.rating) }
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .clickable { onInspectBot(bot) },
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .size(46.dp)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(bot.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
              Surface(
                color = Color(botRank.colorHex).copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.small,
              ) {
                Text(
                  text = "${botRank.badge} • ${bot.rating} Elo",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(botRank.colorHex),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              text = "${bot.difficulty.label} (${botRank.title}) • ${bot.style}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("challenge_${bot.id}"),
          ) {
            Text(stringResource(R.string.bots_play_now))
          }
        }
      }
    }

    // Custom Elo Bot Card (200 - 3200 Max)
    item {
      val customEloInt = targetElo.roundToInt()
      val customRank = RankSystem.rankForElo(customEloInt)
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Custom Elo Bot (200–3200)", style = MaterialTheme.typography.titleMedium)
            }
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = MaterialTheme.shapes.small,
            ) {
              Text(
                text = "${customRank.badge} • $customEloInt",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "${stringResource(R.string.bots_custom_elo)}: $customEloInt Elo (${customRank.title})",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Slider(
            value = targetElo,
            onValueChange = { targetElo = it },
            valueRange = BotCatalog.MIN_ELO.toFloat()..BotCatalog.MAX_ELO.toFloat(),
            steps = 29,
          )
          Text("Playing Style: ${selectedStyle.label}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotPlayingStyle.entries.forEach { st ->
              FilterChip(
                selected = selectedStyle == st,
                onClick = { selectedStyle = st },
                label = { Text(st.label) },
              )
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text("${stringResource(R.string.bots_custom_depth)}: ${customDepth.roundToInt()}", style = MaterialTheme.typography.bodySmall)
          Slider(value = customDepth, onValueChange = { customDepth = it }, valueRange = 1f..6f, steps = 4)
          Text("${stringResource(R.string.bots_custom_randomness)}: ${customRandom.roundToInt()} cp", style = MaterialTheme.typography.bodySmall)
          Slider(value = customRandom, onValueChange = { customRandom = it }, valueRange = 0f..450f)
          Text("${stringResource(R.string.bots_custom_time)}: ${customTime.roundToInt()} ms", style = MaterialTheme.typography.bodySmall)
          Slider(value = customTime, onValueChange = { customTime = it }, valueRange = 150f..2500f)

          Button(
            onClick = {
              val customProfile = BotCatalog.botForTargetElo(
                targetElo = customEloInt,
                thinkTimeOverrideMs = customTime.roundToInt().toLong(),
                playingStyle = selectedStyle,
              ).copy(
                maxDepth = customDepth.roundToInt(),
                randomnessCp = customRandom.roundToInt(),
              )
              onChallenge(customProfile)
            },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("challenge_custom_bot"),
          ) {
            Text("${stringResource(R.string.bots_play_now)} ($customEloInt Elo • ${selectedStyle.label})")
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
      val rank = RankSystem.rankForElo(bot.rating)
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
            Text("${bot.name} (${bot.rating} Elo • ${rank.badge})", style = MaterialTheme.typography.titleMedium)
            Text("${bot.difficulty.label} • ${rank.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
  var whiteBot by remember { mutableStateOf(BotCatalog.presets[3]) }
  var blackBot by remember { mutableStateOf(BotCatalog.presets[4]) }

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
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.play_mode_bvb), style = MaterialTheme.typography.titleMedium)
          }
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
                Text("${idx + 1}. ${row.bot.name} (${row.bot.rating})", fontWeight = FontWeight.SemiBold)
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

@Composable
private fun BotChallengeDialog(
  bot: BotProfile,
  stats: BotStatsEntity?,
  onStartMatch: (Boolean?, Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val rank = remember(bot.rating) { RankSystem.rankForElo(bot.rating) }
  val greeting = remember(bot.id, bot.rating) { BotDialogueEngine.greetingFor(bot) }
  var sideChoice by remember { mutableIntStateOf(0) } // 0=White, 1=Black, 2=Random
  var trainingMode by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Color(bot.emblemColorHex))
        Column {
          Text("${bot.name} • ${bot.rating} Elo")
          Text(
            text = "${rank.title} (${rank.badge}) • ${bot.difficulty.label}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "\"$greeting\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
        Text(
          text = bot.style,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stats != null && stats.gamesPlayed > 0) {
          Text(
            text = "Your Record: ${stats.playerWins}W - ${stats.draws}D - ${stats.playerLosses}L",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Text(stringResource(R.string.play_your_color), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = sideChoice == 0,
            onClick = { sideChoice = 0 },
            label = { Text(stringResource(R.string.play_color_white)) },
          )
          FilterChip(
            selected = sideChoice == 1,
            onClick = { sideChoice = 1 },
            label = { Text(stringResource(R.string.play_color_black)) },
          )
          FilterChip(
            selected = sideChoice == 2,
            onClick = { sideChoice = 2 },
            label = { Text(stringResource(R.string.play_color_random)) },
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = !trainingMode,
            onClick = { trainingMode = false },
            label = { Text("Rated") },
          )
          FilterChip(
            selected = trainingMode,
            onClick = { trainingMode = true },
            label = { Text(stringResource(R.string.bots_tab_training)) },
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val playWhite = when (sideChoice) {
            0 -> true
            1 -> false
            else -> null
          }
          onStartMatch(playWhite, trainingMode)
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
