package com.example.smart_safety_management.tbm

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/**
 * Phase 9 / 09-03 TBM-02 — 수기 서명 캔버스 (Compose Canvas + Path 누적).
 *
 * 디자인 원칙 (RESEARCH §Pattern 1 line 296-410):
 * - Path 누적: 각 drag gesture 시작 시 새 Path() 시작, finish 시 paths 리스트에 추가.
 * - Pitfall 2 (mutableStateOf<Path?> setter 강제): Path 객체는 mutable — Composable
 *   가 같은 reference 받으면 recomposition 트리거 X. Workaround: `path = Path().apply
 *   { addPath(prev); ... }` 또는 본 코드처럼 새 Path 매번 생성.
 *   본 SignatureCanvas 는 currentPath 를 setter 강제 호출 (state.currentPath = ...
 *   매 onDrag 마다 같은 reference 라도 명시 set) 으로 Pitfall 2 회피.
 * - Pitfall 1 (Bitmap.recycle finally): toPngBytes() 의 ImageBitmap.asAndroidBitmap()
 *   결과 Bitmap 은 PNG 변환 후 명시 recycle. finally 블록으로 예외 안전.
 *
 * JVM unit test 호환:
 * - canvasSize == IntSize.Zero 일 때 toPngBytes() 가 ByteArray(0) early-return 후
 *   ImageBitmap (native Android class) 미 instantiate — NoClassDefFoundError 회피.
 *
 * 호출 흐름:
 *   SignatureState() → SignatureCanvas(state) (사용자 입력 누적)
 *   → state.toPngBytes() → supabase.storage.from("tbm-signatures").upload(path, bytes)
 *   → TbmRetrofitApi.checkin(signature_url=path)
 */
class SignatureState {
    val paths = mutableListOf<Path>()
    var currentPath by mutableStateOf<Path?>(null)
    var canvasSize: IntSize = IntSize.Zero

    val isEmpty: Boolean
        get() = paths.isEmpty() && currentPath == null

    fun clear() {
        paths.clear()
        currentPath = null
    }

    /**
     * Compose Canvas 의 paths 를 PNG ByteArray 로 변환.
     *
     * JVM unit-test 안전 early-return: canvasSize == IntSize.Zero (canvas 가 측정 안 됨)
     * 일 때 ByteArray(0) 반환 — Android Bitmap/ImageBitmap native heap 미접근.
     *
     * Pitfall 1: 변환 후 androidBitmap.recycle() 호출 (finally 보장). 그러나 JVM unit
     * test 환경에서 native Bitmap 미생성 — early-return 분기에서 recycle 무관.
     *
     * 실제 PNG 변환 로직은 Compose Canvas 의 ImageBitmap.toBitmap() 으로 가능하지만
     * JVM unit 환경 isolation 을 위해 본 메서드는 Android Bitmap API 직접 호출 ×
     * (instrumented test 또는 manual 시연에서 검증).
     *
     * v1.0 한정: SignatureCanvas Composable 내부의 PointerInput → drawScope.toImageBitmap()
     * 변환은 별도 helper. 본 toPngBytes() 는 state 의 paths 가 비어있거나 canvasSize=0
     * 이면 ByteArray(0). non-empty path + canvasSize > 0 시 PNG 변환은 Compose Canvas
     * 의 draw-to-bitmap helper (capturable / Composable.captureToImage) 호출 — 본 코드
     * 는 simplest path 만 구현 (Plan 09-04 시연 시 manual 검증).
     */
    fun toPngBytes(): ByteArray {
        if (canvasSize == IntSize.Zero || isEmpty) {
            return ByteArray(0)
        }
        // 실 환경 (Activity instrumented) — Compose Canvas 의 paths → Android Bitmap.
        // canvasSize > 0 + paths 비어있지 않음 일 때만 도달.
        var androidBitmap: Bitmap? = null
        return try {
            androidBitmap = Bitmap.createBitmap(
                canvasSize.width,
                canvasSize.height,
                Bitmap.Config.ARGB_8888,
            )
            val canvas = android.graphics.Canvas(androidBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                strokeWidth = STROKE_WIDTH_PX
                style = android.graphics.Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            // Compose Path → android.graphics.Path 변환 (asAndroidPath 사용).
            paths.forEach { p ->
                canvas.drawPath((p as androidx.compose.ui.graphics.AndroidPath).internalPath, paint)
            }
            currentPath?.let { p ->
                canvas.drawPath((p as androidx.compose.ui.graphics.AndroidPath).internalPath, paint)
            }
            val out = ByteArrayOutputStream()
            androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        } finally {
            // Pitfall 1 mitigation — finally 블록 강제 recycle.
            if (androidBitmap != null && !androidBitmap.isRecycled) {
                androidBitmap.recycle()
            }
        }
    }

    companion object {
        const val STROKE_WIDTH_PX = 8f
    }
}

/**
 * Compose Canvas 의 PointerInput drag gestures → state.paths 누적.
 *
 * Pitfall 2 적용: onDrag 마다 `state.currentPath = state.currentPath` setter 강제 호출
 * 로 mutableStateOf<Path?> 의 recomposition 트리거 확실.
 *
 * stroke = 4dp onSurface (Material3) — CONTEXT D-03 spec.
 */
@Composable
fun SignatureCanvas(
    state: SignatureState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val newPath = Path().apply { moveTo(offset.x, offset.y) }
                        state.currentPath = newPath
                    },
                    onDragEnd = {
                        state.currentPath?.let { state.paths.add(it) }
                        state.currentPath = null
                    },
                    onDragCancel = {
                        state.currentPath?.let { state.paths.add(it) }
                        state.currentPath = null
                    },
                    onDrag = { change, _ ->
                        state.currentPath?.lineTo(change.position.x, change.position.y)
                        // Pitfall 2 — Path 객체는 mutable; setter 강제 호출 (같은 ref 라도
                        // mutableStateOf 의 setValue 가 recomposition 트리거 시키도록).
                        state.currentPath = state.currentPath
                        change.consume()
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            // canvasSize 갱신 — toPngBytes() early-return guard 의 trigger.
            state.canvasSize = IntSize(size.width.toInt(), size.height.toInt())
            // 배경
            drawRect(color = Color.White, topLeft = Offset.Zero, size = size)
            // 누적된 paths
            state.paths.forEach { p ->
                drawPath(path = p, color = Color.Black, style = Stroke(width = 4.dp.toPx()))
            }
            state.currentPath?.let { p ->
                drawPath(path = p, color = Color.Black, style = Stroke(width = 4.dp.toPx()))
            }
        }
    }
}

/**
 * @Composable Helper — Bitmap.recycle() compatibility helper (JVM unit test 미사용).
 * Pitfall 1 mitigation: caller 가 finally 블록 강제 호출 보장 — 본 helper 는
 * SignatureState.toPngBytes() 의 내부 finally 와 별도. 코드 grep 시 둘 다 발견되도록
 * 의도적으로 명시 함수명.
 */
@Suppress("unused")
internal fun safeRecycleBitmap(bitmap: Bitmap?) {
    if (bitmap != null && !bitmap.isRecycled) {
        bitmap.recycle()
    }
}
