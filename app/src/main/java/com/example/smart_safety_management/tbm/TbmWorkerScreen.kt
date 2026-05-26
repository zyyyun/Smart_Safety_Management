package com.example.smart_safety_management.tbm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("TBM 작업자 가이드", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        if (sessions.isEmpty()) {
            Text("오늘 시작된 TBM 세션이 없습니다.", fontSize = 14.sp, color = Color.Gray)
            return@Column
        }

        Text("오늘의 세션", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        sessions.forEach { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { selectedSessionId = session.sessionId },
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(session.workScope, fontWeight = FontWeight.Bold)
                    Text("${session.workType} / ${formatTimeShort(session.expectedEndAt)}", fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        val s = selectedSession ?: return@Column

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(s.workScope, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("OPS: ${s.workType}", fontSize = 13.sp, color = Color.Gray)
                Text("리더: ${s.leaderUserId}", fontSize = 13.sp, color = Color.Gray)
                Text("예상 종료: ${formatTimeShort(s.expectedEndAt)}", fontSize = 13.sp, color = Color.Gray)
                if (sessionEnded) Text("종료됨: ${formatTimeShort(s.endedAt ?: "")}", color = Color.Gray)
            }
        }

        Text("위험요인", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        s.hazardsSnapshot.forEach { Text("- ${it.text}", fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))

        Text("점검 항목", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        checklists.forEach { item -> ChecklistRow(item = item, repo = repo, scope = scope) }
        Spacer(Modifier.height(12.dp))

        if (alreadyJoined) {
            Text("참여 완료 (${formatTimeShort(myParticipation!!.signedAt)})", color = Color(0xFF22C55E))
        } else if (sessionEnded) {
            Text("이미 종료된 세션입니다.", color = Color.Gray)
        } else {
            Text("아래에 서명하세요", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            SignatureCanvas(state = signatureState)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { signatureState.clear() }, modifier = Modifier.weight(1f)) {
                    Text("지우기")
                }
                Button(
                    onClick = {
                        if (signatureState.isEmpty) {
                            resultMsg = "서명이 비어있습니다"
                            return@Button
                        }
                        submitting = true
                        scope.launch {
                            try {
                                val timestamp = System.currentTimeMillis()
                                val path = "${s.sessionId}/${userId}_${timestamp}.png"
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
                                            sessionId = s.sessionId,
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
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (submitting) "참여 중..." else "참여하기")
                }
            }
            resultMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFF2563EB), fontSize = 13.sp)
            }
        }
    }
}
