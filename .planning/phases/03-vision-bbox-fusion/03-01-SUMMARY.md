---
phase: 03-vision-bbox-fusion
plan: 01
subsystem: ai_agent (vision detection pipeline)
tags: [phase-3, vision, bbox-fusion, iou, hardhat, fusion-helpers, fusion-configs, detect-all]

# Dependency graph
requires:
  - phase: 02-vision-frames-required
    provides: "_detection_buffer deque pattern + frames_required N-consecutive rule + test fixture patterns (autouse reset_module_state, MagicMock bridge, capture stub)"
  - phase: 01-vision-demo-videos
    provides: "GenericYoloDetector.detect() + DETECTOR_CONFIGS 4 items + DetectResult dataclass"
provides:
  - "GenericYoloDetector.detect_all(image_bgr) -> list[DetectResult] — all bboxes, no extra inference"
  - "fusion_helpers.py: iou_xyxy, hardhat_is_on, compute_fusion_collision, compute_fusion_helmet_missing (4 pure functions)"
  - "fusion_configs.py: FUSION_CONFIGS dict (forklift_collision camera_ids=[4], helmet_missing camera_ids=[5])"
  - "test_fusion.py: 12 pytest cases for Phase 3 helper functions (D-10 cases 1-7 + subtests)"
affects:
  - 03-02 (Plan 02 will import detect_all + fusion_helpers + FUSION_CONFIGS for scheduler integration)
  - Phase 5 EVAL (test patterns and fusion math provide correctness baseline)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "pure-function fusion math: iou_xyxy + hardhat_is_on with no numpy or side effects"
    - "forward-compat fixture: getattr(scheduler, '_fusion_buffer', {}).clear() for Plan 02 additions"
    - "list accumulation pattern in detect_all: replaces best-selection loop, same inference result reused"
    - "y2 clamp bug fix from legacy bbox_utils.py: max(0.0, ...) not conditional reassignment"

key-files:
  created:
    - ai_agent/fusion_helpers.py
    - ai_agent/fusion_configs.py
    - ai_agent/tests/test_fusion.py
  modified:
    - ai_agent/yolo_detector.py

key-decisions:
  - "D-01: detect_all() reuses same yolov5/v8 inference result as detect() — zero extra inference cost. Best-selection loop replaced with list accumulation. target_classes filter applied identically."
  - "D-02: fusion_helpers.py pure functions — numpy-free. hardhat_is_on ports legacy bbox_utils.py:10-29 with y2 clamp bug fix. iou_xyxy guards union==0 → returns 0.0."
  - "D-03: FUSION_CONFIGS dict in separate fusion_configs.py (distinct from DETECTOR_CONFIGS — fusion vs single-detector semantics). 2 entries: forklift_collision (camera_ids=[4], threshold=0.3, N=3) and helmet_missing (camera_ids=[5], threshold=0.0, N=3)."
  - "T-03-01 mitigated: iou_xyxy returns 0.0 if union==0 — no ZeroDivisionError on degenerate bboxes."
  - "T-03-02 mitigated: hardhat_is_on uses fw=max(1, frame_width) — no clamp underflow on frame_width=0."

patterns-established:
  - "detect_all: list[DetectResult] from same inference result — Plan 02 scheduler wiring uses this directly"
  - "FUSION_CONFIGS structure mirrors DETECTOR_CONFIGS dict style — Plan 02 can iterate identically"
  - "test_fusion.py autouse fixture: reset_module_state with forward-compat _fusion_buffer.clear() — extend for Plan 02 Case 8"

requirements-completed: [FUSION-01, FUSION-02]

# Metrics
duration: 25min
completed: 2026-05-14
---

# Phase 3 Plan 01: bbox Fusion Foundation Summary

**Pure-function IoU math + head-region spatial matching (hardhat_is_on) + detect_all() multi-bbox API as a standalone testable layer — 12/12 pytest green, zero new dependencies**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-05-14T (session start)
- **Completed:** 2026-05-14T
- **Tasks:** 3/3
- **Files modified:** 4 (1 modified + 3 created)

## Accomplishments

