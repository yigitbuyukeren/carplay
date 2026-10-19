package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.CallKind
import dev.ygtlab.carplay.data.Favorite
import dev.ygtlab.carplay.data.PhoneMath
import dev.ygtlab.carplay.data.RecentCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneTest {
    private fun call(number: String, at: Long, kind: CallKind = CallKind.INCOMING) = RecentCall(number, null, kind, at)

    @Test fun logTypesMapToKinds() {
        assertEquals(CallKind.INCOMING, PhoneMath.kindOf(1))
        assertEquals(CallKind.OUTGOING, PhoneMath.kindOf(2))
        assertEquals(CallKind.MISSED, PhoneMath.kindOf(3))
        assertEquals(CallKind.MISSED, PhoneMath.kindOf(5))
        assertNull(PhoneMath.kindOf(4))
    }

    @Test fun collapseMergesBackToBackCallsOfOneNumber() {
        val out = PhoneMath.collapse(listOf(call("1", 5), call("1", 4), call("2", 3), call("1", 2)))
        assertEquals(listOf("1" to 2, "2" to 1, "1" to 1), out.map { it.number to it.count })
        assertEquals(5L, out[0].atMs)
    }

    @Test fun collapseKeepsEmptyListEmpty() = assertEquals(emptyList<RecentCall>(), PhoneMath.collapse(emptyList()))

    @Test fun keypadBuildsAndErasesNumber() {
        var s = ""
        for (k in "0532") s = PhoneMath.append(s, k)
        assertEquals("0532", s)
        assertEquals("053", PhoneMath.backspace(s))
        assertEquals("", PhoneMath.backspace(""))
    }

    @Test fun keypadIgnoresUnknownKeysAndOverflow() {
        assertEquals("1", PhoneMath.append("1", 'a'))
        val full = "1".repeat(PhoneMath.MAX_DIGITS)
        assertEquals(full, PhoneMath.append(full, '2'))
    }

    @Test fun dialableDropsFormatting() = assertEquals("+905321234567", PhoneMath.dialable("+90 (532) 123-45 67"))

    @Test fun dialableOfJunkIsEmpty() = assertEquals("", PhoneMath.dialable("abc"))

    @Test fun favoritesKeepFirstNumberPerNameSortedAndSkipBlank() {
        val out = PhoneMath.favorites(
            listOf(Favorite("Zeynep", "3"), Favorite("ali", "1"), Favorite("ali", "2"), Favorite("Can", " ")),
        )
        assertEquals(listOf("ali" to "1", "Zeynep" to "3"), out.map { it.name to it.number })
    }
}
