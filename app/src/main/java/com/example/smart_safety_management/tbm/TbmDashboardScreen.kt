package com.example.smart_safety_management.tbm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

private const val FREETEXT_ITEM_TEXT = "추가 작업 사항"

// Phase 12 의 카드 상태 톤과 일관:
//   진행중 (active)  → orange #F59E0B (Phase 12 InProgress)
//   종료 (ended)     → 회색 #F3F4F6 배경 + #6B7280 본문
//   미참여 알림 발송 → red #EF4444 (MissedAlertSent — 본 화면에서는 본문 안 별도 표시)
private val COLOR_ACTIVE_ORANGE = Color(0xFFF59E0B)
private val COLOR_ENDED_BG = Color(0xFFF3F4F6)
private val COLOR_TEXT_MUTED = Color(0xFF6B7280)
private val COLOR_TEXT_INFO = Color(0xFF2563EB)
private val COLOR_TEXT_DANGER = Color(0xFFEF4444)

@Composable
fun TbmDashboardScreen(
    leaderUserId: String,
    supabase: SupabaseClient,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { TbmRepository(supabase) }

    var groups by remember { mutableStateOf<List<GroupRow>>(emptyList()) }
    var sessionsByGroup by remember { mutableStateOf<Map<Int, List<TbmSessionRow>>>(emptyMap()) }

    LaunchedEffect(leaderUserId) {
        groups = runCatching { repo.fetchGroupsForManager(leaderUserId) }.getOrElse { emptyList() }
    }
    LaunchedEffect(groups.map { it.groupId }) {
        if (groups.isEmpty()) {
            sessionsByGroup = emptyMap()
            return@LaunchedEffect
        }
        repo.todaySessionsFlow(groups.map { it.groupId }).collectLatest { sessionsByGroup = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 헤더 — Assignment icon + 제목
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Assignment,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("TBM 대시보드", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.height(16.dp))

        // 1단 — 세션 시작 폼 (아침 우선 위계, D1 선택 반영)
        TbmStartSection(
            leaderUserId = leaderUserId,
            supabase = supabase,
            onSubmitted = {},
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // 2단 — 그룹별 오늘 세션 (진행중 섹션 + 종료 섹션 분리)
        if (groups.isEmpty()) {
            Text("그룹 불러오는 중...", fontSize = 13.sp, color = COLOR_TEXT_MUTED)
        } else {
            groups.forEach { group ->
                val groupSessions = sessionsByGroup[group.groupId].orEmpty()
                val activeSessions = groupSessions.filter { it.endedAt == null }
                val endedSessions = groupSessions.filter { it.endedAt != null }

                GroupSessionsSection(
                    group = group,
                    activeSessions = activeSessions,
                    endedSessions = endedSessions,
                    leaderUserId = leaderUserId,
                    repo = repo,
                    scope = scope,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun GroupSessionsSection(
    group: GroupRow,
    activeSessions: List<TbmSessionRow>,
    endedSessions: List<TbmSessionRow>,
    leaderUserId: String,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "그룹 #${group.groupId} (${group.inviteCode})",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )

        if (activeSessions.isEmpty() && endedSessions.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("오늘 TBM 세션 없음", fontSize = 12.sp, color = COLOR_TEXT_MUTED)
            return@Column
        }

        // 진행중 섹션
        if (activeSessions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionHeader(
                icon = Icons.Default.Schedule,
                iconTint = COLOR_ACTIVE_ORANGE,
                label = "진행중 (${activeSessions.size}개)",
            )
            activeSessions.forEach { session ->
                Spacer(Modifier.height(6.dp))
                SessionDetailCard(
                    session = session,
                    isActive = true,
                    leaderUserId = leaderUserId,
                    repo = repo,
                    scope = scope,
                )
            }
        }

        // 종료 섹션
        if (endedSessions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionHeader(
                icon = Icons.Default.CheckCircle,
                iconTint = COLOR_TEXT_MUTED,
                label = "종료 (${endedSessions.size}개)",
            )
            endedSessions.forEach { session ->
                Spacer(Modifier.height(6.dp))
                SessionDetailCard(
                    session = session,
                    isActive = false,
                    leaderUserId = leaderUserId,
                    repo = repo,
                    scope = scope,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = iconTint)
    }
}

@Composable
private fun SessionDetailCard(
    session: TbmSessionRow,
    isActive: Boolean,
    leaderUserId: String,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
    // 진행중 세션은 default expanded, 종료 세션은 default collapsed (정보 폭주 차단).
    var expanded by remember(session.sessionId) { mutableStateOf(isActive) }
    var participants by remember(session.sessionId) { mutableStateOf<List<TbmParticipantRow>>(emptyList()) }
    var checklists by remember(session.sessionId) { mutableStateOf<List<TbmChecklistRow>>(emptyList()) }
    var keyHazardId by remember(session.sessionId) { mutableStateOf(session.keyHazardId ?: "") }
    var feedbackNotes by remember(session.sessionId) { mutableStateOf(session.feedbackNotes ?: "") }
    var endResultMsg by remember(session.sessionId) { mutableStateOf<String?>(null) }
    var ending by remember(session.sessionId) { mutableStateOf(false) }
    val api = remember { buildTbmFunctionsApi() }

    LaunchedEffect(session.sessionId, expanded) {
        if (expanded) repo.participantsFlow(session.sessionId).collectLatest { participants = it }
    }
    LaunchedEffect(session.sessionId, expanded) {
        if (expanded) repo.checklistsFlow(session.sessionId).collectLatest { checklists = it }
    }

    // 활성/종료 톤 분리:
    //   진행중 → 2dp orange border + 기본 카드 배경
    //   종료   → 회색 배경 + border 0
    val cardBorder = if (isActive) BorderStroke(2.dp, COLOR_ACTIVE_ORANGE) else null
    val cardColors = if (isActive) {
        CardDefaults.cardColors()
    } else {
        CardDefaults.cardColors(containerColor = COLOR_ENDED_BG)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = cardBorder,
        colors = cardColors,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 헤더 행 — 토글 클릭 가능
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (expanded) "접기" else "펼치기",
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        session.workScope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isActive) Color.Black else COLOR_TEXT_MUTED,
                    )
                    val statusKor = if (isActive) "진행중" else "종료"
                    Text(
                        "${workTypeKorean(session.workType)} · $statusKor · 참여자 ${participants.size}명",
                        fontSize = 12.sp,
                        color = COLOR_TEXT_MUTED,
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))

                // 메타 정보 행 (시간 + 위치)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = COLOR_TEXT_MUTED,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "예상 종료 ${formatTimeShort(session.expectedEndAt)}",
                        fontSize = 12.sp,
                        color = COLOR_TEXT_MUTED,
                    )
                }
                session.location?.let { loc ->
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = COLOR_TEXT_MUTED,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("위치: $loc", fontSize = 12.sp, color = COLOR_TEXT_MUTED)
                    }
                }
                Spacer(Modifier.height(10.dp))

                Text("위험요인", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                session.hazardsSnapshot.forEach { Text("- ${it.text}", fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))

                Text("대책", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                session.controlsSnapshot.forEach { Text("- ${it.text}", fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))

                Text(
                    "점검 항목 (${checklists.count { it.isChecked }}/${checklists.size})",
                    fontWeight = FontWeight.SemiBold,
                )
                checklists.forEach { item -> ChecklistRow(item = item, repo = repo, scope = scope) }
                Spacer(Modifier.height(8.dp))

                if (participants.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = COLOR_TEXT_MUTED,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("참여자", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    participants.forEach { p ->
                        Text(
                            "${p.userId} (${formatTimeShort(p.signedAt)})",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 20.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (session.endedAt == null) {
                    OutlinedTextField(
                        value = keyHazardId,
                        onValueChange = { keyHazardId = it },
                        label = { Text("중점위험 id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedbackNotes,
                        onValueChange = { feedbackNotes = it },
                        label = { Text("환류 조치 메모") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
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
                                            keyHazardId = keyHazardId.ifBlank { null },
                                            feedbackNotes = feedbackNotes.ifBlank { null },
                                        ),
                                    )
                                    endResultMsg = when {
                                        resp.isSuccessful && resp.body()?.ok == true ->
                                            "종료 완료 (참여자 ${resp.body()?.participantCount ?: 0}명)"
                                        resp.code() == 404 -> "리더가 아니거나 이미 종료됨"
                                        else -> "오류 ${resp.code()}"
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
                        Text(it, color = COLOR_TEXT_INFO, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ChecklistRow(
    item: TbmChecklistRow,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
    val isFreetext = item.itemText == FREETEXT_ITEM_TEXT
    val isChecked = item.isChecked

    if (isFreetext) {
        var draft by remember(item.checklistId) { mutableStateOf(item.note ?: "") }
        LaunchedEffect(item.note) {
            if ((item.note ?: "") != draft) draft = item.note ?: ""
        }
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
                        scope.launch { runCatching { repo.updateChecklistItem(item.checklistId, isChecked = newChecked) } }
                    },
                )
                Text("추가 작업 사항", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("메모") },
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { newChecked ->
                    scope.launch { runCatching { repo.updateChecklistItem(item.checklistId, isChecked = newChecked) } }
                },
            )
            Text(item.itemText, fontSize = 13.sp)
        }
    }
}
