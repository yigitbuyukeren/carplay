package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.LeanEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class LeanEstimatorTest {
    // An arbitrary mount: upright "up" and a lateral direction perpendicular to it, in device axes.
    private val up = norm(floatArrayOf(0.2f, 0.9f, 0.38f))
    private val lat = norm(cross(up, floatArrayOf(0f, 0f, 1f)))
    private val fwd = cross(up, lat)

    private fun norm(v: FloatArray): FloatArray {
        val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return floatArrayOf(v[0] / n, v[1] / n, v[2] / n)
    }

    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0],
    )

    /** World-up in the device frame for a lean of [deg] (positive = left) plus [pitchDeg] of pitch. */
    private fun upAt(deg: Double, pitchDeg: Double = 0.0): FloatArray {
        val a = Math.toRadians(deg); val p = Math.toRadians(pitchDeg)
        return norm(FloatArray(3) { i ->
            (up[i] * cos(a) + lat[i] * sin(a) + fwd[i] * sin(p)).toFloat()
        })
    }

    private fun trained(): LeanEstimator {
        val e = LeanEstimator()
        e.calibrate(up[0], up[1], up[2])
        val rnd = Random(1)
        // Ride S-curves: lean follows the yaw rate, pitch wobbles independently.
        for (i in 0 until 600) {
            val phase = i / 40.0
            val lean = 30 * sin(phase)
            val yaw = (0.5 * sin(phase)).toFloat()
            val pitch = rnd.nextDouble(-3.0, 3.0)
            val u = upAt(lean, pitch)
            e.update(u[0], u[1], u[2], yaw, moving = true)
        }
        return e
    }

    @Test
    fun notReadyBeforeAnyCornering() {
        val e = LeanEstimator()
        assertFalse(e.calibrated)
        e.calibrate(up[0], up[1], up[2])
        assertTrue(e.calibrated)
        assertFalse(e.ready)
        val u = upAt(20.0)
        assertNull(e.update(u[0], u[1], u[2], 0f, moving = true))
    }

    @Test
    fun learnsTheLeanAxisAndItsSign() {
        val e = trained()
        assertTrue(e.ready)
        val left = upAt(25.0).let { e.update(it[0], it[1], it[2], 0f, moving = true) }
        val right = upAt(-35.0).let { e.update(it[0], it[1], it[2], 0f, moving = true) }
        assertNotNull(left); assertNotNull(right)
        assertEquals(25.0, left!!.toDouble(), 2.0)
        assertEquals(-35.0, right!!.toDouble(), 2.0)
    }

    @Test
    fun pitchDoesNotLookLikeLean() {
        val e = trained()
        val braking = upAt(0.0, pitchDeg = 15.0).let { e.update(it[0], it[1], it[2], 0f, moving = true) }
        assertEquals(0.0, braking!!.toDouble(), 3.0)
    }

    @Test
    fun uprightReadsZero() {
        val e = trained()
        val r = e.update(up[0], up[1], up[2], 0f, moving = true)
        assertEquals(0.0, r!!.toDouble(), 0.5)
    }

    @Test
    fun noLearningWhileStandingStill() {
        val e = LeanEstimator()
        e.calibrate(up[0], up[1], up[2])
        for (i in 0 until 600) {
            val u = upAt(30 * sin(i / 40.0))
            e.update(u[0], u[1], u[2], (0.5 * sin(i / 40.0)).toFloat(), moving = false)
        }
        assertFalse(e.ready)
    }

    @Test
    fun snapshotRestoresTheLearnedState() {
        val a = trained()
        val b = LeanEstimator()
        b.restore(a.snapshot()!!)
        assertTrue(b.ready)
        val u = upAt(18.0)
        assertEquals(
            a.update(u[0], u[1], u[2], 0f, true)!!.toDouble(),
            b.update(u[0], u[1], u[2], 0f, true)!!.toDouble(),
            0.01,
        )
    }

    @Test
    fun recalibratingForgetsTheAxis() {
        val e = trained()
        e.calibrate(up[0], up[1], up[2])
        assertFalse(e.ready)
    }
}
