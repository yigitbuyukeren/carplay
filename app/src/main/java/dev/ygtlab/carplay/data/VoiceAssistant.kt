package dev.ygtlab.carplay.data

import dev.ygtlab.carplay.R
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
)

/**
 * Hands-free control: listens once, parses the Turkish phrase ([VoiceParser]) and runs it on the existing
 * controllers. A call is never placed at once: it counts down so a wrong guess can be cancelled with a tap.
 * Outside collaborators are injected so the logic needs no Android.
 */
class VoiceAssistant(
    private val text: (Int) -> String,
    private val scope: CoroutineScope,
    private val listener: SpeechListener,
    private val nav: NavigationController,
    private val speaker: Speaker,
    private val places: () -> List<SavedPlace>,
    private val contacts: () -> List<Contact>,
    private val canReadContacts: () -> Boolean,
    private val dial: (String) -> Unit,
    private val media: MediaControls,
    private val openMap: () -> Unit,
) {
    /** The speech engine, injectable for tests. */
    interface SpeechListener {
        fun available(): Boolean
        fun start(onPartial: (String) -> Unit, onResult: (List<String>) -> Unit)
        fun cancel()
    }

    interface MediaControls {
        fun play()
        fun pause()
        fun next()
        fun previous()
        fun volumeUp()
        fun volumeDown()
    }

    private val _ui = MutableStateFlow(VoiceUi())
    val ui: StateFlow<VoiceUi> = _ui
    private var job: Job? = null

    /** [searchFallback]: on the map's search box, an unrecognised phrase is simply searched for. */
    fun listen(searchFallback: Boolean = false) {
        job?.cancel()
        if (!listener.available()) {
            say(text(R.string.voice_unavailable))
            return
        }
        _ui.value = VoiceUi(VoicePhase.LISTENING)
        listener.start(
            onPartial = { partial -> _ui.update { if (it.phase == VoicePhase.LISTENING) it.copy(text = partial) else it } },
            onResult = { heard -> handle(heard, searchFallback) },
        )
    }

    fun cancel() {
        job?.cancel()
        listener.cancel()
        _ui.value = VoiceUi()
    }

    private fun handle(alternatives: List<String>, searchFallback: Boolean) {
        if (alternatives.isEmpty()) {
            say(text(R.string.voice_not_heard))
            return
        }
        // The recogniser lists alternatives best first; the first one that is a real command wins.
        val command = alternatives.map(VoiceParser::parse).firstOrNull { it !is VoiceCommand.Unknown }
        if (command == null) {
            if (searchFallback) {
                val query = alternatives.first()
                nav.onQueryChange(query)
                nav.search()
                _ui.value = VoiceUi()
            } else {
                say(text(R.string.voice_unknown) + " " + alternatives.first())
            }
            return
        }
        run(command)
    }

    private fun run(command: VoiceCommand) {
        when (command) {
            is VoiceCommand.NavigateTo -> {
                openMap()
                _ui.value = VoiceUi(VoicePhase.MESSAGE, text(R.string.voice_searching) + " " + command.candidates.first())
                job = scope.launch {
                    val ok = nav.goToFirstMatch(command.candidates)
                    if (ok) _ui.value = VoiceUi() else say(text(R.string.no_results))
                }
            }
            VoiceCommand.GoHome -> goSaved(PlaceKind.HOME, R.string.voice_no_home)
            VoiceCommand.GoWork -> goSaved(PlaceKind.WORK, R.string.voice_no_work)
            VoiceCommand.StopNavigation -> { nav.stopNavigation(); done(R.string.voice_nav_stopped) }
            VoiceCommand.FindStops -> { openMap(); nav.findFuel(); _ui.value = VoiceUi() }
            is VoiceCommand.Call -> call(command.name)
            VoiceCommand.MusicPlay -> { media.play(); done(R.string.voice_music_play) }
            VoiceCommand.MusicPause -> { media.pause(); done(R.string.voice_music_pause) }
            VoiceCommand.MusicNext -> { media.next(); done(R.string.voice_music_next) }
            VoiceCommand.MusicPrevious -> { media.previous(); done(R.string.voice_music_previous) }
            VoiceCommand.VolumeUp -> { media.volumeUp(); _ui.value = VoiceUi() }
            VoiceCommand.VolumeDown -> { media.volumeDown(); _ui.value = VoiceUi() }
            is VoiceCommand.Unknown -> say(text(R.string.voice_unknown) + " " + command.heard)
        }
    }

    private fun goSaved(kind: PlaceKind, missing: Int) {
        val place = places().firstOrNull { it.kind == kind }?.place
        if (place == null) { say(text(missing)); return }
        openMap()
        nav.selectPlace(place)
        _ui.value = VoiceUi()
    }

    private fun call(name: String) {
        if (!canReadContacts()) { say(text(R.string.voice_no_contacts_permission)); return }
        val found = VoiceParser.matchContact(name, contacts()).firstOrNull()
        if (found == null) { say(text(R.string.voice_contact_not_found) + " " + name); return }
        val title = text(R.string.voice_calling) + " " + found.name
        _ui.value = VoiceUi(VoicePhase.CONFIRM, title = title, secondsLeft = CONFIRM_SECONDS)
        speaker.speak(title)
        job = scope.launch {
            for (left in CONFIRM_SECONDS - 1 downTo 1) {
                delay(1000)
                _ui.update { it.copy(secondsLeft = left) }
            }
            delay(1000)
            _ui.value = VoiceUi()
            dial(found.number)
        }
    }

    private fun done(message: Int) = say(text(message))

    /** A short message the rider can read; it clears itself. */
    private fun say(message: String) {
        job?.cancel()
        _ui.value = VoiceUi(VoicePhase.MESSAGE, message)
        job = scope.launch {
            delay(MESSAGE_MS)
            _ui.update { if (it.phase == VoicePhase.MESSAGE) VoiceUi() else it }
        }
    }

    private companion object {
        const val CONFIRM_SECONDS = 3
        const val MESSAGE_MS = 3500L
    }
}
