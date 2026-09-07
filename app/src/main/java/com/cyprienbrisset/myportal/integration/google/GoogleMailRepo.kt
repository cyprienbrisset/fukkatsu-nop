package com.cyprienbrisset.myportal.integration.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.Base64

data class MailMessage(
    val id: String,
    val threadId: String,
    val from: String,
    val subject: String,
    val date: Instant,
    val snippet: String,
    val isUnread: Boolean,
)

data class MailBody(
    val html: String?,
    val text: String?,
)

private const val GMAIL_BASE = "https://www.googleapis.com/gmail/v1/users/me"

class GoogleMailRepo(
    private val authManager: GoogleAuthManager,
    private val http: OkHttpClient = OkHttpClient(),
) {
    suspend fun listInbox(): List<MailMessage> = withContext(Dispatchers.IO) {
        val token = authManager.validToken() ?: return@withContext emptyList()
        val listResponse = http.newCall(
            Request.Builder()
                .url("$GMAIL_BASE/messages?labelIds=INBOX&maxResults=20")
                .header("Authorization", "Bearer $token")
                .build()
        ).execute()
        val listRaw = listResponse.use {
            if (!it.isSuccessful) return@withContext emptyList()
            it.body?.string() ?: return@withContext emptyList()
        }
        val ids = parseMessageIds(listRaw)
        ids.mapNotNull { id ->
            val metaResponse = http.newCall(
                Request.Builder()
                    .url("$GMAIL_BASE/messages/$id?format=metadata&metadataHeaders=From,Subject,Date")
                    .header("Authorization", "Bearer $token")
                    .build()
            ).execute()
            val metaRaw = metaResponse.use {
                if (!it.isSuccessful) return@mapNotNull null
                it.body?.string() ?: return@mapNotNull null
            }
            runCatching { parseMessageMeta(metaRaw) }.getOrNull()
        }
    }

    suspend fun fetchBody(id: String): MailBody = withContext(Dispatchers.IO) {
        val token = authManager.validToken() ?: return@withContext MailBody(null, null)
        val response = http.newCall(
            Request.Builder()
                .url("$GMAIL_BASE/messages/$id?format=full")
                .header("Authorization", "Bearer $token")
                .build()
        ).execute()
        val raw = response.use {
            if (!it.isSuccessful) return@withContext MailBody(null, null)
            it.body?.string() ?: return@withContext MailBody(null, null)
        }
        parseMessageBody(raw)
    }
}

// ── Pure helpers ─────────────────────────────────────────────────────────────

internal fun parseMessageIds(raw: String): List<String> {
    val arr = Json.parseToJsonElement(raw).jsonObject["messages"]?.jsonArray
        ?: return emptyList()
    return arr.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
}

internal fun parseMessageMeta(raw: String): MailMessage {
    val obj      = Json.parseToJsonElement(raw).jsonObject
    val id       = obj["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("missing id")
    val threadId = obj["threadId"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("missing threadId")
    val snippet  = obj["snippet"]?.jsonPrimitive?.content ?: ""
    val isUnread = obj["labelIds"]?.jsonArray?.any { it.jsonPrimitive.content == "UNREAD" } ?: false
    val dateMs   = obj["internalDate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
    val headers  = obj["payload"]?.jsonObject?.get("headers")?.jsonArray ?: JsonArray(emptyList())
    fun header(name: String) = headers.firstOrNull {
        it.jsonObject["name"]?.jsonPrimitive?.content.equals(name, ignoreCase = true)
    }?.jsonObject?.get("value")?.jsonPrimitive?.content ?: ""
    return MailMessage(
        id       = id,
        threadId = threadId,
        from     = header("From"),
        subject  = header("Subject"),
        date     = Instant.ofEpochMilli(dateMs),
        snippet  = snippet,
        isUnread = isUnread,
    )
}

internal fun parseMessageBody(raw: String): MailBody {
    val payload = Json.parseToJsonElement(raw).jsonObject["payload"]?.jsonObject
        ?: return MailBody(null, null)
    var html: String? = null
    var text: String? = null
    extractMimeParts(payload) { mimeType, data ->
        when {
            mimeType == "text/html"  && html == null -> html = decodeBase64(data)
            mimeType == "text/plain" && text == null -> text = decodeBase64(data)
        }
    }
    return MailBody(html, text)
}

internal fun extractMimeParts(
    part: JsonObject,
    onPart: (mimeType: String, data: String) -> Unit,
) {
    val mimeType = part["mimeType"]?.jsonPrimitive?.content ?: return
    val data = part["body"]?.jsonObject?.get("data")?.jsonPrimitive?.content
    if (data != null && data.isNotEmpty()) onPart(mimeType, data)
    part["parts"]?.jsonArray?.forEach { child ->
        extractMimeParts(child.jsonObject, onPart)
    }
}

private fun decodeBase64(data: String): String =
    String(
        Base64.getUrlDecoder().decode(data.filter { !it.isWhitespace() }),
        Charsets.UTF_8,
    )
