---
phase: 03-vision-bbox-fusion
plan: 02
subsystem: ai_agent (vision detection pipeline)
tags: [phase-3, vision, bbox-fusion, scheduler, fusion-buffer, migration, D-04, FUSION-01, FUSION-02]

# Dependency graph
requires:
  - phase: 03-vision-bbox-fusion (plan 01)
    provides: "detect_all() + fusion_helpers.py 4 pure functions + FUSION_CONFIGS dict + test_fusion.py 12/12 PASS"
  - phase: 02-vision-frames-required
    provides: "_detection_buffer deque pattern + _detection_cooldown shared dict + frames_required N-consecutive rule + test fixtures"
provides:
  - "_fusion_buffer: dict[tuple[int,str], deque] module-level dict in scheduler.py (Phase 3 D-06)"
  - "_process_fusion_for_camera: 7-step function — cooldown → capture → detect_all × detectors → rule branch (iou_gt/hardhat_missing) → buffer push → N-consecutive → alarm fire (D-05)"
  - "fusion loop in run_detection_cycle: iterates FUSION_CONFIGS after detector loop (D-12 orthogonal)"
  - "D-04 disabled guard: if cfg.get('disabled', False): continue — skips helmet single-alarm path in detector loop"
  - "migration 009: '지게차 충돌 위험' event_type INSERT ON CONFLICT DO NOTHING (D-11) — applied to production DB"
  - "test_fusion.py Case 8: 3-cycle alarm trigger + spike-absorption tests (2 new cases → 14 total)"
affects:
  - Phase 5 EVAL (fusion alarm path and event_types are the measurement surface)
  - Phase 6 demo build (fusion events '지게차 충돌 위험' / '안전모 미착용' are demo incidents)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "_fusion_buffer mirrors _detection_buffer: same (camera_id, key) tuple key + deque(maxlen=max(5, N)) lazy init"
    - "disabled flag pattern for detectors: cfg.get('disabled', False) guard in run_detection_cycle — preserves detector instance for detect_all() while suppressing single-alarm path"
    - "shared _detection_cooldown for both single-detector and fusion alarms — same cooldown dict, different tuple keys"
    - "fusion loop orthogonal to detector loop: same run_detection_cycle, appended after, FUSION_CONFIGS iteration"
    - "helmet filter in hardhat_missing rule: label.lower() in ('helmet','hardhat') excludes 'head'-labeled bboxes from fusion matching"

key-files:
  created:
    - supabase/migrations/009_phase3_fusion_event_types.sql
  modified:
    - ai_agent/detector_configs.py
    - ai_agent/scheduler.py
    - ai_agent/tests/test_fusion.py

key-decisions:
  - "D-04 Option A: helmet disabled=True (not removed) — detector instance stays in detectors dict for detect_all() fusion lookup. Single-alarm path suppressed by guard. target_classes=None so detect_all() returns both head and helmet labels."
  - "Rule 1 fix: camera dict keys corrected in _process_fusion_for_camera — plan snippet used camera['id'] and camera.get('rtsp_url'), but production camera dict uses camera['camera_id'] and camera['live_url_detail'] (consistent with all 3 sibling functions)"
  - "T-03-04 mitigated: _fusion_buffer uses deque(maxlen=max(5, frames_required)) — unbounded growth prevented. 2 deque(maxlen=) hits in scheduler.py (detection + fusion)."
  - "T-03-06 mitigated: cfg.get('disabled', False) guard in run_detection_cycle detector loop — helmet single-alarm confirmed blocked."
  - "T-03-08 mitigated: _process_fusion_for_camera checks detectors.get(det_key) is None → returns [FUSION_ERR] with explicit log.warning."

patterns-established:
  - "fusion 7-step flow: mirrors _process_detection_for_camera exactly — same cooldown/capture/buffer/N-consecutive/alarm pattern, extended step 3 for multi-detector detect_all"
  - "fusion log prefix: [FUSION] / [no_fusion] / [fusion_skip_cooldown] / [no_fusion_yet] — consistent with Phase 2 [DETECT] / [no_detect] / [detect_skip_cooldown] / [no_alert_yet]"
  - "TDD RED/GREEN for scheduler integration: test added first (RED=fail at fixture level), then implementation (GREEN=22/22)"
  - "CAM fixture shape: {camera_id: int, live_url_detail: str} — matches Supabase camera row projection used throughout scheduler"

requirements-completed: [FUSION-01, FUSION-02]

# Metrics
duration: 40min
completed: 2026-05-14
---

# Phase 3 Plan 02: Scheduler Fusion Wiring Summary

**Fusion pipeline wired into scheduler — `_process_fusion_for_camera` (IoU collision + hardhat-missing rules), `_fusion_buffer` deque, D-04 helmet single-alarm suppression, and migration 009 `지게차 충돌 위험` applied to production DB — 22/22 pytest green**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-05-14T
- **Completed:** 2026-05-14T
- **Tasks:** 3/3
- **Files modified:** 3 (2 modified + 1 created; migration also created and pushed)

## Accomplishments

