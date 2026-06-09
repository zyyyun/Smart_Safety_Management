package com.example.smart_safety_management.mobileai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

class MobileFireDetectionEngine(context: Context) : MobileFireDetector {
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
    private val resizeBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val resizeCanvas = Canvas(resizeBitmap)
    private val resizeTarget = Rect(0, 0, INPUT_SIZE, INPUT_SIZE)
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
                Log.i(
                    TAG,
                    "model_loaded asset=$MODEL_ASSET labels=${labels.joinToString("|")} maxDetections=$maxDetections"
                )
            } catch (e: Throwable) {
                loadedInterpreter.close()
                throw e
            }
        } catch (e: Throwable) {
            loadError = e
            Log.e(TAG, "model_load_failed asset=$MODEL_ASSET error=${e.message}", e)
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
        val scoreSummary = MobileYoloOutputParser.summarizeRows(outputBuffer[0])
        Log.d(
            TAG,
            "raw_output frame=${describeFrame(bitmap)} " +
                "maxScore=${scoreSummary.maxScore.fmt3()} " +
                "maxClass=${scoreSummary.maxClassValue.fmt3()} " +
                "maxCombined=${scoreSummary.maxCombinedScore.fmt3()} " +
                "scoreCounts(0.2/0.3/0.5)=" +
                "${scoreSummary.scoreAbove02}/${scoreSummary.scoreAbove03}/${scoreSummary.scoreAbove05} " +
                "bestScoreRow=${scoreSummary.bestScoreRow.toDiagnosticRow()} " +
                "bestCombinedRow=${scoreSummary.bestCombinedRow.toDiagnosticRow()}"
        )
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

    override fun detectFrame(frame: Bitmap): MobileFireResult = detect(frame)

    @Synchronized
    override fun close() {
        interpreter?.close()
        interpreter = null
        if (!resizeBitmap.isRecycled) {
            resizeBitmap.recycle()
        }
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        val inputBitmap = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            resizeBitmap.eraseColor(Color.TRANSPARENT)
            resizeCanvas.drawBitmap(bitmap, null, resizeTarget, null)
            resizeBitmap
        }
        inputBuffer.clear()
        inputBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((pixel and 0xFF) / 255f)
        }
        inputBuffer.rewind()
    }

    private fun describeFrame(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return "size=${width}x$height"
        }

        var lumaSum = 0.0
        var darkSamples = 0
        var brightSamples = 0
        var samples = 0
        val xStep = (width / FRAME_DIAGNOSTIC_GRID).coerceAtLeast(1)
        val yStep = (height / FRAME_DIAGNOSTIC_GRID).coerceAtLeast(1)

        var y = yStep / 2
        while (y < height) {
            var x = xStep / 2
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                val luma = red * 0.2126 + green * 0.7152 + blue * 0.0722
                lumaSum += luma
                if (luma < DARK_LUMA_THRESHOLD) darkSamples += 1
                if (luma > BRIGHT_LUMA_THRESHOLD) brightSamples += 1
                samples += 1
                x += xStep
            }
            y += yStep
        }

        val averageLuma = if (samples == 0) 0.0 else lumaSum / samples
        val darkPct = if (samples == 0) 0.0 else darkSamples * 100.0 / samples
        val brightPct = if (samples == 0) 0.0 else brightSamples * 100.0 / samples
        return "size=${width}x$height config=${bitmap.config} " +
            "avgLuma=${averageLuma.fmt1()} darkPct=${darkPct.fmt1()} brightPct=${brightPct.fmt1()}"
    }

    private fun FloatArray?.toDiagnosticRow(): String {
        if (this == null || size < ROW_SIZE) return "none"
        return "[x=${this[0].fmt3()},y=${this[1].fmt3()},w=${this[2].fmt3()},h=${this[3].fmt3()}," +
            "score=${this[4].fmt3()},class=${this[5].fmt3()}]"
    }

    private fun Float.fmt3(): String = String.format(Locale.US, "%.3f", this)

    private fun Double.fmt1(): String = String.format(Locale.US, "%.1f", this)

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
        private const val TAG = "MobileFireEngine"

        const val MODEL_ASSET = "mobile_fire.tflite"
        const val LABELS_ASSET = "mobile_fire_labels.txt"
        const val INPUT_SIZE = 640
        const val MAX_DETECTIONS = 300
        const val SCORE_THRESHOLD = 0.50f

        private const val CHANNELS = 3
        private const val FLOAT_BYTES = 4
        private const val ROW_SIZE = 6
        private const val FRAME_DIAGNOSTIC_GRID = 16
        private const val DARK_LUMA_THRESHOLD = 12.0
        private const val BRIGHT_LUMA_THRESHOLD = 245.0

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
