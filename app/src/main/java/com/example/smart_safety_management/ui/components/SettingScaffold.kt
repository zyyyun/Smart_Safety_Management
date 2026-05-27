package com.example.smart_safety_management.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

/**
 * Phase 11 / 11-02 Sub-task 3.3 (UX-03) — Setting* Compose 화면 헤더 공통화.
 *
 * settingScaffoldConfig(title, hasBack) 는 pure — UI 분기 없음.
 * @Composable SettingScaffold 는 ScreenScaffold 의 Setting* 특화 wrapper.
 *
 * D4 (A 옵션) 적용: XML Setting* 은 common_toolbar.xml include + setSupportActionBar 로,
 * Compose Setting* 은 본 Composable 로 헤더 통일.
 *
 * Compose 람다 early-exit 금지 (b2d8745 lesson) — ScreenScaffold delegate 사용.
 */

data class SettingScaffoldConfig(
    val title: String,
    val hasBack: Boolean,
)

fun settingScaffoldConfig(title: String, hasBack: Boolean): SettingScaffoldConfig =
    SettingScaffoldConfig(title = title, hasBack = hasBack)

@Composable
fun SettingScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // ScreenScaffold (Plan 11-01) 를 Setting* 화면용으로 위임.
    // hasBack 의 시각 분기는 ScreenScaffold 의 onBack null 여부로 결정.
    ScreenScaffold(title = title, onBack = onBack, content = content)
}
