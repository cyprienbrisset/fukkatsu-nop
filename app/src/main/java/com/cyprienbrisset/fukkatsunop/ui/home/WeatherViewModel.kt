package com.cyprienbrisset.fukkatsunop.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.settings.SettingsRepository
import com.cyprienbrisset.fukkatsunop.data.settings.WeatherLocation
import com.cyprienbrisset.fukkatsunop.data.weather.Weather
import com.cyprienbrisset.fukkatsunop.data.weather.WeatherRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val weatherRepo = WeatherRepository(
        cache = AppDatabase.get(app).weatherCacheDao()
    )

    val weatherCities: StateFlow<List<WeatherLocation>> = settings.weatherCities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _weatherIndex = MutableStateFlow(0)
    val weatherIndex: StateFlow<Int> = _weatherIndex.asStateFlow()

    val currentCity: StateFlow<WeatherLocation?> =
        combine(settings.weatherCities, _weatherIndex) { cities, idx -> cities.getOrNull(idx) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _weatherRefreshTrigger = MutableStateFlow(0L)
    private val _weatherError = MutableStateFlow<String?>(null)
    private val _weatherFetchedAt = MutableStateFlow<Long?>(null)

    val weatherError: StateFlow<String?> = _weatherError.asStateFlow()
    val weatherFetchedAt: StateFlow<Long?> = _weatherFetchedAt.asStateFlow()

    fun refreshWeather() { _weatherRefreshTrigger.value++ }

    val weather: StateFlow<Weather?> =
        combine(settings.weatherCities, _weatherIndex, _weatherRefreshTrigger) { cities, idx, _ ->
            cities.getOrNull(idx)
        }.flatMapLatest { loc ->
            flow {
                while (true) {
                    if (loc == null) {
                        emit(null)
                    } else {
                        runCatching { weatherRepo.currentWeather(loc.lat, loc.lon) }
                            .onSuccess { w -> _weatherError.value = null; _weatherFetchedAt.value = System.currentTimeMillis(); emit(w) }
                            .onFailure { e ->
                                Timber.w(e, "weather fetch failed")
                                _weatherError.value = e.message ?: "Erreur réseau"
                            }
                    }
                    delay(15 * 60 * 1000)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun nextWeatherCity() {
        val size = weatherCities.value.size
        if (size > 1) _weatherIndex.value = (_weatherIndex.value + 1) % size
    }

    fun prevWeatherCity() {
        val size = weatherCities.value.size
        if (size > 1) _weatherIndex.value = (_weatherIndex.value - 1 + size) % size
    }
}
