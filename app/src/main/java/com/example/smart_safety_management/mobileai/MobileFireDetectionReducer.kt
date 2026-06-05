package com.example.smart_safety_management.mobileai

object MobileFireDetectionReducer {
    fun reduce(
        state: MobileFireDetectionState,
        action: MobileFireDetectionAction
    ): MobileFireDetectionState {
        return when (action) {
            is MobileFireDetectionAction.Start -> state.copy(
                cameraId = action.cameraId,
                status = MobileFireDetectionStatus.RUNNING,
                canUpload = false,
                message = null
            )
            MobileFireDetectionAction.Stop -> MobileFireDetectionState()
            is MobileFireDetectionAction.InferenceResult -> reduceInferenceResult(state, action)
            is MobileFireDetectionAction.UploadComplete -> state.copy(
                lastUploadEventId = action.eventId,
                canUpload = false,
                message = "uploaded"
            )
            is MobileFireDetectionAction.Error -> state.copy(
                status = MobileFireDetectionStatus.ERROR,
                canUpload = false,
                cooldownUntilMs = 0L,
                message = action.message
            )
        }
    }

    private fun reduceInferenceResult(
        state: MobileFireDetectionState,
        action: MobileFireDetectionAction.InferenceResult
    ): MobileFireDetectionState {
        return if (!action.result.detected) {
            state.copy(
                status = MobileFireDetectionStatus.RUNNING,
                lastResult = action.result,
                canUpload = false,
                message = null
            )
        } else if (action.nowMs < state.cooldownUntilMs) {
            state.copy(
                status = MobileFireDetectionStatus.COOLDOWN,
                lastResult = action.result,
                canUpload = false,
                message = "cooldown"
            )
        } else {
            state.copy(
                status = MobileFireDetectionStatus.DETECTED,
                lastResult = action.result,
                cooldownUntilMs = action.nowMs + action.cooldownMs,
                canUpload = true,
                message = null
            )
        }
    }
}
