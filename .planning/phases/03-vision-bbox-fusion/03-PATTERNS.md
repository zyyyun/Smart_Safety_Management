# Phase 3: 비전 — bbox 겹침/공간 매칭 - Pattern Map

**Mapped:** 2026-05-14
**Files analyzed:** 7 (2 new, 4 modified, 1 new test)
**Analogs found:** 5 / 7 (2 files have no in-repo analog)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ai_agent/yolo_detector.py` | utility/inference | transform | itself — `GenericYoloDetector.detect()` lines 130-184 | exact (add method alongside) |
| `ai_agent/fusion_helpers.py` | utility | transform | none in repo | no analog |
| `ai_agent/fusion_configs.py` | config | — | `ai_agent/detector_configs.py` | role-match (dict-of-dicts shape) |
| `ai_agent/detector_configs.py` | config | — | itself — `DETECTOR_CONFIGS["helmet"]` lines 45-61 | exact (modify 2 fields) |
| `ai_agent/scheduler.py` | service | batch/event-driven | itself — `_process_detection_for_camera` lines 210-296; buffer dicts lines 43-53; `run_detection_cycle` lines 299-339 | exact (additive: new buffer dict + new per-camera function + extend run loop) |
| `ai_agent/tests/test_fusion.py` | test | transform | `ai_agent/tests/test_scheduler_buffer.py` | exact (same harness, extend mock for `detect_all`) |
| `supabase/migrations/009_phase3_fusion_event_types.sql` | migration | — | `supabase/migrations/008_event_types_extension.sql` | exact |

---

## Pattern Assignments

### `ai_agent/yolo_detector.py` — add `detect_all()` to `GenericYoloDetector`

**Analog:** itself — `GenericYoloDetector.detect()` (lines 130-184)

**Imports pattern** (lines 15-26 — unchanged, reuse as-is):
```python
from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import numpy as np

log = logging.getLogger(__name__)
```

**Core pattern — inference extraction (lines 136-163):**
`detect()` already extracts all `cls_ids / confs / xyxys` from inference, then selects best 1.
`detect_all()` copies the exact same extraction block and replaces the best-selection loop
(lines 166-173) with a list-accumulation loop.

```python
# yolov5 branch — lines 136-143 (copy verbatim into detect_all yolov5 branch)
results = self.model(image_bgr, size=self.img_size)
preds = results.xyxy[0].cpu().numpy() if hasattr(results.xyxy[0], "cpu") else results.xyxy[0]
cls_ids = [int(row[5]) for row in preds]
confs   = [float(row[4]) for row in preds]
xyxys   = [[float(row[0]), float(row[1]), float(row[2]), float(row[3])] for row in preds]

# yolov8 branch — lines 144-163 (copy predict_kwargs block verbatim)
predict_kwargs = dict(
    conf=self.conf_thres,
    iou=self.iou_thres,
    imgsz=self.img_size,
    verbose=False,
)
if self._device:
    predict_kwargs["device"] = self._device
results = self.model.predict(image_bgr, **predict_kwargs)
# ... early-return if not results / boxes is None / len == 0
cls_ids = boxes.cls.tolist() if hasattr(boxes.cls, "tolist") else list(boxes.cls)
confs   = boxes.conf.tolist() if hasattr(boxes.conf, "tolist") else list(boxes.conf)
xyxys   = boxes.xyxy.tolist() if hasattr(boxes.xyxy, "tolist") else [list(b) for b in boxes.xyxy]
```

**Best-selection → list-accumulation (lines 166-184, replace in detect_all):**
```python
# detect() selects best — lines 166-184
best: tuple[float, int, list[float], str] | None = None
for cls_id, conf, xyxy in zip(cls_ids, confs, xyxys):
    label = self.class_names.get(int(cls_id), str(int(cls_id)))
    if self.targets is not None and label.lower() not in self.targets:
        continue
    if best is None or conf > best[0]:
        best = (float(conf), int(cls_id), list(xyxy), label)

