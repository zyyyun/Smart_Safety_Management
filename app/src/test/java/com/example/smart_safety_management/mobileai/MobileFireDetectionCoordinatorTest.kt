package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MobileFireDetectionCoordinatorTest {
    @Test
    fun detectedSampleUploadsOnceAndStoresEventId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val frame = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val sampler = FakeSampler(frame)
        val detector = FakeDetector(MobileFireResult(detected = true, confidence = 0.82f))
        val uploader = FakeUploader(eventId = 123)
        val coordinator = MobileFireDetectionCoordinator(
            cameraId = 5,
            sampler = sampler,
            detector = detector,
            uploader = uploader,
            sampleIntervalMs = 1_000L,
            cooldownMs = 10_000L,
            nowMs = { 1_000L },
            loopDispatcher = dispatcher,
            samplerDispatcher = dispatcher
        )

        coordinator.start(this)
        runCurrent()

        assertEquals(1, sampler.sampleCount)
        assertEquals(1, detector.detectCount)
        assertEquals(listOf(Upload(cameraId = 5, confidence = 0.82f)), uploader.uploads)
        assertEquals(123, coordinator.state.value.lastUploadEventId)
        assertTrue(frame.isRecycled)

        coordinator.close()
    }

    private class FakeSampler(
        private val frame: Bitmap
    ) : RtspFrameSampler {
        var sampleCount = 0

        override fun sampleFrame(width: Int, height: Int): Bitmap? {
            sampleCount += 1
            return frame
        }
    }

    private class FakeDetector(
        private val result: MobileFireResult
    ) : MobileFireDetector {
        var detectCount = 0

        override fun detectFrame(frame: Bitmap): MobileFireResult {
            detectCount += 1
            return result
        }

        override fun close() = Unit
    }

    private class FakeUploader(
        private val eventId: Int
    ) : MobileFireUploader {
        val uploads = mutableListOf<Upload>()

        override suspend fun upload(cameraId: Int, frame: Bitmap, confidence: Float): Int? {
            uploads += Upload(cameraId = cameraId, confidence = confidence)
            return eventId
        }
    }

    private data class Upload(
        val cameraId: Int,
        val confidence: Float
    )
}
