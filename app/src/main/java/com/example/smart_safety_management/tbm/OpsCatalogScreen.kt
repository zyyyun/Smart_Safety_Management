package com.example.smart_safety_management.tbm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.BuildConfig
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.launch

/**
 * Phase 12 — TBM-08 manager OPS 카탈로그.
 *
 * Modes:
 *   - List (default): 전체 template 카드 + is_active toggle Switch + 편집 진입 + 신규 추가
 *   - Edit: OpsEditScreen — 신규 또는 기존 template 편집 (HazardsListReducer 사용)
 *
 * Fix 2026-05-26: 이전 "Add draft OPS" stub 버튼은 hazards=[1] / controls=[1] hardcoded 호출.
 * 이제 OpsEditScreen 으로 진입 — 사용자가 실제로 hazards/controls/key_actions/checks 입력 가능.
 */
@Composable
fun OpsCatalogScreen(
    userId: String,
    supabase: SupabaseClient,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { TbmRepository(supabase) }
    val api = remember { buildTbmFunctionsApi() }
    var templates by remember { mutableStateOf<List<TbmTemplateRow>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EditMode>(EditMode.None) }

    suspend fun reload() {
        templates = repo.fetchTemplates()
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure { message = "불러오기 실패: ${it.message}" }
    }

    when (val mode = editing) {
        EditMode.None -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text("OPS 카탈로그", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(12.dp))
                message?.let {
                    Text(it, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }

                templates.forEach { template ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(template.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${template.workType} / ${template.targetDetector ?: "검출기 없음"}" +
                                            (if (template.isCustom) " · custom" else ""),
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        "위험 ${template.hazards.size} / 대책 ${template.controls.size} / 점검 ${template.checks.size}",
                                        fontSize = 12.sp,
                                    )
                                }
                                Switch(
                                    checked = template.isActive,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            val resp = api.callOpsToggle(
                                                url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                                auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                                body = OpsToggleRequest(
                                                    userId = userId,
                                                    templateId = template.templateId,
                                                    isActive = checked,
                                                ),
                                            )
                                            message = if (resp.isSuccessful && resp.body()?.ok == true) {
                                                        "${template.workType} 토글 완료"
                                            } else {
                                                "토글 실패 (${resp.code()})"
                                            }
                                            runCatching { reload() }
                                        }
                                    },
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row {
                                OutlinedButton(onClick = { editing = EditMode.Edit(template) }) {
                                    Text("편집", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { editing = EditMode.New },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("신규 OPS 추가")
                }
            }
        }
        is EditMode.New -> {
            OpsEditScreen(
                userId = userId,
                existing = null,
                onSaved = {
                    editing = EditMode.None
                    message = "신규 OPS 저장 완료"
                    scope.launch { runCatching { reload() } }
                },
                onCancel = { editing = EditMode.None },
            )
        }
        is EditMode.Edit -> {
            OpsEditScreen(
                userId = userId,
                existing = mode.template,
                onSaved = {
                    editing = EditMode.None
                    message = "${mode.template.workType} 수정 완료"
                    scope.launch { runCatching { reload() } }
                },
                onCancel = { editing = EditMode.None },
            )
        }
    }
}

private sealed interface EditMode {
    object None : EditMode
    object New : EditMode
    data class Edit(val template: TbmTemplateRow) : EditMode
}
