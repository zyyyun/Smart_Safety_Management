package com.example.smart_safety_management.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingScaffoldConfigTest {
    @Test fun titleAndBackPreserved() {
        val c = settingScaffoldConfig(title = "장소 설정", hasBack = true)
        assertEquals("장소 설정", c.title)
        assertEquals(true, c.hasBack)
    }
    @Test fun hasBackFalse_isPreserved() {
        val c = settingScaffoldConfig(title = "메인 설정", hasBack = false)
        assertEquals(false, c.hasBack)
    }
    @Test fun emptyTitle_isPreserved() {
        assertEquals("", settingScaffoldConfig(title = "", hasBack = true).title)
    }
}
