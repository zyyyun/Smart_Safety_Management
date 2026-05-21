package com.example.smart_safety_management.watch

import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 2026-05-21 — J2208A 워치 raw 데이터를 사람이 이해할 수 있는 라벨/색상으로 정제.
 *
 * ⚠ 의료기기 면책 (PROJECT.md key decision):
 *   - HR=0, wear-state ∈ {OFF, WARMUP} 시 측정값 무효 → "측정 대기" 라벨.
 *   - WORN 시점의 측정값만 신뢰. 단 임상 진단 표현 (혈압, 심전도, 의료기기) 사용 금지.
 *   - "정상 / 주의 / 위험" 만 사용 (의료기기 카테고리 회피).
 */
object WatchHealthFormatter {

    // ── 색상 팔레트 (시맨틱) ───────────────────────────────────────────────
    val ColorNormal: Color = Color(0xFF22C55E)   // 정상 (초록)
    val ColorWarning: Color = Color(0xFFF59E0B)  // 주의 (노랑/주황)
    val ColorDanger: Color = Color(0xFFEF4444)   // 위험 (빨강)
    val ColorIdle: Color = Color(0xFF9CA3AF)     // 측정 대기 (회색)
    val ColorInfo: Color = Color(0xFF3B82F6)     // 정보 (파랑)

    enum class HealthLevel { IDLE, NORMAL, WARNING, DANGER }

    fun levelToColor(level: HealthLevel): Color = when (level) {
        HealthLevel.IDLE -> ColorIdle
        HealthLevel.NORMAL -> ColorNormal
        HealthLevel.WARNING -> ColorWarning
        HealthLevel.DANGER -> ColorDanger
    }

    // ── 심박 (HR) ──────────────────────────────────────────────────────────
    /** WORN 상태 + 0 < HR 일 때만 신뢰. 그 외 IDLE. */
    fun classifyHr(hr: Int?, wearState: String?): HealthLevel {
        if (hr == null || hr <= 0) return HealthLevel.IDLE
        if (wearState in listOf("OFF", "WARMUP")) return HealthLevel.IDLE
        return when {
            hr >= 120 -> HealthLevel.DANGER         // 빈맥 (위험)
            hr in 101..119 -> HealthLevel.WARNING   // 빈맥 (주의)
            hr in 60..100 -> HealthLevel.NORMAL     // 정상 안정
            hr in 50..59 -> HealthLevel.WARNING     // 서맥 (주의)
            else -> HealthLevel.DANGER              // 서맥 (위험, ≤49)
        }
    }

    fun hrDisplay(hr: Int?, wearState: String?): String =
        if (classifyHr(hr, wearState) == HealthLevel.IDLE) "— bpm"
        else "${hr ?: "—"} bpm"

    fun hrLabel(level: HealthLevel): String = when (level) {
        HealthLevel.IDLE -> "측정 대기"
        HealthLevel.NORMAL -> "정상 안정"
        HealthLevel.WARNING -> "주의 — 심박 비정상"
        HealthLevel.DANGER -> "위험 — 빈맥/서맥"
    }

    // ── 체온 ──────────────────────────────────────────────────────────────
    fun classifyTemp(temp: Float?, wearState: String?): HealthLevel {
        if (temp == null || temp <= 0f) return HealthLevel.IDLE
        if (wearState in listOf("OFF", "WARMUP")) return HealthLevel.IDLE
        return when {
            temp >= 38.1f -> HealthLevel.DANGER     // 고열
            temp >= 37.6f -> HealthLevel.WARNING    // 미열
            temp >= 36.0f -> HealthLevel.NORMAL     // 정상
            temp >= 35.5f -> HealthLevel.WARNING    // 저체온 주의
            else -> HealthLevel.DANGER              // 저체온 위험
        }
    }

    fun tempDisplay(temp: Float?, wearState: String?): String =
        if (classifyTemp(temp, wearState) == HealthLevel.IDLE) "—.—°C"
        else String.format("%.1f°C", temp)

    fun tempLabel(level: HealthLevel): String = when (level) {
        HealthLevel.IDLE -> "측정 대기"
        HealthLevel.NORMAL -> "정상 체온"
        HealthLevel.WARNING -> "주의 — 체온 비정상"
        HealthLevel.DANGER -> "위험 — 고열/저체온"
    }

