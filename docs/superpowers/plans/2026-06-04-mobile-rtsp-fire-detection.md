# Mobile RTSP Fire Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first Android on-device fire-detection PoC that samples a Drift RTSP stream on the real-time detail screen, runs a TFLite fire model locally, and creates the same Supabase AI event/capture records the current PC worker creates.

**Architecture:** Keep the PC `ai_agent` path unchanged and add a screen-scoped Android path. Android owns RTSP playback, frame sampling, TFLite inference, local cooldown, and calls a new Supabase Edge Function that uploads the detected JPEG and delegates event creation to the existing shared `createAiEvent` logic.

**Tech Stack:** Kotlin, Jetpack Compose, LibVLC `TextureView`, TensorFlow Lite Interpreter, Supabase Edge Functions, existing `camera-captures` Storage bucket, existing `detection_events` UI contract.

---

## Scope Guard

This plan implements only the approved PoC:

- Fire detection only.
- RTSP real-time/detail screen only.
- Foreground screen execution only.
- Existing PC `ai_agent` remains the stable fallback.
- No background service, no five-detector migration, no fall detection migration.

## File Structure

- Create `scripts/export_mobile_fire_tflite.py`
  - Exports the existing fire `.pt` model to `app/src/main/assets/mobile_fire.tflite`.
  - Writes a small model contract JSON beside the model.
- Create `app/src/main/assets/mobile_fire_labels.txt`
  - Labels used by the Android detector.
- Modify `app/build.gradle.kts`
  - Adds TensorFlow Lite dependency.
  - Prevents `.tflite` compression.
- Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireModels.kt`
  - Pure model/result/state types.
- Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionReducer.kt`
  - Pure reducer for UI state and cooldown decisions.
- Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileYoloOutputParser.kt`
  - Pure parser for the exported TFLite NMS output.
- Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionEngine.kt`
  - TensorFlow Lite model loading and inference wrapper.
- Create `app/src/main/java/com/example/smart_safety_management/mobileai/RtspFrameSampler.kt`
  - Tiny interface over `TextureView.getBitmap(...)`.
- Create `app/src/main/java/com/example/smart_safety_management/mobileai/RtspTexturePlayer.kt`
  - LibVLC `TextureView` player for RTSP streams.
- Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionCoordinator.kt`
  - Coroutine loop: sample, infer, cooldown, upload, expose state.
- Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireEventRepository.kt`
  - Retrofit-backed call to the mobile Edge Function.
- Modify `app/src/main/java/com/example/smart_safety_management/RetrofitClient.kt`
  - Routes `/create_mobile_fire_event` to `mobile-ai-event`.
- Modify `app/src/main/java/com/example/smart_safety_management/SignUpService.kt`
  - Adds request/response DTOs and API method.
- Modify `app/src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt`
  - Uses the new RTSP player/detector only for RTSP URLs.
  - Leaves MP4/HLS playback behavior unchanged.
- Create `supabase/functions/mobile-ai-event/index.ts`
  - Request handler and deployment entrypoint.
- Create `supabase/functions/mobile-ai-event/helpers.ts`
  - Pure helper functions that can be imported by Deno tests without starting `Deno.serve`.
- Create `supabase/functions/mobile-ai-event/index.test.ts`
  - Deno tests for pure helper behavior.
  - Authenticated mobile endpoint, service-role upload, shared AI event creation.
- Create tests under:
  - `app/src/test/java/com/example/smart_safety_management/mobileai/`
  - `app/src/test/java/com/example/smart_safety_management/screens/detail/`
  - `app/src/test/java/com/example/smart_safety_management/MobileFireApiContractTest.kt`
  - `supabase/functions/mobile-ai-event/index.test.ts`

---

### Task 1: Export Mobile Fire Model Contract

**Files:**
- Create: `scripts/export_mobile_fire_tflite.py`
- Create: `app/src/main/assets/mobile_fire_labels.txt`
- Create when script runs: `app/src/main/assets/mobile_fire.tflite`
- Create when script runs: `app/src/main/assets/mobile_fire_model_contract.json`
- Test: `ai_agent/tests/test_mobile_fire_export_script.py`

- [ ] **Step 1: Write the failing Python test**

Create `ai_agent/tests/test_mobile_fire_export_script.py`:

```python
from pathlib import Path


def test_export_script_points_to_android_assets():
    text = Path("scripts/export_mobile_fire_tflite.py").read_text(encoding="utf-8")
    assert 'app/src/main/assets/mobile_fire.tflite' in text
    assert 'mobile_fire_model_contract.json' in text
    assert 'nms=True' in text
    assert 'imgsz=640' in text


def test_labels_file_contains_fire_and_smoke():
    labels = Path("app/src/main/assets/mobile_fire_labels.txt").read_text(encoding="utf-8").splitlines()
    assert labels == ["fire", "smoke"]
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
python -m pytest ai_agent/tests/test_mobile_fire_export_script.py -q
```

Expected: FAIL because the script and labels file do not exist.

- [ ] **Step 3: Create the labels file**

Create `app/src/main/assets/mobile_fire_labels.txt`:

