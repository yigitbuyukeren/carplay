package com.ridedeck.app.data

import com.ridedeck.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VoicePhase { IDLE, LISTENING, CONFIRM, MESSAGE }

data class VoiceUi(
    val phase: VoicePhase = VoicePhase.IDLE,
    /** What the recogniser heard so far, or the message to show. */
    val text: String = "",
    /** The action waiting for its countdown (a call), shown as the card title. */
    val title: String = "",
    val secondsLeft: Int = 0,
    /** While the assistant asks "which one?": the options, so they can also be tapped. */
    val choices: List<String> = emptyList(),
)

/** The latest chat message as the assistant needs it. */
data class MessageInfo(val sender: String, val app: String, val text: String, val canReply: Boolean)

data class AppRef(val label: String, val pkg: String)

/** Everything the assistant can do to the app; the Android side implements it, tests use a fake. */
interface VoiceActions {
    fun openScreen(screen: VoiceScreen)
    suspend fun navigateTo(candidates: List<String>): String?
    fun goSaved(kind: PlaceKind): String?
    fun stopNavigation(): Boolean
    fun findStops()
    suspend fun findNearest(term: String, along: Boolean): String?
    suspend fun alternativeRoute(): Boolean
    fun navInfo(question: NavQuestion): String?
    fun setAvoid(highways: Boolean?, tolls: Boolean?)
    fun recalculate(): Boolean
    suspend fun saveHere(kind: PlaceKind): String?

    fun canReadContacts(): Boolean
    fun contacts(): List<Contact>
    fun recents(): List<RecentCall>
    fun dial(number: String, name: String?)
    fun callPhase(): CallPhase
    fun answerCall()
    fun rejectCall()
    fun muteCall()
    fun setSpeaker(on: Boolean)

    fun lastMessage(): MessageInfo?
    fun reply(text: String): Boolean

    fun mediaPlay()
    fun mediaPause()
    fun mediaNext()
    fun mediaPrevious()
    fun volumeUp()
    fun volumeDown()
    fun volumeSet(percent: Int)
    fun playSearch(query: String): Boolean

    suspend fun info(question: InfoQuestion): String?
    fun apps(): List<AppRef>
    fun launchApp(pkg: String): Boolean
    fun setTheme(mode: ThemeMode)
    fun setBrightness(delta: Int)
    fun setWarningVoice(on: Boolean)
    fun setMiles(miles: Boolean)
    fun startSos()
    fun showHelp()

    // Added later; the defaults keep older fakes working.
    /** A stop on the way to the current destination (home/work by [saved], otherwise searched); the stop's name, or null. */
    suspend fun addStop(candidates: List<String>, saved: PlaceKind?): String? = null
    fun canSendSms(): Boolean = false
    fun sendSms(number: String, text: String): Boolean = false
    /** The contact the rider assigned to a nickname key ("annem"), if any. */
    fun nickname(key: String): Contact? = null
    fun setNickname(key: String, contact: Contact) {}
    fun setReminder(minutes: Int, what: String?): Boolean = false
    fun setProactive(on: Boolean) {}
}

/** What the recogniser heard: the alternatives best first, and how sure it was (0..1) when it says. */
data class VoiceHeard(val alternatives: List<String>, val confidence: Float? = null)

/** What the assistant waits for after it asked something. */
private sealed class Expect {
    object None : Expect()
    class YesNo(val prompt: String, val onYes: () -> Unit) : Expect()
    class Choice(val prompt: String, val options: List<Pair<String, () -> Unit>>) : Expect()
    class Dictation(val prompt: String, val onText: (String) -> Unit) : Expect()
}

/**
 * Hands-free control: listens once, parses the Turkish phrase ([VoiceParser]) and runs it through [VoiceActions].
 * It can ask back ("Hangi Ali?", "Gönderilsin mi?") and listens for the answer. A call is never placed at once:
 * it counts down so a wrong guess can be cancelled with a tap; a message is only sent after a spoken "evet".
 */
