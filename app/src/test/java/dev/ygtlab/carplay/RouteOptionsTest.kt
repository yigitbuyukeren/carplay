package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.RouteOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RouteOptionsTest {
    @Test
    fun noPreferenceMeansNoCostingOptionsAtAll() {
        assertNull(RouteOptions().costingOptions())
    }

    @Test
    fun avoidingHighwaysOnlySetsUseHighwaysToZero() {
        val m = RouteOptions(avoidHighways = true).costingOptions()!!.getJSONObject("motorcycle")
        assertEquals(0, m.getInt("use_highways"))
        assertFalse(m.has("use_tolls"))
    }

    @Test
    fun avoidingBothSetsBoth() {
        val m = RouteOptions(avoidHighways = true, avoidTolls = true).costingOptions()!!.getJSONObject("motorcycle")
        assertEquals(0, m.getInt("use_highways"))
        assertEquals(0, m.getInt("use_tolls"))
    }
}
