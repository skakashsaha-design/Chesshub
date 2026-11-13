package com.example.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.example.MainActivity
import com.example.R

/**
 * Lightweight background MediaPlayer foreground service.
 * Keeps local music playing smoothly while playing offline chess on 1 GB RAM devices.
 */
class MusicPlaybackService : Service() {

  inner class LocalBinder : Binder() {
    fun getService(): MusicPlaybackService = this@MusicPlaybackService
  }

  private val binder = LocalBinder()
  private var mediaPlayer: MediaPlayer? = null
  private var mediaSession: MediaSession? = null

  var onTrackCompleted: (() -> Unit)? = null
  var onActionFromNotification: ((String) -> Unit)? = null
  var onPlaybackError: ((String) -> Unit)? = null

  private var currentTitle: String = "ChessHub Music"
  private var currentArtist: String = ""
  private var currentVolume: Float = 0.8f
  private var isPrepared: Boolean = false

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    mediaSession = MediaSession(this, "ChessHubMusicSession").apply {
      isActive = true
    }
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_PLAY_PAUSE -> onActionFromNotification?.invoke(ACTION_PLAY_PAUSE)
      ACTION_NEXT -> onActionFromNotification?.invoke(ACTION_NEXT)
      ACTION_PREV -> onActionFromNotification?.invoke(ACTION_PREV)
      ACTION_STOP -> {
        pause()
        stopForegroundCompat()
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  fun playTrack(track: LocalTrack, volume: Float) {
    currentTitle = track.title
    currentArtist = track.artist
    currentVolume = volume.coerceIn(0f, 1f)
    releasePlayer()
    isPrepared = false

    val mp = MediaPlayer().apply {
      setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
      )
      setOnPreparedListener { player ->
        isPrepared = true
        player.setVolume(currentVolume, currentVolume)
        player.start()
        updateSessionState(true)
        postForegroundNotification(true)
      }
      setOnCompletionListener {
        updateSessionState(false)
        onTrackCompleted?.invoke()
      }
      setOnErrorListener { _, _, _ ->
        isPrepared = false
        updateSessionState(false)
        onPlaybackError?.invoke("Cannot play \"${track.title}\"")
        true
      }
    }

    mediaPlayer = mp
    try {
      mp.setDataSource(applicationContext, track.uri)
      mp.prepareAsync()
    } catch (e: Exception) {
      releasePlayer()
      onPlaybackError?.invoke(e.localizedMessage ?: "Unable to open audio file")
    }
  }

  fun togglePlayPause(): Boolean {
    val mp = mediaPlayer ?: return false
    if (!isPrepared) return false
    return try {
      if (mp.isPlaying) {
        mp.pause()
        updateSessionState(false)
        postForegroundNotification(false)
        false
      } else {
        mp.start()
        updateSessionState(true)
        postForegroundNotification(true)
        true
      }
    } catch (_: Throwable) {
      false
    }
  }

  fun pause() {
    val mp = mediaPlayer ?: return
    if (isPrepared && mp.isPlaying) {
      runCatching { mp.pause() }
      updateSessionState(false)
      postForegroundNotification(false)
    }
  }

  fun seekTo(positionMs: Int) {
    val mp = mediaPlayer ?: return
    if (isPrepared) {
      runCatching { mp.seekTo(positionMs.coerceAtLeast(0)) }
    }
  }

  fun setVolume(volume: Float) {
    currentVolume = volume.coerceIn(0f, 1f)
    runCatching {
      if (isPrepared) mediaPlayer?.setVolume(currentVolume, currentVolume)
    }
  }

  fun currentPositionMs(): Int =
    if (isPrepared) runCatching { mediaPlayer?.currentPosition ?: 0 }.getOrDefault(0) else 0

  fun durationMs(): Int =
    if (isPrepared) runCatching { mediaPlayer?.duration ?: 0 }.getOrDefault(0) else 0

  fun isPlaying(): Boolean =
    if (isPrepared) runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false) else false

  private fun updateSessionState(playing: Boolean) {
    val state = PlaybackState.Builder()
      .setActions(
        PlaybackState.ACTION_PLAY or
          PlaybackState.ACTION_PAUSE or
          PlaybackState.ACTION_SKIP_TO_NEXT or
          PlaybackState.ACTION_SKIP_TO_PREVIOUS
      )
      .setState(
        if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
        currentPositionMs().toLong(),
        1f,
      )
      .build()
    mediaSession?.setPlaybackState(state)
  }

  private fun postForegroundNotification(playing: Boolean) {
    val contentIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val prevPi = serviceActionPi(ACTION_PREV, 1)
    val playPausePi = serviceActionPi(ACTION_PLAY_PAUSE, 2)
    val nextPi = serviceActionPi(ACTION_NEXT, 3)

    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }

    val notification = builder
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle(currentTitle)
      .setContentText(currentArtist.ifBlank { getString(R.string.app_name) })
      .setContentIntent(contentIntent)
      .setOngoing(playing)
      .addAction(android.R.drawable.ic_media_previous, "Prev", prevPi)
      .addAction(
        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        if (playing) "Pause" else "Play",
        playPausePi,
      )
      .addAction(android.R.drawable.ic_media_next, "Next", nextPi)
      .setStyle(
        Notification.MediaStyle()
          .setMediaSession(mediaSession?.sessionToken)
          .setShowActionsInCompactView(0, 1, 2)
      )
      .build()

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } catch (_: Throwable) {
      // Fallback safely if notification permission is denied on API 33+
    }
  }

  private fun serviceActionPi(action: String, reqCode: Int): PendingIntent {
    val intent = Intent(this, MusicPlaybackService::class.java).apply { this.action = action }
    return PendingIntent.getService(
      this,
      reqCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      val channel = NotificationChannel(
        CHANNEL_ID,
        "ChessHub Music Playback",
        NotificationManager.IMPORTANCE_LOW,
      )
      nm?.createNotificationChannel(channel)
    }
  }

  private fun stopForegroundCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
  }

  private fun releasePlayer() {
    runCatching { mediaPlayer?.stop() }
    runCatching { mediaPlayer?.release() }
    mediaPlayer = null
    isPrepared = false
  }

  override fun onDestroy() {
    releasePlayer()
    mediaSession?.release()
    mediaSession = null
    super.onDestroy()
  }

  companion object {
    const val CHANNEL_ID = "chesshub_music_channel"
    const val NOTIFICATION_ID = 4012
    const val ACTION_PLAY_PAUSE = "com.example.music.PLAY_PAUSE"
    const val ACTION_NEXT = "com.example.music.NEXT"
    const val ACTION_PREV = "com.example.music.PREV"
    const val ACTION_STOP = "com.example.music.STOP"
  }
}
