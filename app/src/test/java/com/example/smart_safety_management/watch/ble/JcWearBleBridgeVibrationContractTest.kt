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

    @Test
    fun bridgeSequencesBModeInitThroughWriteCallbacks() {
        val src = bridge.readText()

        assertTrue(src.contains("enum class BModeInitStage"))
        assertTrue(src.contains("override fun onCharacteristicWrite"))
        assertTrue(src.contains("handleBModeWrite(gatt, characteristic, status)"))
        assertTrue(src.contains("BModeInitStage.RESET_SENT"))
        assertTrue(src.contains("BModeInitStage.PPG_INIT_SENT"))
        assertTrue(src.contains("BModeInitStage.REALTIME_START_SENT"))
        assertTrue(src.contains("BModeInitStage.READING"))

        val realtimeWrite = src.indexOf("writeBModeCommand(gatt, JcWearBModeProtocol.realtimeStartCommand)")
        val firstSchedule = src.indexOf("scheduleBModeRead(gatt)", startIndex = realtimeWrite.coerceAtLeast(0))
        val writeCallback = src.indexOf("override fun onCharacteristicWrite")
        assertTrue(realtimeWrite >= 0)
        assertTrue(writeCallback >= 0)
        assertTrue(firstSchedule > writeCallback)
    }

    @Test
    fun identifyUsesSdkCommandCharacteristicSeparateFromBModeDataCharacteristic() {
        val src = bridge.readText()

        assertTrue(src.contains("private fun sdkCommandCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic?"))
        assertTrue(src.contains("SDK_COMMAND_CHARACTERISTIC_UUID"))
        assertTrue(src.contains("writeSdkCommand(activeGatt, BleSDK.MotorVibrationWithTimes(IDENTIFY_VIBRATION_TIMES))"))
        assertTrue(src.contains("JcWearBModeProtocol.dataUuid"))
    }

    @Test
    fun bridgeOnlyEmitsPpgReadingWhenParserReturnsValue() {
        val src = bridge.readText()

        assertTrue(src.contains("JcWearBModeProtocol.parsePpg(value)?.let { ppg ->"))
        assertTrue(src.contains("_healthReadings.tryEmit(JcWearHealthReading(ppgValue = ppg))"))
    }

    @Test
    fun bridgeIgnoresCallbacksFromStaleGattInstances() {
        val src = bridge.readText()

        assertTrue(src.contains("if (gatt !== this@JcWearBleBridge.gatt) return"))
    }

    @Test
    fun bridgeSerializesIdentifyWithBModeGattOperations() {
        val src = bridge.readText()

        assertTrue(src.contains("private var gattOperationInFlight = false"))
        assertTrue(src.contains("private var pendingIdentifyCommand = false"))
        assertTrue(src.contains("private fun processPendingIdentify"))
        assertTrue(src.contains("if (gattOperationInFlight) return false"))
        assertTrue(src.contains("gattOperationInFlight = true"))
        assertTrue(src.contains("gattOperationInFlight = false"))
        assertTrue(src.contains("if (processPendingIdentify(gatt)) return"))
        assertTrue(src.contains("if (characteristic.uuid != JcWearBModeProtocol.dataUuid)"))
    }

    @Test
    fun bridgeUsesApi33BluetoothStatusCodeForWriteAcceptance() {
        val src = bridge.readText()

        assertTrue(src.contains("import android.bluetooth.BluetoothStatusCodes"))
        assertTrue(src.contains(") == BluetoothStatusCodes.SUCCESS"))
        assertFalse(src.contains(") == BluetoothGatt.GATT_SUCCESS"))
    }

    @Test
    fun scanResultNameAccessSuppressesPermissionLintAfterPermissionGate() {
        val src = bridge.readText()
        val conversionIndex = src.indexOf("private fun ScanResult.toJcWearDevice")
        val suppressIndex = src.indexOf("@SuppressLint(\"MissingPermission\")", startIndex = conversionIndex - 80)

        assertTrue(conversionIndex > 0)
        assertTrue(suppressIndex in 0 until conversionIndex)
    }

    @Test
    fun bridgeTimesOutStuckGattOperations() {
        val src = bridge.readText()

        assertTrue(src.contains("private var gattOperationToken = 0"))
        assertTrue(src.contains("private fun markGattOperationInFlight"))
        assertTrue(src.contains("private fun clearGattOperationInFlight"))
        assertTrue(src.contains("private fun completeGattOperationIfCurrent(): Boolean"))
        assertTrue(src.contains("if (!completeGattOperationIfCurrent()) return"))
        assertTrue(src.contains("GATT_OPERATION_TIMEOUT_MS"))
        assertTrue(src.contains("failBModeRead(\"B-mode GATT operation timed out.\")"))
        assertTrue(src.contains("markGattOperationInFlight(gatt)"))
        assertTrue(src.contains("clearGattOperationInFlight()"))
        assertTrue(src.contains("if (!gattOperationInFlight) return false"))
    }

    @Test
    fun bridgeCanConnectWithoutStartingTelemetryForPairingUi() {
        val src = bridge.readText()

        assertTrue(src.contains("startTelemetryOnConnect: Boolean = true"))
        assertTrue(src.contains("private val startTelemetryOnConnect"))
        assertTrue(src.contains("if (startTelemetryOnConnect) {"))
        assertTrue(src.contains("startBModeReadLoop(gatt)"))
    }
}
