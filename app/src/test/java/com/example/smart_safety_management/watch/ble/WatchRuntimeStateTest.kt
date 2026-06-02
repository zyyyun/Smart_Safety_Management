package com.example.smart_safety_management.watch.ble

import com.example.smart_safety_management.watch.DeviceRow
import com.example.smart_safety_management.watch.DeviceWatchSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRuntimeStateTest {

    private val now: Instant = Instant.parse("2026-06-02T03:00:00Z")

    @Test
    fun runtimeReadTimeWinsOverOlderSupabaseTimestamp() {
        val snapshot = WatchRuntimeSnapshot.from(
            device = device(lastCommAt = "2026-06-02T02:00:00Z"),
            dbSnapshot = dbSnapshot(updatedAt = "2026-06-02T02:30:00Z"),
            runtime = WatchRuntimeState(
                deviceId = 7,
                status = WatchRuntimeStatus.READING,
                lastReadAt = now.minusSeconds(5),
            ),
            now = now,
        )

        assertEquals("5초 전", snapshot.lastCommunicationLabel)
        assertTrue(snapshot.isFresh)
        assertEquals("수신 중", snapshot.statusLabel)
    }

    @Test
    fun staleRuntimeReadingWaitsForData() {
        val snapshot = WatchRuntimeSnapshot.from(
            device = device(),
            dbSnapshot = null,
            runtime = WatchRuntimeState(
                status = WatchRuntimeStatus.READING,
                lastReadAt = now.minusSeconds(11),
            ),
            now = now,
        )

        assertFalse(snapshot.isFresh)
        assertEquals("데이터 대기", snapshot.statusLabel)
    }

    @Test
    fun ppgOnlyRuntimeDisplaysPpgAndWaitsForHrAndTemp() {
        val snapshot = WatchRuntimeSnapshot.from(
            device = device(),
            dbSnapshot = null,
            runtime = WatchRuntimeState(
                deviceId = 7,
                latestReading = JcWearHealthReading(ppgValue = 932),
                lastReadAt = now,
            ),
            now = now,
        )

        assertEquals("932", snapshot.ppgDisplay)
        assertEquals("측정 대기", snapshot.hrDisplay)
        assertEquals("측정 대기", snapshot.tempDisplay)
    }

    @Test
    fun clearingRuntimeRemovesActiveDevice() {
        try {
            WatchRuntimeStore.update(WatchRuntimeState(deviceId = 7, status = WatchRuntimeStatus.CONNECTED))

            WatchRuntimeStore.clear(deviceId = 7)

            assertEquals(WatchRuntimeState(), WatchRuntimeStore.state.value)
        } finally {
            WatchRuntimeStore.clear()
        }
    }

    @Test
    fun clearingDifferentDevicePreservesActiveDevice() {
        try {
            val active = WatchRuntimeState(deviceId = 7, status = WatchRuntimeStatus.CONNECTED)
            WatchRuntimeStore.update(active)

            WatchRuntimeStore.clear(deviceId = 8)

            assertEquals(active, WatchRuntimeStore.state.value)
        } finally {
            WatchRuntimeStore.clear()
        }
    }

    @Test
    fun staleMonitoringSessionClearPreservesNewSameDeviceRuntime() {
        try {
            val active = WatchRuntimeState(
                deviceId = 7,
                status = WatchRuntimeStatus.CONNECTING,
                monitoringSessionId = 2L,
            )
            WatchRuntimeStore.update(active)

            WatchRuntimeStore.clearMonitoringSession(deviceId = 7, monitoringSessionId = 1L)

            assertEquals(active, WatchRuntimeStore.state.value)
        } finally {
            WatchRuntimeStore.clear()
        }
    }

    @Test
    fun serviceStartSeedPreservesSameDeviceRuntimeReading() {
        val reading = JcWearHealthReading(
            heartRate = 74,
            bodyTemp = 36.4f,
            batteryLevel = 81,
            ppgValue = 942,
        )
        val current = WatchRuntimeState(
            deviceId = 7,
            userId = "worker-1",
            macAddress = "21:02:02:06:01:69",
            monitoringSessionId = 1L,
            status = WatchRuntimeStatus.READING,
            lastReadAt = now,
            lastUploadAt = now.minusSeconds(1),
            latestReading = reading,
            lastError = "old transient error",
        )

        val seeded = current.seedForServiceStart(
            WatchBleServiceConfig(
                userId = "worker-1",
                deviceId = 7,
                macAddress = "21:02:02:06:01:69",
            ),
        )

        assertEquals(WatchRuntimeStatus.READING, seeded.status)
        assertEquals(1L, seeded.monitoringSessionId)
        assertEquals(now, seeded.lastReadAt)
        assertEquals(now.minusSeconds(1), seeded.lastUploadAt)
        assertEquals(reading, seeded.latestReading)
        assertEquals("old transient error", seeded.lastError)
    }

    @Test
    fun serviceStartSeedReplacesDifferentDeviceRuntime() {
        val current = WatchRuntimeState(
            deviceId = 8,
            userId = "worker-2",
            macAddress = "AA:BB:CC:DD:EE:FF",
            status = WatchRuntimeStatus.READING,
            lastReadAt = now,
            latestReading = JcWearHealthReading(heartRate = 99),
        )

        val seeded = current.seedForServiceStart(
            WatchBleServiceConfig(
                userId = "worker-1",
                deviceId = 7,
                macAddress = "21:02:02:06:01:69",
            ),
        )

        assertEquals(7, seeded.deviceId)
        assertEquals("worker-1", seeded.userId)
        assertEquals("21:02:02:06:01:69", seeded.macAddress)
        assertEquals(null, seeded.monitoringSessionId)
        assertEquals(WatchRuntimeStatus.CONNECTING, seeded.status)
        assertEquals(null, seeded.lastReadAt)
        assertEquals(null, seeded.latestReading)
    }

    @Test
    fun staleRuntimeReadingDoesNotOverrideDbSnapshotValues() {
        val snapshot = WatchRuntimeSnapshot.from(
            device = device(batteryLevel = 41),
            dbSnapshot = dbSnapshot(
                heartRate = 78,
                bodyTemp = 36.5f,
                batteryLevel = 52,
            ),
            runtime = WatchRuntimeState(
                deviceId = 7,
                lastReadAt = now.minusSeconds(11),
                latestReading = JcWearHealthReading(
                    heartRate = 120,
                    bodyTemp = 39.0f,
                    batteryLevel = 10,
                    ppgValue = 999,
                ),
            ),
            now = now,
        )

        assertEquals("--", snapshot.ppgDisplay)
        assertEquals("78 bpm", snapshot.hrDisplay)
        assertEquals("36.5°C", snapshot.tempDisplay)
        assertEquals("52%", snapshot.batteryDisplay)
    }

    @Test
    fun runtimeWithoutDeviceIdAndDifferentMacDoesNotOverrideRenderedDeviceSnapshot() {
        val snapshot = WatchRuntimeSnapshot.from(
            device = device(macAddress = "21:02:02:06:01:69", batteryLevel = 41),
            dbSnapshot = dbSnapshot(
                heartRate = 78,
                bodyTemp = 36.5f,
                batteryLevel = 52,
            ),
            runtime = WatchRuntimeState(
                macAddress = "AA:BB:CC:DD:EE:FF",
                status = WatchRuntimeStatus.READING,
                lastReadAt = now,
                latestReading = JcWearHealthReading(
                    heartRate = 120,
                    bodyTemp = 39.0f,
                    batteryLevel = 10,
                    ppgValue = 999,
                ),
            ),
            now = now,
        )

        assertNotEquals("수신 중", snapshot.statusLabel)
        assertFalse(snapshot.isFresh)
        assertEquals("-", snapshot.lastCommunicationLabel)
        assertEquals("--", snapshot.ppgDisplay)
        assertEquals("78 bpm", snapshot.hrDisplay)
        assertEquals("36.5°C", snapshot.tempDisplay)
        assertEquals("52%", snapshot.batteryDisplay)
    }

    @Test
    fun runtimeWithoutDeviceIdAndMatchingMacAppliesToRenderedDeviceSnapshot() {
        val snapshot = WatchRuntimeSnapshot.from(
            device = device(macAddress = "21:02:02:06:01:69", batteryLevel = 41),
            dbSnapshot = dbSnapshot(
                heartRate = 78,
                bodyTemp = 36.5f,
                batteryLevel = 52,
            ),
            runtime = WatchRuntimeState(
                macAddress = "21:02:02:06:01:69".lowercase(),
                status = WatchRuntimeStatus.READING,
                lastReadAt = now,
                latestReading = JcWearHealthReading(
                    heartRate = 120,
                    bodyTemp = 39.0f,
                    batteryLevel = 10,
                    ppgValue = 999,
                ),
            ),
            now = now,
        )

        assertEquals("수신 중", snapshot.statusLabel)
        assertTrue(snapshot.isFresh)
        assertEquals("0초 전", snapshot.lastCommunicationLabel)
        assertEquals("999", snapshot.ppgDisplay)
        assertEquals("120 bpm", snapshot.hrDisplay)
        assertEquals("39.0°C", snapshot.tempDisplay)
        assertEquals("10%", snapshot.batteryDisplay)
    }

    @Test
    fun runtimeForDifferentDeviceDoesNotOverrideRenderedDeviceSnapshot() {
        val snapshot = WatchRuntimeSnapshot.from(
            device = device(batteryLevel = 41),
            dbSnapshot = dbSnapshot(
                heartRate = 78,
                bodyTemp = 36.5f,
                batteryLevel = 52,
            ),
            runtime = WatchRuntimeState(
                deviceId = 8,
                status = WatchRuntimeStatus.READING,
                lastReadAt = now,
                latestReading = JcWearHealthReading(
                    heartRate = 120,
                    bodyTemp = 39.0f,
                    batteryLevel = 10,
                    ppgValue = 999,
                ),
            ),
            now = now,
        )

        assertNotEquals("수신 중", snapshot.statusLabel)
        assertFalse(snapshot.isFresh)
        assertEquals("-", snapshot.lastCommunicationLabel)
        assertEquals("--", snapshot.ppgDisplay)
        assertEquals("78 bpm", snapshot.hrDisplay)
        assertEquals("36.5°C", snapshot.tempDisplay)
        assertEquals("52%", snapshot.batteryDisplay)
    }

    @Test
    fun batteryPrecedenceUsesRuntimeThenDbThenDevice() {
        val device = device(batteryLevel = 41)
        val dbSnapshot = dbSnapshot(batteryLevel = 52)

        val runtimeBattery = WatchRuntimeSnapshot.from(
            device = device,
            dbSnapshot = dbSnapshot,
            runtime = WatchRuntimeState(
                deviceId = 7,
                lastReadAt = now,
                latestReading = JcWearHealthReading(batteryLevel = 63),
            ),
            now = now,
        )
        val dbBattery = WatchRuntimeSnapshot.from(
            device = device,
            dbSnapshot = dbSnapshot,
            runtime = WatchRuntimeState(),
            now = now,
        )
        val deviceBattery = WatchRuntimeSnapshot.from(
            device = device,
            dbSnapshot = null,
            runtime = WatchRuntimeState(),
            now = now,
        )

        assertEquals("63%", runtimeBattery.batteryDisplay)
        assertEquals("52%", dbBattery.batteryDisplay)
        assertEquals("41%", deviceBattery.batteryDisplay)
    }

    @Test
    fun noMacOrDeviceRendersUnregistered() {
        val snapshot = WatchRuntimeSnapshot.from(
            device = null,
            dbSnapshot = null,
            runtime = WatchRuntimeState(),
            now = now,
        )

        assertEquals("미등록", snapshot.statusLabel)
    }

    private fun device(
        macAddress: String? = "21:02:02:06:01:69",
        lastCommAt: String? = null,
        batteryLevel: Int? = null,
    ) = DeviceRow(
        deviceId = 7,
        deviceType = "watch",
        macAddress = macAddress,
        lastCommAt = lastCommAt,
        batteryLevel = batteryLevel,
    )

    private fun dbSnapshot(
        updatedAt: String? = null,
        heartRate: Int? = null,
        bodyTemp: Float? = null,
        batteryLevel: Int? = null,
    ) = DeviceWatchSnapshot(
        deviceId = 7,
        heartRate = heartRate,
        bodyTemp = bodyTemp,
        updatedAt = updatedAt,
        batteryLevel = batteryLevel,
    )
}
