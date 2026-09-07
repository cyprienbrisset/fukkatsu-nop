package com.cyprienbrisset.fukkatsunop.integration.google

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.googleAuthDataStore by preferencesDataStore(name = "google_auth")

class GoogleTokenStore(private val context: Context) {

    private val KEY_ACCESS  = stringPreferencesKey("access_token")
    private val KEY_REFRESH = stringPreferencesKey("refresh_token")
    private val KEY_EXPIRES = longPreferencesKey("expires_at")

    val isLoggedIn: Flow<Boolean> = context.googleAuthDataStore.data.map {
        it[KEY_REFRESH] != null
    }

    data class Tokens(val accessToken: String, val refreshToken: String, val expiresAt: Long)

    suspend fun getTokens(): Tokens? {
        val p = context.googleAuthDataStore.data.first()
        val access  = p[KEY_ACCESS]  ?: return null
        val refresh = p[KEY_REFRESH] ?: return null
        val expires = p[KEY_EXPIRES] ?: 0L
        return Tokens(access, refresh, expires)
    }

    suspend fun save(accessToken: String, refreshToken: String, expiresAt: Long) {
        context.googleAuthDataStore.edit {
            it[KEY_ACCESS]  = accessToken
            it[KEY_REFRESH] = refreshToken
            it[KEY_EXPIRES] = expiresAt
        }
    }

    suspend fun updateAccess(accessToken: String, expiresAt: Long) {
        context.googleAuthDataStore.edit {
            it[KEY_ACCESS]  = accessToken
            it[KEY_EXPIRES] = expiresAt
        }
    }

    suspend fun clear() {
        context.googleAuthDataStore.edit { it.clear() }
    }
}
