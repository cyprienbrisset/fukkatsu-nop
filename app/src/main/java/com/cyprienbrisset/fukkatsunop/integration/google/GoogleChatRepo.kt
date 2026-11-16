package com.cyprienbrisset.fukkatsunop.integration.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    /** Chat markup (*bold*, _italic_, <url|label>, <users/1>, :emoji:…), when Google sends it. */
    val formattedText: String? = null,
    val attachments: List<ChatAttachment> = emptyList(),
    /** GIFs picked from the Chat GIF search, by URL. */
    val gifs: List<String> = emptyList(),
    val reactions: List<ChatReaction> = emptyList(),
    /** ":name:" → image URL of the space's custom emoji used in the text. */
    val customEmojis: Map<String, String> = emptyMap(),
    /** "users/1" → display name, from the mentions. */
    val mentions: Map<String, String> = emptyMap(),
    val links: List<ChatLink> = emptyList(),
    /** Bot cards: header title and subtitle. */
    val cards: List<String> = emptyList(),
    /** Name of the message this one replies to. */
    val quotes: String? = null,
    val deleted: Boolean = false,
    val edited: Boolean = false,
) {
    /** One line for the conversation list. */
    val summary: String get() = when {
        deleted -> "Message supprimé"
        text.isNotBlank() -> text
        gifs.isNotEmpty() -> "GIF"
        attachments.any { it.isImage } -> "📷 Photo"
        attachments.any { it.isVideo } -> "🎬 Vidéo"
        attachments.isNotEmpty() -> "📎 " + attachments.first().contentName
        cards.isNotEmpty() -> cards.first()
        else -> ""
    }
}

data class ChatAttachment(
    val contentName: String,
    val contentType: String,
    /** For media.download; null for Google Drive files, which the Chat API cannot download. */
    val resourceName: String?,
    val fromDrive: Boolean,
) {
    val isImage get() = contentType.startsWith("image/")
    val isVideo get() = contentType.startsWith("video/")
    /** Bytes of an uploaded file, readable with the Chat token (chat.messages.readonly). */
    val downloadUrl: String? get() = resourceName?.let {
        "$CHAT_BASE/media/" + it.split('/').joinToString("/") { seg ->
            java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        } + "?alt=media"
    }
}

/** An emoji and how many people reacted with it; custom emoji come as an image. */
data class ChatReaction(val emoji: String, val imageUrl: String?, val count: Int)

enum class ChatLinkType { DRIVE, MEET, CALENDAR, CHAT, MAIL, OTHER }
data class ChatLink(val type: ChatLinkType, val uri: String, val detail: String? = null)

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
        // spaceType is the current field; type (DM / ROOM) is deprecated and often missing.
        val type = when (obj["spaceType"]?.jsonPrimitive?.content ?: obj["type"]?.jsonPrimitive?.content) {
            "DIRECT_MESSAGE", "DM" -> ChatSpaceType.DM
            "SPACE", "ROOM"        -> ChatSpaceType.ROOM
            "GROUP_CHAT"           -> ChatSpaceType.GROUP_CHAT
            else                   -> ChatSpaceType.UNKNOWN
        }
        val lastActive = obj["lastActiveTime"]?.jsonPrimitive?.content?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ChatSpace(name = name, displayName = displayName, type = type, lastActive = lastActive)
    }
}

