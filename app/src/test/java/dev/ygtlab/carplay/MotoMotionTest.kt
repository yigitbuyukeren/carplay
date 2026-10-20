package dev.ygtlab.carplay

import dev.ygtlab.carplay.ui.MotoMotion
import dev.ygtlab.carplay.ui.MotoScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class MotoMotionTest {
    @Test
    fun directionFollowsTheDockOrder() {
        assertEquals(1, MotoMotion.direction(MotoScreen.HOME, MotoScreen.MUSIC))
        assertEquals(1, MotoMotion.direction(MotoScreen.MAP, MotoScreen.PHONE))
        assertEquals(-1, MotoMotion.direction(MotoScreen.PHONE, MotoScreen.HOME))
        assertEquals(0, MotoMotion.direction(MotoScreen.MAP, MotoScreen.MAP))
    }
}
