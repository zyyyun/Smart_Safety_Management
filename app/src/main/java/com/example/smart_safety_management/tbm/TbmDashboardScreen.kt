package com.example.smart_safety_management.tbm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

private const val FREETEXT_ITEM_TEXT = "Additional work item"

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
        Text("TBM dashboard", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        TbmStartSection(
            leaderUserId = leaderUserId,
            supabase = supabase,
            onSubmitted = {},
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        val sessionCount = sessionsByGroup.values.sumOf { it.size }
        Text("Today sessions ($sessionCount)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        if (groups.isEmpty()) {
            Text("Loading groups...", fontSize = 13.sp, color = Color(0xFF6B7280))
        } else {
            groups.forEach { group ->
                GroupSessionListCard(
                    group = group,
                    sessions = sessionsByGroup[group.groupId].orEmpty(),
                    leaderUserId = leaderUserId,
                    repo = repo,
                    scope = scope,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GroupSessionListCard(
    group: GroupRow,
    sessions: List<TbmSessionRow>,
    leaderUserId: String,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("#${group.groupId} (${group.inviteCode})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (sessions.isEmpty()) {
                Text("No TBM session today", fontSize = 12.sp, color = Color(0xFF6B7280))
            } else {
                sessions.forEach { session ->
                    Spacer(Modifier.height(8.dp))
                    SessionDetailCard(
                        session = session,
                        leaderUserId = leaderUserId,
                        repo = repo,
                        scope = scope,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionDetailCard(
    session: TbmSessionRow,
    leaderUserId: String,
    repo: TbmRepository,
    scope: CoroutineScope,
) {
    var expanded by remember(session.sessionId) { mutableStateOf(true) }
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (expanded) "v" else ">", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.workScope, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val status = if (session.endedAt != null) "ended" else "active"
                    Text(
                        "${session.workType} / $status / ${participants.size} participant(s)",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("Expected end: ${formatTimeShort(session.expectedEndAt)}", fontSize = 12.sp, color = Color.Gray)
                session.location?.let { Text("Location: $it", fontSize = 12.sp, color = Color.Gray) }
                Spacer(Modifier.height(8.dp))

                Text("Hazards", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                session.hazardsSnapshot.forEach { Text("- ${it.text}", fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))

                Text("Controls", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                session.controlsSnapshot.forEach { Text("- ${it.text}", fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))

                Text("Checklist (${checklists.count { it.isChecked }}/${checklists.size})", fontWeight = FontWeight.SemiBold)
                checklists.forEach { item -> ChecklistRow(item = item, repo = repo, scope = scope) }
                Spacer(Modifier.height(8.dp))

                if (participants.isNotEmpty()) {
                    Text("Participants", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    participants.forEach { p ->
                        Text("${p.userId} (${formatTimeShort(p.signedAt)})", fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (session.endedAt == null) {
                    OutlinedTextField(
                        value = keyHazardId,
                        onValueChange = { keyHazardId = it },
                        label = { Text("Key hazard id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedbackNotes,
                        onValueChange = { feedbackNotes = it },
                        label = { Text("Feedback notes") },
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
                                            "Ended (${resp.body()?.participantCount ?: 0})"
                                        resp.code() == 404 -> "Not leader or already ended"
                                        else -> "Error ${resp.code()}"
                                    }
                                } catch (e: Exception) {
                                    endResultMsg = "Network: ${e.message}"
                                } finally {
                                    ending = false
                                }
                            }
                        },
                        enabled = !ending,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (ending) "Ending..." else "End session")
                    }
                    endResultMsg?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = Color(0xFF2563EB), fontSize = 12.sp)
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
                Text("Additional work item", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Note") },
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
