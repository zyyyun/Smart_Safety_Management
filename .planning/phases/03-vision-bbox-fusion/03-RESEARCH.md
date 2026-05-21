# Phase 3: 비전 — bbox 겹침/공간 매칭 - Research

**Researched:** 2026-05-14
**Domain:** Python AI agent — multi-detector bbox fusion (IoU + spatial matching)
**Confidence:** HIGH (all critical findings verified from live source files)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01**: `GenericYoloDetector.detect_all(image_bgr) -> list[DetectResult]` 신규 메서드.
  기존 `detect()` backward compat 유지. `ai_agent/yolo_detector.py` `GenericYoloDetector` 클래스 내.
- **D-02**: 신규 모듈 `ai_agent/fusion_helpers.py` — `iou_xyxy`, `hardhat_is_on`,
  `compute_fusion_collision`, `compute_fusion_helmet_missing` 4 함수. 모두 pure (no side effects).
- **D-03**: 신규 `FUSION_CONFIGS` dict in `ai_agent/fusion_configs.py`. 2 entries:
  `forklift_collision` (camera_ids=[4]) 및 `helmet_missing` (camera_ids=[5]).
- **D-04**: `DETECTOR_CONFIGS["helmet"]["target_classes"]` → `None`,
  `DETECTOR_CONFIGS["helmet"]["frames_required"]` → `1`. 단독 알람 경로 제거.
- **D-05**: 신규 함수 `_process_fusion_for_camera` in `scheduler.py`.
- **D-06**: 신규 `_fusion_buffer: dict[tuple[int, str], deque[bool]]` in `scheduler.py`.
- **D-07**: IoU > 0.3, frames_required = 3 for `forklift_collision`.
- **D-08**: head 영역 = top 25% × ±width/6 (레거시 그대로), frames_required = 3 for `helmet_missing`.
- **D-09**: forklift_collision camera_id=4, helmet_missing camera_id=5. person detector cross-camera.
- **D-10**: pytest unit tests (8 cases) in `ai_agent/tests/test_fusion.py`.
- **D-11**: Migration `supabase/migrations/009_phase3_fusion_event_types.sql`.
  신규 `"지게차 충돌 위험"` event_type만 INSERT. `"안전모 미착용"` 은 006_seed_data.sql 에 이미 시드.
- **D-12**: Phase 2 `frames_required` 룰과 fusion buffer 는 직교 동작.

### Claude's Discretion

- person `target_classes = ['person']` 유지 — fusion `detect_all` 호출 시 person bbox 만 추출.
- forklift `target_classes = None` 유지.
- fusion event accuracy = IoU rule 은 두 bbox max conf, hardhat_missing rule 은 unmatched person conf.
- log prefix: `[FUSION]` / `[no_fusion]` / `[fusion_skip_cooldown]` / `[no_fusion_yet]`.
- person detector cross-camera 비용: 3카메라 × ~200ms = 600ms < 60s 사이클. 허용.

### Deferred Ideas (OUT OF SCOPE)

- 검단·포천 현장 영상 (forklift+person 같은 frame) — v1.1 6월 설치 직전 수집.
- 사람별 개별 helmet missing 알람 — v1.1.
- IoU 임계 0.3 현장 보정 — v1.1 EVAL.
- head 영역 pose keypoint 사용 — v1.1.
- fire ↔ person fusion — v1.1.
- fusion event storage 영구 보존 옵션 — v1.x.
- person detector frame caching — v1.1.
- 다중 fusion rule chaining — v2.0.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FUSION-01 | 지게차+사람 bbox IoU 계산 — IoU > 0.3 이 N 프레임 연속이면 "지게차 충돌 위험" 알람. scheduler helper 추가 | D-01 `detect_all()` + D-02 `iou_xyxy`/`compute_fusion_collision` + D-05/06 buffer 패턴 |
| FUSION-02 | 사람 bbox head 영역 + helmet 객체 매칭 — 없으면 "안전모 미착용". 레거시 `hardhat_is_on` 이식 | D-01 `detect_all()` + D-02 `hardhat_is_on`/`compute_fusion_helmet_missing` + D-04 helmet target_classes/frames_required 변경 |
</phase_requirements>

---

## Summary

Phase 3은 `ai_agent/scheduler.py`의 `run_detection_cycle` 끝에 두 번째 fusion 루프를 추가한다.
두 신규 이벤트("지게차 충돌 위험" DANGER, "안전모 미착용" fusion 경로)는 각각 별도의 `_fusion_buffer`
deque에 누적되며, Phase 2의 frames_required N=3을 직교 적용한다.

핵심 패턴은 세 가지다: (1) `detect_all()` — `detect()`의 best-selection 단계만 생략하여 모든 bbox를
list로 반환(추가 추론 비용 없음), (2) `fusion_helpers.py` — IoU 계산 + 레거시 head 영역 매칭 pure 함수,
(3) `_process_fusion_for_camera` — `_process_detection_for_camera`와 동일한 7단계 흐름을 단일 스냅샷
1회로 두 detector `detect_all()` 호출로 확장. Phase 2 test 패턴(모듈 상태 초기화 fixture + MagicMock
bridge + capture stub)을 `test_fusion.py`에서 직접 연장한다.

