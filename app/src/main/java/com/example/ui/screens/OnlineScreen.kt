package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.R
import com.example.database.AppSettingsEntity
import com.example.online.MatchType
import com.example.online.OnlineConnectionState
import com.example.online.OnlineMatchRequest

@Composable
fun OnlineScreen(
  settings: AppSettingsEntity,
  connectionState: OnlineConnectionState,
  onSaveServerUrl: (String) -> Unit,
  onConnect: (String, String) -> Unit,
  onDisconnect: () -> Unit,
  onRequestMatch: (OnlineMatchRequest) -> Unit,
) {
  var showServerDialog by remember { mutableStateOf(false) }
  var showLoginDialog by remember { mutableStateOf(false) }
  var roomCodeInput by remember { mutableStateOf("") }
  val isConnected = connectionState is OnlineConnectionState.Connected ||
    connectionState is OnlineConnectionState.SearchingMatch ||
    connectionState is OnlineConnectionState.ActiveOnlineGame

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("online_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Text(
        text = stringResource(R.string.online_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
      )
    }

    if (connectionState is OnlineConnectionState.BackendNotConfigured) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.online_backend_required_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            Text(
              text = stringResource(R.string.online_backend_required_desc),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedButton(onClick = { showServerDialog = true }) {
              Text(stringResource(R.string.online_configure_server))
            }
          }
        }
      }
    } else {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.online_account), style = MaterialTheme.typography.titleMedium)
            }
            val statusText = when (connectionState) {
              is OnlineConnectionState.Connected -> "Connected as ${connectionState.account?.handle ?: "Player"} (${connectionState.serverUrl})"
              is OnlineConnectionState.Connecting -> "Connecting over WSS..."
              is OnlineConnectionState.SearchingMatch -> "Queued: ${connectionState.request.matchType.name}"
              is OnlineConnectionState.ActiveOnlineGame -> "In game vs ${connectionState.opponentName}"
              is OnlineConnectionState.Error -> connectionState.message
              is OnlineConnectionState.Disconnected -> stringResource(R.string.online_status_disconnected)
              is OnlineConnectionState.BackendNotConfigured -> ""
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (!isConnected) {
                Button(onClick = { showLoginDialog = true }) {
                  Text(stringResource(R.string.online_login_token))
                }
              } else {
                OutlinedButton(onClick = onDisconnect) {
                  Text("Disconnect")
                }
              }
              OutlinedButton(onClick = { showServerDialog = true }) {
                Text("Server URL")
              }
            }
          }
        }
      }
    }

    // Matchmaking & Rooms Architecture Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Matchmaking & Modes", style = MaterialTheme.typography.titleMedium)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
              onClick = { onRequestMatch(OnlineMatchRequest(MatchType.QUICK_MATCH, "5+0", true)) },
              enabled = isConnected,
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Filled.FlashOn, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.online_quick_match))
            }
            OutlinedButton(
              onClick = { onRequestMatch(OnlineMatchRequest(MatchType.RATED, "10+0", true)) },
              enabled = isConnected,
              modifier = Modifier.weight(1f),
            ) {
              Text(stringResource(R.string.online_rated_game))
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = { onRequestMatch(OnlineMatchRequest(MatchType.CASUAL, "10+5", false)) },
              enabled = isConnected,
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Filled.Public, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.online_casual_game))
            }
            OutlinedButton(
              onClick = { onRequestMatch(OnlineMatchRequest(MatchType.FRIEND_CHALLENGE, "10+0", false)) },
              enabled = isConnected,
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Filled.GroupAdd, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.online_friend_challenge))
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = roomCodeInput,
              onValueChange = { roomCodeInput = it },
              placeholder = { Text("Room code (optional)") },
              singleLine = true,
              enabled = isConnected,
              modifier = Modifier.weight(1f),
            )
            OutlinedButton(
              onClick = {
                onRequestMatch(
                  OnlineMatchRequest(
                    MatchType.PRIVATE_ROOM,
                    "10+0",
                    false,
                    roomCodeInput.trim().ifEmpty { null },
                  )
                )
              },
              enabled = isConnected,
            ) {
              Icon(Icons.Filled.Lock, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.online_private_room))
            }
          }

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = "Protocol: HTTPS + WSS • Server-side legal move validation • Authoritative clocks • Token auth • Auto-reconnect",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
      }
    }
  }

  if (showServerDialog) {
    var urlInput by remember(settings.onlineServerUrl) { mutableStateOf(settings.onlineServerUrl) }
    AlertDialog(
      onDismissRequest = { showServerDialog = false },
      title = { Text(stringResource(R.string.online_configure_server)) },
      text = {
        OutlinedTextField(
          value = urlInput,
          onValueChange = { urlInput = it },
          placeholder = { Text(stringResource(R.string.online_server_url_hint)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveServerUrl(urlInput)
            showServerDialog = false
          },
        ) {
          Text(stringResource(R.string.confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { showServerDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  if (showLoginDialog) {
    var handle by remember(settings.onlineHandle) { mutableStateOf(settings.onlineHandle) }
    var token by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showLoginDialog = false },
      title = { Text(stringResource(R.string.online_login_token)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = handle,
            onValueChange = { handle = it },
            label = { Text("Handle") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Bearer Session Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onConnect(handle, token)
            showLoginDialog = false
          },
        ) {
          Text(stringResource(R.string.confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { showLoginDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}