# detect_all() replaces this with list accumulation:
# out: list[DetectResult] = []
# for cls_id, conf, xyxy in zip(cls_ids, confs, xyxys):
#     label = self.class_names.get(int(cls_id), str(int(cls_id)))
#     if self.targets is not None and label.lower() not in self.targets:
#         continue
#     out.append(DetectResult(is_detected=True, confidence=float(conf),
#         bbox=(float(xyxy[0]), float(xyxy[1]), float(xyxy[2]), float(xyxy[3])),
#         label=label, inference_ms=inference_ms))
# return out
```

**Return pattern (line 174-184):**
`detect()` returns `DetectResult(is_detected=False)` when nothing passes the filter.
`detect_all()` returns `[]` (empty list) instead — same guard on `image_bgr is None or image_bgr.size == 0` at entry.

---

### `ai_agent/fusion_configs.py` — new `FUSION_CONFIGS` dict

**Analog:** `ai_agent/detector_configs.py` — `DETECTOR_CONFIGS` dict-of-dicts (lines 23-92)

**Module header pattern** (lines 1-22 of detector_configs.py — docstring + import):
```python
"""<module description>"""

from __future__ import annotations

DETECTOR_CONFIGS: dict[str, dict] = {
    "fire": {
        "framework": "yolov5",
        ...
    },
    ...
}
```

**Dict entry value shape** (lines 24-44 — fire entry as schema reference):
```python
"fire": {
    "event_name": "화재",
    "risk_level": "DANGER",
    "camera_ids": [1],
    "conf_thres": 0.10,
    "iou_thres": 0.45,
    "frames_required": 5,
    "img_size": 640,
    "target_classes": None,
    "storage_prefix": "fire",
},
```

`fusion_configs.py` uses same top-level structure with fusion-specific keys replacing detector-only keys.
The two keys unique to fusion: `"detectors_required"` and `"rule"`. Drop `"weights"`, `"framework"`, `"conf_thres"`, `"iou_thres"`, `"img_size"`.

---

### `ai_agent/detector_configs.py` — modify `DETECTOR_CONFIGS["helmet"]`

**Analog:** itself — `DETECTOR_CONFIGS["helmet"]` entry (lines 45-61)

**Current state (lines 45-61):**
```python
"helmet": {
    "framework": "yolov5",
    "weights": r"D:\2025_산업안전\산업안전\모델 7종\안전모 탐지\hard_hat_best.pt",
    "event_name": "안전모 미착용",
    "risk_level": "WARNING",
    "camera_ids": [5],
    "conf_thres": 0.5,
    "iou_thres": 0.45,
    "frames_required": 3,   # Phase 3 D-04: → 1
    "img_size": 640,
    "target_classes": ["head"],  # Phase 3 D-04: → None
    "storage_prefix": "helmet",
},
```

**Two field changes required by D-04:**
- `"target_classes": ["head"]` → `None` (head + helmet both included in detect_all results)
- `"frames_required": 3` → `1` (standalone alarm path removed; fusion buffer provides the N=3 check)

**D-04 suppression mechanism:** `run_detection_cycle` (lines 318-338) iterates all `detectors` keys including `"helmet"`. With `target_classes=None` and `frames_required=1`, the existing `_process_detection_for_camera` would immediately fire on any bbox — the opposite of intent. The suppression mechanism (disabled flag, FUSION_ONLY_DETECTORS set, or entry removal) is a **planner decision** — see RESEARCH.md Open Questions #1 for the three options. Whichever option the planner chooses, the analog for the suppression guard insertion point is `run_detection_cycle:318-338` (the cfg lookup at line 319-321).

---

### `ai_agent/scheduler.py` — add `_fusion_buffer`, `_process_fusion_for_camera`, extend `run_detection_cycle`

**Analog:** itself, three sections:

#### Section A — Module-level buffer dict (lines 43-53)

```python
# lines 43-53 — existing module state dicts (copy pattern for _fusion_buffer)
_fall_cooldown: dict[int, float] = {}
_detection_cooldown: dict[tuple[int, str], float] = {}