**Primary recommendation:** D-04의 helmet 단독 알람 억제 메커니즘을 planner가 명시적으로 결정해야 한다.
현재 CONTEXT는 "단독 알람 발사 안 함"이라는 결과를 기술하지만, `run_detection_cycle`이 여전히 helmet
항목을 순회하는 구조에서 이를 막는 코드 경로가 미정이다. 세 가지 구현 옵션이 있다 — Open Questions
섹션 참조.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| bbox 추출 (all detections) | AI Agent (Python) | — | YOLO inference는 CPU/GPU 로컬에서 실행 |
| IoU 계산 | AI Agent (Python) | — | pure numeric, Supabase 불필요 |
| head 영역 spatial matching | AI Agent (Python) | — | per-frame geometric rule, 서버 불필요 |
| fusion buffer N 연속 룰 | AI Agent (Python) module state | — | Phase 2와 동일 패턴. 휘발성 허용 (D-06) |
| fusion event 등록 | API (Supabase Edge Function) | — | `register_ai_event` 동일 endpoint 재사용 |
| event_type 추가 | Database (Supabase migration) | — | 009.sql ON CONFLICT idempotent |
| snapshot 업로드 | Storage (Supabase) | — | 기존 `upload_detection_snapshot` 재사용 |

---

## Standard Stack

### Core (이미 설치됨 — 신규 deps 없음)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| ultralytics | 8.3.217 [VERIFIED: pip3 show] | YOLOv5/v8 inference | Phase 1·2에서 이미 사용 |
| opencv-python | 4.12.0.88 [VERIFIED: pip3 show] | `cv2.imread`, img.shape | Phase 1·2에서 이미 사용 |
| numpy | 2.2.6 [VERIFIED: pip3 show] | 필요 시 bbox array 조작 | Phase 1·2에서 이미 사용 |
| pytest | 9.0.3 [VERIFIED: pip3 show] | unit test | Phase 2에서 이미 사용 |
| collections.deque | stdlib | fusion buffer | Phase 2와 동일 |

**신규 패키지 설치 없음.** Phase 3의 모든 연산은 순수 Python 산술 (iou_xyxy, hardhat_is_on)
또는 기존 inference 결과 post-processing이다.

---

## Architecture Patterns

### System Architecture Diagram

```
run_detection_cycle (1분 주기)
│
├─ [기존 루프] for event_key in detectors:
│     detector.detect(img) → DetectResult (best 1건)
│     _detection_buffer push + N 연속 검사
│     → register_ai_event("화재"/"지게차 진입" 등)
│
└─ [Phase 3 추가] for fusion_key in FUSION_CONFIGS:
      ↓
      _process_fusion_for_camera(bridge, settings, fusion_key, cfg, cam, detectors)
      │
      ├─ 1. cooldown 검사 → [fusion_skip_cooldown] 반환
      ├─ 2. snapshot capture() → tmp_path → cv2.imread(img)
      ├─ 3. for each detector_key in cfg["detectors_required"]:
      │       detectors[detector_key].detect_all(img) → list[DetectResult]
      ├─ 4. rule 분기:
      │       "iou_gt"  → compute_fusion_collision(forklifts, persons, threshold)
      │       "hardhat_missing" → compute_fusion_helmet_missing(persons, helmets, img.shape[1])
      ├─ 5. fusion_result (bool) → _fusion_buffer[(camera_id, fusion_key)].append()
      ├─ 6. N 연속 검사 → [no_fusion_yet] 반환
      └─ 7. 알람: upload_detection_snapshot + register_ai_event + buffer.clear() + cooldown 갱신
```

```
GenericYoloDetector
│
├─ detect(img) → DetectResult  [기존 — Phase 1·2 호환, best 1건]
│     yolov5: results.xyxy[0] → best 선택
│     yolov8: boxes.xyxy.tolist() → best 선택
│
└─ detect_all(img) → list[DetectResult]  [Phase 3 신규]
      동일 추론 결과에서 best 선택 단계 생략
      → 각 bbox를 DetectResult(is_detected=True) 1건으로 변환
      target_classes 필터 동일 적용
      빈 리스트 = no detection
```

```
fusion_helpers.py (pure functions)
│
├─ iou_xyxy(a, b) → float
│     intersection = max(0, min(x2_a,x2_b) - max(x1_a,x1_b)) * max(0, min(y2_a,y2_b) - max(y1_a,y1_b))
│     union = area_a + area_b - intersection
│     return 0.0 if union == 0 else intersection / union
│
├─ hardhat_is_on(person_bbox, helmet_bbox, frame_width) → bool
│     head_x1 = center_x - width/6  (clamp 0)
│     head_x2 = center_x + width/6  (clamp frame_width)
│     head_y1 = bbox[1] - hardhat_height/2  (clamp 0)
│     head_y2 = bbox[3] - person_height*3/4  (clamp 0)
│     helmet_center ∈ (head_x1,head_y1,head_x2,head_y2) → True
│
├─ compute_fusion_collision(forklifts, persons, iou_thres) → bool
│     any(iou_xyxy(f.bbox, p.bbox) > iou_thres for f in forklifts for p in persons)
│
└─ compute_fusion_helmet_missing(persons, helmets, frame_width) → bool
      any person where not any(hardhat_is_on(p.bbox, h.bbox, frame_width) for h in helmets)
```

### Recommended Project Structure

