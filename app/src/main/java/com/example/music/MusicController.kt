package com.example.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MusicPlayerUiState(
  val playlist: List<LocalTrack> = emptyList(),
  val currentIndex: Int = -1,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val shuffle: Boolean = false,
  val repeatMode: RepeatMode = RepeatMode.OFF,
  val volume: Float = 0.8f,
  val isScanning: Boolean = false,
  val errorMessage: String? = null,
) {
  val currentTrack: LocalTrack?
    get() = playlist.getOrNull(currentIndex)
}

/**
 * Independent local music controller decoupled from chess game state.
 */
class MusicController(context: Context) {

  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _state = MutableStateFlow(MusicPlayerUiState())
  val state: StateFlow<MusicPlayerUiState> = _state.asStateFlow()

  private var service: MusicPlaybackService? = null
  private var bound = false
  private var tickerJob: Job? = null

  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      val svc = (binder as? MusicPlaybackService.LocalBinder)?.getService() ?: return
      service = svc
      bound = true
      svc.onTrackCompleted = { handleTrackCompleted() }
      svc.onPlaybackError = { msg -> _state.update { it.copy(errorMessage = msg, isPlaying = false) } }
      svc.onActionFromNotification = { action ->
        when (action) {
          MusicPlaybackService.ACTION_PLAY_PAUSE -> togglePlayPause()
          MusicPlaybackService.ACTION_NEXT -> next()
          MusicPlaybackService.ACTION_PREV -> previous()
        }
      }
      startTicker()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      service = null
      bound = false
      tickerJob?.cancel()
    }
  }

  private fun ensureServiceBound(onReady: (MusicPlaybackService) -> Unit) {
    val existing = service
    if (existing != null) {
      onReady(existing)
      return
    }
    val intent = Intent(appContext, MusicPlaybackService::class.java)
    appContext.startService(intent)
    appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    scope.launch {
      var tries = 0
      while (service == null && tries < 20) {
        delay(50)
        tries++
      }
      service?.let(onReady)
    }
  }

  fun applySavedPrefs(volume: Float, shuffle: Boolean, repeatMode: RepeatMode) {
    _state.update {
      it.copy(volume = volume.coerceIn(0f, 1f), shuffle = shuffle, repeatMode = repeatMode)
    }
    service?.setVolume(volume)
  }

  fun scanDeviceAudio() {
    scope.launch {
      _state.update { it.copy(isScanning = true, errorMessage = null) }
      val found = LocalAudioScanner.scanDeviceMediaStore(appContext)
      _state.update { cur ->
        cur.copy(
          playlist = found,
          currentIndex = if (found.isNotEmpty() && cur.currentIndex !in found.indices) 0 else cur.currentIndex,
          isScanning = false,
          errorMessage = if (found.isEmpty()) "No audio files found on device." else null,
        )
      }
    }
  }

  fun addPickedUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    scope.launch {
      val added = LocalAudioScanner.resolvePickedUris(appContext, uris)
      var startPlayIdx = -1
      _state.update { cur ->
        val merged = (cur.playlist + added).distinctBy { it.uriString }
        if (cur.currentIndex == -1 && merged.isNotEmpty()) startPlayIdx = 0
        cur.copy(
          playlist = merged,
          currentIndex = if (cur.currentIndex == -1 && merged.isNotEmpty()) 0 else cur.currentIndex,
          errorMessage = null,
        )
      }
      if (startPlayIdx == 0) {
        playAtIndex(0)
      }
    }
  }

  fun playAtIndex(index: Int) {
    val tracks = _state.value.playlist
    if (index !in tracks.indices) return
    val track = tracks[index]
    _state.update {
      it.copy(
        currentIndex = index,
        isPlaying = true,
        positionMs = 0L,
        durationMs = track.durationMs,
        errorMessage = null,
      )
    }
    ensureServiceBound { svc ->
      svc.playTrack(track, _state.value.volume)
    }
  }

  fun togglePlayPause() {
    val cur = _state.value
    if (cur.playlist.isEmpty()) return
    if (cur.currentIndex !in cur.playlist.indices) {
      playAtIndex(0)
      return
    }
    val svc = service
    if (svc == null) {
      playAtIndex(cur.currentIndex)
      return
    }
    val nowPlaying = svc.togglePlayPause()
    _state.update { it.copy(isPlaying = nowPlaying) }
  }

  fun next() {
    val cur = _state.value
    if (cur.playlist.isEmpty()) return
    val nextIdx = if (cur.shuffle && cur.playlist.size > 1) {
      (cur.playlist.indices.filter { it != cur.currentIndex }).random()
    } else {
      (cur.currentIndex + 1) % cur.playlist.size
    }
    playAtIndex(nextIdx)
  }

  fun previous() {
    val cur = _state.value
    if (cur.playlist.isEmpty()) return
    val prevIdx = if (cur.currentIndex <= 0) cur.playlist.lastIndex else cur.currentIndex - 1
    playAtIndex(prevIdx)
  }

  fun seekTo(positionMs: Long) {
    service?.seekTo(positionMs.toInt())
    _state.update { it.copy(positionMs = positionMs) }
  }

  fun setVolume(vol: Float) {
    val v = vol.coerceIn(0f, 1f)
    service?.setVolume(v)
    _state.update { it.copy(volume = v) }
  }

  fun toggleShuffle() {
    _state.update { it.copy(shuffle = !it.shuffle) }
  }

  fun cycleRepeat(): RepeatMode {
    val next = when (_state.value.repeatMode) {
      RepeatMode.OFF -> RepeatMode.ALL
      RepeatMode.ALL -> RepeatMode.ONE
      RepeatMode.ONE -> RepeatMode.OFF
    }
    _state.update { it.copy(repeatMode = next) }
    return next
  }

  private fun handleTrackCompleted() {
    val cur = _state.value
    when (cur.repeatMode) {
      RepeatMode.ONE -> playAtIndex(cur.currentIndex)
      RepeatMode.ALL -> next()
      RepeatMode.OFF -> {
        if (cur.currentIndex + 1 < cur.playlist.size) {
          playAtIndex(cur.currentIndex + 1)
        } else {
          _state.update { it.copy(isPlaying = false, positionMs = 0L) }
        }
      }
    }
  }

  private fun startTicker() {
    tickerJob?.cancel()
    tickerJob = scope.launch {
      while (isActive) {
        val svc = service
        if (svc != null && svc.isPlaying()) {
          val pos = svc.currentPositionMs().toLong()
          val dur = svc.durationMs().toLong()
          _state.update {
            it.copy(
              isPlaying = true,
              positionMs = pos,
              durationMs = if (dur > 0L) dur else it.durationMs,
            )
          }
        }
        delay(500L)
      }
    }
  }

  fun release() {
    tickerJob?.cancel()
    if (bound) {
      runCatching { appContext.unbindService(connection) }
      bound = false
    }
  }
}