# (camera_id, event_key) 별 최근 N 사이클 is_detected 결과 buffer (Phase 2 / MODEL-02).
# - maxlen = 5 (DETECTOR_CONFIGS 의 max frames_required = fire 5).
# - cooldown skip cycle 에서는 push 하지 않음 (D-07).
# - 알람 발사 시 buffer.clear() + cooldown 갱신 (D-02).
# - 1프로세스 PoC 휘발성 OK — 재시작 시 자동 reset (D-06).
_detection_buffer: dict[tuple[int, str], deque] = {}
```

`_fusion_buffer` goes directly below line 53, same pattern: `dict[tuple[int, str], deque] = {}`.
Key is `(camera_id, fusion_key)` — same tuple shape as `_detection_buffer`.

#### Section B — `_process_detection_for_camera` (lines 210-296) — full analog for `_process_fusion_for_camera`

The 7-step flow is identical; only step 3 (detect) and step 4 (rule evaluation) differ.

```python
# lines 221-225 — cooldown check (copy verbatim, change dict key prefix in log msg)
cooldown_key = (camera_id, event_key)
last_ts = _detection_cooldown.get(cooldown_key)
now = time.time()
if last_ts and (now - last_ts) < settings.detectors_cooldown_min * 60:
    return f"[detect_skip_cooldown] camera_id={camera_id} event={event_key}"

# lines 227-238 — snapshot capture + imread (copy verbatim, change tmp_name prefix)
tmp_name = f"detect_{event_key}_{camera_id}_{int(now * 1000)}.jpg"
tmp_path = settings.snapshot_tmp_dir / tmp_name
try:
    capture(
        rtsp_url,
        tmp_path,
        ffmpeg_bin=settings.ffmpeg_bin,
        seek_seconds=settings.detectors_demo_seek_sec,
    )
    img = cv2.imread(str(tmp_path))
    if img is None:
        return f"[DETECT_ERR] camera_id={camera_id} event={event_key}: cv2.imread failed"

# lines 245-256 — lazy buffer create + append + no_detect early return
frames_required = int(cfg.get("frames_required", 1))
buffer = _detection_buffer.get(cooldown_key)
if buffer is None:
    buffer = deque(maxlen=max(5, frames_required))
    _detection_buffer[cooldown_key] = buffer
buffer.append(bool(result.is_detected))

if not result.is_detected:
    return f"[no_detect] camera_id={camera_id} event={event_key} inf={result.inference_ms:.0f}ms"

# lines 260-266 — N consecutive check
recent = list(buffer)[-frames_required:]
if len(buffer) < frames_required or not all(recent):
    return (
        f"[no_alert_yet] camera_id={camera_id} event={event_key} "
        f"frames={sum(recent)}/{frames_required} conf={result.confidence:.2f}"
    )

