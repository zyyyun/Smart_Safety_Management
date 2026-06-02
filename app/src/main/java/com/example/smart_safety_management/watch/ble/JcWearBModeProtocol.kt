package com.example.smart_safety_management.watch.ble

import java.util.UUID

object JcWearBModeProtocol {
    val serviceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val dataUuid: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    val resetCommand: ByteArray = byteArrayOf(0x2E, 0x00, 0x00) + ByteArray(13)
    val ppgInitCommand: ByteArray = byteArrayOf(0x3B, 0x01, 0x01) + ByteArray(13)
    val realtimeStartCommand: ByteArray = byteArrayOf(0x0B, 0x01, 0x01) + ByteArray(13)

    fun parsePpg(payload: ByteArray): Int? {
        if (payload.size < 3) return null
        val high = payload[1].toInt() and 0xFF
        val low = payload[2].toInt() and 0xFF
        return (high shl 8) or low
    }
}
