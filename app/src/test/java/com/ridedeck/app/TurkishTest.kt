package com.ridedeck.app

import com.ridedeck.app.data.Turkish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishTest {
    @Test
    fun normalizeUsesTurkishCase() {
        assertEquals("ısparta ve izmir", Turkish.normalize("ISPARTA ve İzmir!"))
        assertEquals("ali'yi ara", Turkish.normalize("  Ali’yi   ARA. "))
    }

    @Test
    fun foldRemovesTurkishLetters() {
        assertEquals("kadikoy", Turkish.fold("Kadıköy"))
        assertEquals("sisli cigli", Turkish.fold("Şişli Çiğli"))
    }

    @Test
    fun apostropheMarksTheEndingExactly() {
        assertEquals(listOf("ali", "aliyi"), Turkish.nameCandidates("Ali'yi"))
        assertEquals("kadıköy", Turkish.nameCandidates("Kadıköy'e").first())
    }

    @Test
    fun withoutApostropheEveryEndingIsAGuess() {
        val c = Turkish.nameCandidates("kadıköye")
        assertTrue("kadıköy" in c)
        assertTrue("kadıkö" in c)
        assertEquals("kadıköye", c.last())
        assertTrue("ankara" in Turkish.nameCandidates("ankaraya"))
        assertTrue("annem" in Turkish.nameCandidates("annemi"))
    }

    @Test
    fun similarityIsLooseAboutAccentsAndTypos() {
        assertEquals(1.0, Turkish.similarity("Kadıköy", "kadikoy"), 0.0001)
        assertTrue(Turkish.similarity("ayşe", "ayse") > 0.99)
        assertTrue(Turkish.similarity("mehmet", "mehmed") > 0.8)
        assertTrue(Turkish.similarity("ali", "zeynep") < 0.4)
    }

    @Test
    fun bestMatchesFindsNamesDespiteEndingsAndTypos() {
        val names = listOf("Ali Yılmaz", "Ali Kaya", "Ayşe Demir", "Annem", "Mehmet Öz")
        assertEquals(setOf("Ali Yılmaz", "Ali Kaya"), Turkish.bestMatches("ali", names) { it }.toSet())
        assertEquals(listOf("Annem"), Turkish.bestMatches("annemi", names) { it })
        assertEquals(listOf("Ayşe Demir"), Turkish.bestMatches("ayse", names) { it })
        assertEquals(listOf("Mehmet Öz"), Turkish.bestMatches("mehmed", names) { it })
        assertTrue(Turkish.bestMatches("zeynep", names) { it }.isEmpty())
    }

    @Test
    fun numbersFromWords() {
        assertEquals(50, Turkish.parseNumber("elli"))
        assertEquals(25, Turkish.parseNumber("yirmi beş"))
        assertEquals(100, Turkish.parseNumber("yüz"))
        assertEquals(12, Turkish.parseNumber("12"))
        assertNull(Turkish.parseNumber("kırk elma"))
    }

    @Test
    fun digitsFromWordsAndFigures() {
        assertEquals("05363482995", Turkish.parseDigits("sıfır beş üç altı üç dört sekiz iki dokuz dokuz beş"))
        assertEquals("05363482995", Turkish.parseDigits("0536 348 2995"))
        assertNull(Turkish.parseDigits("ali"))
    }
}
