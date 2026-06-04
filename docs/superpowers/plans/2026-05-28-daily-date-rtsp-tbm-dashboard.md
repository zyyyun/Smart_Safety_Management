# Daily Date, RTSP Button, and TBM Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix daily safety checklist date display, remove the unused RTSP POC button, and redesign the TBM dashboard with multi-OPS chip selection.

**Architecture:** Keep the existing Android/Supabase architecture. Make the daily date fix at the client mapping layer, remove only visible RTSP POC UI, and refactor TBM into focused Compose helpers while extending the current `tbm-start` request shape compatibly.

**Tech Stack:** Android Kotlin, Jetpack Compose Material/Material3, Retrofit/Gson, Supabase Edge Function TypeScript, existing Gradle Android unit tests.

---

## File Structure

- Modify `app/src/main/java/com/example/smart_safety_management/MonthlyList.kt`
  - Group daily checklist calendar rows by the selected checklist date field instead of `createdAt`.
- Modify `app/src/main/java/com/example/smart_safety_management/DailyListActivity.kt`
  - Normalize intent-provided year/month/day into ISO `yyyy-MM-dd` so selected dates round-trip consistently.
- Modify `app/src/main/java/com/example/smart_safety_management/HomeActivity.kt`
  - Remove the visible RTSP POC START button from the manager home screen.
- Modify `app/src/main/java/com/example/smart_safety_management/tbm/TbmModels.kt`
  - Add optional multi-OPS request metadata and source fields for OPS grouping.
- Modify `app/src/main/java/com/example/smart_safety_management/tbm/TbmStartSection.kt`
  - Change single OPS dropdown to multi-select chips and selected OPS summaries.
- Modify `app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt`
  - Recompose dashboard into summary, quick start, active sessions, and ended sessions.
- Modify `supabase/functions/notifications/index.ts`
  - Keep single `tbm-start` compatible while accepting multi-OPS metadata and combined snapshots.
- Add or modify unit tests under `app/src/test/java/com/example/smart_safety_management/`
  - Cover date extraction and OPS aggregation helpers.

---

