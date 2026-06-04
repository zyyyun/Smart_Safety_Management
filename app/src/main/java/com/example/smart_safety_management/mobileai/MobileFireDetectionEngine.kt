package com.example.smart_safety_management.mobileai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MobileFireDetectionEngine(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null
    private var loadError: Throwable? = null
    private var unavailableMessage: String = "mobile fire model asset unavailable: $MODEL_ASSET"
    private var labels: List<String> = emptyList()
    private var maxDetections: Int = MAX_DETECTIONS
    private val inputBuffer = ByteBuffer
        .allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNELS * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
    private var outputBuffer: Array<Array<FloatArray>> = Array(1) {
        Array(MAX_DETECTIONS) { FloatArray(ROW_SIZE) }
    }

    init {
        try {
            val loadedInterpreter = Interpreter(loadMappedModel(appContext))
            try {
                validateOutputTensor(loadedInterpreter)
                labels = loadLabels(appContext)
                interpreter = loadedInterpreter
            } catch (e: Throwable) {
                loadedInterpreter.close()
                throw e
            }
        } catch (e: Throwable) {
            loadError = e
        }
    }

    @Synchronized
    fun detect(
        bitmap: Bitmap,
        sampledAtMs: Long = System.currentTimeMillis()
    ): MobileFireResult {
        val loadedInterpreter = interpreter ?: throw IllegalStateException(unavailableMessage, loadError)
        val startedAtNs = System.nanoTime()
        fillInputBuffer(bitmap)

        loadedInterpreter.run(inputBuffer, outputBuffer)

        val inferenceMs = (System.nanoTime() - startedAtNs) / 1_000_000L
        val parsed = MobileYoloOutputParser.parseNmsRows(
            rows = outputBuffer[0],
            labels = labels,
            threshold = SCORE_THRESHOLD
        )
        return parsed.copy(
            inferenceMs = inferenceMs,
            sampledAtMs = sampledAtMs
        )
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        val resized = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }
        inputBuffer.clear()
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((pixel and 0xFF) / 255f)
        }
        inputBuffer.rewind()
        if (resized !== bitmap) {
            resized.recycle()
        }
    }

    private fun validateOutputTensor(loadedInterpreter: Interpreter) {
        if (loadedInterpreter.outputTensorCount != 1) {
            markInvalidContract("expected 1 output tensor, got ${loadedInterpreter.outputTensorCount}")
        }
        val shape = loadedInterpreter.getOutputTensor(0).shape()
        if (shape.size != 3 || shape[0] != 1 || shape[1] <= 0 || shape[2] != ROW_SIZE) {
            markInvalidContract("expected output shape [1, max_detections, 6], got ${shape.contentToString()}")
        }
        maxDetections = shape[1]
        outputBuffer = Array(1) { Array(maxDetections) { FloatArray(ROW_SIZE) } }
    }

    private fun markInvalidContract(detail: String): Nothing {
        unavailableMessage = "mobile fire model contract invalid: $detail"
        throw IllegalStateException(unavailableMessage)
    }

    companion object {
        const val MODEL_ASSET = "mobile_fire.tflite"
        const val LABELS_ASSET = "mobile_fire_labels.txt"
        const val INPUT_SIZE = 640
        const val MAX_DETECTIONS = 300
        const val SCORE_THRESHOLD = 0.50f

        private const val CHANNELS = 3
        private const val FLOAT_BYTES = 4
        private const val ROW_SIZE = 6

        private fun loadMappedModel(context: Context): MappedByteBuffer {
            context.assets.openFd(MODEL_ASSET).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    return input.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength
                    )
                }
            }
        }

        private fun loadLabels(context: Context): List<String> {
            return context.assets.open(LABELS_ASSET).bufferedReader().useLines { lines ->
                lines.map { label -> label.trim() }
                    .filter { label -> label.isNotEmpty() }
                    .toList()
            }
        }
    }
}