```
ai_agent/
├─ scheduler.py           # _fusion_buffer 추가 + _process_fusion_for_camera + run_detection_cycle 수정
├─ yolo_detector.py       # GenericYoloDetector.detect_all() 추가
├─ fusion_helpers.py      # [NEW] iou_xyxy, hardhat_is_on, compute_fusion_collision, compute_fusion_helmet_missing
├─ fusion_configs.py      # [NEW] FUSION_CONFIGS dict (2 entries)
├─ detector_configs.py    # helmet target_classes→None, frames_required→1 수정
└─ tests/
   ├─ __init__.py
   ├─ test_scheduler_buffer.py   # Phase 2 (건드리지 않음)
   └─ test_fusion.py             # [NEW] 8 pytest cases

supabase/migrations/
└─ 009_phase3_fusion_event_types.sql  # [NEW] "지게차 충돌 위험" INSERT
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| IoU 계산 | torch.box_iou, torchvision.ops.box_iou | 직접 구현 (D-02) | torch 기반이라 의존성 과다. 수식이 단순 — pure Python 5줄 |
| bbox 추론 | 별도 추론 호출 | detect() 결과 재사용 | detect_all()이 동일 inference 결과에서 best 생략만. 추론 비용 0 |
| head 영역 계산 | 자체 방식 | 레거시 bbox_utils.py 패턴 이식 | 2025 레거시에서 검증된 공식. 불필요한 발명 금지 |
| event 등록 | 신규 DB direct insert | SupabaseBridge.register_ai_event() | Edge Function이 camera_captures + detection_events + notifications 일괄 생성 |
| 스냅샷 업로드 | 신규 Storage 경로 | upload_detection_snapshot(camera_id, cfg["storage_prefix"], tmp_path) | 기존 코드 그대로 재사용 |

---

## Key Code Facts (VERIFIED from source files)

### 1. scheduler.py 현재 상태 [VERIFIED: scheduler.py 전체 읽기]

**Module-level dicts (line 43-53):**
```python
_fall_cooldown: dict[int, float] = {}
_detection_cooldown: dict[tuple[int, str], float] = {}
_detection_buffer: dict[tuple[int, str], deque] = {}
```

**`_process_detection_for_camera` 흐름 (line 210-296):**
- line 221-225: cooldown 검사 → `[detect_skip_cooldown]` 반환
- line 227-235: `capture()` + `cv2.imread()` → `img`
- line 240: `result = detector.detect(img)`
- line 245-250: lazy buffer 생성 + `buffer.append(bool(result.is_detected))`
- line 252-256: `if not result.is_detected: return [no_detect]`
- line 260-266: N 연속 검사 `→ [no_alert_yet] frames=k/N conf=...`
- line 268-281: 알람 발사 + `buffer.clear()` + cooldown 갱신 → `[DETECT]`

**`run_detection_cycle` (line 299-339):**
- `for event_key, detector in detectors.items():` — DETECTOR_CONFIGS lookup + camera 필터
- Phase 3: 이 루프 **끝에** fusion 루프 추가 (`for fusion_key, cfg in FUSION_CONFIGS.items():`)

**`_fusion_buffer` 추가 위치:** line 53 (`_detection_buffer`) 바로 아래.

### 2. yolo_detector.py detect() 내부 [VERIFIED: yolo_detector.py 전체 읽기]

**`detect()` (line 130-184):**

yolov5 분기 (line 136-143):
```python
results = self.model(image_bgr, size=self.img_size)
preds = results.xyxy[0].cpu().numpy()
cls_ids = [int(row[5]) for row in preds]
confs   = [float(row[4]) for row in preds]
xyxys   = [[float(row[0..3])] for row in preds]
```

yolov8 분기 (line 144-163):
```python
results = self.model.predict(image_bgr, ...)
boxes = results[0].boxes
cls_ids = boxes.cls.tolist()
confs   = boxes.conf.tolist()
xyxys   = boxes.xyxy.tolist()
```

best 선택 루프 (line 166-173):
```python
for cls_id, conf, xyxy in zip(cls_ids, confs, xyxys):
    label = self.class_names.get(int(cls_id), ...)
    if self.targets is not None and label.lower() not in self.targets: continue
    if best is None or conf > best[0]:
        best = (float(conf), int(cls_id), list(xyxy), label)
