package com.cyprienbrisset.fukkatsunop.data.weather

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun parsesCurrentWeather() {
        val body = """{"current":{"temperature_2m":18.6,"weather_code":3}}"""
        val parsed = json.decodeFromString(ForecastResponse.serializer(), body)
        val w = parsed.current!!.toWeather()
        assertEquals(19, w.temperatureC)
        assertEquals("Nuageux", w.description)
    }

    @Test fun parsesGeocode() {
        val body = """{"results":[{"name":"Lyon","latitude":45.75,"longitude":4.85,"country":"France"}]}"""
        val parsed = json.decodeFromString(GeocodeResponse.serializer(), body)
        assertEquals("Lyon", parsed.results.first().name)
    }
}
