package com.ridedeck.app

import com.ridedeck.app.data.AppRef
import com.ridedeck.app.data.CallKind
import com.ridedeck.app.data.CallPhase
import com.ridedeck.app.data.Contact
import com.ridedeck.app.data.InfoQuestion
import com.ridedeck.app.data.MessageInfo
import com.ridedeck.app.data.NavQuestion
import com.ridedeck.app.data.PlaceKind
import com.ridedeck.app.data.RecentCall
import com.ridedeck.app.data.Speaker
import com.ridedeck.app.data.ThemeMode
import com.ridedeck.app.data.VoiceActions
import com.ridedeck.app.data.VoiceHeard
import com.ridedeck.app.data.VoiceAssistant
import com.ridedeck.app.data.VoiceScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The assistant's dialogue (asking back, confirming, giving up) with a scripted speech engine and no Android. */
class VoiceAssistantTest {
    /** Each call to start() answers with the next scripted list of alternatives. */
    private class ScriptedEar(var script: ArrayDeque<List<String>> = ArrayDeque()) : VoiceAssistant.SpeechListener {
        var listens = 0
        /** The score the recogniser reports for the next result (null: it reports none). */
        var confidence: Float? = null
        override fun available() = true
        override fun start(onPartial: (String) -> Unit, onResult: (VoiceHeard) -> Unit) {
            listens++
            onResult(VoiceHeard(script.removeFirstOrNull() ?: emptyList(), confidence))
        }
        override fun cancel() {}
    }

    private class FakeMouth : Speaker {
        val said = mutableListOf<String>()
        override fun speak(text: String) { said += text }
        override fun stop() {}
        override fun shutdown() {}
    }

    private class FakeActions : VoiceActions {
        val log = mutableListOf<String>()
        var contacts = listOf(Contact("Ali Yılmaz", "111"), Contact("Ali Kaya", "222"), Contact("Ayşe", "333"))
        var recents = listOf(RecentCall("444", "Mehmet", CallKind.OUTGOING, 1L), RecentCall("555", null, CallKind.MISSED, 2L))
        var phase = CallPhase.IDLE
        var message: MessageInfo? = MessageInfo("Ali", "WhatsApp", "Nerdesin?", canReply = true)
        var replySucceeds = true
        var homeSaved = true
        var routing = true
        override fun openScreen(screen: VoiceScreen) { log += "screen:$screen" }
        override suspend fun navigateTo(candidates: List<String>): String? { log += "nav:${candidates.first()}"; return candidates.first().replaceFirstChar { it.uppercase() } }
        override fun goSaved(kind: PlaceKind): String? = if (homeSaved) "Ev" else null.also { log += "no:$kind" }
        override fun stopNavigation(): Boolean { log += "stop"; return routing }
        override fun findStops() { log += "stops" }
        override suspend fun findNearest(term: String, along: Boolean): String? { log += "near:$term:$along"; return "Bir $term" }
        var alternative = true
        override suspend fun alternativeRoute(): Boolean { log += "alt"; return alternative }
        override fun navInfo(question: NavQuestion): String? = if (routing) "info:$question" else null
        override fun setAvoid(highways: Boolean?, tolls: Boolean?) { log += "avoid:$highways:$tolls" }
        override fun recalculate(): Boolean = routing
        override suspend fun saveHere(kind: PlaceKind): String? { log += "save:$kind"; return "Burası" }
        override fun canReadContacts() = true
        override fun contacts() = contacts
        override fun recents() = recents
        override fun dial(number: String, name: String?) { log += "dial:$number" }
        override fun callPhase() = phase
        override fun answerCall() { log += "answer" }
        override fun rejectCall() { log += "reject" }
        override fun muteCall() { log += "mute" }
        override fun setSpeaker(on: Boolean) { log += "speaker:$on" }
        override fun lastMessage() = message
        override fun reply(text: String): Boolean { log += "reply:$text"; return replySucceeds }
        override fun mediaPlay() { log += "play" }
        override fun mediaPause() { log += "pause" }
        override fun mediaNext() { log += "next" }
        override fun mediaPrevious() { log += "prev" }
        override fun volumeUp() { log += "vol+" }
        override fun volumeDown() { log += "vol-" }
        override fun volumeSet(percent: Int) { log += "vol=$percent" }
        override fun playSearch(query: String): Boolean { log += "search:$query"; return true }
        override suspend fun info(question: InfoQuestion): String? { log += "info:$question"; return "info:$question" }
        override fun apps() = listOf(AppRef("WhatsApp", "wa"), AppRef("Gmail", "gm"), AppRef("Chrome", "ch"))
        override fun launchApp(pkg: String): Boolean { log += "launch:$pkg"; return true }
        override fun setTheme(mode: ThemeMode) { log += "theme:$mode" }
        override fun setMiles(miles: Boolean) { log += "miles:$miles" }
        override fun setBrightness(delta: Int) { log += "bright:$delta" }
        override fun setWarningVoice(on: Boolean) { log += "warn:$on" }
        var smsAllowed = true
        val nicknames = mutableMapOf<String, Contact>()
        override fun canSendSms() = smsAllowed
        override fun sendSms(number: String, text: String): Boolean { log += "sms:$number:$text"; return true }
        override fun nickname(key: String): Contact? = nicknames[key]
        override fun setNickname(key: String, contact: Contact) { nicknames[key] = contact }
        override fun setReminder(minutes: Int, what: String?): Boolean { log += "remind:$minutes:$what"; return true }
        override fun setProactive(on: Boolean) { log += "proactive:$on" }
        override fun startSos() { log += "sos" }
        override fun showHelp() { log += "help" }
    }

