package com.example.smart_safety_management.tbm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Phase 9 / 09-03 TBM-02 — manager TbmDashboardActivity 의 세션 시작 섹션.
 *
 * 2026-05-20 Change 1+3 — 다중 그룹 동시 생성 + TimePicker (plan tbm-linear-dragonfly):
 *   - work_type 1개 + 시각/위치/비고 공통 입력 → N 그룹 multi-select
 *   - "선택한 N개 그룹 세션 시작" 1회 클릭 → N parallel callTbmStart (coroutineScope.async)
 *   - 그룹별 결과 인라인 칩 (✓ 시작됨 / ⚠ 이미 존재 / ✗ 오류) — 부분 실패 회복
 *   - 시각은 Material3 TimePicker (Compose BOM 2024.05 = M3 1.2 — TimePickerDialog
 *     는 M3 1.3+, 여기서는 AlertDialog 로 wrap)
 *
 * 호환:
 *   - onSubmitted: 모든 그룹 호출 끝 후 trigger. Caller (TbmDashboardScreen) 가
 *     todaySessionsFlow 로 자동 갱신하므로 sessionId 전달 불필요.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TbmStartSection(
    leaderUserId: String,
    supabase: SupabaseClient,
    onSubmitted: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val api = remember { buildTbmFunctionsApi() }
    var templates by remember { mutableStateOf<List<TbmTemplateRow>>(emptyList()) }
    var groups by remember { mutableStateOf<List<GroupRow>>(emptyList()) }
    var selectedGroupIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedWorkType by remember { mutableStateOf("fire") }
    var workTypeDropdownExpanded by remember { mutableStateOf(false) }

    val initialZdt = remember { ExpectedEndAtValidator.nowKst().plusMinutes(15) }
    var hour by remember { mutableStateOf(initialZdt.hour) }
    var minute by remember { mutableStateOf(initialZdt.minute) }
    var showTimePicker by remember { mutableStateOf(false) }

    var locationInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    // (groupId → 결과 메시지). 새 submit 마다 reset.
    var perGroupResults by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val repo = TbmRepository(supabase)
        templates = runCatching { repo.fetchTemplates() }.getOrElse { emptyList() }
        groups = runCatching { repo.fetchGroupsForManager(leaderUserId) }
            .onFailure { loadError = "그룹 목록 조회 실패: ${it.message}" }
            .getOrElse { emptyList() }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("새 TBM 세션 시작 (다중 그룹)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))

        // ── 작업유형 Dropdown ─────────────────────────────────────────────
        ExposedDropdownMenuBox(
            expanded = workTypeDropdownExpanded,
            onExpandedChange = { workTypeDropdownExpanded = !workTypeDropdownExpanded },
        ) {
            OutlinedTextField(
                value = workTypeKorean(selectedWorkType),
                onValueChange = {},
                readOnly = true,
                label = { Text("작업유형") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workTypeDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = workTypeDropdownExpanded,
                onDismissRequest = { workTypeDropdownExpanded = false },
            ) {
                WorkTypeValidator.ALLOWED.toList().sorted().forEach { code ->
                    val title = templates.firstOrNull { it.workType == code }?.title ?: workTypeKorean(code)
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            selectedWorkType = code
                            workTypeDropdownExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── 예정 종료 시각 TimePicker ─────────────────────────────────────
        OutlinedTextField(
            value = "%02d:%02d".format(hour, minute),
            onValueChange = {},
            readOnly = true,
            label = { Text("예정 종료 시각 (오늘 KST)") },
            trailingIcon = { Icon(Icons.Default.Schedule, contentDescription = "시간 선택") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTimePicker = true },
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = locationInput,
            onValueChange = { locationInput = it },
            label = { Text("위치 (선택)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = notesInput,
            onValueChange = { notesInput = it },
            label = { Text("비고 (선택)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        // ── 그룹 선택 ─────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("그룹 선택", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            if (groups.isNotEmpty()) {
                val allSelected = selectedGroupIds.size == groups.size
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { checked ->
                        selectedGroupIds = if (checked) groups.map { it.groupId }.toSet() else emptySet()
                    },
                )
                Text("전체 선택", fontSize = 13.sp)
            }
        }
        loadError?.let {
            Text(it, color = Color(0xFFEF4444), fontSize = 12.sp)
        }
        if (groups.isEmpty() && loadError == null) {
            Text("그룹 로드 중...", fontSize = 13.sp, color = Color(0xFF6B7280))
        }
        groups.forEach { g ->
            val checked = g.groupId in selectedGroupIds
            val resultMsg = perGroupResults[g.groupId]
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        selectedGroupIds = if (isChecked) selectedGroupIds + g.groupId
                                           else selectedGroupIds - g.groupId
                    },
                )
                Text("#${g.groupId} (${g.inviteCode})", fontSize = 13.sp)
                resultMsg?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = when {
                            it.startsWith("✓") -> Color(0xFF22C55E)
                            it.startsWith("⚠") -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── Submit ───────────────────────────────────────────────────────
        Button(
            onClick = {
                val workType = WorkTypeValidator.normalize(selectedWorkType)
                if (!WorkTypeValidator.isValid(workType)) {
                    perGroupResults = mapOf(-1 to "작업유형이 유효하지 않습니다")
                    return@Button
                }
                if (selectedGroupIds.isEmpty()) {
                    perGroupResults = mapOf(-1 to "그룹을 1개 이상 선택하세요")
                    return@Button
                }
                // TimePicker 결과 → 오늘 KST + 선택 시각 → ISO 8601 +09:00
                val zdt = ExpectedEndAtValidator.nowKst()
                    .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                val expectedEndAtIso = ExpectedEndAtValidator.formatForServer(zdt)

                submitting = true
                perGroupResults = emptyMap()
                scope.launch {
                    try {
                        val results: List<Pair<Int, String>> = coroutineScope {
                            selectedGroupIds.map { gid ->
                                async {
                                    val msg = runCatching {
                                        val resp = api.callTbmStart(
                                            url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                            auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                            body = TbmStartRequest(
                                                leaderUserId = leaderUserId,
                                                groupId = gid,
                                                workType = workType,
                                                expectedEndAt = expectedEndAtIso,
                                                location = locationInput.ifBlank { null },
                                                notes = notesInput.ifBlank { null },
                                            ),
                                        )
                                        when {
                                            resp.isSuccessful && resp.body()?.ok == true ->
                                                "✓ 시작됨 (체크리스트 ${resp.body()?.checklistCount ?: 0}개)"
                                            resp.code() == 409 -> "⚠ 이미 존재"
                                            resp.code() == 400 -> "✗ 형식 오류"
                                            else -> "✗ 오류 ${resp.code()}"
                                        }
                                    }.getOrElse { "✗ 네트워크: ${it.message}" }
                                    gid to msg
                                }
                            }.awaitAll()
                        }
                        perGroupResults = results.toMap()
                        onSubmitted()
                    } finally {
                        submitting = false
                    }
                }
            },
            enabled = !submitting && groups.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (submitting) "시작 중..."
                else "선택한 ${selectedGroupIds.size}개 그룹 세션 시작"
            )
        }

        // 작업유형/그룹 미선택 경고 메시지 (key = -1)
        perGroupResults[-1]?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFEF4444), fontSize = 13.sp)
        }
    }

    // ── TimePicker Dialog ────────────────────────────────────────────────
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("예정 종료 시각") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    showTimePicker = false
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("취소") }
            },
        )
    }
}

internal fun buildTbmFunctionsApi(): TbmFunctionsApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TbmFunctionsApi::class.java)
