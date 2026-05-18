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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Phase 9 / 09-03 TBM-02 — TbmDashboardActivity 의 메인 화면 (D-06).
 *
 * 구조:
 *   - 세션 없음 → TbmStartSection (시작 폼)
 *   - 세션 active → 세션 정보 + 체크리스트 LazyColumn + 참여자 grid + "세션 종료" 버튼
 *   - 세션 ended → 세션 정보 + 참여자 grid (read-only)
 *
 * Realtime 3 채널 구독 (Stage A + Stage B 둘) + 권한 검사는 Activity onCreate 에서 처리.
 *
 * Phase 7 SafetyAlertsScreen 패턴 미러 + section 분리.
 */
@Composable
fun TbmDashboardScreen(
    leaderUserId: String,
    groupId: Int,
    supabase: SupabaseClient,
) {
    val scope = rememberCoroutineScope()
    val api = remember { buildTbmFunctionsApi() }
    var session by remember { mutableStateOf<TbmSessionRow?>(null) }
    var participants by remember { mutableStateOf<List<TbmParticipantRow>>(emptyList()) }
    var checklists by remember { mutableStateOf<List<TbmChecklistRow>>(emptyList()) }
    var endResultMsg by remember { mutableStateOf<String?>(null) }
    var ending by remember { mutableStateOf(false) }
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("TBM 대시보드", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        val s = session
        if (s == null) {
            // 세션 없음 — 시작 폼 표시
            TbmStartSection(
                leaderUserId = leaderUserId,
                groupId = groupId,
                supabase = supabase,
                onStarted = { /* Realtime 으로 자동 갱신 */ },
            )
        } else {
            // 세션 active 또는 ended
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${workTypeKorean(s.workType)}  (${if (s.endedAt != null) "종료" else "진행 중"})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Text("리더: ${s.leaderUserId}", fontSize = 13.sp, color = Color.Gray)
                    Text("예정 종료: ${formatTimeShort(s.expectedEndAt)}", fontSize = 13.sp, color = Color.Gray)
                    s.location?.let { Text("위치: $it", fontSize = 13.sp, color = Color.Gray) }
                    if (s.missedAlertAt != null) {
                        Text(
                            "⚠ 미참여 알림 발송됨 (${formatTimeShort(s.missedAlertAt)})",
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Text("체크리스트 (${checklists.count { it.isChecked }}/${checklists.size})",
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                items(checklists) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            if (item.isChecked) "✓" else "○",
                            color = if (item.isChecked) Color(0xFF22C55E) else Color.Gray,
                            fontSize = 18.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "  ${item.itemText}",
                            fontSize = 14.sp,
                            color = if (item.isChecked) Color.Black else Color.Gray,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Text("참여자 (${participants.size}명)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                items(participants) { p ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(p.userId, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("참여 ${formatTimeShort(p.signedAt)}", fontSize = 12.sp, color = Color.Gray)
                            if (p.signatureUrl != null) {
                                Text(
                                    "서명: ${p.signatureUrl}",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                )
                                // v1.0: signature thumbnail / signed URL 모달은 v1.1
                                // (storage signed URL 60s 만료 + AsyncImage cache key 30s)
                            }
                        }
                    }
                }
            }

            if (s.endedAt == null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        ending = true
                        scope.launch {
                            try {
                                val resp = api.callTbmEnd(
                                    url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                    auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                    body = TbmEndRequest(
                                        sessionId = s.sessionId,
                                        leaderUserId = leaderUserId,
                                    ),
                                )
                                endResultMsg = when {
                                    resp.isSuccessful && resp.body()?.ok == true ->
                                        "✓ 세션 종료됨 (참여 ${resp.body()?.participantCount ?: 0}명)"
                                    resp.code() == 404 -> "리더 권한이 없거나 이미 종료된 세션"
                                    else -> "오류 (${resp.code()})"
                                }
                            } catch (e: Exception) {
                                endResultMsg = "네트워크 오류: ${e.message}"
                            } finally {
                                ending = false
                            }
                        }
                    },
                    enabled = !ending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (ending) "종료 중..." else "세션 종료")
                }
                endResultMsg?.let {
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
}
