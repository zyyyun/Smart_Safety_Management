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
import com.example.smart_safety_management.ui.SsmColors
import com.example.smart_safety_management.ui.components.SectionHeader
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val FREETEXT_ITEM_TEXT = "추가 작업 사항"

data class OpsTitleGroup<T>(
    val opsTitle: String?,
    val items: List<T>,
)

internal fun <T> groupByOpsTitle(
    items: List<T>,
    labelOf: (T) -> String?,
): List<OpsTitleGroup<T>> {
    val ordered = linkedMapOf<String?, MutableList<T>>()
    items.forEach { item ->
        val title = labelOf(item)?.takeIf { it.isNotBlank() }
        ordered.getOrPut(title) { mutableListOf() }.add(item)
    }
    return ordered.map { (title, groupedItems) -> OpsTitleGroup(title, groupedItems) }
}

@Composable
fun TbmDashboardScreen(
    leaderUserId: String,
    supabase: SupabaseClient,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { TbmRepository(supabase) }

    var managerGroup by remember { mutableStateOf<GroupRow?>(null) }
    var todaySessions by remember { mutableStateOf<List<TbmSessionRow>>(emptyList()) }

    LaunchedEffect(leaderUserId) {
        managerGroup = runCatching { repo.fetchGroupsForManager(leaderUserId) }
            .getOrNull()?.firstOrNull()
    }
    LaunchedEffect(managerGroup?.groupId) {
        val gid = managerGroup?.groupId
        if (gid == null) {
            todaySessions = emptyList()
        } else {
            repo.todaySessionFlow(gid).collectLatest { todaySessions = it }
        }
    }

    val activeSessions = todaySessions.filter { it.endedAt == null }
    val endedSessions = todaySessions.filter { it.endedAt != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Assignment,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("TBM 현장 운영", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("오늘 세션과 빠른 시작", fontSize = 12.sp, color = SsmColors.TextMuted)
            }
        }
        Spacer(Modifier.height(12.dp))

        TbmDashboardSummary(
            activeCount = activeSessions.size,
            endedCount = endedSessions.size,
            totalCount = todaySessions.size,
        )
        Spacer(Modifier.height(12.dp))

        TbmQuickStartContainer {
            TbmStartSection(
                leaderUserId = leaderUserId,
                supabase = supabase,
                onSubmitted = {},
            )
        }
        Spacer(Modifier.height(16.dp))

        if (managerGroup == null) {
            Text("그룹 정보 불러오는 중..", fontSize = 13.sp, color = SsmColors.TextMuted)
        } else {
            SessionsSection(
                activeSessions = activeSessions,
                endedSessions = endedSessions,
                leaderUserId = leaderUserId,
                repo = repo,
                scope = scope,
            )
        }
    }
}

@Composable
private fun TbmDashboardSummary(
    activeCount: Int,
    endedCount: Int,
    totalCount: Int,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        SummaryMetricCard(
            label = "진행중",
            value = activeCount.toString(),
            color = SsmColors.ActiveOrange,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        SummaryMetricCard(
            label = "완료",
            value = endedCount.toString(),
            color = SsmColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        SummaryMetricCard(
            label = "전체",
            value = totalCount.toString(),
            color = SsmColors.TextInfo,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SsmColors.EndedBg),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = SsmColors.TextMuted)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        }
    }
}

@Composable
private fun TbmQuickStartContainer(content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SsmColors.TextMuted.copy(alpha = 0.18f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (expanded) "접기" else "펼치기",
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("빠른 TBM 시작", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("여러 OPS를 선택해 하나의 세션으로 시작", fontSize = 12.sp, color = SsmColors.TextMuted)
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun SessionsSection(
    activeSessions: List<TbmSessionRow>,
    endedSessions: List<TbmSessionRow>,
    leaderUserId: String,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (activeSessions.isEmpty() && endedSessions.isEmpty()) {
            Text("오늘 TBM 세션 없음", fontSize = 13.sp, color = SsmColors.TextMuted)
            return@Column
        }

        SectionHeader(
            icon = Icons.Default.Schedule,
            label = "진행중",
            count = activeSessions.size,
            iconTint = SsmColors.ActiveOrange,
        )
        if (activeSessions.isEmpty()) {
            Text("진행중 세션 없음", fontSize = 12.sp, color = SsmColors.TextMuted)
        } else {
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

        Spacer(Modifier.height(14.dp))
        SectionHeader(
            icon = Icons.Default.CheckCircle,
            label = "완료",
            count = endedSessions.size,
            iconTint = SsmColors.TextMuted,
        )
        if (endedSessions.isEmpty()) {
            Text("완료 세션 없음", fontSize = 12.sp, color = SsmColors.TextMuted)
        } else {
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
private fun SessionDetailCard(
    session: TbmSessionRow,
    isActive: Boolean,
    leaderUserId: String,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
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

    val cardBorder = if (isActive) BorderStroke(2.dp, SsmColors.ActiveOrange) else null
    val cardColors = if (isActive) {
        CardDefaults.cardColors()
    } else {
        CardDefaults.cardColors(containerColor = SsmColors.EndedBg)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = cardBorder,
        colors = cardColors,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        color = if (isActive) Color.Black else SsmColors.TextMuted,
                    )
                    val statusText = if (isActive) "진행중" else "완료"
                    Text(
                        "${workTypeKorean(session.workType)} · $statusText · 참여자 ${participants.size}명",
                        fontSize = 12.sp,
                        color = SsmColors.TextMuted,
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = SsmColors.TextMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "예상 종료 ${formatTimeShort(session.expectedEndAt)}",
                        fontSize = 12.sp,
                        color = SsmColors.TextMuted,
                    )
                }
                session.location?.let { loc ->
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = SsmColors.TextMuted,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("위치: $loc", fontSize = 12.sp, color = SsmColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(10.dp))

                GroupedSnapshotList(
                    title = "위험요인",
                    grouped = groupByOpsTitle(session.hazardsSnapshot) { it.opsTitle },
                    textOf = { it.text },
                )
                Spacer(Modifier.height(8.dp))

                GroupedSnapshotList(
                    title = "조치",
                    grouped = groupByOpsTitle(session.controlsSnapshot) { it.opsTitle },
                    textOf = { it.text },
                )
                Spacer(Modifier.height(8.dp))

                Text(
                    "평가 항목 (${checklists.count { it.isChecked }}/${checklists.size})",
                    fontWeight = FontWeight.SemiBold,
                )
                checklists.forEach { item -> ChecklistRow(item = item, repo = repo, scope = scope) }
                Spacer(Modifier.height(8.dp))

                if (participants.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = SsmColors.TextMuted,
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
                        label = { Text("후속 조치 메모") },
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
                        Text(if (ending) "종료 중.." else "세션 종료")
                    }
                    endResultMsg?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = SsmColors.TextInfo, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> GroupedSnapshotList(
    title: String,
    grouped: List<OpsTitleGroup<T>>,
    textOf: (T) -> String,
) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    if (grouped.isEmpty()) {
        Text("없음", fontSize = 12.sp, color = SsmColors.TextMuted)
        return
    }

    val hasOpsMetadata = grouped.any { it.opsTitle != null }
    grouped.forEach { group ->
        if (hasOpsMetadata) {
            Text(
                group.opsTitle ?: "기타",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = SsmColors.TextInfo,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        group.items.forEach { item ->
            Text("- ${textOf(item)}", fontSize = 12.sp, modifier = Modifier.padding(start = if (hasOpsMetadata) 8.dp else 0.dp))
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
