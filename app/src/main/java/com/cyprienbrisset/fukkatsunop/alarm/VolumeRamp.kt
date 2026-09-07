package com.cyprienbrisset.fukkatsunop.alarm

import kotlin.math.max
import kotlin.math.min

/**
 * Volume at a given ramp [step] (0-based) out of [totalSteps], climbing to [maxVolume].
 * Always at least 1 so the alarm is immediately audible; clamps at maxVolume.
 */
fun volumeAtStep(step: Int, totalSteps: Int, maxVolume: Int): Int {
    if (maxVolume <= 0) return 0
    if (totalSteps <= 1) return maxVolume
    val progress = (step + 1).toDouble() / totalSteps
    val v = Math.round(progress * maxVolume).toInt()
    return min(maxVolume, max(1, v))
}
