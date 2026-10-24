package com.ridedeck.app.data

import com.ridedeck.app.R

/** A guess from the loose pass: the command, and the question to ask when only part of it was heard. */
data class LooseMatch(val command: VoiceCommand, val askRes: Int)

/**
 * The second pass, after the exact phrase table: the sentence is cut into words and checked for an intent keyword plus
 * a cue word ("hava" + "nasıl"), in any order and with any other words around them. Only harmless commands live here
 * (information, music, volume, screens, route questions): nothing that calls, sends or starts an alarm is guessed.
 * When two different intents fit equally well the result is "not understood" rather than a coin toss.
 */
object VoiceLoose {
    private class LRule(val priority: Int, val groups: List<Set<String>>, val askRes: Int, val build: () -> VoiceCommand)

    private fun rule(priority: Int, ask: Int, vararg groups: Set<String>, build: () -> VoiceCommand) =
        LRule(priority, groups.toList(), ask, build)

    private fun w(vararg words: String): Set<String> = words.toSet()

    private val WEATHER_TOPIC = w("hava", "havanın", "havayı", "havada", "yağmur", "yağmuru", "yağış", "yağışlı", "derece", "sıcaklık", "rüzgar", "rüzgâr", "kar")
    private val WEATHER_CUE = w("nasıl", "durumu", "durum", "olacak", "olur", "yağacak", "yağar", "yağıyor", "var", "kaç", "söyle", "ne", "bak", "nedir", "mı", "mi", "mu", "mü", "söylesene")
    private val TOMORROW = w("yarın", "yarına", "yarınki")
    private val EVENING = w("akşam", "akşama", "akşamki", "gece", "geceye")
    private val SPEED_ASK = w("kaç", "ne", "söyle", "nedir", "kadar", "söylesene")
    private val SWITCH = w("mod", "modu", "moduna", "moda", "tema", "temaya", "temayı", "geç")

    private val NEARBY_CUE = w("yakın", "yakında", "yakınımda", "nerede", "bul", "lazım", "göster", "arıyorum", "ara")
    private val NEARBY_SUFFIXES = listOf("", "yi", "yı", "ler", "leri", "ye", "ya", "si", "sı", "e", "a", "i", "ı")

