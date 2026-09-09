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
)

enum class ChatSpaceType { DM, ROOM, GROUP_CHAT, UNKNOWN }

data class ChatMessage(
    val name: String,
    val senderName: String,
    val text: String,
    val createTime: Instant,
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

    suspend fun fetchMessages(spaceName: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val token = authManager.validToken()
            ?: throw Exception("Non connecté")
        val response = http.newCall(
            Request.Builder()
                .url("$CHAT_BASE/$spaceName/messages?pageSize=50&orderBy=createTime+desc")
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
        ChatSpace(name = name, displayName = displayName, type = type)
    }
}

internal fun parseMessages(raw: String): List<ChatMessage> {
    val items = Json.parseToJsonElement(raw).jsonObject["messages"]?.jsonArray
        ?: return emptyList()
    return items.mapNotNull { el ->
        val obj = el.jsonObject
        val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val text = obj["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val senderName = obj["sender"]?.jsonObject
            ?.get("displayName")?.jsonPrimitive?.content ?: "?"
        val timeStr = obj["createTime"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val createTime = runCatching { Instant.parse(timeStr) }.getOrNull() ?: return@mapNotNull null
        ChatMessage(name = name, senderName = senderName, text = text, createTime = createTime)
    }
}