```

**`detect_all()` 구현 전략:** 위 cls_ids/confs/xyxys 추출 코드를 그대로 재사용하고,
best 선택 루프를 `list[DetectResult]` 누적 루프로 교체. 추가 inference 호출 없음.
`target_classes` 필터는 동일하게 적용.

### 3. DETECTOR_CONFIGS 현재 상태 [VERIFIED: detector_configs.py 전체 읽기]

| detector | target_classes | frames_required | Phase 3 변경 |
|----------|----------------|-----------------|--------------|
| fire | None | 5 | 없음 |
| helmet | **`['head']`** | **3** | → None / → 1 (D-04) |
| forklift | None | 1 | 없음 |
| person | `['person']` | 1 | 없음 |

### 4. 마이그레이션 번호 [VERIFIED: dir 결과]

존재하는 파일: 001, 002, 003, 004, 005, 006, 008, 010
- 007: 없음 (스킵됨)
- **009: 없음 → Phase 3 사용 가능**
- 010: `010_watch_pipeline.sql` — Phase 4 사용 중
- 011: 없음 → 대안으로 가능하나 009 권장 (시퀀스 연속성)

### 5. 레거시 hardhat_is_on [VERIFIED: bbox_utils.py:10-29 직접 읽기]

```python
def hardhat_is_on(bbox, bbox_hh, frame):
    frame_width = frame.shape[1]
    person_height = get_bbox_height(bbox)   # bbox[3] - bbox[1]
    person_width  = get_bbox_width(bbox)    # bbox[2] - bbox[0]
    hardhat_height = get_bbox_height(bbox_hh)

    x1 = bbox[0] + (person_width / 2) - (person_width / 6)
    if x1 < 0: x1 = 0
    x2 = bbox[0] + (person_width / 2) + (person_width / 6)
    if x2 > frame_width: x2 = frame_width
    y1 = bbox[1] - hardhat_height / 2
    if y1 < 0: y1 = 0
    y2 = bbox[3] - person_height * 3 / 4
    if y2 < 0: y1 = 0   # ← BUG: y1 이 아닌 y2 를 0으로 clamp 해야 함

    person_safe_area = (x1, y1, x2, y2)
    hardhat_position = get_bbox_center(bbox_hh)
    return point_in_area(hardhat_position, person_safe_area)
```

**버그 확인:** line `if y2 < 0: y1 = 0` — `y1`이 아닌 `y2 = 0`이어야 한다.
`fusion_helpers.py` 구현 시 `y2 = max(0.0, bbox[3] - person_height * 3 / 4)` 로 수정.

### 6. register_ai_event 시그니처 [VERIFIED: supabase_client.py:124-154]

```python
def register_ai_event(
    self,
    *,
    camera_id: int,
    event_name: str,
    risk_level: str,
    accuracy: float,
    image_url: str | None = None,
) -> dict[str, Any]:
```

모두 keyword-only. `_process_fusion_for_camera`의 호출:
```python
bridge.register_ai_event(
    camera_id=camera_id,
    event_name=cfg["event_name"],
    risk_level=cfg["risk_level"],
    accuracy=max_conf,
    image_url=public_url,
)
```

### 7. event_types 시드 현황 [VERIFIED: 006_seed_data.sql, 008_event_types_extension.sql]

006_seed_data.sql에 이미 존재:
- `'쓰러짐'`, `'추락'`, `'화재'`, **`'안전모 미착용'`**, `'제한구역 침입'`, `'이상행동'`

008_event_types_extension.sql에 추가됨:
- `'지게차 진입'`, `'혼잡도 경고'`

009_phase3_fusion_event_types.sql에서 추가할 것:
- **`'지게차 충돌 위험'`** 만 신규 INSERT
- `'안전모 미착용'`은 이미 존재 — 추가 불필요

### 8. test_scheduler_buffer.py 패턴 [VERIFIED: 전체 읽기]

**test_fusion.py 확장에 필요한 핵심 패턴:**

```python
# autouse fixture — 매 테스트 전후 모듈 상태 초기화
@pytest.fixture(autouse=True)
def reset_module_state():
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()
    # Phase 3: scheduler._fusion_buffer.clear() 추가 필요
    yield
    scheduler._detection_buffer.clear()
    scheduler._detection_cooldown.clear()
    # Phase 3: scheduler._fusion_buffer.clear() 추가 필요
```

```python
# stub_external — capture와 cv2.imread를 mock
monkeypatch.setattr(scheduler, "capture", lambda *a, **kw: None)
monkeypatch.setattr(scheduler.cv2, "imread", lambda _path: fake_img)
```

```python
# make_detector(sequence) — is_detected boolean 시퀀스 iterator
# Phase 3에서 detect_all() mock은 list[SimpleNamespace] 반환 필요:
def make_detect_all_detector(forklifts_seq, persons_seq):
    # forklifts_seq: list[list[DetectResult-like]]
    # 매 cycle에서 detect_all.side_effect가 적절한 list 반환
    ...
```

```python
# make_bridge() — register_ai_event.call_count 카운트
bridge = MagicMock(name="bridge")
bridge.upload_detection_snapshot.return_value = ("https://example.test/snap.jpg", "obj/path")
bridge.register_ai_event.return_value = {"event_id": 999}
```

---

## Code Examples

### detect_all() 구현 패턴 [VERIFIED: yolo_detector.py 추출 로직 기반]

```python
def detect_all(self, image_bgr: np.ndarray) -> list[DetectResult]:
    """모든 bbox를 DetectResult list로 반환. target_classes 필터 동일 적용.
    detect()와 동일 inference 결과 사용 — 추가 추론 비용 없음."""
    if image_bgr is None or image_bgr.size == 0:
        return []

    t0 = time.time()
    if self.framework == "yolov5":
        results = self.model(image_bgr, size=self.img_size)
        preds = results.xyxy[0].cpu().numpy() if hasattr(results.xyxy[0], "cpu") else results.xyxy[0]
        cls_ids = [int(row[5]) for row in preds]
        confs   = [float(row[4]) for row in preds]
        xyxys   = [[float(row[0]), float(row[1]), float(row[2]), float(row[3])] for row in preds]
    else:  # yolov8
        predict_kwargs = dict(conf=self.conf_thres, iou=self.iou_thres, imgsz=self.img_size, verbose=False)
        if self._device:
            predict_kwargs["device"] = self._device
        results = self.model.predict(image_bgr, **predict_kwargs)
        if not results:
            return []
        boxes = results[0].boxes
        if boxes is None or len(boxes) == 0:
            return []
        cls_ids = boxes.cls.tolist() if hasattr(boxes.cls, "tolist") else list(boxes.cls)
        confs   = boxes.conf.tolist() if hasattr(boxes.conf, "tolist") else list(boxes.conf)
        xyxys   = boxes.xyxy.tolist() if hasattr(boxes.xyxy, "tolist") else [list(b) for b in boxes.xyxy]
    inference_ms = (time.time() - t0) * 1000.0

    out: list[DetectResult] = []
    for cls_id, conf, xyxy in zip(cls_ids, confs, xyxys):
        label = self.class_names.get(int(cls_id), str(int(cls_id)))
        if self.targets is not None and label.lower() not in self.targets:
            continue
        out.append(DetectResult(
            is_detected=True,
            confidence=float(conf),
            bbox=(float(xyxy[0]), float(xyxy[1]), float(xyxy[2]), float(xyxy[3])),
            label=label,
            inference_ms=inference_ms,
        ))
    return out
