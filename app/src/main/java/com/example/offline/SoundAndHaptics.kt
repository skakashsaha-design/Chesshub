package com.example.offline

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.database.AppSettingsEntity
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ultra-lightweight synthesized sound effects & Android 8.1+ safe haptics.
 * Avoids bundling large audio asset files to keep APK size and RAM minimal.
 */
class SoundAndHaptics(context: Context) {

  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun getVibrator(): Vibrator? {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
    } catch (_: Throwable) {
      null
    }
  }

  fun hapticTap(settings: AppSettingsEntity, strong: Boolean = false) {
    if (!settings.hapticFeedback || settings.batterySavingMode) return
    val vibrator = getVibrator() ?: return
    try {
      if (!vibrator.hasVibrator()) return
      val ms = if (strong) 28L else 14L
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val amp = if (strong) 180 else 95
        vibrator.vibrate(VibrationEffect.createOneShot(ms, amp))
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(ms)
      }
    } catch (_: Throwable) {
      // Gracefully ignore on devices without vibrator permission/hardware
    }
  }

  fun playMoveSound(settings: AppSettingsEntity) {
    if (!settings.soundMove) return
    playTone(freqHz = 360.0, durationMs = 45, gain = 0.25)
  }

  fun playCaptureSound(settings: AppSettingsEntity) {
    if (!settings.soundCapture) return
    playTone(freqHz = 230.0, durationMs = 65, gain = 0.35)
  }

  fun playCheckSound(settings: AppSettingsEntity) {
    if (!settings.soundCheck) return
    playTone(freqHz = 620.0, durationMs = 95, gain = 0.32)
  }

  fun playGameEndSound(settings: AppSettingsEntity) {
    if (!settings.soundGameEnd) return
    playDualTone(440.0, 587.3, 110)
  }

  fun playUiClick(settings: AppSettingsEntity) {
    if (!settings.soundUi) return
    playTone(freqHz = 520.0, durationMs = 24, gain = 0.14)
  }

  private fun playTone(freqHz: Double, durationMs: Int, gain: Double) {
    scope.launch {
      try {
        val sampleRate = 16000
        val count = (sampleRate * durationMs) / 1000
        val samples = ShortArray(count)
        for (i in 0 until count) {
          val env = 1.0 - (i.toDouble() / count)
          val angle = 2.0 * PI * i * freqHz / sampleRate
          samples[i] = (sin(angle) * env * gain * Short.MAX_VALUE).toInt().toShort()
        }
        writePcm(sampleRate, samples)
      } catch (_: Throwable) {
        // Ignore audio errors on restricted devices
      }
    }
  }

  private fun playDualTone(freq1: Double, freq2: Double, eachMs: Int) {
    scope.launch {
      try {
        val sampleRate = 16000
        val each = (sampleRate * eachMs) / 1000
        val samples = ShortArray(each * 2)
        for (i in 0 until each) {
          val env = 1.0 - (i.toDouble() / each)
          samples[i] = (sin(2.0 * PI * i * freq1 / sampleRate) * env * 0.28 * Short.MAX_VALUE).toInt().toShort()
          samples[each + i] = (sin(2.0 * PI * i * freq2 / sampleRate) * env * 0.28 * Short.MAX_VALUE).toInt().toShort()
        }
        writePcm(sampleRate, samples)
      } catch (_: Throwable) {
        // Ignore
      }
    }
  }

  private fun writePcm(sampleRate: Int, samples: ShortArray) {
    val minBuf = AudioTrack.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_OUT_MONO,
      AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(samples.size * 2)

    val track = AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_GAME)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(sampleRate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build()
      )
      .setTransferMode(AudioTrack.MODE_STATIC)
      .setBufferSizeInBytes(samples.size * 2.coerceAtLeast(minBuf))
      .build()

    try {
      track.write(samples, 0, samples.size)
      track.play()
      Thread.sleep((samples.size * 1000L / sampleRate) + 30L)
    } finally {
      try {
        track.stop()
      } catch (_: Throwable) {}
      track.release()
    }
  }
}
