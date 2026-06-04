package com.example.smart_safety_management.tbm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.BuildConfig
import com.example.smart_safety_management.ui.SsmColors
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var managerGroup by remember { mutableStateOf<GroupRow?>(null) }
    var selectedTemplates by remember { mutableStateOf<List<TbmTemplateRow>>(emptyList()) }

    val initialZdt = remember { ExpectedEndAtValidator.nowKst().plusMinutes(15) }
    var hour by remember { mutableStateOf(initialZdt.hour) }
    var minute by remember { mutableStateOf(initialZdt.minute) }
    var showTimePicker by remember { mutableStateOf(false) }

    var locationInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitResult by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showSlamDialog by remember { mutableStateOf(false) }

    fun toggleTemplate(template: TbmTemplateRow) {
        selectedTemplates = if (selectedTemplates.any { it.templateId == template.templateId }) {
            selectedTemplates.filterNot { it.templateId == template.templateId }
        } else {
            selectedTemplates + template
        }
    }

    LaunchedEffect(Unit) {
        templates = runCatching { repo.fetchActiveTemplates() }
            .onFailure { loadError = "OPS 목록을 불러오지 못했습니다: ${it.message}" }
            .getOrElse { emptyList() }
        selectedTemplates = templates.firstOrNull()?.let { listOf(it) } ?: emptyList()
        managerGroup = runCatching { repo.fetchGroupsForManager(leaderUserId) }
            .onFailure { loadError = "그룹 정보를 불러오지 못했습니다: ${it.message}" }
            .getOrNull()?.firstOrNull()
    }

    val sessionTitle = selectedOpsSessionTitle(selectedTemplates).take(80)

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TBM 세션 시작", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSlamDialog = true }) {
                Icon(Icons.Default.Info, contentDescription = "SLAM 행동요령 보기")
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("작업 종류 (OPS)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        if (templates.isEmpty()) {
            Text("활성 OPS 없음", fontSize = 13.sp, color = SsmColors.TextMuted)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                templates.forEach { template ->
                    val selected = selectedTemplates.any { it.templateId == template.templateId }
                    FilterChip(
                        selected = selected,
                        onClick = { toggleTemplate(template) },
                        label = { Text(template.title, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SsmColors.TextInfo.copy(alpha = 0.14f),
                            selectedLabelColor = SsmColors.TextInfo,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SsmColors.EndedBg),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("세션 이름", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(sessionTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SsmColors.TextInfo)
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

        if (selectedTemplates.isNotEmpty()) {
            Text("선택 OPS 요약", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            selectedTemplates.forEach { template ->
                Text(
                    "${template.title}: 위험 ${template.hazards.size} · 조치 ${template.controls.size} · 체크 ${template.checks.size}",
                    fontSize = 13.sp,
                    color = SsmColors.TextMuted,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        loadError?.let { Text(it, color = SsmColors.TextDanger, fontSize = 13.sp) }

        Button(
            onClick = {
                val group = managerGroup
                when {
                    selectedTemplates.isEmpty() -> submitResult = "활성 OPS를 선택하세요"
                    group == null -> submitResult = "그룹 정보 없음 - 관리자 권한 확인 필요"
                    selectedTemplates.any { !WorkTypeValidator.isValid(it.workType, templates) } ->
                        submitResult = "선택한 OPS가 비활성화되었습니다"
                    else -> {
                        val aggregated = aggregateSelectedOps(selectedTemplates)
                        val zdt = ExpectedEndAtValidator.nowKst()
                            .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                        val expectedEndAtIso = ExpectedEndAtValidator.formatForServer(zdt)

                        submitting = true
                        submitResult = null
                        scope.launch {
                            try {
                                val msg = runCatching {
                                    val resp = api.callTbmStart(
                                        url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                        auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                        body = TbmStartRequest(
                                            leaderUserId = leaderUserId,
                                            groupId = group.groupId,
                                            workType = selectedTemplates.first().workType,
                                            workScope = sessionTitle,
                                            expectedEndAt = expectedEndAtIso,
                                            location = locationInput.ifBlank { null },
                                            notes = notesInput.ifBlank { null },
                                            hazards = aggregated.hazards,
                                            controls = aggregated.controls,
                                            templateIds = aggregated.templateIds,
                                            opsTitles = aggregated.opsTitles,
                                            checks = aggregated.checks,
                                        ),
                                    )
                                    when {
                                        resp.isSuccessful && resp.body()?.ok == true ->
                                            "시작됨 · 평가 항목 ${resp.body()?.checklistCount ?: 0}개"
                                        resp.code() == 409 -> "이미 같은 TBM 세션이 존재합니다"
                                        else -> "오류 ${resp.code()}"
                                    }
                                }.getOrElse { "네트워크 오류: ${it.message}" }
                                submitResult = msg
                                onSubmitted()
                            } finally {
                                submitting = false
                            }
                        }
                    }
                }
            },
            enabled = !submitting && managerGroup != null && selectedTemplates.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (submitting) "시작 중..." else "TBM 시작")
        }

        submitResult?.let {
            Spacer(Modifier.height(8.dp))
            val isErr = it.startsWith("오류") || it.contains("실패") || it.contains("필요") ||
                it.contains("선택하세요") || it.contains("이미")
            Text(it, color = if (isErr) SsmColors.TextDanger else SsmColors.TextInfo, fontSize = 13.sp)
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
