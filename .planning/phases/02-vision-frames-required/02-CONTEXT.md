# Phase 2: 비전 — frames 연속 룰 - Context

**Gathered:** 2026-05-06
**Status:** Ready for planning

<domain>
## Phase Boundary

`ai_agent/scheduler.py` 의 detection 사이클이 단일 frame conf 변동 (조명, 그림자,
NMS 노이즈 등) 으로 false positive 알람을 발사하지 않도록, **N 프레임 연속 검출**
룰을 추가한다. fire/helmet 의 약한 단일 frame conf (Phase 1 D-19 fallback 의 fire
0.10) 도 5 frames consecutive 면 운영급 의미를 가진다.

**Out of scope** (다른 phase / future):
- bbox 겹침 / IoU 룰 (Phase 3 — FUSION-01)
- head + helmet 공간 매칭 (Phase 3 — FUSION-02)
- 검증셋 100장 라벨링 + 자동 평가 (Phase 5 — EVAL-02)
- D-19 fallback 의 진짜 운영급 conf 0.5+ 도달 (v1.1 fine-tune)
- Phase 1 의 D-04 적용 (이미 완료 — Phase 2 에서 재진행 X)

</domain>

<decisions>
## Implementation Decisions

### Frame Buffer 자료구조 + 위치
- **D-01: scheduler 모듈 상태 dict — `_detection_buffer: dict[tuple[int, str], collections.deque[bool]]`**
  - 위치: `ai_agent/scheduler.py` 의 `_detection_cooldown` 옆 (line ~45)
  - 키: `(camera_id, event_key)` — `_detection_cooldown` 와 동일 패턴
  - 값: `collections.deque[bool]` (maxlen = max(frames_required) = 5)
  - 새 cycle 결과 push: `buffer.append(result.is_detected)`
  - N 연속 검사: `len(buffer) >= N and all(list(buffer)[-N:])`
  - 1프로세스 PoC 휘발성 OK (D-07)

### 알람 발사 후 Buffer 처리
- **D-02: `buffer.clear()` reset — 다음 알람까지 N cycle 누적 + cooldown 둘 다 충족**
  - 알람 후: `buffer.clear()` + `_detection_cooldown[key] = now`
  - 다음 알람 조건 (둘 다 만족): (1) cooldown 만료 (10분), (2) buffer 마지막 N 개 모두 True
  - 1분 cycle 가정: fire (N=5) → 빠르면 5분 후 + cooldown 10분 = 10분 간격 (cooldown 이 룰)
  - false positive 흡수 + 알람 폭증 방지

### 검증 방법
- **D-03: pytest unit test (mocked detector)**
  - `ai_agent/tests/test_scheduler_buffer.py` 신규 작성
  - Mock `GenericYoloDetector.detect()` 결과 (DetectionResult.is_detected boolean)
  - 시퀀스 입력별 알람 발사 횟수 검증:
    - `[True, True, True, True, True]` (fire N=5) → 알람 1회
    - `[True, True, True, True, False]` → 알람 0회
    - `[False, True, True, True, True, True]` → 알람 1회 (last 5 모두 True)
    - `[True, True, True, True, True, True]` → 알람 1회 + clear → 다음 cycle [False] → 알람 0회 (재누적)
    - `[True]` (forklift N=1) → 즉시 알람 1회
  - cooldown 와 상호작용도 unit test 에서 검증 (mock time)
  - end-to-end `--once-detect` 검증은 옵션 — Phase 5 EVAL 에서 본격

### 룰 적용 (DETECTOR_CONFIGS 갱신)
- **D-04: `DETECTOR_CONFIGS` 의 각 detector 에 `frames_required: int` 키 추가**
  - `fire`: 5 (메모리 검증된 임계 — 5분 연속 fire = 운영급)
  - `helmet`: 3 (3분 연속 head 검출 = 운영급, 일시적 가림 흡수)
  - `forklift`: 1 (지게차 진입 1회만으로도 알람 — 시간 누적 필요 X)
  - `person`: 1 (사람 등장 1회만으로도 알람)
  - `frames_required = 1` 인 detector 는 N=1 검사 = 즉시 알람 (분기 없이 동일 코드 경로)

### 검출 boolean
- **D-05: `result.is_detected` (yolo_detector 가 conf_thres + target_classes 적용 후 결정)**
  - 이미 `_process_detection_for_camera` 가 사용 중인 boolean (line 232: `if not result.is_detected:`)
  - Phase 2 의 변경: `if not result.is_detected` 분기 → buffer push + N 연속 검사 분기
  - boolean 의 의미는 동일 (conf >= conf_thres + 라벨 매칭)

