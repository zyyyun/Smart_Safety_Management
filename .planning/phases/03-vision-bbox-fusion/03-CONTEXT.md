# Phase 3: 비전 — bbox 겹침/공간 매칭 - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

`ai_agent/scheduler.py` 의 detection 사이클에 **다중 detector cross-reference** 룰을
추가한다. 두 개의 신규 이벤트가 발생한다:

1. **FUSION-01 "지게차 충돌 위험" (DANGER)** — forklift bbox 와 person bbox 의 IoU > 0.3
   이 N=3 frame 연속이면 알람. Phase 2 의 단독 forklift "지게차 진입" (WARNING)
   이벤트는 그대로 유지하면서 *상위 위험 이벤트* 로 추가됨.
2. **FUSION-02 "안전모 미착용" (WARNING)** — person bbox 의 head 영역 (top 25% + ±width/6
   margin) 내에 helmet 객체 중심점이 없으면 알람. **Phase 1 D-05 의 단독 head 알람
   경로를 fusion 으로 대체** (ROADMAP Phase 3 SC #2 — "단일 detector 의 head/no_helmet
   클래스 출력에 의존하지 않음").

**Out of scope** (다른 phase / future):
- 검증셋 100장 라벨링 + 자동 평가 스크립트 (Phase 5 — EVAL-02)
- 워치 (J2208A) fusion (Phase 4 — WATCH 트랙)
- Person ↔ fire bbox fusion (v1.1 — 인명·화재 동시 위험 우선순위 결정)
- 다중 사람 × 다중 지게차 매칭 최적화 (v1.0 은 모든 pair O(N×M) brute-force)
- 데모 영상 신규 촬영 (forklift+person 같은 frame) — v1.1 검단·포천 현장 영상
  수집. v1.0 검증은 mock pytest + 합성 입력 으로 한정 (Phase 1 D-06 / Phase 2 D-03
  의 단위 검증 패턴 연장).

</domain>

<decisions>
## Implementation Decisions

### Multi-bbox API 추가 (D-01)
- **D-01: `GenericYoloDetector.detect_all(image_bgr) -> list[DetectResult]` 신규 메서드**
  - 기존 `detect()` (best 1건 반환) **유지 — backward compat**. Phase 1/2 호출 경로 무영향.
  - 신규 `detect_all()` 가 same inference 결과에서 *모든* bbox 추출 — yolov5/v8 분기
    동일 (`xyxy[0]` ndarray 또는 `boxes.xyxy.tolist()`).
  - 각 bbox 가 `DetectResult(is_detected=True, confidence, bbox, label, inference_ms)`
    1건. 빈 리스트 = no detection.
  - `target_classes` 필터는 `detect_all()` 도 동일 적용 (label 화이트리스트).
  - 위치: `ai_agent/yolo_detector.py` 의 `GenericYoloDetector` 클래스 내 (line 130 옆).

### Fusion helper 모듈 신규 (D-02)
- **D-02: 신규 모듈 `ai_agent/fusion_helpers.py`**
  - `iou_xyxy(bbox_a, bbox_b) -> float` — 순수 numpy-free 함수 (x1,y1,x2,y2 입력).
    `yolov7_fork/utils/general.py` 의 `box_iou` 는 torch 기반이라 재사용 불가.
  - `hardhat_is_on(person_bbox, helmet_bbox, frame_width) -> bool` — 레거시 패턴 이식.
    레거시 출처: `D:\2025_산업안전\산업안전\모델 7종\사람 탐지\utils\bbox_utils.py:10-29`.
    - head 영역 가로 = `[bbox[0] + width/2 - width/6, bbox[0] + width/2 + width/6]`
      (clipped to frame_width)
    - head 영역 세로 = `[bbox[1] - hardhat_height/2, bbox[3] - person_height*3/4]`
      (top 25% + 상단 hat margin)
    - 매칭 = helmet bbox center point ∈ head 영역
  - `compute_fusion_collision(forklifts: list[DetectResult], persons: list[DetectResult],
    iou_thres: float) -> bool` — pairwise IoU 검사, 임계 초과 1쌍 이상이면 True.
  - `compute_fusion_helmet_missing(persons: list[DetectResult], helmets: list[DetectResult],
    frame_width: int) -> bool` — person 마다 head 영역 안에 helmet 1개 이상 있으면
    "착용". 한 명이라도 helmet 미매칭이면 True (안전모 미착용 알람).
  - 모든 함수 pure (no side effects), unit test 친화.

### Fusion 설정 dict 신규 (D-03)
- **D-03: 신규 dict `FUSION_CONFIGS` in `ai_agent/fusion_configs.py`** (별도 파일,
  detector_configs.py 와 분리 — 의미 다름)
  - 항목 키: fusion event 식별자 (`forklift_collision`, `helmet_missing`)
  - 값 schema:
    ```
    {
      "event_name": "지게차 충돌 위험",      # event_types 테이블 매핑
      "risk_level": "DANGER",                # DANGER / WARNING / CAUTION
      "detectors_required": ["forklift", "person"],  # DETECTOR_CONFIGS 키 list
      "camera_ids": [4],                     # fusion 카메라 (보통 한 detector 의
                                             # 카메라 재사용. 두 detector 의
                                             # camera_ids 가 다르면 fusion 측이
                                             # dominant)
      "rule": "iou_gt",                       # iou_gt | hardhat_missing
      "threshold": 0.3,                       # 룰별 임계
      "frames_required": 3,                  # N 연속 buffer (D-06)
      "storage_prefix": "forklift_collision", # 스냅샷 prefix
    }
    ```
  - 2 entries 만 정의 — `forklift_collision` (camera_ids=[4]), `helmet_missing`
    (camera_ids=[5]).

### helmet detector 의 단독 알람 경로 제거 (D-04)
- **D-04: Phase 1 D-05 의 helmet `target_classes=['head']` 단독 알람 경로 → fusion 으로
  대체**
  - `DETECTOR_CONFIGS["helmet"]["target_classes"]` `['head']` → **`None`** (head + helmet
    두 라벨 모두 detect_all 결과에 포함되도록).
  - `DETECTOR_CONFIGS["helmet"]["frames_required"]` `3` → **`1`** (단독 buffer 알람 경로
    제거. fusion event 의 `frames_required=3` 이 대체).
  - **Phase 2 의 frames_required 룰 자체는 유지** — fusion event 가 `_fusion_buffer` 의
    N 연속 검사로 동일 룰 적용 (D-06).
  - helmet detector 의 단독 `_process_detection_for_camera` 알람은 **fusion 으로 흡수
    되어 더 이상 발사 X**. ROADMAP Phase 3 SC #2 가 명시한 의도된 동작.
  - 검증 측면: Phase 1 의 detection_events `ev=24/25 label='head'` baseline 은 **Phase 3
    적용 후 더 이상 생성되지 않음** — fusion event ("안전모 미착용" 위 D-03
    `helmet_missing`) 으로 *완전 대체*. Phase 1 SC #2 의 unit 동작은 Phase 3 의
    `compute_fusion_helmet_missing` unit test 가 인계.

### Fusion 처리 흐름 (D-05)
- **D-05: 신규 함수 `_process_fusion_for_camera` in `scheduler.py`**
  - 위치: `_process_detection_for_camera` 옆 (line ~210).
  - 흐름:
    1. cooldown 검사 (`(camera_id, fusion_key)` 키로 `_detection_cooldown` 공유 — 단일
       module dict 재사용)
    2. snapshot 1회 (`capture()` 동일 코드) → `tmp_path` → `cv2.imread`
    3. `cfg["detectors_required"]` 의 각 detector 에 대해 `detector.detect_all(img)` 호출
       (이미 init 된 GenericYoloDetector 인스턴스 재사용 — `detectors` dict 에서 lookup)
    4. rule 분기:
       - `rule == "iou_gt"` → `compute_fusion_collision(detector_a_list, detector_b_list,
         cfg["threshold"])`
       - `rule == "hardhat_missing"` → `compute_fusion_helmet_missing(persons, helmets,
         img.shape[1])`
    5. boolean 결과 → `_fusion_buffer` push + N 연속 검사 (D-06)
    6. 알람 발사 시 `bridge.register_ai_event(camera_id, cfg["event_name"],
       cfg["risk_level"], accuracy=max_conf, image_url=public_url)` — 기존
       SupabaseBridge 재사용. accuracy 는 fusion 의 최대 conf (IoU 의 경우 두 bbox 의
       max conf, hardhat_missing 의 경우 person conf 사용).
  - `run_detection_cycle` 끝부분에 추가 루프 — `for fusion_key, cfg in
    FUSION_CONFIGS.items(): for cam in target_cams: msg = _process_fusion_for_camera(...)`.
    기존 detector 루프는 그대로.

### Fusion buffer (D-06)
- **D-06: `_fusion_buffer: dict[tuple[int, str], deque[bool]] = {}` — 신규 module dict**
  - 위치: `scheduler.py` 의 `_detection_buffer` (line 53) 옆.
  - 키: `(camera_id, fusion_key)` — Phase 2 의 `_detection_buffer` 와 동일 패턴.
  - 값: `deque[bool]` (maxlen = `max(5, frames_required)`).
  - push: 매 cycle `_process_fusion_for_camera` 의 rule 결과 (True/False).
  - N 연속 검사: `len(buffer) >= N and all(list(buffer)[-N:])` (Phase 2 D-07 그대로).
  - 알람 발사 후 `buffer.clear()` + cooldown 갱신 (Phase 2 D-02 그대로).
  - **별도 dict 인 이유**: detector event_key (`fire`, `helmet`, `forklift`, `person`)
    와 fusion event_key (`forklift_collision`, `helmet_missing`) 가 의미 다름. 충돌
    회피를 위해 분리. 단 cooldown 은 공유 (`_detection_cooldown`) — 같은 카메라의
    detector 와 fusion 알람이 cooldown 측면에선 동등하게 동작.

### FUSION-01 임계값 (D-07)
- **D-07: IoU > 0.3, frames_required = 3**
  - IoU 0.3 = ROADMAP Phase 3 SC #1 명시값. 두 bbox 가 약 30% 겹치면 "근접" 신호.
    검단·포금속가공 현장 forklift 작업 공간 시뮬레이션 시 v1.1 에서 보정 (0.2~0.4
    range).
  - frames_required = 3 = forklift detector 의 1-frame spike (지게차 통과 직후) 흡수.
    fire 의 5 (10분 연속) 보다 짧고 helmet 의 3 과 동일 — 3 frame ≈ 3분 cycle 가정 시
    "지속 충돌 위험". 단일 cycle false positive 흡수.
  - cooldown = `DETECTORS_COOLDOWN_MIN` (10분) 공유. 알람 폭증 방지.

### FUSION-02 임계값 + head 영역 정의 (D-08)
- **D-08: head 영역 = person bbox top 25% × ±width/6 margin (레거시 그대로), frames_required = 3**
  - 가로: `[center - width/6, center + width/6]` (= person 너비의 1/3 중앙) + frame
    경계 clamp.
  - 세로: `[bbox[1] - hardhat_height/2, bbox[3] - height*3/4]` (= top 25% 와 head 위쪽
    여백) + 0 clamp.
  - 매칭 방식: helmet bbox **center point** ∈ head 영역 (`point_in_area`). IoU 가 아닌
    point-in-region — 레거시 패턴 그대로.
  - hardhat_height 는 helmet bbox 의 높이로 동적 계산 (한 helmet 만 있을 땐 그 값
    사용, 여러 helmet 시 max). helmet 0건이면 default = person_height * 0.1.
  - frames_required = 3 = Phase 1 의 helmet `target_classes=['head']` 3 frame 연속
    동작과 동일한 의도 (일시적 가림 흡수).
  - **다중 사람 처리**: 한 frame 에 person N명 + helmet M개 → 각 person 별로
    "matched/unmatched" 평가 → 한 명이라도 unmatched 면 True (알람). v1.0 PoC 의
    단순 정책. v1.1 에서 사람별 개별 알람 가능성 검토.

### 카메라 매핑 + person detector cross-camera 실행 (D-09)
- **D-09: forklift_collision = camera_id 4, helmet_missing = camera_id 5. person detector
  를 두 카메라에 모두 run.**
  - `forklift_collision` 카메라 = forklift detector 가 이미 사용 중인 camera_id 4 재사용.
    person detector 가 camera_id 4 에서도 `detect_all()` 호출되어야 함 (cross-camera).
  - `helmet_missing` 카메라 = helmet detector 가 이미 사용 중인 camera_id 5 재사용.
    person detector 가 camera_id 5 에서도 `detect_all()` 호출.
  - person detector 의 단독 "혼잡도 경고" 알람은 기존 camera_id 3 에서만 계속 발사
    (DETECTOR_CONFIGS unchanged). fusion 의 person detect_all 은 *별도* 호출 (단독
    알람 경로와 무관 — fusion 처리 안에서만 사용).
  - **trade-off**: person detector 가 3 카메라 (3·4·5) 에서 cycle 마다 추론 → 추론
    비용 ~3배. v1.0 1분 cycle 라 허용. v1.1 에서 frame caching 검토.

### 검증 방식 (D-10)
- **D-10: pytest unit test 우선 + 데모 시점 1회 통합 검증**
  - 신규 `ai_agent/tests/test_fusion.py` — 합성 입력으로 fusion helper 단위 테스트.
    최소 8 케이스:
    1. `iou_xyxy` — 동일 bbox = 1.0, 분리 bbox = 0.0, 부분 겹침 = 정확한 IoU 수치
    2. `compute_fusion_collision` — IoU 0.35 (>0.3) 한 쌍 → True
    3. `compute_fusion_collision` — IoU 0.20 (≤0.3) → False
    4. `compute_fusion_collision` — 빈 list → False
    5. `compute_fusion_helmet_missing` — person 1명 + helmet center point in head
       영역 → False (안전모 착용)
    6. `compute_fusion_helmet_missing` — person 1명 + helmet 0개 → True (미착용)
    7. `compute_fusion_helmet_missing` — person 2명 + helmet 1개 (한 명만 매칭) →
       True (한 명 미착용)
    8. `_process_fusion_for_camera` (mock detector + mock bridge) — 3 cycle 연속 True
       → 알람 1회 + buffer.clear, 다음 cycle False → 알람 0회
  - 데모 시점 통합 검증 = `python -m ai_agent.main --once-detect` 1회 + 합성/실제
    영상 1회 + `select event_id, event_type_id, accuracy, camera_id from
    detection_events where event_type_id in ((지게차 충돌 위험), (안전모 미착용))
    order by created_at desc limit 5;` 로 fusion 행 적재 확인.
  - 단일 cycle 검증 = `frames_required=3` 이라 데모 시점은 데몬 모드 (`python main.py`
    + 3분 wait) 또는 mock 으로 buffer pre-fill (Phase 2 D-06 의 "데몬 모드" 컨벤션
    그대로).

### detection_events 신규 event_type (D-11)
- **D-11: 마이그레이션 `supabase/migrations/009_phase3_fusion_event_types.sql` 추가**
  - 신규 `"지게차 충돌 위험"` event_type INSERT (ON CONFLICT DO NOTHING).
  - `"안전모 미착용"` 은 006_seed_data.sql 에 이미 시드 — 신규 추가 X.
  - fusion 알람은 `register_ai_event(event_name=cfg["event_name"], ...)` 로 동일
    `detection_events` 테이블 사용. helmet 단독 알람 경로 제거로 인해 "안전모 미착용"
    이벤트의 *발생 경로* 만 변경됨 (단독 detector → fusion).

### Phase 2 frames_required 룰과의 결합 (D-12)
- **D-12: Phase 2 의 단독 detector frames_required 룰은 fusion 과 *직교 동작***
  - fire (N=5), forklift (N=1), person (N=1) 의 단독 알람 = Phase 2 동작 그대로.
  - helmet 의 단독 알람만 제거 (D-04) — fusion 으로 대체.
  - fusion 의 frames_required (forklift_collision=3, helmet_missing=3) 는 *별도* buffer
    (D-06) 으로 독립 운영. 단독 detector buffer 와 무관 (각자 자기 결과 push).
  - 결과: 한 camera_id=4 cycle 에서 forklift 단독 알람 (1프레임만으로) + forklift_collision
    fusion 알람 (3프레임 연속 + IoU>0.3) 둘 다 발사 가능. 의미 다른 두 이벤트 (위험도
    레벨 다름: WARNING vs DANGER).

### Claude's Discretion
- person 의 `target_classes = ['person']` 그대로 유지 — fusion 에서도 `detect_all` 호출
  시 target 필터링 적용되어 person bbox 만 추출. 변경 X.
- forklift 의 `target_classes = None` 유지 — `forklift_1`/`forklift_2` 둘 다 OK
  (현재와 동일).
- fusion event 의 accuracy 컬럼 의미 — IoU rule 은 max 두 conf, hardhat_missing rule
  은 unmatched person conf 사용 (가독성 우선).
- helmet detector 의 v1.1 fine-tune 시 fusion 의 helmet bbox 안정성 ↑ 기대 — D-08 의
  point-in-area 매칭은 hat detection 의 false positive 에 *민감* (false positive 1개가
  "착용" 으로 인식). v1.0 은 helmet 가중치 한계 인지 후 진행, v1.1 에서 fine-tune 결합.
- person detector 의 yolov8x.pt (~130MB) cross-camera 호출 비용 — 1분 cycle 라 3카메라
  × ~200ms = 600ms < 60s, 허용. v1.1 에서 단일 frame caching 검토.
- log prefix — `[FUSION]` / `[no_fusion]` / `[fusion_skip_cooldown]` / `[no_fusion_yet]`
  (Phase 2 의 `[DETECT]`/`[no_detect]`/`[no_alert_yet]` 컨벤션 연장).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 3 직접 입력
- `.planning/REQUIREMENTS.md` §3 모델 트랙 단계 2 (FUSION-01·02) — 본 phase 의 요구사항
- `.planning/ROADMAP.md` Phase 3 섹션 — Goal · 3 Success Criteria · Depends on
- `.planning/phases/01-vision-demo-videos/01-CONTEXT.md` (D-04, D-05, D-19) — helmet
  detector 의 단독 알람 경로 (Phase 1 lock). Phase 3 의 D-04 가 이를 fusion 으로
  대체하는 입력.
- `.planning/phases/02-vision-frames-required/02-CONTEXT.md` (D-01, D-02, D-04, D-06,
  D-07) — `_detection_buffer` 패턴 + N 연속 검사 + cooldown 상호작용. Phase 3 의 D-06
  이 동일 패턴으로 `_fusion_buffer` 추가.
- `.planning/phases/02-vision-frames-required/02-SUMMARY.md` — 8 pytest pass 패턴 +
  데몬 모드 컨벤션 (`run_detection_cycle` × N + assertion). Phase 3 의 D-10 이 이
  컨벤션 따름.
- `C:\Users\ANNA\.claude\plans\iridescent-percolating-fox.md` §C "모델 트랙 단계 2 —
  bbox 겹침 / 공간 매칭 (W2, ~3일)" — 본 phase 의 원천 컨텍스트

### 레거시 패턴 출처
- `D:\2025_산업안전\산업안전\모델 7종\사람 탐지\utils\bbox_utils.py:10-29` — hardhat_is_on
  레거시 패턴. D-08 의 head 영역 계산 + helmet center point-in-area 매칭 출처.
- `D:\2025_산업안전\산업안전\모델 7종\사람 탐지\trackers\person_tracker.py:52` —
  hardhat_is_on 호출 원본 컨텍스트.

### 코드/스키마 출처
- `ai_agent/scheduler.py` — `_detection_cooldown` 패턴 (line 53), `_detection_buffer`
  패턴 (line 53), `_process_detection_for_camera` (line 210-296), `run_detection_cycle`
  (line 299-339). Phase 3 가 _fusion_buffer + _process_fusion_for_camera 동일 패턴 추가.
- `ai_agent/detector_configs.py` — `DETECTOR_CONFIGS` 4 항목. Phase 3 의 D-04 가
  helmet 항목 일부 변경 (`target_classes` None, `frames_required` 1).
- `ai_agent/yolo_detector.py:130-184` — `GenericYoloDetector.detect()`. Phase 3 의 D-01
  이 같은 클래스에 `detect_all()` 추가.
- `ai_agent/yolo_detector.py:140-163` — yolov5/v8 multi-bbox 추출 패턴 (이미 코드에
  존재하지만 단일 best 만 반환). D-01 이 이 패턴을 `detect_all` 로 노출.
- `ai_agent/tests/test_scheduler_buffer.py` — Phase 2 의 8 case pytest 패턴. Phase 3 의
  `test_fusion.py` 가 동일 구조 따름 (`make_detector` mock + `run_n_cycles` harness).
- `ai_agent/supabase_client.py` (`SupabaseBridge.register_ai_event`) — fusion 알람
  insert API. Phase 3 변경 X, 그대로 호출.
- `supabase/migrations/006_seed_data.sql:6-13` — `event_types` 기본 시드 (안전모
  미착용 포함).
- `supabase/migrations/008_event_types_extension.sql` — 지게차/혼잡도 ON CONFLICT
  패턴. Phase 3 의 D-11 마이그레이션 009 가 동일 패턴 따름.

### 환경 / 매핑
- `cameras` 테이블의 camera_id 1·3·4·5 행 — fusion 카메라 (4, 5) + person detector
  cross-camera 실행 대상.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ai_agent/yolo_detector.py:130-184`** `GenericYoloDetector.detect()`: yolov5/v8 분기
  에서 *이미 모든 bbox 를 추출* 후 best 1건만 반환 (line 166-184). D-01 의 `detect_all()`
  은 같은 추출 로직에서 best 선택 단계만 생략 + list 반환. **추가 추론 호출 없음**.
- **`ai_agent/scheduler.py:53`** `_detection_cooldown`, `_detection_buffer` module dict:
  D-06 의 `_fusion_buffer` 동일 패턴 추가. 메모리 fresh on restart (Phase 2 D-06 컨벤션).
- **`ai_agent/scheduler.py:210-296`** `_process_detection_for_camera`: cooldown skip →
  snapshot → detect → buffer push → N 연속 검사 → 알람 발사 흐름. Phase 3 의
  `_process_fusion_for_camera` 가 동일 7단계 흐름, 단 step 3 (`detect`) 가 detect_all ×
  2 detector + fusion helper 로 확장.
- **`ai_agent/scheduler.py:227-235`** snapshot capture 패턴 (`tmp_path` + `capture()` +
  `cv2.imread`): fusion 도 frame 1회 캡처만 — `detect_all()` 가 같은 img 에 두 번
  추론. 비용 절감.
- **`ai_agent/supabase_client.py:SupabaseBridge.register_ai_event`**: fusion 알람도
  동일 API 호출 — event_name 이 fusion 의 신규 event_type 매핑.
- **`ai_agent/tests/test_scheduler_buffer.py`**: `make_detector` mock + `run_n_cycles`
  harness. D-10 의 test_fusion.py 가 동일 패턴, mock 만 detect_all (list) 로 확장.

### Established Patterns
- **모듈 상태 dict 패턴** (scheduler.py:42-53): tuple key, 단순값 dict. fusion buffer
  도 동일 (단순값이 deque[bool]).
- **`[PREFIX]` log 컨벤션** (scheduler.py 곳곳): `[DETECT]` / `[no_detect]` /
  `[detect_skip_cooldown]` / `[no_alert_yet]`. fusion 도 `[FUSION]` / `[no_fusion]` /
  `[fusion_skip_cooldown]` / `[no_fusion_yet]`.
- **lazy buffer 생성** (scheduler.py:246-249): `_detection_buffer.get(key)` 후 `None`
  이면 생성. fusion buffer 도 동일.
- **cooldown 공유 dict** (`_detection_cooldown`): detector + fusion event 가 같은 dict
  사용. 같은 camera 의 다른 event 는 별개 키 (`(camera_id, event_key)` 와
  `(camera_id, fusion_key)` 가 다름).
- **마이그레이션 ON CONFLICT idempotent** (008_event_types_extension.sql): D-11 의
  009 마이그레이션 동일 패턴.

### Integration Points
- **DETECTOR_CONFIGS["helmet"] 변경 — Phase 1 D-05 부분 revert**: D-04 의 의도된 변경.
  detector_configs.py 의 helmet 항목 `target_classes` None + `frames_required` 1.
  Phase 1/2 의 helmet 단독 알람 경로가 사라지는 명확한 신호 — 주석 추가 필수.
- **scheduler.py 의 run_detection_cycle 끝부분에 fusion loop 추가**: 기존 detector
  loop (line 318-338) 와 *별도* loop. 같은 사이클에서 detector 단독 + fusion 모두
  발사 가능.
- **person detector 의 cross-camera 호출**: fusion 처리 안에서만 발생. `_process_fusion_for_camera`
  가 `detectors["person"]` 인스턴스를 lookup 하고 같은 frame 으로 `detect_all` 호출.
  DETECTOR_CONFIGS["person"]["camera_ids"] = [3] 은 *단독 알람 경로* 매핑만 의미.
- **테스트 위치**: `ai_agent/tests/` (Phase 2 가 생성). Phase 3 가 `test_fusion.py` 추가.
- **마이그레이션 번호**: 008 까지 사용 중 → 009 가 다음. 010 은 Phase 4 watch_pipeline 이
  이미 사용 → 009 는 *비어있음* (커밋 hash 확인 필요). Phase 3 는 009 또는 011 사용.

</code_context>

<specifics>
## Specific Ideas

- **helmet detector 단독 알람 제거 = ROADMAP Phase 3 SC #2 의 정확한 의도** (D-04):
  ROADMAP "단일 detector 의 head/no_helmet 클래스 출력에 의존하지 않음" — Phase 3 가
  helmet 알람 책임 인수. Phase 1 의 helmet "안전모 미착용" 알람이 사라지는 동작
  변경은 의도된 것. SUMMARY 작성 시 명시.
- **forklift 의 이중 알람 (단독 진입 WARNING + 충돌 위험 DANGER)** (D-12): 같은
  forklift bbox 가 단독 알람 ("지게차 진입") 과 fusion 알람 ("지게차 충돌 위험")
  둘 다 trigger 가능. 의미 다른 두 이벤트 (위험도 레벨 다름) — 의도된 동작.
  detection_events 에 두 행 적재.
- **레거시 hardhat_is_on 의 point-in-area 매칭은 IoU 와 다름** (D-08): helmet bbox 의
  *중심점* 이 head 영역 안에 있는지 — IoU 보다 단순하고 helmet 객체가 head 영역에
  완전히 포함되지 않아도 OK. 레거시 코드 의도 = "helmet 가 머리에 위치" 의 직관적
  근사. v1.0 그대로 이식.
- **person detector cross-camera 비용** (D-09): 단일 frame inference ~200ms × 3 카메라
  × 1분 cycle = 0.5% overhead. 허용. v1.1 에서 같은 frame 다중 detector cache.
- **합성 입력 unit test 의 한계** (D-10): IoU 0.35 합성 bbox 는 진짜 forklift 영상 자세
  를 모름. v1.0 PoC 검증 한계. Phase 5 EVAL-02 의 검증셋 100장 단계에서 실제 imagery
  로 보정 가능성 확인.

</specifics>

<deferred>
## Deferred Ideas

- **검단·포천 현장 영상 (forklift+person 같은 frame)** — v1.1 6월 설치 직전 수집. 
  v1.0 PoC 검증은 합성 입력 + mock 으로 한정.
- **사람별 개별 helmet missing 알람** — 현재 D-08 은 "한 명이라도 미착용" → 단일 알람.
  여러 명 미착용 시 N 명분 알람 발사 정책은 v1.1.
- **IoU 임계 0.3 의 현장 보정** (D-07) — 실제 forklift 작업 공간 시뮬 후 0.2~0.4 range
  미세조정. v1.1 EVAL 단계.
- **head 영역 정의의 person pose 의존성** (D-08) — 현재 person bbox 의 axis-aligned
  top 25% 단순 룰. 사람이 누워있거나 측면 시 부정확. v1.1 에서 pose detector
  (yolov7-pose, fall_detector 와 공유) 의 head keypoint 사용 검토.
- **fire ↔ person fusion (인명 화재 위험)** — v1.1 추가 fusion 룰. Phase 5 EVAL 의
  False positive 분석 후 결정.
- **fusion event 의 storage_prefix 별 스냅샷 영구 보존** — 일반 detection_events 와
  동일하게 7일 storage TTL 적용. v1.x 에서 fusion 만 영구 보존 옵션 검토.
- **person detector frame caching** (D-09) — 단일 frame inference 결과를 cycle 내에서
  여러 fusion 에 공유 (재추론 제거). v1.1 성능 최적화.
- **다중 fusion rule chaining** — forklift+person+fire 3-way fusion 같은 복합 룰.
  현재 D-02/D-05 의 2-detector pairwise 만 지원. v2.0 YOLO26 마이그레이션 후.
- **fusion 알람 frames_required 의 차등** — 현재 둘 다 N=3. 충돌 위험 (DANGER) 은
  더 빠르게 N=2, helmet missing (WARNING) 은 더 느리게 N=5 등. v1.1 EVAL 후 보정.
- **frame-level visualization (디버깅용)** — fusion event 발사 frame 의 bbox 오버레이
  + head 영역 표시 이미지 Storage 적재. v1.1 데모 도구.

</deferred>

---

*Phase: 03-vision-bbox-fusion*
*Context gathered: 2026-05-14*
