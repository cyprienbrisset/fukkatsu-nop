package com.cyprienbrisset.fukkatsunop.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

fun CurrentWeather.toWeather(): Weather =
    Weather(temperatureC = temperature.roundToInt(), description = weatherCodeToText(weatherCode))

class WeatherRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun currentWeather(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body?.string() ?: return@withContext null
            json.decodeFromString(ForecastResponse.serializer(), body).current?.toWeather()
        }
    }

    suspend fun geocode(query: String): List<GeocodeResult> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$query&count=5&language=fr"
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string() ?: return@use emptyList<GeocodeResult>()
                json.decodeFromString(GeocodeResponse.serializer(), body).results
            }
        }.getOrDefault(emptyList())
    }
}