```text
fire
smoke
```

- [ ] **Step 4: Create the export script**

Create `scripts/export_mobile_fire_tflite.py`:

```python
from __future__ import annotations

import json
from pathlib import Path

from ultralytics import YOLO

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WEIGHTS = Path(
    r"D:\2025_산업안전\산업안전\모델 7종\화재 탐지\yolov26_fire_best.pt"
)
ASSETS_DIR = REPO_ROOT / "app/src/main/assets"
OUTPUT_TFLITE = ASSETS_DIR / "mobile_fire.tflite"
OUTPUT_CONTRACT = ASSETS_DIR / "mobile_fire_model_contract.json"


def export_fire_model(weights: Path = DEFAULT_WEIGHTS) -> Path:
    if not weights.exists():
        raise FileNotFoundError(f"fire model weights not found: {weights}")
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    model = YOLO(str(weights))
    exported = Path(
        model.export(
            format="tflite",
            imgsz=640,
            nms=True,
            int8=False,
            half=False,
            batch=1,
            device="cpu",
        )
    )
    OUTPUT_TFLITE.write_bytes(exported.read_bytes())
    OUTPUT_CONTRACT.write_text(
        json.dumps(
            {
                "model": "mobile_fire.tflite",
                "labels": ["fire", "smoke"],
                "input_width": 640,
                "input_height": 640,
                "input_channels": 3,
                "input_dtype": "float32",
                "input_normalization": "0_1",
                "output": "ultralytics_nms",
                "output_shape": "[1, max_detections, 6]",
                "box_format": "xywh_normalized",
                "row_format": ["x_center", "y_center", "width", "height", "score", "class_id"],
                "score_threshold": 0.50,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    return OUTPUT_TFLITE


if __name__ == "__main__":
    path = export_fire_model()
    print(f"exported {path}")
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```powershell
python -m pytest ai_agent/tests/test_mobile_fire_export_script.py -q
```

Expected: PASS.

- [ ] **Step 6: Export the model manually**

Run:

```powershell
python scripts/export_mobile_fire_tflite.py
```

Expected:

```text
exported D:\2026_산업안전\Smart_Safety_Management\app\src\main\assets\mobile_fire.tflite
```

If this command fails because Python dependencies are missing, install them in the existing project Python environment and rerun. Do not change Android code until the `.tflite` and contract JSON exist.

- [ ] **Step 7: Commit**

```powershell
git add scripts/export_mobile_fire_tflite.py app/src/main/assets/mobile_fire_labels.txt app/src/main/assets/mobile_fire.tflite app/src/main/assets/mobile_fire_model_contract.json ai_agent/tests/test_mobile_fire_export_script.py
git commit -m "feat(ai): add mobile fire TFLite export"
```

---

### Task 2: Add Android Model Types and Cooldown Reducer

**Files:**
- Create: `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireModels.kt`
- Create: `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionReducer.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireDetectionReducerTest.kt`

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireDetectionReducerTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileFireDetectionReducerTest" --no-daemon
```

Expected: FAIL because model/reducer classes do not exist.

- [ ] **Step 3: Create model types**

Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireModels.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

data class MobileFireBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val classId: Int,
    val label: String
)

data class MobileFireResult(
    val detected: Boolean,
    val confidence: Float? = null,
    val box: MobileFireBox? = null,
    val inferenceMs: Long = 0L,
    val sampledAtMs: Long = 0L
)

enum class MobileFireDetectionStatus {
    OFF,
    WARMING_UP,
    RUNNING,
    DETECTED,
    COOLDOWN,
    ERROR
}

data class MobileFireDetectionState(
    val cameraId: Int = 0,
    val status: MobileFireDetectionStatus = MobileFireDetectionStatus.OFF,
    val lastResult: MobileFireResult? = null,
    val lastUploadEventId: Int? = null,
    val cooldownUntilMs: Long = 0L,
    val canUpload: Boolean = false,
    val message: String? = null
)

sealed interface MobileFireDetectionAction {
    data class Start(val cameraId: Int, val nowMs: Long) : MobileFireDetectionAction
    data object Stop : MobileFireDetectionAction
    data class InferenceResult(
        val result: MobileFireResult,
        val nowMs: Long,
        val cooldownMs: Long
    ) : MobileFireDetectionAction
    data class UploadComplete(val eventId: Int?) : MobileFireDetectionAction
    data class Error(val message: String) : MobileFireDetectionAction
}
```

- [ ] **Step 4: Create reducer**

Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionReducer.kt`:

```kotlin
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
            is MobileFireDetectionAction.InferenceResult -> {
                if (!action.result.detected) {
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
            is MobileFireDetectionAction.UploadComplete -> state.copy(
                lastUploadEventId = action.eventId,
                canUpload = false,
                message = "uploaded"
            )
            is MobileFireDetectionAction.Error -> state.copy(
                status = MobileFireDetectionStatus.ERROR,
                canUpload = false,
                message = action.message
            )
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileFireDetectionReducerTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireModels.kt app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionReducer.kt app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireDetectionReducerTest.kt
git commit -m "feat(mobile-ai): add fire detection state reducer"
```