- Added `detect_all()` to `GenericYoloDetector` — returns all bbox detections as `list[DetectResult]` using the same yolov5/yolov8 inference extraction already in `detect()`, with identical `target_classes` filtering. Zero extra inference calls.
- Created `fusion_helpers.py` with 4 pure functions: `iou_xyxy` (with ZeroDivisionError guard), `hardhat_is_on` (legacy pattern ported, y2 clamp bug fixed), `compute_fusion_collision`, `compute_fusion_helmet_missing`.
- Created `fusion_configs.py` with `FUSION_CONFIGS` dict: `forklift_collision` (camera_ids=[4], IoU>0.3, N=3) and `helmet_missing` (camera_ids=[5], head-region matching, N=3).
- 12 pytest cases all PASS — covers IoU edge cases (degenerate, partial, identical), collision threshold True/False/empty, helmet worn/missing/partial match scenarios. Full suite 20/20 (Phase 2 + 3).

## Task Commits

Each task was committed atomically:

1. **Task 1: detect_all() in yolo_detector.py** - `729a1b4` (feat)
2. **Task 2: fusion_helpers.py + fusion_configs.py** - `cd2528a` (feat)
3. **Task 3: test_fusion.py cases 1-7** - `40a9224` (test)

## Files Created/Modified

- `ai_agent/yolo_detector.py` — `detect_all()` method added to `GenericYoloDetector` (lines 186-228)
- `ai_agent/fusion_helpers.py` — 4 pure functions implementing FUSION-01 and FUSION-02 math
- `ai_agent/fusion_configs.py` — `FUSION_CONFIGS` dict with 2 fusion event entries
- `ai_agent/tests/test_fusion.py` — 12 pytest cases for fusion helper functions

## Decisions Made

- `detect_all()` return type annotation uses `"list[DetectResult]"` string literal — consistent with existing file style (`from __future__ import annotations` already present).
- `hardhat_is_on` y2 bug fix: legacy code had `if y2 < 0: y1 = 0` (wrong variable). Fixed to `y2 = max(0.0, person_bbox[3] - person_height * 3 / 4)`. Documented with inline comment referencing original.
- `fusion_configs.py` stores `threshold: 0.0` for `helmet_missing` rule (threshold is unused by `hardhat_missing` rule — form-field placeholder, consistent schema for Plan 02 dict iteration).
- D-04 (helmet detector `target_classes`→None, `frames_required`→1) is **NOT** in this plan — belongs to Plan 02 per `files_modified` frontmatter scope.

## Deviations from Plan

None — plan executed exactly as written.

The plan provided complete implementation code blocks for all three tasks. All acceptance criteria met without modification. No bugs or blocking issues encountered.

## Issues Encountered

None.

## Threat Model Results

| ID | Disposition | Evidence |
|----|-------------|----------|
| T-03-01 (iou_xyxy division by zero) | mitigated | `return 0.0 if union == 0 else intersection / union` — line 33 of fusion_helpers.py. test_iou_xyxy_degenerate_union_zero PASS. |
| T-03-02 (hardhat_is_on frame_width=0) | mitigated | `fw = max(1, frame_width)` at function entry — line 48 of fusion_helpers.py. |
| T-03-03 (detect_all unbounded list) | accepted | YOLO NMS always enabled; conf_thres provides secondary bound. Monitored in Phase 5 EVAL. |

## User Setup Required

None — no external service configuration required. All changes are pure Python functions and tests.

## Next Phase Readiness

Plan 02 (03-02) can proceed immediately. All dependencies are available:
- `detect_all()` in `GenericYoloDetector` (ready)
- `fusion_helpers.py` (ready — 4 pure functions)
- `FUSION_CONFIGS` in `fusion_configs.py` (ready)
- `test_fusion.py` (ready — Case 8 scheduler integration test will be added by Plan 02 Task 2)

Plan 02 work: add `_fusion_buffer` to `scheduler.py`, implement `_process_fusion_for_camera`, wire into `run_detection_cycle`, handle D-04 helmet suppression (Option A `disabled` flag), add `supabase/migrations/009_phase3_fusion_event_types.sql`.

---
*Phase: 03-vision-bbox-fusion*
*Completed: 2026-05-14*
*Executor: Claude Sonnet 4.6 (1M context)*
