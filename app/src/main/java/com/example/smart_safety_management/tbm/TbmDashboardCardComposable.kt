package com.example.smart_safety_management.tbm

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

@Composable
fun TbmDashboardCardComposable(
    groupId: Int,
    supabase: SupabaseClient,
    onClickDashboard: () -> Unit,
) {
    var sessions by remember { mutableStateOf<List<TbmSessionRow>>(emptyList()) }
    var participants by remember { mutableStateOf<List<TbmParticipantRow>>(emptyList()) }
    var checklists by remember { mutableStateOf<List<TbmChecklistRow>>(emptyList()) }
    val repo = remember { TbmRepository(supabase) }
    val firstSession = sessions.firstOrNull()

    LaunchedEffect(groupId) {
        repo.todaySessionFlow(groupId).collectLatest { sessions = it }
    }
    LaunchedEffect(firstSession?.sessionId) {
        val sid = firstSession?.sessionId
        if (sid != null) repo.participantsFlow(sid).collectLatest { participants = it }
        else participants = emptyList()
    }
    LaunchedEffect(firstSession?.sessionId) {
        val sid = firstSession?.sessionId
        if (sid != null) repo.checklistsFlow(sid).collectLatest { checklists = it }
        else checklists = emptyList()
    }

    val state = computeDashboardCardState(firstSession, checklists)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClickDashboard,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("오늘의 TBM", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                TbmStatusBadge(state.label, state.color)
            }
            Spacer(Modifier.height(8.dp))
            firstSession?.let { session ->
                Text("${session.workScope} / ${workTypeKorean(session.workType)}", fontSize = 13.sp, color = Color.Gray)
                Text("참여자: ${participants.size}명", fontSize = 13.sp, color = Color.Gray)
                if (sessions.size > 1) {
                    Text("오늘 ${sessions.size}개 세션", fontSize = 12.sp, color = Color(0xFF2563EB))
                }
                if (session.missedAlertAt != null) {
                    Text("미참여 알림 발송 ${formatTimeShort(session.missedAlertAt)}", color = Color(0xFFEF4444))
                }
            } ?: Text("탭하여 TBM 시작", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

sealed class TbmDashboardCardState(val label: String, val color: Color) {
    object NoSession : TbmDashboardCardState("세션 없음", Color.Gray)
    class InProgress(checked: Int, total: Int) :
        TbmDashboardCardState("진행중 ($checked/$total)", Color(0xFFF59E0B))
    object Completed : TbmDashboardCardState("종료", Color(0xFF22C55E))
    object MissedAlertSent : TbmDashboardCardState("미참여 알림", Color(0xFFEF4444))
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