### Task 1: Daily Checklist Date Mapping

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/MonthlyList.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/DailyListActivity.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/DailyChecklistDateTest.kt`

- [ ] **Step 1: Add a pure date extraction helper test**

Create `DailyChecklistDateTest.kt` with tests for the intended date precedence:

```kotlin
package com.example.smart_safety_management

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyChecklistDateTest {
    @Test
    fun usesExplicitChecklistDateBeforeCreatedAt() {
        assertEquals(
            LocalDate.parse("2026-05-20"),
            dailyChecklistDisplayDate(date = "2026-05-20", createdAt = "2026-05-28T01:23:45Z")
        )
    }

    @Test
    fun fallsBackToCreatedAtDateWhenChecklistDateMissing() {
        assertEquals(
            LocalDate.parse("2026-05-28"),
            dailyChecklistDisplayDate(date = null, createdAt = "2026-05-28T01:23:45Z")
        )
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DailyChecklistDateTest"`

Expected: FAIL because `dailyChecklistDisplayDate` does not exist.

- [ ] **Step 3: Add the helper and switch monthly grouping**

In `MonthlyList.kt`, add:

```kotlin
@RequiresApi(Build.VERSION_CODES.O)
internal fun dailyChecklistDisplayDate(date: String?, createdAt: String?): LocalDate {
    val explicit = date?.takeIf { it.length >= 10 }?.substring(0, 10)
    val fallback = createdAt?.takeIf { it.length >= 10 }?.substring(0, 10)
    return LocalDate.parse(explicit ?: fallback ?: LocalDate.now().toString(), DateTimeFormatter.ISO_DATE)
}
```

Then replace the grouping block that parses `it.createdAt` with:

```kotlin
val grouped = checks.groupBy {
    try {
        dailyChecklistDisplayDate(it.date, it.createdAt)
    } catch (e: Exception) {
        LocalDate.now()
    }
}
```

If the DTO field is not named `date`, inspect the DTO and use the actual selected-date field name from `GetDailyChecksResponse`.

- [ ] **Step 4: Normalize intent date construction**

In `DailyListActivity.kt`, replace raw `"$y-$m-$d"` construction with zero-padded ISO:

```kotlin
if (y != -1 && m != -1 && d != -1) "%04d-%02d-%02d".format(y, m, d) else null
```

- [ ] **Step 5: Run test and commit**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DailyChecklistDateTest"`

Expected: PASS.

Commit:

```bash
git add app/src/main/java/com/example/smart_safety_management/MonthlyList.kt app/src/main/java/com/example/smart_safety_management/DailyListActivity.kt app/src/test/java/com/example/smart_safety_management/DailyChecklistDateTest.kt
git commit -m "fix: use selected daily checklist date"
```

---

### Task 2: Remove Home RTSP POC Button

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/HomeActivity.kt`

- [ ] **Step 1: Locate the visible RTSP POC control**

Search in `HomeActivity.kt` for labels or handlers matching `RTSP`, `POC`, `START`, or related test actions.

- [ ] **Step 2: Remove only the visible button/composable**

Delete the composable button or card that exposes RTSP POC START on the main screen. Keep navigation, services, scripts, and ai_agent PoC code intact.

- [ ] **Step 3: Compile check and commit**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: existing Android unit tests PASS.

Commit:

```bash
git add app/src/main/java/com/example/smart_safety_management/HomeActivity.kt
git commit -m "fix: remove rtsp poc home button"
```

---

### Task 3: Add OPS Aggregation Model Helpers

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/tbm/TbmModels.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/tbm/TbmOpsAggregationTest.kt`

- [ ] **Step 1: Write aggregation tests**

Create `TbmOpsAggregationTest.kt`:

```kotlin
package com.example.smart_safety_management.tbm

import org.junit.Assert.assertEquals
import org.junit.Test

class TbmOpsAggregationTest {
    @Test
    fun aggregatesHazardsControlsAndChecksWithOpsSource() {
        val forklift = TbmTemplateRow(
            templateId = 1,
            workType = "forklift",
            title = "지게차 작업",
            hazards = listOf(TbmTemplateHazard("h1", "충돌 위험")),
            controls = listOf(TbmTemplateControl("c1", "h1", "control", "유도자 배치")),
            checks = listOf("작업 반경 통제")
        )
        val hot = TbmTemplateRow(
            templateId = 2,
            workType = "hot",
            title = "고온 작업",
            hazards = listOf(TbmTemplateHazard("h2", "화재 위험")),
            controls = listOf(TbmTemplateControl("c2", "h2", "control", "소화기 비치")),
            checks = listOf("불티 비산 확인")
        )

        val result = aggregateSelectedOps(listOf(forklift, hot))

        assertEquals(listOf(1, 2), result.templateIds)
        assertEquals(2, result.hazards.size)
        assertEquals("지게차 작업", result.hazards[0].opsTitle)
        assertEquals(2, result.controls.size)
        assertEquals("고온 작업", result.controls[1].opsTitle)
        assertEquals(2, result.checks.size)
        assertEquals("지게차 작업", result.checks[0].opsTitle)
    }
}
```

- [ ] **Step 2: Run focused test and verify RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TbmOpsAggregationTest"`

Expected: FAIL because aggregation types do not exist.

- [ ] **Step 3: Add source-aware models and helper**

In `TbmModels.kt`, add optional source metadata to hazards/controls and new check metadata:

```kotlin
@Serializable
data class TbmTemplateHazard(
    val id: String,
    val text: String,
    @SerialName("is_custom") @SerializedName("is_custom") val isCustom: Boolean = false,
    @SerialName("ops_template_id") @SerializedName("ops_template_id") val opsTemplateId: Int? = null,
    @SerialName("ops_title") @SerializedName("ops_title") val opsTitle: String? = null,
)
```

Apply the same optional `opsTemplateId` and `opsTitle` fields to `TbmTemplateControl`.

Add:

```kotlin
@Serializable
data class TbmChecklistSourceItem(
    val text: String,
    @SerialName("ops_template_id") @SerializedName("ops_template_id") val opsTemplateId: Int,
    @SerialName("ops_title") @SerializedName("ops_title") val opsTitle: String,
)

data class AggregatedOpsSelection(
    val templateIds: List<Int>,
    val opsTitles: List<String>,
    val hazards: List<TbmTemplateHazard>,
    val controls: List<TbmTemplateControl>,
    val checks: List<TbmChecklistSourceItem>,
)

fun aggregateSelectedOps(templates: List<TbmTemplateRow>): AggregatedOpsSelection =
    AggregatedOpsSelection(
        templateIds = templates.map { it.templateId },
        opsTitles = templates.map { it.title },
        hazards = templates.flatMap { template ->
            template.hazards.map {
                it.copy(opsTemplateId = template.templateId, opsTitle = template.title)
            }
        },
        controls = templates.flatMap { template ->
            template.controls.map {
                it.copy(opsTemplateId = template.templateId, opsTitle = template.title)
            }
        },
        checks = templates.flatMap { template ->
            template.checks.map {
                TbmChecklistSourceItem(it, template.templateId, template.title)
            }
        },
    )
```

Extend `TbmStartRequest` with optional metadata:

```kotlin
@SerialName("template_ids") @SerializedName("template_ids") val templateIds: List<Int> = emptyList(),
@SerialName("ops_titles") @SerializedName("ops_titles") val opsTitles: List<String> = emptyList(),
val checks: List<TbmChecklistSourceItem> = emptyList(),
```

- [ ] **Step 4: Run test and commit**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TbmOpsAggregationTest"`

Expected: PASS.

Commit:

```bash
git add app/src/main/java/com/example/smart_safety_management/tbm/TbmModels.kt app/src/test/java/com/example/smart_safety_management/tbm/TbmOpsAggregationTest.kt
git commit -m "feat: aggregate selected tbm ops"
```

---

### Task 4: Redesign TBM Quick Start and Dashboard UI

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/tbm/TbmStartSection.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt`

- [ ] **Step 1: Refactor `TbmStartSection` state**

Replace:

```kotlin
var selectedTemplate by remember { mutableStateOf<TbmTemplateRow?>(null) }
```

with:

```kotlin
var selectedTemplates by remember { mutableStateOf<List<TbmTemplateRow>>(emptyList()) }
```

After templates load, initialize with the first active template when available:

```kotlin
selectedTemplates = templates.firstOrNull()?.let { listOf(it) } ?: emptyList()
```

- [ ] **Step 2: Replace dropdown with chip selector**

Render templates as Material3 filter chips or compact buttons. Each chip toggles membership in `selectedTemplates`. Selected chips use `SsmColors.TextInfo` background/tint; unselected chips use neutral border.

For each selected template, show:

```kotlin
Text("${template.title}: 위험 ${template.hazards.size} · 조치 ${template.controls.size}")
```

- [ ] **Step 3: Build request from aggregated OPS**

Before submit:

```kotlin
val aggregated = aggregateSelectedOps(selectedTemplates)
```

Send:

```kotlin
TbmStartRequest(
    leaderUserId = leaderUserId,
    groupId = group.groupId,
    workType = selectedTemplates.first().workType,
    workScope = workScopeInput.trim(),
    expectedEndAt = expectedEndAt,
    location = locationInput.takeIf { it.isNotBlank() },
    notes = notesInput.takeIf { it.isNotBlank() },
    hazards = aggregated.hazards,
    controls = aggregated.controls,
    templateIds = aggregated.templateIds,
    opsTitles = aggregated.opsTitles,
    checks = aggregated.checks,
)
```

Disable the start button when `selectedTemplates.isEmpty()`.

- [ ] **Step 4: Reorganize dashboard**

In `TbmDashboardScreen.kt`, add focused private composables:

```kotlin
@Composable
private fun TbmDashboardSummary(activeCount: Int, endedCount: Int) { ... }

@Composable
private fun TbmQuickStartContainer(content: @Composable () -> Unit) { ... }
```

The top-level order becomes:

1. title row
2. summary metrics
3. collapsible quick-start panel
4. active sessions
5. ended sessions

- [ ] **Step 5: Run Android tests and commit**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: PASS.

Commit:

```bash
git add app/src/main/java/com/example/smart_safety_management/tbm/TbmStartSection.kt app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt
git commit -m "feat: redesign tbm dashboard quick start"
```

---

### Task 5: Extend `tbm-start` Edge Function Compatibility

**Files:**
- Modify: `supabase/functions/notifications/index.ts`

- [ ] **Step 1: Extend request destructuring**

In the `case "tbm-start"` block, include:

```ts
template_ids,
ops_titles,
checks,
```

- [ ] **Step 2: Keep single-template lookup compatible**

Keep the existing `work_type` template lookup as fallback. For multi-OPS requests, use client-provided `hazards`, `controls`, and `checks` after cleaning.

Set:

```ts
const safeChecks = Array.isArray(checks)
  ? cleanStringArray(checks.map((c) => typeof c === "string" ? c : c?.text).filter(Boolean), 30, 180)
  : cleanStringArray(tmpl.checks, 30, 180);
```

- [ ] **Step 3: Preserve OPS metadata in snapshots**

Do not strip `ops_template_id` or `ops_title` from hazard/control objects unless the existing sanitizer rejects them. If `cleanHazards` or `cleanControls` drops unknown fields, extend those cleaners to preserve:

```ts
ops_template_id: Number.isInteger(item.ops_template_id) ? item.ops_template_id : undefined,
ops_title: typeof item.ops_title === "string" ? item.ops_title.slice(0, 80) : undefined,
```

- [ ] **Step 4: Use safe checks for checklist insert**

Replace:

```ts
const checks = cleanStringArray(tmpl.checks, 30, 180);
```

with:

```ts
const checkTexts = safeChecks;
```

and map `checkTexts`.

- [ ] **Step 5: Commit**

Commit:

```bash
git add supabase/functions/notifications/index.ts
git commit -m "feat: accept multi ops tbm start"
```

---

### Task 6: Final Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Run debug build**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Review working tree**

Run: `git status --short`

Expected: only pre-existing untracked files remain unless build outputs are ignored.

- [ ] **Step 4: Final commit if needed**

If verification caused small follow-up fixes, commit them:

```bash
git add <changed-files>
git commit -m "fix: polish tbm dashboard implementation"
```

---

## Self-Review

- Spec coverage: daily selected date, RTSP button removal, TBM field-operations dashboard, collapsible quick start, OPS chip multi-select, OPS grouped source metadata, and compatibility-first Edge Function are covered by Tasks 1-5.
- Completeness scan: all tasks contain concrete files, commands, and expected outcomes.
- Type consistency: `TbmChecklistSourceItem`, `AggregatedOpsSelection`, `aggregateSelectedOps`, and added `TbmStartRequest` fields are defined before use.