    private val rules: List<LRule> by lazy {
        val base = mutableListOf(
            // Information
            rule(3, R.string.voice_ask_weather_tomorrow, WEATHER_TOPIC, TOMORROW, WEATHER_CUE) { VoiceCommand.Info(InfoQuestion.WEATHER_TOMORROW) },
            rule(3, R.string.voice_ask_weather_evening, WEATHER_TOPIC, EVENING, WEATHER_CUE) { VoiceCommand.Info(InfoQuestion.WEATHER_EVENING) },
            rule(1, R.string.voice_ask_weather, WEATHER_TOPIC, WEATHER_CUE) { VoiceCommand.Info(InfoQuestion.WEATHER) },
            rule(1, R.string.voice_ask_time, w("saat", "saati", "saate"), w("kaç", "ne", "söyle", "oldu", "söylesene")) { VoiceCommand.Info(InfoQuestion.TIME) },
            rule(1, R.string.voice_ask_battery, w("pil", "pilim", "pili", "batarya", "bataryam", "şarjım", "şarj", "şarjı"), w("kaç", "ne", "durumu", "durum", "kaldı", "yüzde", "kalmış", "nasıl")) { VoiceCommand.Info(InfoQuestion.BATTERY) },
            rule(1, R.string.voice_ask_speed, w("hız", "hızım", "hızımı", "hızımız"), SPEED_ASK) { VoiceCommand.Info(InfoQuestion.SPEED) },
            rule(1, R.string.voice_ask_speed, w("kaç"), w("gidiyorum", "yapıyorum", "basıyorum", "uçuyorum", "gidiyoruz")) { VoiceCommand.Info(InfoQuestion.SPEED) },
            rule(2, R.string.voice_ask_speed_limit, w("limit", "limiti", "sınır", "sınırı", "limitini", "sınırını"), w("kaç", "ne", "nedir", "söyle", "burada", "yolda", "kadar")) { VoiceCommand.Info(InfoQuestion.SPEED_LIMIT) },
            rule(1, R.string.voice_ask_now_playing, w("çalıyor", "çalan", "çaldığı", "şarkı", "şarkının", "şarkıyı", "parça", "parçanın", "müzik"), w("hangi", "kim", "kimin", "adı", "adını", "söylüyor", "söyleyen")) { VoiceCommand.Info(InfoQuestion.NOW_PLAYING) },
            rule(1, R.string.voice_ask_now_playing, w("şarkı", "parça"), w("bu"), w("ne", "nedir")) { VoiceCommand.Info(InfoQuestion.NOW_PLAYING) },

            // Music and volume
            rule(2, R.string.voice_ask_music_pause, w("müziği", "müzik", "şarkıyı", "şarkı", "çalmayı", "çalanı", "radyoyu"), w("durdur", "duraklat", "kes", "kapat", "sustur")) { VoiceCommand.MusicPause },
            rule(2, R.string.voice_ask_volume_down, w("sesi", "ses", "sesini", "müziği", "müzik", "radyoyu"), w("kıs", "azalt", "düşür")) { VoiceCommand.VolumeDown },
            rule(2, R.string.voice_ask_volume_up, w("sesi", "ses", "sesini"), w("aç", "yükselt", "artır", "arttır", "büyüt")) { VoiceCommand.VolumeUp },
            rule(1, R.string.voice_ask_music_play, w("müziği", "müzik", "şarkıyı", "şarkı", "radyoyu"), w("aç", "çal", "başlat", "oynat", "devam")) { VoiceCommand.MusicPlay },
            rule(2, R.string.voice_ask_music_next, w("şarkıyı", "şarkı", "parçayı", "parça", "müziği"), w("geç", "atla", "değiştir", "sonraki")) { VoiceCommand.MusicNext },
            rule(2, R.string.voice_ask_music_previous, w("şarkıyı", "şarkı", "parçayı", "parça"), w("önceki", "başa", "geri")) { VoiceCommand.MusicPrevious },

            // The route
            rule(2, R.string.voice_ask_stop_nav, w("navigasyonu", "navigasyon", "rotayı", "rota", "yönlendirmeyi", "yönlendirme", "yolculuğu", "gitmeyi"), w("bitir", "durdur", "kapat", "iptal", "sonlandır", "sil", "vazgeç", "vazgeçtim")) { VoiceCommand.StopNavigation },
            rule(1, R.string.voice_ask_arrival, w("varırım", "varacağım", "varacağız", "varırız", "varış", "ulaşırım"), w("ne", "zaman", "kaçta", "kaç", "saat")) { VoiceCommand.NavInfo(NavQuestion.ARRIVAL) },
            rule(3, R.string.voice_ask_remaining_time, w("dakika", "saat", "süre", "sürer", "kalan"), w("kaldı", "kalır", "sonra", "sürer", "kalmış")) { VoiceCommand.NavInfo(NavQuestion.REMAINING_TIME) },
            rule(3, R.string.voice_ask_remaining_distance, w("kilometre", "km", "metre", "mesafe", "yol"), w("kaldı", "kalmış", "kalan", "var")) { VoiceCommand.NavInfo(NavQuestion.REMAINING_DISTANCE) },
            rule(1, R.string.voice_ask_next_turn, w("dönüş", "dönüşü", "dönecek", "döneceğim", "dönmem", "sapak", "sapağı", "manevra"), w("ne", "nerede", "nereden", "sıradaki", "sonraki", "kaç", "kadar")) { VoiceCommand.NavInfo(NavQuestion.NEXT_TURN) },
            rule(1, R.string.voice_ask_stops, w("benzin", "benzinlik", "benzinliği", "yakıt", "akaryakıt"), w("nerede", "bul", "lazım", "yakın", "yakında", "göster", "azaldı", "bitti", "bitiyor", "kalmadı", "var")) { VoiceCommand.FindStops },

            // Screens and settings
            rule(1, R.string.voice_ask_map, w("harita", "haritayı", "haritaya", "haritası"), w("aç", "göster", "git", "geç", "getir")) { VoiceCommand.OpenScreen(VoiceScreen.MAP) },
            rule(1, R.string.voice_ask_settings, w("ayar", "ayarlar", "ayarları", "ayarlara"), w("aç", "göster", "git", "geç")) { VoiceCommand.OpenScreen(VoiceScreen.SETTINGS) },
            rule(1, R.string.voice_ask_theme_night, w("gece", "karanlık", "koyu"), SWITCH) { VoiceCommand.SetTheme(ThemeMode.NIGHT) },
            rule(1, R.string.voice_ask_theme_day, w("gündüz", "aydınlık"), SWITCH) { VoiceCommand.SetTheme(ThemeMode.DAY) },

            // Messages
            rule(1, R.string.voice_ask_read_message, w("mesaj", "mesajı", "mesajlar", "mesajları", "mesajımı", "mesajlarımı"), w("oku", "okur", "okusana", "söyle")) { VoiceCommand.ReadMessage },
            rule(1, R.string.voice_ask_who_wrote, w("kim", "kimden"), w("mesaj", "mesajı", "mesajda")) { VoiceCommand.WhoWrote },
        )
        // "Yakında kafe var mı" and friends, one rule per place word (the ambiguous ones, such as "yemek", stay out).
        VoiceParser.categories.filterKeys { it !in setOf("yemek", "acil", "polis") }.forEach { (word, entry) ->
            val forms = NEARBY_SUFFIXES.map { word + it }.toSet()
            base += rule(1, 0, forms, NEARBY_CUE) { VoiceCommand.FindNearest(entry.first, entry.second) }
        }
        base
    }

