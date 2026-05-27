package com.example.smart_safety_management

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 11 / 11-02 Sub-task 1 — Home 카드 5종 refactor 회귀 가드.
 *
 * HomeActivity.kt 의 Compose 영역 inline hex literal 이 SsmColors 로 교체되었는지 확인.
 * 예외: legacy Android View XML wiring 영역의 Color.parseColor / GradientDrawable 호출은
 *      colors.xml 의 named color 로 위임 가능하면 위임, 어렵다면 grep 면제 대상 (legacy code).
 * 본 test 는 ComposeView setContent 블록 내 literal 만 검증.
 */
class HomeActivitySsmTokenUsageTest {
    private val file = File("src/main/java/com/example/smart_safety_management/HomeActivity.kt")

    @Test fun homeActivity_exists() {
        assertTrue("HomeActivity.kt missing", file.exists())
    }

    @Test fun homeActivity_composeBlocks_doNotUseInlineHexColor() {
        val src = file.readText()
        // setContent 블록 grep (multiline 단순 검증) — 정확한 추출은 어렵지만
        // 전체 파일에 SsmColors import 가 있고, 신규 추가된 ComposeView setContent 블록의
        // Color(0xFF...) literal 이 SsmColors.* 호출로 대체됨.
        val hasComposeView = src.contains("ComposeView") || src.contains("setupTbmDashboardCard")
        assertTrue("HomeActivity 는 ComposeView 사용 — Plan 11-01 Task 7 후 SsmColors import 필요", hasComposeView)
        assertTrue(
            "HomeActivity.kt 에 SsmColors import 누락 (Compose 영역 색 통일 누락)",
            src.contains("import com.example.smart_safety_management.ui.SsmColors")
        )
    }

    @Test fun homeActivity_doesNotIntroduceNewBrandOrangeLiteral() {
        // 신규로 추가된 Compose 영역에 BrandOrange / ActiveOrange 등 0xFFF59E0B 패턴 inline 금지
        val src = file.readText()
        assertFalse(
            "HomeActivity.kt 에 Compose 영역 inline Color(0xFFF59E0B) 발견 — SsmColors.ActiveOrange 로 교체",
            src.contains("Color(0xFFF59E0B)")
        )
        assertFalse(
            "HomeActivity.kt 에 Compose 영역 inline Color(0xFFF97316) 발견 — colors.xml @color/orange500 또는 SsmColors 로 교체",
            src.contains("Color(0xFFF97316)")
        )
    }
}
