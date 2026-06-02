package com.example.smart_safety_management.watch.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JcWearScanModelsTest {

    @Test
    fun displayNameFallsBackWhenBleNameIsBlank() {
        val device = JcWearDiscoveredDevice(
            address = "21:02:02:06:01:69",
            name = " ",
            rssi = -62,
        )

        assertEquals("Unknown J2208A", device.displayName)
    }

    @Test
    fun rssiLabelFormatsDbm() {
        val device = JcWearDiscoveredDevice(
            address = "21:02:02:06:01:69",
            name = "J2208A",
            rssi = -62,
        )

        assertEquals("-62 dBm", device.rssiLabel)
    }

    @Test
    fun registrationSerialUsesAddressWhenDeviceNameExists() {
        val device = JcWearDiscoveredDevice(
            address = "21:02:02:06:01:69",
            name = "Worker Watch",
            rssi = -55,
        )

        assertEquals("J2208A-21:02:02:06:01:69", device.registrationSerial)
    }

    @Test
    fun scanActionLabelReflectsPermissionAndScanState() {
        assertEquals("권한 허용", JcWearScanUiState(permissionGranted = false).scanActionLabel)
        assertEquals("스캔 중지", JcWearScanUiState(permissionGranted = true, scanning = true).scanActionLabel)
        assertEquals("스캔 시작", JcWearScanUiState(permissionGranted = true, scanning = false).scanActionLabel)
    }

    @Test
    fun canRegisterOnlyWhenConnectedDeviceSelected() {
        val disconnected = JcWearScanUiState(
            permissionGranted = true,
            selectedAddress = "21:02:02:06:01:69",
            connectionState = JcWearConnectionState.DISCONNECTED,
        )
        val connected = disconnected.copy(connectionState = JcWearConnectionState.CONNECTED)

        assertFalse(disconnected.canRegister)
        assertTrue(connected.canRegister)
    }

    @Test
    fun healthParserReadsBatteryFromNestedSdkData() {
        val reading = JcWearHealthReading.fromSdkMap(
            mapOf(
                "dataType" to "9",
                "dicData" to mapOf("batteryLevel" to "87"),
            ),
        )

        assertEquals(87, reading.batteryLevel)
    }

    @Test
    fun healthParserReadsHeartAndTemperatureFromMeasurementData() {
        val reading = JcWearHealthReading.fromSdkMap(
            mapOf(
                "dataType" to "74",
                "dicData" to mapOf(
                    "heartRate" to "82",
                    "KHrvTempValue" to "36.8",
                ),
            ),
        )

        assertEquals(82, reading.heartRate)
        assertEquals(36.8f, reading.bodyTemp)
    }

    @Test
    fun healthParserReadsHeartAndTemperatureFromRealtimeStepData() {
        val reading = JcWearHealthReading.fromSdkMap(
            mapOf(
                "dataType" to "23",
                "dicData" to mapOf(
                    "heartRate" to "81",
                    "TempData" to "36.6",
                ),
            ),
        )

        assertEquals(81, reading.heartRate)
        assertEquals(36.6f, reading.bodyTemp)
    }

    @Test
    fun healthParserReadsTopLevelBatteryFromSdkData() {
        val reading = JcWearHealthReading.fromSdkMap(
            mapOf(
                "dataType" to "GetDeviceBatteryLevel",
                "batteryLevel" to 86,
            ),
        )

        assertEquals(86, reading.batteryLevel)
    }

    @Test
    fun healthParserReadsHeartAliasesFromSdkMeasurementData() {
        val reading = JcWearHealthReading.fromSdkMap(
            mapOf(
                "dataType" to "MeasurementHeartCallback",
                "dicData" to mapOf("heartValue" to 78),
            ),
        )

        assertEquals(78, reading.heartRate)
    }
}
