package com.example.smart_safety_management.watch

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WatchConnectionStatusTest {
    private fun watch(lastCommAt: String?, updatedAt: String? = null) = DeviceRow(
        deviceId = 1,
        deviceType = "WATCH",
        macAddress = "21:02:02:06:01:69",
        lastCommAt = lastCommAt,
        updatedAt = updatedAt,
    )

    @Test
    fun recentSupabaseOffsetTimestampIsConnected() {
        val status = computeStatus(
            watch(lastCommAt = "2026-06-01T01:14:52.289+00:00"),
            now = Instant.parse("2026-06-01T01:14:55Z"),
        )

        assertEquals(WatchStatus.CONNECTED, status)
    }

    @Test
    fun staleTimestampIsDisconnected() {
        val status = computeStatus(
            watch(lastCommAt = "2026-06-01T01:14:52.289+00:00"),
            now = Instant.parse("2026-06-01T01:20:30Z"),
        )

        assertEquals(WatchStatus.DISCONNECTED, status)
    }

    @Test
    fun freshUpdatedAtCanKeepExistingWatchConnectedWhenLastCommIsMissing() {
        val status = computeStatus(
            watch(
                lastCommAt = null,
                updatedAt = "2026-06-01T01:14:52.289+00:00",
            ),
            now = Instant.parse("2026-06-01T01:14:55Z"),
        )

        assertEquals(WatchStatus.CONNECTED, status)
    }
}
