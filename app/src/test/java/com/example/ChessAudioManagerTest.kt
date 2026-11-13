package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.database.AppSettingsEntity
import com.example.offline.ChessAudioManager
import com.example.offline.ChessSoundEffect
import com.example.offline.SoundAndHaptics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChessAudioManagerTest {

  @Test
  fun `wav synthesis generates distinct compact RIFF WAVE payloads for move capture and check`() {
    val moveWav = ChessAudioManager.buildWavBytes(ChessSoundEffect.MOVE)
    val captureWav = ChessAudioManager.buildWavBytes(ChessSoundEffect.CAPTURE)
    val checkWav = ChessAudioManager.buildWavBytes(ChessSoundEffect.CHECK)

    assertEquals("RIFF", String(moveWav.copyOfRange(0, 4), Charsets.US_ASCII))
    assertEquals("WAVE", String(moveWav.copyOfRange(8, 12), Charsets.US_ASCII))
    assertEquals("fmt ", String(moveWav.copyOfRange(12, 16), Charsets.US_ASCII))
    assertEquals("data", String(moveWav.copyOfRange(36, 40), Charsets.US_ASCII))

    assertTrue("Move WAV should be compact (< 4 KB)", moveWav.size in 1000..4000)
    assertTrue("Capture WAV should be compact (< 4 KB)", captureWav.size in 1500..4000)
    assertTrue("Check WAV should be compact (< 6 KB)", checkWav.size in 2000..6000)

    assertFalse("Move and Capture audio clips must be distinct", moveWav.contentEquals(captureWav))
    assertFalse("Capture and Check audio clips must be distinct", captureWav.contentEquals(checkWav))
    assertFalse("Move and Check audio clips must be distinct", moveWav.contentEquals(checkWav))
  }

  @Test
  fun `audio manager caches wav files and plays move capture and check safely`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val audioManager = ChessAudioManager(context)

    val moveFile = audioManager.ensureSoundFile(ChessSoundEffect.MOVE)
    val captureFile = audioManager.ensureSoundFile(ChessSoundEffect.CAPTURE)
    val checkFile = audioManager.ensureSoundFile(ChessSoundEffect.CHECK)
    assertTrue(moveFile.exists() && moveFile.length() > 44L)
    assertTrue(captureFile.exists() && captureFile.length() > 44L)
    assertTrue(checkFile.exists() && checkFile.length() > 44L)

    audioManager.playMove()
    audioManager.playCapture()
    audioManager.playCheck()
    audioManager.release()
  }

  @Test
  fun `sound and haptics plays and releases without error when enabled or muted`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val soundAndHaptics = SoundAndHaptics(context)

    soundAndHaptics.playMoveSound(AppSettingsEntity(soundMove = false))
    soundAndHaptics.playCaptureSound(AppSettingsEntity(soundCapture = false))
    soundAndHaptics.playCheckSound(AppSettingsEntity(soundCheck = false))
    soundAndHaptics.playMoveSound(AppSettingsEntity(soundMove = true))
    soundAndHaptics.playCaptureSound(AppSettingsEntity(soundCapture = true))
    soundAndHaptics.playCheckSound(AppSettingsEntity(soundCheck = true))
    soundAndHaptics.release()
  }
}
