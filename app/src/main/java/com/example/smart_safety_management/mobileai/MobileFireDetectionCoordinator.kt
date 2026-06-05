package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileFireDetectionCoordinator(
    private val cameraId: Int,
    private val sampler: RtspFrameSampler,
    private val detector: MobileFireDetector,
    private val uploader: MobileFireUploader,
    private val sampleIntervalMs: Long = 1_000L,
    private val cooldownMs: Long = 60_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val loopDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val samplerDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : AutoCloseable {
    private val _state = MutableStateFlow(MobileFireDetectionState())
    val state: StateFlow<MobileFireDetectionState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        reduce(MobileFireDetectionAction.Start(cameraId = cameraId, nowMs = nowMs()))
        job = scope.launch(loopDispatcher) {
            while (isActive) {
                runOneCycle()
                delay(sampleIntervalMs)
            }
        }
    }

    suspend fun runOneCycle() {
        var frame: Bitmap? = null
        try {
            frame = withContext(samplerDispatcher) {
                sampler.sampleFrame()
            } ?: return

            val result = detector.detectFrame(frame)
            reduce(
                MobileFireDetectionAction.InferenceResult(
                    result = result,
                    nowMs = nowMs(),
                    cooldownMs = cooldownMs
                )
            )

            val currentState = state.value
            if (currentState.canUpload && result.confidence != null) {
                val eventId = uploader.upload(cameraId, frame, result.confidence)
                reduce(MobileFireDetectionAction.UploadComplete(eventId))
            }
        } catch (e: Throwable) {
            reduce(MobileFireDetectionAction.Error(e.message ?: e.javaClass.simpleName))
        } finally {
            frame?.let { sampledFrame ->
                if (!sampledFrame.isRecycled) {
                    sampledFrame.recycle()
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        job?.cancel()
        job = null
        detector.close()
        reduce(MobileFireDetectionAction.Stop)
    }

    private fun reduce(action: MobileFireDetectionAction) {
        _state.value = MobileFireDetectionReducer.reduce(_state.value, action)
    }
}