```

### iou_xyxy 구현 [VERIFIED: 수학 공식, D-02]

```python
def iou_xyxy(bbox_a: tuple, bbox_b: tuple) -> float:
    """IoU of two (x1,y1,x2,y2) bboxes. Returns 0.0 if no overlap or degenerate."""
    ix1 = max(bbox_a[0], bbox_b[0])
    iy1 = max(bbox_a[1], bbox_b[1])
    ix2 = min(bbox_a[2], bbox_b[2])
    iy2 = min(bbox_a[3], bbox_b[3])
    inter_w = max(0.0, ix2 - ix1)
    inter_h = max(0.0, iy2 - iy1)
    intersection = inter_w * inter_h
    area_a = max(0.0, bbox_a[2] - bbox_a[0]) * max(0.0, bbox_a[3] - bbox_a[1])
    area_b = max(0.0, bbox_b[2] - bbox_b[0]) * max(0.0, bbox_b[3] - bbox_b[1])
    union = area_a + area_b - intersection
    return 0.0 if union == 0 else intersection / union
```

### hardhat_is_on 이식 (버그 수정 포함) [VERIFIED: bbox_utils.py + 버그 확인]

```python
def hardhat_is_on(person_bbox: tuple, helmet_bbox: tuple, frame_width: int) -> bool:
    """helmet bbox 중심점이 person의 head 영역 안에 있으면 True.
    레거시 bbox_utils.py:hardhat_is_on 이식, y2 clamp 버그 수정.
    person_bbox, helmet_bbox: (x1, y1, x2, y2)
    """
    person_height = person_bbox[3] - person_bbox[1]
    person_width  = person_bbox[2] - person_bbox[0]
    hardhat_height = helmet_bbox[3] - helmet_bbox[1]

    center_x = person_bbox[0] + person_width / 2
    x1 = max(0.0, center_x - person_width / 6)
    x2 = min(float(frame_width), center_x + person_width / 6)

    y1 = max(0.0, person_bbox[1] - hardhat_height / 2)
    y2 = max(0.0, person_bbox[3] - person_height * 3 / 4)   # 원본 버그: y1=0 → y2=0 수정

    helmet_cx = (helmet_bbox[0] + helmet_bbox[2]) / 2
    helmet_cy = (helmet_bbox[1] + helmet_bbox[3]) / 2
    return x1 <= helmet_cx <= x2 and y1 <= helmet_cy <= y2
```

### fusion buffer 패턴 [VERIFIED: Phase 2 scheduler.py 패턴]

```python
# scheduler.py module-level (line 53 아래)
_fusion_buffer: dict[tuple[int, str], deque] = {}

# _process_fusion_for_camera 내부 — Phase 2 pattern 그대로
fusion_key_tuple = (camera_id, fusion_key)
last_ts = _detection_cooldown.get(fusion_key_tuple)   # cooldown 공유
now = time.time()
if last_ts and (now - last_ts) < settings.detectors_cooldown_min * 60:
    return f"[fusion_skip_cooldown] camera_id={camera_id} fusion={fusion_key}"

# ... capture + detect_all + rule 평가 ...

frames_required = cfg["frames_required"]
fbuf = _fusion_buffer.get(fusion_key_tuple)
if fbuf is None:
    fbuf = deque(maxlen=max(5, frames_required))
    _fusion_buffer[fusion_key_tuple] = fbuf
fbuf.append(bool(fusion_result))

if not fusion_result:
    return f"[no_fusion] camera_id={camera_id} fusion={fusion_key}"

recent = list(fbuf)[-frames_required:]
if len(fbuf) < frames_required or not all(recent):
    return f"[no_fusion_yet] camera_id={camera_id} fusion={fusion_key} frames={sum(recent)}/{frames_required}"

# 알람 발사
public_url, _ = bridge.upload_detection_snapshot(camera_id, cfg["storage_prefix"], tmp_path)
event = bridge.register_ai_event(
    camera_id=camera_id,
    event_name=cfg["event_name"],
    risk_level=cfg["risk_level"],
    accuracy=max_conf,
    image_url=public_url,
)
fbuf.clear()
_detection_cooldown[fusion_key_tuple] = now
```

### 009 마이그레이션 패턴 [VERIFIED: 008_event_types_extension.sql 패턴]

```sql
-- 009_phase3_fusion_event_types.sql
INSERT INTO public.event_types (event_name) VALUES
    ('지게차 충돌 위험')
