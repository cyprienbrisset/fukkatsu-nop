package com.cyprienbrisset.myportal.ui.google

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.integration.google.CalendarEvent
import com.cyprienbrisset.myportal.integration.google.DeviceFlowData
import com.cyprienbrisset.myportal.integration.google.GoogleAuthManager
import com.cyprienbrisset.myportal.integration.google.GoogleCalendarRepo
import com.cyprienbrisset.myportal.integration.google.GoogleMailRepo
import com.cyprienbrisset.myportal.integration.google.GoogleTokenStore
import com.cyprienbrisset.myportal.integration.google.MailMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── State types ───────────────────────────────────────────────────────────────

data class GoogleUiState(
    val authState: AuthState                    = AuthState.Loading,
    val agenda: TabState<List<CalendarEvent>>   = TabState.Loading,
    val mail: TabState<List<MailMessage>>       = TabState.Loading,
)

sealed interface AuthState {
    object Loading     : AuthState
    object NotLoggedIn : AuthState
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
    private val mailRepo: GoogleMailRepo,
) : ViewModel() {

    private val _state = MutableStateFlow(GoogleUiState())
    val state: StateFlow<GoogleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    _state.update { it.copy(authState = AuthState.LoggedIn) }
                    loadAll()
                } else if (_state.value.authState !is AuthState.DeviceFlow) {
                    _state.update { it.copy(authState = AuthState.NotLoggedIn) }
                }
            }
        }
    }

    fun startDeviceFlow() {
        viewModelScope.launch {
            try {
                val data: DeviceFlowData = authManager.startDeviceFlow()
                _state.update {
                    it.copy(authState = AuthState.DeviceFlow(data.userCode, data.verificationUrl))
                }
                val success = authManager.pollForToken(data)
                if (!success) _state.update { it.copy(authState = AuthState.NotLoggedIn) }
            } catch (e: Exception) {
                _state.update { it.copy(authState = AuthState.NotLoggedIn) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authManager.logout() }
    }

    fun retryAgenda() { viewModelScope.launch { loadAgenda() } }
    fun retryMail()   { viewModelScope.launch { loadMail() } }

    private fun loadAll() {
        viewModelScope.launch {
            val a = async { loadAgenda() }
            val m = async { loadMail() }
            a.await(); m.await()
        }
    }

    private suspend fun loadAgenda() {
        _state.update { it.copy(agenda = TabState.Loading) }
        runCatching { calendarRepo.fetchEvents() }
            .onSuccess { events -> _state.update { it.copy(agenda = TabState.Success(events)) } }
            .onFailure { e ->     _state.update { it.copy(agenda = TabState.Error(e.message ?: "Erreur réseau")) } }
    }

    private suspend fun loadMail() {
        _state.update { it.copy(mail = TabState.Loading) }
        runCatching { mailRepo.listInbox() }
            .onSuccess { msgs -> _state.update { it.copy(mail = TabState.Success(msgs)) } }
            .onFailure { e ->    _state.update { it.copy(mail = TabState.Error(e.message ?: "Erreur réseau")) } }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val store    = GoogleTokenStore(context)
            val auth     = GoogleAuthManager(context, store)
            val calendar = GoogleCalendarRepo(auth)
            val mail     = GoogleMailRepo(auth)
            return GoogleViewModel(auth, calendar, mail) as T
        }
    }
}
