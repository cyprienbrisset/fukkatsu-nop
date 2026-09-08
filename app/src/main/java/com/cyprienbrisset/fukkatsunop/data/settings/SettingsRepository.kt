package com.cyprienbrisset.fukkatsunop.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class WeatherLocation(val city: String, val lat: Double, val lon: Double)

class SettingsRepository(private val context: Context) {
    // Legacy single-city keys (migration only)
    private val CITY_LEGACY      = stringPreferencesKey("weather_city")
    private val LAT_LEGACY       = doublePreferencesKey("weather_lat")
    private val LON_LEGACY       = doublePreferencesKey("weather_lon")
    private val WEATHER_CITIES   = stringPreferencesKey("weather_cities")
    private val WEATHER_EFFECTS  = booleanPreferencesKey("weather_effects")
    private val SAVER_MODE       = booleanPreferencesKey("saver_mode")

    val weatherCities: Flow<List<WeatherLocation>> = context.dataStore.data.map { p ->
        val json = p[WEATHER_CITIES]
        when {
            !json.isNullOrEmpty() ->
                runCatching { Json.decodeFromString<List<WeatherLocation>>(json) }.getOrElse { emptyList() }
            else -> {
                // Migration depuis les anciennes clés single-city
                val city = p[CITY_LEGACY]; val lat = p[LAT_LEGACY]; val lon = p[LON_LEGACY]
                if (city != null && lat != null && lon != null) listOf(WeatherLocation(city, lat, lon))
                else emptyList()
            }
        }
    }

    // Compatibilité avec HomeViewModel (premier élément de la liste)
    val weatherLocation: Flow<WeatherLocation?> = weatherCities.map { it.firstOrNull() }

    suspend fun addWeatherCity(loc: WeatherLocation) {
        context.dataStore.edit { p ->
            val current = decodeCities(p[WEATHER_CITIES])
            if (current.none { it.lat == loc.lat && it.lon == loc.lon }) {
                p[WEATHER_CITIES] = Json.encodeToString(current + loc)
            }
        }
    }

    suspend fun removeWeatherCity(index: Int) {
        context.dataStore.edit { p ->
            val current = decodeCities(p[WEATHER_CITIES]).toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                p[WEATHER_CITIES] = Json.encodeToString<List<WeatherLocation>>(current)
            }
        }
    }

    val weatherEffectsEnabled: Flow<Boolean> = context.dataStore.data.map { p ->
        p[WEATHER_EFFECTS] ?: true
    }

    suspend fun setWeatherEffectsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WEATHER_EFFECTS] = enabled }
    }

    val saverMode: Flow<Boolean> = context.dataStore.data.map { p -> p[SAVER_MODE] ?: false }

    suspend fun setSaverMode(enabled: Boolean) {
        context.dataStore.edit { it[SAVER_MODE] = enabled }
    }

    private fun decodeCities(json: String?): List<WeatherLocation> =
        if (!json.isNullOrEmpty()) runCatching { Json.decodeFromString<List<WeatherLocation>>(json) }.getOrElse { emptyList() }
        else emptyList()
}
