package com.example.smart_safety_management.tbm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.launch

/**
 * Phase 9 / 09-03 TBM-02 — HomeWorkerActivity 의 TBM 카드 (D-07).
 *
 * 4 상태 (CONTEXT D-07):
 *   - 세션 없음 (오늘 group 의 tbm_sessions row 부재): 회색 "오늘 TBM 미시작" + 클릭 비활성
 *   - 세션 active + 본인 미참여: 노랑 "⚠ TBM 참여 필요 (예정 종료 {time})" + 클릭 → TbmWorkerActivity
 *   - 세션 active + 본인 참여 완료: 초록 "✓ TBM 참여 완료 {time}" + 클릭 → 참여 내역
 *   - 세션 종료: 회색 "오늘 TBM 종료 ({time})" + 클릭 → 참여 내역
 *
 * 데이터: TbmRepository.todaySessionFlow(groupId) Stage A + participantsFlow(sessionId) Stage B.
 * Phase 7 WatchCardComposable 의 status badge 3-색상 패턴 직접 미러 + 1 추가 상태.
 */
@Composable
fun TbmWorkerCardComposable(
    groupId: Int,
    userId: String,
    supabase: SupabaseClient,
    onClickGuide: (Long) -> Unit,
) {
    var session by remember { mutableStateOf<TbmSessionRow?>(null) }
    var participants by remember { mutableStateOf<List<TbmParticipantRow>>(emptyList()) }
    val repo = remember { TbmRepository(supabase) }

    // Stage A — 오늘 세션 구독 (groupId 키)
    LaunchedEffect(groupId) {
        repo.todaySessionFlow(groupId).collectLatest { session = it }
    }

    // Stage B — session_id 변경 시 participants 재구독 (dynamic session_id 패턴)
    LaunchedEffect(session?.sessionId) {
        val sid = session?.sessionId
        if (sid != null) {
            repo.participantsFlow(sid).collectLatest { participants = it }
        } else {
            participants = emptyList()
        }
    }

    val state = computeWorkerCardState(session, participants, userId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (state is TbmWorkerCardState.NeedsCheckin || state is TbmWorkerCardState.AlreadyJoined ||
                state is TbmWorkerCardState.Ended) {
                session?.sessionId?.let { onClickGuide(it) }
            }
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("오늘 TBM", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                TbmStatusBadge(state.label, state.color)
            }
            Spacer(Modifier.height(8.dp))
            session?.let { s ->
                Text(
                    "작업유형: ${workTypeKorean(s.workType)}",
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
                Text(
                    "예정 종료: ${formatTimeShort(s.expectedEndAt)}",
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
            } ?: Text("관리자의 TBM 세션 시작을 대기 중", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

sealed class TbmWorkerCardState(val label: String, val color: Color) {
    object NoSession : TbmWorkerCardState("세션 없음", Color.Gray)
    object NeedsCheckin : TbmWorkerCardState("⚠ 참여 필요", Color(0xFFFBBF24))
    object AlreadyJoined : TbmWorkerCardState("✓ 참여 완료", Color(0xFF22C55E))
    object Ended : TbmWorkerCardState("종료됨", Color.Gray)
}

internal fun computeWorkerCardState(
    session: TbmSessionRow?,
    participants: List<TbmParticipantRow>,
    userId: String,
): TbmWorkerCardState {
    if (session == null) return TbmWorkerCardState.NoSession
    if (session.endedAt != null) return TbmWorkerCardState.Ended
    val joined = participants.any { it.userId == userId }
    return if (joined) TbmWorkerCardState.AlreadyJoined else TbmWorkerCardState.NeedsCheckin
}

@Composable
internal fun TbmStatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * work_type code → 한글 label. v1.0 한정 하드코딩 (tbm_templates.title 이 실 source).
 * v1.1 에서 fetchTemplates() 의 title 사용.
 */
internal fun workTypeKorean(code: String): String = when (code) {
    "fire"     -> "화재 위험 작업"
    "electric" -> "전기 작업"
    "height"   -> "고소 작업"
    "heavy"    -> "중량물 취급"
    "general"  -> "일반 작업"
    else       -> code
}

/**
 * "2026-05-18T09:15:00+09:00" → "09:15" (시:분 시제만 표시).
 * naive parsing — Pitfall 8 의 Validator 와 분리, 표시용.
 */
internal fun formatTimeShort(iso: String): String = try {
    // ISO 의 'T' 뒤 5자 (HH:mm) 만 표시
    val tIdx = iso.indexOf('T')
    if (tIdx in 0..iso.length - 6) iso.substring(tIdx + 1, tIdx + 6) else iso
} catch (e: Exception) {
    iso
}