    // ── 착용 상태 ─────────────────────────────────────────────────────────
    fun wearStateLabel(state: String?): String = when (state) {
        "WORN" -> "정상 착용 중"
        "WARMUP" -> "측정 준비 중 (착용 직후)"
        "OFF" -> "탈착됨"
        "TRANSIENT" -> "착탈 중"
        "ABNORMAL" -> "측정 비정상"
        null -> "상태 불명"
        else -> state
    }

    fun wearStateColor(state: String?): Color = when (state) {
        "WORN" -> ColorNormal
        "WARMUP", "TRANSIENT" -> ColorInfo
        "OFF" -> ColorIdle
        "ABNORMAL" -> ColorWarning
        else -> ColorIdle
    }

    // ── 위험 알림 라벨 ─────────────────────────────────────────────────────
    fun alertTypeKorean(alertType: String): String = when (alertType) {
        "TACHY" -> "빈맥 의심"
        "REMOVED" -> "워치 미착용 5분"
        "COMMS_LOST" -> "통신 두절"
        else -> alertType
    }

    fun severityKorean(severity: String): String = when (severity) {
        "DANGER" -> "위험"
        "WARNING" -> "주의"
        "CAUTION" -> "안내"
        else -> severity
    }

    fun severityColor(severity: String): Color = when (severity) {
        "DANGER" -> ColorDanger
        "WARNING" -> ColorWarning
        "CAUTION" -> ColorInfo
        else -> ColorIdle
    }

    // ── 배터리 ────────────────────────────────────────────────────────────
    fun batteryLevel(level: Int?): HealthLevel {
        if (level == null) return HealthLevel.IDLE
        return when {
            level <= 15 -> HealthLevel.DANGER
            level <= 30 -> HealthLevel.WARNING
            else -> HealthLevel.NORMAL
        }
    }

    fun batteryDisplay(level: Int?): String =
        if (level == null) "—%" else "$level%"

    // ── 시간 — "방금 전 / N분 전 / N시간 전" ──────────────────────────────
    fun relativeTime(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val past = OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val secs = Duration.between(past, OffsetDateTime.now(past.offset)).seconds
            when {
                secs < 0 -> "방금"
                secs < 10 -> "방금"
                secs < 60 -> "${secs}초 전"
                secs < 3600 -> "${secs / 60}분 전"
                secs < 86400 -> "${secs / 3600}시간 전"
                else -> "${secs / 86400}일 전"
            }
        } catch (e: Exception) {
            iso.takeLast(8).take(5)
        }
    }

    // ── 종합 운용 상태 (위에서 모은 결과를 한 줄 요약) ────────────────────
    /**
     * 우선순위: 활성 alert > HR DANGER > 체온 DANGER > HR WARNING > 체온 WARNING
     * > wear OFF > IDLE > 정상.
     */
    fun overallStatus(
        snapshot: DeviceWatchSnapshot?,
        wearState: String?,
        activeAlert: SafetyAlertRow?,
    ): Pair<String, Color> {
        if (activeAlert != null) {
            val sev = severityKorean(activeAlert.severity)
            val type = alertTypeKorean(activeAlert.alertType)
            return "$sev — $type" to severityColor(activeAlert.severity)
        }
        val hrLevel = classifyHr(snapshot?.heartRate, wearState)
        val tempLevel = classifyTemp(snapshot?.bodyTemp, wearState)
        val worst = listOf(hrLevel, tempLevel).maxByOrNull {
            when (it) {
                HealthLevel.DANGER -> 3
                HealthLevel.WARNING -> 2
                HealthLevel.NORMAL -> 1
                HealthLevel.IDLE -> 0
            }
        } ?: HealthLevel.IDLE
        return when (worst) {
            HealthLevel.DANGER -> "위험 — 측정값 비정상" to ColorDanger
            HealthLevel.WARNING -> "주의 — 측정값 확인" to ColorWarning
            HealthLevel.NORMAL -> "정상 운용 중" to ColorNormal
            HealthLevel.IDLE -> (wearStateLabel(wearState) to wearStateColor(wearState))
        }
    }
}
