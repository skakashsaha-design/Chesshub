package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.AppSection
import com.example.PlatformProfileUiState
import com.example.R
import com.example.database.AppSettingsEntity
import com.example.database.GameEntity
import com.example.database.GameSummaryRow
import com.example.music.MusicPlayerUiState
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
  settings: AppSettingsEntity,
  unfinishedGame: GameEntity?,
  recentGames: List<GameSummaryRow>,
  lichessProfile: PlatformProfileUiState,
  chessComProfile: PlatformProfileUiState,
  musicState: MusicPlayerUiState,
  onQuickPlay: () -> Unit,
  onStartLocalTwoPlayer: () -> Unit,
  onContinueGame: (GameEntity) -> Unit,
  onReplayGame: (Long) -> Unit,
  onNavigate: (AppSection) -> Unit,
  onToggleMusic: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("home_screen"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text = stringResource(R.string.home_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = { onNavigate(AppSection.SETTINGS) },
            modifier = Modifier.testTag("home_settings_btn"),
          ) {
            Icon(
              imageVector = Icons.Filled.Settings,
              contentDescription = stringResource(R.string.nav_settings),
            )
          }
        }

        if (settings.lowMemoryMode) {
          Spacer(modifier = Modifier.height(6.dp))
          AssistChip(
            onClick = { onNavigate(AppSection.SETTINGS) },
            label = { Text(stringResource(R.string.home_low_memory_badge)) },
            leadingIcon = {
              Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
            },
          )
        }
      }
    }

    // Continue unfinished game card
    if (unfinishedGame != null) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable { onContinueGame(unfinishedGame) }
            .testTag("continue_game_card"),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
          ),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.home_continue_game),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text = "${unfinishedGame.whiteName} vs ${unfinishedGame.blackName} • ${unfinishedGame.movesCount} moves",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
    }

    // Quick Actions Grid
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onQuickPlay,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("quick_play_btn"),
        ) {
          Icon(Icons.Filled.FlashOn, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.home_quick_play),
            style = MaterialTheme.typography.titleMedium,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          HomeActionTile(
            title = stringResource(R.string.home_play_vs_bot),
            icon = Icons.Filled.SmartToy,
            onClick = { onNavigate(AppSection.BOTS) },
            modifier = Modifier.weight(1f).testTag("home_vs_bot_btn"),
          )
          HomeActionTile(
            title = stringResource(R.string.home_local_2_player),
            icon = Icons.Filled.Group,
            onClick = onStartLocalTwoPlayer,
            modifier = Modifier.weight(1f).testTag("home_2p_btn"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          HomeActionTile(
            title = stringResource(R.string.home_online_chess),
            icon = Icons.Filled.Public,
            onClick = { onNavigate(AppSection.ONLINE) },
            modifier = Modifier.weight(1f).testTag("home_online_btn"),
          )
          HomeActionTile(
            title = stringResource(R.string.home_puzzles),
            icon = Icons.Filled.Extension,
            onClick = { onNavigate(AppSection.PUZZLES) },
            modifier = Modifier.weight(1f).testTag("home_puzzles_btn"),
          )
        }
      }
    }

    // Music shortcut
    item {
      OutlinedCard(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .clickable { onNavigate(AppSection.MUSIC) }
          .testTag("home_music_card"),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.home_music_shortcut),
              style = MaterialTheme.typography.labelLarge,
            )
            Text(
              text = musicState.currentTrack?.title ?: stringResource(R.string.music_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (musicState.playlist.isNotEmpty()) {
            IconButton(onClick = onToggleMusic) {
              Icon(
                imageVector = if (musicState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play or pause music",
              )
            }
          }
        }
      }
    }

    // Connected Profiles Shortcuts
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(R.string.home_profiles_shortcuts),
          style = MaterialTheme.typography.titleMedium,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ProfileShortcutCard(
            title = stringResource(R.string.home_lichess_profile),
            subtitle = settings.lichessUsername.ifBlank { stringResource(R.string.home_tap_to_configure) },
            ratings = lichessProfile.ratingsSummary,
            onClick = { onNavigate(AppSection.PROFILES) },
            modifier = Modifier.weight(1f),
          )
          ProfileShortcutCard(
            title = stringResource(R.string.home_chesscom_profile),
            subtitle = settings.chessComUsername.ifBlank { stringResource(R.string.home_tap_to_configure) },
            ratings = chessComProfile.ratingsSummary,
            onClick = { onNavigate(AppSection.PROFILES) },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // Recent Games
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.home_recent_games),
          style = MaterialTheme.typography.titleMedium,
        )
        FilledTonalButton(onClick = { onNavigate(AppSection.PLAY) }) {
          Text(stringResource(R.string.nav_play))
        }
      }
    }

    if (recentGames.isEmpty()) {
      item {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
          Text(
            text = stringResource(R.string.home_no_recent_games),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    } else {
      items(recentGames.take(5), key = { it.id }) { game ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable { onReplayGame(game.id) },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${game.whiteName} vs ${game.blackName}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              val dateText = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(game.dateMillis))
              Text(
                text = "${game.timeControlLabel} • ${game.movesCount}m • $dateText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = game.result,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HomeActionTile(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .height(74.dp)
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ProfileShortcutCard(
  title: String,
  subtitle: String,
  ratings: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedCard(
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.Person,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (ratings.isNotBlank()) {
        Text(
          text = ratings,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
