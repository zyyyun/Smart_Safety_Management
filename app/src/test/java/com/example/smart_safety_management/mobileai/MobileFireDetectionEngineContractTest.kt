package com.example.smart_safety_management.mobileai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

class MobileFireDetectionEngineContractTest {
    @Test
    fun constantsMatchExportContract() {
        assertEquals("mobile_fire.tflite", MobileFireDetectionEngine.MODEL_ASSET)
        assertEquals(640, MobileFireDetectionEngine.INPUT_SIZE)
        assertEquals(0.50f, MobileFireDetectionEngine.SCORE_THRESHOLD, 0.001f)
    }

    @Test
    fun missingModelAssetIsReportedFromDetectInsteadOfConstructor() {
        val source = engineSource()

        assertTrue(source.contains("private var interpreter: Interpreter?"))
        assertTrue(source.contains("private var loadError: Throwable?"))
        assertTrue(source.contains("mobile fire model asset unavailable: "))
        assertTrue(source.contains("\$MODEL_ASSET"))
        assertTrue(source.contains("IllegalStateException(unavailableMessage, loadError)"))
    }

    @Test
    fun validatesOutputTensorShapeBeforeInferenceBuffersAreUsed() {
        val source = engineSource()

        assertTrue(source.contains("outputTensorCount"))
        assertTrue(source.contains("getOutputTensor(0).shape()"))
        assertTrue(source.contains("shape.size != 3"))
        assertTrue(source.contains("shape[2] != ROW_SIZE"))
        assertTrue(source.contains("maxDetections = shape[1]"))
    }

    @Test
    fun reusesInferenceBuffersAndClosesAssetDescriptor() {
        val source = engineSource()

        assertTrue(source.contains("@Synchronized"))
        assertTrue(source.contains("private val inputBuffer"))
        assertTrue(source.contains("private val pixels"))
        assertTrue(source.contains("private var outputBuffer"))
        assertTrue(source.contains("context.assets.openFd(MODEL_ASSET).use"))
    }

    private fun engineSource(): String {
        val bytes = Files.readAllBytes(
            Paths.get("src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionEngine.kt")
        )
        return String(bytes, StandardCharsets.UTF_8)
    }

    companion object {
        private const val MODEL_ASSET = "mobile_fire.tflite"
        private const val ROW_SIZE = 6
    }
}
