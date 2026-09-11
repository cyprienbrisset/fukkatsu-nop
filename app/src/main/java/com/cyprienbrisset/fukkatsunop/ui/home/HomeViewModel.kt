package com.cyprienbrisset.fukkatsunop.ui.home

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayReceiver
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState
import com.cyprienbrisset.fukkatsunop.alarm.nextTriggerTime
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmRepository
import com.cyprienbrisset.fukkatsunop.data.settings.SettingsRepository
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileRepository
import com.cyprienbrisset.fukkatsunop.integration.RecentContactsRepository
import com.cyprienbrisset.fukkatsunop.presence.PresenceManager
import com.cyprienbrisset.fukkatsunop.presence.PresenceService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TileRepository(AppDatabase.get(app).tileDao())
    private val settings = SettingsRepository(app)
    private val alarmRepo = AlarmRepository(AppDatabase.get(app).alarmDao())

    val recentContacts = RecentContactsRepository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tiles = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val now: StateFlow<LocalDateTime> = flow {
        while (true) { emit(LocalDateTime.now()); delay(1000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDateTime.now())

    val nextAlarm: StateFlow<LocalDateTime?> =
        combine(alarmRepo.observeAll(), now) { list, current ->
            list.filter { it.enabled }
                .map { nextTriggerTime(it, current) }
                .minOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val airPlayState = AirPlayReceiver.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AirPlayState.Waiting)

    private val _recentApps = MutableStateFlow<List<RecentApp>>(emptyList())
    val recentApps = _recentApps.asStateFlow()

    data class RamInfo(val totalMb: Long, val availMb: Long, val lowMemory: Boolean)
    private val _ramInfo = MutableStateFlow<RamInfo?>(null)
    val ramInfo = _ramInfo.asStateFlow()
    private val _ramFreedMb = MutableStateFlow<Long?>(null)
    val ramFreedMb = _ramFreedMb.asStateFlow()

    fun refreshRam() {
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        _ramInfo.value = RamInfo(info.totalMem / 1_048_576L, info.availMem / 1_048_576L, info.lowMemory)
    }

    val weatherEffectsEnabled: StateFlow<Boolean> = settings.weatherEffectsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val saverMode: StateFlow<Boolean> = settings.saverMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val presenceEnabled: StateFlow<Boolean> = settings.presenceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val contacts = loadMessengerShortcuts(app)
            if (contacts.isNotEmpty()) RecentContactsRepository.setBaseContacts(contacts)
        }
        viewModelScope.launch {
            settings.presenceEnabled.flatMapLatest { enabled ->
                if (enabled) PresenceManager.isPresent else flowOf(null)
            }.collect { present ->
                present ?: return@collect
                val timeout = if (present) 30 * 60 * 1000 else 30 * 1000
                if (Settings.System.canWrite(app)) {
                    Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeout)
                }
            }
        }
    }

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

    fun clearRecents() {
        refreshRam()
        val availBefore = _ramInfo.value?.availMb ?: 0L
        _recentApps.value.forEach { killApp(it.packageName) }
        _recentApps.value = emptyList()
        viewModelScope.launch {
            delay(600)
            refreshRam()
            val freed = (_ramInfo.value?.availMb ?: 0L) - availBefore
            _ramFreedMb.value = if (freed > 0) freed else null
        }
    }

    fun clearRamFreed() { _ramFreedMb.value = null }

    fun deleteTile(tile: TileEntity) = viewModelScope.launch { repo.delete(tile) }

    fun reorderTiles(ordered: List<TileEntity>) = viewModelScope.launch { repo.reorder(ordered) }

    fun setWeatherEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setWeatherEffectsEnabled(enabled) }
    }

    fun setSaverMode(enabled: Boolean) {
        viewModelScope.launch { settings.setSaverMode(enabled) }
    }

    fun setPresenceEnabled(ctx: Context, enabled: Boolean) {
        viewModelScope.launch {
            settings.setPresenceEnabled(enabled)
            if (enabled) PresenceService.start(ctx) else PresenceService.stop(ctx)
            if (!enabled && Settings.System.canWrite(ctx)) {
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 5 * 60 * 1000)
            }
        }
    }

    private fun killApp(packageName: String) {
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        runCatching { am.killBackgroundProcesses(packageName) }
    }
}

private val MESSAGING_PACKAGES = listOf(
    "com.whatsapp",
    "com.facebook.orca",
    "com.facebook.aloha.app.messenger",
    "com.facebook.aloha.app.whatsapp",
)

private fun loadMessengerShortcuts(app: Application): List<com.cyprienbrisset.fukkatsunop.integration.RecentContact> {
    val launcherApps = app.getSystemService(android.content.pm.LauncherApps::class.java)
        ?: return emptyList()
    val userHandle = android.os.Process.myUserHandle()
    val query = android.content.pm.LauncherApps.ShortcutQuery().apply {
        setQueryFlags(
            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER,
        )
    }
    return MESSAGING_PACKAGES.flatMap { pkg ->
        runCatching {
            query.setPackage(pkg)
            launcherApps.getShortcuts(query, userHandle)?.mapNotNull { sc ->
                val label = sc.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: sc.longLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val bmp = runCatching {
                    launcherApps.getShortcutIconDrawable(sc, 0)?.toBitmap()
                }.getOrNull()
                com.cyprienbrisset.fukkatsunop.integration.RecentContact(
                    key = "$pkg:${sc.id}",
                    name = label,
                    avatar = bmp,
                    packageName = pkg,
                    lastSeenMs = sc.lastChangedTimestamp,
                    tapIntent = null,
                    callIntent = null,
                    shortcutId = sc.id,
                )
            } ?: emptyList()
        }.getOrElse { emptyList() }
    }.sortedByDescending { it.lastSeenMs }.take(6)
}
