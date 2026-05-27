package com.example.smart_safety_management

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 11 / 11-02 Sub-task 1 — Home 카드 5종 refactor 회귀 가드 (worker).
 *
 * HomeWorkerActivity.kt 의 Compose 영역 inline hex literal 이 SsmColors 로 교체되었는지 확인.
 * 본 test 는 ComposeView setContent 블록 내 literal 만 검증.
 */
class HomeWorkerActivitySsmTokenUsageTest {
    private val file = File("src/main/java/com/example/smart_safety_management/HomeWorkerActivity.kt")

    @Test fun homeWorkerActivity_exists() {
        assertTrue("HomeWorkerActivity.kt missing", file.exists())
    }

    @Test fun homeWorkerActivity_composeBlocks_doNotUseInlineHexColor() {
        val src = file.readText()
        val hasComposeView = src.contains("ComposeView") || src.contains("setupWatchCard")
        assertTrue("HomeWorkerActivity 는 ComposeView 사용 — Plan 11-01 Task 7 후 SsmColors import 필요", hasComposeView)
        assertTrue(
            "HomeWorkerActivity.kt 에 SsmColors import 누락 (Compose 영역 색 통일 누락)",
            src.contains("import com.example.smart_safety_management.ui.SsmColors")
        )
    }

    @Test fun homeWorkerActivity_doesNotIntroduceNewBrandOrangeLiteral() {
        val src = file.readText()
        assertFalse(
            "HomeWorkerActivity.kt 에 Compose 영역 inline Color(0xFFF59E0B) 발견 — SsmColors.ActiveOrange 로 교체",
            src.contains("Color(0xFFF59E0B)")
        )
        assertFalse(
            "HomeWorkerActivity.kt 에 Compose 영역 inline Color(0xFFF97316) 발견 — colors.xml @color/orange500 또는 SsmColors 로 교체",
            src.contains("Color(0xFFF97316)")
        )
    }
}
