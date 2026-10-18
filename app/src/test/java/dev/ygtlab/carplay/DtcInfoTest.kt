package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.DtcArea
import dev.ygtlab.carplay.data.DtcInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DtcInfoTest {
    @Test
    fun specificBeforeGeneric() {
        assertEquals(DtcArea.CRANK_CAM_SENSOR, DtcInfo.area("P0335"))
        assertEquals(DtcArea.IGNITION, DtcInfo.area("P0350"))
        assertEquals(DtcArea.MANUFACTURER, DtcInfo.area("P1A00"))
        assertEquals(DtcArea.NETWORK, DtcInfo.area("U0100"))
    }
}
