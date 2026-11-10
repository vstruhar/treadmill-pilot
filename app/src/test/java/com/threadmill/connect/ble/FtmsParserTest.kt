package com.threadmill.connect.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtmsParserTest {
    @Test
    fun parsesInstantaneousSpeedInHundredthsOfAKilometer() {
        val metrics = FtmsParser.parseTreadmill(byteArrayOf(0x00, 0x00, 0xE8.toByte(), 0x03))
        assertEquals(10.0, metrics!!.speedKmh!!, 0.001)
        assertNull(metrics.averageSpeedKmh)
    }

    @Test
    fun skipsSpeedWhenMoreDataBitIsSet() {
        val metrics = FtmsParser.parseTreadmill(byteArrayOf(0x03, 0x00, 0xF4.toByte(), 0x01))
        assertNull(metrics!!.speedKmh)
        assertEquals(5.0, metrics.averageSpeedKmh!!, 0.001)
    }

    @Test
    fun parsesDistanceAndInclineAfterSpeed() {
        val metrics = FtmsParser.parseTreadmill(
            byteArrayOf(
                0x0C, 0x00,
                0x64, 0x00,
                0x10, 0x20, 0x30,
                0x9C.toByte(), 0xFF.toByte(),
                0x00, 0x00,
            ),
        )
        assertEquals(1.0, metrics!!.speedKmh!!, 0.001)
        assertEquals(0x302010, metrics.distanceMeters)
        assertEquals(-10.0, metrics.inclinePercent!!, 0.001)
    }

    @Test
    fun returnsPartialPacketWhenAFieldIsCutOff() {
        val metrics = FtmsParser.parseTreadmill(byteArrayOf(0x00, 0x00, 0x0A))
        assertNull(metrics!!.speedKmh)
    }

    @Test
    fun parsesHeartRateMeasurement() {
        assertEquals(72, FtmsParser.parseHeartRate(byteArrayOf(0x00, 72)))
        assertEquals(300, FtmsParser.parseHeartRate(byteArrayOf(0x01, 0x2C, 0x01)))
    }

    @Test
    fun parsesSupportedRanges() {
        val speed = FtmsParser.parseSpeedRange(byteArrayOf(0x32, 0x00, 0x20, 0x03, 0x0A, 0x00))
        assertEquals(0.5, speed!!.minKmh, 0.001)
        assertEquals(8.0, speed.maxKmh, 0.001)
        assertEquals(0.1, speed.stepKmh, 0.001)

        val incline = FtmsParser.parseInclineRange(
            byteArrayOf(0x00, 0x00, 0x64, 0x00, 0x05, 0x00),
        )
        assertEquals(0.0, incline!!.minPercent, 0.001)
        assertEquals(10.0, incline.maxPercent, 0.001)
        assertEquals(0.5, incline.stepPercent, 0.001)
    }
}