---

### Task 3: Add TFLite Output Parser and Runtime Wiring

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/smart_safety_management/mobileai/MobileYoloOutputParser.kt`
- Create: `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionEngine.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/mobileai/MobileYoloOutputParserTest.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireDetectionEngineContractTest.kt`

- [ ] **Step 1: Write parser tests**

Create `app/src/test/java/com/example/smart_safety_management/mobileai/MobileYoloOutputParserTest.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileYoloOutputParserTest {
    @Test
    fun parsesHighestFireDetectionAboveThreshold() {
        val rows = arrayOf(
            floatArrayOf(0.5f, 0.5f, 0.2f, 0.4f, 0.91f, 0f),
            floatArrayOf(0.4f, 0.4f, 0.2f, 0.2f, 0.60f, 1f)
        )
        val result = MobileYoloOutputParser.parseNmsRows(
            rows = rows,
            labels = listOf("fire", "smoke"),
            threshold = 0.50f
        )
        assertTrue(result.detected)
        assertEquals(0.91f, result.confidence!!, 0.001f)
        assertEquals("fire", result.box!!.label)
        assertEquals(0.4f, result.box!!.left, 0.001f)
        assertEquals(0.3f, result.box!!.top, 0.001f)
        assertEquals(0.6f, result.box!!.right, 0.001f)
        assertEquals(0.7f, result.box!!.bottom, 0.001f)
    }

    @Test
    fun ignoresRowsBelowThreshold() {
        val rows = arrayOf(floatArrayOf(0.5f, 0.5f, 0.2f, 0.4f, 0.49f, 0f))
        val result = MobileYoloOutputParser.parseNmsRows(
            rows = rows,
            labels = listOf("fire", "smoke"),
            threshold = 0.50f
        )
        assertFalse(result.detected)
    }

    @Test
    fun clampsBoxCoordinatesToUnitRange() {
        val rows = arrayOf(floatArrayOf(0.0f, 1.0f, 0.6f, 0.8f, 0.80f, 0f))
        val result = MobileYoloOutputParser.parseNmsRows(
            rows = rows,
            labels = listOf("fire", "smoke"),
            threshold = 0.50f
        )
        assertEquals(0.0f, result.box!!.left, 0.001f)
        assertEquals(0.6f, result.box!!.top, 0.001f)
        assertEquals(0.3f, result.box!!.right, 0.001f)
        assertEquals(1.0f, result.box!!.bottom, 0.001f)
    }
}
```

- [ ] **Step 2: Write engine contract test**

Create `app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireDetectionEngineContractTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileYoloOutputParserTest" --tests "*MobileFireDetectionEngineContractTest" --no-daemon
```

Expected: FAIL because parser/engine do not exist.

- [ ] **Step 4: Add TFLite dependency and no-compress rule**

Modify `app/build.gradle.kts`:

```kotlin
android {
    androidResources {
        noCompress += listOf("tflite")
    }
}

dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
}
```

Keep the new `androidResources` block inside the existing `android { ... }` block and the dependency inside the existing `dependencies { ... }` block.

- [ ] **Step 5: Create parser**

Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileYoloOutputParser.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

object MobileYoloOutputParser {
    fun parseNmsRows(
        rows: Array<FloatArray>,
        labels: List<String>,
        threshold: Float
    ): MobileFireResult {
        val best = rows
            .filter { it.size >= 6 }
            .filter { it[4] >= threshold }
            .maxByOrNull { it[4] }
            ?: return MobileFireResult(detected = false)

        val labelIndex = best[5].toInt().coerceIn(0, labels.lastIndex)
        val cx = best[0]
        val cy = best[1]
        val width = best[2]
        val height = best[3]
        val left = (cx - width / 2f).coerceIn(0f, 1f)
        val top = (cy - height / 2f).coerceIn(0f, 1f)
        val right = (cx + width / 2f).coerceIn(0f, 1f)
        val bottom = (cy + height / 2f).coerceIn(0f, 1f)
        val box = MobileFireBox(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            score = best[4],
            classId = labelIndex,
            label = labels[labelIndex]
        )
        return MobileFireResult(
            detected = true,
            confidence = best[4],
            box = box
        )
    }
}
```

