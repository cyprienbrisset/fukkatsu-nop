package com.cyprienbrisset.fukkatsunop.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class WeatherLocation(val city: String, val lat: Double, val lon: Double)

class SettingsRepository(private val context: Context) {
    private val CITY            = stringPreferencesKey("weather_city")
    private val LAT             = doublePreferencesKey("weather_lat")
    private val LON             = doublePreferencesKey("weather_lon")
    private val WEATHER_EFFECTS = booleanPreferencesKey("weather_effects")

    val weatherLocation: Flow<WeatherLocation?> = context.dataStore.data.map { p ->
        val city = p[CITY]; val lat = p[LAT]; val lon = p[LON]
        if (city != null && lat != null && lon != null) WeatherLocation(city, lat, lon) else null
    }

    suspend fun setWeatherLocation(loc: WeatherLocation) {
        context.dataStore.edit { it[CITY] = loc.city; it[LAT] = loc.lat; it[LON] = loc.lon }
    }

    val weatherEffectsEnabled: Flow<Boolean> = context.dataStore.data.map { p ->
        p[WEATHER_EFFECTS] ?: true
    }

    suspend fun setWeatherEffectsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WEATHER_EFFECTS] = enabled }
    }
}