- Wired `_process_fusion_for_camera` into `scheduler.py` — 7-step flow mirroring `_process_detection_for_camera` with multi-detector `detect_all()`, IoU/hardhat-missing rule dispatch, `_fusion_buffer` N-consecutive guard, and alarm fire via existing `SupabaseBridge`.
- Implemented D-04 Option A helmet suppression: `disabled=True` in `DETECTOR_CONFIGS["helmet"]` + `if cfg.get("disabled", False): continue` guard in `run_detection_cycle` — helmet single-alarm path fully blocked while detector instance stays available for `detect_all()` calls in fusion.
- Applied migration 009 `지게차 충돌 위험` to production Supabase DB via `supabase db push --include-all`; verified via `supabase db query --linked`.
- 22/22 pytest green (14 fusion + 8 Phase 2 scheduler buffer tests): Case 8 adds 3-cycle alarm trigger integration test and spike-absorption test for `_process_fusion_for_camera`.

## Task Commits

Each task was committed atomically:

1. **Task 1: D-04 suppression + migration 009 file** - `769a0fc` (feat)
2. **Task 2 RED: Case 8 failing tests** - `a2a31c8` (test)
3. **Task 2 GREEN: scheduler fusion implementation** - `d546fb5` (feat)

_TDD: test commit RED (`a2a31c8`) then implementation commit GREEN (`d546fb5`)_

**Plan metadata:** (created after this summary)

## Files Created/Modified

- `ai_agent/detector_configs.py` — helmet entry: `disabled=True`, `target_classes=None`, `frames_required=1` (D-04)
- `ai_agent/scheduler.py` — `_fusion_buffer` dict + `_process_fusion_for_camera` (117 lines) + disabled guard + fusion loop + 2 new imports
- `ai_agent/tests/test_fusion.py` — autouse fixture updated (direct `_fusion_buffer.clear()`), Case 8 appended (2 new tests: `test_process_fusion_3cycles_triggers_alarm`, `test_process_fusion_no_alarm_when_not_consecutive`)
- `supabase/migrations/009_phase3_fusion_event_types.sql` — created + pushed to production DB

## Decisions Made

- D-04 Option A chosen (disabled flag) over Option B (removal): keeps detector instance in `detectors` dict for `_process_fusion_for_camera` to call `detect_all()` without loading model twice.
- camera dict shape fixed to use `camera["camera_id"]` + `camera["live_url_detail"]` — matches all 3 sibling functions (`_process_single_camera`, `_process_fall_for_camera`, `_process_detection_for_camera`).
- helmet label filter in `hardhat_missing` rule: only `label.lower() in ("helmet", "hardhat")` bboxes treated as helmets — excludes `"head"`-labeled unhelmeted detections which would falsely suppress the alarm.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] camera dict key mismatch in _process_fusion_for_camera**
- **Found during:** Pre-implementation advisor review (before Task 2 GREEN)
- **Issue:** Plan snippet used `camera["id"]` and `camera.get("rtsp_url") or camera.get("live_url_detail")` — production camera rows use `camera["camera_id"]` and `camera["live_url_detail"]` (all 3 existing sibling functions confirm this). Would cause `KeyError` at runtime.
- **Fix:** Changed to `camera_id = int(camera["camera_id"])` and `rtsp_url = camera["live_url_detail"]`. Updated CAM_FUSION test fixture to `{"camera_id": 4, "live_url_detail": "rtsp://fake/stream"}`.
- **Files modified:** `ai_agent/scheduler.py`, `ai_agent/tests/test_fusion.py`
- **Verification:** 22/22 pytest PASS including Case 8 integration test with corrected keys.
- **Committed in:** `d546fb5` (Task 2 GREEN feat commit), `a2a31c8` (Task 2 RED test commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Fix essential for runtime correctness. No scope creep.

## Issues Encountered

- `supabase db push` without `--include-all` flag reported migration 009 was "before the last migration on remote" (migration 010 exists). Re-ran with `--include-all`; applied cleanly on first attempt.

## Threat Model Results

| ID | Disposition | Evidence |
|----|-------------|----------|
| T-03-04 (deque unbounded growth) | mitigated | `deque(maxlen=max(5, frames_required))` in `_process_fusion_for_camera` line ~380. `grep 'deque(maxlen=' scheduler.py` returns 2 hits (detection + fusion). |
| T-03-05 (module state accessible) | accepted | 1-process PoC trusted boundary. Same disposition as Phase 2 T-02-01. |
| T-03-06 (disabled guard bypassed) | mitigated | `if cfg.get("disabled", False): continue` in `run_detection_cycle`. `DETECTOR_CONFIGS["helmet"]["disabled"] is True` assertion passes. |
| T-03-07 (SQL injection in migration) | accepted | Literal config constant, not user input. ON CONFLICT server-side predicate. |
| T-03-08 (person detector not loaded) | mitigated | `det = detectors.get(det_key); if det is None: return [FUSION_ERR] ...` with explicit `log.warning`. |

## Known Stubs

None — all fusion paths are fully wired. Event names map to production event_types table (verified via `supabase db query --linked`).

## User Setup Required

None — migration 009 already applied to production DB during Task 3 execution.

## Next Phase Readiness

FUSION-01 and FUSION-02 requirements fully met:
- `_process_fusion_for_camera` handles both `iou_gt` (forklift collision) and `hardhat_missing` (helmet missing) rules.
- Fusion loop runs every `run_detection_cycle` call, after the detector loop.
- Helmet single-alarm path suppressed; `안전모 미착용` events now generated only via fusion path.
- `지게차 충돌 위험` event_type in production DB.
- 22/22 pytest green.

Phase 3 is complete (Plans 01 + 02). Ready for Phase 5 EVAL (정량 지표) once Phase 4 status resolved.

---
*Phase: 03-vision-bbox-fusion*
*Completed: 2026-05-14*
*Executor: Claude Sonnet 4.6 (1M context)*
