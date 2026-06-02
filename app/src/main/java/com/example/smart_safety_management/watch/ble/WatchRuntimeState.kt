package com.example.smart_safety_management.watch.ble

import com.example.smart_safety_management.watch.DeviceRow
import com.example.smart_safety_management.watch.DeviceWatchSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WatchRuntimeStatus {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    READING,
    UPLOADING,
    RETRYING,
    DISCONNECTED,
    FAILED,
}

data class WatchRuntimeState(
    val deviceId: Int? = null,
    val userId: String? = null,
    val macAddress: String? = null,
    val status: WatchRuntimeStatus = WatchRuntimeStatus.IDLE,
    val lastReadAt: Instant? = null,
    val lastUploadAt: Instant? = null,
    val latestReading: JcWearHealthReading? = null,
    val lastError: String? = null,
)

data class WatchRuntimeSnapshot(
    val statusLabel: String,
    val isFresh: Boolean,
    val lastCommunicationLabel: String,
    val ppgDisplay: String,
    val hrDisplay: String,
    val tempDisplay: String,
    val batteryDisplay: String,
) {
    companion object {
        fun from(
            device: DeviceRow?,
            dbSnapshot: DeviceWatchSnapshot?,
            runtime: WatchRuntimeState,
            now: Instant = Instant.now(),
        ): WatchRuntimeSnapshot {
            val latestReading = runtime.latestReading
            val isFresh = runtime.lastReadAt?.let { Duration.between(it, now).seconds in 0..10 } ?: false
            val hasMac = !runtime.macAddress.isNullOrBlank() || !device?.macAddress.isNullOrBlank()
            val lastCommunicationAt = listOfNotNull(
                runtime.lastReadAt,
                runtime.lastUploadAt,
                dbSnapshot?.updatedAt?.toInstantOrNull(),
                device?.lastCommAt?.toInstantOrNull(),
                device?.updatedAt?.toInstantOrNull(),
            ).maxOrNull()

            return WatchRuntimeSnapshot(
                statusLabel = statusLabel(runtime.status, isFresh, hasMac),
                isFresh = isFresh,
                lastCommunicationLabel = relativeLabel(lastCommunicationAt, now),
                ppgDisplay = latestReading?.ppgValue?.toString() ?: "--",
                hrDisplay = readingLabel(latestReading?.heartRate ?: dbSnapshot?.heartRate, suffix = " bpm"),
                tempDisplay = tempLabel(latestReading?.bodyTemp ?: dbSnapshot?.bodyTemp),
                batteryDisplay = batteryLabel(
                    latestReading?.batteryLevel
                        ?: dbSnapshot?.batteryLevel
                        ?: device?.batteryLevel,
                ),
            )
        }

        private fun statusLabel(status: WatchRuntimeStatus, isFresh: Boolean, hasMac: Boolean): String {
            if (!hasMac) return "미등록"
            return when (status) {
                WatchRuntimeStatus.READING -> if (isFresh) "수신 중" else "데이터 대기"
                WatchRuntimeStatus.CONNECTING -> "연결 중"
                WatchRuntimeStatus.CONNECTED -> "연결됨"
                WatchRuntimeStatus.UPLOADING -> if (isFresh) "업로드 중" else "연결됨"
                WatchRuntimeStatus.RETRYING -> "재연결 중"
                WatchRuntimeStatus.FAILED -> "연결 실패"
                WatchRuntimeStatus.DISCONNECTED -> "끊김"
                WatchRuntimeStatus.SCANNING -> "검색 중"
                WatchRuntimeStatus.IDLE -> "데이터 대기"
            }
        }

        private fun relativeLabel(instant: Instant?, now: Instant): String {
            if (instant == null) return "-"
            val seconds = Duration.between(instant, now).seconds.coerceAtLeast(0)
            return when {
                seconds < 60 -> "${seconds}초 전"
                seconds < 3600 -> "${seconds / 60}분 전"
                seconds < 86400 -> "${seconds / 3600}시간 전"
                else -> "${seconds / 86400}일 전"
            }
        }

        private fun readingLabel(value: Int?, suffix: String): String =
            value?.let { "$it$suffix" } ?: "측정 대기"

        private fun tempLabel(value: Float?): String =
            value?.let { String.format("%.1f°C", it) } ?: "측정 대기"

        private fun batteryLabel(value: Int?): String =
            value?.let { "$it%" } ?: "--"

        private fun String.toInstantOrNull(): Instant? {
            val trimmed = trim().takeIf { it.isNotEmpty() } ?: return null
            return runCatching { Instant.parse(trimmed) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC) }.getOrNull()
        }
    }
}

object WatchRuntimeStore {
    private val mutableState = MutableStateFlow(WatchRuntimeState())
    val state: StateFlow<WatchRuntimeState> = mutableState.asStateFlow()

    fun update(next: WatchRuntimeState) {
        mutableState.value = next
    }

    fun mutate(block: (WatchRuntimeState) -> WatchRuntimeState) {
        mutableState.value = block(mutableState.value)
    }

    fun clear(deviceId: Int? = null) {
        if (deviceId == null || mutableState.value.deviceId == deviceId) {
            mutableState.value = WatchRuntimeState()
        }
    }
}
