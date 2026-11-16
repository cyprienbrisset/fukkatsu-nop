package com.cyprienbrisset.fukkatsunop.ui.google

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMarkupTest {
    private fun render(s: String, mentions: Map<String, String> = emptyMap(), emojis: Set<String> = emptySet()) =
        chatMarkup(s, mentions, emojis, Color.Blue)

    @Test fun markupIsRemovedAndStyled() {
        val t = render("Salut <users/2>, *rdv* à <https://x.fr|la salle> _demain_")
        assertEquals("Salut @Bob, rdv à la salle demain", render("Salut <users/2>, *rdv* à <https://x.fr|la salle> _demain_", mapOf("users/2" to "Bob")).text)
        assertEquals("Salut @quelqu'un, rdv à la salle demain", t.text)
        val bold = render("*rdv*").spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
    }

    @Test fun bulletsAndKnownCustomEmoji() {
        assertEquals("•  Midi\n•  15 h", render("* Midi\n- 15 h").text)
        // A known custom emoji becomes an inline image placeholder; an unknown one stays as text.
        assertEquals(":parrot: :x:", render(":parrot: :x:", emojis = setOf(":parrot:")).text)
        assertEquals(1, render(":parrot: :x:", emojis = setOf(":parrot:")).getStringAnnotations(0, 20).size)
    }

    @Test fun plainAsterisksInsideWordsStayAsIs() {
        assertEquals("2*3*4 = 24", render("2*3*4 = 24").text)
    }
}
