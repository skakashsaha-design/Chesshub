package com.example.offline

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.database.AppSettingsEntity

/**
 * Coordinates ChessAudioManager (MediaPlayer SFX) and Android 8.1+ safe haptics.
 */
class SoundAndHaptics(context: Context) {

  private val appContext = context.applicationContext
  private val audioManager = ChessAudioManager(appContext)

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
    audioManager.playMove()
  }

  fun playCaptureSound(settings: AppSettingsEntity) {
    if (!settings.soundCapture) return
    audioManager.playCapture()
  }

  fun playCheckSound(settings: AppSettingsEntity) {
    if (!settings.soundCheck) return
    audioManager.playCheck()
  }

  fun playGameEndSound(settings: AppSettingsEntity) {
    if (!settings.soundGameEnd) return
    audioManager.playGameEnd()
  }

  fun playUiClick(settings: AppSettingsEntity) {
    if (!settings.soundUi) return
    audioManager.playUiClick()
  }

  fun release() {
    audioManager.release()
  }
}