    private lateinit var ear: ScriptedEar
    private lateinit var mouth: FakeMouth
    private lateinit var actions: FakeActions
    private lateinit var assistant: VoiceAssistant

    @Before
    fun setUp() {
        ear = ScriptedEar()
        mouth = FakeMouth()
        actions = FakeActions()
        // The strings are looked up by id; the test only needs them to be distinct, so the id itself is the text.
        assistant = VoiceAssistant(
            text = { id -> "s$id" },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            listener = ear, speaker = mouth, actions = actions,
            pause = { },
        )
    }

    private fun say(vararg phrases: List<String>) {
        ear.script = ArrayDeque(phrases.toList())
        assistant.listen()
    }

    @Test
    fun aSimpleCommandRunsAtOnce() {
        say(listOf("müziği aç"))
        assertEquals(listOf("play"), actions.log)
        say(listOf("sonraki şarkı"))
        assertTrue("next" in actions.log)
    }

    @Test
    fun aLaterAlternativeIsUsedWhenTheFirstIsNoise() {
        say(listOf("müziği ac mı", "müziği aç"))
        assertEquals(listOf("play"), actions.log)
    }

    @Test
    fun nothingHeardSaysSoAndDoesNothing() {
        say(emptyList())
        assertTrue(actions.log.isEmpty())
    }

    @Test
    fun callingOnePersonCountsDownThenDials() {
        say(listOf("Ayşe'yi ara"))
        assertEquals(listOf("dial:333"), actions.log)
    }

    @Test
    fun twoAlisAskWhichOneAndAcceptAnOrdinal() {
        say(listOf("Ali'yi ara"), listOf("ikinci"))
        assertEquals(listOf("dial:222"), actions.log)
    }

    @Test
    fun theChoiceCanBeNamedToo() {
        say(listOf("Ali'yi ara"), listOf("ali yılmaz"))
        assertEquals(listOf("dial:111"), actions.log)
    }

    @Test
    fun anUnclearAnswerIsAskedOnceMoreThenDropped() {
        say(listOf("Ali'yi ara"), listOf("hımm"), listOf("belki"))
        assertTrue(actions.log.none { it.startsWith("dial") })
        assertEquals(3, ear.listens)
    }

    @Test
    fun cancelAnswersStopTheCall() {
        say(listOf("Ali'yi ara"), listOf("vazgeç"))
        assertTrue(actions.log.isEmpty())
    }

    @Test
    fun unknownNameIsNotCalled() {
        say(listOf("Zeynep'i ara"))
        assertTrue(actions.log.isEmpty())
    }

    @Test
    fun lastDialledAndMissed() {
        say(listOf("son aradığımı ara"))
        assertEquals(listOf("dial:444"), actions.log)
        actions.log.clear()
        say(listOf("cevapsızı ara"))
        assertEquals(listOf("dial:555"), actions.log)
    }

    @Test
    fun replyAsksWhatToWriteThenConfirmsBeforeSending() {
        say(listOf("cevap yaz"), listOf("yoldayım geliyorum"), listOf("evet"))
        assertEquals(listOf("reply:yoldayım geliyorum"), actions.log)
    }

