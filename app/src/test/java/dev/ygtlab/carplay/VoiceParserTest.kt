package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.Contact
import dev.ygtlab.carplay.data.VoiceCommand
import dev.ygtlab.carplay.data.VoiceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {
    private fun p(s: String) = VoiceParser.parse(s)

    @Test
    fun homeWorkAndStop() {
        assertEquals(VoiceCommand.GoHome, p("Eve git"))
        assertEquals(VoiceCommand.GoHome, p("eve götür"))
        assertEquals(VoiceCommand.GoWork, p("İşe git"))
        assertEquals(VoiceCommand.StopNavigation, p("Navigasyonu bitir"))
    }

    @Test
    fun navigateKeepsPlaceAndTriesWithoutDative() {
        assertEquals(VoiceCommand.NavigateTo(listOf("kadıköy")), p("Kadıköy'e git"))
        assertEquals(VoiceCommand.NavigateTo(listOf("kadıköye", "kadıköy", "kadıkö")), p("Kadıköye git"))
        assertEquals(VoiceCommand.NavigateTo(listOf("ankara", "ankar")), p("Ankara git"))
        assertEquals(VoiceCommand.NavigateTo(listOf("taksim meydanı")), p("rota Taksim meydanı").let { c ->
            // "rota X" has no dative ending to strip
            VoiceCommand.NavigateTo(listOf((c as VoiceCommand.NavigateTo).candidates.first()))
        })
    }

    @Test
    fun callStripsAccusative() {
        assertEquals(VoiceCommand.Call("ali"), p("Ali'yi ara"))
        assertEquals(VoiceCommand.Call("ali"), p("aliyi ara"))
        assertEquals(VoiceCommand.Call("ali"), p("Ali ara"))
        assertEquals(VoiceCommand.Call("ayşe"), p("ara Ayşe"))
    }

    @Test
    fun musicAndVolume() {
        assertEquals(VoiceCommand.MusicPlay, p("Müziği aç"))
        assertEquals(VoiceCommand.MusicPause, p("müziği durdur"))
        assertEquals(VoiceCommand.MusicNext, p("sonraki şarkı"))
        assertEquals(VoiceCommand.MusicPrevious, p("önceki şarkı"))
        assertEquals(VoiceCommand.VolumeUp, p("sesi aç"))
        assertEquals(VoiceCommand.VolumeDown, p("sesi kıs"))
    }

    @Test
    fun stopsAndUnknown() {
        assertEquals(VoiceCommand.FindStops, p("en yakın benzinlik"))
        assertTrue(p("bugün hava nasıl") is VoiceCommand.Unknown)
        assertTrue(p("   ") is VoiceCommand.Unknown)
    }

    @Test
    fun contactMatching() {
        val list = listOf(Contact("Ali Yılmaz", "1"), Contact("Ayşe", "2"), Contact("Annem", "3"))
        assertEquals("Ali Yılmaz", VoiceParser.matchContact("ali", list).first().name)
        assertEquals("Annem", VoiceParser.matchContact("annem", list).first().name)
        assertEquals("Ayşe", VoiceParser.matchContact("ayşe", list).first().name)
        assertTrue(VoiceParser.matchContact("zeynep", list).isEmpty())
    }
}