# lines 268-287 — alarm: upload snapshot + register_ai_event + buffer.clear + cooldown update
public_url, _path = bridge.upload_detection_snapshot(
    camera_id, cfg.get("storage_prefix", event_key), tmp_path
)
event = bridge.register_ai_event(
    camera_id=camera_id,
    event_name=cfg["event_name"],
    risk_level=cfg["risk_level"],
    accuracy=float(result.confidence or 0.0),
    image_url=public_url,
)
buffer.clear()
_detection_cooldown[cooldown_key] = now
```

`_process_fusion_for_camera` copies lines 210-296 with:
- `_detection_buffer` → `_fusion_buffer`
- `detector.detect(img)` → two `detector.detect_all(img)` calls (one per `cfg["detectors_required"]`)
- After detection: rule branch (`iou_gt` or `hardhat_missing`) producing `fusion_result: bool`
- `buffer.append(bool(fusion_result))` (not `bool(result.is_detected)`)
- Log prefixes: `[fusion_skip_cooldown]`, `[no_fusion]`, `[no_fusion_yet]`, `[FUSION]`
- `tmp_name` prefix: `f"fusion_{fusion_key}_{camera_id}_{int(now * 1000)}.jpg"` (avoid collision)

#### Section C — `run_detection_cycle` loop (lines 299-339) — analog for the fusion loop insertion

```python
# lines 318-338 — existing detector loop (analog for fusion loop shape)
for event_key, detector in detectors.items():
    cfg = DETECTOR_CONFIGS.get(event_key)
    if cfg is None:
        log.warning("[%s] DETECTOR_CONFIGS 에 항목 없음, 스킵", event_key)
        continue
    target_cams = [
        cams_by_id[cid] for cid in cfg["camera_ids"] if cid in cams_by_id
    ]
    if not target_cams:
        log.debug(
            "[%s] 매핑 카메라 %s 중 활성된 것 없음", event_key, cfg["camera_ids"]
        )
        continue
    for cam in target_cams:
        msg = _process_detection_for_camera(
            bridge, settings, event_key, detector, cfg, cam
        )
        if msg.startswith(("[no_detect]", "[detect_skip_cooldown]", "[no_alert_yet]")):
            log.debug(msg)
        else:
            log.info(msg)
```

Fusion loop appended after line 338 (after detector loop), same `cams_by_id` lookup:
```python
# [Phase 3 addition] — fusion loop after detector loop
for fusion_key, cfg in FUSION_CONFIGS.items():
    target_cams = [cams_by_id[cid] for cid in cfg["camera_ids"] if cid in cams_by_id]
    ...
    for cam in target_cams:
        msg = _process_fusion_for_camera(bridge, settings, fusion_key, cfg, cam, detectors)
        if msg.startswith(("[no_fusion]", "[fusion_skip_cooldown]", "[no_fusion_yet]")):
            log.debug(msg)
        else:
            log.info(msg)
```

**New import needed:** `from fusion_configs import FUSION_CONFIGS` (alongside existing `from detector_configs import DETECTOR_CONFIGS` at line 33).

---

### `ai_agent/tests/test_fusion.py` — new test file

**Analog:** `ai_agent/tests/test_scheduler_buffer.py` (full file, 248 lines)

**sys.path + import block** (lines 9-22):
```python
from __future__ import annotations

import sys
import time
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

AGENT_DIR = Path(__file__).resolve().parents[1]
if str(AGENT_DIR) not in sys.path:
    sys.path.insert(0, str(AGENT_DIR))

import scheduler  # noqa: E402
```
`test_fusion.py` adds: `import fusion_helpers` alongside `import scheduler`.

**autouse fixture** (lines 28-35 — copy and extend):
```python
@pytest.fixture(autouse=True)
def reset_module_state():
    """매 테스트마다 _detection_buffer / _detection_cooldown / _fusion_buffer 초기화."""
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()
    # Phase 3 addition:
    # scheduler._fusion_buffer.clear()
    yield
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()
    # scheduler._fusion_buffer.clear()
```

**stub_external fixture** (lines 39-48 — copy verbatim):
```python
@pytest.fixture
def stub_external(monkeypatch, tmp_path):
    monkeypatch.setattr(scheduler, "capture", lambda *a, **kw: None)
    fake_img = MagicMock(name="fake_bgr_ndarray")
    fake_img.shape = (480, 640, 3)   # Phase 3: img.shape[1] needed by hardhat_is_on
    monkeypatch.setattr(scheduler.cv2, "imread", lambda _path: fake_img)
    return fake_img
```

**make_bridge factory** (lines 61-65 — copy verbatim):
```python
def make_bridge():
    bridge = MagicMock(name="bridge")
    bridge.upload_detection_snapshot.return_value = ("https://example.test/snap.jpg", "obj/path")
    bridge.register_ai_event.return_value = {"event_id": 999}
    return bridge
