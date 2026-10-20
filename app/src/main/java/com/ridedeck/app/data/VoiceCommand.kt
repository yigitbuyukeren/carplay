package com.ridedeck.app.data

/** Where a spoken "open …" can take the rider. */
enum class VoiceScreen { HOME, MAP, MUSIC, PHONE, APPS, SETTINGS }

enum class NavQuestion { REMAINING_TIME, REMAINING_DISTANCE, ARRIVAL, NEXT_TURN }

enum class InfoQuestion { WEATHER, TIME, BATTERY, SPEED, SPEED_LIMIT, NOW_PLAYING, TRIP }

/** What the rider asked for by voice. */
sealed class VoiceCommand {
    // Navigation
    /** Route to a place; [candidates] are search texts to try in order (the first is the text as heard). */
    data class NavigateTo(val candidates: List<String>) : VoiceCommand()
    object GoHome : VoiceCommand()
    object GoWork : VoiceCommand()
    object GoParked : VoiceCommand()
    object StopNavigation : VoiceCommand()
    object FindStops : VoiceCommand()
    /** "En yakın kafe": [term] is the search word, [label] what to call it aloud. */
    data class FindNearest(val term: String, val label: String, val along: Boolean = false) : VoiceCommand()
    data class NavInfo(val question: NavQuestion) : VoiceCommand()
    data class AvoidHighways(val on: Boolean) : VoiceCommand()
    data class AvoidTolls(val on: Boolean) : VoiceCommand()
    object Recalculate : VoiceCommand()
    /** "Başka rota": another route to the same place. */
    object AlternativeRoute : VoiceCommand()
    object SaveFavoriteHere : VoiceCommand()
    object SetHomeHere : VoiceCommand()
    object SetWorkHere : VoiceCommand()

    // Phone
    data class Call(val name: String) : VoiceCommand()
    data class CallDigits(val number: String) : VoiceCommand()
    object CallLast : VoiceCommand()
    object CallMissed : VoiceCommand()
    object ShowRecents : VoiceCommand()
    object AnswerCall : VoiceCommand()
    object RejectCall : VoiceCommand()
    object MuteCall : VoiceCommand()
    data class SpeakerCall(val on: Boolean) : VoiceCommand()

    // Messages
    object ReadMessage : VoiceCommand()
    object WhoWrote : VoiceCommand()
    /** Reply to the latest chat message; [text] is what to send when it was already said ("cevap yaz geliyorum"). */
    data class ReplyMessage(val text: String?) : VoiceCommand()

    // Music
    object MusicPlay : VoiceCommand()
    object MusicPause : VoiceCommand()
    object MusicNext : VoiceCommand()
    object MusicPrevious : VoiceCommand()
    data class PlaySearch(val query: String) : VoiceCommand()
    object VolumeUp : VoiceCommand()
    object VolumeDown : VoiceCommand()
    data class VolumeSet(val percent: Int) : VoiceCommand()

    // Information
    data class Info(val question: InfoQuestion) : VoiceCommand()

    // Apps and settings
    data class OpenApp(val name: String) : VoiceCommand()
    data class OpenScreen(val screen: VoiceScreen) : VoiceCommand()
    data class SetTheme(val mode: ThemeMode) : VoiceCommand()
    data class SetMiles(val miles: Boolean) : VoiceCommand()
    /** Screen brightness one step up (+1) or down (-1). */
    data class Brightness(val delta: Int) : VoiceCommand()
    /** Spoken weather, speed-limit and similar warnings on or off. */
    data class WarningVoice(val on: Boolean) : VoiceCommand()

    // The assistant itself
    object Help : VoiceCommand()
    object Emergency : VoiceCommand()
    data class Unknown(val heard: String) : VoiceCommand()
}

/** What an answer to a question from the assistant means. */
sealed class VoiceAnswer {
    object Yes : VoiceAnswer()
    object No : VoiceAnswer()
    object Cancel : VoiceAnswer()
    object Repeat : VoiceAnswer()
    data class Ordinal(val index: Int) : VoiceAnswer()
    data class Other(val text: String) : VoiceAnswer()
}

/**
 * Turns what the speech recogniser heard (Turkish) into a command with a table of phrase rules, no Android.
 * The recogniser is noisy on a motorcycle, so only clear phrasings are accepted; everything else is
 * [VoiceCommand.Unknown] and the assistant says so instead of guessing.
 */
