package com.example.smart_safety_management.mobileai

data class MobileFireBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val classId: Int,
    val label: String
)

data class MobileFireResult(
    val detected: Boolean,
    val confidence: Float? = null,
    val box: MobileFireBox? = null,
    val inferenceMs: Long = 0L,
    val sampledAtMs: Long = 0L
)

enum class MobileFireDetectionStatus {
    OFF,
    WARMING_UP,
    RUNNING,
    DETECTED,
    COOLDOWN,
    ERROR
}

data class MobileFireDetectionState(
    val cameraId: Int = 0,
    val status: MobileFireDetectionStatus = MobileFireDetectionStatus.OFF,
    val lastResult: MobileFireResult? = null,
    val lastUploadEventId: Int? = null,
    val cooldownUntilMs: Long = 0L,
    val canUpload: Boolean = false,
    val message: String? = null
)

sealed interface MobileFireDetectionAction {
    data class Start(val cameraId: Int, val nowMs: Long) : MobileFireDetectionAction
    data object Stop : MobileFireDetectionAction
    data class InferenceResult(
        val result: MobileFireResult,
        val nowMs: Long,
        val cooldownMs: Long
    ) : MobileFireDetectionAction
    data object FrameUnavailable : MobileFireDetectionAction
    data class UploadComplete(val eventId: Int?) : MobileFireDetectionAction
    data class Error(val message: String) : MobileFireDetectionAction
}
