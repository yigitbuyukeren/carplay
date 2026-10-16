package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.TripMath
import dev.ygtlab.carplay.data.TripPoint
import dev.ygtlab.carplay.util.Gpx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTest {
    // One degree of longitude at latitude 0 is about 111.19 km; 0.0009 deg is about 100 m.
    private fun p(sec: Int, lonDeg: Double, kmh: Int) = TripPoint(sec * 1000L, 0.0, lonDeg, kmh)

    @Test
    fun distanceSpeedAndMovingTime() {
        val stats = TripMath.compute(listOf(p(0, 0.0, 0), p(10, 0.0009, 36), p(20, 0.0018, 40)))
        assertEquals(200.0, stats.distanceM, 3.0)
        assertEquals(20_000L, stats.movingMs)
        assertEquals(40, stats.maxKmh)
        assertEquals(36, stats.avgKmh)
    }

    @Test
    fun standingStillDoesNotCountAsMoving() {
        val stats = TripMath.compute(listOf(p(0, 0.0, 0), p(60, 0.0, 0), p(70, 0.0009, 36)))
        assertEquals(10_000L, stats.movingMs)
    }

    @Test
    fun gapsAndGpsJumpsAreIgnored() {
        val gap = TripMath.compute(listOf(p(0, 0.0, 50), p(120, 0.05, 50)))
        assertEquals(0.0, gap.distanceM, 0.001)
        val jump = TripMath.compute(listOf(p(0, 0.0, 50), p(1, 0.05, 50)))
        assertEquals(0.0, jump.distanceM, 0.001)
    }

    @Test
    fun emptyTripHasZeroStats() {
        val stats = TripMath.compute(emptyList())
        assertEquals(0, stats.avgKmh)
        assertEquals(0, stats.maxKmh)
    }

    @Test
    fun gpxContainsPointsInUtc() {
        val gpx = Gpx.write("A & B", listOf(TripPoint(0L, 41.0, 29.0, 36)))
        assertTrue(gpx.contains("<name>A &amp; B</name>"))
        assertTrue(gpx.contains("lat=\"41.000000\" lon=\"29.000000\""))
        assertTrue(gpx.contains("<time>1970-01-01T00:00:00Z</time>"))
        assertTrue(gpx.contains("<speed>10.00</speed>"))
    }
}