internal fun parseMessages(raw: String): List<ChatMessage> {
    val items = Json.parseToJsonElement(raw).jsonObject["messages"]?.jsonArray
        ?: return emptyList()
    return items.mapNotNull { el -> runCatching { parseMessage(el.jsonObject) }.getOrNull() }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.arr(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

private fun parseMessage(obj: JsonObject): ChatMessage? {
    val name = obj.str("name") ?: return null
    val createTime = obj.str("createTime")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val sender = obj.obj("sender")
    val senderName = sender?.str("displayName")?.takeIf { it.isNotBlank() } ?: "?"
    val senderId = sender?.str("name") ?: ""

    val attachments = obj.arr("attachment").map { a ->
        ChatAttachment(
            contentName = a.str("contentName") ?: "Fichier",
            contentType = a.str("contentType") ?: "",
            resourceName = a.obj("attachmentDataRef")?.str("resourceName"),
            fromDrive = a.str("source") == "DRIVE_FILE" || a.obj("driveDataRef") != null,
        )
    }
    val gifs = obj.arr("attachedGifs").mapNotNull { it.str("uri") }
    val reactions = obj.arr("emojiReactionSummaries").mapNotNull { r ->
        val emoji = r.obj("emoji") ?: return@mapNotNull null
        val custom = emoji.obj("customEmoji")
        val label = emoji.str("unicode") ?: custom?.str("emojiName") ?: return@mapNotNull null
        ChatReaction(label, custom?.str("temporaryImageUri"), r.str("reactionCount")?.toIntOrNull() ?: 1)
    }
    val customEmojis = mutableMapOf<String, String>()
    val mentions = mutableMapOf<String, String>()
    val links = mutableListOf<ChatLink>()
    for (a in obj.arr("annotations")) {
        when (a.str("type")) {
            "CUSTOM_EMOJI" -> a.obj("customEmojiMetadata")?.obj("customEmoji")?.let { e ->
                val n = e.str("emojiName"); val img = e.str("temporaryImageUri")
                if (n != null && img != null) customEmojis[n] = img
            }
            "USER_MENTION" -> a.obj("userMention")?.obj("user")?.let { u ->
                val id = u.str("name"); val dn = u.str("displayName")
                if (id != null && !dn.isNullOrBlank()) mentions[id] = dn
            }
            "RICH_LINK" -> a.obj("richLinkMetadata")?.let { m ->
                val uri = m.str("uri") ?: return@let
                links += when (m.str("richLinkType")) {
                    "DRIVE_FILE" -> ChatLink(ChatLinkType.DRIVE, uri, m.obj("driveLinkData")?.str("mimeType"))
                    "MEET_SPACE" -> ChatLink(ChatLinkType.MEET, uri, m.obj("meetSpaceLinkData")?.str("meetingCode"))
                    "CALENDAR_EVENT" -> ChatLink(ChatLinkType.CALENDAR, uri)
                    "CHAT_SPACE" -> ChatLink(ChatLinkType.CHAT, uri)
                    "GMAIL_MESSAGE" -> ChatLink(ChatLinkType.MAIL, uri)
                    else -> ChatLink(ChatLinkType.OTHER, uri)
                }
            }
        }
    }
    val cards = obj.arr("cardsV2").mapNotNull { c ->
        val header = c.obj("card")?.obj("header") ?: return@mapNotNull null
        listOfNotNull(header.str("title"), header.str("subtitle")).joinToString(" · ").takeIf { it.isNotBlank() }
    }
    val deleted = obj.str("deleteTime") != null || obj.obj("deletionMetadata") != null
    val text = obj.str("text") ?: ""
    if (!deleted && text.isBlank() && attachments.isEmpty() && gifs.isEmpty() && cards.isEmpty()) return null
    val updated = obj.str("lastUpdateTime")?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return ChatMessage(
        name = name,
        senderName = senderName,
        text = if (deleted) "" else text,
        createTime = createTime,
        senderId = senderId,
        formattedText = obj.str("formattedText")?.takeIf { it.isNotBlank() && !deleted },
        attachments = attachments,
        gifs = gifs,
        reactions = reactions,
        customEmojis = customEmojis,
        mentions = mentions,
        links = links,
        cards = cards,
        quotes = obj.obj("quotedMessageMetadata")?.str("name"),
        deleted = deleted,
        edited = !deleted && updated != null && updated.epochSecond - createTime.epochSecond > 5,
    )
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

/**
 * Title of a space Google left unnamed (direct message or group chat): the other people,
 * from their latest messages. Without knowing "me", every name, me included.
 */
fun dmTitle(messages: List<ChatMessage>, me: String?): String? =
    messages.filter { it.senderName != "?" && (me == null || it.senderId != me) }
        .map { it.senderName }.distinct().take(3)
        .takeIf { it.isNotEmpty() }?.joinToString(", ")