- [ ] **Step 6: Create engine wrapper**

Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionEngine.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MobileFireDetectionEngine(context: Context) : AutoCloseable {
    private val interpreter: Interpreter = Interpreter(loadModel(context, MODEL_ASSET))
    private val labels: List<String> = context.assets.open(LABELS_ASSET).bufferedReader().use { reader ->
        reader.readLines().filter { it.isNotBlank() }
    }

    fun detect(bitmap: Bitmap, sampledAtMs: Long = System.currentTimeMillis()): MobileFireResult {
        val started = SystemClock.elapsedRealtime()
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val input = bitmapToFloatBuffer(resized)
        if (resized !== bitmap) resized.recycle()

        val output = Array(1) { Array(MAX_DETECTIONS) { FloatArray(6) } }
        interpreter.run(input, output)
        val parsed = MobileYoloOutputParser.parseNmsRows(
            rows = output[0],
            labels = labels,
            threshold = SCORE_THRESHOLD
        )
        return parsed.copy(
            inferenceMs = SystemClock.elapsedRealtime() - started,
            sampledAtMs = sampledAtMs
        )
    }

    override fun close() {
        interpreter.close()
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    companion object {
        const val MODEL_ASSET = "mobile_fire.tflite"
        const val LABELS_ASSET = "mobile_fire_labels.txt"
        const val INPUT_SIZE = 640
        const val MAX_DETECTIONS = 300
        const val SCORE_THRESHOLD = 0.50f

        private fun loadModel(context: Context, assetName: String): MappedByteBuffer {
            val fd = context.assets.openFd(assetName)
            FileInputStream(fd.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                )
            }
        }
    }
}
```

- [ ] **Step 7: Run tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileYoloOutputParserTest" --tests "*MobileFireDetectionEngineContractTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add app/build.gradle.kts app/src/main/java/com/example/smart_safety_management/mobileai/MobileYoloOutputParser.kt app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionEngine.kt app/src/test/java/com/example/smart_safety_management/mobileai/MobileYoloOutputParserTest.kt app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireDetectionEngineContractTest.kt
git commit -m "feat(mobile-ai): add TFLite fire engine"
```

---

### Task 4: Add Mobile Edge Function for Fire Event Creation

**Files:**
- Create: `supabase/functions/mobile-ai-event/helpers.ts`
- Create: `supabase/functions/mobile-ai-event/index.ts`
- Create: `supabase/functions/mobile-ai-event/index.test.ts`
- Modify: `app/src/main/java/com/example/smart_safety_management/RetrofitClient.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/SignUpService.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/MobileFireApiContractTest.kt`

- [ ] **Step 1: Write Deno helper tests**

Create `supabase/functions/mobile-ai-event/index.test.ts`:

```typescript
import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { buildCapturePath, normalizeAccuracy } from "./helpers.ts";

Deno.test("buildCapturePath uses existing detection prefix", () => {
  const path = buildCapturePath(5, 1780298670271);
  assertEquals(path, "detection/5/fire_5_1780298670271.jpg");
});

Deno.test("normalizeAccuracy clamps to zero-one range", () => {
  assertEquals(normalizeAccuracy(-1), 0);
  assertEquals(normalizeAccuracy(0.75), 0.75);
  assertEquals(normalizeAccuracy(2), 1);
});
```

- [ ] **Step 2: Write Android API contract test**

Create `app/src/test/java/com/example/smart_safety_management/MobileFireApiContractTest.kt`:

```kotlin
package com.example.smart_safety_management

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MobileFireApiContractTest {
    @Test
    fun retrofitRoutesMobileFireEventToFunction() {
        val text = Files.readString(Paths.get("src/main/java/com/example/smart_safety_management/RetrofitClient.kt"))
        assertTrue(text.contains("\"/create_mobile_fire_event\" to Route(\"mobile-ai-event\", \"create_mobile_fire_event\")"))
    }

    @Test
    fun signUpServiceContainsMobileFireEventMethod() {
        val text = Files.readString(Paths.get("src/main/java/com/example/smart_safety_management/SignUpService.kt"))
        assertTrue(text.contains("data class CreateMobileFireEventRequest"))
        assertTrue(text.contains("fun createMobileFireEvent"))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```powershell
deno test supabase/functions/mobile-ai-event/index.test.ts
```

Expected: FAIL because the function does not exist.

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileFireApiContractTest" --no-daemon
```

Expected: FAIL because route/API DTOs do not exist.

- [ ] **Step 4: Create Edge Function helpers**

Create `supabase/functions/mobile-ai-event/helpers.ts`:

```typescript
export function buildCapturePath(cameraId: number, timestampMs: number): string {
  return `detection/${cameraId}/fire_${cameraId}_${timestampMs}.jpg`;
}

export function normalizeAccuracy(value: unknown): number {
  if (typeof value !== "number" || Number.isNaN(value)) return 0;
  return Math.min(1, Math.max(0, value));
}
```

- [ ] **Step 5: Create Edge Function**

Create `supabase/functions/mobile-ai-event/index.ts`:

