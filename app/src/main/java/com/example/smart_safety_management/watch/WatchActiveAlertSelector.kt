package com.example.smart_safety_management.watch

object WatchActiveAlertSelector {
    fun select(
        alerts: List<SafetyAlertRow>,
        wearState: String?,
    ): SafetyAlertRow? = alerts.firstOrNull { alert ->
        alert.resolvedAt == null && !isContradictedByCurrentState(alert, wearState)
    }

    private fun isContradictedByCurrentState(alert: SafetyAlertRow, wearState: String?): Boolean =
        alert.alertType == "REMOVED" && wearState == "WORN"
}
