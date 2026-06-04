package com.example.smart_safety_management.watch.ble

import java.util.UUID

object JcWearBModeProtocol {
    val serviceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val dataUuid: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    private val resetCommandBytes = byteArrayOf(0x2E, 0x00, 0x00) + ByteArray(13)
    private val ppgInitCommandBytes = byteArrayOf(0x3B, 0x01, 0x01) + ByteArray(13)
    private val realtimeStartCommandBytes = byteArrayOf(0x0B, 0x01, 0x01) + ByteArray(13)

    val resetCommand: ByteArray get() = resetCommandBytes.copyOf()
    val ppgInitCommand: ByteArray get() = ppgInitCommandBytes.copyOf()
    val realtimeStartCommand: ByteArray get() = realtimeStartCommandBytes.copyOf()

    fun parsePpg(payload: ByteArray): Int? {
        if (payload.size < 3) return null
        val high = payload[1].toInt() and 0xFF
        val low = payload[2].toInt() and 0xFF
        return (high shl 8) or low
    }
}
