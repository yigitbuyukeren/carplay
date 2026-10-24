package com.ridedeck.app.data

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * GPS gives one fix a second; drawn as is, the marker and camera hop once a second. This turns the fixes into a
 * position for every frame: it carries the last fix forward along its heading at its speed (dead reckoning) and
 * eases the shown position toward that estimate, so a new fix bends the path instead of snapping it.
 * Pure maths; the caller supplies a monotonic clock in nanoseconds.
 */
class PositionSmoother {
    data class Frame(val lat: Double, val lon: Double, val bearing: Double)

    private var fixLat = 0.0
    private var fixLon = 0.0
    private var fixSpeedMps = 0.0
    private var fixBearing: Double? = null
    private var fixAtNanos = 0L
    private var hasFix = false

    private var shownLat = 0.0
    private var shownLon = 0.0
    private var shownBearing = 0.0
    private var shownAtNanos = 0L
    private var started = false

    fun onFix(lat: Double, lon: Double, speedMps: Double, bearing: Float?, nowNanos: Long) {
        fixLat = lat
        fixLon = lon
        fixSpeedMps = speedMps
        fixBearing = bearing?.toDouble()
        fixAtNanos = nowNanos
        hasFix = true
    }

    /** The position and heading to draw at [nowNanos], or null before the first fix. */
    fun step(nowNanos: Long, fallbackBearing: Double): Frame? {
        if (!hasFix) return null
        val heading = fixBearing
        val ahead = if (heading != null && fixSpeedMps > MIN_MOVING_MPS) {
            fixSpeedMps * min((nowNanos - fixAtNanos) / 1e9, MAX_AHEAD_S)
        } else 0.0
        val cosLat = cos(Math.toRadians(fixLat))
        val rad = Math.toRadians(heading ?: 0.0)
        val targetLat = fixLat + Math.toDegrees(ahead * cos(rad) / EARTH_RADIUS_M)
        val targetLon = fixLon + Math.toDegrees(ahead * sin(rad) / (EARTH_RADIUS_M * cosLat))
        val targetBearing = heading ?: if (started) shownBearing else fallbackBearing

        if (!started) {
            shownLat = targetLat; shownLon = targetLon; shownBearing = targetBearing
            shownAtNanos = nowNanos
            started = true
            return Frame(shownLat, shownLon, shownBearing)
        }
        val dt = ((nowNanos - shownAtNanos) / 1e9).coerceIn(0.0, 0.25)
        shownAtNanos = nowNanos

        val jumpM = hypot(
            Math.toRadians(targetLat - shownLat) * EARTH_RADIUS_M,
            Math.toRadians(targetLon - shownLon) * EARTH_RADIUS_M * cosLat,
        )
        if (jumpM > SNAP_M) {
            shownLat = targetLat; shownLon = targetLon
        } else {
            val k = 1 - exp(-dt / POSITION_TAU_S)
            shownLat += (targetLat - shownLat) * k
            shownLon += (targetLon - shownLon) * k
        }
        val turn = ((targetBearing - shownBearing + 540) % 360) - 180
        shownBearing = (shownBearing + turn * (1 - exp(-dt / BEARING_TAU_S)) + 360) % 360
        return Frame(shownLat, shownLon, shownBearing)
    }

    fun reset() {
        hasFix = false
        started = false
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
        const val MIN_MOVING_MPS = 0.8
        const val MAX_AHEAD_S = 2.0
        const val SNAP_M = 120.0
        const val POSITION_TAU_S = 0.3
        const val BEARING_TAU_S = 0.45
    }
}
