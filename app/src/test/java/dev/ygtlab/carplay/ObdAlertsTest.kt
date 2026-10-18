package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.EngineAlert
import dev.ygtlab.carplay.data.EngineData
import dev.ygtlab.carplay.data.ObdAlerts
import org.junit.Assert.assertEquals
import org.junit.Test

class ObdAlertsTest {
    @Test
    fun overheatAnnouncedOnceWithHysteresis() {
        val a = ObdAlerts()
        assertEquals(listOf(EngineAlert.OVERHEAT), a.check(EngineData(coolantC = 116)))
        assertEquals(emptyList<EngineAlert>(), a.check(EngineData(coolantC = 114)))
        assertEquals(emptyList<EngineAlert>(), a.check(EngineData(coolantC = 116)))
        a.check(EngineData(coolantC = 100))
        assertEquals(listOf(EngineAlert.OVERHEAT), a.check(EngineData(coolantC = 117)))
    }

    @Test
    fun lowVoltageOnlyWhileRunning() {
        val a = ObdAlerts()
        assertEquals(emptyList<EngineAlert>(), a.check(EngineData(rpm = 0, voltage = 12.3f)))
        assertEquals(listOf(EngineAlert.LOW_VOLTAGE), a.check(EngineData(rpm = 3000, voltage = 12.0f)))
    }

    @Test
    fun malfunctionLamp() {
        val a = ObdAlerts()
        assertEquals(listOf(EngineAlert.MALFUNCTION), a.check(EngineData(milOn = true)))
        assertEquals(emptyList<EngineAlert>(), a.check(EngineData(milOn = true)))
    }
}
