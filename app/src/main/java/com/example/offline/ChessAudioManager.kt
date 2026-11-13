package com.example.offline

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class ChessSoundEffect(
  val fileName: String,
  val defaultVolume: Float,
) {
  MOVE("sfx_move.wav", 0.34f),
  CAPTURE("sfx_capture.wav", 0.44f),
  CHECK("sfx_check.wav", 0.48f),
  GAME_END("sfx_game_end.wav", 0.42f),
  UI_CLICK("sfx_ui_click.wav", 0.18f),
}

/**
 * Lightweight sound manager for short, non-intrusive chess sound effects
 * (piece moves, captures, check alerts, game end, and UI feedback).
 *
 * Synthesizes tiny 16 kHz mono WAV clips (~1-5 KB each) on demand—keeping APK size minimal—
 * and plays them via low-latency [SoundPool] with automatic [MediaPlayer] fallback on older
 * or constrained Android 8.1+ devices (~1 GB RAM).
 */
class ChessAudioManager(context: Context) {

  private val appContext = context.applicationContext
  private val sfxDir = File(appContext.cacheDir, "chess_sfx_v2")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val playerLock = Any()

  private val gameAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_GAME)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

  private var soundPool: SoundPool? = runCatching {
    SoundPool.Builder()
      .setMaxStreams(4)
      .setAudioAttributes(gameAudioAttributes)
      .build()
  }.getOrNull()

  private val soundIds = EnumMap<ChessSoundEffect, Int>(ChessSoundEffect::class.java)
  private val readySoundIds = HashSet<Int>()
  private var activePlayer: MediaPlayer? = null

  @Volatile
  private var isReleased = false

  init {
    soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
      if (status == 0) {
        synchronized(playerLock) {
          readySoundIds.add(sampleId)
        }
      }
    }
    preloadCoreEffects()
  }

  fun preloadCoreEffects() {
    if (isReleased) return
    scope.launch {
      if (isReleased) return@launch
      runCatching {
        val legacyDir = File(appContext.cacheDir, "chess_sfx")
        if (legacyDir.exists()) {
          legacyDir.deleteRecursively()
        }
        listOf(
          ChessSoundEffect.MOVE,
          ChessSoundEffect.CAPTURE,
          ChessSoundEffect.CHECK,
        ).forEach { effect ->
          if (!isReleased) {
            val file = ensureSoundFile(effect)
            ensureLoadedInSoundPool(effect, file)
          }
        }
      }
    }
  }

  fun playMove() = play(ChessSoundEffect.MOVE)

  fun playCapture() = play(ChessSoundEffect.CAPTURE)

  fun playCheck() = play(ChessSoundEffect.CHECK)

  fun playGameEnd() = play(ChessSoundEffect.GAME_END)

  fun playUiClick() = play(ChessSoundEffect.UI_CLICK)

  fun play(effect: ChessSoundEffect, volume: Float = effect.defaultVolume) {
    if (isReleased) return
    val clampedVolume = volume.coerceIn(0.05f, 1.0f)

    // Fast path: zero-allocation SoundPool playback if already decoded in memory
    val readyId = synchronized(playerLock) {
      val id = soundIds[effect]
      if (id != null && id > 0 && id in readySoundIds) id else null
    }
    if (readyId != null) {
      val playedStream = runCatching {
        soundPool?.play(readyId, clampedVolume, clampedVolume, 1, 0, 1.0f) ?: 0
      }.getOrDefault(0)
      if (playedStream != 0) return
    }

    // Async path: synthesize/load into SoundPool and play immediately via MediaPlayer fallback
    scope.launch {
      if (isReleased) return@launch
      try {
        val wavFile = ensureSoundFile(effect)
        ensureLoadedInSoundPool(effect, wavFile)
        startMediaPlayer(wavFile, clampedVolume)
      } catch (_: Throwable) {
        // Ignore audio errors on headless/emulated or restricted devices
      }
    }
  }

  private fun ensureLoadedInSoundPool(effect: ChessSoundEffect, wavFile: File) {
    val pool = soundPool ?: return
    synchronized(playerLock) {
      if (isReleased || soundIds.containsKey(effect)) return
      val id = runCatching { pool.load(wavFile.absolutePath, 1) }.getOrDefault(0)
      if (id > 0) {
        soundIds[effect] = id
      }
    }
  }

  private fun startMediaPlayer(wavFile: File, volume: Float) {
    val player = MediaPlayer()
    try {
      player.setAudioAttributes(gameAudioAttributes)
      player.setDataSource(wavFile.absolutePath)
      player.setVolume(volume, volume)
      player.setOnCompletionListener { completed ->
        releaseSpecificPlayer(completed)
      }
      player.setOnErrorListener { failed, _, _ ->
        releaseSpecificPlayer(failed)
        true
      }
      player.prepare()

      synchronized(playerLock) {
        if (isReleased) {
          runCatching { player.release() }
          return
        }
        activePlayer?.let { prev ->
          runCatching {
            if (prev.isPlaying) prev.stop()
            prev.release()
          }
        }
        activePlayer = player
        player.start()
      }
    } catch (_: Throwable) {
      runCatching { player.release() }
    }
  }

  private fun releaseSpecificPlayer(player: MediaPlayer) {
    synchronized(playerLock) {
      if (activePlayer === player) {
        activePlayer = null
      }
    }
    runCatching { player.release() }
  }

  fun release() {
    isReleased = true
    synchronized(playerLock) {
      activePlayer?.let { player ->
        runCatching {
          if (player.isPlaying) player.stop()
          player.release()
        }
      }
      activePlayer = null
      runCatching { soundPool?.release() }
      soundPool = null
      soundIds.clear()
      readySoundIds.clear()
    }
    scope.cancel()
  }

  internal fun ensureSoundFile(effect: ChessSoundEffect): File {
    if (!sfxDir.exists()) {
      sfxDir.mkdirs()
    }
    val file = File(sfxDir, effect.fileName)
    if (!file.exists() || file.length() <= 44L) {
      val wavBytes = buildWavBytes(effect)
      FileOutputStream(file).use { out ->
        out.write(wavBytes)
      }
    }
    return file
  }

  companion object {
    private const val SAMPLE_RATE = 16_000

    internal fun buildWavBytes(effect: ChessSoundEffect): ByteArray {
      val pcm = synthesizeSamples(effect)
      val dataSize = pcm.size * 2
      val out = ByteArrayOutputStream(44 + dataSize)

      // RIFF header
      out.write("RIFF".toByteArray(Charsets.US_ASCII))
      writeIntLE(out, 36 + dataSize)
      out.write("WAVE".toByteArray(Charsets.US_ASCII))

      // fmt subchunk
      out.write("fmt ".toByteArray(Charsets.US_ASCII))
      writeIntLE(out, 16) // PCM chunk size
      writeShortLE(out, 1) // PCM format
      writeShortLE(out, 1) // Mono
      writeIntLE(out, SAMPLE_RATE)
      writeIntLE(out, SAMPLE_RATE * 2) // Byte rate
      writeShortLE(out, 2) // Block align
      writeShortLE(out, 16) // Bits per sample

      // data subchunk
      out.write("data".toByteArray(Charsets.US_ASCII))
      writeIntLE(out, dataSize)
      for (sample in pcm) {
        writeShortLE(out, sample.toInt())
      }
      return out.toByteArray()
    }

    private fun synthesizeSamples(effect: ChessSoundEffect): ShortArray {
      return when (effect) {
        ChessSoundEffect.MOVE -> {
          // Soft wooden piece placement knock (52 ms)
          val count = (SAMPLE_RATE * 52) / 1000
          ShortArray(count) { i ->
            val t = i.toDouble() / count
            val env = exp(-5.5 * t) * (1.0 - t)
            val freq = 340.0 - (140.0 * t)
            val wave = sin(2.0 * PI * i * freq / SAMPLE_RATE) * 0.75 +
              sin(2.0 * PI * i * (freq * 0.5) / SAMPLE_RATE) * 0.25
            (wave * env * 0.55 * Short.MAX_VALUE).toInt().toShort()
          }
        }

        ChessSoundEffect.CAPTURE -> {
          // Distinct two-stage percussive capture snap + resonant woody thump (78 ms)
          val count = (SAMPLE_RATE * 78) / 1000
          val strikeSamples = (SAMPLE_RATE * 16) / 1000
          ShortArray(count) { i ->
            val t = i.toDouble() / count
            val snap = if (i < strikeSamples) {
              val st = i.toDouble() / strikeSamples
              sin(2.0 * PI * i * 740.0 / SAMPLE_RATE) * exp(-4.0 * st) * 0.45
            } else 0.0
            val thumpEnv = exp(-4.2 * t) * (1.0 - t)
            val thumpFreq = 250.0 - (90.0 * t)
            val thump = (sin(2.0 * PI * i * thumpFreq / SAMPLE_RATE) * 0.65 +
              sin(2.0 * PI * i * (thumpFreq * 2.0) / SAMPLE_RATE) * 0.35) * thumpEnv
            val mixed = (snap + thump).coerceIn(-1.0, 1.0)
            (mixed * 0.64 * Short.MAX_VALUE).toInt().toShort()
          }
        }

        ChessSoundEffect.CHECK -> {
          // Distinct bright two-tone alert chime for check notifications (125 ms)
          val count = (SAMPLE_RATE * 125) / 1000
          val split = count / 2
          ShortArray(count) { i ->
            val localIdx = if (i < split) i else i - split
            val localLen = if (i < split) split else count - split
            val t = localIdx.toDouble() / localLen
            val attack = (localIdx / (SAMPLE_RATE * 0.004)).coerceAtMost(1.0)
            val env = attack * exp(-3.4 * t) * (1.0 - t)
            val freq = if (i < split) 659.25 else 987.77
            val wave = sin(2.0 * PI * localIdx * freq / SAMPLE_RATE) * 0.8 +
              sin(2.0 * PI * localIdx * (freq * 1.5) / SAMPLE_RATE) * 0.2
            (wave * env * 0.56 * Short.MAX_VALUE).toInt().toShort()
          }
        }

        ChessSoundEffect.GAME_END -> {
          // Warm three-step resolution chime (180 ms)
          val count = (SAMPLE_RATE * 180) / 1000
          val step = count / 3
          ShortArray(count) { i ->
            val segment = (i / step).coerceAtMost(2)
            val localIdx = i - (segment * step)
            val t = localIdx.toDouble() / step
            val env = exp(-3.2 * t) * (1.0 - t)
            val freq = when (segment) {
              0 -> 440.0
              1 -> 554.37
              else -> 659.25
            }
            val wave = sin(2.0 * PI * localIdx * freq / SAMPLE_RATE)
            (wave * env * 0.48 * Short.MAX_VALUE).toInt().toShort()
          }
        }

        ChessSoundEffect.UI_CLICK -> {
          // Subtle soft tap (22 ms)
          val count = (SAMPLE_RATE * 22) / 1000
          ShortArray(count) { i ->
            val t = i.toDouble() / count
            val env = exp(-6.0 * t) * (1.0 - t)
            val wave = sin(2.0 * PI * i * 520.0 / SAMPLE_RATE)
            (wave * env * 0.35 * Short.MAX_VALUE).toInt().toShort()
          }
        }
      }
    }

    private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
      out.write(value and 0xFF)
      out.write((value ushr 8) and 0xFF)
      out.write((value ushr 16) and 0xFF)
      out.write((value ushr 24) and 0xFF)
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
      out.write(value and 0xFF)
      out.write((value ushr 8) and 0xFF)
    }
  }
}
