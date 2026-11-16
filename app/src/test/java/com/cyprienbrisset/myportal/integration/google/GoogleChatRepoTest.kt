package com.cyprienbrisset.fukkatsunop.integration.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class GoogleChatRepoTest {

    // ── parseSpaces ────────────────────────────────────────────────────────────

    private val spacesJson = """
    {
      "spaces": [
        {
          "name": "spaces/AAABBB",
          "displayName": "Équipe Dev",
          "type": "ROOM"
        },
        {
          "name": "spaces/CCCDD",
          "displayName": "",
          "type": "DIRECT_MESSAGE"
        },
        {
          "name": "spaces/EEEEE",
          "displayName": "Café virtuel",
          "type": "GROUP_CHAT"
        },
        {
          "name": "spaces/FFFFF",
          "displayName": "Future type",
          "type": "UNKNOWN_TYPE"
        }
      ]
    }
    """.trimIndent()

    @Test fun parseSpaces_room() {
        val spaces = parseSpaces(spacesJson)
        assertEquals(4, spaces.size)
        val room = spaces[0]
        assertEquals("spaces/AAABBB", room.name)
        assertEquals("Équipe Dev", room.displayName)
        assertEquals(ChatSpaceType.ROOM, room.type)
    }

    @Test fun parseSpaces_directMessage() {
        val spaces = parseSpaces(spacesJson)
        val dm = spaces[1]
        assertEquals(ChatSpaceType.DM, dm.type)
        assertEquals("", dm.displayName)
    }

    @Test fun parseSpaces_groupChat() {
        val spaces = parseSpaces(spacesJson)
        assertEquals(ChatSpaceType.GROUP_CHAT, spaces[2].type)
    }

    @Test fun parseSpaces_unknownType() {
        val spaces = parseSpaces(spacesJson)
        assertEquals(ChatSpaceType.UNKNOWN, spaces[3].type)
    }

    @Test fun parseSpaces_emptyOnMissingKey() {
        assertEquals(emptyList<ChatSpace>(), parseSpaces("""{"kind":"chat#listSpacesResponse"}"""))
    }

    @Test fun parseSpaces_skipsEntryWithoutName() {
        val json = """{"spaces":[{"displayName":"No name here","type":"ROOM"}]}"""
        assertEquals(0, parseSpaces(json).size)
    }

    // ── parseMessages ──────────────────────────────────────────────────────────

    private val messagesJson = """
    {
      "messages": [
        {
          "name": "spaces/AAABBB/messages/msg1",
          "text": "Bonjour tout le monde",
          "sender": { "displayName": "Alice" },
          "createTime": "2026-09-08T09:00:00Z"
        },
        {
          "name": "spaces/AAABBB/messages/msg2",
          "text": "Hello",
          "sender": { "displayName": "Bob" },
          "createTime": "2026-09-08T09:05:30Z"
        }
      ]
    }
    """.trimIndent()

    @Test fun parseMessages_basic() {
        val msgs = parseMessages(messagesJson)
        assertEquals(2, msgs.size)
        val m = msgs[0]
        assertEquals("spaces/AAABBB/messages/msg1", m.name)
        assertEquals("Bonjour tout le monde", m.text)
        assertEquals("Alice", m.senderName)
        assertEquals(Instant.parse("2026-09-08T09:00:00Z"), m.createTime)
    }

    @Test fun parseMessages_secondMessage() {
        val msgs = parseMessages(messagesJson)
        assertEquals("Bob", msgs[1].senderName)
        assertEquals(Instant.parse("2026-09-08T09:05:30Z"), msgs[1].createTime)
    }

    @Test fun parseMessages_emptyOnMissingKey() {
        assertEquals(emptyList<ChatMessage>(), parseMessages("""{"kind":"chat#listMessagesResponse"}"""))
    }

    @Test fun parseMessages_skipsEntryWithoutText() {
        val json = """{"messages":[{"name":"spaces/X/messages/y","sender":{"displayName":"A"},"createTime":"2026-09-08T09:00:00Z"}]}"""
        assertEquals(0, parseMessages(json).size)
    }

    @Test fun parseMessages_senderFallbackOnMissingSender() {
        val json = """{"messages":[{"name":"spaces/X/messages/y","text":"Hi","createTime":"2026-09-08T09:00:00Z"}]}"""
        val msgs = parseMessages(json)
        assertEquals(1, msgs.size)
        assertEquals("?", msgs[0].senderName)
    }

    @Test fun parseMessages_skipsEntryWithInvalidTimestamp() {
        val json = """{"messages":[{"name":"spaces/X/messages/y","text":"Hi","sender":{"displayName":"A"},"createTime":"not-a-date"}]}"""
        assertEquals(0, parseMessages(json).size)
    }

    // ── Names of direct messages ───────────────────────────────────────────────

    private fun msg(id: String, name: String) = ChatMessage("m", name, "t", Instant.EPOCH, senderId = id)

    @Test fun parseMessages_keepsAttachmentOnlyMessage() {
        val json = """{"messages":[{"name":"spaces/X/messages/y","sender":{"name":"users/1","displayName":"A"},"attachment":[{}],"createTime":"2026-09-08T09:00:00Z"}]}"""
        val m = parseMessages(json).single()
        assertEquals("", m.text)
        assertEquals("📎 Fichier", m.summary)
        assertEquals("users/1", m.senderId)
    }

    @Test fun parseMessages_richContent() {
        val json = """{"messages":[{
          "name":"spaces/X/messages/r","createTime":"2026-11-16T08:00:00Z","lastUpdateTime":"2026-11-16T08:05:00Z",
          "sender":{"name":"users/1","displayName":"Alice"},
          "text":"Salut @Bob","formattedText":"Salut <users/2> *top*",
          "attachment":[{"contentName":"photo.jpg","contentType":"image/jpeg","source":"UPLOADED_CONTENT",
                         "attachmentDataRef":{"resourceName":"abc/def+g="}}],
          "attachedGifs":[{"uri":"https://media.tenor.com/x.gif"}],
          "emojiReactionSummaries":[{"emoji":{"unicode":"👍"},"reactionCount":3},
                                    {"emoji":{"customEmoji":{"emojiName":":parrot:","temporaryImageUri":"https://e/p.png"}},"reactionCount":1}],
          "annotations":[{"type":"USER_MENTION","userMention":{"user":{"name":"users/2","displayName":"Bob"}}},
                         {"type":"RICH_LINK","richLinkMetadata":{"uri":"https://meet.google.com/abc","richLinkType":"MEET_SPACE","meetSpaceLinkData":{"meetingCode":"abc"}}}],
          "quotedMessageMetadata":{"name":"spaces/X/messages/q"}
        }]}"""
        val m = parseMessages(json).single()
        assertEquals("https://chat.googleapis.com/v1/media/abc/def%2Bg%3D?alt=media", m.attachments.single().downloadUrl)
        assertEquals(true, m.attachments.single().isImage)
        assertEquals(listOf("https://media.tenor.com/x.gif"), m.gifs)
        assertEquals(ChatReaction("👍", null, 3), m.reactions[0])
        assertEquals("https://e/p.png", m.reactions[1].imageUrl)
        assertEquals("Bob", m.mentions["users/2"])
        assertEquals(ChatLinkType.MEET, m.links.single().type)
        assertEquals("spaces/X/messages/q", m.quotes)
        assertEquals(true, m.edited)
    }

    @Test fun parseMessages_keepsDeletedMessage() {
        val json = """{"messages":[{"name":"spaces/X/messages/d","createTime":"2026-11-16T08:00:00Z","deleteTime":"2026-11-16T08:01:00Z","deletionMetadata":{"deletionType":"CREATOR"}}]}"""
        val m = parseMessages(json).single()
        assertEquals(true, m.deleted)
        assertEquals("Message supprimé", m.summary)
    }

    @Test fun parseSpaces_lastActiveTime() {
        val json = """{"spaces":[{"name":"spaces/A","type":"DIRECT_MESSAGE","lastActiveTime":"2026-11-16T08:00:00Z"}]}"""
        assertEquals(Instant.parse("2026-11-16T08:00:00Z"), parseSpaces(json).single().lastActive)
    }

    @Test fun guessMe_isTheSenderPresentInEveryDm() {
        val dms = listOf(
            listOf(msg("users/me", "Moi"), msg("users/a", "Alice")),
            listOf(msg("users/b", "Bob"), msg("users/me", "Moi")),
        )
        val me = guessMe(dms)
        assertEquals("users/me", me)
        assertEquals("Alice", dmTitle(dms[0], me))
        assertEquals("Moi, Alice", dmTitle(dms[0], null))
        assertEquals("Bob", dmTitle(dms[1], me))
    }

    @Test fun guessMe_unknownWithASingleDm() {
        assertNull(guessMe(listOf(listOf(msg("users/me", "Moi"), msg("users/a", "Alice")))))
    }

    @Test fun parseSpaces_spaceTypeWinsOverDeprecatedType() {
        val json = """{"spaces":[{"name":"spaces/A","spaceType":"DIRECT_MESSAGE"},{"name":"spaces/B","spaceType":"SPACE","displayName":"Équipe"}]}"""
        val spaces = parseSpaces(json)
        assertEquals(ChatSpaceType.DM, spaces[0].type)
        assertEquals(ChatSpaceType.ROOM, spaces[1].type)
    }
}
