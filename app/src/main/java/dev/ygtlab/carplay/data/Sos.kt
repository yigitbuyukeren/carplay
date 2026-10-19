package dev.ygtlab.carplay.data

import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class SosContact(val name: String, val number: String)

enum class SosReason { AUTO, MANUAL, TEST }

enum class SosPhase { IDLE, COUNTDOWN, SENT, FAILED }

enum class SosError { NO_CONTACTS, NO_PERMISSION, SEND_FAILED }

data class SosUi(
    val phase: SosPhase = SosPhase.IDLE,
    val reason: SosReason = SosReason.AUTO,
    val secondsLeft: Int = 0,
    /** Names the message went to; empty for a test. */
    val sentTo: List<String> = emptyList(),
    val error: SosError? = null,
)

object SosMath {
    const val MAX_CONTACTS = 3
    const val AUTO_SECONDS = 30
    const val MANUAL_SECONDS = 5
    const val TEST_SECONDS = 8

    /** A location older than this is sent as "last known" rather than "now". */
    const val FRESH_FIX_MS = 2 * 60_000L

    fun seconds(reason: SosReason): Int = when (reason) {
        SosReason.AUTO -> AUTO_SECONDS
        SosReason.MANUAL -> MANUAL_SECONDS
        SosReason.TEST -> TEST_SECONDS
    }

    /** Keeps a leading + and digits only. Null when it cannot be a phone number. */
    fun normalizeNumber(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length !in 7..15) return null
        return (if (trimmed.startsWith("+")) "+" else "") + digits
    }

    /** Adds [new] unless it is already there (same digits) or the list is full. */
    fun addContact(list: List<SosContact>, new: SosContact): List<SosContact> {
        val number = normalizeNumber(new.number) ?: return list
        if (list.size >= MAX_CONTACTS || list.any { it.number.filter(Char::isDigit).takeLast(10) == number.filter(Char::isDigit).takeLast(10) }) return list
        return list + SosContact(new.name.trim().ifBlank { number }, number)
    }

    /** OpenStreetMap link: opens in any browser, no app or account needed on the receiving phone. */
    fun locationLink(lat: Double, lon: Double): String {
        val la = "%.5f".format(Locale.US, lat)
        val lo = "%.5f".format(Locale.US, lon)
        return "https://www.openstreetmap.org/?mlat=$la&mlon=$lo#map=17/$la/$lo"
    }

    fun toJson(list: List<SosContact>): String = JSONArray().also { arr ->
        list.forEach { arr.put(JSONObject().put("name", it.name).put("number", it.number)) }
    }.toString()

    fun fromJson(json: String?): List<SosContact> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val number = normalizeNumber(o.optString("number")) ?: return@mapNotNull null
                SosContact(o.optString("name").ifBlank { number }, number)
            }.take(MAX_CONTACTS)
        } catch (e: JSONException) {
            emptyList()
        }
    }
}

/**
 * Decides from accelerometer samples and GPS speed whether a rider has crashed (thresholds per vehicle): a hard impact while
 * riding, followed by the phone staying still and the bike not moving. A pothole (impact but the bike
 * rolls on), a hard stop (no impact) and a bike knocked over in a car park (never riding) all stay quiet.
 */
