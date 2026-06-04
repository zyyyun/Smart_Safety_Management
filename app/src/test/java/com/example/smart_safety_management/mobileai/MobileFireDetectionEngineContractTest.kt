package com.example.smart_safety_management.mobileai

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileFireDetectionEngineContractTest {
    @Test
    fun constantsMatchExportContract() {
        assertEquals("mobile_fire.tflite", MobileFireDetectionEngine.MODEL_ASSET)
        assertEquals(640, MobileFireDetectionEngine.INPUT_SIZE)
        assertEquals(0.50f, MobileFireDetectionEngine.SCORE_THRESHOLD, 0.001f)
    }
}
