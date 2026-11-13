package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.PublicFeedUiState
import com.example.R
import com.example.database.AppSettingsEntity
import com.example.online.MatchType
import com.example.online.OnlineChatMessage
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
  publicFeed: PublicFeedUiState = PublicFeedUiState(),
  onlineChatMessages: List<OnlineChatMessage> = emptyList(),
  onSendChat: (String) -> Unit = {},
  onRefreshTv: () -> Unit = {},
  onWatchPublicGame: (String, String) -> Unit = { _, _ -> },
  onRefreshTournaments: () -> Unit = {},
  onRefreshNews: () -> Unit = {},
  onOpenOfflineTournaments: () -> Unit = {},
  initialTab: Int = 0,
) {
  var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
  var showServerDialog by remember { mutableStateOf(false) }
  var showLoginDialog by remember { mutableStateOf(false) }

  LaunchedEffect(selectedTab) {
    when (selectedTab) {
      1 -> if (publicFeed.tvChannels.isEmpty() && !publicFeed.isLoadingTv) onRefreshTv()
      2 -> if (publicFeed.liveTournaments.isEmpty() && !publicFeed.isLoadingTournaments) onRefreshTournaments()
      3 -> if (publicFeed.newsItems.isEmpty() && !publicFeed.isLoadingNews) onRefreshNews()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .testTag("online_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 600.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
      Text(
        text = stringResource(R.string.online_title),
        style = MaterialTheme.typography.headlineMedium,
      )
      Text(
        text = "Matchmaking, player chat, live games, arenas & chess news",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    TabRow(
      selectedTabIndex = selectedTab,
      modifier = Modifier.widthIn(max = 600.dp),
    ) {
      Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Play & Chat") })
      Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Watch Live") })
      Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Arenas") })
      Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("News") })
    }

    when (selectedTab) {
      0 -> PlayAndChatTab(
        settings = settings,
        connectionState = connectionState,
        chatMessages = onlineChatMessages,
        onOpenServerDialog = { showServerDialog = true },
        onOpenLoginDialog = { showLoginDialog = true },
        onDisconnect = onDisconnect,
        onRequestMatch = onRequestMatch,
        onSendChat = onSendChat,
      )
      1 -> WatchLiveTab(
        publicFeed = publicFeed,
        onRefresh = onRefreshTv,
        onWatchGame = onWatchPublicGame,
      )
      2 -> TournamentsTab(
        publicFeed = publicFeed,
        onRefresh = onRefreshTournaments,
        onOpenOfflineTournaments = onOpenOfflineTournaments,
      )
      3 -> ChessNewsTab(
        publicFeed = publicFeed,
        onRefresh = onRefreshNews,
      )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayAndChatTab(
  settings: AppSettingsEntity,
  connectionState: OnlineConnectionState,
  chatMessages: List<OnlineChatMessage>,
  onOpenServerDialog: () -> Unit,
  onOpenLoginDialog: () -> Unit,
  onDisconnect: () -> Unit,
  onRequestMatch: (OnlineMatchRequest) -> Unit,
  onSendChat: (String) -> Unit,
) {
  var roomCodeInput by remember { mutableStateOf("") }
  var queueTc by remember { mutableStateOf("5+0") }
  var chatDraft by remember { mutableStateOf("") }
  val isConnected = connectionState is OnlineConnectionState.Connected ||
    connectionState is OnlineConnectionState.SearchingMatch ||
    connectionState is OnlineConnectionState.ActiveOnlineGame

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (connectionState is OnlineConnectionState.BackendNotConfigured) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
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
            OutlinedButton(onClick = onOpenServerDialog) {
              Text(stringResource(R.string.online_configure_server))
            }
          }
        }
      }
    } else {
      item {
        Card(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.online_account), style = MaterialTheme.typography.titleMedium)
            }
            val statusText = when (connectionState) {
              is OnlineConnectionState.Connected -> "Connected as ${connectionState.account?.handle ?: "Player"} (${connectionState.serverUrl})"
              is OnlineConnectionState.Connecting -> "Connecting over WSS..."
              is OnlineConnectionState.SearchingMatch -> "Queued: ${connectionState.request.matchType.name} (${connectionState.request.timeControlLabel})"
              is OnlineConnectionState.ActiveOnlineGame -> "In game vs ${connectionState.opponentName}"
              is OnlineConnectionState.Error -> connectionState.message
              is OnlineConnectionState.Disconnected -> stringResource(R.string.online_status_disconnected)
              is OnlineConnectionState.BackendNotConfigured -> ""
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (!isConnected) {
                Button(onClick = onOpenLoginDialog) {
                  Text(stringResource(R.string.online_login_token))
                }
              } else {
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
              }
              OutlinedButton(onClick = onOpenServerDialog) { Text("Server URL") }
            }
          }
        }
      }
    }

    // Matchmaking & Modes Card
    item {
      Card(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Matchmaking & Modes (Bullet • Blitz • Rapid)", style = MaterialTheme.typography.titleMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1+0 Bullet", "3+0 Blitz", "5+0 Blitz", "10+0 Rapid", "15+10 Rapid").forEach { itemLabel ->
              val tcVal = itemLabel.substringBefore(' ')
              FilterChip(
                selected = queueTc == tcVal,
                onClick = { queueTc = tcVal },
                label = { Text(itemLabel) },
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
              onClick = { onRequestMatch(OnlineMatchRequest(MatchType.QUICK_MATCH, queueTc, true)) },
              enabled = isConnected,
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Filled.FlashOn, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.online_quick_match))
            }
            OutlinedButton(
              onClick = { onRequestMatch(OnlineMatchRequest(MatchType.RATED, queueTc, true)) },
              enabled = isConnected,
              modifier = Modifier.weight(1f),
            ) {
              Text(stringResource(R.string.online_rated_game))
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = { onRequestMatch(OnlineMatchRequest(MatchType.CASUAL, queueTc, false)) },
              enabled = isConnected,
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Filled.Public, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.online_casual_game))
            }
            OutlinedButton(
              onClick = { onRequestMatch(OnlineMatchRequest(MatchType.FRIEND_CHALLENGE, queueTc, false)) },
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
                    queueTc,
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
              text = "Protocol: HTTPS + WSS • Server-side legal move validation • Authoritative clocks • Real-time player chat",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
      }
    }

    // Online Player Chat Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .testTag("online_player_chat_card"),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Online Player Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          }
          if (!isConnected) {
            Text(
              text = "Sign in to a configured WSS chess server above to exchange real-time chat messages with online opponents.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else if (chatMessages.isEmpty()) {
            Text(
              text = "Connected. No messages yet — send a greeting!",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            chatMessages.takeLast(12).forEach { msg ->
              Surface(
                color = if (msg.isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "${msg.sender}: ${msg.text}",
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(8.dp),
                )
              }
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = chatDraft,
              onValueChange = { chatDraft = it },
              placeholder = { Text(if (isConnected) "Type message..." else "Connect to chat") },
              enabled = isConnected,
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = {
                if (chatDraft.isNotBlank()) {
                  onSendChat(chatDraft)
                  chatDraft = ""
                }
              },
              enabled = isConnected && chatDraft.isNotBlank(),
            ) {
              Icon(Icons.Filled.Send, contentDescription = "Send chat")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WatchLiveTab(
  publicFeed: PublicFeedUiState,
  onRefresh: () -> Unit,
  onWatchGame: (String, String) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("watch_live_tab"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Watch Live Public Games", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onRefresh, enabled = !publicFeed.isLoadingTv) {
          Icon(Icons.Filled.Refresh, contentDescription = "Refresh live games")
        }
      }
    }

    if (publicFeed.isLoadingTv) {
      item { CircularProgressIndicator() }
    } else if (publicFeed.tvError != null) {
      item {
        ErrorRetryCard(message = publicFeed.tvError, onRetry = onRefresh)
      }
    } else if (publicFeed.tvChannels.isEmpty()) {
      item {
        OutlinedButton(onClick = onRefresh) { Text("Load Live Lichess TV Channels") }
      }
    } else {
      items(publicFeed.tvChannels, key = { it.channelKey }) { ch ->
        Card(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(ch.channelLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
              val playerTitle = ch.featuredTitle?.let { "$it " } ?: ""
              val ratingText = ch.featuredRating?.let { " ($it)" } ?: ""
              Text(
                text = "$playerTitle${ch.featuredPlayer}$ratingText • ${ch.color}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Button(onClick = { onWatchGame(ch.gameId, "${ch.channelLabel}: ${ch.featuredPlayer}") }) {
              Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Watch")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TournamentsTab(
  publicFeed: PublicFeedUiState,
  onRefresh: () -> Unit,
  onOpenOfflineTournaments: () -> Unit,
) {
  val context = LocalContext.current
  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("online_tournaments_tab"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Offline Bot Arenas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Run Bullet, Blitz, Rapid or Classical 4-bot brackets offline", style = MaterialTheme.typography.bodySmall)
          }
          Button(onClick = onOpenOfflineTournaments) { Text("Open") }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
          Text("Live Public Tournaments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onRefresh, enabled = !publicFeed.isLoadingTournaments) {
          Icon(Icons.Filled.Refresh, contentDescription = "Refresh tournaments")
        }
      }
    }

    if (publicFeed.isLoadingTournaments) {
      item { CircularProgressIndicator() }
    } else if (publicFeed.tournamentsError != null) {
      item { ErrorRetryCard(message = publicFeed.tournamentsError, onRetry = onRefresh) }
    } else {
      items(publicFeed.liveTournaments, key = { it.id }) { t ->
        Card(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${t.statusLabel} • ${t.speedLabel} ${t.timeControlLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
              Text(t.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("${t.nbPlayers} players • ${t.minutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
              onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.url))) } },
            ) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Open")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ChessNewsTab(
  publicFeed: PublicFeedUiState,
  onRefresh: () -> Unit,
) {
  val context = LocalContext.current
  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("chess_news_tab"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.Newspaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("World Chess Broadcasts & News", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onRefresh, enabled = !publicFeed.isLoadingNews) {
          Icon(Icons.Filled.Refresh, contentDescription = "Refresh chess news")
        }
      }
    }

    if (publicFeed.isLoadingNews) {
      item { CircularProgressIndicator() }
    } else if (publicFeed.newsError != null) {
      item { ErrorRetryCard(message = publicFeed.newsError, onRetry = onRefresh) }
    } else {
      items(publicFeed.newsItems, key = { it.id }) { item ->
        Card(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item.roundName?.let { r ->
              Text(r, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
              text = item.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(
              onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) } },
            ) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Read Coverage")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ErrorRetryCard(message: String, onRetry: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
      OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
  }
}
