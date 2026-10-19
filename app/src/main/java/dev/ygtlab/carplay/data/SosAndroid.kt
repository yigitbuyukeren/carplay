package dev.ygtlab.carplay.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import kotlin.math.sqrt

/** Plain text messages through the phone's own SIM; one message per number, no repeat. */
class AndroidSmsSender(private val context: Context) : SmsSender {
    override fun send(numbers: List<String>, text: String): Result<Unit> {
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        var delivered = 0
        for (number in numbers) {
            try {
                val parts = sms.divideMessage(text)
                if (parts.size > 1) sms.sendMultipartTextMessage(number, null, parts, null, null)
                else sms.sendTextMessage(number, null, text, null, null)
                delivered++
            } catch (e: Exception) {
                Log.w("MotoDash", "SOS sms failed", e)
            }
        }
        // One contact reached is enough to call the alert sent; all failing is reported.
        return if (delivered > 0) Result.success(Unit) else Result.failure(IllegalStateException("no sms sent"))
    }
}

/** Loud beep on the alarm stream, plus a buzz. The alarm volume is raised for the countdown and put back. */
class AndroidSosAlarm(private val context: Context) : SosAlarm {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var tone: ToneGenerator? = null
    private var savedVolume: Int? = null

    override fun beep() {
        try {
            if (savedVolume == null) {
                savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            }
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)
            vibrator()?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: RuntimeException) {
            Log.w("MotoDash", "SOS alarm failed", e)
        }
    }

    override fun stop() {
        tone?.release()
        tone = null
        savedVolume?.let { audio.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
        savedVolume = null
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
}

/** Feeds [CrashDetector] from the accelerometer. Only listens while the dashboard is on screen. */
class CrashSensor(
    context: Context,
    private val speedKmh: () -> Int,
    config: () -> ProfileConfig,
    private val onCrash: () -> Unit,
) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = CrashDetector({ config().crashImpactG }, { config().crashMovingKmh })
    private var listening = false

    val supported: Boolean get() = accelerometer != null

    fun start() {
        if (listening || accelerometer == null) return
        sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        listening = true
    }

    fun stop() {
        if (!listening) return
        sensors.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val g = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]) /
            SensorManager.GRAVITY_EARTH
        if (detector.onSample(event.timestamp / 1_000_000L, g, speedKmh())) onCrash()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
