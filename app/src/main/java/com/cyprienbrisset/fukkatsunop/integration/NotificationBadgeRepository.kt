package com.cyprienbrisset.fukkatsunop.integration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationBadgeRepository {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun onPosted(packageName: String) {
        val m = _counts.value.toMutableMap()
        m[packageName] = (m[packageName] ?: 0) + 1
        _counts.value = m
    }

    fun onRemoved(packageName: String) {
        val m = _counts.value.toMutableMap()
        val n = (m[packageName] ?: 1) - 1
        if (n <= 0) m.remove(packageName) else m[packageName] = n
        _counts.value = m
    }

    fun setCount(packageName: String, count: Int) {
        _counts.value = _counts.value.toMutableMap().also {
            if (count <= 0) it.remove(packageName) else it[packageName] = count
        }
    }
}
