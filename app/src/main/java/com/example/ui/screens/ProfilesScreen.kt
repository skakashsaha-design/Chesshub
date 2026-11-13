package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.PlatformProfileUiState
import com.example.PublicFeedUiState
import com.example.R
import com.example.badges.EvaluatedBadge
import com.example.bots.BotCatalog
import com.example.chess.RankSystem
import com.example.database.AppSettingsEntity
import com.example.lichess.PublicRecentGame
import kotlin.math.roundToInt

private data class LadderParticipant(
  val name: String,
  val elo: Int,
  val source: String,
  val isSelf: Boolean = false,
)

@Composable
fun ProfilesScreen(
  settings: AppSettingsEntity,
  lichessProfile: PlatformProfileUiState,
  chessComProfile: PlatformProfileUiState,
  evaluatedBadges: List<EvaluatedBadge> = emptyList(),
  publicFeed: PublicFeedUiState = PublicFeedUiState(),
  onUpdatePlayerName: (String) -> Unit = {},
  onLinkLichess: (String) -> Unit,
  onRefreshLichess: () -> Unit,
  onUnlinkLichess: () -> Unit,
  onLinkChessCom: (String) -> Unit,
  onRefreshChessCom: () -> Unit,
  onUnlinkChessCom: () -> Unit,
  onReplayPublicGame: (PublicRecentGame) -> Unit,
  onScoutPlayer: (String, String) -> Unit = { _, _ -> },
) {
  val context = LocalContext.current
  val totalLocal = settings.localWins + settings.localLosses + settings.localDraws
  val winRatePct = if (totalLocal > 0) ((settings.localWins * 100f) / totalLocal).roundToInt() else 0
  val myRank = remember(settings.localRating) { RankSystem.rankForElo(settings.localRating) }
  val rankProgress = remember(settings.localRating) { RankSystem.progressToNextRank(settings.localRating) }

  var isEditingName by remember { mutableStateOf(false) }
  var draftName by remember(settings.playerName) { mutableStateOf(settings.playerName) }

  val ladderRows = remember(
    settings.playerName,
    settings.localRating,
    settings.lichessUsername,
    lichessProfile.ratingsSummary,
    settings.chessComUsername,
    chessComProfile.ratingsSummary,
  ) {
    buildList {
      add(
        LadderParticipant(
          name = settings.playerName.ifBlank { "ChessHub Player" },
          elo = settings.localRating,
          source = "You (Local)",
          isSelf = true,
        )
      )
      BotCatalog.presets.forEach { bot ->
        add(
          LadderParticipant(
            name = bot.name,
            elo = bot.rating,
            source = "Bot • ${bot.difficulty.label}",
          )
        )
      }
    }.sortedByDescending { it.elo }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("profiles_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Text(
        text = stringResource(R.string.profiles_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
      )
    }

    // 1. MY CHESSHUB PROFILE (Name, My Elo, Rank, Win Rate, Stats)
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.profiles_my_chesshub),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = settings.playerName.ifBlank { "ChessHub Player" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Surface(
                color = Color(myRank.colorHex).copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small,
              ) {
                Text(
                  text = "${myRank.badge} • ${myRank.title}",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color(myRank.colorHex),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
              IconButton(
                onClick = { isEditingName = !isEditingName },
                modifier = Modifier.testTag("edit_player_name_btn"),
              ) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profiles_player_name))
              }
            }
          }

          if (isEditingName) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                label = { Text(stringResource(R.string.profiles_player_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("player_name_input"),
              )
              Button(
                onClick = {
                  onUpdatePlayerName(draftName)
                  isEditingName = false
                },
                enabled = draftName.isNotBlank(),
                modifier = Modifier.testTag("save_player_name_btn"),
              ) {
                Text(stringResource(R.string.profiles_save_name))
              }
            }
          }

          // Progress bar to next rank
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(
                text = "${stringResource(R.string.profiles_local_rating)}: ${settings.localRating}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = myRank.nextThreshold?.let { "Next rank: $it Elo" } ?: "Top Rank Achieved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            LinearProgressIndicator(
              progress = { rankProgress },
              modifier = Modifier.fillMaxWidth().height(6.dp),
            )
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatBlock(stringResource(R.string.profiles_local_rating), settings.localRating.toString())
            StatBlock(stringResource(R.string.profiles_win_rate), "$winRatePct%")
            StatBlock(stringResource(R.string.profiles_games), totalLocal.toString())
            StatBlock(stringResource(R.string.profiles_wins), settings.localWins.toString())
            StatBlock(stringResource(R.string.profiles_losses), settings.localLosses.toString())
            StatBlock(stringResource(R.string.profiles_draws), settings.localDraws.toString())
          }
        }
      }
    }

    // 2. FAIR PLAY & RATING INTEGRITY
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text(
                text = stringResource(R.string.profiles_fair_play_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = MaterialTheme.shapes.small,
            ) {
              Text(
                text = stringResource(R.string.profiles_fair_play_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
          Text(
            text = stringResource(R.string.profiles_fair_play_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // 3. ELO & RANK LADDER (My Elo vs Other Bots & Ranks)
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(
              text = stringResource(R.string.profiles_elo_ladder),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          ladderRows.forEachIndexed { idx, row ->
            val rank = RankSystem.rankForElo(row.elo)
            Surface(
              color = if (row.isSelf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
              else Color.Transparent,
              shape = MaterialTheme.shapes.small,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text(
                    text = "#${idx + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Column {
                    Text(
                      text = row.name,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (row.isSelf) FontWeight.Bold else FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = row.source,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Surface(
                    color = Color(rank.colorHex).copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.small,
                  ) {
                    Text(
                      text = rank.badge,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = Color(rank.colorHex),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                  Text(
                    text = "${row.elo}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
          }
        }
      }
    }

    // 4. GAME BADGES SHOWCASE
    if (evaluatedBadges.isNotEmpty()) {
      item {
        GameBadgesShowcaseCard(badges = evaluatedBadges)
      }
    }

    // 5. SCOUT OTHER PLAYER STATS (Lichess & Chess.com)
    item {
      OtherPlayerScoutCard(
        initialPlatform = publicFeed.scoutPlatform,
        scoutProfile = publicFeed.scoutProfile,
        onScout = onScoutPlayer,
        onOpenUrl = { url ->
          runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        },
        onReplayGame = onReplayPublicGame,
      )
    }

    // 6. LICHESS
    item {
      PlatformProfileCard(
        platformTitle = "LICHESS",
        savedUsername = settings.lichessUsername,
        profileState = lichessProfile,
        footerNote = stringResource(R.string.profiles_lichess_auth_note),
        onLink = onLinkLichess,
        onRefresh = onRefreshLichess,
        onUnlink = onUnlinkLichess,
        onOpenUrl = { url ->
          runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        },
        onReplayGame = onReplayPublicGame,
      )
    }

    // 7. CHESS.COM
    item {
      PlatformProfileCard(
        platformTitle = "CHESS.COM",
        savedUsername = settings.chessComUsername,
        profileState = chessComProfile,
        footerNote = stringResource(R.string.profiles_chesscom_note),
        onLink = onLinkChessCom,
        onRefresh = onRefreshChessCom,
        onUnlink = onUnlinkChessCom,
        onOpenUrl = { url ->
          runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        },
        onReplayGame = onReplayPublicGame,
      )
    }
  }
}

@Composable
private fun StatBlock(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun PlatformProfileCard(
  platformTitle: String,
  savedUsername: String,
  profileState: PlatformProfileUiState,
  footerNote: String,
  onLink: (String) -> Unit,
  onRefresh: () -> Unit,
  onUnlink: () -> Unit,
  onOpenUrl: (String) -> Unit,
  onReplayGame: (PublicRecentGame) -> Unit,
) {
  var input by remember(savedUsername) { mutableStateOf(savedUsername) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 600.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(platformTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (savedUsername.isNotBlank()) {
          Row {
            IconButton(onClick = onRefresh, enabled = !profileState.isLoading) {
              Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.profiles_refresh))
            }
            IconButton(onClick = onUnlink) {
              Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.profiles_unlink))
            }
          }
        }
      }

      if (savedUsername.isBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(stringResource(R.string.profiles_username_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          Button(onClick = { onLink(input) }, enabled = input.isNotBlank()) {
            Text(stringResource(R.string.profiles_link))
          }
        }
      } else {
        if (profileState.isLoading) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(savedUsername, style = MaterialTheme.typography.bodyMedium)
          }
        } else {
          val displayUser = buildString {
            profileState.title?.let { append("$it ") }
            append(profileState.username.ifBlank { savedUsername })
          }
          Text(displayUser, style = MaterialTheme.typography.titleMedium)
          if (profileState.ratingsSummary.isNotBlank()) {
            Text(
              profileState.ratingsSummary,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          if (profileState.totalGames > 0) {
            Text(
              text = "${profileState.totalGames} ${stringResource(R.string.profiles_games)} • " +
                "${profileState.wins}W / ${profileState.losses}L / ${profileState.draws}D",
              style = MaterialTheme.typography.labelMedium,
            )
          }
          if (profileState.isCachedOffline) {
            Text(
              text = stringResource(R.string.profiles_cached_offline),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
          if (profileState.profileUrl.isNotBlank()) {
            OutlinedButton(onClick = { onOpenUrl(profileState.profileUrl) }) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.profiles_open_official))
            }
          }
        }

        if (profileState.errorMessage != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = profileState.errorMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(8.dp),
            )
          }
        }

        if (profileState.recentGames.isNotEmpty()) {
          HorizontalDivider()
          Text(stringResource(R.string.profiles_recent_public_games), style = MaterialTheme.typography.labelLarge)
          profileState.recentGames.take(5).forEach { g ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${g.white} vs ${g.black}",
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${g.result} • ${g.speed}" + (g.opening?.let { " • $it" } ?: ""),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (g.pgn.isNotBlank()) {
                IconButton(onClick = { onReplayGame(g) }) {
                  Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.profiles_import_to_board))
                }
              }
            }
          }
        }
      }

      Text(
        text = footerNote,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameBadgesShowcaseCard(badges: List<EvaluatedBadge>) {
  val unlockedCount = badges.count { it.unlocked }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 600.dp)
      .testTag("game_badges_card"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Game Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            text = "$unlockedCount / ${badges.size} Unlocked",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }
      LinearProgressIndicator(
        progress = { if (badges.isNotEmpty()) unlockedCount.toFloat() / badges.size else 0f },
        modifier = Modifier.fillMaxWidth().height(6.dp),
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        badges.forEach { entry ->
          val accent = Color(entry.badge.tierColorHex)
          OutlinedCard(
            modifier = Modifier.widthIn(min = 148.dp, max = 270.dp),
            border = BorderStroke(
              width = 1.dp,
              color = if (entry.unlocked) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant,
            ),
            colors = CardDefaults.outlinedCardColors(
              containerColor = if (entry.unlocked) accent.copy(alpha = 0.1f)
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = if (entry.unlocked) Icons.Filled.MilitaryTech else Icons.Filled.Lock,
                  contentDescription = null,
                  tint = if (entry.unlocked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = entry.badge.title,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = if (entry.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text(
                text = entry.badge.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OtherPlayerScoutCard(
  initialPlatform: String,
  scoutProfile: PlatformProfileUiState,
  onScout: (String, String) -> Unit,
  onOpenUrl: (String) -> Unit,
  onReplayGame: (PublicRecentGame) -> Unit,
) {
  var selectedPlatform by remember(initialPlatform) {
    mutableStateOf(if (initialPlatform == "CHESSCOM") "CHESSCOM" else "LICHESS")
  }
  var usernameInput by remember { mutableStateOf("") }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 600.dp)
      .testTag("other_player_scout_card"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Column {
          Text("Player Stats Lookup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            "Inspect another player's ratings, W/D/L stats, and public games without linking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = selectedPlatform == "LICHESS",
          onClick = { selectedPlatform = "LICHESS" },
          label = { Text("Lichess") },
        )
        FilterChip(
          selected = selectedPlatform == "CHESSCOM",
          onClick = { selectedPlatform = "CHESSCOM" },
          label = { Text("Chess.com") },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = usernameInput,
          onValueChange = { usernameInput = it },
          placeholder = { Text("Enter username (e.g. DrNykterstein)") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("scout_username_input"),
        )
        Button(
          onClick = { onScout(selectedPlatform, usernameInput) },
          enabled = usernameInput.isNotBlank() && !scoutProfile.isLoading,
          modifier = Modifier.testTag("scout_search_btn"),
        ) {
          Text("Scout")
        }
      }

      if (scoutProfile.isLoading) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Text("Fetching ${scoutProfile.username}...", style = MaterialTheme.typography.bodyMedium)
        }
      } else if (scoutProfile.errorMessage != null) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            text = scoutProfile.errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
      } else if (scoutProfile.username.isNotBlank()) {
        HorizontalDivider()
        val displayTitle = buildString {
          scoutProfile.title?.let { append("$it ") }
          append(scoutProfile.username)
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (scoutProfile.ratingsSummary.isNotBlank()) {
              Text(
                text = scoutProfile.ratingsSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            if (scoutProfile.totalGames > 0) {
              Text(
                text = "${scoutProfile.totalGames} games • ${scoutProfile.wins}W / ${scoutProfile.losses}L / ${scoutProfile.draws}D",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (scoutProfile.profileUrl.isNotBlank()) {
            OutlinedButton(onClick = { onOpenUrl(scoutProfile.profileUrl) }) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Profile")
            }
          }
        }

        if (scoutProfile.recentGames.isNotEmpty()) {
          Text("Recent Games", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
          scoutProfile.recentGames.take(4).forEach { g ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${g.white} vs ${g.black}",
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${g.result} • ${g.speed}" + (g.opening?.let { " • $it" } ?: ""),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (g.pgn.isNotBlank()) {
                IconButton(onClick = { onReplayGame(g) }) {
                  Icon(Icons.Filled.PlayArrow, contentDescription = "Replay game on board")
                }
              }
            }
          }
        }
      }
    }
  }
}
