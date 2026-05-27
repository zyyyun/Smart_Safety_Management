package com.example.smart_safety_management.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 11 / 11-01 — Compose 진입점 통일을 위한 scaffold config.
 *
 * - title: 화면 제목 (헤더 우측 표시). 빈 문자열도 그대로 보존.
 * - showBack: 좌측 뒤로가기 표시 여부. onBack 의 null 여부로 결정.
 */
data class ScaffoldConfig(
    val title: String,
    val showBack: Boolean,
)

/**
 * Pure builder: scaffoldConfig(title, onBack) → ScaffoldConfig.
 * Plan 11-02 가 28+ 화면에서 일괄 호출.
 *
 * onBack == null → showBack=false (Home 같은 진입점)
 * onBack != null → showBack=true  (Setting/Detail 같은 sub 화면)
 */
fun scaffoldConfig(title: String, onBack: (() -> Unit)?): ScaffoldConfig =
    ScaffoldConfig(title = title, showBack = onBack != null)

/**
 * 공통 ScreenScaffold Composable — Compose 진입점 시각 통일.
 *
 * 시각 사양 (Quick #03 TbmDashboardScreen 헤더 패턴 기준):
 *   - 헤더 Row: Icon size 24.dp (back) + Spacer 8.dp + Text fontWeight=Bold fontSize=22.sp
 *   - 본문 Column(verticalScroll(rememberScrollState()).padding(16.dp))
 *   - actions 슬롯: 우측 Row 에 placement (예: 새로고침/설정 IconButton)
 *
 * Compose 람다 early-exit 금지 (b2d8745 lesson) — if/else 양분 패턴 강제.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로가기",
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            actions()
        }
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}
