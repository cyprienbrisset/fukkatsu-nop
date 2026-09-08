package com.cyprienbrisset.fukkatsunop.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** -1 = idle/error, 0-100 = download % */
object UpdateProgress {
    private val _pct = MutableStateFlow(-1)
    val pct: StateFlow<Int> = _pct
    fun set(value: Int) { _pct.value = value }
}
