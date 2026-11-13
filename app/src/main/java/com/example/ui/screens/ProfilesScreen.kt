package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.PlatformProfileUiState
import com.example.R
import com.example.database.AppSettingsEntity
import com.example.lichess.PublicRecentGame

@Composable
fun ProfilesScreen(
  settings: AppSettingsEntity,
  lichessProfile: PlatformProfileUiState,
  chessComProfile: PlatformProfileUiState,
  onLinkLichess: (String) -> Unit,
  onRefreshLichess: () -> Unit,
  onUnlinkLichess: () -> Unit,
  onLinkChessCom: (String) -> Unit,
  onRefreshChessCom: () -> Unit,
  onUnlinkChessCom: () -> Unit,
  onReplayPublicGame: (PublicRecentGame) -> Unit,
) {
  val context = LocalContext.current
  val totalLocal = settings.localWins + settings.localLosses + settings.localDraws

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

    // 1. MY CHESSHUB
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.profiles_my_chesshub),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatBlock(stringResource(R.string.profiles_local_rating), settings.localRating.toString())
            StatBlock(stringResource(R.string.profiles_games), totalLocal.toString())
            StatBlock(stringResource(R.string.profiles_wins), settings.localWins.toString())
            StatBlock(stringResource(R.string.profiles_losses), settings.localLosses.toString())
            StatBlock(stringResource(R.string.profiles_draws), settings.localDraws.toString())
          }
        }
      }
    }

    // 2. LICHESS
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

    // 3. CHESS.COM
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
