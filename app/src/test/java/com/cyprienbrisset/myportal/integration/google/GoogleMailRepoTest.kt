package com.cyprienbrisset.fukkatsunop.integration.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMailRepoTest {

    private val listJson = """
    {
      "messages": [
        {"id": "msg1", "threadId": "thr1"},
        {"id": "msg2", "threadId": "thr2"}
      ]
    }
    """.trimIndent()

    private val metaJson = """
    {
      "id": "msg1",
      "threadId": "thr1",
      "labelIds": ["INBOX", "UNREAD"],
      "snippet": "Bonjour, voici le résumé...",
      "internalDate": "1725696000000",
      "payload": {
        "headers": [
          {"name": "From",    "value": "Alice <alice@example.com>"},
          {"name": "Subject", "value": "Test email"},
          {"name": "Date",    "value": "Mon, 7 Sep 2026 10:00:00 +0000"}
        ]
      }
    }
    """.trimIndent()

    private val plainB64 = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("Texte brut".toByteArray())
    private val htmlB64 = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("<b>HTML</b>".toByteArray())

    private val htmlBodyJson: String get() = """
{
  "id": "msg1",
  "threadId": "thr1",
  "labelIds": ["INBOX"],
  "snippet": "Hello",
  "internalDate": "1725696000000",
  "payload": {
    "mimeType": "multipart/alternative",
    "headers": [
      {"name": "From", "value": "Bob <bob@example.com>"},
      {"name": "Subject", "value": "HTML mail"}
    ],
    "parts": [
      {"mimeType": "text/plain", "body": {"data": "$plainB64"}},
      {"mimeType": "text/html",  "body": {"data": "$htmlB64"}}
    ]
  }
}
""".trimIndent()

    @Test fun parseMessageIds_extractsIds() {
        val ids = parseMessageIds(listJson)
        assertEquals(listOf("msg1", "msg2"), ids)
    }

    @Test fun parseMessageMeta_parsesAllFields() {
        val msg = parseMessageMeta(metaJson)
        assertEquals("msg1",                       msg.id)
        assertEquals("Alice <alice@example.com>",  msg.from)
        assertEquals("Test email",                 msg.subject)
        assertEquals("Bonjour, voici le résumé...", msg.snippet)
        assertTrue(msg.isUnread)
    }

    @Test fun parseMessageBody_prefersHtml() {
        val body = parseMessageBody(htmlBodyJson)
        assertNotNull(body.html)
        assertEquals("<b>HTML</b>", body.html)
        assertEquals("Texte brut",  body.text)
    }

    @Test fun parseMessageBody_fallsBackToPlainText() {
        val plainB64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("Plain only".toByteArray())
        val plainOnly = """
        {
          "id": "msg2", "threadId": "t", "labelIds": ["INBOX"],
          "snippet": "s", "internalDate": "0",
          "payload": {
            "mimeType": "text/plain",
            "body": { "data": "$plainB64" },
            "headers": []
          }
        }
        """.trimIndent()
        val body = parseMessageBody(plainOnly)
        assertNull(body.html)
        assertEquals("Plain only", body.text)
    }
}
