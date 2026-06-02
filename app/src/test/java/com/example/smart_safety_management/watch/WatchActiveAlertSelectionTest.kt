package com.example.smart_safety_management.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchActiveAlertSelectionTest {
    private fun alert(type: String, id: Long = 1L, resolvedAt: String? = null) = SafetyAlertRow(
        alertId = id,
        deviceId = 1,
        alertType = type,
        severity = "CAUTION",
        raisedAt = "2026-06-01T01:00:00Z",
        resolvedAt = resolvedAt,
        ackAt = null,
    )

    @Test
    fun wornStateSuppressesUnresolvedRemovedAlert() {
        val selected = WatchActiveAlertSelector.select(
            alerts = listOf(alert("REMOVED")),
            wearState = "WORN",
        )

        assertNull(selected)
    }

    @Test
    fun wornStateKeepsOtherActiveAlerts() {
        val selected = WatchActiveAlertSelector.select(
            alerts = listOf(alert("TACHY")),
            wearState = "WORN",
        )

        assertEquals("TACHY", selected?.alertType)
    }

    @Test
    fun nonWornStateKeepsUnresolvedRemovedAlert() {
        val selected = WatchActiveAlertSelector.select(
            alerts = listOf(alert("REMOVED")),
            wearState = "OFF",
        )

        assertEquals("REMOVED", selected?.alertType)
    }

    @Test
    fun resolvedAlertsAreIgnored() {
        val selected = WatchActiveAlertSelector.select(
            alerts = listOf(alert("REMOVED", resolvedAt = "2026-06-01T01:05:00Z")),
            wearState = "OFF",
        )

        assertNull(selected)
    }
}
