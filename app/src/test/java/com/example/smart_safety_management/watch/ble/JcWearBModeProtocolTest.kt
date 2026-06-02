package com.example.smart_safety_management.watch.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JcWearBModeProtocolTest {
    @Test
    fun commandsAreFixedSixteenBytePayloadsWithoutCrc() {
        assertArrayEquals(byteArrayOf(0x2E, 0x00, 0x00) + ByteArray(13), JcWearBModeProtocol.resetCommand)
        assertArrayEquals(byteArrayOf(0x3B, 0x01, 0x01) + ByteArray(13), JcWearBModeProtocol.ppgInitCommand)
        assertArrayEquals(byteArrayOf(0x0B, 0x01, 0x01) + ByteArray(13), JcWearBModeProtocol.realtimeStartCommand)
    }

    @Test
    fun parsePpgReadsSecondAndThirdBytesAsBigEndianValue() {
        val parsed = JcWearBModeProtocol.parsePpg(byteArrayOf(0x00, 0x03, 0xA4.toByte()))

        assertEquals(932, parsed)
    }

    @Test
    fun parsePpgRejectsShortPayloads() {
        assertNull(JcWearBModeProtocol.parsePpg(byteArrayOf(0x00, 0x03)))
    }
}
