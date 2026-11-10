package com.threadmill.connect.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtmsControlTest {
    @Test
    fun encodesTargetSpeedInHundredths() {
        val payload = FtmsControl.setTargetSpeed(10.0)
        assertEquals(0x02, payload[0].toInt() and 0xFF)
        assertEquals(0xE8, payload[1].toInt() and 0xFF)
        assertEquals(0x03, payload[2].toInt() and 0xFF)
    }

    @Test
    fun clampsSpeedToTheSupportedRange() {
        val range = SpeedRange(minKmh = 1.0, maxKmh = 8.0, stepKmh = 0.1)
        val payload = FtmsControl.setTargetSpeed(30.0, range)
        assertEquals(0x20, payload[1].toInt() and 0xFF)
        assertEquals(0x03, payload[2].toInt() and 0xFF)
    }

    @Test
    fun parsesASuccessfulResponse() {
        val response = FtmsControl.parseResponse(byteArrayOf(0x80.toByte(), 0x00, 0x01))
        assertEquals(FtmsControl.REQUEST_CONTROL, response!!.opcode)
        assertTrue(response.success)
    }

    @Test
    fun ignoresPacketsThatAreNotResponses() {
        assertNull(FtmsControl.parseResponse(byteArrayOf(0x02, 0x10, 0x00)))
    }

    @Test
    fun parsesMachineStatus() {
        val speed = FtmsControl.parseMachineStatus(byteArrayOf(0x05, 0x90.toByte(), 0x01))
        assertEquals(4.0, (speed as FtmsControl.MachineEvent.TargetSpeedChanged).kmh, 0.001)
        val paused = FtmsControl.parseMachineStatus(byteArrayOf(0x02, 0x02))
        assertTrue((paused as FtmsControl.MachineEvent.StoppedOrPaused).paused)
        val stopped = FtmsControl.parseMachineStatus(byteArrayOf(0x02, 0x01))
        assertFalse((stopped as FtmsControl.MachineEvent.StoppedOrPaused).paused)
        assertTrue(FtmsControl.parseMachineStatus(byteArrayOf(0x03)) is FtmsControl.MachineEvent.SafetyStop)
    }

    @Test
    fun rejectsOpcodesOutsideTheAllowList() {
        assertTrue(FtmsControl.isAllowed(FtmsControl.requestControl()))
        assertTrue(FtmsControl.isAllowed(FtmsControl.pause()))
        assertTrue(FtmsControl.isAllowed(FtmsControl.stop()))
        assertFalse(FtmsControl.isAllowed(byteArrayOf(0x03)))
        assertFalse(FtmsControl.isAllowed(byteArrayOf()))
    }
}
