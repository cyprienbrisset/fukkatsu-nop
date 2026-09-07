package com.cyprienbrisset.fukkatsunop.ui.google

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.fukkatsunop.integration.google.CalendarEvent
import com.cyprienbrisset.fukkatsunop.integration.google.DeviceFlowData
import com.cyprienbrisset.fukkatsunop.integration.google.GoogleAuthManager
import com.cyprienbrisset.fukkatsunop.integration.google.GoogleCalendarRepo
import com.cyprienbrisset.fukkatsunop.integration.google.GoogleTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── State types ───────────────────────────────────────────────────────────────

data class GoogleUiState(
    val authState: AuthState                  = AuthState.Loading,
    val agenda: TabState<List<CalendarEvent>> = TabState.Loading,
)

sealed interface AuthState {
    object Loading     : AuthState
    data class NotLoggedIn(val error: String? = null) : AuthState
    data class DeviceFlow(val userCode: String, val verificationUrl: String) : AuthState
    object LoggedIn    : AuthState
}

sealed interface TabState<out T> {
    object Loading                          : TabState<Nothing>
    data class Success<T>(val data: T)     : TabState<T>
    data class Error(val message: String)  : TabState<Nothing>
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GoogleViewModel(
    private val authManager: GoogleAuthManager,
    private val calendarRepo: GoogleCalendarRepo,
) : ViewModel() {

    private val _state = MutableStateFlow(GoogleUiState())
    val state: StateFlow<GoogleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    val alreadyLoggedIn = _state.value.authState is AuthState.LoggedIn
                    _state.update { it.copy(authState = AuthState.LoggedIn) }
                    if (!alreadyLoggedIn) loadAll()
                } else if (_state.value.authState !is AuthState.DeviceFlow) {
                    _state.update { it.copy(authState = AuthState.NotLoggedIn()) }
                }
            }
        }
    }

    fun startDeviceFlow() {
        _state.update { it.copy(authState = AuthState.Loading) }
        viewModelScope.launch {
            try {
                val data: DeviceFlowData = authManager.startDeviceFlow()
                _state.update {
                    it.copy(authState = AuthState.DeviceFlow(data.userCode, data.verificationUrl))
                }
                val success = authManager.pollForToken(data)
                if (!success) _state.update { it.copy(authState = AuthState.NotLoggedIn("Autorisation refusée ou expirée")) }
            } catch (e: Exception) {
                _state.update { it.copy(authState = AuthState.NotLoggedIn(e.message ?: "Erreur de connexion")) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authManager.logout() }
    }

    fun retryAgenda() { viewModelScope.launch { loadAgenda() } }

    fun createEvent(title: String, start: java.time.Instant, end: java.time.Instant) {
        viewModelScope.launch {
            runCatching { calendarRepo.createEvent(title, start, end) }
                .onSuccess { ok -> if (ok) loadAgenda() }
        }
    }

    private fun loadAll() {
        viewModelScope.launch { loadAgenda() }
    }

    private suspend fun loadAgenda() {
        _state.update { it.copy(agenda = TabState.Loading) }
        runCatching { calendarRepo.fetchEvents() }
            .onSuccess { events -> _state.update { it.copy(agenda = TabState.Success(events)) } }
            .onFailure { e ->     _state.update { it.copy(agenda = TabState.Error(e.message ?: "Erreur réseau")) } }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val store    = GoogleTokenStore(context)
            val auth     = GoogleAuthManager(context, store)
            val calendar = GoogleCalendarRepo(auth)
            return GoogleViewModel(auth, calendar) as T
        }
    }
}