```typescript
import { createAdminClient, createUserClient } from "../_shared/supabase.ts";
import { createAiEvent } from "../_shared/ai_events.ts";
import { err, ok, optionsResponse } from "../_shared/response.ts";
import { buildCapturePath, normalizeAccuracy } from "./helpers.ts";

const CAPTURES_BUCKET = "camera-captures";
const EVENT_NAME = "화재";
const RISK_LEVEL = "DANGER";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return optionsResponse();
  if (req.method !== "POST") return err("Method not allowed", 405);

  try {
    const body = await req.json();
    if (body.action !== "create_mobile_fire_event") {
      return err("Unknown action", 400);
    }

    const cameraId = Number(body.camera_id);
    const jpegBase64 = typeof body.jpeg_base64 === "string" ? body.jpeg_base64 : "";
    const accuracy = normalizeAccuracy(body.accuracy);
    if (!Number.isInteger(cameraId) || cameraId <= 0) {
      return err("camera_id must be a positive integer", 400);
    }
    if (!jpegBase64) {
      return err("jpeg_base64 is required", 400);
    }

    const userClient = createUserClient(req);
    const { data: visibleCamera, error: visibleErr } = await userClient
      .from("cameras")
      .select("camera_id")
      .eq("camera_id", cameraId)
      .maybeSingle();
    if (visibleErr || !visibleCamera) {
      return err("Camera not visible for current user", 403);
    }

    const bytes = Uint8Array.from(atob(jpegBase64), (char) => char.charCodeAt(0));
    if (bytes.length < 1024) {
      return err("jpeg payload too small", 400);
    }

    const admin = createAdminClient();
    const path = buildCapturePath(cameraId, Date.now());
    const { error: uploadErr } = await admin.storage
      .from(CAPTURES_BUCKET)
      .upload(path, bytes, {
        contentType: "image/jpeg",
        upsert: false,
      });
    if (uploadErr) return err(uploadErr.message, 500);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const publicUrl = `${supabaseUrl}/storage/v1/object/public/${CAPTURES_BUCKET}/${path}`;

    await admin
      .from("cameras")
      .update({ last_frame_at: new Date().toISOString() })
      .eq("camera_id", cameraId);

    return await createAiEvent(admin, {
      camera_id: cameraId,
      accuracy,
      risk_level: RISK_LEVEL,
      event_name: EVENT_NAME,
      image_url: publicUrl,
    });
  } catch (e) {
    console.error("[mobile-ai-event]", e);
    return err(e instanceof Error ? e.message : "Internal server error", 500);
  }
});
```

- [ ] **Step 6: Add Retrofit route**

Modify `app/src/main/java/com/example/smart_safety_management/RetrofitClient.kt` inside `postRoutes`:

```kotlin
"/create_mobile_fire_event" to Route("mobile-ai-event", "create_mobile_fire_event"),
```

- [ ] **Step 7: Add API DTOs and method**

Modify `app/src/main/java/com/example/smart_safety_management/SignUpService.kt`:

```kotlin
data class CreateMobileFireEventRequest(
    @SerializedName("camera_id") val cameraId: Int,
    @SerializedName("accuracy") val accuracy: Double,
    @SerializedName("jpeg_base64") val jpegBase64: String
)

data class CreateMobileFireEventResponse(
    @SerializedName("event_id") val eventId: Int?,
    @SerializedName("capture_id") val captureId: Int?,
    @SerializedName("capture_image_url") val captureImageUrl: String?,
    @SerializedName("skipped") val skipped: Boolean? = null,
    @SerializedName("reason") val reason: String? = null
)
```

Add method to `interface SignUpService`:

```kotlin
@POST("/create_mobile_fire_event")
fun createMobileFireEvent(
    @Body request: CreateMobileFireEventRequest
): Call<CreateMobileFireEventResponse>
```

- [ ] **Step 8: Run tests**

Run:

```powershell
deno test supabase/functions/mobile-ai-event/index.test.ts
```

