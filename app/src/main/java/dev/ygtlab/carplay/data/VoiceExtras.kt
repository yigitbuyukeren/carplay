package dev.ygtlab.carplay.data

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.view.KeyEvent

/** Whether Turkish recognition works without a network on this phone. */
enum class OfflineVoice { UNKNOWN, INSTALLED, MISSING, UNSUPPORTED }

object OfflineVoiceCheck {
    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")

    /** Asks the recogniser which languages it has on the device (Android 13+); [onResult] runs on the main thread. */
    fun check(context: Context, onResult: (OfflineVoice) -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isRecognitionAvailable(context)) { onResult(OfflineVoice.UNKNOWN); return }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.checkRecognitionSupport(intent(), context.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                val turkish = { l: List<String> -> l.any { it.startsWith("tr", ignoreCase = true) } }
                onResult(
                    when {
                        turkish(support.installedOnDeviceLanguages) -> OfflineVoice.INSTALLED
                        turkish(support.supportedOnDeviceLanguages) || turkish(support.pendingOnDeviceLanguages) -> OfflineVoice.MISSING
                        else -> OfflineVoice.UNSUPPORTED
                    }
                )
                r.destroy()
            }

            override fun onError(error: Int) { onResult(OfflineVoice.UNKNOWN); r.destroy() }
        })
    }

    /** Asks the system to download the Turkish on-device model (Android 13+). */
    fun download(context: Context) {
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isRecognitionAvailable(context)) return
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.triggerModelDownload(intent())
        Handler(Looper.getMainLooper()).postDelayed({ r.destroy() }, 5000)
    }
}

/**
 * "Hey MotoDash", experimental and off by default: while the dashboard is on screen it listens with the
 * on-device recogniser only (never the network one) for the wake phrase, restarting after every short session.
 * It needs Android 13+ with the Turkish offline model; without them it stays silent.
 */
class WakeWord(private val context: Context, private val onWake: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var paused = false

    companion object {
        private val PHRASES = listOf("hey motodash", "hey moto dash", "hey motodaş", "hey moto daş", "hey motodeş", "hey moto deş", "motodash", "moto dash", "motodaş", "moto daş")

        /** True when the heard text contains the wake phrase (loosely, since the recogniser spells it any way). */
        fun matches(heard: String): Boolean {
            val t = Turkish.normalize(heard).replace('ş', 's')
            return PHRASES.any { t.contains(it.replace('ş', 's')) } || t.replace(" ", "").contains("heymotodas")
        }

        fun available(context: Context): Boolean =
            Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    fun start() {
        if (running || !available(context)) return
        running = true
        paused = false
        listenOnce()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    /** The assistant is using the microphone: wait. */
    fun pause() {
        paused = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    fun resume() {
        if (!paused) return
        paused = false
        if (running) handler.postDelayed(::listenOnce, RESTART_MS)
    }

    private fun listenOnce() {
        if (!running || paused || Build.VERSION.SDK_INT < 33) return
        recognizer?.destroy()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            private fun check(bundle: Bundle?): Boolean {
                val heard = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (heard.any(::matches)) {
                    pause()
                    onWake()
                    return true
                }
                return false
            }

            override fun onPartialResults(partialResults: Bundle?) { check(partialResults) }
            override fun onResults(results: Bundle?) { if (!check(results)) again() }
            override fun onError(error: Int) = again()
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        )
    }

    private fun again() {
        if (!running || paused) return
        handler.postDelayed(::listenOnce, RESTART_MS)
    }

    private val RESTART_MS = 400L
}

/**
 * A double press of a Bluetooth headset's play/pause (or hook) key starts the voice assistant. While it is on, the
 * dashboard holds a media session so it sees the key; a single press is passed on to the music after a short wait.
 */
class VoiceButtonTrigger(
    private val context: Context,
    private val onDoublePress: () -> Unit,
    private val onSinglePress: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var lastDownAt = 0L
    private var pendingSingle: Runnable? = null

    fun start() {
        if (session != null) return
        val s = MediaSession(context, "MotoDashVoiceButton")
        s.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                @Suppress("DEPRECATION")
                val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
                if (event.keyCode !in KEYS) return false
                val now = event.eventTime
                if (now - lastDownAt <= DOUBLE_MS && pendingSingle != null) {
                    handler.removeCallbacks(pendingSingle!!)
                    pendingSingle = null
                    lastDownAt = 0L
                    onDoublePress()
                } else {
                    lastDownAt = now
                    pendingSingle = Runnable { pendingSingle = null; onSinglePress() }.also { handler.postDelayed(it, DOUBLE_MS) }
                }
                return true
            }
        })
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PAUSED, 0, 1f)
                .build()
        )
        s.isActive = true
        session = s
    }

    fun stop() {
        pendingSingle?.let { handler.removeCallbacks(it) }
        pendingSingle = null
        session?.isActive = false
        session?.release()
        session = null
    }

    private companion object {
        const val DOUBLE_MS = 450L
        val KEYS = setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }
}
