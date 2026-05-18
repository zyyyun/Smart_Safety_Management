package com.example.smart_safety_management.tbm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Phase 9 / 09-03 TBM-02 — manager TbmDashboardActivity 의 세션 시작 섹션 (D-06).
 *
 * UI:
 *   - work_type 5종 Dropdown (fire/electric/height/heavy/general — title 한글 표시)
 *   - expected_end_at OutlinedTextField (default = nowKst().plusMinutes(15) ISO_OFFSET_DATE_TIME)
 *   - "세션 시작" 버튼 → TbmFunctionsApi.callTbmStart → 200 / 409 / 400 분기
 *
 * Phase 7 PairWatchSection 패턴 미러 + dropdown 추가.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TbmStartSection(
    leaderUserId: String,
    groupId: Int,
    supabase: SupabaseClient,
    onStarted: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val api = remember { buildTbmFunctionsApi() }
    var templates by remember { mutableStateOf<List<TbmTemplateRow>>(emptyList()) }
    var selectedWorkType by remember { mutableStateOf("fire") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val defaultEndAt = remember { ExpectedEndAtValidator.formatForServer(ExpectedEndAtValidator.nowKst().plusMinutes(15)) }
    var expectedEndAtInput by remember { mutableStateOf(defaultEndAt) }
    var locationInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        templates = runCatching { TbmRepository(supabase).fetchTemplates() }.getOrElse { emptyList() }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("새 TBM 세션 시작", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))

        // 작업유형 Dropdown
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = !dropdownExpanded },
        ) {
            OutlinedTextField(
                value = workTypeKorean(selectedWorkType),
                onValueChange = {},
                readOnly = true,
                label = { Text("작업유형") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
            ) {
                WorkTypeValidator.ALLOWED.toList().sorted().forEach { code ->
                    val title = templates.firstOrNull { it.workType == code }?.title ?: workTypeKorean(code)
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            selectedWorkType = code
                            dropdownExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = expectedEndAtInput,
            onValueChange = { expectedEndAtInput = it },
            label = { Text("예정 종료 시각 (ISO 8601)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
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
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                // 클라이언트 검증
                val workType = WorkTypeValidator.normalize(selectedWorkType)
                if (!WorkTypeValidator.isValid(workType)) {
                    resultMsg = "작업유형이 유효하지 않습니다"
                    return@Button
                }
                val endAtParsed = ExpectedEndAtValidator.parse(expectedEndAtInput)
                if (endAtParsed.isFailure) {
                    resultMsg = "예정 종료 시각 형식이 올바르지 않습니다 (ISO 8601 + offset 필요)"
                    return@Button
                }
                submitting = true
                scope.launch {
                    try {
                        val resp = api.callTbmStart(
                            url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                            auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                            body = TbmStartRequest(
                                leaderUserId = leaderUserId,
                                groupId = groupId,
                                workType = workType,
                                expectedEndAt = expectedEndAtInput,
                                location = locationInput.ifBlank { null },
                                notes = notesInput.ifBlank { null },
                            ),
                        )
                        resultMsg = when {
                            resp.isSuccessful && resp.body()?.ok == true -> {
                                val sid = resp.body()?.sessionId
                                if (sid != null) onStarted(sid)
                                "✓ 세션 시작됨 (id=$sid, 체크리스트 ${resp.body()?.checklistCount ?: 0}개)"
                            }
                            resp.code() == 409 -> "이미 오늘 세션이 존재합니다"
                            resp.code() == 400 -> "필수 항목 누락 또는 형식 오류"
                            else -> "오류 (${resp.code()})"
                        }
                    } catch (e: Exception) {
                        resultMsg = "네트워크 오류: ${e.message}"
                    } finally {
                        submitting = false
                    }
                }
            },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (submitting) "시작 중..." else "세션 시작")
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

internal fun buildTbmFunctionsApi(): TbmFunctionsApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TbmFunctionsApi::class.java)
