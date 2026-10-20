package com.ridedeck.app

import com.ridedeck.app.util.simplifyMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTextTest {
    @Test
    fun linksBecomeTheLinkWord() {
        assertEquals("bak link tamam", simplifyMessage("bak https://example.com/a?b=1 tamam", "link"))
        assertEquals("link", simplifyMessage("www.example.com", "link"))
    }

    @Test
    fun emojiAreRemovedAndWhitespaceCollapsed() {
        assertEquals("tamam geliyorum", simplifyMessage("tamam 😀👍  geliyorum\n", "link"))
    }

    @Test
    fun turkishLettersSurvive() {
        assertEquals("İyi akşamlar şöför ğ", simplifyMessage("İyi akşamlar şöför ğ", "link"))
    }

    @Test
    fun longMessagesAreCutAtAWordBoundary() {
        val text = "kelime ".repeat(100)
        val out = simplifyMessage(text, "link", maxChars = 50)
        assertTrue(out.endsWith("…"))
        assertTrue(out.length <= 51)
        assertTrue(out.removeSuffix("…").endsWith("kelime"))
    }

    @Test
    fun shortMessagesAreUntouched() {
        assertEquals("selam", simplifyMessage("selam", "link"))
    }
}
