package com.example.smart_safety_management.tbm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TbmStartSection(
    leaderUserId: String,
    supabase: SupabaseClient,
    onSubmitted: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val repo = remember { TbmRepository(supabase) }
    val api = remember { buildTbmFunctionsApi() }

    var templates by remember { mutableStateOf<List<TbmTemplateRow>>(emptyList()) }
    var groups by remember { mutableStateOf<List<GroupRow>>(emptyList()) }
    var selectedGroupIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedTemplate by remember { mutableStateOf<TbmTemplateRow?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var workScopeInput by remember { mutableStateOf("") }
    var hazards by remember { mutableStateOf<List<TbmTemplateHazard>>(emptyList()) }
    var controls by remember { mutableStateOf<List<TbmTemplateControl>>(emptyList()) }
    var customHazardInput by remember { mutableStateOf("") }

    val initialZdt = remember { ExpectedEndAtValidator.nowKst().plusMinutes(15) }
    var hour by remember { mutableStateOf(initialZdt.hour) }
    var minute by remember { mutableStateOf(initialZdt.minute) }
    var showTimePicker by remember { mutableStateOf(false) }

    var locationInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var perGroupResults by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showSlamDialog by remember { mutableStateOf(false) }
    var customControlInput by remember { mutableStateOf("") }
    var customControlHazardId by remember { mutableStateOf("") }

    fun selectTemplate(template: TbmTemplateRow?) {
        selectedTemplate = template
        hazards = template?.hazards ?: emptyList()
        controls = template?.controls ?: emptyList()
    }

    LaunchedEffect(Unit) {
        templates = runCatching { repo.fetchActiveTemplates() }
            .onFailure { loadError = "Failed to load OPS templates: ${it.message}" }
            .getOrElse { emptyList() }
        selectTemplate(templates.firstOrNull())
        groups = runCatching { repo.fetchGroupsForManager(leaderUserId) }
            .onFailure { loadError = "Failed to load groups: ${it.message}" }
            .getOrElse { emptyList() }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TBM 세션 시작", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSlamDialog = true }) {
                Icon(Icons.Default.Info, contentDescription = "SLAM 행동요령 보기")
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = workScopeInput,
            onValueChange = { workScopeInput = it.take(80) },
            label = { Text("작업 범위") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = !dropdownExpanded },
        ) {
            OutlinedTextField(
                value = selectedTemplate?.title ?: "활성 OPS 없음",
                onValueChange = {},
                readOnly = true,
                label = { Text("작업 종류 (OPS)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
            ) {
                templates.forEach { template ->
                    DropdownMenuItem(
                        text = { Text(template.title) },
                        onClick = {
                            selectTemplate(template)
                            dropdownExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = "%02d:%02d".format(hour, minute),
            onValueChange = {},
            readOnly = true,
            label = { Text("예상 종료 시각") },
            trailingIcon = {
                TextButton(onClick = { showTimePicker = true }) {
                    Icon(Icons.Default.Schedule, contentDescription = "시각 선택")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = locationInput,
            onValueChange = { locationInput = it },
            label = { Text("위치") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = notesInput,
            onValueChange = { notesInput = it },
            label = { Text("메모") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Text("위험요인 (Hazards)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        hazards.forEach { hazard ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "- ${hazard.text}" + (if (hazard.isCustom) " (custom)" else ""),
                    fontSize = 12.sp,
                    color = Color(0xFF374151),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    hazards = HazardsListReducer.removeHazardById(hazards, hazard.id)
                }) {
                    Text("X", fontSize = 11.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customHazardInput,
                onValueChange = { customHazardInput = it },
                label = { Text("위험 추가") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = customHazardInput.isNotBlank(),
                onClick = {
                    hazards = HazardsListReducer.addHazard(hazards, customHazardInput)
                    customHazardInput = ""
                },
            ) { Text("추가") }
        }
        Spacer(Modifier.height(12.dp))

        Text("대책 (Controls)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        controls.forEach { control ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "- (${control.level}) ${control.hazardId ?: "-"}: ${control.text}" + (if (control.isCustom) " (custom)" else ""),
                    fontSize = 12.sp,
                    color = Color(0xFF374151),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    controls = HazardsListReducer.removeControlById(controls, control.id)
                }) {
                    Text("X", fontSize = 11.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customControlHazardId,
                onValueChange = { customControlHazardId = it.trim() },
                label = { Text("위험 id") },
                singleLine = true,
                modifier = Modifier.width(80.dp),
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = customControlInput,
                onValueChange = { customControlInput = it },
                label = { Text("대책 본문") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = customControlInput.isNotBlank(),
                onClick = {
                    controls = HazardsListReducer.addControl(
                        list = controls,
                        text = customControlInput,
                        hazardId = customControlHazardId.takeIf { it.isNotBlank() },
                    )
                    customControlInput = ""
                    customControlHazardId = ""
                },
            ) { Text("추가") }
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("대상 그룹", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            if (groups.isNotEmpty()) {
                val allSelected = selectedGroupIds.size == groups.size
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { checked ->
                        selectedGroupIds = if (checked) groups.map { it.groupId }.toSet() else emptySet()
                    },
                )
                Text("전체", fontSize = 13.sp)
            }
        }
        loadError?.let { Text(it, color = Color(0xFFEF4444), fontSize = 12.sp) }
        groups.forEach { group ->
            val checked = group.groupId in selectedGroupIds
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        selectedGroupIds =
                            if (isChecked) selectedGroupIds + group.groupId else selectedGroupIds - group.groupId
                    },
                )
                Text("#${group.groupId} (${group.inviteCode})", fontSize = 13.sp)
                perGroupResults[group.groupId]?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, fontSize = 12.sp, color = Color(0xFF2563EB))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val template = selectedTemplate
                val scopeText = workScopeInput.trim()
                when {
                    template == null -> perGroupResults = mapOf(-1 to "활성 OPS 를 선택하세요")
                    scopeText.isEmpty() -> perGroupResults = mapOf(-1 to "작업 범위를 입력하세요")
                    selectedGroupIds.isEmpty() -> perGroupResults = mapOf(-1 to "그룹을 1개 이상 선택하세요")
                    hazards.isEmpty() || controls.isEmpty() -> perGroupResults = mapOf(-1 to "위험요인·대책이 필요합니다")
                    !WorkTypeValidator.isValid(template.workType, templates) ->
                        perGroupResults = mapOf(-1 to "선택한 OPS 가 비활성화됨")
                    else -> {
                        val zdt = ExpectedEndAtValidator.nowKst()
                            .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                        val expectedEndAtIso = ExpectedEndAtValidator.formatForServer(zdt)

                        submitting = true
                        perGroupResults = emptyMap()
                        scope.launch {
                            try {
                                val results = coroutineScope {
                                    selectedGroupIds.map { groupId ->
                                        async {
                                            val msg = runCatching {
                                                val resp = api.callTbmStart(
                                                    url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                                    auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                                    body = TbmStartRequest(
                                                        leaderUserId = leaderUserId,
                                                        groupId = groupId,
                                                        workType = template.workType,
                                                        workScope = scopeText,
                                                        expectedEndAt = expectedEndAtIso,
                                                        location = locationInput.ifBlank { null },
                                                        notes = notesInput.ifBlank { null },
                                                        hazards = hazards,
                                                        controls = controls,
                                                    ),
                                                )
                                                when {
                                                    resp.isSuccessful && resp.body()?.ok == true ->
                                                        "시작됨 (${resp.body()?.checklistCount ?: 0})"
                                                    resp.code() == 409 -> "이미 존재"
                                                    else -> "오류 ${resp.code()}"
                                                }
                                            }.getOrElse { "네트워크 오류: ${it.message}" }
                                            groupId to msg
                                        }
                                    }.awaitAll()
                                }
                                perGroupResults = results.toMap()
                                onSubmitted()
                            } finally {
                                submitting = false
                            }
                        }
                    }
                }
            },
            enabled = !submitting && groups.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (submitting) "시작 중..." else "${selectedGroupIds.size}개 세션 시작")
        }

        perGroupResults[-1]?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFEF4444), fontSize = 13.sp)
        }
    }

    if (showSlamDialog) {
        SlamGuideDialog(onDismiss = { showSlamDialog = false })
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("예상 종료 시각") },
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
