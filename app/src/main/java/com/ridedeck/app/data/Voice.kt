package com.ridedeck.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Anything that can say a sentence out loud; lets the navigation logic run in plain unit tests. */
interface Speaker {
    fun speak(text: String)

    /** Says [text] after whatever is being said (a turn-by-turn prompt is never cut off by it). */
    fun speakQueued(text: String) = speak(text)

    /** Says [text] after whatever is being said, then calls [then] (at once when there is nothing to say). */
    fun speakThen(text: String, then: () -> Unit) {
        speakQueued(text)
        then()
    }

    fun stop()
    fun shutdown()
}

/** Turn-by-turn announcements that duck the music while speaking. */
class Voice(context: Context) : Speaker {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    @Volatile private var ready = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            tts.language = Locale.getDefault()
            tts.setAudioAttributes(attributes)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = finished(utteranceId)
                // A navigation prompt may cut a queued sentence off; its follow-up must still run.
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished(utteranceId)
            })
            ready = true
        }
    }

    override fun speak(text: String) {
        if (!ready || text.isBlank()) return
        audio.requestAudioFocus(focusRequest)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    private val thenCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var thenCounter = 0

    override fun speakQueued(text: String) {
        if (!ready || text.isBlank()) return
        audio.requestAudioFocus(focusRequest)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "queued-${thenCounter++}")
    }

    override fun speakThen(text: String, then: () -> Unit) {
        if (!ready || text.isBlank()) { then(); return }
        val id = "then-${thenCounter++}"
        thenCallbacks[id] = then
        audio.requestAudioFocus(focusRequest)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    private fun finished(id: String?) {
        audio.abandonAudioFocusRequest(focusRequest)
        val callback = id?.let { thenCallbacks.remove(it) } ?: return
        mainHandler.post(callback)
    }

    override fun stop() {
        if (ready) tts.stop()
        audio.abandonAudioFocusRequest(focusRequest)
    }

    override fun shutdown() {
        tts.shutdown()
    }
}
