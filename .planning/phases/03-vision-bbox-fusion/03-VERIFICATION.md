---
phase: 03-vision-bbox-fusion
verified: 2026-05-14T00:00:00Z
status: human_needed
score: 5/6 must-haves verified (5 automated pass, 1 human-only)
overrides_applied: 0
human_verification:
  - test: "SELECT event_name FROM public.event_types WHERE event_name = '지게차 충돌 위험';"
    expected: "1 row returned"
    why_human: "Cannot verify remote Supabase DB row programmatically without credentials. SUMMARY claims supabase db push --include-all succeeded. Migration file 009 exists and is syntactically correct."
---

# Phase 3: 비전 — bbox 겹침/공간 매칭 Verification Report

**Phase Goal:** 지게차 충돌 위험과 안전모 미착용이 단일 detector 결과 대신 다중 모델 fusion (지게차+사람 IoU, 사람 head 영역 + helmet 객체 매칭) 으로 판정된다.
**Verified:** 2026-05-14
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `compute_fusion_collision` exists in fusion_helpers.py with IoU>threshold rule | ✓ VERIFIED | `ai_agent/fusion_helpers.py` lines 66-82: function present, `iou_xyxy(f.bbox, p.bbox) > iou_thres` comparison confirmed |
| 2 | `compute_fusion_helmet_missing` + `hardhat_is_on` exist in fusion_helpers.py with head-region matching | ✓ VERIFIED | `ai_agent/fusion_helpers.py` lines 36-111: both functions present, head-region spatial matching logic confirmed |
| 3 | `_process_fusion_for_camera` exists in scheduler.py with iou_gt + hardhat_missing rule branches, `_fusion_buffer` N=3 consecutive check, and event insert | ✓ VERIFIED | `ai_agent/scheduler.py` lines 308-440: 7-step function confirmed, both rule branches present, buffer push + N-consecutive guard confirmed, `register_ai_event` called at step 7 |
| 4 | helmet `disabled=True` suppresses single-alarm path in `run_detection_cycle` — 안전모 이벤트는 fusion 경로만 통해 발생 | ✓ VERIFIED | `ai_agent/detector_configs.py` line 51: `"disabled": True`; `ai_agent/scheduler.py` lines 467-469: `if cfg.get("disabled", False): continue` guard is BEFORE `_process_detection_for_camera` call |
| 5 | frames_required=3 applies to both fusion events (not single-frame alarms) — pytest 22/22 PASS | ✓ VERIFIED | `ai_agent/fusion_configs.py`: both entries have `"frames_required": 3`; pytest run confirmed: `22 passed in 5.58s`; Case 8 test asserts cycle 1+2 return `[no_fusion_yet]`, cycle 3 fires alarm |
| 6 | `지게차 충돌 위험` event_type exists in production Supabase event_types table | ? UNCERTAIN | Migration file `supabase/migrations/009_phase3_fusion_event_types.sql` exists and is correct (INSERT ON CONFLICT DO NOTHING). SUMMARY claims `supabase db push --include-all` succeeded. Cannot verify remote DB row without credentials. |

