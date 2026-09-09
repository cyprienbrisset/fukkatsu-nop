package com.cyprienbrisset.fukkatsunop.data.weather

import com.cyprienbrisset.fukkatsunop.network.RetryInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

fun CurrentWeather.toWeather(): Weather =
    Weather(temperatureC = temperature.roundToInt(), description = weatherCodeToText(weatherCode))

class WeatherRepository(
    private val cache: WeatherCacheDao? = null,
    private val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(RetryInterceptor()).build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun currentWeather(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        val key = "${lat.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP)},${lon.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP)}"
        val now = System.currentTimeMillis()
        cache?.get(key)?.takeIf { now - it.fetchedAt < WEATHER_TTL_MS }
            ?.let { return@withContext Weather(it.temperatureC, it.description) }

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body?.string() ?: return@withContext null
            json.decodeFromString(ForecastResponse.serializer(), body).current?.toWeather()
                ?.also { w -> cache?.put(WeatherCacheEntity(key, w.temperatureC, w.description, now)) }
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

    companion object {
        private const val WEATHER_TTL_MS = 15 * 60 * 1_000L
    }
}
