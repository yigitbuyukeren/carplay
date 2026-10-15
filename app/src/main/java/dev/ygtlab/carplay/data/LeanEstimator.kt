package dev.ygtlab.carplay.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Lean angle from the direction of gravity in the phone's frame, without assuming how the phone
 * is mounted.
 *
 * 1. Calibration stores the "up" vector while the bike stands upright.
 * 2. Tilt away from that vector is the lean plus some pitch. The lean axis is learned while
 *    riding: in a corner the tilt grows and shrinks together with the yaw rate (gyroscope), while
 *    pitch from braking does not follow it, so the covariance of tilt and yaw rate points along the
 *    lean axis. Its sign also fixes left/right: positive is a lean to the left, because a left turn
 *    has a positive yaw rate.
 *
 * Inputs are unit "up" vectors in the device frame (world up as the phone sees it) and the yaw rate
 * in rad/s about that up axis, positive counter-clockwise seen from above.
 */
class LeanEstimator {
    private var ux = 0f; private var uy = 0f; private var uz = 0f
    private var hasReference = false

    private var covX = 0f; private var covY = 0f; private var covZ = 0f
    private var samples = 0
    private var weight = 0f

    val calibrated: Boolean get() = hasReference

    /** True once enough corners have been ridden to know the lean axis. */
    val ready: Boolean
        get() = hasReference && samples >= MIN_SAMPLES && weight > 0f && covNorm() / weight >= MIN_COHERENCE

    fun calibrate(x: Float, y: Float, z: Float) {
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1e-3f) return
        ux = x / n; uy = y / n; uz = z / n
        hasReference = true
        covX = 0f; covY = 0f; covZ = 0f
        samples = 0
        weight = 0f
    }

    /** Feeds one sample; returns the signed lean in degrees (positive = left) once [ready], else null. */
    fun update(x: Float, y: Float, z: Float, yawRate: Float, moving: Boolean): Float? {
        if (!hasReference) return null
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1e-3f) return null
        val px = x / n; val py = y / n; val pz = z / n
        val c = px * ux + py * uy + pz * uz
        val dx = px - c * ux; val dy = py - c * uy; val dz = pz - c * uz
        val dNorm = sqrt(dx * dx + dy * dy + dz * dz)

        if (moving && abs(yawRate) >= MIN_YAW_RATE && dNorm >= MIN_TILT) {
            covX += dx * yawRate; covY += dy * yawRate; covZ += dz * yawRate
            weight += dNorm * abs(yawRate)
            samples++
        }
        if (!ready) return null
        val norm = covNorm()
        val lat = (dx * covX + dy * covY + dz * covZ) / norm
        return Math.toDegrees(atan2(lat, c).toDouble()).toFloat()
    }

    private fun covNorm() = sqrt(covX * covX + covY * covY + covZ * covZ)

    /** Everything needed to resume after a restart. */
    fun snapshot(): FloatArray? =
        if (!hasReference) null else floatArrayOf(ux, uy, uz, covX, covY, covZ, samples.toFloat(), weight)

    fun restore(s: FloatArray) {
        if (s.size != 8) return
        ux = s[0]; uy = s[1]; uz = s[2]
        covX = s[3]; covY = s[4]; covZ = s[5]
        samples = s[6].toInt()
        weight = s[7]
        hasReference = true
    }

    private companion object {
        const val MIN_SAMPLES = 100
        const val MIN_COHERENCE = 0.6f
        const val MIN_YAW_RATE = 0.12f // about 7 degrees per second
        const val MIN_TILT = 0.05f // about 3 degrees
    }
}
