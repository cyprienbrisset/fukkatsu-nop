package com.cyprienbrisset.fukkatsunop.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileRepository
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.launch.LaunchIntentResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TileEditViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TileRepository(AppDatabase.get(app).tileDao())
    val tiles = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun installedApps() = withContext(Dispatchers.IO) {
        LaunchIntentResolver.installedLaunchableApps(getApplication())
    }

    fun addApp(label: String, pkg: String) = viewModelScope.launch {
        repo.add(TileEntity(type = TileType.APP, label = label, packageName = pkg, position = 0))
    }

    fun addWeb(label: String, url: String) = viewModelScope.launch {
        val normalized = if (url.startsWith("http")) url else "https://$url"
        repo.add(TileEntity(type = TileType.WEB, label = label, url = normalized, position = 0))
    }

    fun addAirPlay() = viewModelScope.launch {
        val existing = repo.getAll().firstOrNull { it.type == TileType.AIRPLAY }
        if (existing == null) {
            repo.add(TileEntity(type = TileType.AIRPLAY, label = "Écran Mac", position = 0))
        }
    }

    fun delete(tile: TileEntity) = viewModelScope.launch { repo.delete(tile) }
    fun moveUp(tile: TileEntity) = viewModelScope.launch {
        val list = repo.getAll().toMutableList()
        val i = list.indexOfFirst { it.id == tile.id }
        if (i > 0) { list.add(i - 1, list.removeAt(i)); repo.reorder(list) }
    }
    fun moveDown(tile: TileEntity) = viewModelScope.launch {
        val list = repo.getAll().toMutableList()
        val i = list.indexOfFirst { it.id == tile.id }
        if (i in 0 until list.lastIndex) { list.add(i + 1, list.removeAt(i)); repo.reorder(list) }
    }
}
