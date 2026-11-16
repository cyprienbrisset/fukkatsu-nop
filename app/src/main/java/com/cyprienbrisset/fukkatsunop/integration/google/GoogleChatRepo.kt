package com.cyprienbrisset.fukkatsunop.integration.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

data class ChatSpace(
    val name: String,          // "spaces/AAAA"
    val displayName: String,   // vide pour DM
    val type: ChatSpaceType,
    /** Last message in the space, to sort conversations like a messaging app. */
    val lastActive: Instant? = null,
)

enum class ChatSpaceType { DM, ROOM, GROUP_CHAT, UNKNOWN }

data class ChatMessage(
    val name: String,
    val senderName: String,
    val text: String,
    val createTime: Instant,
    /** "users/123": tells who wrote it even when Google leaves the display name out. */
    val senderId: String = "",
)

private const val CHAT_BASE = "https://chat.googleapis.com/v1"

class GoogleChatRepo(
    private val authManager: ChatAuthManager,
    private val http: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetchSpaces(): List<ChatSpace> = withContext(Dispatchers.IO) {
        val token = authManager.validToken()
            ?: throw Exception("Non connecté")
        val response = http.newCall(
            Request.Builder()
                .url("$CHAT_BASE/spaces?pageSize=50")
                .header("Authorization", "Bearer $token")
                .build()
        ).execute()
        val raw = response.use {
            if (it.code == 403) throw Exception("403")
            if (!it.isSuccessful) throw Exception("Erreur ${it.code}")
            it.body?.string() ?: return@withContext emptyList()
        }
        parseSpaces(raw)
    }

    /** Newest first. */
    suspend fun fetchMessages(spaceName: String, pageSize: Int = 50): List<ChatMessage> = withContext(Dispatchers.IO) {
        val token = authManager.validToken()
            ?: throw Exception("Non connecté")
        val response = http.newCall(
            Request.Builder()
                .url("$CHAT_BASE/$spaceName/messages?pageSize=$pageSize&orderBy=createTime+desc")
                .header("Authorization", "Bearer $token")
                .build()
        ).execute()
        val raw = response.use {
            if (it.code == 403) throw Exception("403")
            if (!it.isSuccessful) throw Exception("Erreur ${it.code}")
            it.body?.string() ?: return@withContext emptyList()
        }
        parseMessages(raw)
    }
}

internal fun parseSpaces(raw: String): List<ChatSpace> {
    val items = Json.parseToJsonElement(raw).jsonObject["spaces"]?.jsonArray
        ?: return emptyList()
    return items.mapNotNull { el ->
        val obj = el.jsonObject
        val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val displayName = obj["displayName"]?.jsonPrimitive?.content ?: ""
        val type = when (obj["type"]?.jsonPrimitive?.content) {
            "DIRECT_MESSAGE" -> ChatSpaceType.DM
            "ROOM"           -> ChatSpaceType.ROOM
            "GROUP_CHAT"     -> ChatSpaceType.GROUP_CHAT
            else             -> ChatSpaceType.UNKNOWN
        }
        val lastActive = obj["lastActiveTime"]?.jsonPrimitive?.content?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ChatSpace(name = name, displayName = displayName, type = type, lastActive = lastActive)
    }
}

internal fun parseMessages(raw: String): List<ChatMessage> {
    val items = Json.parseToJsonElement(raw).jsonObject["messages"]?.jsonArray
        ?: return emptyList()
    return items.mapNotNull { el ->
        val obj = el.jsonObject
        val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        // A photo or file sent alone has no text: show it as such rather than dropping it.
        val text = obj["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: if (obj["attachment"] != null) "Pièce jointe" else return@mapNotNull null
        val sender = obj["sender"]?.jsonObject
        val senderName = sender?.get("displayName")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "?"
        val senderId = sender?.get("name")?.jsonPrimitive?.content ?: ""
        val timeStr = obj["createTime"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val createTime = runCatching { Instant.parse(timeStr) }.getOrNull() ?: return@mapNotNull null
        ChatMessage(name = name, senderName = senderName, text = text, createTime = createTime, senderId = senderId)
    }
}

/** What the conversation list shows for a space: a readable title and its last message. */
data class ChatPreview(val title: String?, val last: ChatMessage?)

/**
 * Google gives no name to a direct-message space and, under user sign-in, may leave out
 * who "me" is. I am the one person writing in every direct message: the sender seen in
 * the most DM spaces. Needs at least two DMs where both sides wrote, otherwise null.
 */
fun guessMe(dmMessages: Collection<List<ChatMessage>>): String? {
    val counts = dmMessages
        .flatMap { msgs -> msgs.map { it.senderId }.filter { it.isNotEmpty() }.distinct() }
        .groupingBy { it }.eachCount()
    val best = counts.maxByOrNull { it.value } ?: return null
    return best.key.takeIf { best.value >= 2 && counts.values.count { it == best.value } == 1 }
}

/** The other person of a direct message; without knowing "me", both names. */
fun dmTitle(messages: List<ChatMessage>, me: String?): String? {
    val named = messages.filter { it.senderName != "?" }
    if (me != null) return named.firstOrNull { it.senderId != me }?.senderName
    return named.map { it.senderName }.distinct().takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
