package com.cyprienbrisset.fukkatsunop.system

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class DndDurationTest {
    @Test fun laterTodayWhenBeforeTarget() {
        val now = LocalDateTime.of(2026, 9, 5, 6, 0)
        assertEquals(2 * 60 * 60 * 1000L, millisUntilHour(now, 8))
    }
    @Test fun tomorrowWhenAfterTarget() {
        val now = LocalDateTime.of(2026, 9, 5, 9, 0)
        assertEquals(23 * 60 * 60 * 1000L, millisUntilHour(now, 8))
    }
}