    @Test
    fun replyWithTextStillAsksToConfirm() {
        say(listOf("cevap yaz geliyorum"), listOf("hayır"))
        assertTrue(actions.log.isEmpty())
        say(listOf("cevap yaz geliyorum"), listOf("gönder"))
        assertEquals(listOf("reply:geliyorum"), actions.log)
    }

    @Test
    fun replyIsRefusedWhenTheAppOffersNoReplyButton() {
        actions.message = MessageInfo("Ali", "Mesajlar", "Selam", canReply = false)
        say(listOf("cevap yaz"))
        assertTrue(actions.log.isEmpty())
    }

    @Test
    fun cevaplaMeansTheCallWhenRingingAndTheMessageOtherwise() {
        actions.phase = CallPhase.RINGING
        say(listOf("cevapla"))
        assertEquals(listOf("answer"), actions.log)
        actions.log.clear()
        actions.phase = CallPhase.IDLE
        say(listOf("cevapla"), listOf("evet, olur"), listOf("evet"))
        // No text was given, so it asked what to write; the scripted "evet, olur" became the text.
        assertEquals(listOf("reply:evet, olur"), actions.log)
    }

    @Test
    fun sosAsksFirst() {
        say(listOf("yardım çağır"), listOf("hayır"))
        assertFalse("sos" in actions.log)
        say(listOf("yardım çağır"), listOf("evet"))
        assertTrue("sos" in actions.log)
    }

    @Test
    fun homeAndRoutes() {
        say(listOf("eve git"))
        assertEquals(listOf("screen:MAP"), actions.log)
        actions.log.clear()
        actions.homeSaved = false
        say(listOf("eve git"))
        assertFalse("screen:MAP" in actions.log)
    }

    @Test
    fun navigatingAndNearestPlace() {
        say(listOf("Kadıköy'e git"))
        assertEquals(listOf("screen:MAP", "nav:kadıköy"), actions.log)
        actions.log.clear()
        say(listOf("en yakın kafe"))
        assertEquals(listOf("screen:MAP", "near:kafe:false"), actions.log)
    }

    @Test
    fun appsAreOpenedByLooseName() {
        say(listOf("whatsapp'ı aç"))
        assertEquals(listOf("launch:wa"), actions.log)
        actions.log.clear()
        say(listOf("kıl aç"))
        assertTrue(actions.log.isEmpty())
    }

    @Test
    fun anUnknownPhraseOnTheMapSearchBoxIsSearched() {
        ear.script = ArrayDeque(listOf(listOf("moda sahil yolu")))
        assistant.listen(searchFallback = true)
        assertEquals(listOf("screen:MAP", "nav:moda sahil yolu"), actions.log)
    }

    @Test
    fun musicAndVolume() {
        say(listOf("Tarkan çal"))
        say(listOf("sesi yüzde elli yap"))
        say(listOf("müziği durdur"))
        assertEquals(listOf("search:tarkan", "vol=50", "pause"), actions.log)
    }

    @Test
    fun aStopOnTheWayIsLookedForAlongTheRoute() {
        say(listOf("yolda kafe"))
        assertEquals(listOf("screen:MAP", "near:kafe:true"), actions.log)
    }

    @Test
    fun anotherRouteAndTheQuickOnes() {
        say(listOf("başka rota"))
        say(listOf("ekranı kıs"))
        say(listOf("parlaklığı artır"))
        say(listOf("sesli uyarıları kapat"))
        say(listOf("yolculuk özeti"))
        assertEquals(listOf("alt", "bright:-1", "bright:1", "warn:false", "info:TRIP"), actions.log)
        actions.log.clear()
        actions.alternative = false
        say(listOf("alternatif rota"))
        assertEquals(listOf("alt"), actions.log)
    }

    @Test
    fun aLowScoreAsksBeforeRunning() {
        ear.confidence = 0.2f
        say(listOf("müziği aç"), listOf("evet"))
        assertEquals(listOf("play"), actions.log)
        actions.log.clear()
        say(listOf("müziği aç"), listOf("hayır"))
        assertTrue(actions.log.isEmpty())
    }

    @Test
    fun noScoreOrAGoodScoreRunsAtOnce() {
        ear.confidence = 0f
        say(listOf("müziği aç"))
        ear.confidence = 0.9f
        say(listOf("müziği durdur"))
        assertEquals(listOf("play", "pause"), actions.log)
    }