ON CONFLICT (event_name) DO NOTHING;
-- "안전모 미착용"은 006_seed_data.sql에 이미 존재 — 추가 불필요
```

### person detector cross-camera 동작 [VERIFIED: CONTEXT D-09 + detector_configs.py]

```python
# run_detection_cycle 내 기존 루프:
# DETECTOR_CONFIGS["person"]["camera_ids"] = [3] 이므로
# 단독 알람("혼잡도 경고")은 camera_id=3에서만 발생.

# _process_fusion_for_camera 내:
# detectors["person"]을 직접 lookup — camera_ids 매핑 무관
# forklift_collision (camera 4): detectors["forklift"].detect_all(img) + detectors["person"].detect_all(img)
# helmet_missing (camera 5):    detectors["helmet"].detect_all(img)  + detectors["person"].detect_all(img)
# 단독 알람과 fusion은 완전히 별개 코드 경로 — person 인스턴스 1개로 공유
```

---

## Common Pitfalls

### Pitfall 1: D-04 helmet 단독 알람이 제거되지 않음

**What goes wrong:** `DETECTOR_CONFIGS["helmet"]["target_classes"] = None`으로 설정 후
`frames_required = 1`이면, `run_detection_cycle`의 helmet 경로가 head 또는 helmet bbox
감지 시 즉시 `"안전모 미착용"` 알람을 발사한다 — 사람이 안전모를 올바르게 착용했어도.
**Why it happens:** `run_detection_cycle`은 `DETECTOR_CONFIGS` 전 항목을 순회하며
`_process_detection_for_camera`를 호출한다. target_classes=None + frames_required=1 =
"항상 즉시 알람".
**How to avoid:** 아래 Open Questions #1 참조 — planner가 억제 메커니즘 결정 필요.
**Warning signs:** 배포 후 "안전모 미착용" 이벤트가 착용자에게도 발생.

### Pitfall 2: _fusion_buffer autouse fixture 누락

**What goes wrong:** `test_fusion.py`의 autouse fixture가 `_fusion_buffer.clear()`를 빠뜨리면
이전 테스트의 buffer 상태가 다음 테스트에 누설된다. Phase 2 `test_scheduler_buffer.py`와
동일한 문제.
**How to avoid:** `reset_module_state` fixture에 `scheduler._fusion_buffer.clear()` 추가.

### Pitfall 3: detect_all()에서 yolov5 conf_thres 미적용

**What goes wrong:** yolov5 분기는 `self.model(image_bgr, size=...)` 호출 시 `self.model.conf`
가 이미 `__init__`에서 설정되므로 문제 없지만, yolov8 분기는 `predict()` 호출 시 `conf=self.conf_thres`
를 명시해야 한다. `detect()`에서 이미 처리되고 있으나 `detect_all()`에서도 동일하게 적용 필요.
**How to avoid:** `detect()` yolov8 분기의 `predict_kwargs` dict를 그대로 복사.

### Pitfall 4: IoU 계산 division by zero

**What goes wrong:** 두 bbox가 모두 degenerate (width=0 또는 height=0)이면 union=0, ZeroDivisionError.
**How to avoid:** `iou_xyxy`에서 `return 0.0 if union == 0 else intersection / union`.

### Pitfall 5: hardhat_height default = 0 시 head 영역 축소

**What goes wrong:** helmet 0건일 때 `hardhat_height`에 어떤 값을 쓸지 미정.
`compute_fusion_helmet_missing`에서 helmet이 없으면 `hardhat_is_on`을 호출할 필요 자체가 없다
(person 있고 helmet 0개 → 즉시 미착용). 단, D-08은 `hardhat_height default = person_height * 0.1`을
명시 — 단일 helmet 있을 때 사용할 default.
**How to avoid:** `compute_fusion_helmet_missing`에서 helmet 리스트가 비어있으면 즉시 `True` 반환.
hardhat_is_on은 helmet이 있을 때만 호출.

### Pitfall 6: _process_fusion_for_camera에서 tmp_path 중복

**What goes wrong:** `_process_detection_for_camera`와 `_process_fusion_for_camera`가 같은
`tmp_path` 이름 패턴을 쓰면 동시 실행 시 파일 충돌. run_detection_cycle은 직렬 실행이지만
fusion key + camera 조합이 많아지면 주의.
**How to avoid:** `tmp_name = f"fusion_{fusion_key}_{camera_id}_{int(now * 1000)}.jpg"` — prefix를
`fusion_`으로 다르게 지정.

---

## D-04 Implementation Gap — 억제 메커니즘 옵션

D-04는 helmet 단독 알람 경로를 "제거"하도록 지정하지만, 구체적인 억제 코드 경로를 명시하지 않는다.
세 가지 구현 옵션이 있다:

**Option A: `disabled` 플래그 (권장)**
```python
# detector_configs.py
"helmet": { ..., "disabled": True, ... }

# run_detection_cycle
cfg = DETECTOR_CONFIGS.get(event_key)
if cfg is None or cfg.get("disabled", False):
    continue
