package com.example.smart_safety_management.tbm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 11 / 11-01 Bucket D — TBM inline 색 → SsmColors refactor 회귀 가드.
 *
 * Task 6 commit: RED (3 test 중 1 이상 FAIL — inline literal + private SectionHeader 존재).
 * Task 7 commit: GREEN (refactor 후 3 test 모두 PASS).
 *
 * cwd fallback: Gradle 의 Android module JVM test cwd 는 일반적으로 `app/`.
 */
class TbmDashboardSsmColorsUsageTest {

    private fun tbmFile(name: String): File {
        val rel = File("src/main/java/com/example/smart_safety_management/tbm/$name")
        return if (rel.exists()) rel else File("app/src/main/java/com/example/smart_safety_management/tbm/$name")
    }

    private val tbmFiles = listOf(
        tbmFile("TbmDashboardScreen.kt"),
        tbmFile("TbmDashboardCardComposable.kt"),
        tbmFile("TbmWorkerCardComposable.kt"),
        tbmFile("TbmWorkerScreen.kt"),
    )

    private val forbiddenInlineLiterals = listOf(
        "Color(0xFFF59E0B)",  // → SsmColors.ActiveOrange
        "Color(0xFFF3F4F6)",  // → SsmColors.EndedBg
        "Color(0xFF6B7280)",  // → SsmColors.TextMuted
        "Color(0xFF2563EB)",  // → SsmColors.TextInfo
        "Color(0xFFEF4444)",  // → SsmColors.TextDanger
    )

    @Test fun tbmFiles_doNotContainInlineHexColorLiterals() {
        tbmFiles.forEach { f ->
            assertTrue("file not found: ${f.absolutePath}", f.exists())
            val src = f.readText()
            forbiddenInlineLiterals.forEach { lit ->
                assertFalse(
                    "TBM refactor 미완: ${f.name} 가 inline literal '$lit' 포함. SsmColors 로 교체 필요.",
                    src.contains(lit),
                )
            }
        }
    }

    @Test fun tbmDashboardScreen_importsSsmColors() {
        val src = tbmFile("TbmDashboardScreen.kt").readText()
        assertTrue(
            "TbmDashboardScreen.kt 에 SsmColors import 누락",
            src.contains("import com.example.smart_safety_management.ui.SsmColors"),
        )
    }

    @Test fun tbmDashboardScreen_doesNotDefinePrivateSectionHeader() {
        // SectionHeader 는 ui/components/SectionHeader.kt 에서 single source-of-truth.
        // tbm/ 내 중복 정의 제거 → import 로 교체.
        val src = tbmFile("TbmDashboardScreen.kt").readText()
        assertFalse(
            "TbmDashboardScreen.kt 가 여전히 private fun SectionHeader 정의 — ui.components.SectionHeader 로 교체 필요",
            src.contains("private fun SectionHeader("),
        )
    }
}
