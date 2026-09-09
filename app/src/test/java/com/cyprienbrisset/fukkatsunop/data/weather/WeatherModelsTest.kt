package com.cyprienbrisset.fukkatsunop.data.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherModelsTest {

    @Test
    fun `code 0 retourne Ciel clair`() {
        assertEquals("Ciel clair", weatherCodeToText(0))
    }

    @Test
    fun `codes 1 2 3 retournent Nuageux`() {
        assertEquals("Nuageux", weatherCodeToText(1))
        assertEquals("Nuageux", weatherCodeToText(2))
        assertEquals("Nuageux", weatherCodeToText(3))
    }

    @Test
    fun `codes 45 et 48 retournent Brouillard`() {
        assertEquals("Brouillard", weatherCodeToText(45))
        assertEquals("Brouillard", weatherCodeToText(48))
    }

    @Test
    fun `plage 51 a 67 retourne Pluie`() {
        assertEquals("Pluie", weatherCodeToText(51))
        assertEquals("Pluie", weatherCodeToText(61))
        assertEquals("Pluie", weatherCodeToText(67))
    }

    @Test
    fun `plage 71 a 77 retourne Neige`() {
        assertEquals("Neige", weatherCodeToText(71))
        assertEquals("Neige", weatherCodeToText(77))
    }

    @Test
    fun `plage 80 a 82 retourne Averses`() {
        assertEquals("Averses", weatherCodeToText(80))
        assertEquals("Averses", weatherCodeToText(82))
    }

    @Test
    fun `plage 95 a 99 retourne Orage`() {
        assertEquals("Orage", weatherCodeToText(95))
        assertEquals("Orage", weatherCodeToText(99))
    }

    @Test
    fun `code inconnu retourne tiret`() {
        assertEquals("—", weatherCodeToText(999))
        assertEquals("—", weatherCodeToText(-1))
        assertEquals("—", weatherCodeToText(4))
    }

    @Test
    fun `toWeather convertit CurrentWeather correctement`() {
        val cw = CurrentWeather(temperature = 22.7, weatherCode = 0)
        val w = cw.toWeather()
        assertEquals(23, w.temperatureC)
        assertEquals("Ciel clair", w.description)
    }

    @Test
    fun `toWeather arrondit correctement vers le bas`() {
        val cw = CurrentWeather(temperature = 18.3, weatherCode = 61)
        val w = cw.toWeather()
        assertEquals(18, w.temperatureC)
        assertEquals("Pluie", w.description)
    }
}