**Score:** 5/6 truths verified (1 human-only)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `ai_agent/yolo_detector.py` | `GenericYoloDetector.detect_all()` method | ✓ VERIFIED | Lines 186-227: method present, both yolov5/yolov8 inference branches, list accumulation (`out.append(DetectResult(...))`), target_classes filter applied, returns `[]` on None/empty |
| `ai_agent/fusion_helpers.py` | 4 pure functions: iou_xyxy, hardhat_is_on, compute_fusion_collision, compute_fusion_helmet_missing | ✓ VERIFIED | All 4 functions present; `return 0.0 if union == 0` guard confirmed (line 33); `y2 = max(0.0, ...)` bug fix confirmed (line 59); `fw = max(1, frame_width)` guard confirmed (line 48) |
| `ai_agent/fusion_configs.py` | FUSION_CONFIGS dict with forklift_collision and helmet_missing entries | ✓ VERIFIED | Both entries present; forklift_collision: threshold=0.3, frames_required=3, camera_ids=[4]; helmet_missing: threshold=0.0, frames_required=3, camera_ids=[5] |
| `ai_agent/detector_configs.py` | helmet disabled=True, target_classes=None, frames_required=1 | ✓ VERIFIED | Line 51: `"disabled": True`; line 63: `"frames_required": 1`; line 65: `"target_classes": None` |
| `ai_agent/scheduler.py` | _fusion_buffer dict + _process_fusion_for_camera + fusion loop + disabled guard | ✓ VERIFIED | Line 62: `_fusion_buffer: dict[tuple[int, str], deque] = {}`; lines 308-440: `_process_fusion_for_camera` (117 lines); lines 467-469: disabled guard; lines 488-505: fusion loop iterating `FUSION_CONFIGS.items()` |
| `ai_agent/tests/test_fusion.py` | pytest cases 1-7 helpers + Case 8 integration, min 11 test functions | ✓ VERIFIED | 14 `def test_` functions counted; autouse fixture directly references `scheduler._fusion_buffer.clear()` (not forward-compat getattr); Case 8: `test_process_fusion_3cycles_triggers_alarm` + `test_process_fusion_no_alarm_when_not_consecutive` present |
| `supabase/migrations/009_phase3_fusion_event_types.sql` | 지게차 충돌 위험 INSERT, ON CONFLICT DO NOTHING | ✓ VERIFIED | File exists; contains `('지게차 충돌 위험')`; `ON CONFLICT (event_name) DO NOTHING` present; `안전모 미착용` not re-inserted (correct) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `scheduler.py (run_detection_cycle)` | `DETECTOR_CONFIGS['helmet']['disabled']` | `if cfg.get("disabled", False): continue` | ✓ WIRED | Guard at line 467, positioned BEFORE `_process_detection_for_camera` is called — helmet single-alarm path fully blocked |
| `scheduler.py (_process_fusion_for_camera)` | `fusion_helpers.py (compute_fusion_collision / compute_fusion_helmet_missing)` | rule branch dispatch | ✓ WIRED | Lines 380 and 389: both functions called in correct rule branches |
| `scheduler.py (run_detection_cycle fusion loop)` | `fusion_configs.py (FUSION_CONFIGS)` | `for fusion_key, fcfg in FUSION_CONFIGS.items()` | ✓ WIRED | Line 488: fusion loop confirmed, appended after detector loop |
| `supabase/migrations/009_phase3_fusion_event_types.sql` | `event_types table` | `INSERT ON CONFLICT DO NOTHING` | ? UNCERTAIN | File is correct and was pushed per SUMMARY, but DB row unverifiable without credentials |
| `scheduler.py (_process_fusion_for_camera)` | camera dict keys | `camera["camera_id"]`, `camera["live_url_detail"]` | ✓ WIRED | Lines 327-328: corrected keys match all sibling functions; plan deviation was auto-fixed. Test CAM_FUSION fixture also uses `{"camera_id": 4, "live_url_detail": "..."}` (line 185 of test_fusion.py) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `scheduler.py _process_fusion_for_camera` | `fusion_result` (bool) | `compute_fusion_collision` / `compute_fusion_helmet_missing` called with live `detect_all()` output | Yes — functions compute from actual bbox coordinates, not hardcoded | ✓ FLOWING |
| `scheduler.py _process_fusion_for_camera` | `det_results` (dict) | `det.detect_all(img)` for each detector in `cfg["detectors_required"]` | Yes — calls real detector inference; test stubs confirm the data path with synthetic bboxes | ✓ FLOWING |
| `fusion_helpers.py compute_fusion_collision` | return bool | `iou_xyxy(f.bbox, p.bbox) > iou_thres` pure math | Yes — no hardcoded values | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full pytest suite 22/22 | `cd ai_agent && python -m pytest tests/ -q` | `22 passed in 5.58s` | ✓ PASS |
| Case 8: 3-cycle triggers alarm, 2-cycle does not | included in pytest suite | PASS (part of 22 total) | ✓ PASS |
| iou_xyxy ZeroDivisionError guard | included in pytest suite (test_iou_xyxy_degenerate_union_zero) | PASS | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| FUSION-01 | 03-01-PLAN.md, 03-02-PLAN.md | 지게차+사람 bbox IoU > 0.3이 N 프레임 연속 → "지게차 충돌 위험" | ✓ SATISFIED | `compute_fusion_collision` in fusion_helpers.py + `_process_fusion_for_camera` iou_gt rule + N=3 consecutive buffer + 22/22 pytest including Case 8 integration test |
| FUSION-02 | 03-01-PLAN.md, 03-02-PLAN.md | 사람 head 영역 helmet 매칭 없으면 "안전모 미착용" — 단일 detector 아님 | ✓ SATISFIED | `hardhat_is_on` + `compute_fusion_helmet_missing` in fusion_helpers.py + hardhat_missing rule in scheduler + helmet `disabled=True` suppresses single-alarm path + helmet label filter (`label.lower() in ("helmet", "hardhat")`) prevents head-labeled bboxes from falsely matching |

