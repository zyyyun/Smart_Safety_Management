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
    fun bridgeUsesBModeReadLoopForMonitoring() {
        val src = bridge.readText()

        assertTrue(src.contains("JcWearBModeProtocol.dataUuid"))
        assertTrue(src.contains("JcWearBModeProtocol.resetCommand"))
        assertTrue(src.contains("JcWearBModeProtocol.ppgInitCommand"))
        assertTrue(src.contains("JcWearBModeProtocol.realtimeStartCommand"))
        assertTrue(src.contains("JcWearBModeProtocol.parsePpg"))
        assertFalse(src.contains("BleSDK.RealTimeStep(true, true)"))
        assertFalse(src.contains("BleSDK.GetDeviceBatteryLevel()"))
    }

    @Test
    fun bridgeSupportsManualIdentifyVibration() {
        val src = bridge.readText()

        assertTrue(src.contains("fun identify(device: JcWearDiscoveredDevice)"))
        assertTrue(src.contains("private const val IDENTIFY_VIBRATION_TIMES = 2"))
        assertTrue(src.contains("BleSDK.MotorVibrationWithTimes(IDENTIFY_VIBRATION_TIMES)"))
    }
}
