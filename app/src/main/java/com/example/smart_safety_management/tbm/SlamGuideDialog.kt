package com.example.smart_safety_management.tbm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 12 — TBM-06 SLAM 행동요령 안내 다이얼로그.
 *
 * 4 단계 (Stop / Look / Assess / Manage) — KOSHA 가이드의 핵심 행동패턴.
 * TbmStartSection 상단의 info icon 또는 TbmDashboardScreen 의 활성 세션 카드 상단에서 호출.
 */
@Composable
fun SlamGuideDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SLAM 행동요령", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SlamStep(
                    number = "1",
                    name = "Stop (멈춤)",
                    detail = "작업 시작 전·중 이상 신호를 느끼면 일단 멈춘다. 무리한 계속은 사고로 이어진다.",
                )
                Spacer(Modifier.height(12.dp))
                SlamStep(
                    number = "2",
                    name = "Look (관찰)",
                    detail = "주변 위험을 한 번 더 살핀다. 사람·장비·환경 (조도·바닥·기상) 모두 확인.",
                )
                Spacer(Modifier.height(12.dp))
                SlamStep(
                    number = "3",
                    name = "Assess (평가)",
                    detail = "발견한 위험의 영향과 대응 가능성을 판단한다. 본인 권한·역량 안에서 처리 가능한가?",
                )
                Spacer(Modifier.height(12.dp))
                SlamStep(
                    number = "4",
                    name = "Manage (조치)",
                    detail = "처리 가능하면 즉시 시정, 불가능하면 작업 중지 + 관리자/leader 보고. 절차 무시 금지.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인") }
        },
    )
}

@Composable
private fun SlamStep(number: String, name: String, detail: String) {
    Text("$number. $name", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Spacer(Modifier.height(4.dp))
    Text(detail, fontSize = 13.sp)
}
