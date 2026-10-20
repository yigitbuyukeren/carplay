package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.VoiceHistory
import dev.ygtlab.carplay.data.VoiceHistoryEntry
import dev.ygtlab.carplay.data.WakeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceHistoryTest {
    @Test
    fun keepsTheLastTenNewestFirst() {
        var list = emptyList<VoiceHistoryEntry>()
        for (i in 1..12) list = VoiceHistory.add(list, VoiceHistoryEntry("komut $i", i % 2 == 0, i.toLong()))
        assertEquals(10, list.size)
        assertEquals("komut 12", list.first().heard)
        assertEquals("komut 3", list.last().heard)
    }

    @Test
    fun jsonRoundTripAndBadJson() {
        val list = listOf(VoiceHistoryEntry("eve git", true, 5L), VoiceHistoryEntry("asdf", false, 4L))
        assertEquals(list, VoiceHistory.fromJson(VoiceHistory.toJson(list)))
        assertTrue(VoiceHistory.fromJson("not json").isEmpty())
        assertTrue(VoiceHistory.fromJson(null).isEmpty())
    }

    @Test
    fun theWakePhraseIsRecognisedLoosely() {
        for (p in listOf("hey motodash", "Hey MotoDash", "hey moto dash", "hey motodaş", "tamam hey motodash müziği aç", "hey motodeş")) {
            assertTrue(p, WakeWord.matches(p))
        }
        for (p in listOf("müziği aç", "hey google", "moto kurye", "")) assertFalse(p, WakeWord.matches(p))
    }
}