Expected: PASS.

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileFireApiContractTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add supabase/functions/mobile-ai-event/helpers.ts supabase/functions/mobile-ai-event/index.ts supabase/functions/mobile-ai-event/index.test.ts app/src/main/java/com/example/smart_safety_management/RetrofitClient.kt app/src/main/java/com/example/smart_safety_management/SignUpService.kt app/src/test/java/com/example/smart_safety_management/MobileFireApiContractTest.kt
git commit -m "feat(mobile-ai): add mobile fire event function"
```

---

### Task 5: Add Upload Repository and Coordinator

**Files:**
- Create: `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireEventRepository.kt`
- Create: `app/src/main/java/com/example/smart_safety_management/mobileai/RtspFrameSampler.kt`
- Create: `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionCoordinator.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireEventRepositoryTest.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireDetectionCoordinatorTest.kt`

- [ ] **Step 1: Write repository test**

Create `app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireEventRepositoryTest.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MobileFireEventRepositoryTest {
    @Test
    fun jpegBase64IsNotEmptyForSolidBitmap() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        val encoded = MobileFireEventRepository.bitmapToJpegBase64(bitmap, quality = 80)
        assertTrue(encoded.length > 100)
    }
}
```

- [ ] **Step 2: Write coordinator test**

Create `app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireDetectionCoordinatorTest.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MobileFireDetectionCoordinatorTest {
    @Test
    fun oneDetectedSampleUploadsOnce() = runTest {
        val uploader = FakeUploader()
        val coordinator = MobileFireDetectionCoordinator(
            cameraId = 9,
            engine = FakeEngine(
                MobileFireResult(detected = true, confidence = 0.9f, inferenceMs = 12L, sampledAtMs = 100L)
            ),
            sampler = FakeSampler(hasFrame = true),
            uploader = uploader,
            nowMs = { 100L },
            cooldownMs = 60_000L
        )
        coordinator.runOneCycle()
        assertEquals(1, uploader.uploadCount)
        assertTrue(coordinator.state.value.lastUploadEventId == 123)
    }

    private class FakeEngine(private val result: MobileFireResult) : MobileFireDetector {
        override fun detectFrame(frame: android.graphics.Bitmap): MobileFireResult = result
        override fun close() = Unit
    }

    private class FakeSampler(private val hasFrame: Boolean) : RtspFrameSampler {
        override fun sampleFrame(width: Int, height: Int): android.graphics.Bitmap? {
            return if (!hasFrame) null else android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        }
    }

    private class FakeUploader : MobileFireUploader {
        var uploadCount = 0
        override suspend fun upload(cameraId: Int, frame: android.graphics.Bitmap, confidence: Float): Int? {
            uploadCount += 1
            return 123
        }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileFireEventRepositoryTest" --tests "*MobileFireDetectionCoordinatorTest" --no-daemon
```

Expected: FAIL because repository/coordinator interfaces do not exist.

- [ ] **Step 4: Add Robolectric test dependency**

Modify `app/build.gradle.kts` inside `dependencies { ... }`:

```kotlin
testImplementation("org.robolectric:robolectric:4.12.2")
```

- [ ] **Step 5: Create sampler and detector interfaces**

Create `app/src/main/java/com/example/smart_safety_management/mobileai/RtspFrameSampler.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import android.view.TextureView

interface RtspFrameSampler {
    fun sampleFrame(width: Int = 640, height: Int = 640): Bitmap?
}

class TextureViewFrameSampler(
    private val textureView: TextureView
) : RtspFrameSampler {
    override fun sampleFrame(width: Int, height: Int): Bitmap? {
        return if (textureView.isAvailable) textureView.getBitmap(width, height) else null
    }
}

interface MobileFireDetector : AutoCloseable {
    fun detectFrame(frame: Bitmap): MobileFireResult
}
```

Modify `MobileFireDetectionEngine` so it implements `MobileFireDetector`:

```kotlin
class MobileFireDetectionEngine(context: Context) : MobileFireDetector {
    override fun detectFrame(frame: Bitmap): MobileFireResult = detect(frame)
}
```

- [ ] **Step 6: Create repository**

Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireEventRepository.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import android.util.Base64
import com.example.smart_safety_management.CreateMobileFireEventRequest
import com.example.smart_safety_management.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface MobileFireUploader {
    suspend fun upload(cameraId: Int, frame: Bitmap, confidence: Float): Int?
}

class MobileFireEventRepository : MobileFireUploader {
    override suspend fun upload(cameraId: Int, frame: Bitmap, confidence: Float): Int? {
        val jpeg = bitmapToJpegBase64(frame)
        val request = CreateMobileFireEventRequest(
            cameraId = cameraId,
            accuracy = java.lang.Double.parseDouble(confidence.coerceIn(0f, 1f).toString()),
            jpegBase64 = jpeg
        )
        return suspendCancellableCoroutine { cont ->
            val call = RetrofitClient.instance.createMobileFireEvent(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback<com.example.smart_safety_management.CreateMobileFireEventResponse> {
                override fun onResponse(
                    call: Call<com.example.smart_safety_management.CreateMobileFireEventResponse>,
                    response: Response<com.example.smart_safety_management.CreateMobileFireEventResponse>
                ) {
                    if (response.isSuccessful) {
                        cont.resume(response.body()?.eventId)
                    } else {
                        cont.resumeWithException(IllegalStateException("mobile fire upload failed: HTTP ${response.code()}"))
                    }
                }

                override fun onFailure(
                    call: Call<com.example.smart_safety_management.CreateMobileFireEventResponse>,
                    t: Throwable
                ) {
                    cont.resumeWithException(t)
                }
            })
        }
    }

    companion object {
        fun bitmapToJpegBase64(bitmap: Bitmap, quality: Int = 85): String {
            val bytes = ByteArrayOutputStream(64 * 1024).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}
```

- [ ] **Step 7: Create coordinator**

Create `app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionCoordinator.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileFireDetectionCoordinator(
    private val cameraId: Int,
    private val engine: MobileFireDetector,
    private val sampler: RtspFrameSampler,
    private val uploader: MobileFireUploader,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val cooldownMs: Long = 60_000L,
    private val sampleIntervalMs: Long = 2_000L
) : AutoCloseable {
    private val _state = MutableStateFlow(MobileFireDetectionState())
    val state: StateFlow<MobileFireDetectionState> = _state
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        _state.value = MobileFireDetectionReducer.reduce(
            _state.value,
            MobileFireDetectionAction.Start(cameraId, nowMs())
        )
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                runOneCycle()
                delay(sampleIntervalMs)
            }
        }
    }

    suspend fun runOneCycle() {
        val frame = withContext(Dispatchers.Main) { sampler.sampleFrame(640, 640) } ?: return
        try {
            val result = engine.detectFrame(frame)
            val next = MobileFireDetectionReducer.reduce(
                _state.value,
                MobileFireDetectionAction.InferenceResult(result, nowMs(), cooldownMs)
            )
            _state.value = next
            if (next.canUpload && result.detected && result.confidence != null) {
                val eventId = uploader.upload(cameraId, frame, result.confidence)
                _state.value = MobileFireDetectionReducer.reduce(
                    _state.value,
                    MobileFireDetectionAction.UploadComplete(eventId)
                )
            }
        } catch (e: Exception) {
            _state.value = MobileFireDetectionReducer.reduce(
                _state.value,
                MobileFireDetectionAction.Error(e.message ?: "mobile fire detection failed")
            )
        } finally {
            frame.recycle()
        }
    }

    override fun close() {
        job?.cancel()
        job = null
        engine.close()
        _state.value = MobileFireDetectionReducer.reduce(_state.value, MobileFireDetectionAction.Stop)
    }
}
```

- [ ] **Step 8: Run tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileFireEventRepositoryTest" --tests "*MobileFireDetectionCoordinatorTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add app/build.gradle.kts app/src/main/java/com/example/smart_safety_management/mobileai/RtspFrameSampler.kt app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireEventRepository.kt app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionCoordinator.kt app/src/main/java/com/example/smart_safety_management/mobileai/MobileFireDetectionEngine.kt app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireEventRepositoryTest.kt app/src/test/java/com/example/smart_safety_management/mobileai/MobileFireDetectionCoordinatorTest.kt
git commit -m "feat(mobile-ai): coordinate RTSP fire detection uploads"
```

---

### Task 6: Add RTSP Texture Player and Detection UI Badge

**Files:**
- Create: `app/src/main/java/com/example/smart_safety_management/mobileai/RtspTexturePlayer.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/screens/detail/MobileFireDetectionUiContractTest.kt`

- [ ] **Step 1: Write UI contract test**

Create `app/src/test/java/com/example/smart_safety_management/screens/detail/MobileFireDetectionUiContractTest.kt`:

```kotlin
package com.example.smart_safety_management.screens.detail

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MobileFireDetectionUiContractTest {
    @Test
    fun internalDetailUsesRtspMobileDetectionPlayerForRtspUrls() {
        val text = Files.readString(Paths.get("src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt"))
        assertTrue(text.contains("RtspMobileDetectionPlayer("))
        assertTrue(text.contains("MobileFireDetectionBadge("))
    }

    @Test
    fun rtspPlayerUsesTextureViewForSampling() {
        val text = Files.readString(Paths.get("src/main/java/com/example/smart_safety_management/mobileai/RtspTexturePlayer.kt"))
        assertTrue(text.contains("TextureView"))
        assertTrue(text.contains("TextureViewFrameSampler"))
        assertTrue(text.contains("MobileFireDetectionCoordinator"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileFireDetectionUiContractTest" --no-daemon
```

Expected: FAIL because the RTSP mobile player does not exist.

- [ ] **Step 3: Create RTSP player composable**

Create `app/src/main/java/com/example/smart_safety_management/mobileai/RtspTexturePlayer.kt`:

```kotlin
package com.example.smart_safety_management.mobileai

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

@Composable
fun RtspMobileDetectionPlayer(
    url: String,
    cameraId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    val coordinator = remember(url, cameraId, textureView) {
        val view = textureView
        if (view == null || cameraId <= 0) null else MobileFireDetectionCoordinator(
            cameraId = cameraId,
            engine = MobileFireDetectionEngine(context),
            sampler = TextureViewFrameSampler(view),
            uploader = MobileFireEventRepository()
        )
    }
    val state by (coordinator?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(MobileFireDetectionState()) }).collectAsState()

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { textureView = it }
            },
            modifier = Modifier.matchParentSize()
        )
        MobileFireDetectionBadge(
            state = state,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        )
    }

    DisposableEffect(url, textureView) {
        val view = textureView
        if (view == null) {
            onDispose { }
        } else {
            val libVlc = LibVLC(context, listOf("--rtsp-tcp", "--network-caching=300"))
            val media = Media(libVlc, Uri.parse(url)).apply { setHWDecoderEnabled(true, false) }
            val player = MediaPlayer(libVlc).apply { setMedia(media) }
            media.release()
            player.vlcVout.setVideoView(view)
            player.vlcVout.setWindowSize(1280, 720)
            player.vlcVout.attachViews()
            player.play()
            onDispose {
                coordinator?.close()
                runCatching { player.stop() }
                runCatching { player.vlcVout.detachViews() }
                runCatching { player.release() }
                runCatching { libVlc.release() }
            }
        }
    }

    LaunchedEffect(coordinator) {
        delay(3_000L)
        coordinator?.start(this)
    }
}

@Composable
fun MobileFireDetectionBadge(
    state: MobileFireDetectionState,
    modifier: Modifier = Modifier
) {
    val label = when (state.status) {
        MobileFireDetectionStatus.OFF -> "모바일 감지 꺼짐"
        MobileFireDetectionStatus.WARMING_UP -> "모바일 감지 준비 중"
        MobileFireDetectionStatus.RUNNING -> "모바일 감지 실행 중"
        MobileFireDetectionStatus.DETECTED -> "화재 감지됨"
        MobileFireDetectionStatus.COOLDOWN -> "모바일 감지 대기"
        MobileFireDetectionStatus.ERROR -> "모바일 감지 오류"
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        modifier = modifier
    )
}
```

- [ ] **Step 4: Wire RTSP player into detail screen**

Modify `app/src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt`.

Add import:

```kotlin
import com.example.smart_safety_management.mobileai.RtspMobileDetectionPlayer
```

Change `SmartPreviewCard` signature:

```kotlin
private fun SmartPreviewCard(
    imageRes: Int,
    border: Color,
    modifier: Modifier = Modifier,
    isLive: Boolean = true,
    imageUrl: String? = null,
    streamId: String?,
    label: String,
    cameraId: Int = 0
)
```

Pass `cameraId = cameraId` from both calls in `InternalDetailScreen`.

Inside `SmartPreviewCard`, replace RTSP playback branch:

```kotlin
if (isVideo) {
    val trimmedUrl = imageUrl!!.trim()
    if (trimmedUrl.startsWith("rtsp://", ignoreCase = true) ||
        trimmedUrl.startsWith("rtsps://", ignoreCase = true)
    ) {
        RtspMobileDetectionPlayer(
            url = trimmedUrl,
            cameraId = cameraId,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        VideoPlayer(url = trimmedUrl, modifier = Modifier.fillMaxSize())
    }
}
```

- [ ] **Step 5: Run UI contract test**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*MobileFireDetectionUiContractTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/smart_safety_management/mobileai/RtspTexturePlayer.kt app/src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt app/src/test/java/com/example/smart_safety_management/screens/detail/MobileFireDetectionUiContractTest.kt
git commit -m "feat(mobile-ai): show fire detection on RTSP detail"
```

---

### Task 7: Full Android Verification and Edge Function Deploy Notes

**Files:**
- Modify: `docs/superpowers/specs/2026-06-04-mobile-rtsp-fire-detection-design.md`
- Create: `docs/mobile-rtsp-fire-detection-runbook.md`

- [ ] **Step 1: Create runbook**

Create `docs/mobile-rtsp-fire-detection-runbook.md`:

```markdown
# Mobile RTSP Fire Detection Runbook

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

## Supabase Deploy

```powershell
supabase functions deploy mobile-ai-event
```

## Manual PoC

1. Install the debug APK on the Android test phone.
2. Log in as a manager.
3. Open `AI감지` once to confirm current event list loads.
4. Open `실시간상황`.
5. Tap the Drift RTSP camera.
6. Confirm the live panel shows `모바일 감지 실행 중`.
7. Present an approved fire reference target in front of the Drift camera.
8. Wait up to 10 seconds.
9. Confirm the badge changes to `화재 감지됨`.
10. Confirm a new Storage object exists under `camera-captures/detection/{cameraId}/fire_...jpg`.
11. Confirm `AI감지` shows a new fire event using the uploaded image.

## Rollback

If the Android PoC misbehaves during a demo, switch back to the PC path by opening an APK build before this feature branch or by disabling the RTSP detail screen entry point for `RtspMobileDetectionPlayer`.
```

- [ ] **Step 2: Add implementation note to spec**

Append to `docs/superpowers/specs/2026-06-04-mobile-rtsp-fire-detection-design.md`:

```markdown
## Implementation Plan

Implementation is tracked in `docs/superpowers/plans/2026-06-04-mobile-rtsp-fire-detection.md`.
Operational verification steps are tracked in `docs/mobile-rtsp-fire-detection-runbook.md`.
```

- [ ] **Step 3: Run all Android tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run Android debug build**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run Deno tests**

Run:

```powershell
deno test supabase/functions/mobile-ai-event/index.test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add docs/mobile-rtsp-fire-detection-runbook.md docs/superpowers/specs/2026-06-04-mobile-rtsp-fire-detection-design.md
git commit -m "docs: add mobile fire detection runbook"
```

---

## Self-Review

Spec coverage:

- Fire-only scope is covered by Tasks 1, 3, 4, 5, and 6.
- RTSP detail screen placement is covered by Task 6.
- Foreground-only execution is enforced by Task 6 using `DisposableEffect` and a screen-scoped coordinator.
- Existing Supabase capture/event contract is covered by Task 4.
- Existing AI Event UI reuse is covered by Task 4 because it delegates to `createAiEvent`.
- Error handling is covered by reducer states in Task 2, coordinator catch path in Task 5, and UI badge states in Task 6.
- Verification and runbook are covered by Task 7.

Unresolved marker scan:

- This plan contains no unresolved marker words or open-ended implementation instructions.
- Each code-changing step includes concrete code.

Type consistency:

- `MobileFireResult`, `MobileFireDetectionState`, `MobileFireDetector`, `RtspFrameSampler`, and `MobileFireUploader` are defined before use in later tasks.
- `CreateMobileFireEventRequest` and `CreateMobileFireEventResponse` are defined before `MobileFireEventRepository` uses them.
- `RtspMobileDetectionPlayer` is created before `InternalDetail.kt` imports it.

## Execution Handoff

Plan complete. Use one of these execution options:

1. **Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.
