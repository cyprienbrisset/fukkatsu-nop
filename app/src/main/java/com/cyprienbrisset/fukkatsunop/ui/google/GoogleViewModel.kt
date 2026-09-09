package com.cyprienbrisset.fukkatsunop.ui.google

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.fukkatsunop.integration.google.CalendarEvent
import com.cyprienbrisset.fukkatsunop.integration.google.ChatAuthManager
import com.cyprienbrisset.fukkatsunop.integration.google.ChatMessage
import com.cyprienbrisset.fukkatsunop.integration.google.ChatSpace
import com.cyprienbrisset.fukkatsunop.integration.google.ChatTokenStore
import com.cyprienbrisset.fukkatsunop.integration.google.DeviceFlowData
import com.cyprienbrisset.fukkatsunop.integration.google.GoogleAuthManager
import com.cyprienbrisset.fukkatsunop.integration.google.GoogleCalendarRepo
import com.cyprienbrisset.fukkatsunop.integration.google.GoogleChatRepo
import com.cyprienbrisset.fukkatsunop.integration.google.GoogleTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── State types ───────────────────────────────────────────────────────────────

data class GoogleUiState(
    val authState: AuthState                      = AuthState.Loading,
    val agenda: TabState<List<CalendarEvent>>     = TabState.Loading,
    val isChatLoggedIn: Boolean                   = false,
    val chatAuthUrl: String?                      = null,
    val chatSpaces: TabState<List<ChatSpace>>     = TabState.Loading,
    val chatMessages: TabState<List<ChatMessage>> = TabState.Loading,
    val selectedSpace: ChatSpace?                 = null,
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
    private val chatRepo: GoogleChatRepo,
    private val chatAuthManager: ChatAuthManager,
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
        viewModelScope.launch {
            chatAuthManager.isLoggedIn.collect { loggedIn ->
                _state.update { it.copy(isChatLoggedIn = loggedIn) }
                if (loggedIn && _state.value.chatSpaces is TabState.Loading) {
                    loadChatSpaces()
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
    fun startChatAuth() {
        val url = chatAuthManager.buildAuthUrl()
        _state.update { it.copy(chatAuthUrl = url) }
    }
    fun onChatAuthCode(code: String) {
        _state.update { it.copy(chatAuthUrl = null) }
        viewModelScope.launch {
            val ok = chatAuthManager.exchangeCode(code)
            if (!ok) _state.update { it.copy(chatSpaces = TabState.Error("Échange de code échoué")) }
        }
    }
    fun dismissChatAuth() { _state.update { it.copy(chatAuthUrl = null) } }
    fun logoutChat() { viewModelScope.launch { chatAuthManager.logout() } }
    fun retryChatSpaces() { viewModelScope.launch { loadChatSpaces() } }
    fun retryMessages() {
        val space = _state.value.selectedSpace ?: return
        viewModelScope.launch { loadMessages(space) }
    }
    fun selectSpace(space: ChatSpace) {
        _state.update { it.copy(selectedSpace = space, chatMessages = TabState.Loading) }
        viewModelScope.launch { loadMessages(space) }
    }
    fun clearSelectedSpace() { _state.update { it.copy(selectedSpace = null) } }

    fun createEvent(title: String, start: java.time.Instant, end: java.time.Instant) {
        viewModelScope.launch {
            runCatching { calendarRepo.createEvent(title, start, end) }
                .onSuccess { ok -> if (ok) loadAgenda() }
        }
    }

    private fun loadAll() {
        viewModelScope.launch { loadAgenda() }
        viewModelScope.launch { loadChatSpaces() }
    }

    private suspend fun loadAgenda() {
        _state.update { it.copy(agenda = TabState.Loading) }
        runCatching { calendarRepo.fetchEvents() }
            .onSuccess { events -> _state.update { it.copy(agenda = TabState.Success(events)) } }
            .onFailure { e ->     _state.update { it.copy(agenda = TabState.Error(e.message ?: "Erreur réseau")) } }
    }

    private suspend fun loadChatSpaces() {
        _state.update { it.copy(chatSpaces = TabState.Loading) }
        runCatching { chatRepo.fetchSpaces() }
            .onSuccess { spaces -> _state.update { it.copy(chatSpaces = TabState.Success(spaces)) } }
            .onFailure { e ->      _state.update { it.copy(chatSpaces = TabState.Error(e.message ?: "Erreur réseau")) } }
    }

    private suspend fun loadMessages(space: ChatSpace) {
        _state.update { it.copy(chatMessages = TabState.Loading) }
        runCatching { chatRepo.fetchMessages(space.name) }
            .onSuccess { msgs -> _state.update { it.copy(chatMessages = TabState.Success(msgs)) } }
            .onFailure { e ->    _state.update { it.copy(chatMessages = TabState.Error(e.message ?: "Erreur réseau")) } }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val store     = GoogleTokenStore(context)
            val auth      = GoogleAuthManager(context, store)
            val calendar  = GoogleCalendarRepo(auth)
            val chatStore = ChatTokenStore(context)
            val chatAuth  = ChatAuthManager(chatStore)
            val chat      = GoogleChatRepo(chatAuth)
            return GoogleViewModel(auth, calendar, chat, chatAuth) as T
        }
    }
}
