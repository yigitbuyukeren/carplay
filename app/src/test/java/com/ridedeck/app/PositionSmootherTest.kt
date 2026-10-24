package com.ridedeck.app

import com.ridedeck.app.data.Geo
import com.ridedeck.app.data.GeoPoint
import com.ridedeck.app.data.PositionSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSmootherTest {
    private val frameNs = 16_666_667L

    @Test
    fun nothingBeforeTheFirstFix() {
        assertNull(PositionSmoother().step(0L, 0.0))
    }

    @Test
    fun movesEveryFrameBetweenFixes() {
        val s = PositionSmoother()
        // 20 m/s due north, one fix at t=0.
        s.onFix(41.0, 29.0, 20.0, 0f, 0L)
        var last = s.step(0L, 0.0)!!
        var moved = 0
        for (i in 1..60) {
            val f = s.step(i * frameNs, 0.0)!!
            if (f.lat > last.lat) moved++
            last = f
        }
        assertEquals(60, moved)
    }

    @Test
    fun stepsStaySmallComparedWithAFixHop() {
        val s = PositionSmoother()
        s.onFix(41.0, 29.0, 20.0, 0f, 0L)
        var last = s.step(0L, 0.0)!!
        var biggest = 0.0
        for (i in 1..59) last = s.step(i * frameNs, 0.0)!!
        for (sec in 1..5) {
            val fixNs = sec * 1_000_000_000L
            s.onFix(41.0 + 20.0 * sec / 111_195.0, 29.0, 20.0, 0f, fixNs)
            for (i in 1..60) {
                val f = s.step(fixNs + i * frameNs, 0.0)!!
                biggest = maxOf(biggest, Geo.distance(GeoPoint(last.lat, last.lon), GeoPoint(f.lat, f.lon)))
                last = f
            }
        }
        // A raw fix hop is 20 m; per-frame moves must be near 20/60 m.
        assertTrue("biggest step $biggest", biggest < 1.0)
    }

    @Test
    fun standingStillDoesNotDrift() {
        val s = PositionSmoother()
        s.onFix(41.0, 29.0, 0.0, 90f, 0L)
        val a = s.step(0L, 0.0)!!
        val b = s.step(2_000_000_000L, 0.0)!!
        assertEquals(a.lat, b.lat, 1e-12)
        assertEquals(a.lon, b.lon, 1e-12)
    }

    @Test
    fun bearingTurnsTheShortWay() {
        val s = PositionSmoother()
        s.onFix(41.0, 29.0, 10.0, 350f, 0L)
        s.step(0L, 0.0)
        s.onFix(41.0, 29.0, 10.0, 10f, frameNs)
        val f = s.step(2 * frameNs, 0.0)!!
        assertTrue("bearing ${f.bearing}", f.bearing > 350.0 || f.bearing < 10.0)
    }
}