### 휘발성
- **D-06: buffer 는 프로세스 재시작 시 자동 reset (휘발 메모리)**
  - 1프로세스 PoC 적합. v1.1 다중 프로세스 진입 시 영속화 검토.
  - 재시작 후 첫 N cycle 동안은 알람 없음 (= 5분 지연). 운영상 허용.

### Cooldown 과의 상호작용
- **D-07: cooldown 만료 검사 → buffer push + 검사 → 알람 발사 + buffer.clear() + cooldown 갱신**
  - 순서:
    1. cooldown 활성 (`now - last_ts < cooldown_min * 60`) → `[skip_cooldown]` (현재 동작 유지)
    2. detector.detect() 실행 → `result.is_detected`
    3. `buffer.append(result.is_detected)` (cooldown skip 시에는 push 안 함 — 검사 안 했으므로)
    4. `if not result.is_detected: return [no_detect]` (현재 동작 유지)
    5. `if len(buffer) < frames_required or not all(list(buffer)[-N:]): return [no_alert_yet] f={len(buffer)}/{N}`
    6. 모두 충족 → 기존 알람 발사 코드 + `buffer.clear()` + cooldown 갱신

### Phase 1 의 D-19 fallback 과 결합
- **D-08: Phase 2 의 frames_required = 5 가 fire 의 D-19 fallback (conf 0.10) 의 운영급
  의미를 *완성*함**
  - Phase 1 단독: fire conf 0.10 = false positive 위험. 의미는 "검출 가능성"
  - Phase 1+2 결합: 5 frames × 0.10 conf 연속 = "5분 연속 fire 가시" → 의미는 "지속 화재 가능성"
  - statistical 의미: P(false positive 5 연속) ≈ P(false positive 1회)^5 (독립 가정 약하지만)
    → 단일 conf 0.5 임계보다 더 robust 한 시그널
  - ROADMAP Phase 2 SC #3 ("fire/helmet conf 0.5 + helmet target_classes ['head'] 복원
    검증") 의 fire 부분은 **D-19 fallback 으로 대체** (이미 Phase 1 에서 lock).
    실제 검증 항목 = "frames_required 결합 시 false positive 흡수 입증".

### Claude's Discretion
- buffer maxlen — `max(cfg["frames_required"] for cfg in DETECTOR_CONFIGS.values())` 동적
  계산 vs 상수 5 — 동적 권장 (향후 N 변경 자동 반영)
- N 연속 검사의 slicing — `list(buffer)[-N:]` vs `itertools.islice(buffer, len(buffer)-N, len(buffer))` — 가독성 우선 list slicing
- forklift/person 의 N=1 이라도 buffer push 여부 — push (코드 단순화), N=1 검사 즉시 통과
- log 메시지 포맷 — `[no_alert_yet] camera_id=X event=Y frames={n}/{N}` 신규 로그 레벨

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 2 직접 입력
- `.planning/REQUIREMENTS.md` §2 모델 트랙 단계 1 (MODEL-01·02·03) — 본 phase 의 요구
- `.planning/ROADMAP.md` Phase 2 섹션 — Goal · 3 Success Criteria · Depends on
- `.planning/phases/01-vision-demo-videos/01-CONTEXT.md` (D-04, D-19, D-22) — Phase 1 의
  fire/helmet 임계 결정. Phase 2 의 D-08 (frames_required + D-19 결합) 의 input
- `.planning/phases/01-vision-demo-videos/01-SUMMARY.md` — Phase 1 검증 결과 (event_id
  22-25 baseline)
- `C:\Users\ANNA\.claude\plans\iridescent-percolating-fox.md` §B "모델 트랙 단계 1
  — 프레임 연속 룰 (W1~W2)" — 본 phase 의 원천 컨텍스트

### 코드/스키마 출처
- `ai_agent/scheduler.py` — `_detection_cooldown` 패턴 (line 45), `_process_detection_for_camera`
  (line 202-264), `run_detection_cycle` (line 267-)
