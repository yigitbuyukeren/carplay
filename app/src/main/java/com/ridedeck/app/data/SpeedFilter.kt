package com.ridedeck.app.data

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Display-only speed estimate: an alpha-beta filter over the 1 Hz GPS speed (state: speed and acceleration) that is
 * extrapolated between fixes, so the number leads the raw fix a little instead of lagging behind it. Crash detection,
 * trip recording and the limit warning keep using the raw speed; this is only for what the rider reads.
 */
class SpeedFilter {
    private var v = 0.0
    private var a = 0.0
    private var lastFixMs = 0L
    private var started = false
    private var standstill = true
    private var slowFixes = 0
    private var outliers = 0

    /** A fix: [tMs] on any steady clock (elapsed realtime), [speedMps] the measured speed, [accuracyMps] its 1-sigma error if the chip reports it. */
    fun onFix(tMs: Long, speedMps: Float, accuracyMps: Float? = null) {
        val z = max(0.0, speedMps.toDouble())
        val gap = (tMs - lastFixMs) / 1000.0
        if (!started || gap > GAP_RESET_S || gap <= 0.0) {
            // First fix, a long hole or a clock hiccup: start over from the measurement.
            v = z; a = 0.0; started = true; outliers = 0
            lastFixMs = tMs
            updateStandstill(z)
            return
        }
        val dt = min(gap, 2.0)
        val pred = v + a * dt
        val residual = z - pred
        val impliedAccel = abs(z - v) / dt
        val sigma = (accuracyMps ?: DEFAULT_SIGMA).toDouble().coerceAtLeast(MIN_SIGMA)
        // A jump no vehicle can make, from a fix that admits it is poor, is held back; a second one in a row is believed.
        if (impliedAccel > MAX_ACCEL * 1.6 && sigma > 1.5 && outliers < 1) {
            outliers++
            lastFixMs = tMs
            updateStandstill(v)
            return
        }
        outliers = 0
        val alpha = if (abs(residual) > JUMP_MPS) 1.0 else (0.85 - 0.2 * max(sigma - 0.5, 0.0)).coerceIn(0.3, 0.85)
        val beta = alpha * alpha / (2 - alpha)
        v = max(0.0, pred + alpha * residual)
        a = (a + beta * residual / dt).coerceIn(-MAX_ACCEL, MAX_ACCEL)
        if (alpha >= 1.0) a = ((z - (pred - a * dt)) / dt).coerceIn(-MAX_ACCEL, MAX_ACCEL)
        lastFixMs = tMs
        updateStandstill(z)
    }

    /** Hysteresis for the stop: two slow fixes in a row to be standing, a clearly higher speed to be moving again (no 0/1/0 at a red light). */
    private fun updateStandstill(rawMps: Double) {
        if (standstill) {
            if (rawMps > LEAVE_STANDSTILL_MPS) standstill = false.also { slowFixes = 0 }
        } else {
            if (rawMps < ENTER_STANDSTILL_MPS) {
                if (++slowFixes >= 2) { standstill = true; v = 0.0; a = 0.0 }
            } else slowFixes = 0
        }
    }

    /** The estimated speed in m/s at [nowMs], extrapolated from the last fix by at most 1.2 s. */
    fun speedMps(nowMs: Long): Double {
        if (!started || standstill) return 0.0
        val dt = ((nowMs - lastFixMs) / 1000.0).coerceIn(0.0, MAX_EXTRAPOLATE_S)
        return max(0.0, v + a * dt)
    }

    fun speedKmh(nowMs: Long): Double = speedMps(nowMs) * 3.6

    fun reset() {
        v = 0.0; a = 0.0; started = false; standstill = true; slowFixes = 0; outliers = 0; lastFixMs = 0L
    }

    companion object {
        const val MAX_ACCEL = 6.0
        const val MAX_EXTRAPOLATE_S = 1.2
        private const val GAP_RESET_S = 3.0
        private const val DEFAULT_SIGMA = 0.5f
        private const val MIN_SIGMA = 0.1
        /** About 8 km/h: a measurement this far from the prediction is believed outright, so a hard brake is not smoothed away. */
        private const val JUMP_MPS = 2.2
        private const val ENTER_STANDSTILL_MPS = 0.55
        private const val LEAVE_STANDSTILL_MPS = 1.0
    }
}

/**
 * Follows the filter's estimate once per frame with a short critically damped lag (about 80 ms), and turns it into a
 * whole number that only changes when the value is clearly past the half, so it does not flicker between two digits.
 */
class SpeedSmoother {
    var value = 0.0
        private set
    private var shown = 0

    fun step(targetKmh: Double, dtS: Double): Double {
        value = if (dtS <= 0.0 || abs(targetKmh - value) > SNAP_KMH) targetKmh
        else value + (targetKmh - value) * (1 - exp(-dtS / TIME_CONSTANT_S))
        return value
    }

    /** The digit to show for the current [value]: moves only once the value is [HYSTERESIS] past the shown number's half. */
    fun digit(): Int {
        if (abs(value - shown) >= 0.5 + HYSTERESIS) shown = value.roundToInt()
        if (value < 0.3) shown = 0
        return shown
    }

    companion object {
        const val TIME_CONSTANT_S = 0.08
        const val HYSTERESIS = 0.1
        private const val SNAP_KMH = 60.0
    }
}
