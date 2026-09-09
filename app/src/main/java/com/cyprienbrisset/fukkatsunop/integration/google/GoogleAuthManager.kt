package com.cyprienbrisset.fukkatsunop.integration.google

import android.content.Context
import com.cyprienbrisset.fukkatsunop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

private const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
private const val TOKEN_URL       = "https://oauth2.googleapis.com/token"
private const val SCOPES = "https://www.googleapis.com/auth/calendar"

data class DeviceFlowData(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int,
    val interval: Int,
)

class GoogleAuthManager(
    context: Context,
    private val tokenStore: GoogleTokenStore = GoogleTokenStore(context),
    private val http: OkHttpClient = OkHttpClient(),
) {
    val isLoggedIn = tokenStore.isLoggedIn

    suspend fun startDeviceFlow(): DeviceFlowData = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.GOOGLE_CLIENT_ID)
            .add("scope", SCOPES)
            .add("access_type", "offline")
            .build()
        val raw = http.newCall(
            Request.Builder().url(DEVICE_CODE_URL).post(body).build()
        ).execute().use { it.body!!.string() }
        parseDeviceCodeResponse(raw)
    }

    /** Polls until token received, returns true on success. Cancellable coroutine. */
    suspend fun pollForToken(data: DeviceFlowData): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + data.expiresIn * 1_000L
        var intervalMs = data.interval * 1_000L
        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            val pollBody = FormBody.Builder()
                .add("client_id",     BuildConfig.GOOGLE_CLIENT_ID)
                .add("client_secret", BuildConfig.GOOGLE_CLIENT_SECRET)
                .add("device_code",   data.deviceCode)
                .add("grant_type",    "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            // Transient network errors don't kill the flow — we just retry next interval.
            val raw = try {
                http.newCall(Request.Builder().url(TOKEN_URL).post(pollBody).build())
                    .execute().use { it.body!!.string() }
            } catch (_: Exception) { continue }
            val obj = try {
                Json.parseToJsonElement(raw).jsonObject
            } catch (_: Exception) { continue }
            when {
                obj["access_token"] != null -> {
                    val access    = obj["access_token"]!!.jsonPrimitive.content
                    val refresh   = obj["refresh_token"]?.jsonPrimitive?.content
                        ?: return@withContext false
                    val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
                    tokenStore.save(access, refresh, System.currentTimeMillis() + expiresIn * 1_000L)
                    return@withContext true
                }
                obj["error"]?.jsonPrimitive?.content == "slow_down"             -> intervalMs += 5_000L
                obj["error"]?.jsonPrimitive?.content == "authorization_pending"  -> Unit
                // Terminal errors — user explicitly denied or code expired server-side.
                // Terminal errors — stop immediately regardless of remaining time.
                else -> return@withContext false
            }
        }
        false
    }

    /** Returns a valid access token, refreshing silently if expiring within 60 s. */
    suspend fun validToken(): String? = withContext(Dispatchers.IO) {
        val tokens = tokenStore.getTokens() ?: return@withContext null
        if (tokens.expiresAt > System.currentTimeMillis() + 60_000L) return@withContext tokens.accessToken
        refreshToken(tokens.refreshToken)
    }

    private suspend fun refreshToken(refreshToken: String): String? {
        val body = FormBody.Builder()
            .add("client_id",     BuildConfig.GOOGLE_CLIENT_ID)
            .add("client_secret", BuildConfig.GOOGLE_CLIENT_SECRET)
            .add("refresh_token", refreshToken)
            .add("grant_type",    "refresh_token")
            .build()
        val raw = http.newCall(
            Request.Builder().url(TOKEN_URL).post(body).build()
        ).execute().use { it.body!!.string() }
        val obj = Json.parseToJsonElement(raw).jsonObject
        val access = obj["access_token"]?.jsonPrimitive?.content ?: run {
            tokenStore.clear()   // refresh token révoqué — déconnexion
            return null
        }
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        tokenStore.updateAccess(access, System.currentTimeMillis() + expiresIn * 1_000L)
        return access
    }

    suspend fun logout() = tokenStore.clear()
}

// ── Pure parsing helpers — testable without Context ──────────────────────────

internal fun parseDeviceCodeResponse(raw: String): DeviceFlowData {
    val obj = Json.parseToJsonElement(raw).jsonObject
    return DeviceFlowData(
        deviceCode      = obj["device_code"]!!.jsonPrimitive.content,
        userCode        = obj["user_code"]!!.jsonPrimitive.content,
        verificationUrl = obj["verification_url"]!!.jsonPrimitive.content,
        expiresIn       = obj["expires_in"]!!.jsonPrimitive.content.toInt(),
        interval        = obj["interval"]!!.jsonPrimitive.content.toInt(),
    )
}