```

**make_detector factory — Phase 3 extension** (lines 68-87 — copy and add `detect_all`):
```python
def make_detector(sequence):
    """sequence 에서 한 번에 하나씩 is_detected 를 꺼내는 detector mock."""
    iterator = iter(sequence)

    def _detect(_img):
        try:
            flag = next(iterator)
        except StopIteration:
            flag = False
        return SimpleNamespace(
            is_detected=flag,
            confidence=0.42 if flag else None,
            bbox=None,
            label="x" if flag else None,
            inference_ms=12.3,
        )

    detector = MagicMock(name="detector")
    detector.detect.side_effect = _detect
    return detector

# Phase 3 addition — detect_all mock returning list[SimpleNamespace]:
def make_detect_all_detector(sequences_by_call):
    """sequences_by_call: list of list[SimpleNamespace] — one per detect_all() call."""
    iterator = iter(sequences_by_call)

    def _detect_all(_img):
        try:
            return next(iterator)
        except StopIteration:
            return []

    detector = MagicMock(name="detector_all")
    detector.detect_all.side_effect = _detect_all
    return detector
```

**run_n_cycles harness** (lines 120-127 — copy, change function called):
```python
def run_n_fusion_cycles(n, *, bridge, settings, fusion_key, cfg, detectors):
    results = []
    for _ in range(n):
        msg = scheduler._process_fusion_for_camera(
            bridge, settings, fusion_key, cfg, CAM, detectors
        )
        results.append(msg)
    return results
```

**Test naming convention** (lines 133-248 — copy `test_<scenario>` pattern):
`test_iou_xyxy_identical_bbox`, `test_collision_iou_above_threshold`,
`test_collision_iou_below_threshold`, `test_collision_empty_list`,
`test_helmet_worn`, `test_helmet_missing_no_helmet`, `test_helmet_missing_partial`,
`test_process_fusion_3cycles_triggers_alarm`.

---

### `supabase/migrations/009_phase3_fusion_event_types.sql` — new migration

**Analog:** `supabase/migrations/008_event_types_extension.sql` (complete file, 11 lines)

```sql
-- ============================================
-- 008: event_types extension (LP-2 확장)
-- 화재·안전모 미착용 은 006_seed_data.sql 에 이미 seed.
-- 지게차·사람(혼잡도) 만 추가. ON CONFLICT 로 idempotent.
-- ============================================

INSERT INTO public.event_types (event_name) VALUES
    ('지게차 진입'),
    ('혼잡도 경고')
ON CONFLICT (event_name) DO NOTHING;
```

`009_phase3_fusion_event_types.sql` copies this exactly, inserting only `'지게차 충돌 위험'`.
`'안전모 미착용'` is already seeded in `006_seed_data.sql` line 10 — do NOT re-insert.

---

## Shared Patterns

### Cooldown check (shared: `_process_detection_for_camera` → `_process_fusion_for_camera`)
**Source:** `ai_agent/scheduler.py` lines 221-225
**Apply to:** `_process_fusion_for_camera` (step 1 of 7)
```python
cooldown_key = (camera_id, event_key)   # fusion: (camera_id, fusion_key)
last_ts = _detection_cooldown.get(cooldown_key)
now = time.time()
if last_ts and (now - last_ts) < settings.detectors_cooldown_min * 60:
    return f"[detect_skip_cooldown] camera_id={camera_id} event={event_key}"
```
`_fusion_buffer` uses `_detection_cooldown` (shared dict, D-06) — same lookup pattern, different string key.

### Snapshot capture + imread block
**Source:** `ai_agent/scheduler.py` lines 227-238
**Apply to:** `_process_fusion_for_camera` step 2; tmp_name uses `fusion_` prefix
```python
tmp_name = f"detect_{event_key}_{camera_id}_{int(now * 1000)}.jpg"
tmp_path = settings.snapshot_tmp_dir / tmp_name
try:
    capture(rtsp_url, tmp_path, ffmpeg_bin=settings.ffmpeg_bin,
            seek_seconds=settings.detectors_demo_seek_sec)
    img = cv2.imread(str(tmp_path))
    if img is None:
        return f"[DETECT_ERR] camera_id={camera_id} event={event_key}: cv2.imread failed"
