package com.example.smart_safety_management.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenScaffoldConfigTest {
    @Test fun nullOnBack_hidesBackButton() {
        val c = scaffoldConfig(title = "홈", onBack = null)
        assertEquals("홈", c.title)
        assertEquals(false, c.showBack)
    }
    @Test fun nonNullOnBack_showsBackButton() {
        val c = scaffoldConfig(title = "설정", onBack = {})
        assertEquals("설정", c.title)
        assertEquals(true, c.showBack)
    }
    @Test fun emptyTitle_isPreserved() {
        assertEquals("", scaffoldConfig(title = "", onBack = null).title)
    }
    @Test fun longKoreanTitle_isPreserved() {
        val long = "TBM 대시보드 — 진행중 세션"
        assertEquals(long, scaffoldConfig(title = long, onBack = {}).title)
    }
}
