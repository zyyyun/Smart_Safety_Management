package com.example.smart_safety_management.tbm

import androidx.compose.foundation.background
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
fun TbmWorkerCardComposable(
    groupId: Int,
    userId: String,
    supabase: SupabaseClient,
    onClickGuide: (Long) -> Unit,
) {
    var sessions by remember { mutableStateOf<List<TbmSessionRow>>(emptyList()) }
    var participants by remember { mutableStateOf<List<TbmParticipantRow>>(emptyList()) }
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

    val state = computeWorkerCardState(firstSession, participants, userId, sessions.size)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val target = firstSession
            if (target != null && state !is TbmWorkerCardState.NoSession) {
                onClickGuide(target.sessionId)
            }
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today TBM", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                TbmStatusBadge(state.label, state.color)
            }
            Spacer(Modifier.height(8.dp))
            firstSession?.let { session ->
                Text("${session.workScope} / ${session.workType}", fontSize = 13.sp, color = Color.Gray)
                Text("Expected end: ${formatTimeShort(session.expectedEndAt)}", fontSize = 13.sp, color = Color.Gray)
                if (sessions.size > 1) {
                    Text("${sessions.size} sessions today", fontSize = 12.sp, color = Color(0xFF2563EB))
                }
            } ?: Text("No TBM session started today", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

sealed class TbmWorkerCardState(val label: String, val color: Color) {
    object NoSession : TbmWorkerCardState("No session", Color.Gray)
    class NeedsCheckin(count: Int) : TbmWorkerCardState("Join needed ($count)", Color(0xFFF59E0B))
    object AlreadyJoined : TbmWorkerCardState("Joined", Color(0xFF22C55E))
    object Ended : TbmWorkerCardState("Ended", Color.Gray)
}

internal fun computeWorkerCardState(
    session: TbmSessionRow?,
    participants: List<TbmParticipantRow>,
    userId: String,
    sessionCount: Int = if (session == null) 0 else 1,
): TbmWorkerCardState {
    if (session == null) return TbmWorkerCardState.NoSession
    if (session.endedAt != null) return TbmWorkerCardState.Ended
    val joined = participants.any { it.userId == userId }
    return if (joined) TbmWorkerCardState.AlreadyJoined else TbmWorkerCardState.NeedsCheckin(sessionCount)
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

internal fun workTypeKorean(code: String): String = when (code) {
    "forklift" -> "Forklift"
    "chemical" -> "Chemical"
    "hot_work" -> "Hot work"
    else -> code.replace('_', ' ')
}

internal fun formatTimeShort(iso: String): String = try {
    val tIdx = iso.indexOf('T')
    if (tIdx in 0..iso.length - 6) iso.substring(tIdx + 1, tIdx + 6) else iso
} catch (e: Exception) {
    iso
}