object VoiceParser {
    fun normalize(raw: String): String = Turkish.normalize(raw)

    private class Rule(val pattern: Regex, val build: (MatchResult) -> VoiceCommand?)

    private fun rule(pattern: String, build: (MatchResult) -> VoiceCommand?) = Rule(Regex(pattern), build)
    private fun rule(pattern: String, command: VoiceCommand) = Rule(Regex(pattern)) { command }

    private val FILLER = Regex("^(lütfen |şimdi |hadi |bana |bir )+")
    private val TRAILING_FILLER = Regex(" (lütfen|rica ederim|acaba)$")

    /** Words for places to look for, with the term to search and how to say it. */
    private val CATEGORIES = mapOf(
        "kafe" to ("kafe" to "kafe"), "kahve" to ("kafe" to "kafe"), "kafeterya" to ("kafe" to "kafe"),
        "restoran" to ("restoran" to "restoran"), "lokanta" to ("restoran" to "lokanta"), "yemek" to ("restoran" to "restoran"),
        "eczane" to ("eczane" to "eczane"), "hastane" to ("hastane" to "hastane"), "acil" to ("hastane" to "hastane"),
        "atm" to ("atm" to "ATM"), "bankamatik" to ("atm" to "ATM"), "otopark" to ("otopark" to "otopark"),
        "market" to ("market" to "market"), "bakkal" to ("bakkal" to "bakkal"), "tuvalet" to ("tuvalet" to "tuvalet"),
        "otel" to ("otel" to "otel"), "karakol" to ("polis karakolu" to "karakol"), "polis" to ("polis karakolu" to "karakol"),
        "fırın" to ("fırın" to "fırın"), "berber" to ("berber" to "berber"),
    )
    private val CATEGORY_WORDS = CATEGORIES.keys.joinToString("|") { Regex.escape(it) }

    private val NOT_A_SONG = setOf("müziği", "müzik", "şarkıyı", "şarkı", "bir şarkı", "bir müzik", "radyoyu", "sesi", "haritayı", "telefonu")

