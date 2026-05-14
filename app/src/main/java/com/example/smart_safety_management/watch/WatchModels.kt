package com.example.smart_safety_management.watch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phase 7 / 07-03 Wave 3 — supabase-kt 2.2.0 의 Realtime/PostgREST 디코딩 모델.
 * 010_watch_pipeline.sql 스키마 그대로. 컬럼명은 snake_case → SerialName 매핑.
 *
 * ⚠ Pitfall 5 (07-CONTEXT.md D-03): safety_alerts 의 컬럼명은 ack_at —
 * REQUIREMENTS.md §7 의 acknowledg* 표기는 오기. 본 파일에서 ack_at 으로 통일.
 */

@Serializable
data class DeviceWatchSnapshot(
    @SerialName("device_id") val deviceId: Int,
    @SerialName("heart_rate") val heartRate: Int? = null,
    @SerialName("body_temp") val bodyTemp: Float? = null,
    @SerialName("battery_level") val batteryLevel: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class WearStateEventRow(
    @SerialName("event_id") val eventId: Long,
    @SerialName("device_id") val deviceId: Int,
    val ts: String,
    @SerialName("from_state") val fromState: String,
    @SerialName("to_state") val toState: String,
)

@Serializable
data class SafetyAlertRow(
    @SerialName("alert_id") val alertId: Long,
    @SerialName("device_id") val deviceId: Int,
    @SerialName("alert_type") val alertType: String,   // TACHY|REMOVED|COMMS_LOST
    val severity: String,                              // CAUTION|WARNING|DANGER
    @SerialName("raised_at") val raisedAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("ack_at") val ackAt: String? = null,
)

@Serializable
data class DeviceRow(
    @SerialName("device_id") val deviceId: Int,
    @SerialName("device_type") val deviceType: String,
    @SerialName("mac_address") val macAddress: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("last_comm_at") val lastCommAt: String? = null,
)

// Retrofit payload — Edge Function 'watch-ack'
@Serializable
data class WatchAckRequest(
    val action: String = "watch-ack",
    val alert_id: Long,
    val user_id: String,
)

@Serializable
data class WatchAckResponse(
    val ok: Boolean? = null,
    val ack_at: String? = null,
    val alert_id: Long? = null,
    val error: String? = null,
)

// Retrofit payload — Edge Function 'watch-pair'
@Serializable
data class WatchPairRequest(
    val action: String = "watch-pair",
    val user_id: String,
    val mac_address: String? = null,
    val op: String,  // "pair" | "unpair"
)

@Serializable
data class WatchPairResponse(
    val ok: Boolean? = null,
    val device_id: Int? = null,
    val mac_address: String? = null,
    val op: String? = null,
    val count: Int? = null,
    val error: String? = null,
)
