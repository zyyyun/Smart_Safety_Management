# Daily Date, RTSP Button, and TBM Dashboard Design

## Goal

Fix two clear UI/data issues and redesign the TBM dashboard so managers can quickly understand today's TBM status and start a new TBM session.

## Scope

1. Daily safety checklist date handling
   - When a user selects a 작성일, saved data must use that selected date.
   - Main calendar indicators must reflect the selected 작성일, not today's date unless today was selected.

2. Home screen RTSP POC START button removal
   - Remove the unused RTSP POC START button from the main screen.
   - Do not remove RTSP internals, tests, scripts, or PoC code paths in this task.

3. TBM dashboard redesign
   - Use a balanced dashboard structure: top status summary, collapsible quick-start panel, then active/ended session lists.
   - Use a field-operations dashboard tone: compact metrics, clear status badges, restrained colors, and scan-friendly rows.
   - The quick-start panel supports selecting multiple OPS templates via chips.
   - Quick-start shows only summary counts per selected OPS, such as `위험 4 · 조치 5`.
   - Created sessions show hazards, controls, and checklists grouped by OPS source.

## Out of Scope

- Rebuilding the whole TBM module.
- Changing RTSP runtime behavior.
- Adding a new normalized `tbm_session_ops` table.
- Changing worker-side TBM sign-in flow beyond what is required to display grouped OPS data safely.

## UX Design

The TBM dashboard opens with today's operational summary:

- 진행중 count
- 완료 count
- 참여율 or participant progress when available
- 지연 or missed count when available

Below the summary, a quick-start panel stays collapsed by default or uses a compact expanded state when the user is starting a session. The panel contains:

- work scope
- expected end time
- location and notes as secondary fields
- OPS chips for multi-select
- selected OPS summary counts
- start action

OPS chips toggle selection directly. If no OPS is selected, the start button is disabled. The UI should avoid long always-visible forms.

Session cards remain split into active and ended groups. Active sessions are emphasized; ended sessions are visually quieter. Within a session detail view, OPS-derived data is grouped by OPS title so users can see where each hazard/control/checklist came from.

## Technical Design

### Daily Date Fix

Inspect the daily checklist create/update path and the calendar query path. The selected 작성일 should be passed into persistence explicitly. Any server-side or client-side `now()` fallback must only be used when the user did not choose a date.

### RTSP Button Removal

Find the main/home screen entry point for the RTSP POC START button and remove the visible control. Keep related backing code intact unless it becomes unreachable dead UI-only code.

### TBM Android Changes

`TbmDashboardScreen` should be reorganized into focused composables:

- `TbmDashboardSummary`
- `TbmQuickStartPanel`
- `OpsChipSelector`
- `SelectedOpsSummary`
- `SessionsSection`
- `SessionDetailCard`

`TbmStartSection` should move from a single `selectedTemplate` to `selectedTemplates: List<TbmTemplateRow>`.

For compatibility, Android should still send existing fields:

- `work_type`
- `hazards`
- `controls`

It should also include multi-OPS metadata, such as selected template IDs or selected OPS summaries, so the server and later UI can preserve source grouping.

### Edge Function Changes

Keep existing single-OPS `tbm-start` behavior valid. Extend the request handling to accept optional multi-OPS metadata. The safest implementation path is compatibility-first:

- existing single template requests continue working
- multi-template requests accept combined hazards/controls/checks from Android
- snapshots preserve enough source metadata for OPS-grouped display

Do not require a new DB relation table in this task.

## Error Handling

- Disable TBM start when no OPS is selected.
- Show a short loading or error message when groups or templates fail to load.
- If today's sessions are empty, show an empty state that points users to quick start.
- If a selected OPS has no hazards or controls, keep the chip selectable but show `위험 0 · 조치 0`.

## Testing

Add or update tests around the highest-risk logic:

- daily checklist selected-date mapping
- OPS multi-select aggregation and source grouping
- request model compatibility for single and multi OPS

Run at minimum:

- `:app:testDebugUnitTest`

Run `:app:assembleDebug` if the edit touches Compose UI or request models broadly enough to risk compile errors.

## Approved Decisions

- Processing approach: fix daily date and remove RTSP button directly; focus design effort on TBM.
- TBM structure: balanced status and quick-start layout.
- TBM visual tone: field-operations dashboard.
- Quick start: collapsible panel.
- OPS selection: chip multi-select.
- Multi-OPS behavior: one TBM session can include multiple OPS.
- OPS data display: group hazards, controls, and checklists by OPS source.
- Quick-start OPS detail: summary counts only.
- Implementation approach: compatibility extension, no full schema redesign.
