package com.ridedeck.app.data

/** What the assistant may bring up by itself. */
enum class ProactiveKind { RAIN, BATTERY, ARRIVAL, BREAK }

data class ProactiveInput(
    val nowMs: Long,
    val speedKmh: Int,
    /** Minutes until rain (0 = already raining, null = dry); see [WeatherSnapshot.rainInMin]. */
    val rainInMin: Int?,
    val batteryPercent: Int,
    val charging: Boolean,
    val navigating: Boolean,
    val remainingM: Int,
    /** A call, the assistant itself, or the navigation voice is busy: stay quiet. */
    val busy: Boolean,
)

/**
 * Decides when a short, optional question is worth asking: rain within the hour, a low phone battery, two kilometres to
 * go, a long ride without a break. Each kind at most once in [COOLDOWN_MS], any prompt at least [GAP_MS] after the
 * last, never while something else is talking. The on/off switch (default off) lives in the settings, outside this class.
 */
class ProactivePrompts {
    private val lastAskedAt = mutableMapOf<ProactiveKind, Long>()
    private var lastAnyAt = NEVER
    private var ridingSince = 0L
    private var lastMovingAt = 0L

    /** Feed every speed sample; a ride is a stretch of moving with no stop longer than [STOP_RESETS_MS]. */
    fun onSpeed(nowMs: Long, speedKmh: Int) {
        if (speedKmh >= RIDING_KMH) {
            if (ridingSince == 0L || nowMs - lastMovingAt > STOP_RESETS_MS) ridingSince = nowMs
            lastMovingAt = nowMs
        }
    }

    fun ridingMinutes(nowMs: Long): Int = if (ridingSince == 0L || nowMs - lastMovingAt > STOP_RESETS_MS) 0 else ((nowMs - ridingSince) / 60_000L).toInt()

    /** The prompt to ask now, or null. Records that it was asked. */
    fun next(input: ProactiveInput): ProactiveKind? {
        onSpeed(input.nowMs, input.speedKmh)
        if (input.busy || lastAnyAt != NEVER && input.nowMs - lastAnyAt < GAP_MS) return null
        val due = buildList {
            if (input.rainInMin != null && input.rainInMin in 1..60) add(ProactiveKind.RAIN)
            if (input.navigating && input.remainingM in 1..ARRIVAL_M) add(ProactiveKind.ARRIVAL)
            if (input.batteryPercent in 1 until LOW_BATTERY && !input.charging) add(ProactiveKind.BATTERY)
            if (ridingMinutes(input.nowMs) >= BREAK_AFTER_MIN) add(ProactiveKind.BREAK)
        }
        val kind = due.firstOrNull { k -> lastAskedAt[k]?.let { input.nowMs - it >= COOLDOWN_MS } ?: true } ?: return null
        lastAskedAt[kind] = input.nowMs
        lastAnyAt = input.nowMs
        return kind
    }

    companion object {
        const val COOLDOWN_MS = 30 * 60_000L
        const val GAP_MS = 5 * 60_000L
        const val ARRIVAL_M = 2000
        const val LOW_BATTERY = 20
        const val BREAK_AFTER_MIN = 120
        private const val NEVER = Long.MIN_VALUE
        private const val RIDING_KMH = 10
        private const val STOP_RESETS_MS = 10 * 60_000L
    }
}
