package dev.ygtlab.carplay.data

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
                override fun onDone(utteranceId: String?) {
                    audio.abandonAudioFocusRequest(focusRequest)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    audio.abandonAudioFocusRequest(focusRequest)
                }
            })
            ready = true
        }
    }

    override fun speak(text: String) {
        if (!ready || text.isBlank()) return
        audio.requestAudioFocus(focusRequest)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    override fun stop() {
        if (ready) tts.stop()
        audio.abandonAudioFocusRequest(focusRequest)
    }

    override fun shutdown() {
        tts.shutdown()
    }
}
