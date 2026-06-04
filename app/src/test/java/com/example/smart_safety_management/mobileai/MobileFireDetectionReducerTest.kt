package com.example.smart_safety_management.mobileai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileFireDetectionReducerTest {
    @Test
    fun runningAfterStart() {
        val state = MobileFireDetectionReducer.reduce(
            MobileFireDetectionState(),
            MobileFireDetectionAction.Start(cameraId = 7, nowMs = 1000L)
        )

        assertEquals(7, state.cameraId)
        assertEquals(MobileFireDetectionStatus.RUNNING, state.status)
    }

    @Test
    fun detectionAboveThresholdEntersCooldown() {
        val state = MobileFireDetectionReducer.reduce(
            MobileFireDetectionState(cameraId = 7, status = MobileFireDetectionStatus.RUNNING),
            MobileFireDetectionAction.InferenceResult(
                result = MobileFireResult(
                    detected = true,
                    confidence = 0.91f,
                    inferenceMs = 44L,
                    sampledAtMs = 2_000L
                ),
                nowMs = 2_000L,
                cooldownMs = 60_000L
            )
        )

        assertEquals(MobileFireDetectionStatus.DETECTED, state.status)
        assertEquals(62_000L, state.cooldownUntilMs)
        assertTrue(state.canUpload)
    }

    @Test
    fun detectionInsideCooldownDoesNotUpload() {
        val state = MobileFireDetectionReducer.reduce(
            MobileFireDetectionState(
                cameraId = 7,
                status = MobileFireDetectionStatus.RUNNING,
                cooldownUntilMs = 10_000L
            ),
            MobileFireDetectionAction.InferenceResult(
                result = MobileFireResult(
                    detected = true,
                    confidence = 0.88f,
                    inferenceMs = 40L,
                    sampledAtMs = 5_000L
                ),
                nowMs = 5_000L,
                cooldownMs = 60_000L
            )
        )

        assertEquals(MobileFireDetectionStatus.COOLDOWN, state.status)
        assertFalse(state.canUpload)
    }

    @Test
    fun errorStateKeepsMessage() {
        val state = MobileFireDetectionReducer.reduce(
            MobileFireDetectionState(cameraId = 3),
            MobileFireDetectionAction.Error("model load failed")
        )

        assertEquals(MobileFireDetectionStatus.ERROR, state.status)
        assertEquals("model load failed", state.message)
    }
}