```

### Lazy buffer create + deque(maxlen=…)
**Source:** `ai_agent/scheduler.py` lines 245-250
**Apply to:** `_process_fusion_for_camera` step 5 (using `_fusion_buffer`)
```python
frames_required = int(cfg.get("frames_required", 1))
buffer = _detection_buffer.get(cooldown_key)
if buffer is None:
    buffer = deque(maxlen=max(5, frames_required))
    _detection_buffer[cooldown_key] = buffer
buffer.append(bool(result.is_detected))
```

### N-consecutive check + alarm + buffer.clear + cooldown update
**Source:** `ai_agent/scheduler.py` lines 260-287
**Apply to:** `_process_fusion_for_camera` steps 6-7
```python
recent = list(buffer)[-frames_required:]
if len(buffer) < frames_required or not all(recent):
    return f"[no_alert_yet] ..."

public_url, _path = bridge.upload_detection_snapshot(...)
event = bridge.register_ai_event(
    camera_id=camera_id,
    event_name=cfg["event_name"],
    risk_level=cfg["risk_level"],
    accuracy=float(result.confidence or 0.0),
    image_url=public_url,
)
buffer.clear()
_detection_cooldown[cooldown_key] = now
```

### `register_ai_event` keyword-only signature
**Source:** `ai_agent/supabase_client.py` lines 124-154 (verified in RESEARCH.md)
**Apply to:** all alarm-firing points in `_process_fusion_for_camera`
```python
bridge.register_ai_event(
    camera_id=camera_id,
    event_name=cfg["event_name"],
    risk_level=cfg["risk_level"],
    accuracy=max_conf,           # fusion: max conf of involved bboxes
    image_url=public_url,
)
```
All 5 parameters are keyword-only. `accuracy` for fusion: IoU rule = max(forklift_conf, person_conf); hardhat_missing rule = unmatched person conf.

### Log prefix convention
**Source:** `ai_agent/scheduler.py` — `[DETECT]`, `[no_detect]`, `[detect_skip_cooldown]`, `[no_alert_yet]`
**Apply to:** all log/return strings in `_process_fusion_for_camera`
Phase 3 equivalents: `[FUSION]`, `[no_fusion]`, `[fusion_skip_cooldown]`, `[no_fusion_yet]`

### Test autouse fixture + monkeypatch stubs
**Source:** `ai_agent/tests/test_scheduler_buffer.py` lines 28-48
**Apply to:** `test_fusion.py` — copy both fixtures, add `scheduler._fusion_buffer.clear()` to reset_module_state, add `fake_img.shape = (480, 640, 3)` to stub_external.

### Migration ON CONFLICT idempotent pattern
**Source:** `supabase/migrations/008_event_types_extension.sql` lines 7-10
**Apply to:** `009_phase3_fusion_event_types.sql`
```sql
INSERT INTO public.event_types (event_name) VALUES (...)
ON CONFLICT (event_name) DO NOTHING;
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `ai_agent/fusion_helpers.py` | utility | transform | No pure-math geometry helpers exist in repo. Closest out-of-repo source: `D:\2025_산업안전\산업안전\모델 7종\사람 탐지\utils\bbox_utils.py:10-29` (legacy `hardhat_is_on`). Note: that file has a y2 clamp bug (`if y2 < 0: y1 = 0` should be `y2 = 0`) — fix during port. Planner should use RESEARCH.md Code Examples section for this file's implementation. |

---

## Metadata

**Analog search scope:** `ai_agent/` (scheduler.py, yolo_detector.py, detector_configs.py, tests/), `supabase/migrations/`
**Files scanned:** 7 source files read directly
**D-04 suppression mechanism:** Not a pattern question — is a planner architectural decision. Analog for insertion point: `scheduler.py:318-321` (cfg lookup + skip guard). Three options documented in RESEARCH.md Open Questions #1.
**Pattern extraction date:** 2026-05-14
