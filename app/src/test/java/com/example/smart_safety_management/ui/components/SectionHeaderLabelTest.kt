package com.example.smart_safety_management.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionHeaderLabelTest {
    @Test fun nullCount_returnsLabelOnly() {
        assertEquals("진행중", sectionHeaderLabel("진행중", null))
    }
    @Test fun countOne_appendsKoreanUnit() {
        assertEquals("진행중 (1개)", sectionHeaderLabel("진행중", 1))
    }
    @Test fun countZero_appendsZeroWithKoreanUnit() {
        assertEquals("종료 (0개)", sectionHeaderLabel("종료", 0))
    }
    @Test fun countLarge_formatsCorrectly() {
        assertEquals("알림 (12개)", sectionHeaderLabel("알림", 12))
    }
    @Test fun labelDoesNotContainEnglishUnit() {
        val out = sectionHeaderLabel("진행중", 3)
        assertEquals(false, out.contains("count"))
        assertEquals(false, out.contains("items"))
    }
}
