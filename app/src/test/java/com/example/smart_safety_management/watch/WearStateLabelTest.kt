package com.example.smart_safety_management.watch

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Phase 7 / 07-03 BRIDGE-01b — wear-state 5 상태 + null + unknown 7 케이스 매핑.
 * 의료기기 면책: 모든 라벨에 정확한 단어 '측정값' 부분 문자열 포함 0건 (07-CONTEXT.md).
 */
class WearStateLabelTest {

    @Test
    fun test_WORN_returnsGreenLabel() {
        val mapping = WearStateLabelMap.map("WORN")
        assertEquals("착용 중", mapping.label)
        assertEquals(Color(0xFF22C55E), mapping.color)
    }

    @Test
    fun test_OFF_returnsRedLabel() {
        val mapping = WearStateLabelMap.map("OFF")
        assertEquals("미착용", mapping.label)
        assertEquals(Color(0xFFEF4444), mapping.color)
    }

    @Test
    fun test_ABNORMAL_returnsRedLabel() {
        val mapping = WearStateLabelMap.map("ABNORMAL")
        assertEquals("비정상", mapping.label)
        assertEquals(Color(0xFFEF4444), mapping.color)
    }

    @Test
    fun test_WARMUP_returnsYellowLabel() {
        val mapping = WearStateLabelMap.map("WARMUP")
        assertEquals("워치 준비 중", mapping.label)
        assertEquals(Color(0xFFFBBF24), mapping.color)
    }

    @Test
    fun test_TRANSIENT_returnsYellowLabel() {
        val mapping = WearStateLabelMap.map("TRANSIENT")
        assertEquals("전이 중", mapping.label)
        assertEquals(Color(0xFFFBBF24), mapping.color)
    }

    @Test
    fun test_null_returnsUnknownGray() {
        val mapping = WearStateLabelMap.map(null)
        assertEquals("알 수 없음", mapping.label)
        assertEquals(Color.Gray, mapping.color)
    }

    @Test
    fun test_unknownState_returnsRawWithGray() {
        val mapping = WearStateLabelMap.map("FOO")
        assertEquals("FOO", mapping.label)
        assertEquals(Color.Gray, mapping.color)
    }

    @Test
    fun test_noLabelContainsMeasurementValueWord() {
        // 의료기기 면책 — 정확한 단어 '측정값' 사용 0건 (07-CONTEXT.md)
        val states = listOf("WORN", "OFF", "ABNORMAL", "WARMUP", "TRANSIENT", null, "FOO")
        for (s in states) {
            val mapping = WearStateLabelMap.map(s)
            assertFalse(
                "label '${mapping.label}' for state $s 가 '측정값' 단어 포함",
                mapping.label.contains("측정값")
            )
        }
    }
}
