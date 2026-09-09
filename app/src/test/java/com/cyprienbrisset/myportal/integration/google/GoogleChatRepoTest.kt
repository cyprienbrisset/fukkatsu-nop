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
}
