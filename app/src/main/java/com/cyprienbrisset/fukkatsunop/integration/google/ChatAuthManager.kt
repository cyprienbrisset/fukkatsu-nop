package com.cyprienbrisset.fukkatsunop.integration.google

import android.util.Base64
import com.cyprienbrisset.fukkatsunop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

// Redirect URI = reversed Android client ID (standard pour les clients Android OAuth).
private val REDIRECT_URI: String get() {
    val id = BuildConfig.GOOGLE_ANDROID_CLIENT_ID
        .removePrefix("https://")
        .removeSuffix(".apps.googleusercontent.com")
    return "com.googleusercontent.apps.$id:/"
}

private const val CHAT_SCOPES =
    "https://www.googleapis.com/auth/chat.spaces.readonly" +
    " https://www.googleapis.com/auth/chat.messages.readonly"

private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

class ChatAuthManager(
    private val tokenStore: ChatTokenStore,
    private val http: OkHttpClient = OkHttpClient(),
) {
    val isLoggedIn: Flow<Boolean> = tokenStore.isLoggedIn

    private var pendingVerifier: String? = null

    fun buildAuthUrl(): String {
        val verifier  = generateCodeVerifier().also { pendingVerifier = it }
        val challenge = codeChallenge(verifier)
        val scope     = URLEncoder.encode(CHAT_SCOPES, "UTF-8")
        val redirect  = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=${BuildConfig.GOOGLE_ANDROID_CLIENT_ID}" +
            "&redirect_uri=$redirect" +
            "&response_type=code" +
            "&scope=$scope" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&access_type=offline" +
            "&prompt=consent"
    }

    suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val verifier = pendingVerifier ?: return@withContext false
        val body = FormBody.Builder()
            .add("client_id",     BuildConfig.GOOGLE_ANDROID_CLIENT_ID)
            .add("code",          code)
            .add("code_verifier", verifier)
            .add("grant_type",    "authorization_code")
            .add("redirect_uri",  REDIRECT_URI)
            .build()
        val raw = http.newCall(
            Request.Builder().url(TOKEN_URL).post(body).build()
        ).execute().use { it.body?.string() ?: return@withContext false }

        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return@withContext false
        val access    = obj["access_token"]?.jsonPrimitive?.content ?: return@withContext false
        val refresh   = obj["refresh_token"]?.jsonPrimitive?.content ?: return@withContext false
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        tokenStore.save(access, refresh, System.currentTimeMillis() + expiresIn * 1_000L)
        pendingVerifier = null
        true
    }

    suspend fun validToken(): String? = withContext(Dispatchers.IO) {
        val tokens = tokenStore.getTokens() ?: return@withContext null
        if (tokens.expiresAt > System.currentTimeMillis() + 60_000L) return@withContext tokens.accessToken
        refreshToken(tokens.refreshToken)
    }

    private suspend fun refreshToken(refreshToken: String): String? {
        val body = FormBody.Builder()
            .add("client_id",     BuildConfig.GOOGLE_ANDROID_CLIENT_ID)
            .add("refresh_token", refreshToken)
            .add("grant_type",    "refresh_token")
            .build()
        val raw = http.newCall(
            Request.Builder().url(TOKEN_URL).post(body).build()
        ).execute().use { it.body?.string() ?: return null }
        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: run {
            tokenStore.clear(); return null
        }
        val access = obj["access_token"]?.jsonPrimitive?.content ?: run {
            tokenStore.clear(); return null
        }
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        tokenStore.updateAccess(access, System.currentTimeMillis() + expiresIn * 1_000L)
        return access
    }

    suspend fun logout() = tokenStore.clear()

    companion object {
        // MainActivity publie le code ici quand le deep link arrive.
        private val _pendingCode = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val pendingCode: SharedFlow<String> = _pendingCode

        fun onAuthCode(code: String) { _pendingCode.tryEmit(code) }
    }
}

// ── PKCE helpers ─────────────────────────────────────────────────────────────

private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

private fun codeChallenge(verifier: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