    @Test
    fun everyCommandIsWrittenToTheHistory() {
        val seen = mutableListOf<Pair<String, Boolean>>()
        assistant = VoiceAssistant(text = { id -> "s$id" }, scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), listener = ear, speaker = mouth, actions = actions, pause = { }, history = { h, ok -> seen += h to ok })
        say(listOf("müziği aç"))
        say(listOf("bugün hava çok güzel dostum"))
        assertEquals(listOf("müziği aç" to true, "bugün hava çok güzel dostum" to false), seen)
    }

    @Test
    fun settingsAndScreens() {
        say(listOf("gece modu"))
        say(listOf("mil kullan"))
        say(listOf("müzik ekranını aç"))
        say(listOf("ne diyebilirim"))
        assertEquals(listOf("theme:NIGHT", "miles:true", "screen:MUSIC", "help"), actions.log)
    }

    @Test
    fun twoCommandsRunQuickOnesFirstAndACallLast() {
        say(listOf("Ayşe'yi ara ve müziği kıs"))
        assertEquals(listOf("vol-", "dial:333"), actions.log)
    }

    @Test
    fun twoCallsInOneSentenceAreRefused() {
        say(listOf("Ayşe'yi ara ve Mehmet'i ara"))
        assertTrue(actions.log.none { it.startsWith("dial") })
    }

    @Test
    fun aNewMessageIsDictatedReadBackAndOnlySentAfterAYes() {
        say(listOf("Ayşe'ye mesaj at"), listOf("yoldayım"), listOf("hayır"))
        assertTrue(actions.log.none { it.startsWith("sms") })
        say(listOf("Ayşe'ye mesaj at"), listOf("yoldayım"), listOf("evet"))
        assertEquals(listOf("sms:333:yoldayım"), actions.log)
    }

    @Test
    fun aMessageNeedsTheSmsPermission() {
        actions.smsAllowed = false
        say(listOf("Ayşe'ye mesaj at"))
        assertTrue(actions.log.isEmpty())
    }

    @Test
    fun onuAraMeansTheLastPerson() {
        say(listOf("Ayşe'yi ara"))
        say(listOf("onu ara"))
        assertEquals(listOf("dial:333", "dial:333"), actions.log)
    }

    @Test
    fun aSpokenCorrectionChangesThePlace() {
        say(listOf("Kadıköy'e git"))
        say(listOf("hayır Kadıköy değil Moda"))
        assertEquals(listOf("screen:MAP", "nav:kadıköy", "screen:MAP", "nav:moda"), actions.log)
    }

    @Test
    fun undoTakesBackTheLastCommand() {
        say(listOf("sesi kıs"))
        say(listOf("geri al"))
        assertEquals(listOf("vol-", "vol+"), actions.log)
    }

    @Test
    fun theFirstTimeANicknameIsAskedForThenRemembered() {
        say(listOf("annemi ara"), listOf("ayşe"))
        assertEquals(listOf("dial:333"), actions.log)
        assertEquals("Ayşe", actions.nicknames["annem"]?.name)
        say(listOf("annemi ara"))
        assertEquals(listOf("dial:333", "dial:333"), actions.log)
    }

    @Test
    fun aReminderIsSet() {
        say(listOf("yirmi dakika sonra hatırlat"))
        assertEquals(listOf("remind:20:null"), actions.log)
    }

    @Test
    fun theAssistantsOwnQuestionRunsOnYesAndIsSilentOtherwise() {
        ear.script = ArrayDeque(listOf(listOf("evet")))
        assertTrue(assistant.proactive("Yağmur geliyor, okuyayım mı?", com.ridedeck.app.data.VoiceCommand.Info(InfoQuestion.WEATHER)))
        assertEquals(listOf("info:WEATHER"), actions.log)
        ear.script = ArrayDeque(listOf(listOf("hayır")))
        assertTrue(assistant.proactive("Pil düşük, ekranı kısayım mı?", com.ridedeck.app.data.VoiceCommand.Brightness(-1)))
        assertEquals(listOf("info:WEATHER"), actions.log)
        assertEquals(com.ridedeck.app.data.VoicePhase.IDLE, assistant.ui.value.phase)
    }

    @Test
    fun aHalfHeardSentenceAsksWhatWasMeant() {
        say(listOf("hava çok güzel"), listOf("evet"))
        assertEquals(listOf("info:WEATHER"), actions.log)
    }
}