class VoiceAssistant(
    private val text: (Int) -> String,
    private val scope: CoroutineScope,
    private val listener: SpeechListener,
    private val speaker: Speaker,
    private val actions: VoiceActions,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    /** Called once per command with what was heard and whether it was understood (for the command history). */
    private val history: (heard: String, understood: Boolean) -> Unit = { _, _ -> },
    /** After an answer, listen once more for a few seconds without beeping (setting; off by default here). */
    private val continuous: () -> Boolean = { false },
    private val context: DialogContext = DialogContext(),
) {
    /** The speech engine, injectable for tests. */
    interface SpeechListener {
        fun available(): Boolean
        /** Listens once; [onResult] gets what was heard (no alternatives when nothing was understood). */
        fun start(onPartial: (String) -> Unit, onResult: (VoiceHeard) -> Unit)
        /** The same without the start and end beeps and the buzz (the follow-up listen after an answer). */
        fun startQuiet(onPartial: (String) -> Unit, onResult: (VoiceHeard) -> Unit) = start(onPartial, onResult)
        fun cancel()
    }

    private val _ui = MutableStateFlow(VoiceUi())
    val ui: StateFlow<VoiceUi> = _ui
    private var job: Job? = null
    private var expect: Expect = Expect.None
    private var attempts = 0
    private var allowFollowUp = true
    private var followUpTurn = false
    private var followUpWatchdog: Job? = null
    /** The current question was the assistant's own idea: an unanswered one is dropped without a word. */
    private var proactiveAsk = false

    /** [searchFallback]: on the map's search box, an unrecognised phrase is simply searched for. */
    fun listen(searchFallback: Boolean = false) {
        job?.cancel()
        followUpWatchdog?.cancel()
        followUpTurn = false
        proactiveAsk = false
        allowFollowUp = true
        expect = Expect.None
        attempts = 0
        startListening(searchFallback)
    }

    /**
     * The assistant's own question ("Yağmur geliyor, hava durumunu okuyayım mı?"): asked and listened for only when it is
     * idle; "evet" runs [then]. A silent or negative answer is dropped without a word. False when it is busy.
     */
    fun proactive(question: String, then: VoiceCommand): Boolean {
        if (_ui.value.phase != VoicePhase.IDLE || expect !is Expect.None || !listener.available()) return false
        allowFollowUp = false
        proactiveAsk = true
        ask(Expect.YesNo(question) { proactiveAsk = false; run(then) })
        return true
    }

    private fun startListening(searchFallback: Boolean) {
        if (!listener.available()) {
            say(text(R.string.voice_unavailable))
            return
        }
        _ui.update { VoiceUi(VoicePhase.LISTENING, choices = it.choices) }
        listener.start(
            onPartial = { partial -> _ui.update { if (it.phase == VoicePhase.LISTENING) it.copy(text = partial) else it } },
            onResult = { heard -> handle(heard.alternatives, heard.confidence, searchFallback) },
        )
    }

    fun cancel() {
        job?.cancel()
        followUpWatchdog?.cancel()
        followUpTurn = false
        proactiveAsk = false
        listener.cancel()
        expect = Expect.None
        _ui.value = VoiceUi()
    }

    /** A tap on one of the shown choices. */
    fun choose(index: Int) {
        val e = expect as? Expect.Choice ?: return
        e.options.getOrNull(index)?.let { (_, action) -> listener.cancel(); expect = Expect.None; action() }
    }

    private fun handle(alternatives: List<String>, confidence: Float?, searchFallback: Boolean) {
        val e = expect
        if (e !is Expect.None) {
            handleAnswer(e, alternatives)
            return
        }
        if (alternatives.isEmpty()) {
            say(text(R.string.voice_not_heard))
            return
        }
        // What was just talked about ("onu ara", "oraya git", a correction) first; then the recogniser's alternatives, best first.
        val command = alternatives.firstNotNullOfOrNull { context.resolve(it) }
            ?: alternatives.map(VoiceParser::parse).firstOrNull { it !is VoiceCommand.Unknown }
        history(alternatives.first(), command != null)
        if (command == null) {
            if (searchFallback) {
                actions.openScreen(VoiceScreen.MAP)
                scope.launch { actions.navigateTo(listOf(alternatives.first())) }
                _ui.value = VoiceUi()
                return
            }
            // Nothing fits, but one harmless command nearly does: ask instead of "anlamadım".
            val guess = alternatives.firstNotNullOfOrNull { VoiceLoose.suggestion(it) }
            if (guess != null) {
                ask(Expect.YesNo(text(guess.askRes)) { run(guess.command) })
            } else {
                say(text(R.string.voice_unknown) + " " + alternatives.first())
            }
            return
        }
        // A low score (0 means "no score") makes a wrong guess likely: say what was heard and ask.
        if (confidence != null && confidence > 0f && confidence < LOW_CONFIDENCE && command !is VoiceCommand.Help) {
            ask(Expect.YesNo(text(R.string.voice_did_you_say) + " " + alternatives.first() + "?") { run(command) })
            return
        }
        run(command)
    }

    private fun handleAnswer(e: Expect, alternatives: List<String>) {
        if (alternatives.isEmpty()) {
            giveUp(); return
        }
        val answers = alternatives.map(VoiceParser::parseAnswer)
        when (e) {
            is Expect.YesNo -> when {
                answers.any { it is VoiceAnswer.Yes } -> { expect = Expect.None; e.onYes() }
                answers.any { it is VoiceAnswer.No || it is VoiceAnswer.Cancel } -> {
                    expect = Expect.None
                    if (proactiveAsk) { proactiveAsk = false; _ui.value = VoiceUi() } else say(text(R.string.voice_cancelled))
                }
                else -> askAgain(e.prompt)
            }
            is Expect.Choice -> {
                val ordinal = answers.firstNotNullOfOrNull { (it as? VoiceAnswer.Ordinal)?.index }
                val byName = alternatives.firstNotNullOfOrNull { heard ->
                    Turkish.bestMatches(heard, e.options.indices.toList(), threshold = 0.8) { e.options[it].first }.singleOrNull()
                }
                val picked = ordinal?.takeIf { it < e.options.size } ?: byName
                when {
                    answers.any { it is VoiceAnswer.Cancel || it is VoiceAnswer.No } -> { expect = Expect.None; say(text(R.string.voice_cancelled)) }
                    picked != null -> { expect = Expect.None; e.options[picked].second() }
                    else -> askAgain(e.prompt)
                }
            }
            is Expect.Dictation -> {
                if (answers.any { it is VoiceAnswer.Cancel }) {
                    expect = Expect.None; say(text(R.string.voice_cancelled))
                } else {
                    expect = Expect.None
                    e.onText(alternatives.first().trim())
                }
            }
            Expect.None -> Unit
        }
    }

    /** One more try with the question repeated; after that the assistant gives up rather than guess. */
    private fun askAgain(prompt: String) {
        attempts++
        if (attempts > 1) { giveUp(); return }
        askListen(prompt)
    }

    private fun giveUp() {
        expect = Expect.None
        if (proactiveAsk) { proactiveAsk = false; _ui.value = VoiceUi(); return }
        say(text(R.string.voice_gave_up))
    }

    /** Says [prompt], shows it, then listens for the answer. */
    private fun askListen(prompt: String, choices: List<String> = emptyList()) {
        _ui.value = VoiceUi(VoicePhase.MESSAGE, prompt, choices = choices)
        speaker.speakThen(prompt) { startListening(false) }
    }

    private fun ask(e: Expect) {
        expect = e
        attempts = 0
        when (e) {
            is Expect.YesNo -> askListen(e.prompt)
            is Expect.Choice -> askListen(e.prompt, e.options.map { it.first })
            is Expect.Dictation -> askListen(e.prompt)
            Expect.None -> Unit
        }
    }

    private fun run(command: VoiceCommand) {
        if (command !is VoiceCommand.Chain) context.rememberCommand(command)
        when (command) {
            is VoiceCommand.NavigateTo -> {
                actions.openScreen(VoiceScreen.MAP)
                _ui.value = VoiceUi(VoicePhase.MESSAGE, text(R.string.voice_searching) + " " + command.candidates.first())
                job = scope.launch {
                    val place = actions.navigateTo(command.candidates)
                    if (place != null) { context.rememberPlace(place); done(text(R.string.voice_route_ready) + " " + place) } else say(text(R.string.no_results))
                }
            }
            VoiceCommand.GoHome -> goSaved(PlaceKind.HOME, R.string.voice_no_home)
            VoiceCommand.GoWork -> goSaved(PlaceKind.WORK, R.string.voice_no_work)
            VoiceCommand.GoParked -> goSaved(PlaceKind.PARKED, R.string.voice_no_parked)
            VoiceCommand.StopNavigation -> if (actions.stopNavigation()) done(text(R.string.voice_nav_stopped)) else say(text(R.string.voice_no_route))
            VoiceCommand.FindStops -> { actions.openScreen(VoiceScreen.MAP); actions.findStops(); done(text(R.string.voice_looking_stops)) }
            is VoiceCommand.FindNearest -> {
                actions.openScreen(VoiceScreen.MAP)
                _ui.value = VoiceUi(VoicePhase.MESSAGE, text(R.string.voice_searching) + " " + command.label)
                job = scope.launch {
                    val place = actions.findNearest(command.term, command.along)
                    if (place != null) done(command.label + ": " + place) else say(text(R.string.no_results))
                }
            }
            is VoiceCommand.NavInfo -> actions.navInfo(command.question)?.let { done(it) } ?: say(text(R.string.voice_no_route))
            is VoiceCommand.AvoidHighways -> { actions.setAvoid(command.on, null); done(text(if (command.on) R.string.voice_avoid_highways_on else R.string.voice_avoid_highways_off)) }
            is VoiceCommand.AvoidTolls -> { actions.setAvoid(null, command.on); done(text(if (command.on) R.string.voice_avoid_tolls_on else R.string.voice_avoid_tolls_off)) }
            VoiceCommand.Recalculate -> if (actions.recalculate()) done(text(R.string.voice_recalculated)) else say(text(R.string.voice_no_route))
            VoiceCommand.AlternativeRoute -> {
                _ui.value = VoiceUi(VoicePhase.MESSAGE, text(R.string.voice_looking_alternative))
                job = scope.launch { if (actions.alternativeRoute()) done(text(R.string.voice_alternative_ready)) else say(text(R.string.voice_no_alternative)) }
            }
            VoiceCommand.SaveFavoriteHere -> saveHere(PlaceKind.FAVORITE)
            VoiceCommand.SetHomeHere -> saveHere(PlaceKind.HOME)
            VoiceCommand.SetWorkHere -> saveHere(PlaceKind.WORK)

            is VoiceCommand.Call -> callPerson(command.name)
            is VoiceCommand.CallDigits -> confirmCall(Contact(command.number, command.number))
            VoiceCommand.CallLast -> callFromRecents { it.kind == CallKind.OUTGOING }
            VoiceCommand.CallMissed -> callFromRecents { it.kind == CallKind.MISSED }
            VoiceCommand.ShowRecents -> { actions.openScreen(VoiceScreen.PHONE); _ui.value = VoiceUi() }
            VoiceCommand.AnswerCall -> when {
                actions.callPhase() == CallPhase.RINGING -> { actions.answerCall(); _ui.value = VoiceUi() }
                // Nothing is ringing: "cevapla" most likely means the chat message.
                actions.lastMessage()?.canReply == true -> run(VoiceCommand.ReplyMessage(null))
                else -> { actions.openScreen(VoiceScreen.PHONE); _ui.value = VoiceUi() }
            }
            VoiceCommand.RejectCall -> if (actions.callPhase() != CallPhase.IDLE) { actions.rejectCall(); _ui.value = VoiceUi() } else say(text(R.string.voice_no_call))
            VoiceCommand.MuteCall -> if (actions.callPhase() == CallPhase.ACTIVE) { actions.muteCall(); _ui.value = VoiceUi() } else say(text(R.string.voice_no_call))
            is VoiceCommand.SpeakerCall -> if (actions.callPhase() == CallPhase.ACTIVE) { actions.setSpeaker(command.on); _ui.value = VoiceUi() } else say(text(R.string.voice_no_call))

            VoiceCommand.ReadMessage -> actions.lastMessage()?.let { speakAndShow(it.sender + ": " + it.text) } ?: say(text(R.string.voice_no_message))
            VoiceCommand.WhoWrote -> actions.lastMessage()?.let { speakAndShow(it.sender + ", " + it.app) } ?: say(text(R.string.voice_no_message))
            is VoiceCommand.ReplyMessage -> reply(command.text)

            VoiceCommand.MusicPlay -> { actions.mediaPlay(); done(text(R.string.voice_music_play)) }
            VoiceCommand.MusicPause -> { actions.mediaPause(); done(text(R.string.voice_music_pause)) }
            VoiceCommand.MusicNext -> { actions.mediaNext(); done(text(R.string.voice_music_next)) }
            VoiceCommand.MusicPrevious -> { actions.mediaPrevious(); done(text(R.string.voice_music_previous)) }
            is VoiceCommand.PlaySearch -> if (actions.playSearch(command.query)) done(text(R.string.voice_playing) + " " + command.query) else say(text(R.string.voice_cannot_play))
            VoiceCommand.VolumeUp -> { actions.volumeUp(); _ui.value = VoiceUi() }
            VoiceCommand.VolumeDown -> { actions.volumeDown(); _ui.value = VoiceUi() }
            is VoiceCommand.VolumeSet -> { actions.volumeSet(command.percent); done(text(R.string.voice_volume) + " %" + command.percent) }

            is VoiceCommand.Info -> {
                job = scope.launch { actions.info(command.question)?.let { speakAndShow(it) } ?: say(text(R.string.voice_no_info)) }
            }
            is VoiceCommand.OpenApp -> openApp(command.name)
            is VoiceCommand.OpenScreen -> { actions.openScreen(command.screen); _ui.value = VoiceUi() }
            is VoiceCommand.SetTheme -> { actions.setTheme(command.mode); _ui.value = VoiceUi() }
            is VoiceCommand.Brightness -> { actions.setBrightness(command.delta); _ui.value = VoiceUi() }
            is VoiceCommand.WarningVoice -> { actions.setWarningVoice(command.on); done(text(if (command.on) R.string.voice_warnings_on else R.string.voice_warnings_off)) }
            is VoiceCommand.SetMiles -> { actions.setMiles(command.miles); done(text(if (command.miles) R.string.voice_miles else R.string.voice_km)) }

            is VoiceCommand.SendSms -> sendSms(command.name)
            is VoiceCommand.AddStop -> addStop(command)
            is VoiceCommand.Reminder -> {
                if (actions.setReminder(command.minutes, command.what)) done(text(R.string.voice_reminder_set) + " " + spokenMinutes(command.minutes))
                else say(text(R.string.voice_reminder_failed))
            }
            is VoiceCommand.Chain -> runChain(command)
            VoiceCommand.Repeat -> context.lastSpoken?.let { done(it) } ?: say(text(R.string.voice_nothing_to_repeat))
            VoiceCommand.Undo -> context.lastCommand?.inverse()?.let { run(it) } ?: say(text(R.string.voice_nothing_to_undo))
            VoiceCommand.StopProactive -> { actions.setProactive(false); done(text(R.string.voice_proactive_off)) }
            VoiceCommand.Help -> { actions.showHelp(); _ui.value = VoiceUi() }
            VoiceCommand.Emergency -> ask(Expect.YesNo(text(R.string.voice_sos_ask)) { actions.startSos(); _ui.value = VoiceUi() })
            is VoiceCommand.Unknown -> say(text(R.string.voice_unknown) + " " + command.heard)
        }
    }

    private fun goSaved(kind: PlaceKind, missing: Int) {
        val name = actions.goSaved(kind)
        if (name == null) { say(text(missing)); return }
        actions.openScreen(VoiceScreen.MAP)
        context.rememberPlace(name)
        done(text(R.string.voice_route_ready) + " " + name)
    }

    private fun saveHere(kind: PlaceKind) {
        job = scope.launch {
            val name = actions.saveHere(kind)
            if (name != null) done(text(R.string.voice_saved) + " " + name) else say(text(R.string.voice_no_fix))
        }
    }

    private fun callPerson(name: String) = withContact(name) { confirmCall(it) }

    /**
     * Finds the person for a heard name and hands them to [then]. A nickname ("annemi") uses the contact the rider
     * assigned to it; the first time, when no contact is literally called that, the rider is asked who it is.
     */
    private fun withContact(name: String, then: (Contact) -> Unit) {
        if (!actions.canReadContacts()) { say(text(R.string.voice_no_contacts_permission)); return }
        val key = Nicknames.canonical(name)
        if (key != null) {
            actions.nickname(key)?.let { then(it); return }
            if (VoiceParser.matchContact(name, actions.contacts()).isEmpty()) {
                ask(Expect.Dictation(Nicknames.asking(key) + " " + text(R.string.voice_nick_who)) { heard ->
                    pickContact(heard) { c -> actions.setNickname(key, c); then(c) }
                })
                return
            }
        }
        pickContact(name, then)
    }

    private fun pickContact(name: String, then: (Contact) -> Unit) {
        val found = VoiceParser.matchContact(name, actions.contacts())
        when {
            found.isEmpty() -> say(text(R.string.voice_contact_not_found) + " " + name)
            found.size == 1 -> then(found.first())
            else -> {
                val top = found.take(MAX_CHOICES)
                val prompt = text(R.string.voice_which) + " " + top.joinToString(", ") { it.name }
                ask(Expect.Choice(prompt, top.map { c -> c.name to { then(c) } }))
            }
        }
    }

    /** "Ali'ye mesaj at": who, what to write, read it back, send only after a spoken yes. */
    private fun sendSms(name: String) {
        if (!actions.canSendSms()) { say(text(R.string.voice_no_sms_permission)); return }
        withContact(name) { c ->
            context.rememberContact(c)
            ask(Expect.Dictation(text(R.string.voice_what_to_write) + " " + c.name) { body ->
                ask(Expect.YesNo("${c.name}: “$body”. " + text(R.string.voice_send_ask)) {
                    if (actions.sendSms(c.number, body)) done(text(R.string.voice_sent)) else say(text(R.string.voice_send_failed))
                })
            })
        }
    }

    private fun addStop(command: VoiceCommand.AddStop) {
        _ui.value = VoiceUi(VoicePhase.MESSAGE, text(R.string.voice_adding_stop))
        job = scope.launch {
            val stop = actions.addStop(command.candidates, command.saved)
            if (stop != null) { context.rememberPlace(stop); done(text(R.string.voice_stop_added) + " " + stop) } else say(text(R.string.voice_stop_failed))
        }
    }

    /** Two commands in one sentence: quick ones first, then the slow ones, a call or message last; two of those are refused. */
    private fun runChain(chain: VoiceCommand.Chain) {
        if (chain.first.isCritical() && chain.second.isCritical()) { say(text(R.string.voice_chain_one_critical)); return }
        val (a, b) = listOf(chain.first, chain.second).sortedBy(::chainWeight)
        context.rememberCommand(b)
        run(a)
        if (chainWeight(a) == 1) {
            val firstJob = job
            scope.launch { firstJob?.join(); run(b) }
        } else run(b)
    }

    private fun chainWeight(c: VoiceCommand): Int = when {
        c.isCritical() -> 2
        c is VoiceCommand.NavigateTo || c is VoiceCommand.FindNearest || c is VoiceCommand.AlternativeRoute || c is VoiceCommand.AddStop ||
            c is VoiceCommand.Info || c == VoiceCommand.SaveFavoriteHere || c == VoiceCommand.SetHomeHere || c == VoiceCommand.SetWorkHere -> 1
        else -> 0
    }

    private fun spokenMinutes(minutes: Int): String =
        if (minutes % 60 == 0) "${minutes / 60} " + text(R.string.voice_hour_word) else "$minutes " + text(R.string.voice_minute_word)

    private fun callFromRecents(match: (RecentCall) -> Boolean) {
        val recent = actions.recents().firstOrNull(match)
        if (recent == null) { say(text(R.string.voice_no_recent)); return }
        confirmCall(Contact(recent.name ?: recent.number, recent.number))
    }

    private fun confirmCall(contact: Contact) {
        context.rememberContact(contact)
        val title = text(R.string.voice_calling) + " " + contact.name
        _ui.value = VoiceUi(VoicePhase.CONFIRM, title = title, secondsLeft = CONFIRM_SECONDS)
        speaker.speak(title)
        job = scope.launch {
            for (left in CONFIRM_SECONDS - 1 downTo 1) {
                pause(1000)
                _ui.update { it.copy(secondsLeft = left) }
            }
            pause(1000)
            _ui.value = VoiceUi()
            actions.dial(contact.number, contact.name)
        }
    }

    private fun reply(typed: String?) {
        val m = actions.lastMessage()
        when {
            m == null -> say(text(R.string.voice_no_message))
            !m.canReply -> say(text(R.string.voice_cannot_reply))
            typed != null -> confirmSend(m, typed)
            else -> ask(Expect.Dictation(text(R.string.voice_what_to_write) + " " + m.sender) { body -> confirmSend(m, body) })
        }
    }

    private fun confirmSend(m: MessageInfo, body: String) {
        ask(Expect.YesNo("${m.sender}: “$body”. " + text(R.string.voice_send_ask)) {
            if (actions.reply(body)) done(text(R.string.voice_sent)) else say(text(R.string.voice_send_failed))
        })
    }

    private fun openApp(name: String) {
        val matches = Turkish.bestMatches(name, actions.apps(), threshold = 0.75) { it.label }
        when {
            matches.isEmpty() -> say(text(R.string.voice_app_not_found) + " " + name)
            matches.size == 1 -> launch(matches.first())
            else -> {
                val top = matches.take(MAX_CHOICES)
                ask(Expect.Choice(text(R.string.voice_which) + " " + top.joinToString(", ") { it.label }, top.map { a -> a.label to { launch(a) } }))
            }
        }
    }

    private fun launch(app: AppRef) {
        if (actions.launchApp(app.pkg)) { context.rememberApp(app.label); _ui.value = VoiceUi() } else say(text(R.string.voice_app_not_found) + " " + app.label)
    }

    /** A short result: spoken and shown, then it clears itself. With the follow-up setting on, the rider may add one more command. */
    private fun done(message: String) {
        context.rememberSpoken(message)
        if (allowFollowUp && continuous()) {
            allowFollowUp = false
            speaker.speakThen(message) { followUp() }
        } else {
            speaker.speak(message)
        }
        say(message, speak = false)
    }

    /** Listens once more for a few seconds without beeping; silence just closes the card. */
    private fun followUp() {
        if (expect !is Expect.None || _ui.value.phase == VoicePhase.CONFIRM || !listener.available()) return
        followUpTurn = true
        _ui.value = VoiceUi(VoicePhase.LISTENING)
        listener.startQuiet(
            onPartial = { partial -> _ui.update { if (it.phase == VoicePhase.LISTENING) it.copy(text = partial) else it } },
            onResult = { heard ->
                if (followUpTurn) {
                    followUpTurn = false
                    followUpWatchdog?.cancel()
                    if (heard.alternatives.isEmpty()) _ui.value = VoiceUi() else handle(heard.alternatives, heard.confidence, false)
                }
            },
        )
        followUpWatchdog = scope.launch {
            pause(FOLLOW_UP_MS)
            if (followUpTurn) { followUpTurn = false; listener.cancel(); _ui.value = VoiceUi() }
        }
    }

    private fun speakAndShow(message: String) = done(message)

    private fun say(message: String, speak: Boolean = false) {
        if (speak) speaker.speak(message)
        job?.cancel()
        _ui.value = VoiceUi(VoicePhase.MESSAGE, message)
        job = scope.launch {
            pause(MESSAGE_MS)
            _ui.update { if (it.phase == VoicePhase.MESSAGE) VoiceUi() else it }
        }
    }

    private companion object {
        const val CONFIRM_SECONDS = 3
        const val MESSAGE_MS = 3500L
        const val FOLLOW_UP_MS = 5000L
        const val MAX_CHOICES = 3
        /** Below this recogniser score a command is confirmed before it runs. */
        const val LOW_CONFIDENCE = 0.35f
    }
}
