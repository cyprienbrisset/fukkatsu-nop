package com.cyprienbrisset.fukkatsunop.alarm

import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AlarmSchedulerTest {

    private fun alarm(hour: Int, minute: Int, repeatDays: Int = 0) =
        AlarmEntity(hour = hour, minute = minute, repeatDays = repeatDays)

    private fun bits(vararg days: Int): Int = days.fold(0) { acc, d -> acc or (1 shl d) }

    @Test
    fun `one-shot futur meme jour retourne aujourd'hui`() {
        val from = LocalDateTime.of(2026, 9, 7, 10, 0)  // Lundi
        val alarm = alarm(hour = 11, minute = 0)
        val result = nextTriggerTime(alarm, from)
        assertEquals(LocalDateTime.of(2026, 9, 7, 11, 0), result)
    }

    @Test
    fun `one-shot passe meme jour retourne demain`() {
        val from = LocalDateTime.of(2026, 9, 7, 12, 0)
        val alarm = alarm(hour = 11, minute = 0)
        val result = nextTriggerTime(alarm, from)
        assertEquals(LocalDateTime.of(2026, 9, 8, 11, 0), result)
    }

    @Test
    fun `repeatDays lundi retourne meme jour si futur`() {
        val from = LocalDateTime.of(2026, 9, 7, 8, 0)  // Lundi
        val alarm = alarm(hour = 9, minute = 0, repeatDays = bits(0))
        val result = nextTriggerTime(alarm, from)
        assertEquals(LocalDateTime.of(2026, 9, 7, 9, 0), result)
    }

    @Test
    fun `repeatDays lundi retourne semaine suivante si passe`() {
        val from = LocalDateTime.of(2026, 9, 7, 10, 0)
        val alarm = alarm(hour = 9, minute = 0, repeatDays = bits(0))
        val result = nextTriggerTime(alarm, from)
        assertEquals(LocalDateTime.of(2026, 9, 14, 9, 0), result)
    }

    @Test
    fun `repeatDays lundi-vendredi retourne prochain jour ouvrable`() {
        val from = LocalDateTime.of(2026, 9, 9, 12, 0)  // Mercredi
        val alarm = alarm(hour = 9, minute = 0, repeatDays = bits(0, 1, 2, 3, 4))
        val result = nextTriggerTime(alarm, from)
        assertEquals(LocalDateTime.of(2026, 9, 10, 9, 0), result)
    }

    @Test
    fun `repeatDays weekend retourne samedi depuis lundi`() {
        val from = LocalDateTime.of(2026, 9, 7, 8, 0)  // Lundi
        val alarm = alarm(hour = 10, minute = 0, repeatDays = bits(5, 6))
        val result = nextTriggerTime(alarm, from)
        assertEquals(LocalDateTime.of(2026, 9, 12, 10, 0), result)
    }

    @Test
    fun `repeatDays tous les jours retourne aujourd'hui si futur`() {
        val from = LocalDateTime.of(2026, 9, 9, 6, 0)
        val alarm = alarm(hour = 7, minute = 0, repeatDays = bits(0, 1, 2, 3, 4, 5, 6))
        val result = nextTriggerTime(alarm, from)
        assertEquals(LocalDateTime.of(2026, 9, 9, 7, 0), result)
    }
}
