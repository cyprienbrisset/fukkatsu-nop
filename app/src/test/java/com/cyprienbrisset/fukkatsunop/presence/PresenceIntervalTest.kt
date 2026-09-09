package com.cyprienbrisset.fukkatsunop.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceIntervalTest {

    @Test
    fun `intervalle court si presence detectee`() {
        assertEquals(2_000L, adaptiveIntervalMs(isPresent = true))
    }

    @Test
    fun `intervalle long si pas de presence`() {
        assertEquals(10_000L, adaptiveIntervalMs(isPresent = false))
    }
}
