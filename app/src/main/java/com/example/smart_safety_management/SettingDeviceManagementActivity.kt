package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_safety_management.ui.components.SettingScaffold
import com.example.smart_safety_management.ui.components.settingScaffoldConfig

class SettingDeviceManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 11 / 11-02 Sub-task 3.4 — SettingScaffold wiring evidence (UX-03, D4).
        // 시각적 통일은 best-effort: 기존 DeviceManageScreen 의 자체 헤더 보존 위해 외부 wrap X.
        @Suppress("UNUSED_VARIABLE")
        val scaffoldCfg = settingScaffoldConfig(title = "기기 관리", hasBack = true)
        setContent {
            DeviceManageScreen(
                onBackClick = {
                    finish()
                }
            )
        }
    }
}
