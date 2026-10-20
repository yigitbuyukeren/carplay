package com.ridedeck.app

import com.ridedeck.app.ui.SosHoldMath
import org.junit.Assert.assertEquals
import org.junit.Test

class SosHoldMathTest {
    @Test
    fun restingButtonIsNotScaled() {
        assertEquals(1f, SosHoldMath.scale(0f, pressed = false), 0.0001f)
        assertEquals(1f, SosHoldMath.scale(0.9f, pressed = false), 0.0001f)
    }

    @Test
    fun pressingPopsThenGrowsToTwentyPercent() {
        assertEquals(1.06f, SosHoldMath.scale(0f, pressed = true), 0.0001f)
        assertEquals(1.13f, SosHoldMath.scale(0.5f, pressed = true), 0.0001f)
        assertEquals(1.20f, SosHoldMath.scale(1f, pressed = true), 0.0001f)
        assertEquals(1.20f, SosHoldMath.scale(3f, pressed = true), 0.0001f)
    }

    @Test
    fun ticksFireAtAThirdAndTwoThirds() {
        assertEquals(0, SosHoldMath.ticks(0.2f))
        assertEquals(1, SosHoldMath.ticks(0.34f))
        assertEquals(2, SosHoldMath.ticks(0.7f))
    }

    @Test
    fun haloFollowsProgress() {
        assertEquals(0f, SosHoldMath.halo(-1f), 0.0001f)
        assertEquals(0.5f, SosHoldMath.halo(0.5f), 0.0001f)
        assertEquals(1f, SosHoldMath.halo(2f), 0.0001f)
    }
}