    private val rules: List<Rule> = listOf(
        // The assistant itself
        rule("^(ne diyebilirim|neler diyebilirim|komutlar|komutları söyle|sana ne diyebilirim|yardım|ne yapabilirsin|neler yapabilirsin)$", VoiceCommand.Help),
        rule("^(yardım çağır|acil yardım|acil durum|kaza yaptım|ambulans çağır|ambulans|sos|yardım lazım|kaza geçirdim)$", VoiceCommand.Emergency),

        // Volume before music: "sesi aç" must not be taken for "müziği aç"
        rule("^(?:sesi|ses seviyesini|müziğin sesini|ses) (?:yüzde )?(.+?)(?: yüzde)? (?:yap|olsun|ayarla)$") { m -> Turkish.parseNumber(m.groupValues[1])?.takeIf { it in 0..100 }?.let { VoiceCommand.VolumeSet(it) } },
        rule("^ses(?:i| seviyesi)? yüzde (.+)$") { m -> Turkish.parseNumber(m.groupValues[1])?.takeIf { it in 0..100 }?.let { VoiceCommand.VolumeSet(it) } },
        rule("^(?:sesi |müziğin sesini |ses seviyesini )(?:biraz |çok )?(?:aç|yükselt|artır|arttır|büyüt)$", VoiceCommand.VolumeUp),
        rule("^(?:sesi |müziğin sesini |ses seviyesini )(?:biraz |çok )?(?:kıs|azalt|düşür|küçült)$", VoiceCommand.VolumeDown),
        rule("^(?:sesi |ses )?(?:yükselt|artır)$", VoiceCommand.VolumeUp),
        rule("^(?:sesi )?(?:kıs|azalt)$", VoiceCommand.VolumeDown),
        rule("^(?:daha )?(?:yüksek|fazla) ses$", VoiceCommand.VolumeUp),

        // Calls in progress
        rule("^(?:aramayı |gelen aramayı |telefonu )?(?:cevapla|aç|kabul et|yanıtla)$", VoiceCommand.AnswerCall),
        rule("^(?:aramayı |gelen aramayı |telefonu |görüşmeyi )?(?:reddet|kapat|bitir|sonlandır|meşgul et)$", VoiceCommand.RejectCall),
        rule("^(?:aramayı |görüşmeyi )?(?:sessize al|sustur)$|^mikrofonu (?:kapat|sustur)$|^sesimi kapat$", VoiceCommand.MuteCall),
        rule("^(?:hoparlör(?:e al|ü aç| aç)?|hoparlöre geç)$", VoiceCommand.SpeakerCall(true)),
        rule("^(?:hoparlörü kapat|kulaklığa al|kulaklığa geç|ahizeye al)$", VoiceCommand.SpeakerCall(false)),

        // Messages
        rule("^(?:son |yeni )?(?:mesajı|mesajımı|mesajlarımı|mesajları) (?:oku|okur musun|söyle)$|^(?:oku|mesajı oku)$|^(?:yeni mesaj var mı|mesaj geldi mi|ne yazmış|ne yazdı)$", VoiceCommand.ReadMessage),
        rule("^(?:kim yazdı|kimden mesaj var|kimden geldi|mesaj kimden|kim mesaj attı)$", VoiceCommand.WhoWrote),
        rule("^(?:mesaja |ona )?(?:cevap yaz|yanıt yaz|cevap ver|yanıt ver|cevapla yaz|yaz)$|^mesaja cevap$", VoiceCommand.ReplyMessage(null)),
        rule("^(?:cevap yaz|yanıt yaz|cevap ver|cevapla|yanıtla|ona yaz|yaz|mesaj yaz) (.+)$") { m -> VoiceCommand.ReplyMessage(m.groupValues[1].trim()) },

        // Music
        rule("^(?:müziği |şarkıyı |çalmayı |parçayı )?(?:durdur|duraklat|kes)$|^müziği (?:kapat|sustur)$|^müzik(?:i)? (?:durdur|kapat)$", VoiceCommand.MusicPause),
        rule("^(?:sonraki|bir sonraki|sıradaki)(?: şarkı| parça| şarkıya geç)?$|^(?:şarkıyı |parçayı )?(?:geç|atla)$|^sonrakine (?:geç|atla)$|^(?:ileri sar)$", VoiceCommand.MusicNext),
        rule("^(?:önceki|bir önceki)(?: şarkı| parça| şarkıya geç)?$|^önceki şarkıya (?:dön|geç)$|^(?:şarkıyı )?başa (?:al|sar|dön)$", VoiceCommand.MusicPrevious),
        rule("^(?:müziği |müzik |şarkıyı )?(?:aç|çal|başlat|oynat|devam ettir)$|^(?:müzik|müzik çal|müzik aç|müziğe devam|devam et|oynat|çal)$", VoiceCommand.MusicPlay),
        rule("^(?:ne çalıyor|bu ne şarkı|bu şarkı ne|şarkının adı ne|bu kimin şarkısı|kim söylüyor|bu şarkıyı kim söylüyor|şu an ne çalıyor)$", VoiceCommand.Info(InfoQuestion.NOW_PLAYING)),
        rule("^(?:(?:spotify|youtube music|youtube müzik|müzik)(?:'?(?:ta|te|da|de|ten|tan))? )?(.+?) (?:çal|çalar mısın|dinlemek istiyorum|dinle|oynat)$") { m ->
            val q = m.groupValues[1].trim().removeSuffix("'ı").removeSuffix("'i").removeSuffix("'u").removeSuffix("'ü")
            if (q in NOT_A_SONG || q.isEmpty()) null else VoiceCommand.PlaySearch(q)
        },
        rule("^(?:çal|dinle|oynat) (.+)$") { m ->
            val q = m.groupValues[1].trim()
            if (q in NOT_A_SONG) null else VoiceCommand.PlaySearch(q)
        },

        // Navigation questions and settings
        rule("^(?:navigasyonu|yönlendirmeyi|rotayı|yolculuğu|gitmeyi|haritayı) (?:bitir|durdur|kapat|iptal et|sonlandır|sil)$|^(?:gitmekten vazgeç|vazgeçtim|yönlendirmeyi iptal et|rotayı iptal et|navigasyonu iptal et|yolculuk bitti|geldik|vardım)$", VoiceCommand.StopNavigation),
        rule("^(?:ne zaman varırım|kaçta varırım|kaçta varacağım|varış saati(?:m)?(?: kaç| ne)?|tahmini varış|ne zaman varacağım|varış saatini söyle)$", VoiceCommand.NavInfo(NavQuestion.ARRIVAL)),
        rule("^(?:kaç dakika kaldı|kaç saat kaldı|ne kadar kaldı|kalan süre(?:m)?(?: ne| kaç)?|ne kadar sürer|ne kadar süre kaldı|varmama ne kadar var|süre ne kadar)$", VoiceCommand.NavInfo(NavQuestion.REMAINING_TIME)),
        rule("^(?:kaç kilometre kaldı|kaç km kaldı|kaç metre kaldı|kalan mesafe(?:m)?(?: ne| kaç)?|ne kadar yol var|ne kadar yol kaldı|kalan yol(?: ne kadar)?|mesafe ne kadar)$", VoiceCommand.NavInfo(NavQuestion.REMAINING_DISTANCE)),
        rule("^(?:sıradaki dönüş(?: ne| nerede)?|bir sonraki dönüş(?: ne| nerede)?|sonraki dönüş|nereden döneceğim|nerede döneceğim|ne zaman döneceğim|şimdi ne yapayım|ne yapayım|sonraki manevra|dönüş ne)$", VoiceCommand.NavInfo(NavQuestion.NEXT_TURN)),
        rule("^(?:otoyolsuz git|otoyolu kullanma|otoyol kullanma|otoyoldan gitme|otoyollardan kaçın|otoyolları kullanma|otoyolsuz rota)$", VoiceCommand.AvoidHighways(true)),
        rule("^(?:otoyol kullan|otoyoldan git|otoyolu kullan|otoyolları kullan|otoyolu aç)$", VoiceCommand.AvoidHighways(false)),
        rule("^(?:ücretsiz yol(?:dan git)?|ücretsiz git|paralı yolları kullanma|paralı yol kullanma|paralı yoldan gitme|gişesiz git|gişesiz rota|ücretli yolları kullanma)$", VoiceCommand.AvoidTolls(true)),
        rule("^(?:paralı yol kullan|paralı yoldan git|paralı yolları kullan|ücretli yolları kullan)$", VoiceCommand.AvoidTolls(false)),
        rule("^(?:rotayı |yolu )?(?:yeniden|tekrar|baştan) hesapla$|^(?:yeni rota)$", VoiceCommand.Recalculate),
        rule("^(?:başka rota|başka yoldan git|alternatif rota|başka yol var mı|yeni yol bul|başka bir rota|alternatif yol|başka yol)$", VoiceCommand.AlternativeRoute),
        rule("^(?:burayı |burayı bir )?(?:favoriye ekle|favori yap|favorilere ekle|kaydet|favori olarak kaydet)$|^(?:bu konumu kaydet|konumu kaydet)$", VoiceCommand.SaveFavoriteHere),
        rule("^(?:evimi buraya ayarla|burası evim|evim burası|ev adresimi buraya ayarla|evimi buraya kaydet|burayı ev yap)$", VoiceCommand.SetHomeHere),
        rule("^(?:işimi buraya ayarla|burası işim|işim burası|iş adresimi buraya ayarla|işimi buraya kaydet|burayı iş yap)$", VoiceCommand.SetWorkHere),
        rule("^(?:eve|evime|ev adresime|evimize)(?: doğru)? (?:git|gidelim|götür|dön|yönlendir|rota|gidiyorum|gidiyoruz)$|^(?:beni )?eve götür$|^eve dönüş$|^eve git$", VoiceCommand.GoHome),
        rule("^(?:işe|iş yerime|işyerime|iş adresime|ofise|ofisime)(?: doğru)? (?:git|gidelim|götür|dön|yönlendir|rota|gidiyorum|gidiyoruz)$|^(?:beni )?(?:işe|ofise) götür$", VoiceCommand.GoWork),
        rule("^(?:park yerime|park ettiğim yere|motoruma|arabama|bisikletime|aracıma|park yerine)(?: doğru)? (?:git|gidelim|götür|dön|yönlendir|rota)$|^(?:park yerimi|park yerim nerede|aracım nerede|motorum nerede|arabam nerede|bisikletim nerede)(?: göster)?$", VoiceCommand.GoParked),

        // Information
        rule("^(?:hava nasıl|hava durumu|hava durumunu söyle|hava kaç derece|kaç derece|dışarısı kaç derece|hava nasıl olacak|yağmur(?:)? var mı|yağmur yağacak mı|yağmur geliyor mu|yağmur yağıyor mu|bugün yağmur var mı|hava soğuk mu|hava sıcak mı)$", VoiceCommand.Info(InfoQuestion.WEATHER)),
        rule("^(?:saat kaç|saat ne|saati söyle|saat kaç oldu|şimdi saat kaç|saati ver)$", VoiceCommand.Info(InfoQuestion.TIME)),
        rule("^(?:pil|pilim|batarya|şarjım|şarj)(?: durumu| yüzde kaç| kaç| ne kadar| ne durumda| yüzde kaç kaldı)?$|^(?:pil ne kadar kaldı|telefonun şarjı kaç|şarjım ne kadar kaldı)$") { m ->
            // "şarj" alone is a stop search; only the question forms are about the battery.
            if (m.value == "şarj") VoiceCommand.FindStops else VoiceCommand.Info(InfoQuestion.BATTERY)
        },
        rule("^(?:kaç ile gidiyorum|hızım kaç|hızım ne|hız kaç|ne kadar hızlı gidiyorum|kaç yapıyorum|hızımı söyle|şu an kaç ile gidiyorum)$", VoiceCommand.Info(InfoQuestion.SPEED)),
        rule("^(?:yolculuk özeti|yolculuk özetini söyle|bu yolculuk nasıl gitti|bugün kaç km|ne kadar yol yaptım|kaç km yaptım|yolculuk istatistiği|son yolculuk|son yolculuk nasıldı)$", VoiceCommand.Info(InfoQuestion.TRIP)),
        rule("^(?:hız sınırı(?: kaç| ne)?|hız limiti(?: kaç| ne)?|limit kaç|burada hız limiti kaç|buranın hız limiti ne|sınır kaç)$", VoiceCommand.Info(InfoQuestion.SPEED_LIMIT)),

        // Settings and screens
        rule("^(?:gece modu(?:na geç)?|gece moduna geç|karanlık mod|karanlık moda geç|koyu tema|karanlık tema)$", VoiceCommand.SetTheme(ThemeMode.NIGHT)),
        rule("^(?:gündüz modu(?:na geç)?|aydınlık mod|aydınlık moda geç|açık tema|aydınlık tema)$", VoiceCommand.SetTheme(ThemeMode.DAY)),
        rule("^(?:otomatik tema|tema otomatik|temayı otomatik yap|otomatik mod)$", VoiceCommand.SetTheme(ThemeMode.AUTO)),
        rule("^(?:ekranı kıs|ekranı karart|parlaklığı azalt|parlaklığı düşür|ekranı biraz kıs)$", VoiceCommand.Brightness(-1)),
        rule("^(?:ekranı aç|ekranı aydınlat|parlaklığı artır|parlaklığı arttır|parlaklığı yükselt|ekranı biraz aç|ekranı parlat)$", VoiceCommand.Brightness(1)),
        rule("^(?:sesli uyarıları kapat|uyarıları sustur|sesli uyarılar kapansın|uyarıları sessize al)$", VoiceCommand.WarningVoice(false)),
        rule("^(?:sesli uyarıları aç|uyarıları aç|sesli uyarılar açılsın)$", VoiceCommand.WarningVoice(true)),
        rule("^(?:mil kullan|mil cinsinden göster|mph kullan|birimi mil yap|mil ve mph|mile geç|mil olsun)$", VoiceCommand.SetMiles(true)),
        rule("^(?:kilometre kullan|km kullan|kilometre cinsinden göster|birimi kilometre yap|kilometreye geç|kilometre olsun|km olsun)$", VoiceCommand.SetMiles(false)),
        rule("^(?:haritayı aç|haritaya git|haritayı göster|harita ekranı|haritaya geç)$", VoiceCommand.OpenScreen(VoiceScreen.MAP)),
        rule("^(?:müzik ekranını aç|müzik ekranına git|müzik çalara git|müzik ekranı)$", VoiceCommand.OpenScreen(VoiceScreen.MUSIC)),
        rule("^(?:rehberi aç|telefon ekranını aç|tuş takımını aç|telefon ekranına git|arama ekranını aç)$", VoiceCommand.OpenScreen(VoiceScreen.PHONE)),
        rule("^(?:uygulamaları aç|uygulama rafını aç|uygulamalara git|uygulama rafı|uygulamalar)$", VoiceCommand.OpenScreen(VoiceScreen.APPS)),
        rule("^(?:ayarları aç|ayarlara git|ayarlar)$", VoiceCommand.OpenScreen(VoiceScreen.SETTINGS)),
        rule("^(?:ana ekrana dön|ana ekrana git|ana ekran|ana sayfa|geri dön)$", VoiceCommand.OpenScreen(VoiceScreen.HOME)),

        // Open an app by name (anything else with "aç" was handled above)
        rule("^(.+?) (?:aç|başlat|çalıştır|uygulamasını aç|uygulamasını başlat|uygulamasını çalıştır)$") { m ->
            val name = stripAccusative(m.groupValues[1].trim())
            if (name.isEmpty() || name in NOT_A_SONG) null else VoiceCommand.OpenApp(name)
        },

        // Phone calls to people and numbers
        rule("^(?:son aradığımı ara|son arananı ara|son aranan numarayı ara|son numarayı ara|tekrar ara|yeniden ara|son aramayı tekrarla)$", VoiceCommand.CallLast),
        rule("^(?:cevapsız(?: aramaları| arayanı| aramayı| olanı)? ara|beni arayanı ara|cevapsızı ara|kaçırdığım aramayı ara|geri ara)$", VoiceCommand.CallMissed),
        rule("^(?:cevapsız aramalar(?:a bak)?|cevapsızlar|cevapsızlara bak|son aramalar|arama geçmişi|aramalara bak|arama geçmişini aç)$", VoiceCommand.ShowRecents),
        rule("^(?:ara|arama yap|numarayı ara) (.+)$|^(.+?)(?: numarasını| numaralı)? ara$") { m ->
            val body = (m.groups[1]?.value ?: m.groups[2]?.value).orEmpty().trim()
            when {
                body.isEmpty() -> null
                Turkish.parseDigits(body) != null -> VoiceCommand.CallDigits(Turkish.parseDigits(body)!!)
                else -> VoiceCommand.Call(stripAccusative(body))
            }
        },
        rule("^(.+?)(?:'?(?:i|ı|u|ü|yi|yı|yu|yü))? arar mısın$|^(.+?) (?:ile konuş|aramak istiyorum|arayalım)$") { m ->
            val body = (m.groups[1]?.value ?: m.groups[2]?.value).orEmpty().trim()
            if (body.isEmpty()) null else VoiceCommand.Call(stripAccusative(body))
        },

        // Places to look for, along the road or around
        rule("^(?:yolda|yol üstünde|rota üzerinde|yolumda|rotamda) (?:bir )?(benzinlik|benzin|yakıt|şarj istasyonu|şarj|tamirci|tamir noktası|su noktası|çeşme|içme suyu|istasyon)\\w*$|^(?:en yakın |yakındaki |yakınımdaki |yakında )(?:bir )?(?:benzinlik|benzin istasyonu|akaryakıt|şarj istasyonu|şarj noktası|tamirci|tamir noktası|bisiklet tamircisi|su noktası|çeşme|içme suyu)\\w*(?: var mı| nerede| göster)?$|^(?:benzinlik|benzinlik bul|benzin lazım|yakıt lazım|benzinliğe git|şarj istasyonu bul|tamirci bul|su bul|tamir noktası bul)$", VoiceCommand.FindStops),
        rule("^(?:yolda|yol üstünde|rota üzerinde|yolumda|rotamda|en yakın|yakındaki|yakınımdaki|yakında|buraya yakın) (?:bir )?($CATEGORY_WORDS)\\w*(?: var mı| nerede| göster| bul| ara)?$|^($CATEGORY_WORDS)\\w*(?: bul| göster| nerede| var mı| lazım)$") { m ->
            val word = (m.groups[1]?.value ?: m.groups[2]?.value).orEmpty()
            val entry = CATEGORIES.entries.firstOrNull { word.startsWith(it.key) }?.value ?: return@rule null
            val along = Regex("^(?:yolda|yol üstünde|rota üzerinde|yolumda|rotamda) ").containsMatchIn(m.value)
            VoiceCommand.FindNearest(entry.first, entry.second, along)
        },
    )