```
장점: 다른 detector 코드 무변경. disabled 상태를 명시적으로 문서화.
단점: `detectors` dict에 여전히 helmet 인스턴스 포함 (로드 비용 존재).

**Option B: run_detection_cycle에서 helmet 스킵**
```python
FUSION_ONLY_DETECTORS = {"helmet"}  # scheduler.py 상수

for event_key, detector in detectors.items():
    if event_key in FUSION_ONLY_DETECTORS:
        continue
```
장점: detector_configs.py 변경 최소. 스킵 의도 명확.
단점: scheduler.py에 예외 케이스 코딩.

**Option C: DETECTOR_CONFIGS에서 helmet 제거**
```python
# detector_configs.py에서 helmet 항목 완전 제거
# main.py에서 helmet GenericYoloDetector 인스턴스를 detectors dict에 직접 추가
```
장점: run_detection_cycle 코드 단순.
단점: main.py 수정 + helmet 인스턴스 init 코드 분산.

**Planner 결정 필요** — 세 옵션 모두 기술적으로 올바르다.

---

## Validation Architecture

> config.json에 `nyquist_validation` 키 없음 → 기본값 enabled [VERIFIED: config.json 읽기]

### Test Framework

| Property | Value |
|----------|-------|
| Framework | pytest 9.0.3 [VERIFIED: pip3 show] |
| Config file | 없음 (Phase 2 기준 — `ai_agent/tests/` 직접 실행) |
| Quick run command | `cd ai_agent && python -m pytest tests/test_fusion.py -x -q` |
| Full suite command | `cd ai_agent && python -m pytest tests/ -q` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FUSION-01 | IoU > 0.3 한 쌍 → True | unit | `pytest tests/test_fusion.py::test_collision_iou_above_threshold -x` | ❌ Wave 0 |
| FUSION-01 | IoU ≤ 0.3 → False | unit | `pytest tests/test_fusion.py::test_collision_iou_below_threshold -x` | ❌ Wave 0 |
| FUSION-01 | 빈 list → False | unit | `pytest tests/test_fusion.py::test_collision_empty_list -x` | ❌ Wave 0 |
| FUSION-01 | 3 cycle 연속 → 알람 1회 | unit | `pytest tests/test_fusion.py::test_process_fusion_3cycles -x` | ❌ Wave 0 |
| FUSION-02 | person 1명 + helmet in head 영역 → False | unit | `pytest tests/test_fusion.py::test_helmet_worn -x` | ❌ Wave 0 |
| FUSION-02 | person 1명 + helmet 0개 → True | unit | `pytest tests/test_fusion.py::test_helmet_missing_no_helmet -x` | ❌ Wave 0 |
| FUSION-02 | person 2명 + helmet 1개 (한 명만 매칭) → True | unit | `pytest tests/test_fusion.py::test_helmet_missing_partial -x` | ❌ Wave 0 |
| FUSION-01+02 | D-10 case 8: 3 cycle False → 알람 0회 | unit | `pytest tests/test_fusion.py::test_process_fusion_no_alarm_after_false -x` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `cd ai_agent && python -m pytest tests/test_fusion.py -x -q`
- **Per wave merge:** `cd ai_agent && python -m pytest tests/ -q`
- **Phase gate:** Full suite (`tests/`) green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `ai_agent/tests/test_fusion.py` — covers FUSION-01, FUSION-02, D-10 8 cases
- [ ] `ai_agent/fusion_helpers.py` — `iou_xyxy`, `hardhat_is_on`, `compute_fusion_collision`, `compute_fusion_helmet_missing`
- [ ] `ai_agent/fusion_configs.py` — `FUSION_CONFIGS` dict

*(기존 `ai_agent/tests/__init__.py` — Phase 2에서 생성됨, 재사용 가능)*

---

## Security Domain

> config.json에 `security_enforcement` 키 없음 → 기본값 enabled [VERIFIED: config.json 읽기]

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | 내부 Python agent — 외부 인증 불필요 |
| V3 Session Management | no | stateless per-cycle |
| V4 Access Control | no | 단일 프로세스 trusted boundary |
| V5 Input Validation | yes | bbox 좌표 bounds 검사 (음수, 0 division), frame_width > 0 |
| V6 Cryptography | no | 암호화 연산 없음 |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| `iou_xyxy` division by zero (union=0) | Tampering | `return 0.0 if union == 0 else ...` |
| `_fusion_buffer` 무한 성장 | DoS | `deque(maxlen=max(5, frames_required))` — Phase 2 T-02-02와 동일 패턴 |
| `detect_all()` 결과 리스트 무한 성장 (매우 많은 bbox) | DoS | YOLO NMS가 이미 상한 적용. `conf_thres` 필터로 결과 제한. 추가 방어 불필요. |
| 모듈 상태 dict 오염 (`_fusion_buffer`) | Tampering | 1프로세스 PoC trusted boundary. v1.1 다중 프로세스 진입 시 검토. |
| `frame_width = 0`으로 인한 head 영역 clamp 오동작 | Tampering | `frame_width = max(1, img.shape[1])` 방어 추가 권장 |

---

## Runtime State Inventory

> 이 Phase는 신규 추가 (greenfield additive) 이며 rename/refactor 아님 → 생략.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| ultralytics | detect_all() | ✓ | 8.3.217 | — |
| opencv-python | cv2.imread, img.shape | ✓ | 4.12.0.88 | — |
| numpy | bbox array 조작 (선택적) | ✓ | 2.2.6 | — |
| pytest | test_fusion.py | ✓ | 9.0.3 | — |
| Supabase (운영 DB) | 마이그레이션 009 적용 | ✓ (assumed) | — | local test 불필요 |

**Missing dependencies:** 없음. Phase 3는 기존 스택 위에서 완전히 동작.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `DETECTOR_CONFIGS["person"]["camera_ids"] = [3]` 는 단독 알람 경로 매핑만 의미하며, `detectors["person"]` 인스턴스를 fusion에서 직접 호출하는 것은 main.py의 init 코드와 충돌하지 않는다 | Code Examples (cross-camera) | main.py가 camera_ids 기반으로 detector 인스턴스를 제한하면 fusion에서 person lookup 실패 |
| A2 | Supabase 운영 DB에 `event_types` 테이블의 `event_name` 컬럼에 UNIQUE constraint가 있다 (ON CONFLICT 사용 근거) | 마이그레이션 패턴 | constraint 없으면 중복 삽입 발생 — 006/008 패턴이 이미 ON CONFLICT 사용 중이므로 MEDIUM confidence |

**대부분의 클레임은 소스 파일에서 직접 검증됨 — ASSUMED 항목 최소.**

---

## Open Questions

1. **D-04 helmet 단독 알람 억제 메커니즘 — Planner 결정 필요**
   - What we know: CONTEXT D-04는 helmet 단독 알람을 "더 이상 발사 X"로 명시. `run_detection_cycle`은 DETECTOR_CONFIGS 전 항목을 순회.
   - What's unclear: target_classes=None + frames_required=1 설정만으로는 억제되지 않는다. 별도 억제 코드 경로가 필요하다.
   - Recommendation: 위 Option A (`"disabled": True` 플래그)를 채택. 가장 명시적이고 다른 코드 경로 변경 최소. DETECTOR_CONFIGS에 `"disabled": True` 추가 + `run_detection_cycle`에서 `if cfg.get("disabled"): continue`.

2. **detect_all() 호출 시 yolov5 중복 추론 여부**
   - What we know: `detect()`는 `self.model(image_bgr, size=self.img_size)`를 매번 호출. `detect_all()`도 동일 호출이면 같은 frame에 두 번 추론된다.
   - What's unclear: fusion에서 같은 img에 `detect()`와 `detect_all()`이 동시에 필요한 경우가 있는가? — 없음. fusion은 `detect_all()`만 사용.
   - Recommendation: `_process_fusion_for_camera`는 `detect_all()`만 호출. `detect()`는 기존 `_process_detection_for_camera` 전용. 중복 없음.

---

## Sources

### Primary (HIGH confidence)
- `ai_agent/scheduler.py` — 전체 읽기. 모듈 상태 dict (line 43-53), `_process_detection_for_camera` (line 210-296), `run_detection_cycle` (line 299-339) 직접 확인.
- `ai_agent/yolo_detector.py` — 전체 읽기. detect() 내부 yolov5/v8 분기 (line 130-184) 직접 확인.
- `ai_agent/detector_configs.py` — 전체 읽기. DETECTOR_CONFIGS 4 항목 현재 상태 직접 확인.
- `ai_agent/tests/test_scheduler_buffer.py` — 전체 읽기. 8 test case 패턴 직접 확인.
- `ai_agent/supabase_client.py` — 전체 읽기. `register_ai_event` 시그니처 직접 확인.
- `supabase/migrations/` dir 출력 — 마이그레이션 번호 009 가용 확인 (001-006, 008, 010 존재).
- `supabase/migrations/006_seed_data.sql` — `"안전모 미착용"` 기존 시드 확인.
- `supabase/migrations/008_event_types_extension.sql` — ON CONFLICT idempotent 패턴 확인.
- `D:\2025_산업안전\산업안전\모델 7종\사람 탐지\utils\bbox_utils.py` — hardhat_is_on 레거시 코드 직접 읽기. y2 clamp 버그 확인.
- `.planning/config.json` — nyquist_validation 키 없음, security_enforcement 키 없음 확인.
- `pip3 show` — ultralytics 8.3.217, opencv-python 4.12.0.88, numpy 2.2.6, pytest 9.0.3 확인.

### Secondary (MEDIUM confidence)
- `.planning/phases/02-vision-frames-required/02-CONTEXT.md` + `02-SUMMARY.md` — Phase 2 패턴 (buffer, cooldown, test structure) 이해.
- `.planning/phases/03-vision-bbox-fusion/03-CONTEXT.md` — 12 locked decisions 전체.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 설치 버전 직접 확인
- Architecture: HIGH — 소스 코드 직접 검증
- Code patterns (detect_all, buffer): HIGH — detect() 내부 구조 확인 완료
- Legacy hardhat_is_on: HIGH — 원본 파일 직접 읽기, 버그도 확인
- Migration number: HIGH — dir 출력으로 009 가용 확인
- D-04 억제 메커니즘: MEDIUM — CONTEXT가 결과를 명시하나 코드 경로를 미정으로 남김

**Research date:** 2026-05-14
**Valid until:** 2026-06-14 (30일 — 안정적 Python 스택)