### ROADMAP Success Criteria Coverage

| SC | Text | Status |
|----|------|--------|
| SC #1 | `_process_collision_check` helper + IoU>0.3 N=frames_required consecutive → "지게차 충돌 위험" event insert. Unit test with IoU>0.3 synthetic input passes. | ✓ SATISFIED — Implemented as `_process_fusion_for_camera` with `iou_gt` rule. ROADMAP used `_process_collision_check` as working name; final name is `_process_fusion_for_camera`. Functionality equivalent and verified. |
| SC #2 | `hardhat_is_on` pattern ported from legacy — head area no helmet → "안전모 미착용" event; not dependent on single detector head/no_helmet class output | ✓ SATISFIED — `hardhat_is_on` in fusion_helpers.py with legacy y2 bug fix. `hardhat_missing` rule in scheduler filters to `("helmet","hardhat")` labels only, explicitly excluding `"head"` class. |
| SC #3 | frames_required=3 applies to FUSION-01/02 — 1-frame spike → no alarm; N consecutive → 1 alarm | ✓ SATISFIED — Both FUSION_CONFIGS entries have `frames_required: 3`. Scheduler `_process_fusion_for_camera` implements identical N-consecutive buffer check. Case 8 test_process_fusion_3cycles_triggers_alarm + test_process_fusion_no_alarm_when_not_consecutive both PASS. |

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| None | No TODO/FIXME/placeholder/stub patterns found in key phase files | — | — |

No stubs, placeholder returns, or disconnected wiring found. The `return []` patterns in `detect_all()` and `compute_fusion_helmet_missing` are correct early-exit guards (not stubs).

### Human Verification Required

#### 1. Production DB: event_types table contains '지게차 충돌 위험'

**Test:** Run the following SQL against the production Supabase database:
```sql
SELECT event_name FROM public.event_types WHERE event_name = '지게차 충돌 위험';
```
**Expected:** 1 row returned with `event_name = '지게차 충돌 위험'`
**Why human:** Remote Supabase DB row cannot be verified programmatically without database credentials. Migration file `supabase/migrations/009_phase3_fusion_event_types.sql` exists and is syntactically correct. SUMMARY reports `supabase db push --include-all` completed successfully (with a note that `--include-all` was required because migration 010 existed). The migration is idempotent (ON CONFLICT DO NOTHING), so re-running it is safe if uncertain.

### Gaps Summary

No automated verification gaps. All code artifacts verified at all four levels (exists, substantive, wired, data flowing). The only open item is the production DB row, which requires human confirmation via SQL query.

**Notable design decision verified:** The helmet label filter in `_process_fusion_for_camera` (line 385-388 of scheduler.py) — `label.lower() in ("helmet", "hardhat")` — correctly excludes `"head"`-labeled bbox detections from the helmet matching set. This is critical for FUSION-02 correctness: the helmet detector returns `label='head'` for unhelmeted heads (Phase 1 configuration). Without this filter, an unhelmeted head would be treated as a worn helmet and falsely suppress the alarm. The filter was added as a deviation from the PLAN (which did not include it) and is an essential correctness fix. It is present in the actual code and the 22/22 pytest suite passes through this code path.

---

_Verified: 2026-05-14_
_Verifier: Claude (gsd-verifier)_
