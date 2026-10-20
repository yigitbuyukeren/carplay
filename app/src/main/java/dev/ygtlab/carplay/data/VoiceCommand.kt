package dev.ygtlab.carplay.data

import java.util.Locale

/** What the rider asked for by voice. */
sealed class VoiceCommand {
    /** Route to a place; [candidates] are search texts to try in order (the first is the text as heard). */
    data class NavigateTo(val candidates: List<String>) : VoiceCommand()
    object GoHome : VoiceCommand()
    object GoWork : VoiceCommand()
    object StopNavigation : VoiceCommand()
    object FindStops : VoiceCommand()
    data class Call(val name: String) : VoiceCommand()
    object MusicPlay : VoiceCommand()
    object MusicPause : VoiceCommand()
    object MusicNext : VoiceCommand()
    object MusicPrevious : VoiceCommand()
    object VolumeUp : VoiceCommand()
    object VolumeDown : VoiceCommand()
    data class Unknown(val heard: String) : VoiceCommand()
}

/**
 * Turns what the speech recogniser heard (Turkish) into a command. Pure text rules, no Android: the recogniser
 * is noisy on a motorcycle, so only clear phrasings are accepted and everything else is [VoiceCommand.Unknown].
 */
object VoiceParser {
    private val TR = Locale("tr", "TR")
    private val NOISE = Regex("[.,!?\"]")
    private val SPACES = Regex("\\s+")

    fun normalize(raw: String): String =
        raw.lowercase(TR).replace('’', '\'').replace(NOISE, " ").replace(SPACES, " ").trim()

    private val NAVIGATE_VERBS = listOf("git", "gidelim", "götür", "gidebilir miyiz", "gider misin", "rota", "yol tarifi", "navigasyon", "yönlendir")

    fun parse(raw: String): VoiceCommand {
        val t = normalize(raw)
        if (t.isEmpty()) return VoiceCommand.Unknown(raw)

        // Volume first: "sesi aç" must not be taken for "müziği aç".
        if (t.matches(Regex(".*\\bses(i|ini)? (aç|yükselt|artır|arttır)\\b.*")) || t == "sesi yükselt") return VoiceCommand.VolumeUp
        if (t.matches(Regex(".*\\bses(i|ini)? (kıs|azalt|düşür)\\b.*"))) return VoiceCommand.VolumeDown

        if (t.matches(Regex(".*\\b(sonraki|ileri|bir sonraki)( şarkı| parça)?\\b.*")) && !t.contains("git")) return VoiceCommand.MusicNext
        if (t.matches(Regex(".*\\b(önceki|bir önceki|geri)( şarkı| parça)?\\b.*")) && !t.contains("git")) return VoiceCommand.MusicPrevious
        if (t.matches(Regex(".*\\b(müziği|şarkıyı|müzik) ?(durdur|duraklat|kapat|sustur)\\b.*")) || t == "durdur" || t == "duraklat") return VoiceCommand.MusicPause
        if (t.matches(Regex(".*\\b(müziği|şarkıyı|müzik) ?(aç|çal|başlat|devam)\\b.*")) || t == "müzik" || t == "çal" || t == "devam et") return VoiceCommand.MusicPlay

        if (t.matches(Regex(".*\\b(navigasyonu|yönlendirmeyi|rotayı|yolculuğu) (bitir|durdur|kapat|iptal et)\\b.*"))) return VoiceCommand.StopNavigation
        if (t.matches(Regex("^(eve|evime) (git|gidelim|götür|dön)\\b.*")) || t == "ev") return VoiceCommand.GoHome
        if (t.matches(Regex("^(işe|iş yerime|işyerime) (git|gidelim|götür)\\b.*"))) return VoiceCommand.GoWork
        if (t.matches(Regex(".*\\b(benzinlik|benzin|yakıt|şarj|tamirci|tamir noktası|istasyon)\\w*\\b.*")) && !t.contains(" ara ")) return VoiceCommand.FindStops

        // "Ali'yi ara", "annemi ara": the name is everything before the final "ara".
        Regex("^(.+?) (ara|arar mısın|arayalım|aramak istiyorum)$").find(t)?.let { m ->
            val name = stripAccusative(m.groupValues[1])
            if (name.isNotBlank()) return VoiceCommand.Call(name)
        }
        Regex("^(ara|arama yap) (.+)$").find(t)?.let { m -> return VoiceCommand.Call(m.groupValues[2]) }

        for (verb in NAVIGATE_VERBS) {
            val suffix = " $verb"
            if (t.endsWith(suffix) && t.length > suffix.length) {
                return navigate(t.removeSuffix(suffix))
            }
            if (t.startsWith("$verb ") && verb in listOf("rota", "yol tarifi", "navigasyon")) {
                return navigate(t.removePrefix("$verb "))
            }
        }
        return VoiceCommand.Unknown(raw)
    }

    private fun navigate(place: String): VoiceCommand {
        val heard = place.trim()
        if (heard.isEmpty()) return VoiceCommand.Unknown(place)
        val candidates = LinkedHashSet<String>()
        if (heard.contains('\'')) {
            // "Kadıköy'e": the apostrophe marks the suffix exactly.
            candidates.add(heard.substringBefore('\''))
        } else {
            candidates.add(heard)
            // "Kadıköye git": the recogniser often drops the apostrophe; also try without a dative ending.
            if (heard.length > 3 && heard.last() in "ea") {
                candidates.add(heard.dropLast(1))
                if (heard[heard.length - 2] == 'y') candidates.add(heard.dropLast(2))
            }
        }
        return VoiceCommand.NavigateTo(candidates.map { it.trim() }.filter { it.isNotEmpty() })
    }

    /** "ali'yi" → "ali", "aliyi" → "ali", "annemi" → "annem"; an apostrophe is trusted, the bare ending only after y. */
    private fun stripAccusative(name: String): String {
        val n = name.trim()
        if (n.contains('\'')) return n.substringBefore('\'')
        return Regex("^(.{2,})y[iıuü]$").find(n)?.groupValues?.get(1) ?: n
    }

    /** Best contact for a heard name: exact, then a contact whose name starts with it or the other way round. */
    fun matchContact(heard: String, contacts: List<Contact>): List<Contact> {
        val q = normalize(heard)
        if (q.isEmpty()) return emptyList()
        fun norm(c: Contact) = normalize(c.name)
        val exact = contacts.filter { norm(it) == q }
        if (exact.isNotEmpty()) return exact
        val prefix = contacts.filter { val n = norm(it); n.startsWith(q) || q.startsWith(n.substringBefore(' ')) && n.substringBefore(' ').length >= 3 }
        if (prefix.isNotEmpty()) return prefix
        return contacts.filter { norm(it).contains(q) }
    }
}
