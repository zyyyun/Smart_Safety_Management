package com.example.smart_safety_management.tbm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.collectLatest

/**
 * Phase 9 / 09-03 TBM-02 — HomeActivity 의 manager TBM 대시보드 카드 (D-06).
 *
 * 3줄 layout (CONTEXT D-06):
 *   (a) 상태 badge: "세션 없음" 회색 / "진행 중 ({checked}/{total})" 노랑 / "완료" 초록
 *                   / "⚠ 미참여 알림 발사됨" 빨강
 *   (b) 참여 카운트: "{참여}/{대상}" (예: "5/8 명") — 대상은 본 plan 에선 참여 수만 (대상은
 *                   tbm-missed 호출 시점에서만 계산, 카드에선 참여수 표시).
 *   (c) 미참여 알림 상태: missed_alert_at NOT NULL 시 "⚠ 미참여 알림 발송됨 {time}"
 *
 * 클릭 시 onClickDashboard() — TbmDashboardActivity 진입.
 *
 * Phase 7 WatchCardComposable 직접 미러 + 3 색상 badge + 추가 1색상.
 */
@Composable
fun TbmDashboardCardComposable(
    groupId: Int,
    supabase: SupabaseClient,
    onClickDashboard: () -> Unit,
) {
    var session by remember { mutableStateOf<TbmSessionRow?>(null) }
    var participants by remember { mutableStateOf<List<TbmParticipantRow>>(emptyList()) }
    var checklists by remember { mutableStateOf<List<TbmChecklistRow>>(emptyList()) }
    val repo = remember { TbmRepository(supabase) }

    LaunchedEffect(groupId) {
        repo.todaySessionFlow(groupId).collectLatest { session = it }
    }
    LaunchedEffect(session?.sessionId) {
        val sid = session?.sessionId
        if (sid != null) {
            repo.participantsFlow(sid).collectLatest { participants = it }
        } else {
            participants = emptyList()
        }
    }
    LaunchedEffect(session?.sessionId) {
        val sid = session?.sessionId
        if (sid != null) {
            repo.checklistsFlow(sid).collectLatest { checklists = it }
        } else {
            checklists = emptyList()
        }
    }

    val state = computeDashboardCardState(session, checklists)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClickDashboard,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("오늘 TBM 현황", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                TbmStatusBadge(state.label, state.color)
            }
            Spacer(Modifier.height(8.dp))
            session?.let { s ->
                Text(
                    "작업유형: ${workTypeKorean(s.workType)}  ·  예정 종료: ${formatTimeShort(s.expectedEndAt)}",
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "참여: ${participants.size}명",
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
                if (s.missedAlertAt != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠ 미참여 알림 발송됨 (${formatTimeShort(s.missedAlertAt)})",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                    )
                }
            } ?: Text("탭하여 오늘 TBM 세션 시작", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

sealed class TbmDashboardCardState(val label: String, val color: Color) {
    object NoSession : TbmDashboardCardState("세션 없음", Color.Gray)
    class InProgress(checked: Int, total: Int) :
        TbmDashboardCardState("진행 중 ($checked/$total)", Color(0xFFFBBF24))
    object Completed : TbmDashboardCardState("완료", Color(0xFF22C55E))
    object MissedAlertSent : TbmDashboardCardState("⚠ 미참여 알림 발사됨", Color(0xFFEF4444))
}

internal fun computeDashboardCardState(
    session: TbmSessionRow?,
    checklists: List<TbmChecklistRow>,
): TbmDashboardCardState {
    if (session == null) return TbmDashboardCardState.NoSession
    if (session.endedAt != null) return TbmDashboardCardState.Completed
    if (session.missedAlertAt != null) return TbmDashboardCardState.MissedAlertSent
    val checked = checklists.count { it.isChecked }
    val total = checklists.size
    return TbmDashboardCardState.InProgress(checked, total)
}
