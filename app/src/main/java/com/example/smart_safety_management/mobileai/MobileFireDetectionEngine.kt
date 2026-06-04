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
    private val interpreter = Interpreter(loadMappedModel(appContext))
    private val labels = loadLabels(appContext)

    fun detect(
        bitmap: Bitmap,
        sampledAtMs: Long = System.currentTimeMillis()
    ): MobileFireResult {
        val startedAtNs = System.nanoTime()
        val input = bitmap.toInputBuffer()
        val output = Array(1) { Array(MAX_DETECTIONS) { FloatArray(6) } }

        interpreter.run(input, output)

        val inferenceMs = (System.nanoTime() - startedAtNs) / 1_000_000L
        val parsed = MobileYoloOutputParser.parseNmsRows(
            rows = output[0],
            labels = labels,
            threshold = SCORE_THRESHOLD
        )
        return parsed.copy(
            inferenceMs = inferenceMs,
            sampledAtMs = sampledAtMs
        )
    }

    override fun close() {
        interpreter.close()
    }

    private fun Bitmap.toInputBuffer(): ByteBuffer {
        val resized = if (width == INPUT_SIZE && height == INPUT_SIZE) {
            this
        } else {
            Bitmap.createScaledBitmap(this, INPUT_SIZE, INPUT_SIZE, true)
        }
        val buffer = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNELS * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }
        buffer.rewind()
        if (resized !== this) {
            resized.recycle()
        }
        return buffer
    }

    companion object {
        const val MODEL_ASSET = "mobile_fire.tflite"
        const val LABELS_ASSET = "mobile_fire_labels.txt"
        const val INPUT_SIZE = 640
        const val MAX_DETECTIONS = 300
        const val SCORE_THRESHOLD = 0.50f

        private const val CHANNELS = 3
        private const val FLOAT_BYTES = 4

        private fun loadMappedModel(context: Context): MappedByteBuffer {
            val descriptor = context.assets.openFd(MODEL_ASSET)
            FileInputStream(descriptor.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
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
