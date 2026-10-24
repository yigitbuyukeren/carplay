package com.ridedeck.app

import com.ridedeck.app.data.SpeedFilter
import com.ridedeck.app.data.SpeedSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeedFilterTest {
    private fun feed(f: SpeedFilter, speeds: List<Float>, acc: Float? = 0.4f, startMs: Long = 1000L) {
        speeds.forEachIndexed { i, v -> f.onFix(startMs + i * 1000L, v, acc) }
    }

    @Test
    fun steadySpeedStaysSteady() {
        val f = SpeedFilter()
        feed(f, List(10) { 20f })
        assertEquals(72.0, f.speedKmh(1000L + 9 * 1000L), 1.0)
    }

    @Test
    fun noiseIsDampedBelowTheRawSpread() {
        val f = SpeedFilter()
        val noise = listOf(20.0f, 20.9f, 19.2f, 20.7f, 19.4f, 20.8f, 19.3f, 20.6f, 19.5f, 20.0f)
        feed(f, noise, acc = 1.0f)
        assertTrue(abs(f.speedMps(1000L + 9000L) - 20.0) < 0.9)
    }

    @Test
    fun rampIsLeadByTheExtrapolation() {
        val f = SpeedFilter()
        // 2 m/s per second from 10 m/s: the estimate half a second after a fix is ahead of that fix's raw speed.
        val ramp = List(8) { 10f + 2f * it }
        feed(f, ramp)
        val raw = ramp.last().toDouble()
        assertTrue(f.speedMps(1000L + 7000L + 500L) > raw + 0.5)
    }

    @Test
    fun hardBrakeIsBelievedAtOnce() {
        val f = SpeedFilter()
        feed(f, List(6) { 25f })
        f.onFix(1000L + 6000L, 12f, 0.4f)
        assertEquals(12.0, f.speedMps(1000L + 6000L), 2.5)
    }

    @Test
    fun aPoorOutlierIsHeldBackButARepeatIsAccepted() {
        val f = SpeedFilter()
        feed(f, List(6) { 15f })
        f.onFix(7000L, 40f, 5f)
        assertTrue(f.speedMps(7000L) < 20.0)
        f.onFix(8000L, 40f, 5f)
        assertTrue(f.speedMps(8000L) > 25.0)
    }

    @Test
    fun standstillJitterAtALightDoesNotFlicker() {
        val f = SpeedFilter()
        feed(f, List(5) { 10f })
        feed(f, listOf(3f, 0.2f, 0.1f), startMs = 1000L + 5000L)
        feed(f, listOf(0.6f, 0.2f, 0.7f, 0.1f), startMs = 1000L + 8000L)
        assertEquals(0.0, f.speedMps(1000L + 11000L), 0.0)
    }

    @Test
    fun smootherReachesTheTargetAndHoldsTheDigit() {
        val s = SpeedSmoother()
        repeat(60) { s.step(50.0, 1 / 60.0) }
        assertEquals(50.0, s.value, 1.0)
        assertEquals(50, s.digit())
        s.step(50.3, 1 / 60.0)
        assertEquals(50, s.digit())
    }
}
