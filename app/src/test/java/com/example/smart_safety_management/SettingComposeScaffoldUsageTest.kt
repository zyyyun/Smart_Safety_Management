package com.example.smart_safety_management

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 11 / 11-02 Sub-task 3.4 — D4 의 SettingScaffold 적용.
 * Compose-driven Setting* 화면이 SettingScaffold import 또는 호출.
 *
 * Pre-flight inventory (executor): SettingActivity.kt 는 XML 기반으로 확인 → Task 3.1/3.2 로 이동.
 * 최종 Compose-driven list = 6 파일 (Activity 5 + Screen 1).
 */
class SettingComposeScaffoldUsageTest {
    private val composeFiles = listOf(
        "SettingWorkplaceAreaScreen.kt",
        "SettingWorkplaceAreaActivity.kt",
        "SettingWorkplaceLocationActivity.kt",
        "SettingDeviceManagementActivity.kt",
        "SettingCctvManagementActivity.kt",
        "SettingOpsCatalogActivity.kt",
    )

    @Test fun composeSettingsUseSettingScaffold() {
        composeFiles.forEach { name ->
            val f = File("src/main/java/com/example/smart_safety_management/$name")
            assertTrue("$name not found", f.exists())
            val src = f.readText()
            assertTrue(
                "$name 가 SettingScaffold import 또는 호출 누락 (D4 적용 안됨)",
                src.contains("SettingScaffold") || src.contains("settingScaffoldConfig")
            )
        }
    }
}
