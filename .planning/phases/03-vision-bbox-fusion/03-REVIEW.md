---
phase: 03-vision-bbox-fusion
reviewed: 2026-05-14T10:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - ai_agent/fusion_helpers.py
  - ai_agent/fusion_configs.py
  - ai_agent/tests/test_fusion.py
  - ai_agent/yolo_detector.py
  - supabase/migrations/009_phase3_fusion_event_types.sql
  - ai_agent/detector_configs.py
  - ai_agent/scheduler.py
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase 3: Code Review Report

**Reviewed:** 2026-05-14T10:00:00Z
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 3 adds bbox-level fusion rules (FUSION-01 forklift-person IoU, FUSION-02 helmet-missing geometry) on top of the Phase 2 N-consecutive detection buffer.  The IoU math, union-zero guard, and `hardhat_is_on` geometry are structurally correct.  SQL migration uses literal constants — no injection vector.  Snapshot filename uses `int(now*1000)` and a dict key from `FUSION_CONFIGS` — no path traversal.

One structural defect needs immediate attention before this ships to a production cron: the `_process_fusion_for_camera` temp file is only cleaned up in the alarm-fire path, leaving a `fusion_*.jpg` on disk for every non-alarm early-return.  This is the dominant code path at run-time.

Two additional warnings concern a label-filter/test mismatch that could produce false-positive helmet alarms in production, and an invertible head-region bounding box in degenerate frames.

---

## Warnings

### WR-01: Temp file leaked on every non-alarm return in `_process_fusion_for_camera`

**File:** `ai_agent/scheduler.py:338-413`

**Issue:** `tmp_path` (`fusion_*.jpg`) is created at line 338 during every cycle.  The `tmp_path.unlink()` cleanup lives only in the `finally` of the inner alarm-fire `try` block at lines 436-440.  All early returns before that block skip cleanup:

- Line 350 — `capture` exception
- Line 352 — `cv2.imread` returned `None`
- Lines 365-368 — required detector not loaded
- Line 394 — unknown `rule`
- Line 405 — `[no_fusion]` (fusion rule evaluated False — **the dominant path**)
- Lines 410-413 — `[no_fusion_yet]` (buffer accumulating, **fires 2 out of every 3 cycles**)

Compare to the sibling `_process_detection_for_camera` (lines 238-305) which wraps the entire body in one `try/finally` so `unlink` always fires.  Over a 1-minute cycle on two fusion cameras, `[no_fusion_yet]` fires ~66 % of the time; over hours this fills the `snapshot_tmp_dir`.

**Fix:** Mirror the structure of `_process_detection_for_camera` — wrap the entire function body (after `tmp_path` is assigned) in a single outer `try/finally`:

```python
tmp_path = settings.snapshot_tmp_dir / tmp_name
try:
    # ... all of steps 2-7 ...
    return result_string
finally:
    try:
        tmp_path.unlink(missing_ok=True)
    except OSError:
        pass
```

Remove the inner `finally` block from the alarm-fire section (lines 436-440) once the outer one is in place.

---

### WR-02: Helmet label filter in scheduler diverges from test fixture — possible production false-positive

**File:** `ai_agent/scheduler.py:385-388` and `ai_agent/tests/test_fusion.py:145`

**Issue:** The scheduler filters helmet detections to only `("helmet", "hardhat")` labels (line 387), excluding `"head"`-labeled bboxes (bare, unhelmeted heads).  This is the correct semantic intent: the `hard_hat_best.pt` model emits `"head"` for a bare head and `"helmet"` (or `"hardhat"`) for a covered head, so filtering out `"head"` prevents a bare-head detection from suppressing the missing-helmet alarm.

However, `test_helmet_worn` (test_fusion.py line 145) constructs a _worn_ helmet fixture with `label="head"`:

```python
helmet = [_dr(140, 90, 160, 110, label="head")]
assert fusion_helpers.compute_fusion_helmet_missing(person, helmet, 640) is False
```

This test calls `compute_fusion_helmet_missing` directly, bypassing the scheduler's label filter, so the test passes.  But an identical real detection with `label="head"` would be stripped by the scheduler filter and never reach `compute_fusion_helmet_missing`, causing a false-positive alarm even for a helmeted worker.

If `hard_hat_best.pt` genuinely emits `"helmet"` for covered heads (not `"head"`), the production logic is correct and the test fixture's `label="head"` is misleading.  If the model emits `"head"` for both, the production filter is over-aggressive.  Either way, the mismatch is unverified in the codebase.

**Fix:** One of:
1. Confirm the model's actual label for a worn helmet (e.g., from training metadata or `class_names`). If it is `"helmet"`, change the test fixture to `label="helmet"` to align with the production filter.
2. If the model emits `"head"` for worn helmets, update the scheduler filter to reflect that.

