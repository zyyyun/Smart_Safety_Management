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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Phase 9 / 09-03 TBM-02 — TbmDashboardActivity 의 메인 화면.
 *
 * 2026-05-20 Change 1+2 (plan tbm-linear-dragonfly):
 *   - 다중 그룹 dashboard: fetchGroupsForManager + todaySessionsFlow(groupIds)
 *     → 그룹별 세션 카드 N개 동시 렌더 + inline 체크리스트 toggle
 *   - 체크리스트 "추가 작업 사항" row 분기: item_text == FREETEXT_ITEM_TEXT 면
 *     OutlinedTextField (note 컬럼 PATCH) + Checkbox. 500ms debounce.
 *   - is_checked / note 모두 supabase-kt 직접 PATCH (014 의 UPDATE RLS 통과).
 *
 * 구조:
 *   1. 상단: TbmStartSection (다중 그룹 폼)
 *   2. 하단: 그룹별 GroupSessionCard 컬럼
 *      - 세션 없음 그룹 → "오늘 세션 없음" + 폼으로 안내
 *      - 세션 active → 체크리스트 (collapsible) + 참여자 + 세션 종료 버튼
 */
private const val FREETEXT_ITEM_TEXT = "추가 작업 사항"

@Composable
fun TbmDashboardScreen(
    leaderUserId: String,
    supabase: SupabaseClient,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { TbmRepository(supabase) }

    var groups by remember { mutableStateOf<List<GroupRow>>(emptyList()) }
    var sessions by remember { mutableStateOf<Map<Int, TbmSessionRow?>>(emptyMap()) }

    LaunchedEffect(leaderUserId) {
        groups = runCatching { repo.fetchGroupsForManager(leaderUserId) }.getOrElse { emptyList() }
    }
    LaunchedEffect(groups.map { it.groupId }) {
        if (groups.isEmpty()) {
            sessions = emptyMap()
            return@LaunchedEffect
        }
        repo.todaySessionsFlow(groups.map { it.groupId }).collectLatest { sessions = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("TBM 대시보드", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        // ── 폼 (다중 그룹 생성) ──────────────────────────────────────────
        TbmStartSection(
            leaderUserId = leaderUserId,
            supabase = supabase,
            onSubmitted = { /* Realtime 으로 자동 갱신 */ },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ── 오늘 세션 목록 ───────────────────────────────────────────────
        Text("오늘 세션 목록 (${sessions.values.count { it != null }}/${groups.size} 그룹)",
             fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        if (groups.isEmpty()) {
            Text("그룹 로드 중...", fontSize = 13.sp, color = Color(0xFF6B7280))
        } else {
            groups.forEach { g ->
                val s = sessions[g.groupId]
                GroupSessionCard(
                    group = g,
                    session = s,
                    leaderUserId = leaderUserId,
                    repo = repo,
                    scope = scope,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 한 그룹의 세션 카드 — 헤더 + (있으면) 체크리스트/참여자/종료 버튼.
 * 펼침/접힘 토글 (default 펼침).
 */
@Composable
private fun GroupSessionCard(
    group: GroupRow,
    session: TbmSessionRow?,
    leaderUserId: String,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
    var expanded by remember(group.groupId) { mutableStateOf(true) }
    var participants by remember(session?.sessionId) { mutableStateOf<List<TbmParticipantRow>>(emptyList()) }
    var checklists by remember(session?.sessionId) { mutableStateOf<List<TbmChecklistRow>>(emptyList()) }
    var endResultMsg by remember(session?.sessionId) { mutableStateOf<String?>(null) }
    var ending by remember(session?.sessionId) { mutableStateOf(false) }
    val api = remember { buildTbmFunctionsApi() }

    LaunchedEffect(session?.sessionId, expanded) {
        val sid = session?.sessionId
        if (sid != null && expanded) {
            repo.participantsFlow(sid).collectLatest { participants = it }
        } else {
            participants = emptyList()
        }
    }
    LaunchedEffect(session?.sessionId, expanded) {
        val sid = session?.sessionId
        if (sid != null && expanded) {
            repo.checklistsFlow(sid).collectLatest { checklists = it }
        } else {
            checklists = emptyList()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 헤더 — 클릭으로 펼침/접힘
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (expanded) "▼" else "▶", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "#${group.groupId} (${group.inviteCode})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    if (session == null) {
                        Text("오늘 세션 없음", fontSize = 12.sp, color = Color(0xFF6B7280))
                    } else {
                        val statusTxt = if (session.endedAt != null) "종료" else "진행 중"
                        val checked = checklists.count { it.isChecked }
                        val total = checklists.size
                        Text(
                            "${workTypeKorean(session.workType)} · $statusTxt" +
                                if (total > 0) " · ✓ $checked/$total" else "",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                }
            }

            if (expanded && session != null) {
                Spacer(Modifier.height(8.dp))
                // 세션 메타 ────────────────────────────────────────
                Text("리더: ${session.leaderUserId}", fontSize = 12.sp, color = Color.Gray)
                Text("예정 종료: ${formatTimeShort(session.expectedEndAt)}",
                     fontSize = 12.sp, color = Color.Gray)
                session.location?.let { Text("위치: $it", fontSize = 12.sp, color = Color.Gray) }
                if (session.missedAlertAt != null) {
                    Text(
                        "⚠ 미참여 알림 발송됨 (${formatTimeShort(session.missedAlertAt)})",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))

                // 체크리스트 ──────────────────────────────────────
                Text(
                    "체크리스트 (${checklists.count { it.isChecked }}/${checklists.size})",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                checklists.forEach { item ->
                    ChecklistRow(item = item, repo = repo, scope = scope)
                }
                Spacer(Modifier.height(8.dp))

                // 참여자 ──────────────────────────────────────────
                if (participants.isNotEmpty()) {
                    Text("참여자 (${participants.size}명)",
                         fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    participants.forEach { p ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(p.userId, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(8.dp))
                            Text("(${formatTimeShort(p.signedAt)})",
                                 fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 세션 종료 버튼 ──────────────────────────────────
                if (session.endedAt == null) {
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
                                            sessionId = session.sessionId,
                                            leaderUserId = leaderUserId,
                                        ),
                                    )
                                    endResultMsg = when {
                                        resp.isSuccessful && resp.body()?.ok == true ->
                                            "✓ 세션 종료됨 (참여 ${resp.body()?.participantCount ?: 0}명)"
                                        resp.code() == 404 -> "리더 권한 없음 또는 이미 종료"
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            color = if (it.startsWith("✓")) Color(0xFF22C55E) else Color(0xFFEF4444),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 체크리스트 단일 row.
 *
 * - item_text == "추가 작업 사항" 이면 OutlinedTextField (note 직접 입력) + Checkbox
 *   - 500ms debounce 로 PATCH 폭주 방지
 * - 그 외에는 Text(item_text) + Checkbox
 *
 * 두 경우 모두 Checkbox onCheckedChange → updateChecklistItem(isChecked=...).
 */
@Composable
fun ChecklistRow(
    item: TbmChecklistRow,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
    val isFreetext = item.itemText == FREETEXT_ITEM_TEXT

    // Local checkbox state — server 응답 (Realtime) 으로 자동 reset.
    val isChecked = item.isChecked

    if (isFreetext) {
        // 자유 입력 row — note 컬럼 PATCH (debounced)
        var draft by remember(item.checklistId) { mutableStateOf(item.note ?: "") }
        // Realtime 으로 note 가 바뀌면 local draft 도 갱신 (다른 디바이스가 수정한 경우)
        LaunchedEffect(item.note) {
            if ((item.note ?: "") != draft) {
                draft = item.note ?: ""
            }
        }
        // Debounced PATCH — 마지막 입력 후 500ms 멈춤 시 send
        LaunchedEffect(draft) {
            if (draft != (item.note ?: "")) {
                delay(500)
                runCatching { repo.updateChecklistItem(item.checklistId, note = draft) }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { newChecked ->
                        scope.launch {
                            runCatching { repo.updateChecklistItem(item.checklistId, isChecked = newChecked) }
                        }
                    },
                )
                Text(
                    "추가 작업 사항",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF3B82F6),
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("자유 입력 (admin/워커 누구나 작성)") },
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
            )
        }
    } else {
        // 정형 row — 정적 텍스트 + 체크박스
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { newChecked ->
                    scope.launch {
                        runCatching { repo.updateChecklistItem(item.checklistId, isChecked = newChecked) }
                    }
                },
            )
            Text(
                item.itemText,
                fontSize = 13.sp,
                color = if (isChecked) Color.Black else Color(0xFF6B7280),
            )
        }
    }
}
