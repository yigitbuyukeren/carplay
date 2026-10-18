package dev.ygtlab.carplay.data

/** Spoken engine warnings. */
enum class EngineAlert { OVERHEAT, LOW_VOLTAGE, HIGH_VOLTAGE, MALFUNCTION }

/**
 * Turns engine readings into one-off warnings with hysteresis, so a value hovering around a limit
 * is announced once instead of on every reading.
 */
class ObdAlerts {
    private val active = mutableSetOf<EngineAlert>()

    /** Returns the alerts that just started. */
    fun check(d: EngineData): List<EngineAlert> {
        val started = mutableListOf<EngineAlert>()
        fun track(alert: EngineAlert, on: Boolean, off: Boolean) {
            if (on && active.add(alert)) started += alert
            if (off) active.remove(alert)
        }
        d.coolantC?.let { track(EngineAlert.OVERHEAT, it >= OVERHEAT_C, it <= OVERHEAT_C - 5) }
        // Low voltage only counts with the engine running; at rest a healthy battery sits around 12.4-12.8 V.
        val running = (d.rpm ?: 0) > 1000
        d.voltage?.let { v ->
            track(EngineAlert.LOW_VOLTAGE, running && v < LOW_V, !running || v > LOW_V + 0.5f)
            track(EngineAlert.HIGH_VOLTAGE, v > HIGH_V, v < HIGH_V - 0.3f)
        }
        track(EngineAlert.MALFUNCTION, d.milOn, !d.milOn)
        return started
    }

    companion object {
        const val OVERHEAT_C = 115
        const val LOW_V = 12.5f
        const val HIGH_V = 15.2f
    }
}
