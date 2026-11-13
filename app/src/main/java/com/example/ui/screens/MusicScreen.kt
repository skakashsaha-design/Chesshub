package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.music.MusicPlayerUiState
import com.example.music.RepeatMode

@Composable
fun MusicScreen(
  musicState: MusicPlayerUiState,
  onScanDeviceAudio: () -> Unit,
  onPickAudioUris: (List<android.net.Uri>) -> Unit,
  onPlayIndex: (Int) -> Unit,
  onTogglePlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onSeek: (Long) -> Unit,
  onVolumeChange: (Float) -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onPermissionDeniedMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
  } else {
    Manifest.permission.READ_EXTERNAL_STORAGE
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      onScanDeviceAudio()
    } else {
      onPermissionDeniedMessage(context.getString(R.string.music_permission_needed))
    }
  }

  val pickFilesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    if (uris.isNotEmpty()) {
      onPickAudioUris(uris)
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("music_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
        Text(stringResource(R.string.music_title), style = MaterialTheme.typography.headlineMedium)
        Text(
          stringResource(R.string.music_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Source actions
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = {
            val hasPerm = ContextCompat.checkSelfPermission(context, audioPermission) ==
              PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
              onScanDeviceAudio()
            } else {
              permissionLauncher.launch(audioPermission)
            }
          },
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.FolderOpen, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.music_scan_device), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = { pickFilesLauncher.launch(arrayOf("audio/*")) },
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.AudioFile, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.music_pick_files), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }

    if (musicState.isScanning) {
      item {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp))
      }
    }

    // Now Playing Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          val track = musicState.currentTrack
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Filled.MusicNote,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = track?.title ?: stringResource(R.string.music_title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = track?.artist ?: stringResource(R.string.music_unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          val maxDur = musicState.durationMs.coerceAtLeast(1L).toFloat()
          Slider(
            value = musicState.positionMs.toFloat().coerceIn(0f, maxDur),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..maxDur,
            enabled = track != null,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(formatDuration(musicState.positionMs), style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(musicState.durationMs), style = MaterialTheme.typography.labelSmall)
          }

          // Playback controls
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(onClick = onToggleShuffle) {
              Icon(
                Icons.Filled.Shuffle,
                contentDescription = stringResource(R.string.music_shuffle),
                tint = if (musicState.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(onClick = onPrevious, enabled = musicState.playlist.isNotEmpty()) {
              Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
            }
            FilledIconButton(
              onClick = onTogglePlayPause,
              enabled = musicState.playlist.isNotEmpty(),
              modifier = Modifier.size(52.dp),
            ) {
              Icon(
                imageVector = if (musicState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play or pause",
              )
            }
            IconButton(onClick = onNext, enabled = musicState.playlist.isNotEmpty()) {
              Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
            IconButton(onClick = onCycleRepeat) {
              Icon(
                imageVector = if (musicState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = stringResource(R.string.music_repeat),
                tint = if (musicState.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          // Volume
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.music_volume))
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
              value = musicState.volume,
              onValueChange = onVolumeChange,
              valueRange = 0f..1f,
              modifier = Modifier.weight(1f),
            )
          }

          if (musicState.errorMessage != null) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = MaterialTheme.shapes.small,
            ) {
              Text(
                text = musicState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(8.dp),
              )
            }
          }
        }
      }
    }

    // Playlist
    if (musicState.playlist.isEmpty()) {
      item {
        Surface(
          modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = MaterialTheme.shapes.medium,
        ) {
          Text(
            text = stringResource(R.string.music_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    } else {
      itemsIndexed(musicState.playlist, key = { _, t -> t.id }) { idx, track ->
        val isCurrent = idx == musicState.currentIndex
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable { onPlayIndex(idx) },
          colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          ),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = track.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            if (track.durationMs > 0L) {
              Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
        }
      }
    }
  }
}

private fun formatDuration(ms: Long): String {
  val totalSec = (ms / 1000L).coerceAtLeast(0L)
  val m = totalSec / 60L
  val s = totalSec % 60L
  return "%d:%02d".format(m, s)
}
