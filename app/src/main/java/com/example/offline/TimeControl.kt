package com.example.offline

import android.os.SystemClock
import com.example.chess.PieceColor

data class TimeControl(
  val id: String,
  val label: String,
  val initialMinutes: Int,
  val incrementSeconds: Int,
  val isUnlimited: Boolean = false,
) {
  val initialMillis: Long get() = initialMinutes * 60_000L
  val incrementMillis: Long get() = incrementSeconds * 1_000L

  companion object {
    val presets: List<TimeControl> = listOf(
      TimeControl("1_0", "1+0", 1, 0),
      TimeControl("2_1", "2+1", 2, 1),
      TimeControl("3_0", "3+0", 3, 0),
      TimeControl("3_2", "3+2", 3, 2),
      TimeControl("5_0", "5+0", 5, 0),
      TimeControl("5_3", "5+3", 5, 3),
      TimeControl("10_0", "10+0", 10, 0),
      TimeControl("10_5", "10+5", 10, 5),
      TimeControl("15_10", "15+10", 15, 10),
      TimeControl("30_0", "30+0", 30, 0),
      TimeControl("unlimited", "Unlimited", 0, 0, isUnlimited = true),
    )

    fun custom(minutes: Int, incrementSeconds: Int): TimeControl {
      val m = minutes.coerceIn(1, 180)
      val inc = incrementSeconds.coerceIn(0, 60)
      return TimeControl("custom_${m}_${inc}", "$m+$inc", m, inc)
    }
  }
}

/**
 * Timestamp-anchored dual chess clock using SystemClock.elapsedRealtime().
 * Remains completely accurate even when the app goes into the background.
 */
class ChessClockTracker(
  private var timeControl: TimeControl,
) {
  private var whiteRemainingMs: Long = timeControl.initialMillis
  private var blackRemainingMs: Long = timeControl.initialMillis
  private var activeSide: PieceColor? = null
  private var lastTickRealtimeMs: Long = 0L

  fun reset(tc: TimeControl) {
    timeControl = tc
    whiteRemainingMs = tc.initialMillis
    blackRemainingMs = tc.initialMillis
    activeSide = null
    lastTickRealtimeMs = 0L
  }

  fun start(side: PieceColor) {
    if (timeControl.isUnlimited) return
    val now = SystemClock.elapsedRealtime()
    syncElapsed(now)
    activeSide = side
    lastTickRealtimeMs = now
  }

  fun onMoveCompleted(mover: PieceColor) {
    if (timeControl.isUnlimited) return
    val now = SystemClock.elapsedRealtime()
    syncElapsed(now)
    if (mover == PieceColor.WHITE && whiteRemainingMs > 0L) {
      whiteRemainingMs += timeControl.incrementMillis
    } else if (mover == PieceColor.BLACK && blackRemainingMs > 0L) {
      blackRemainingMs += timeControl.incrementMillis
    }
    activeSide = mover.opposite()
    lastTickRealtimeMs = now
  }

  fun pause() {
    if (timeControl.isUnlimited) return
    syncElapsed(SystemClock.elapsedRealtime())
    activeSide = null
  }

  fun snapshot(): Pair<Long, Long> {
    if (timeControl.isUnlimited) return 0L to 0L
    syncElapsed(SystemClock.elapsedRealtime())
    return whiteRemainingMs to blackRemainingMs
  }

  fun timedOutSide(): PieceColor? {
    if (timeControl.isUnlimited) return null
    syncElapsed(SystemClock.elapsedRealtime())
    return when {
      whiteRemainingMs <= 0L -> PieceColor.WHITE
      blackRemainingMs <= 0L -> PieceColor.BLACK
      else -> null
    }
  }

  private fun syncElapsed(nowMs: Long) {
    val side = activeSide ?: return
    if (lastTickRealtimeMs <= 0L) {
      lastTickRealtimeMs = nowMs
      return
    }
    val delta = (nowMs - lastTickRealtimeMs).coerceAtLeast(0L)
    lastTickRealtimeMs = nowMs
    if (side == PieceColor.WHITE) {
      whiteRemainingMs = (whiteRemainingMs - delta).coerceAtLeast(0L)
    } else {
      blackRemainingMs = (blackRemainingMs - delta).coerceAtLeast(0L)
    }
  }

  companion object {
    fun formatMillis(ms: Long, isUnlimited: Boolean): String {
      if (isUnlimited) return "∞"
      val clamped = ms.coerceAtLeast(0L)
      val totalSec = (clamped + 999L) / 1000L
      val mins = totalSec / 60L
      val secs = totalSec % 60L
      return if (mins == 0L && clamped < 10_000L) {
        val tenths = (clamped % 1000L) / 100L
        "0:%02d.%d".format(clamped / 1000L, tenths)
      } else {
        "%d:%02d".format(mins, secs)
      }
    }
  }
}
