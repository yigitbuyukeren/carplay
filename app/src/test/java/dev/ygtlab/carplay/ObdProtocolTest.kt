package dev.ygtlab.carplay

import dev.ygtlab.carplay.data.ObdProtocol
import dev.ygtlab.carplay.data.Pid
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdProtocolTest {
    @Test
    fun readsRpmWithSpacesAndSearching() {
        val raw = "SEARCHING...\r41 0C 1A F8\r\r>"
        val d = ObdProtocol.mode01Data(raw, Pid.RPM)!!
        assertEquals(1726, ObdProtocol.rpm(d))
    }

    @Test
    fun readsCompactReplyAndIgnoresOtherEcus() {
        val raw = "410D32\r410D33\r"
        assertArrayEquals(intArrayOf(0x32), ObdProtocol.mode01Data(raw, Pid.SPEED))
    }

    @Test
    fun noDataIsNoAnswer() {
        assertTrue(ObdProtocol.isNoAnswer("NO DATA\r\r>"))
        assertTrue(ObdProtocol.isNoAnswer("SEARCHING...\rUNABLE TO CONNECT\r>"))
        assertFalse(ObdProtocol.isNoAnswer("4100BE1F8011\r"))
        assertNull(ObdProtocol.mode01Data("NO DATA", Pid.RPM))
    }

    @Test
    fun supportedPidBitmask() {
        val pids = ObdProtocol.supportedPids("41 00 BE 1F 80 11", 0x00)
        assertTrue(pids.containsAll(listOf(0x01, 0x04, 0x05, 0x0C, 0x0D, 0x0F, 0x11, 0x20)))
        assertFalse(Pid.MODULE_VOLTAGE in pids)
        assertEquals(setOf(0x42), ObdProtocol.supportedPids("41 40 40 00 00 00", 0x40))
    }

    @Test
    fun decodesValues() {
        assertEquals(92, ObdProtocol.temperatureC(intArrayOf(132)))
        assertEquals(100, ObdProtocol.percent(intArrayOf(255)))
        assertEquals(14.088f, ObdProtocol.moduleVoltage(intArrayOf(0x37, 0x08)), 0.001f)
        assertEquals(Pair(true, 2), ObdProtocol.monitorStatus(intArrayOf(0x82, 0, 0, 0)))
        assertEquals(12.6f, ObdProtocol.adapterVoltage("12.6V\r\r>")!!, 0.01f)
    }

    @Test
    fun detectsCanProtocol() {
        assertTrue(ObdProtocol.isCanProtocol("A6"))
        assertTrue(ObdProtocol.isCanProtocol("7"))
        assertFalse(ObdProtocol.isCanProtocol("A5"))
        assertFalse(ObdProtocol.isCanProtocol("4"))
    }

    @Test
    fun formatsCodes() {
        assertEquals("P0335", ObdProtocol.formatCode(0x03, 0x35))
        assertEquals("C0123", ObdProtocol.formatCode(0x41, 0x23))
        assertEquals("U1A00", ObdProtocol.formatCode(0xDA, 0x00))
    }

    @Test
    fun canCodesSingleFrame() {
        assertEquals(listOf("P0335"), ObdProtocol.troubleCodes("43 01 03 35\r", can = true))
        assertEquals(emptyList<String>(), ObdProtocol.troubleCodes("43 00\r", can = true))
    }

    @Test
    fun canCodesMultiFrame() {
        val raw = "00A\r0: 43 04 01 33 03 35\r1: 01 13 C1 23 00 00\r"
        assertEquals(listOf("P0133", "P0335", "P0113", "U0123"), ObdProtocol.troubleCodes(raw, can = true))
    }

    @Test
    fun kLineCodes() {
        val raw = "43 01 33 03 35 00 00\r43 01 13 00 00 00 00\r"
        assertEquals(listOf("P0133", "P0335", "P0113"), ObdProtocol.troubleCodes(raw, can = false))
    }
}
