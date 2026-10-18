package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.EngineData
import dev.ygtlab.carplay.data.FakeElmLink
import dev.ygtlab.carplay.data.ObdSession
import dev.ygtlab.carplay.data.Pid
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdSessionTest {
    @Test
    fun fullSessionAgainstFakeAdapter() = runBlocking {
        val link = FakeElmLink()
        link.open()
        val s = ObdSession(link)
        s.setup()
        assertTrue(s.discover())
        assertTrue(s.isCan)
        assertTrue(Pid.MODULE_VOLTAGE in s.supported)

        var d = s.readFast(EngineData())
        assertNotNull(d.rpm)
        d = s.readSlow(d)
        assertEquals(92, d.coolantC)
        assertEquals(13.8f, d.voltage!!, 0.01f)
        assertTrue(d.milOn)

        assertEquals(listOf("P0335"), s.readCodes())
        assertTrue(s.clearCodes())
        assertEquals(emptyList<String>(), s.readCodes())
    }
}