    private fun tokens(raw: String): List<String> = Turkish.normalize(raw).split(' ').filter { it.isNotEmpty() }

    /** Every rule with at least its first keyword group present, best first; the flag says whether all groups were. */
    private fun scan(raw: String): List<Pair<LRule, Boolean>> {
        val words = tokens(raw).toSet()
        if (words.isEmpty()) return emptyList()
        return rules.mapNotNull { r ->
            val hits = r.groups.map { g -> g.any { it in words } }
            when {
                hits.all { it } -> r to true
                hits.first() && r.askRes != 0 && r.groups.size <= 2 -> r to false
                else -> null
            }
        }
    }

    /** The command when the sentence clearly means exactly one thing; null when nothing fits or two things fit equally. */
    fun best(raw: String): VoiceCommand? {
        val strong = scan(raw).filter { it.second }.map { it.first }
        if (strong.isEmpty()) return null
        val top = strong.maxOf { it.priority }
        val commands = strong.filter { it.priority == top }.map { it.build() }.distinct()
        return commands.singleOrNull()
    }

    /** For a short sentence nothing clearly fits: the one command it most likely was meant for, to ask "did you mean". */
    fun suggestion(raw: String): LooseMatch? {
        if (tokens(raw).size > MAX_WORDS_TO_GUESS) return null
        val hits = scan(raw)
        if (hits.isEmpty()) return null
        val top = hits.maxOf { (r, strong) -> r.priority + if (strong) 10 else 0 }
        val best = hits.filter { (r, strong) -> r.priority + (if (strong) 10 else 0) == top }
        if (best.size != 1) return null
        val rule = best.first().first
        return LooseMatch(rule.build(), rule.askRes)
    }

    private const val MAX_WORDS_TO_GUESS = 6
}
