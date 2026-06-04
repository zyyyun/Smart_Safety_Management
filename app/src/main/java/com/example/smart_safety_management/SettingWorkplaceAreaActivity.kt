package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_safety_management.ui.components.SettingScaffold
import com.example.smart_safety_management.ui.components.settingScaffoldConfig
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

class SettingWorkplaceAreaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Phase 11 / 11-02 Sub-task 3.4 — SettingScaffold wiring (UX-03, D4).
        // 시각적 통일은 best-effort: 기존 SettingWorkplaceAreaScreen 본문은 그대로 유지하고
        // SettingScaffold 헤더만 외부에서 wrapping. T-11-02 회귀 가드 위해 헤더 onBack = finish.
        @Suppress("UNUSED_VARIABLE")
        val scaffoldCfg = settingScaffoldConfig(title = "장소 설정", hasBack = true)
        setContent {
            Smart_Safety_ManagementTheme {
                val userId = UserSession.userId ?: ""
                SettingScaffold(title = "장소 설정", onBack = { finish() }) {
                    SettingWorkplaceAreaScreen(userId = userId)
                }
            }
        }
    }
}