- `ai_agent/detector_configs.py` — `DETECTOR_CONFIGS` 4 항목 (fire/helmet/forklift/person)
- `ai_agent/yolo_detector.py` — `GenericYoloDetector.detect()` 의 `DetectionResult.is_detected`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ai_agent/scheduler.py:42-45`**: `_fall_cooldown`, `_detection_cooldown` 의 module-level
  dict 패턴. D-01 의 `_detection_buffer` 동일 패턴 추가.
- **`ai_agent/scheduler.py:202-264`** `_process_detection_for_camera`: cooldown skip →
  snapshot → detect → 알람 발사 흐름. Phase 2 의 변경은 detect 결과 → buffer push
  → N 연속 검사 분기 추가만.
- **`ai_agent/scheduler.py:213-218`**: cooldown 검사 + skip 패턴. Phase 2 의 D-07 흐름이
  이 위에 buffer push 추가.
- **`ai_agent/yolo_detector.py`** `DetectionResult.is_detected`: D-05 의 boolean 입력원.

### Established Patterns
- **모듈 상태 dict 패턴** (scheduler.py:42-45): tuple key, 단순값 dict. Phase 2 의
  buffer 도 동일 (단순값이 deque 인 점만 다름).
- **`[DETECT]`/`[no_detect]`/`[skip_cooldown]`/`[ERR]` log prefix**: scheduler 의 메시지
  컨벤션. 신규 `[no_alert_yet]` 동일 패턴.
- **cooldown_min `* 60` (분 → 초)**: 시간 비교 패턴. Phase 2 의 frame buffer 는 시간
  대신 frame 카운트 → `* 60` 불필요.

### Integration Points
- **DETECTOR_CONFIGS 의 frames_required 추가** — `ai_agent/detector_configs.py` 에서
  Phase 2 가 직접 수정. Phase 1 에서 conf_thres / target_classes 갱신한 같은 dict.
- **scheduler ↔ detector_configs**: `_process_detection_for_camera` 가 `cfg` 인자로
  detector config 받음. `cfg["frames_required"]` 로 N 추출.
- **테스트 위치**: `ai_agent/tests/` 디렉터리 — 기존에 ai_agent 단위 테스트 위치 미상.
  Phase 2 가 `ai_agent/tests/test_scheduler_buffer.py` 새로 만들거나, 프로젝트 루트
  `tests/` 사용 검토 (planner 가 결정).

</code_context>

<specifics>
## Specific Ideas

- **N=1 detector 의 동일 코드 경로** (D-04): forklift/person 도 buffer push + 마지막 1
  검사 = `buffer[-1] == True` → 즉시 알람. 분기 없이 단일 흐름.
- **알람 후 reset + cooldown 동시 적용** (D-02 + D-07): "5분 + 10분 = 15분 간격" 이
  아니라 "max(5, 10) = 10분 간격". cooldown 이 dominant.
- **Phase 1 의 D-19 fallback 의 진짜 의미는 Phase 2 에서 완성** (D-08): fire 의 conf
  0.10 + 5 frames 연속 = 5분 지속 fire 시그널. 단일 frame conf 0.5 임계보다 더 robust.
  v1.1 fine-tune 으로 0.5+ 도달하더라도 frames_required 룰 자체는 유지 — false
  positive 흡수 효과는 가중치 품질과 직교.
- **Phase 1 SUMMARY 의 event_id 22-25 baseline**: Phase 2 적용 후 단일 cycle 알람이
  아닌 5 cycle 누적 알람으로 변경됨. 검증 시 비교 baseline.

</specifics>

<deferred>
## Deferred Ideas

- **frames_required 의 시간 기반 윈도우** ("5 frames within 10 minutes" vs "5 consecutive"):
  v1.0 은 순수 카운트 (1분 cycle 가정 시 둘 동등). v1.1 cycle 간격 변경 시 재검토.
- **buffer 영속화 (재시작 후 복원)**: detection_buffer 테이블 + DB persistence. v1.0
  PoC 휘발성 OK. v1.1 다중 프로세스 / 게이트웨이 진입 시 검토.
- **detector 별 cooldown 차등**: 현재 모든 detector 가 `DETECTORS_COOLDOWN_MIN` (10분)
  공유. fire DANGER 는 짧게 / forklift WARNING 은 길게 등 차등. v1.1.
- **frames_required 동적 조정** (e.g., 야간엔 N=3, 주간엔 N=5): 환경 기반 적응. v1.x.
- **frame buffer 시각화 UI** (관리자가 실시간 buffer 상태 확인): v1.1 Android 대시보드.
- **end-to-end 영상 입력 검증** (--once-detect 5회 반복 후 알람 발사 횟수 측정): Phase
  5 EVAL-02 와 결합. v1.0 Phase 2 단독 검증은 unit test 로 한정.
- **`--once-detect` 의 cycle 강제 진행** (cooldown / N=5 무시 옵션): 데모 시연용
  강제 알람. v1.x 데모 도구.

</deferred>

---

*Phase: 02-vision-frames-required*
*Context gathered: 2026-05-06*
