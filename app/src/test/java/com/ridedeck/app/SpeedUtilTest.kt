package com.ridedeck.app

import com.ridedeck.app.util.SpeedUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedUtilTest {
    @Test fun convertsMetersPerSecondToKmh() = assertEquals(72, SpeedUtil.msToKmh(20f))
    @Test fun roundsToNearest() = assertEquals(50, SpeedUtil.msToKmh(13.9f))
    @Test fun standstillSnapsToZero() = assertEquals(0, SpeedUtil.msToKmh(0.4f))
    @Test fun zeroIsZero() = assertEquals(0, SpeedUtil.msToKmh(0f))
}
