package com.ridedeck.app

import com.ridedeck.app.ui.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {
    @Test fun shiftUsesTurkishCasing() {
        assertEquals("İ", KeyboardLayout.cased("i", true))
        assertEquals("I", KeyboardLayout.cased("ı", true))
        assertEquals("ç", KeyboardLayout.cased("ç", false))
    }

    @Test fun appendAndBackspace() {
        val typed = KeyboardLayout.append(KeyboardLayout.append("", "i", true), "s", false)
        assertEquals("İs", typed)
        assertEquals("İ", KeyboardLayout.backspace(typed))
        assertEquals("", KeyboardLayout.backspace(""))
    }

    @Test fun appendStopsAtTheLimit() = assertEquals("abc", KeyboardLayout.append("abc", "d", false, max = 3))

    @Test fun capitalisesAtStartAndAfterASpace() {
        assertTrue(KeyboardLayout.autoShift(""))
        assertTrue(KeyboardLayout.autoShift("ankara "))
        assertFalse(KeyboardLayout.autoShift("ankara"))
    }

    @Test fun everyTurkishLetterIsOnTheKeyboard() {
        val all = KeyboardLayout.letters.joinToString("")
        "ğüşıöç".forEach { assertTrue("$it missing", it in all) }
        assertEquals(32, all.length) // 26 latin letters, with i and ı, plus ğ ü ş ö ç
    }
}
