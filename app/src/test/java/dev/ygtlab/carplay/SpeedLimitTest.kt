package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.SpeedLimitMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLimitTest {
    @Test
    fun parsesTheLastEdgesLimit() {
        val json = """{"edges":[{"speed_limit":50},{"speed":13},{"speed_limit":90,"speed":84}]}"""
        assertEquals(90, SpeedLimitMath.parseLimit(json))
    }

    @Test
    fun missingOrOddLimitsAreNull() {
        assertNull(SpeedLimitMath.parseLimit("""{"edges":[{"speed":13}]}"""))
        assertNull(SpeedLimitMath.parseLimit("""{"edges":[{"speed_limit":"unlimited"}]}"""))
        assertNull(SpeedLimitMath.parseLimit("""{"edges":[{"speed_limit":0}]}"""))
        assertNull(SpeedLimitMath.parseLimit("""{"edges":[]}"""))
        assertNull(SpeedLimitMath.parseLimit("{}"))
    }

    @Test
    fun overAndWarnThresholds() {
        assertFalse(SpeedLimitMath.isOver(50, 50))
        assertTrue(SpeedLimitMath.isOver(51, 50))
        assertFalse(SpeedLimitMath.isOver(80, null))
        assertFalse(SpeedLimitMath.shouldWarn(55, 50))
        assertTrue(SpeedLimitMath.shouldWarn(56, 50))
        assertFalse(SpeedLimitMath.shouldWarn(200, null))
    }
}
