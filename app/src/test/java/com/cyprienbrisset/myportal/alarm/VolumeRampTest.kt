package com.cyprienbrisset.fukkatsunop.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeRampTest {
    @Test fun startsLowReachesMaxAtEnd() {
        assertEquals(1, volumeAtStep(step = 0, totalSteps = 10, maxVolume = 10))
        assertEquals(10, volumeAtStep(step = 9, totalSteps = 10, maxVolume = 10))
        assertEquals(10, volumeAtStep(step = 20, totalSteps = 10, maxVolume = 10)) // clamps
    }
    @Test fun monotonicNonDecreasing() {
        var prev = 0
        for (s in 0..9) {
            val v = volumeAtStep(s, 10, 10)
            assert(v >= prev)
            prev = v
        }
    }
    @Test fun neverZeroSoAlarmIsAudible() {
        assertEquals(1, volumeAtStep(step = 0, totalSteps = 30, maxVolume = 7))
    }
}
