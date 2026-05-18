package com.example.smart_safety_management.tbm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
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

/**
 * Phase 9 / 09-03 TBM-02 — TbmWorkerActivity 의 메인 화면 (D-07).
 *
 * 구조:
 *   - 상단: 세션 정보 (work_type, leader, expected_end_at)
 *   - 중단: 체크리스트 LazyColumn (read-only — manager 입력 그대로 표시)
 *   - 하단:
 *     - 미참여: SignatureCanvas + "지우기" / "참여 확인" 버튼 → Storage 업로드 → checkin
 *     - 참여 완료: "✓ 참여 완료 {time}" + 버튼 disable
 *
 * Storage 업로드 (Option A, C3 amendment):
 *   path = "{session_id}/{user_id}_{epoch_ms}.png" (013 RLS key prefix 가드 충족)
 *   supabase.storage.from("tbm-signatures").upload(path, bytes)
 *
 * Phase 7 SafetyAlertsScreen 패턴 미러 + SignatureCanvas 추가.
 */
@Composable
fun TbmWorkerScreen(
    groupId: Int,
    userId: String,
    supabase: SupabaseClient,
    @Suppress("UNUSED_PARAMETER") sessionHintFromFcm: Long? = null,
) {
    val scope = rememberCoroutineScope()
    val api = remember { buildTbmFunctionsApi() }
    val signatureState = remember { SignatureState() }
    var session by remember { mutableStateOf<TbmSessionRow?>(null) }
    var participants by remember { mutableStateOf<List<TbmParticipantRow>>(emptyList()) }
    var checklists by remember { mutableStateOf<List<TbmChecklistRow>>(emptyList()) }
    var submitting by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    val repo = remember { TbmRepository(supabase) }

    // FCM extras 의 sessionHintFromFcm 은 신뢰 X — DB 재조회 (Phase 7 D-02 anti-pattern 회피)
    // sessionHintFromFcm 은 logging only / 향후 deep-link UX 개선용 hint.

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

    val s = session
    val myParticipation = participants.firstOrNull { it.userId == userId }
    val alreadyJoined = myParticipation != null
    val sessionEnded = s?.endedAt != null

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("TBM 가이드", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        if (s == null) {
            Text(
                "오늘 TBM 세션이 시작되지 않았습니다",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }

        // 세션 정보
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(workTypeKorean(s.workType), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("리더: ${s.leaderUserId}", fontSize = 13.sp, color = Color.Gray)
                Text("예정 종료: ${formatTimeShort(s.expectedEndAt)}", fontSize = 13.sp, color = Color.Gray)
                if (sessionEnded) {
                    Text(
                        "✓ 세션 종료됨 (${formatTimeShort(s.endedAt ?: "")})",
                        color = Color.Gray,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // 체크리스트 (read-only)
        Text("체크리스트", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            items(checklists) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (item.isChecked) "✓" else "○",
                        color = if (item.isChecked) Color(0xFF22C55E) else Color.Gray,
                        fontSize = 18.sp,
                    )
                    Text(
                        "  ${item.itemText}",
                        fontSize = 14.sp,
                        color = if (item.isChecked) Color.Black else Color.Gray,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 참여 영역
        if (alreadyJoined) {
            Text(
                "✓ 참여 완료 (${formatTimeShort(myParticipation!!.signedAt)})",
                color = Color(0xFF22C55E),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        } else if (sessionEnded) {
            Text(
                "세션이 종료되어 더 이상 참여할 수 없습니다",
                color = Color.Gray,
                fontSize = 14.sp,
            )
        } else {
            // 미참여 — SignatureCanvas + 참여 버튼
            Text("아래에 서명해주세요", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            SignatureCanvas(state = signatureState)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { signatureState.clear() },
                    modifier = Modifier.weight(1f),
                ) { Text("지우기") }
                Button(
                    onClick = {
                        if (signatureState.isEmpty) {
                            resultMsg = "서명이 비어 있습니다"
                            return@Button
                        }
                        submitting = true
                        scope.launch {
                            try {
                                val sessionId = s.sessionId
                                val timestamp = System.currentTimeMillis()
                                val path = "${sessionId}/${userId}_${timestamp}.png"
                                val bytes = signatureState.toPngBytes()

                                // Storage 업로드 (Option A, C3 amendment, T-9-01 mitigation)
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
                                            sessionId = sessionId,
                                            userId = userId,
                                            signatureUrl = path,
                                        ),
                                    )
                                    resultMsg = when {
                                        resp.isSuccessful && resp.body()?.ok == true ->
                                            if (resp.body()?.idempotent == true) "이미 참여한 세션입니다"
                                            else "✓ 참여 완료"
                                        resp.code() == 403 -> "권한 없음 (다른 그룹의 세션)"
                                        resp.code() == 404 -> "세션을 찾을 수 없습니다"
                                        resp.code() == 410 -> "세션이 이미 종료되었습니다"
                                        else -> "오류 (${resp.code()})"
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
                    Text(if (submitting) "처리 중..." else "참여 확인")
                }
            }
            resultMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = if (it.startsWith("✓")) Color(0xFF22C55E) else Color(0xFFEF4444),
                    fontSize = 13.sp,
                )
            }
        }
    }
}
