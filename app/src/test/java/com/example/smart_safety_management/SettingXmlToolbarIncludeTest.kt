package com.example.smart_safety_management

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 11 / 11-02 Sub-task 3.1 — D4 의 A 옵션 적용.
 * setting_*.xml + setting.xml (SettingActivity 메인 진입점) 모두 common_toolbar.xml include 보유.
 *
 * setting.xml 은 11-02 plan inventory 단계에서 추가 — SettingActivity 가 XML 기반임을 확인
 * (setContentView(R.layout.setting) + findViewById<ImageButton>(R.id.backButton)).
 */
class SettingXmlToolbarIncludeTest {
    private val xmlFiles = listOf(
        "setting.xml",                       // SettingActivity 메인 (inventory 결과 XML)
        "setting_worker.xml",
        "setting_people_management.xml",
        "setting_my_profile.xml",
        "setting_invite_phonenumber.xml",
        "setting_invite_code.xml",
        "setting_invite_cancel.xml",
        "setting_invite.xml",
        "setting_create_workplace.xml",
        "setting_change_password.xml",
    )

    @Test fun allSettingXmlsIncludeCommonToolbar() {
        xmlFiles.forEach { name ->
            val f = File("src/main/res/layout/$name")
            assertTrue("$name not found", f.exists())
            val src = f.readText()
            assertTrue(
                "$name 가 common_toolbar.xml include 누락 (D4 A 옵션 적용 안됨)",
                src.contains("@layout/common_toolbar")
            )
        }
    }
}
