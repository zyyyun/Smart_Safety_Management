package com.example.smart_safety_management.tbm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch

private val TARGET_DETECTOR_OPTIONS = listOf("fire", "helmet", "forklift", "person", "fall")
private val CONTROL_LEVEL_OPTIONS = listOf("eliminate", "substitute", "control")

/**
 * Phase 12 — TBM-08 OPS 신규/편집 form.
 *
 * existing == null  → 신규 (ops-create)
 * existing != null  → 편집 (ops-update)
 *
 * 사용자 입력:
 *   - work_type (신규 시만, 기존 시 read-only)
 *   - title / description
 *   - hazards: List<TbmTemplateHazard>  add/edit/delete (HazardsListReducer)
 *   - controls: List<TbmTemplateControl> add/edit/delete + level dropdown
 *   - key_actions: List<TbmTemplateAction> add/delete
 *   - checks: List<String> add/delete
 *   - target_detector: dropdown (fire/helmet/forklift/person/fall 또는 미선택)
 *   - is_active: Switch
 *
 * 저장 → ops-create or ops-update, 응답 후 onSaved() callback.
 */
@Composable
fun OpsEditScreen(
    userId: String,
    existing: TbmTemplateRow?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val api = remember { buildTbmFunctionsApi() }
    val isNew = existing == null

    var workType by remember { mutableStateOf(existing?.workType ?: "") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var hazards by remember { mutableStateOf(existing?.hazards ?: emptyList()) }
    var controls by remember { mutableStateOf(existing?.controls ?: emptyList()) }
    var actions by remember { mutableStateOf(existing?.keyActions ?: emptyList()) }
    var checks by remember { mutableStateOf(existing?.checks ?: emptyList()) }
    var targetDetector by remember { mutableStateOf(existing?.targetDetector) }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }

    var newHazardText by remember { mutableStateOf("") }
    var newControlText by remember { mutableStateOf("") }
    var newControlHazardId by remember { mutableStateOf("") }
    var newControlLevel by remember { mutableStateOf("control") }
    var newActionText by remember { mutableStateOf("") }
    var newCheckText by remember { mutableStateOf("") }

    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(if (isNew) "신규 OPS 추가" else "OPS 편집", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = workType,
            onValueChange = { if (isNew) workType = it.lowercase().take(40) },
            label = { Text("work_type (소문자, _ 가능)") },
            enabled = isNew,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(120) },
            label = { Text("title (작업 명칭)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(500) },
            label = { Text("description (선택)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        DetectorDropdown(selected = targetDetector, onSelect = { targetDetector = it })
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("활성화 (is_active)", modifier = Modifier.weight(1f))
            Switch(checked = isActive, onCheckedChange = { isActive = it })
        }
        Spacer(Modifier.height(16.dp))

        // Hazards
        SectionHeader("잠재위험 (hazards) — 1개 이상 필수")
        hazards.forEach { h ->
            ItemCard(text = "${h.id}: ${h.text}", onRemove = { hazards = HazardsListReducer.removeHazardById(hazards, h.id) })
        }
        InlineAddRow(
            value = newHazardText,
            onValueChange = { newHazardText = it },
            placeholder = "새 위험 항목",
            onAdd = {
                hazards = HazardsListReducer.addHazard(hazards, newHazardText)
                newHazardText = ""
            },
        )
        Spacer(Modifier.height(16.dp))

        // Controls
        SectionHeader("대책 (controls) — 1개 이상 필수")
        controls.forEach { c ->
            ItemCard(
                text = "${c.id} (${c.level}) → ${c.hazardId ?: "—"}: ${c.text}",
                onRemove = { controls = HazardsListReducer.removeControlById(controls, c.id) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newControlHazardId,
                onValueChange = { newControlHazardId = it.trim() },
                label = { Text("hazard_id") },
                modifier = Modifier.width(110.dp),
            )
            Spacer(Modifier.width(6.dp))
            ControlLevelDropdown(selected = newControlLevel, onSelect = { newControlLevel = it })
        }
        Spacer(Modifier.height(6.dp))
        InlineAddRow(
            value = newControlText,
            onValueChange = { newControlText = it },
            placeholder = "새 대책 본문",
            onAdd = {
                controls = HazardsListReducer.addControl(
                    list = controls,
                    text = newControlText,
                    hazardId = newControlHazardId.takeIf { it.isNotBlank() },
                    level = newControlLevel,
                )
                newControlText = ""
                newControlHazardId = ""
            },
        )
        Spacer(Modifier.height(16.dp))

        // Key actions
        SectionHeader("핵심 안전조치 (key_actions, 선택)")
        actions.forEach { a ->
            ItemCard(text = "${a.id}: ${a.text}", onRemove = { actions = HazardsListReducer.removeActionById(actions, a.id) })
        }
        InlineAddRow(
            value = newActionText,
            onValueChange = { newActionText = it },
            placeholder = "새 핵심 조치",
            onAdd = {
                actions = HazardsListReducer.addAction(actions, newActionText)
                newActionText = ""
            },
        )
        Spacer(Modifier.height(16.dp))

        // Checks
        SectionHeader("자율점검 (checks, 선택)")
        checks.forEachIndexed { idx, text ->
            ItemCard(
                text = "${idx + 1}. $text",
                onRemove = { checks = checks.filterIndexed { i, _ -> i != idx } },
            )
        }
        InlineAddRow(
            value = newCheckText,
            onValueChange = { newCheckText = it },
            placeholder = "새 자율점검 항목",
            onAdd = {
                val trimmed = newCheckText.trim()
                if (trimmed.isNotEmpty()) checks = checks + trimmed
                newCheckText = ""
            },
        )
        Spacer(Modifier.height(16.dp))

        message?.let {
            Text(it, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, enabled = !saving) { Text("취소") }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = !saving && workType.isNotBlank() && title.isNotBlank()
                    && hazards.isNotEmpty() && controls.isNotEmpty(),
                onClick = {
                    saving = true
                    message = null
                    scope.launch {
                        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications"
                        val apiKey = BuildConfig.SUPABASE_ANON_KEY
                        val auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
                        val safeWorkType = workType.lowercase().trim()
                        val safeTitle = title.trim()
                        val safeDesc = description.trim().ifEmpty { null }
                        try {
                            val resp = if (isNew) {
                                api.callOpsCreate(
                                    url = baseUrl, apiKey = apiKey, auth = auth,
                                    body = OpsCreateRequest(
                                        userId = userId,
                                        workType = safeWorkType,
                                        title = safeTitle,
                                        description = safeDesc,
                                        hazards = hazards,
                                        controls = controls,
                                        keyActions = actions,
                                        checks = checks,
                                        targetDetector = targetDetector,
                                        isActive = isActive,
                                    ),
                                )
                            } else {
                                api.callOpsUpdate(
                                    url = baseUrl, apiKey = apiKey, auth = auth,
                                    body = OpsUpdateRequest(
                                        userId = userId,
                                        templateId = existing!!.templateId,
                                        title = safeTitle,
                                        description = safeDesc,
                                        hazards = hazards,
                                        controls = controls,
                                        keyActions = actions,
                                        checks = checks,
                                        targetDetector = targetDetector,
                                        isActive = isActive,
                                    ),
                                )
                            }
                            if (resp.isSuccessful && resp.body()?.ok == true) {
                                onSaved()
                            } else {
                                message = "저장 실패 (${resp.code()}): ${resp.body()?.error ?: resp.message()}"
                            }
                        } catch (t: Throwable) {
                            message = "저장 오류: ${t.message}"
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (isNew) "추가" else "저장") }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ItemCard(text: String, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onRemove) { Text("삭제", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun InlineAddRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onAdd: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 12.sp) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAdd, enabled = value.isNotBlank()) { Text("추가") }
    }
}

@Composable
private fun DetectorDropdown(selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text("target_detector: ${selected ?: "(선택 안 함)"}")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("(선택 안 함)") },
            onClick = { onSelect(null); expanded = false },
        )
        TARGET_DETECTOR_OPTIONS.forEach { opt ->
            DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
        }
    }
}

@Composable
private fun ControlLevelDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) { Text("level: $selected") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        CONTROL_LEVEL_OPTIONS.forEach { opt ->
            DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
        }
    }
}
