package com.example.smart_safety_management.tbm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9 / 09-03 TBM-02 — SignatureState 단위 테스트 (TDD).
 *
 * 본 test 는 JVM unit test 환경 (Android Bitmap native heap 부재) 에서 동작 가능한
 * code path 만 검증:
 *   - isEmpty (canvasSize=0 + paths empty)
 *   - toPngBytes() 가 canvasSize == IntSize.Zero 시 ByteArray(0) early-return
 *
 * PNG bytes 의 magic header 검증은 instrumented test (Bitmap 의 native heap 필요) 에서
 * 수행 — JVM unit 에선 skip. 본 test 는 early-return guard 만 보장.
 *
 * Pitfall 1 (Bitmap.recycle finally) + Pitfall 2 (mutableStateOf<Path?> setter 강제) 의
 * end-to-end 검증은 manual 또는 Plan 09-04 1일 사이클 시연에서.
 *
 * JUnit4 (org.junit.Test) — Phase 7 일관.
 */
class SignatureStateTest {

    @Test
    fun test_emptyState_isEmpty_true() {
        val s = SignatureState()
        assertTrue(s.isEmpty)
    }

    @Test
    fun test_canvasSizeZero_yields_emptyBytes() {
        val s = SignatureState()
        // canvasSize remains IntSize.Zero by default
        val bytes = s.toPngBytes()
        assertEquals(0, bytes.size)
    }
}
