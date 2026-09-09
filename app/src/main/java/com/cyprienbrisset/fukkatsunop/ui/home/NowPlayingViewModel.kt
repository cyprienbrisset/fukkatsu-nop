package com.cyprienbrisset.fukkatsunop.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.fukkatsunop.media.NowPlayingController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NowPlayingViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = NowPlayingController(app)
    val nowPlaying = controller.state

    init {
        viewModelScope.launch {
            while (true) { controller.refresh(); delay(5_000) }
        }
    }

    fun toggle() = controller.toggle()
    fun next() = controller.next()
    fun prev() = controller.prev()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    override fun onCleared() {
        controller.dispose()
        super.onCleared()
    }
}
