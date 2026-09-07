package com.cyprienbrisset.fukkatsunop.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponse(
    @SerialName("current") val current: CurrentWeather? = null,
)

@Serializable
data class CurrentWeather(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val weatherCode: Int,
)

@Serializable
data class GeocodeResponse(
    @SerialName("results") val results: List<GeocodeResult> = emptyList(),
)

@Serializable
data class GeocodeResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

data class Weather(val temperatureC: Int, val description: String)

fun weatherCodeToText(code: Int): String = when (code) {
    0 -> "Ciel clair"
    1, 2, 3 -> "Nuageux"
    45, 48 -> "Brouillard"
    in 51..67 -> "Pluie"
    in 71..77 -> "Neige"
    in 80..82 -> "Averses"
    in 95..99 -> "Orage"
    else -> "—"
}
