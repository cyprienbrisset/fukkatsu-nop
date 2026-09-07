package com.cyprienbrisset.fukkatsunop.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.fukkatsunop.data.settings.SettingsRepository
import com.cyprienbrisset.fukkatsunop.data.settings.WeatherLocation
import com.cyprienbrisset.fukkatsunop.data.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CityResult(val label: String, val city: String, val lat: Double, val lon: Double)

class WeatherSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val weather = WeatherRepository()
    private val settings = SettingsRepository(app)
    private val _results = MutableStateFlow<List<CityResult>>(emptyList())
    val results: StateFlow<List<CityResult>> = _results

    fun search(q: String) = viewModelScope.launch {
        _results.value = weather.geocode(q).map {
            CityResult(
                label = listOfNotNull(it.name, it.admin1, it.country).joinToString(", "),
                city = it.name, lat = it.latitude, lon = it.longitude,
            )
        }
    }

    fun select(r: CityResult) = viewModelScope.launch {
        settings.setWeatherLocation(WeatherLocation(r.city, r.lat, r.lon))
    }
}