    /** Places to go: "X'e git", "rota X", "yol tarifi X", "beni X'e götür", "X'e nasıl giderim". */
    private val navigateRules: List<Regex> = listOf(
        Regex("^(?:beni )?(.+?) (?:git|gidelim|gidiyoruz|gidiyorum|gideyim|götür|götürür müsün|gidebilir miyiz|gider misin|yönlendir|yönlendirir misin|nasıl giderim|nasıl gidilir|navigasyon|yol tarifi|rota|rotası|adresine git|adresine gidelim|tarif et)$"),
        Regex("^(?:rota|rota ver|yol tarifi|yol tarifi ver|navigasyon|navigasyonu başlat|git|yönlendir|nasıl giderim|götür|beni götür) (.+)$"),
    )

    fun parse(raw: String): VoiceCommand {
        var t = normalize(raw)
        if (t.isEmpty()) return VoiceCommand.Unknown(raw)
        t = t.replace(FILLER, "").replace(TRAILING_FILLER, "").trim()
        if (t.isEmpty()) return VoiceCommand.Unknown(raw)
        for (r in rules) {
            val m = r.pattern.find(t) ?: continue
            r.build(m)?.let { return it }
        }
        for (r in navigateRules) {
            val m = r.find(t) ?: continue
            val place = m.groupValues[1].trim()
            if (place.isNotEmpty() && place !in NOT_A_PLACE) return navigate(place)
        }
        return VoiceCommand.Unknown(raw)
    }

