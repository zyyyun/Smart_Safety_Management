package com.example.smart_safety_management

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 11 / 11-02 Sub-task 3.2 — Setting* XML Activity 의 setSupportActionBar wiring 회귀 가드.
 *
 * SettingActivity 는 11-02 plan inventory 단계에서 추가 (XML 기반 확인).
 */
class SettingActivityToolbarWiringTest {
    private val activities = listOf(
        "SettingActivity.kt",                  // inventory 결과 XML
        "SettingWorkerActivity.kt",
        "SettingPeopleManagementActivity.kt",
        "SettingProfileActivity.kt",
        "SettingInvitePhonenumberActivity.kt",
        "SettingInviteCodeActivity.kt",
        "SettingInviteCancelActivity.kt",
        "SettingInviteActivity.kt",
        "SettingCreateWorkplaceActivity.kt",
        "SettingChangePasswordActivity.kt",
    )

    @Test fun allSettingActivitiesWireSetSupportActionBar() {
        activities.forEach { name ->
            val f = File("src/main/java/com/example/smart_safety_management/$name")
            assertTrue("$name not found", f.exists())
            val src = f.readText()
            assertTrue(
                "$name 가 setSupportActionBar(toolbar) wiring 누락",
                src.contains("setSupportActionBar(") && src.contains("R.id.toolbar")
            )
        }
    }
}
