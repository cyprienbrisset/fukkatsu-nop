package com.cyprienbrisset.fukkatsunop.ui.home

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.fukkatsunop.alarm.nextTriggerTime
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.integration.RecentContactsRepository
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmRepository
import com.cyprienbrisset.fukkatsunop.data.settings.SettingsRepository
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileRepository
import kotlinx.coroutines.launch
import com.cyprienbrisset.fukkatsunop.data.weather.Weather
import com.cyprienbrisset.fukkatsunop.data.weather.WeatherRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayReceiver
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState
import com.cyprienbrisset.fukkatsunop.data.settings.WeatherLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TileRepository(AppDatabase.get(app).tileDao())
    private val settings = SettingsRepository(app)
    private val weatherRepo = WeatherRepository()
    private val alarmRepo = AlarmRepository(AppDatabase.get(app).alarmDao())

    val recentContacts = RecentContactsRepository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tiles = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val now: StateFlow<LocalDateTime> = flow {
        while (true) { emit(LocalDateTime.now()); delay(1000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDateTime.now())

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val weather: StateFlow<Weather?> =
        combine(settings.weatherCities, _weatherIndex, _weatherRefreshTrigger) { cities, idx, _ -> cities.getOrNull(idx) }
            .flatMapLatest { loc ->
                flow {
                    while (true) {
                        if (loc == null) {
                            emit(null)
                        } else {
                            runCatching { weatherRepo.currentWeather(loc.lat, loc.lon) }
                                .onSuccess { w -> _weatherError.value = null; _weatherFetchedAt.value = System.currentTimeMillis(); emit(w) }
                                .onFailure { e -> _weatherError.value = e.message ?: "Erreur réseau" }
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

    val nextAlarm: StateFlow<LocalDateTime?> =
        combine(alarmRepo.observeAll(), now) { list, current ->
            list.filter { it.enabled }
                .map { nextTriggerTime(it, current) }
                .minOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val nowPlayingController = com.cyprienbrisset.fukkatsunop.media.NowPlayingController(app)
    val nowPlaying = nowPlayingController.state

    // Rafraîchissement toutes les 5s — était déclenché par le tick d'horloge (1/s) côté UI.
    init {
        viewModelScope.launch {
            while (true) { nowPlayingController.refresh(); delay(5_000) }
        }
    }

    val airPlayState = AirPlayReceiver.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AirPlayState.Waiting)
    fun mediaToggle() = nowPlayingController.toggle()
    fun mediaNext() = nowPlayingController.next()
    fun mediaPrev() = nowPlayingController.prev()
    fun mediaSeek(positionMs: Long) = nowPlayingController.seekTo(positionMs)

    fun deleteTile(tile: TileEntity) = viewModelScope.launch { repo.delete(tile) }

    fun reorderTiles(ordered: List<TileEntity>) = viewModelScope.launch { repo.reorder(ordered) }

    private val _recentApps = MutableStateFlow<List<RecentApp>>(emptyList())
    val recentApps = _recentApps.asStateFlow()

    fun recordLaunch(packageName: String, label: String) {
        val current = _recentApps.value.toMutableList()
        current.removeAll { it.packageName == packageName }
        current.add(0, RecentApp(packageName, label))
        _recentApps.value = if (current.size > 8) current.take(8) else current
    }

    fun removeRecent(packageName: String) {
        _recentApps.value = _recentApps.value.filter { it.packageName != packageName }
        killApp(packageName)
    }

    val weatherEffectsEnabled: StateFlow<Boolean> = settings.weatherEffectsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setWeatherEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setWeatherEffectsEnabled(enabled) }
    }

    val saverMode: StateFlow<Boolean> = settings.saverMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setSaverMode(enabled: Boolean) {
        viewModelScope.launch { settings.setSaverMode(enabled) }
    }

    fun clearRecents() {
        _recentApps.value.forEach { killApp(it.packageName) }
        _recentApps.value = emptyList()
    }

    private fun killApp(packageName: String) {
        // Best-effort: kills cached background processes. Apps with foreground services (WhatsApp,
        // music players, etc.) cannot be terminated without system privileges — Android by design.
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        runCatching { am.killBackgroundProcesses(packageName) }
    }

    override fun onCleared() {
        nowPlayingController.dispose()
        super.onCleared()
    }
}
