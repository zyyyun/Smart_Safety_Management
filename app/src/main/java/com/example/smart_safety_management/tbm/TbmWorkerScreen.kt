package com.example.smart_safety_management.tbm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.BuildConfig
import com.example.smart_safety_management.ui.SsmColors
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun TbmWorkerScreen(
    groupId: Int,
    userId: String,
    supabase: SupabaseClient,
    sessionHintFromFcm: Long? = null,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { TbmRepository(supabase) }
    val api = remember { buildTbmFunctionsApi() }
    val signatureState = remember { SignatureState() }

    var sessions by remember { mutableStateOf<List<TbmSessionRow>>(emptyList()) }
    var selectedSessionId by remember(sessionHintFromFcm) { mutableStateOf(sessionHintFromFcm) }
    var participants by remember { mutableStateOf<List<TbmParticipantRow>>(emptyList()) }
    var checklists by remember { mutableStateOf<List<TbmChecklistRow>>(emptyList()) }
    var submitting by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(groupId) {
        repo.todaySessionFlow(groupId).collectLatest { todaySessions ->
            sessions = todaySessions
            if (selectedSessionId == null || todaySessions.none { it.sessionId == selectedSessionId }) {
                selectedSessionId = todaySessions.firstOrNull()?.sessionId
            }
        }
    }

    val selectedSession = sessions.firstOrNull { it.sessionId == selectedSessionId }

    LaunchedEffect(selectedSession?.sessionId) {
        val sid = selectedSession?.sessionId
        if (sid != null) repo.participantsFlow(sid).collectLatest { participants = it }
        else participants = emptyList()
    }
    LaunchedEffect(selectedSession?.sessionId) {
        val sid = selectedSession?.sessionId
        if (sid != null) repo.checklistsFlow(sid).collectLatest { checklists = it }
        else checklists = emptyList()
    }

    val myParticipation = participants.firstOrNull { it.userId == userId }
    val alreadyJoined = myParticipation != null
    val sessionEnded = selectedSession?.endedAt != null
    val canSubmit = selectedSession != null && !alreadyJoined && !sessionEnded

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (canSubmit) 230.dp else 24.dp),
        ) {
            Text("TBM 작업자 가이드", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(14.dp))

            if (sessions.isEmpty()) {
                Text("오늘 시작된 TBM 세션이 없습니다.", fontSize = 15.sp, color = Color.Gray)
            } else {
                WorkerSessionPicker(
                    sessions = sessions,
                    selectedSessionId = selectedSessionId,
                    onSelect = { selectedSessionId = it },
                )
                Spacer(Modifier.height(12.dp))

                val session = selectedSession
                if (session == null) {
                    Text("선택된 TBM 세션이 없습니다.", fontSize = 15.sp, color = Color.Gray)
                } else {
                    WorkerSessionSummary(
                        session = session,
                        participantCount = participants.size,
                        alreadyJoined = alreadyJoined,
                    )
                    Spacer(Modifier.height(12.dp))

                    WorkerGroupedSnapshotList(
                        title = "위험요인",
                        grouped = groupByOpsTitle(session.hazardsSnapshot) { it.opsTitle },
                        textOf = { it.text },
                    )
                    Spacer(Modifier.height(12.dp))

                    WorkerGroupedSnapshotList(
                        title = "조치",
                        grouped = groupByOpsTitle(session.controlsSnapshot) { it.opsTitle },
                        textOf = { it.text },
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("자가 항목", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    WorkerGroupedChecklistList(
                        grouped = groupByOpsTitle(checklists.map(::checklistDisplayItem)) { it.opsTitle },
                        repo = repo,
                        scope = scope,
                    )

                    if (alreadyJoined) {
                        Spacer(Modifier.height(16.dp))
                        Text("참여 완료 (${formatTimeShort(myParticipation!!.signedAt)})", color = SsmColors.SuccessGreen, fontSize = 15.sp)
                    } else if (sessionEnded) {
                        Spacer(Modifier.height(16.dp))
                        Text("이미 종료된 세션입니다.", color = Color.Gray, fontSize = 15.sp)
                    }

                    resultMsg?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = SsmColors.TextInfo, fontSize = 14.sp)
                    }
                }
            }
        }

        if (canSubmit) {
            WorkerSubmitPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                signatureState = signatureState,
                submitting = submitting,
                resultMsg = resultMsg,
                onClear = { signatureState.clear() },
                onSubmit = {
                    val session = selectedSession ?: return@WorkerSubmitPanel
                    if (signatureState.isEmpty) {
                        resultMsg = "서명이 비어있습니다"
                        return@WorkerSubmitPanel
                    }
                    submitting = true
                    scope.launch {
                        try {
                            val timestamp = System.currentTimeMillis()
                            val path = "${session.sessionId}/${userId}_${timestamp}.png"
                            val bytes = signatureState.toPngBytes()

                            val uploadOk = try {
                                supabase.storage.from("tbm-signatures")
                                    .upload(path = path, data = bytes, upsert = false)
                                true
                            } catch (e: Exception) {
                                resultMsg = "서명 업로드 실패: ${e.message}"
                                false
                            }

                            if (uploadOk) {
                                val resp = api.callTbmCheckin(
                                    url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                    auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                    body = TbmCheckinRequest(
                                        sessionId = session.sessionId,
                                        userId = userId,
                                        signatureUrl = path,
                                    ),
                                )
                                resultMsg = when {
                                    resp.isSuccessful && resp.body()?.ok == true ->
                                        if (resp.body()?.idempotent == true) "이미 참여함" else "참여 완료"
                                    resp.code() == 403 -> "다른 그룹의 세션"
                                    resp.code() == 404 -> "세션을 찾을 수 없음"
                                    resp.code() == 410 -> "이미 종료된 세션"
                                    else -> "오류 ${resp.code()}"
                                }
                            }
                        } catch (e: Exception) {
                            resultMsg = "네트워크 오류: ${e.message}"
                        } finally {
                            submitting = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun WorkerSessionPicker(
    sessions: List<TbmSessionRow>,
    selectedSessionId: Long?,
    onSelect: (Long) -> Unit,
) {
    Text("오늘의 세션", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    Spacer(Modifier.height(6.dp))
    sessions.forEach { session ->
        val selected = session.sessionId == selectedSessionId
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onSelect(session.sessionId) },
            border = if (selected) BorderStroke(2.dp, SsmColors.ActiveOrange) else null,
            colors = CardDefaults.cardColors(containerColor = if (selected) SsmColors.EndedBg else Color.White),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(tbmSessionDisplayTitle(session), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("예상 종료 ${formatTimeShort(session.expectedEndAt)}", fontSize = 14.sp, color = SsmColors.TextMuted)
            }
        }
    }
}

@Composable
private fun WorkerSessionSummary(
    session: TbmSessionRow,
    participantCount: Int,
    alreadyJoined: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SsmColors.EndedBg),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(tbmSessionDisplayTitle(session), fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("리더: ${session.leaderUserId}", fontSize = 14.sp, color = SsmColors.TextMuted)
            Text("예상 종료: ${formatTimeShort(session.expectedEndAt)}", fontSize = 14.sp, color = SsmColors.TextMuted)
            Text(
                "${if (alreadyJoined) "참여 완료" else "참여 전"} · 현재 참여자 ${participantCount}명",
                fontSize = 14.sp,
                color = if (alreadyJoined) SsmColors.SuccessGreen else SsmColors.ActiveOrange,
            )
        }
    }
}

@Composable
private fun <T> WorkerGroupedSnapshotList(
    title: String,
    grouped: List<OpsTitleGroup<T>>,
    textOf: (T) -> String,
) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    if (grouped.isEmpty()) {
        Text("없음", fontSize = 14.sp, color = SsmColors.TextMuted)
    } else {
        val hasOpsMetadata = grouped.any { it.opsTitle != null }
        grouped.forEach { group ->
            if (hasOpsMetadata) {
                Text(
                    group.opsTitle ?: "기타",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = SsmColors.TextInfo,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            group.items.forEach { item ->
                Text(
                    "- ${textOf(item)}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = if (hasOpsMetadata) 8.dp else 0.dp, top = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkerGroupedChecklistList(
    grouped: List<OpsTitleGroup<ChecklistDisplayItem>>,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
    if (grouped.isEmpty()) {
        Text("없음", fontSize = 14.sp, color = SsmColors.TextMuted)
    } else {
        val hasOpsMetadata = grouped.any { it.opsTitle != null }
        grouped.forEach { group ->
            if (hasOpsMetadata) {
                Text(
                    group.opsTitle ?: "기타",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = SsmColors.TextInfo,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            group.items.forEach { item ->
                WorkerChecklistRow(
                    item = item.row,
                    displayText = item.displayText,
                    repo = repo,
                    scope = scope,
                    indent = if (hasOpsMetadata) 8.dp else 0.dp,
                )
            }
        }
    }
}

@Composable
private fun WorkerChecklistRow(
    item: TbmChecklistRow,
    displayText: String,
    repo: TbmRepository,
    scope: CoroutineScope,
    indent: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = indent, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { checked ->
                scope.launch { runCatching { repo.updateChecklistItem(item.checklistId, isChecked = checked) } }
            },
        )
        Text(displayText, fontSize = 15.sp)
    }
}

@Composable
private fun WorkerSubmitPanel(
    modifier: Modifier = Modifier,
    signatureState: SignatureState,
    submitting: Boolean,
    resultMsg: String?,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SsmColors.TextMuted.copy(alpha = 0.18f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("서명", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            SignatureCanvas(state = signatureState)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f), enabled = !submitting) {
                    Text("지우기")
                }
                Button(onClick = onSubmit, modifier = Modifier.weight(1f), enabled = !submitting) {
                    Text(if (submitting) "제출 중..." else "참여 제출")
                }
            }
            resultMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = SsmColors.TextInfo, fontSize = 13.sp)
            }
        }
    }
}