class CrashDetector(
    private val impactG: () -> Float = { IMPACT_G },
    private val ridingKmh: () -> Int = { RIDING_KMH },
) {
    private var lastRidingAt = Long.MIN_VALUE
    private var impactAt = 0L
    private var quietSince = 0L
    private var armed = false
    private var cooldownUntil = 0L

    /** @param g acceleration magnitude in g, gravity included (about 1 at rest). */
    fun onSample(tMs: Long, g: Float, speedKmh: Int): Boolean {
        if (speedKmh >= ridingKmh()) lastRidingAt = tMs
        if (tMs < cooldownUntil) return false

        if (!armed) {
            if (g >= impactG() && lastRidingAt != Long.MIN_VALUE && tMs - lastRidingAt <= RIDING_WINDOW_MS) {
                armed = true
                impactAt = tMs
                quietSince = 0L
            }
            return false
        }

        val sinceImpact = tMs - impactAt
        if (sinceImpact > GIVE_UP_MS || (sinceImpact >= RESUME_AFTER_MS && speedKmh >= minOf(RESUME_KMH, ridingKmh() * 3 / 5))) {
            armed = false
            return false
        }
        val quiet = speedKmh < STILL_KMH && abs(g - 1f) <= QUIET_BAND_G
        if (!quiet) {
            quietSince = 0L
            return false
        }
        if (quietSince == 0L) quietSince = tMs
        if (tMs - quietSince >= QUIET_MS) {
            armed = false
            cooldownUntil = tMs + COOLDOWN_MS
            return true
        }
        return false
    }

    companion object {
        const val IMPACT_G = 4.0f
        const val RIDING_KMH = 25
        const val RIDING_WINDOW_MS = 5_000L
        const val STILL_KMH = 5
        const val RESUME_KMH = 15
        const val RESUME_AFTER_MS = 4_000L
        const val QUIET_BAND_G = 0.35f
        const val QUIET_MS = 8_000L
        const val GIVE_UP_MS = 60_000L
        const val COOLDOWN_MS = 5 * 60_000L
    }
}

fun interface SmsSender {
    /** Sends [text] to every number once. Fails if the message could not be handed to the radio. */
    fun send(numbers: List<String>, text: String): Result<Unit>
}

interface SosAlarm {
    /** Called once at the start and then every second of the countdown. */
    fun beep()
    fun stop()
}

/**
 * Countdown, then one SMS per contact with the location link; never a call and never a repeat.
 * Text and voice come in through [message] and [speak] so this stays plain Kotlin.
 */
class SosController(
    private val scope: CoroutineScope,
    private val location: StateFlow<LocationState>,
    private val contacts: StateFlow<List<SosContact>>,
    private val sms: SmsSender,
    private val alarm: SosAlarm,
    private val smsAllowed: () -> Boolean,
    private val speak: (SosReason, Int) -> Unit,
    private val message: (SosReason, String?, Boolean) -> String,
    private val tick: suspend () -> Unit = { delay(1000) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _ui = MutableStateFlow(SosUi())
    val ui: StateFlow<SosUi> = _ui
    private var job: Job? = null

    fun trigger(reason: SosReason) {
        if (_ui.value.phase != SosPhase.IDLE) return
        if (reason != SosReason.TEST) {
            val problem = when {
                contacts.value.isEmpty() -> SosError.NO_CONTACTS
                !smsAllowed() -> SosError.NO_PERMISSION
                else -> null
            }
            if (problem != null) {
                _ui.value = SosUi(SosPhase.FAILED, reason, error = problem)
                return
            }
        }
        val seconds = SosMath.seconds(reason)
        _ui.value = SosUi(SosPhase.COUNTDOWN, reason, seconds)
        speak(reason, seconds)
        job = scope.launch {
            alarm.beep()
            for (left in seconds - 1 downTo 0) {
                tick()
                _ui.update { it.copy(secondsLeft = left) }
                if (left > 0) alarm.beep()
            }
            alarm.stop()
            finish(reason)
        }
    }

    /** "I am fine" / cancel. */
    fun cancel() {
        if (_ui.value.phase != SosPhase.COUNTDOWN) return
        job?.cancel()
        alarm.stop()
        _ui.value = SosUi()
    }

    /** Closes the "sent" / "failed" screen. */
    fun dismiss() {
        if (_ui.value.phase == SosPhase.SENT || _ui.value.phase == SosPhase.FAILED) _ui.value = SosUi()
    }

    private fun finish(reason: SosReason) {
        if (reason == SosReason.TEST) {
            _ui.value = SosUi(SosPhase.SENT, reason)
            return
        }
        val list = contacts.value
        val fix = location.value
        val haveFix = fix.fixAtMs > 0
        val link = if (haveFix) SosMath.locationLink(fix.lat, fix.lon) else null
        val stale = haveFix && now() - fix.fixAtMs > SosMath.FRESH_FIX_MS
        sms.send(list.map { it.number }, message(reason, link, stale))
            .onSuccess { _ui.value = SosUi(SosPhase.SENT, reason, sentTo = list.map { it.name }) }
            .onFailure { _ui.value = SosUi(SosPhase.FAILED, reason, error = SosError.SEND_FAILED) }
    }
}
