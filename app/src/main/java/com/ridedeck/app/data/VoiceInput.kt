package com.ridedeck.app.data

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.ridedeck.app.util.Prefs

/**
 * One-shot Turkish speech recognition with Android's own recogniser (no key, no library). It prefers on-device
 * recognition, gives the recogniser the names it should expect (contacts, saved places, apps), uses the Bluetooth
 * helmet microphone when one is connected, ducks the music while listening and beeps at the start and the end,
 * because a rider does not look at the screen. Everything runs on the main thread, as `SpeechRecognizer` requires.
 */
class VoiceInput(private val context: Context, private val biasing: () -> List<String> = { emptyList() }) : VoiceAssistant.SpeechListener {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        .build()
    private var recognizer: SpeechRecognizer? = null
    private var helmetMic = false
    private var tones: ToneGenerator? = null

    override fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** [onResult] gets the alternatives best first (none when nothing was understood) and the recogniser's score. */
    override fun start(onPartial: (String) -> Unit, onResult: (VoiceHeard) -> Unit) = listen(quiet = false, onPartial, onResult)

    /** The follow-up listen after an answer: same recogniser, no beeps and no buzz. */
    override fun startQuiet(onPartial: (String) -> Unit, onResult: (VoiceHeard) -> Unit) = listen(quiet = true, onPartial, onResult)

    private fun listen(quiet: Boolean, onPartial: (String) -> Unit, onResult: (VoiceHeard) -> Unit) {
        cancel()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        audio.requestAudioFocus(focusRequest)
        useHelmetMic()
        if (!quiet) beep(ToneGenerator.TONE_PROP_BEEP)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val score = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
                finish(onResult, VoiceHeard(heard, score), quiet)
            }

            override fun onError(error: Int) = finish(onResult, VoiceHeard(emptyList()), quiet)
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Wind and engine noise: wait a little longer before deciding the sentence is over.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
        if (Build.VERSION.SDK_INT >= 33) {
            val names = biasing().filter { it.isNotBlank() }.distinct().take(MAX_BIASING)
            if (names.isNotEmpty()) intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(names))
        }
        r.startListening(intent)
    }

    private fun finish(onResult: (VoiceHeard) -> Unit, results: VoiceHeard, quiet: Boolean = false) {
        release()
        if (!quiet) beep(ToneGenerator.TONE_PROP_ACK)
        onResult(results)
    }

    override fun cancel() {
        recognizer?.cancel()
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
        if (helmetMic && Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice()
        helmetMic = false
        audio.abandonAudioFocusRequest(focusRequest)
    }

    /** With a Bluetooth headset connected, listen through its microphone (Android 12+; otherwise the phone's). */
    private fun useHelmetMic() {
        if (Build.VERSION.SDK_INT < 31 || !Prefs.voiceHelmetMic(context)) return
        val device = audio.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        } ?: return
        helmetMic = audio.setCommunicationDevice(device)
    }

    private fun beep(tone: Int) {
        try {
            val generator = tones ?: ToneGenerator(AudioManager.STREAM_MUSIC, BEEP_VOLUME).also { tones = it }
            generator.startTone(tone, BEEP_MS)
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: RuntimeException) {
        }
    }

    private companion object {
        const val MAX_BIASING = 100
        const val BEEP_VOLUME = 60
        const val BEEP_MS = 120
    }
}
