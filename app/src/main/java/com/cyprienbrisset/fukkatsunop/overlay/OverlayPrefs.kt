package com.cyprienbrisset.fukkatsunop.overlay

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object OverlayPrefs {
    private const val PREFS = "overlay_prefs"
    private const val KEY_CONTROLS = "controls_enabled"

    private val _controlsEnabled = MutableStateFlow(true)
    val controlsEnabled: StateFlow<Boolean> get() = _controlsEnabled

    fun init(ctx: Context) {
        _controlsEnabled.value = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONTROLS, true)
    }

    fun setControlsEnabled(ctx: Context, enabled: Boolean) {
        _controlsEnabled.value = enabled
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CONTROLS, enabled).apply()
    }

    fun isControlsEnabled() = _controlsEnabled.value
}
