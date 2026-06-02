package com.example.smart_safety_management.watch.ble

import com.example.smart_safety_management.watch.DeviceRow
import com.example.smart_safety_management.watch.DeviceWatchSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val monitoringSessionId: Long? = null,
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
    val heartRate: Int?,
    val bodyTemp: Float?,
    val batteryDisplay: String,
    val batteryLevel: Int?,
) {
    companion object {
        fun from(
            device: DeviceRow?,
            dbSnapshot: DeviceWatchSnapshot?,
            runtime: WatchRuntimeState,
            now: Instant = Instant.now(),
        ): WatchRuntimeSnapshot {
            val runtimeApplies = runtimeAppliesToTarget(runtime, device, dbSnapshot)
            val effectiveRuntime = runtime.takeIf { runtimeApplies } ?: WatchRuntimeState()
            val isFresh = effectiveRuntime.lastReadAt?.let { Duration.between(it, now).seconds in 0..10 } ?: false
            val freshReading = effectiveRuntime.latestReading.takeIf { isFresh }
            val hasMac = !effectiveRuntime.macAddress.isNullOrBlank() || !device?.macAddress.isNullOrBlank()
            val lastCommunicationAt = listOfNotNull(
                effectiveRuntime.lastReadAt,
                effectiveRuntime.lastUploadAt,
                dbSnapshot?.updatedAt?.toInstantOrNull(),
                device?.lastCommAt?.toInstantOrNull(),
                device?.updatedAt?.toInstantOrNull(),
            ).maxOrNull()
            val hrValue = if (freshReading != null) freshReading.heartRate else dbSnapshot?.heartRate
            val tempValue = if (freshReading != null) freshReading.bodyTemp else dbSnapshot?.bodyTemp
            val batteryLevel = freshReading?.batteryLevel
                ?: dbSnapshot?.batteryLevel
                ?: device?.batteryLevel

            return WatchRuntimeSnapshot(
                statusLabel = statusLabel(effectiveRuntime.status, isFresh, hasMac),
                isFresh = isFresh,
                lastCommunicationLabel = relativeLabel(lastCommunicationAt, now),
                ppgDisplay = freshReading?.ppgValue?.toString() ?: "--",
                hrDisplay = readingLabel(hrValue, suffix = " bpm"),
                tempDisplay = tempLabel(tempValue),
                heartRate = hrValue,
                bodyTemp = tempValue,
                batteryDisplay = batteryLabel(batteryLevel),
                batteryLevel = batteryLevel,
            )
        }

        private fun runtimeAppliesToTarget(
            runtime: WatchRuntimeState,
            device: DeviceRow?,
            dbSnapshot: DeviceWatchSnapshot?,
        ): Boolean {
            val targetUserId = device?.userId.normalizedNonBlank()
            val runtimeUserId = runtime.userId.normalizedNonBlank()
            if (targetUserId != null && runtimeUserId != null && targetUserId != runtimeUserId) {
                return false
            }

            val targetDeviceId = device?.deviceId ?: dbSnapshot?.deviceId
            val runtimeDeviceId = runtime.deviceId
            if (targetDeviceId != null && runtimeDeviceId != null) {
                return targetDeviceId == runtimeDeviceId
            }

            val deviceMac = device?.macAddress.normalizedNonBlank()
            val runtimeMac = runtime.macAddress.normalizedNonBlank()
            if (deviceMac != null && runtimeMac != null) {
                return deviceMac.equals(runtimeMac, ignoreCase = true)
            }

            return targetDeviceId == null && deviceMac == null && runtimeDeviceId == null && runtimeMac == null
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

internal fun WatchRuntimeState.seedForRegisteredWatch(
    userId: String,
    device: DeviceRow,
): WatchRuntimeState {
    val base = takeIf { it.belongsToRegisteredWatchTarget(userId, device) } ?: WatchRuntimeState()
    return base.copy(
        deviceId = device.deviceId,
        userId = userId,
        macAddress = device.macAddress,
        status = WatchRuntimeStatus.CONNECTING,
    )
}

private fun WatchRuntimeState.belongsToRegisteredWatchTarget(
    userId: String,
    device: DeviceRow,
): Boolean {
    val runtimeUserId = this.userId.normalizedNonBlank()
    val targetUserId = userId.normalizedNonBlank()
    if (runtimeUserId != null && targetUserId != null && runtimeUserId != targetUserId) {
        return false
    }

    val sameDeviceId = deviceId == device.deviceId
    val targetMac = device.macAddress.normalizedNonBlank()
    val runtimeMac = macAddress.normalizedNonBlank()
    val sameMac = targetMac != null && runtimeMac?.equals(targetMac, ignoreCase = true) == true

    return sameDeviceId || sameMac
}

private fun String?.normalizedNonBlank(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

object WatchRuntimeStore {
    private val monitoringSessionCounter = AtomicLong()
    private val mutableState = MutableStateFlow(WatchRuntimeState())
    val state: StateFlow<WatchRuntimeState> = mutableState.asStateFlow()

    fun nextMonitoringSessionId(): Long = monitoringSessionCounter.incrementAndGet()

    fun update(next: WatchRuntimeState) {
        mutableState.value = next
    }

    fun mutate(block: (WatchRuntimeState) -> WatchRuntimeState) {
        mutableState.update(block)
    }

    fun clear(deviceId: Int? = null) {
        mutableState.update { current ->
            if (deviceId == null || current.deviceId == deviceId) {
                WatchRuntimeState()
            } else {
                current
            }
        }
    }

    fun clearMonitoringSession(deviceId: Int, monitoringSessionId: Long) {
        mutableState.update { current ->
            if (current.deviceId == deviceId && current.monitoringSessionId == monitoringSessionId) {
                WatchRuntimeState()
            } else {
                current
            }
        }
    }
}
