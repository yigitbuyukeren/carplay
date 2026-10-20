package com.ridedeck.app

import com.ridedeck.app.data.CallKind
import com.ridedeck.app.data.Contact
import com.ridedeck.app.data.PhoneMath
import com.ridedeck.app.data.RecentCall
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

    @Test fun contactsListStarredFirstThenAlphabetical() {
        val out = PhoneMath.contacts(
            listOf(Contact("Zeynep", "3"), Contact("Çağla", "4"), Contact("Ali", "1"), Contact("Veli", "5", starred = true)),
        )
        assertEquals(listOf("Veli", "Ali", "Çağla", "Zeynep"), out.map { it.name })
    }

    @Test fun contactsKeepEachDistinctNumberOfAPerson() {
        val out = PhoneMath.contacts(
            listOf(Contact("Ali", "0532 111"), Contact("Ali", "0532111"), Contact("Ali", "0212 222")),
        )
        assertEquals(listOf("0532 111", "0212 222"), out.map { it.number })
    }

    @Test fun contactsSkipBlankNumbersAndNames() {
        assertEquals(emptyList<Contact>(), PhoneMath.contacts(listOf(Contact("Can", " "), Contact(" ", "5"))))
    }

    @Test fun initialIsUppercaseLetterOrHash() {
        assertEquals('A', PhoneMath.initialOf("ali"))
        assertEquals('#', PhoneMath.initialOf("1-800"))
        assertEquals('#', PhoneMath.initialOf(""))
    }

    @Test fun keyIgnoresCountryPrefixAndLeadingZero() {
        assertEquals(PhoneMath.key("0532 123 45 67"), PhoneMath.key("+90 532 123 45 67"))
        assertEquals("5321234567", PhoneMath.key("0090 532 123 45 67"))
    }

    @Test fun matchesNeedAtLeastThreeDigits() {
        assertEquals(emptyList<Contact>(), PhoneMath.matches("05", listOf(Contact("Ali", "0532 111 22 33")), emptyList()))
    }

    @Test fun matchesPutNumbersStartingWithTheTypedDigitsFirst() {
        val out = PhoneMath.matches(
            "532",
            listOf(Contact("Zeki", "0212 532 00 00"), Contact("Ali", "0532 111 22 33")),
            emptyList(),
        )
        assertEquals(listOf("Ali", "Zeki"), out.map { it.name })
    }

    @Test fun matchesUseNamesFromRecentCallsAndDropDuplicates() {
        val out = PhoneMath.matches(
            "850",
            listOf(Contact("Ofis", "0850 302 94 40")),
            listOf(RecentCall("+908503029440", "Kalyoncu", CallKind.OUTGOING, 1L), RecentCall("0850111", "Banka", CallKind.INCOMING, 2L)),
        )
        assertEquals(listOf("Ofis", "Banka"), out.map { it.name })
    }
}
