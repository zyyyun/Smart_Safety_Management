package com.example.smart_safety_management.watch.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JcWearBleBridgeVibrationContractTest {
    private val bridge = File(
        "src/main/java/com/example/smart_safety_management/watch/ble/JcWearBleBridge.kt",
    )

    @Test
    fun bridgeUsesRealtimeStreamInsteadOfRepeatedManualMeasurementStarts() {
        val src = bridge.readText()

        assertTrue(src.contains("BleSDK.RealTimeStep(true, true)"))
        assertFalse(src.contains("StartDeviceMeasurementWithType"))
        assertFalse(src.contains("20_000"))
    }

    @Test
    fun bridgeSupportsManualIdentifyVibration() {
        val src = bridge.readText()

        assertTrue(src.contains("fun identify(device: JcWearDiscoveredDevice)"))
        assertTrue(src.contains("private const val IDENTIFY_VIBRATION_TIMES = 2"))
        assertTrue(src.contains("BleSDK.MotorVibrationWithTimes(IDENTIFY_VIBRATION_TIMES)"))
    }
}
