package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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
        Log.i(TAG, "loop_start cameraId=$cameraId sampleIntervalMs=$sampleIntervalMs cooldownMs=$cooldownMs")
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
            frame = sampleFrameOrMarkUnavailable() ?: return

            val result = detector.detectFrame(frame)
            Log.d(
                TAG,
                "inference cameraId=$cameraId detected=${result.detected} confidence=${result.confidence} inferenceMs=${result.inferenceMs}"
            )
            reduce(
                MobileFireDetectionAction.InferenceResult(
                    result = result,
                    nowMs = nowMs(),
                    cooldownMs = cooldownMs
                )
            )

            val currentState = state.value
            if (currentState.canUpload && result.confidence != null) {
                try {
                    Log.i(
                        TAG,
                        "upload_start cameraId=$cameraId confidence=${result.confidence} cooldownUntilMs=${currentState.cooldownUntilMs}"
                    )
                    val eventId = uploader.upload(cameraId, frame, result.confidence)
                    Log.i(TAG, "upload_success cameraId=$cameraId eventId=$eventId")
                    reduce(MobileFireDetectionAction.UploadComplete(eventId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "upload_failed cameraId=$cameraId error=${e.message}", e)
                    reduce(MobileFireDetectionAction.Error(e.message ?: e.javaClass.simpleName))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "loop_error cameraId=$cameraId error=${e.message}", e)
            reduce(MobileFireDetectionAction.Error(e.message ?: e.javaClass.simpleName))
        } finally {
            // RtspFrameSampler returns a caller-owned frame for this cycle.
            frame?.let { sampledFrame ->
                if (!sampledFrame.isRecycled) {
                    sampledFrame.recycle()
                }
            }
        }
    }

    private suspend fun sampleFrameOrMarkUnavailable(): Bitmap? {
        return try {
            withContext(samplerDispatcher) {
                sampler.sampleFrame()
            }.also { frame ->
                if (frame == null) {
                    Log.d(TAG, "frame_unavailable cameraId=$cameraId reason=null_frame")
                    reduce(MobileFireDetectionAction.FrameUnavailable)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            if (e.message?.contains("Unable to draw content from GPU", ignoreCase = true) == true) {
                Log.d(TAG, "frame_unavailable cameraId=$cameraId reason=gpu_bitmap_unavailable")
                reduce(MobileFireDetectionAction.FrameUnavailable)
                null
            } else {
                throw e
            }
        }
    }

    @Synchronized
    override fun close() {
        job?.cancel()
        job = null
        detector.close()
        Log.i(TAG, "loop_stop cameraId=$cameraId")
        reduce(MobileFireDetectionAction.Stop)
    }

    private fun reduce(action: MobileFireDetectionAction) {
        _state.value = MobileFireDetectionReducer.reduce(_state.value, action)
    }

    companion object {
        private const val TAG = "MobileFireLoop"
    }
}
