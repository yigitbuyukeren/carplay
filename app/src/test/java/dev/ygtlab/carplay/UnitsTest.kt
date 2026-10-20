package dev.ygtlab.carplay

import dev.ygtlab.carplay.util.UnitMath
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun kilometresPassThrough() {
        assertEquals(90, UnitMath.speed(90, miles = false))
        assertEquals("450 m", UnitMath.distance(453, miles = false))
        assertEquals("1.5 km", UnitMath.distance(1500, miles = false))
    }

    @Test
    fun speedConvertsToMph() {
        assertEquals(56, UnitMath.speed(90, miles = true))
        assertEquals(0, UnitMath.speed(0, miles = true))
        assertEquals(62, UnitMath.speed(100, miles = true))
    }

    @Test
    fun shortDistancesUseFeetAndLongOnesMiles() {
        assertEquals("150 ft", UnitMath.distance(46, miles = true))
        assertEquals("10 ft", UnitMath.distance(1, miles = true))
        assertEquals("0.6 mi", UnitMath.distance(1000, miles = true))
        assertEquals("3.1 mi", UnitMath.distance(5000, miles = true))
    }
}
