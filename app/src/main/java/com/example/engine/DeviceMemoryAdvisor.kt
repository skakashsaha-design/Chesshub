package com.example.engine

import android.app.ActivityManager
import android.content.Context

data class MemoryProfile(
  val totalRamMb: Long,
  val availRamMb: Long,
  val isLowRamHardware: Boolean,
  val recommendLowMemoryMode: Boolean,
)

data class SearchBudget(
  val maxDepth: Int,
  val maxTimeMs: Long,
  val ttPowerOfTwoSize: Int, // number of TT entries (must be power of 2)
  val releaseAfterMove: Boolean,
)

object DeviceMemoryAdvisor {

  fun inspect(context: Context): MemoryProfile {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)
    val totalMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(512L)
    val availMb = (memInfo.availMem / (1024L * 1024L)).coerceAtLeast(64L)
    val isLowRam = am?.isLowRamDevice == true || totalMb <= 1536L
    return MemoryProfile(
      totalRamMb = totalMb,
      availRamMb = availMb,
      isLowRamHardware = isLowRam,
      recommendLowMemoryMode = isLowRam || availMb < 350L,
    )
  }

  fun computeSearchBudget(
    requestedDepth: Int,
    requestedTimeMs: Long,
    lowMemoryMode: Boolean,
    batterySavingMode: Boolean,
    isLowRamHardware: Boolean,
  ): SearchBudget {
    val constrain = lowMemoryMode || isLowRamHardware
    val depthCap = when {
      constrain && batterySavingMode -> 2
      constrain -> 3
      batterySavingMode -> 3
      else -> 5
    }
    val timeCap = when {
      constrain && batterySavingMode -> 350L
      constrain -> 650L
      batterySavingMode -> 750L
      else -> 2500L
    }
    val ttSize = if (constrain) 4096 else 16384 // ~112 KB vs ~448 KB
    return SearchBudget(
      maxDepth = requestedDepth.coerceIn(1, depthCap),
      maxTimeMs = requestedTimeMs.coerceIn(120L, timeCap),
      ttPowerOfTwoSize = ttSize,
      releaseAfterMove = constrain,
    )
  }
}