    private val NOT_A_PLACE = setOf("beni", "buraya", "oraya", "şimdi", "bir", "lütfen")

    private fun navigate(place: String): VoiceCommand {
        val candidates = Turkish.nameCandidates(place)
        // The full phrase as heard comes first (it may be a name that merely ends like an ending), then the stems.
        val ordered = LinkedHashSet<String>()
        ordered.add(place.substringBefore('\'').trim().ifEmpty { place })
        if (!place.contains('\'')) {
            ordered.clear()
            ordered.add(place)
        }
        ordered.addAll(candidates)
        return VoiceCommand.NavigateTo(ordered.filter { it.isNotBlank() })
    }

    private fun stripAccusative(name: String): String {
        val n = name.trim()
        if (n.contains('\'')) return n.substringBefore('\'')
        return Regex("^(.{2,})y[iıuü]$").find(n)?.groupValues?.get(1) ?: n
    }

    private val YES = setOf("evet", "tamam", "olur", "onayla", "aynen", "doğru", "başlat", "gönder", "ara", "yap", "evet gönder", "evet ara", "tabii", "tabi", "kesinlikle", "onaylıyorum", "yes")
    private val NO = setOf("hayır", "yok", "olmaz", "istemiyorum", "hayır gönderme", "hayır arama", "değil", "yanlış", "no")
    private val CANCEL = setOf("iptal", "vazgeç", "vazgeçtim", "dur", "boşver", "bırak", "kapat", "sus", "iptal et", "gerek yok")
    private val REPEAT = setOf("tekrar", "bir daha", "tekrarla", "tekrar söyle", "ne dedin", "bir daha söyle", "tekrar oku")

