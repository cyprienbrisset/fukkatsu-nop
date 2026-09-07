package com.cyprienbrisset.fukkatsunop.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.gplayapi.data.models.AuthData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.storeDataStore by preferencesDataStore(name = "fukkastore")

class FukkaAccount(private val context: Context) {
    private val EMAIL = stringPreferencesKey("email")
    private val AAS = stringPreferencesKey("aas")
    @Volatile private var cached: AuthData? = null

    val isLoggedIn = context.storeDataStore.data.map { it[EMAIL] != null && it[AAS] != null }

    suspend fun save(email: String, aasToken: String) {
        context.storeDataStore.edit { it[EMAIL] = email; it[AAS] = aasToken }
        cached = null
    }

    suspend fun logout() {
        context.storeDataStore.edit { it.remove(EMAIL); it.remove(AAS) }
        cached = null
    }

    /** Returns a usable AuthData (cached), rebuilding from the saved aasToken if needed. */
    suspend fun authData(): AuthData? {
        cached?.let { return it }
        val prefs = context.storeDataStore.data.first()
        val email = prefs[EMAIL] ?: return null
        val aas = prefs[AAS] ?: return null
        return buildAuthDataFromAas(context, email, aas).also { cached = it }
    }
}
