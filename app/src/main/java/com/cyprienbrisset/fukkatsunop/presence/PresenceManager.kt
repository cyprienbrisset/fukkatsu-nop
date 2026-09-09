package com.cyprienbrisset.fukkatsunop.presence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PresenceManager {
    private val _isPresent = MutableStateFlow(false)
    val isPresent: StateFlow<Boolean> = _isPresent

    private var lastFaceMs = 0L
    private const val GRACE_MS = 60_000L

    fun faceDetected() {
        _isPresent.value = true
        lastFaceMs = System.currentTimeMillis()
    }

    fun noFace() {
        if (System.currentTimeMillis() - lastFaceMs >= GRACE_MS) {
            _isPresent.value = false
        }
    }

    fun reset() {
        _isPresent.value = false
        lastFaceMs = 0L
    }
}