    /** What a reply to "send it?" / "which one?" means; ordinals work as "birincisi", "ikinci", "ilki" or the number. */
    fun parseAnswer(raw: String): VoiceAnswer {
        val t = normalize(raw)
        return when {
            t in YES -> VoiceAnswer.Yes
            t in NO -> VoiceAnswer.No
            t in CANCEL -> VoiceAnswer.Cancel
            t in REPEAT -> VoiceAnswer.Repeat
            t in setOf("birinci", "birincisi", "ilki", "ilk", "birincisini", "bir", "1", "birinciyi", "ilkini") -> VoiceAnswer.Ordinal(0)
            t in setOf("ikinci", "ikincisi", "ikincisini", "ikinciyi", "iki", "2", "ikisi") -> VoiceAnswer.Ordinal(1)
            t in setOf("üçüncü", "üçüncüsü", "üçüncüyü", "üç", "3", "üçüncüsünü") -> VoiceAnswer.Ordinal(2)
            t in setOf("dördüncü", "dördüncüsü", "dört", "4") -> VoiceAnswer.Ordinal(3)
            else -> VoiceAnswer.Other(raw.trim())
        }
    }

    /** Best contacts for a heard name: loose match on first name, whole name, endings and typos. */
    fun matchContact(heard: String, contacts: List<Contact>): List<Contact> =
        Turkish.bestMatches(heard, contacts) { it.name }
}