The test fixture label must match what `GenericYoloDetector.detect_all` will actually return from `hard_hat_best.pt`.

---

### WR-03: `hardhat_is_on` head-region `y1 > y2` inversion not guarded — silent false-positive

**File:** `ai_agent/fusion_helpers.py:57-63`

**Issue:** The head-region vertical bounds are:

```python
y1 = max(0.0, person_bbox[1] - hardhat_height / 2)
y2 = max(0.0, person_bbox[3] - person_height * 3 / 4)
```

`y2` is the upper quarter of the person bbox (25 % from the bottom = 75 % of height from top).  If a bbox arrives where `hardhat_height` is unusually large (e.g., a cropped or partially-visible person whose bbox is short, but the helmet bbox is tall), `y1` can exceed `y2`.  When `y1 > y2`, the condition `y1 <= helmet_cy <= y2` is always `False`, causing `hardhat_is_on` to return `False` for a physically plausible helmet, and `compute_fusion_helmet_missing` raises a false-positive alarm for that person.

Concrete example: `person_bbox = (0, 50, 30, 80)` (height=30), `helmet_bbox = (0, 0, 30, 100)` (hardhat_height=100).  
`y1 = max(0, 50 - 50) = 0`, `y2 = max(0, 80 - 22.5) = 57.5` — safe here.  
But: `person_bbox = (0, 100, 30, 120)` (height=20), `helmet_bbox = (0, 0, 30, 220)` (hardhat_height=220).  
`y1 = max(0, 100 - 110) = 0`, `y2 = max(0, 120 - 15) = 105` — still safe.  
Edge case closes when `person_bbox[1] - hardhat_height/2 > person_bbox[3] - person_height*3/4` before the `max(0,…)` clamps.  In practice this is rare, but the function has no assertion and no fallback clamp.

**Fix:** Add a guard after computing `y1`/`y2`:

```python
y1 = max(0.0, person_bbox[1] - hardhat_height / 2)
y2 = max(0.0, person_bbox[3] - person_height * 3 / 4)
if y1 >= y2:          # degenerate head region — no valid range
    return False
```

---

## Info

### IN-01: `_detection_cooldown` shared between single-detector and fusion keys — latent naming collision

**File:** `ai_agent/scheduler.py:331-333`

**Issue:** `_process_fusion_for_camera` uses `_detection_cooldown` (line 333) keyed by `(camera_id, fusion_key)`, while `_process_detection_for_camera` uses the same dict keyed by `(camera_id, event_key)`.  Currently distinct (`"helmet"` vs `"helmet_missing"`, `"forklift"` vs `"forklift_collision"`), so there is no collision.  However, the shared dict is undocumented as an intentional design choice; a future rename of a FUSION_CONFIGS key to match a DETECTOR_CONFIGS event_key (e.g., both being `"forklift"`) would silently make a detector alarm suppress a fusion cooldown and vice versa.

**Fix:** Add a comment on the shared dict declaration (line 48-55) explaining that fusion keys must not collide with detector event keys, or prefix all fusion keys (e.g., `"fusion:forklift_collision"`) to make collision structurally impossible.

---

### IN-02: `max_conf` for `hardhat_missing` alarm reflects max across ALL persons, not unmatched persons

**File:** `ai_agent/scheduler.py:391-392`

**Issue:** When `fusion_result` is True for the `hardhat_missing` rule, the `accuracy` field logged and stored uses:

```python
max_conf = max((p.confidence or 0.0) for p in persons)
```

This overwrites the cross-detector `max_conf` accumulated in the earlier loop (lines 371-373) and reports the highest confidence across _all_ detected persons, not just the unmatched (helmetless) ones.  The comment says "unmatched person conf" but the code doesn't filter.  The value stored in `detection_events.accuracy` will be misleading if there are mixed-helmet workers in frame (some wearing, some not).

**Fix:** Change to iterate only over persons who lack a matching helmet, or document that `accuracy` is intentionally the highest-confidence person as a proxy.

---

### IN-03: `detect_all` duplicates inference block from `detect` — maintenance hazard

**File:** `ai_agent/yolo_detector.py:186-227` vs `130-184`

**Issue:** `detect_all` (lines 194-212) reproduces the YOLOv5/YOLOv8 inference and tensor-extraction code verbatim from `detect` (lines 136-163).  There are two independent copies to keep in sync whenever the inference interface changes (e.g., YOLOv26 migration noted in project memory).

**Fix:** Extract the inference + tensor-unpacking into a private `_run_inference(image_bgr) -> tuple[list, list, list, float]` method returning `(cls_ids, confs, xyxys, inference_ms)`.  Both `detect` and `detect_all` call that shared method.

---

_Reviewed: 2026-05-14T10:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
