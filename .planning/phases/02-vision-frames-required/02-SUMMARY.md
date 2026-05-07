---
phase: 02-vision-frames-required
plan: 01
subsystem: ai_agent (vision detection pipeline)
tags: [phase-2, vision, frames-required, model-track, scheduler]
requires:
  - Phase 1 SUMMARY (fire D-19 fallback locked at conf=0.10, helmet at conf=0.5+target=['head'])
  - DETECTOR_CONFIGS dict 4 항목 (fire/helmet/forklift/person)
  - scheduler._process_detection_for_camera (line 202-264) 의 cooldown→capture→detect→event 흐름
provides:
  - DETECTOR_CONFIGS 의 frames_required 키 (4 detector × int)
  - scheduler._detection_buffer 모듈 dict (deque per (camera_id, event_key))
  - scheduler._process_detection_for_camera 의 N 연속 룰 (D-07 6단계 흐름)
  - run_detection_cycle 의 [no_alert_yet] log 분기
  - ai_agent/tests/ 패키지 + test_scheduler_buffer.py (8 pytest 케이스)
affects:
  - 운영 시점 fire 알람 발사 동작: 단일 cycle → 5 cycle 누적 (Phase 1 baseline event_id 22-25 와 비교)
  - 운영 시점 helmet 알람 발사 동작: 단일 cycle → 3 cycle 누적
  - forklift/person 알람: 동일 동작 유지 (N=1 즉시 알람, 분기 없는 동일 코드 경로)
tech-stack:
  added: ["collections.deque", "pytest 9.0.3 + unittest.mock + monkeypatch"]
  patterns:
    - "모듈 상태 dict (`_detection_cooldown` 와 동일 패턴)"
    - "lazy buffer 생성 + maxlen 동적 (max(5, frames_required))"
    - "log prefix 컨벤션 (`[DETECT]`/`[no_detect]`/`[detect_skip_cooldown]`/신규 `[no_alert_yet]`)"
key-files:
  created:
    - ai_agent/tests/__init__.py
    - ai_agent/tests/test_scheduler_buffer.py
    - .planning/phases/02-vision-frames-required/02-SUMMARY.md
  modified:
    - ai_agent/detector_configs.py
    - ai_agent/scheduler.py
decisions:
  - "D-01 (CONTEXT): _detection_buffer = dict[tuple[int,str], deque[bool]] — _detection_cooldown 옆 line ~53"
  - "D-02 (CONTEXT): 알람 발사 후 buffer.clear() — 다음 알람은 cooldown + 재누적 둘 다 충족 시"
  - "D-03 (CONTEXT): pytest unit test (mocked detector + bridge + capture). end-to-end 검증은 Phase 5 EVAL"
  - "D-04 (CONTEXT): frames_required = 5/3/1/1 (fire/helmet/forklift/person)"
  - "D-05 (CONTEXT): result.is_detected (boolean) 이 buffer 입력원"
  - "D-06 (CONTEXT): 휘발성 OK (재시작 시 자동 reset)"
  - "D-07 (CONTEXT): cooldown skip → push 안 함, detect 후 push + 검사 + clear (6단계)"
  - "D-08 (CONTEXT): Phase 1 D-19 fallback (fire 0.10) 의 운영급 의미가 frames_required 5 결합으로 완성됨"
metrics:
  duration_min: 8
  completed_date: "2026-05-07"
  tests_passed: "8/8"
  tasks_completed: "3/3"
---

# Phase 2 Plan 01: 비전 — frames 연속 룰 Summary

**One-liner:** scheduler 의 일반 detection 사이클에 N 프레임 연속 룰 (`_detection_buffer` deque)
을 도입하여 단일 frame conf 변동으로 인한 false positive 알람을 흡수 — fire 5 / helmet 3 /
forklift·person 1.

## What Was Built

### 1. DETECTOR_CONFIGS 의 frames_required 키 (Task 1, MODEL-01)

`ai_agent/detector_configs.py` (+9 lines, commit `e9da88b`):

| Detector | conf_thres (Phase 1 lock) | target_classes (Phase 1 lock) | frames_required (Phase 2) |
| -------- | ------------------------- | ----------------------------- | ------------------------- |
| fire     | 0.10 (D-19 fallback)      | None                          | **5**                     |
| helmet   | 0.5                       | `['head']`                    | **3**                     |
| forklift | 0.25                      | None                          | **1**                     |
| person   | 0.30                      | `['person']`                  | **1**                     |

키 배치 위치는 각 dict 의 `iou_thres` 다음 (`img_size` 위) — 룰 임계 그룹 가독성. 기존
Phase 1 lock (fire conf 0.10, helmet conf 0.5 + target_classes `['head']`) 무손상.

### 2. scheduler.py 의 _detection_buffer + N 연속 검사 (Task 2, MODEL-02)

`ai_agent/scheduler.py` (+33 / -1 lines, commit `1d77fae`):

**Imports** (line 22):
```python
from collections import deque
```

**모듈 상태** (line 47-53):
```python
_detection_buffer: dict[tuple[int, str], deque] = {}
```
- maxlen = `max(5, frames_required)` 동적 — DETECTOR_CONFIGS 의 max (현재 fire 5).
- 카메라 × event_key 조합당 최대 5 bool ≈ 5 byte → 활성 4 detector × 5 카메라 = 100 byte 상한.

**`_process_detection_for_camera` D-07 6단계 흐름** (line 202-285):
1. cooldown 활성 → `[detect_skip_cooldown]` (push 없음)
2. capture + cv2.imread
3. `result = detector.detect(img)`
4. **신규**: lazy buffer 생성 + `buffer.append(bool(result.is_detected))`
5. `if not result.is_detected: return [no_detect]`
6. **신규**: `recent = list(buffer)[-N:]`; `if len(buffer) < N or not all(recent): return [no_alert_yet] frames=k/N conf=...`
7. 알람 발사 (upload + register_ai_event) + **신규**: `buffer.clear()` + cooldown 갱신

**`run_detection_cycle` log 분기** (line ~324):
```python
if msg.startswith(("[no_detect]", "[detect_skip_cooldown]", "[no_alert_yet]")):
    log.debug(msg)
```

### 3. 단위 테스트 (Task 3, MODEL-02 검증)

`ai_agent/tests/__init__.py` + `ai_agent/tests/test_scheduler_buffer.py` (commit `daa0b4b`).

**8 pytest 케이스 (전부 PASS — 5.20s):**

| # | Test | 입력 시퀀스 | 기대 알람 횟수 | 결과 |
| - | ---- | ----------- | -------------- | ---- |
| 1 | `test_n5_consecutive_true_fires_one_alert` | fire [T,T,T,T,T] | 1 | PASS |
| 2 | `test_n5_breaks_no_alert` | fire [T,T,T,T,F] | 0 | PASS |
| 3 | `test_n5_late_consecutive_fires_alert` | fire [F,T,T,T,T,T] | 1 | PASS |
| 4 | `test_n5_post_alert_clear_then_re_accumulate` | fire [T]×6 cooldown=0 | 1 (5번째), [no_alert_yet] (6번째) | PASS |
| 5 | `test_n1_forklift_fires_immediately` | forklift [T] | 1 | PASS |
| 6 | `test_n1_no_detect_no_alert` | forklift [F] | 0 | PASS |
| 7 | `test_cooldown_active_skips_buffer_push` | cooldown 활성 | 0 + buffer 비어있음 | PASS |
| 8 | `test_n3_helmet_consecutive` | helmet [T,T,T] | 1 | PASS |

**Mocking 전략:**
- `monkeypatch.setattr(scheduler, "capture", lambda *a, **kw: None)` — RTSP 의존 제거
- `monkeypatch.setattr(scheduler.cv2, "imread", lambda _: fake_img)` — 디스크 의존 제거
- `detector.detect.side_effect = _detect` (sequence iterator, `SimpleNamespace` 반환)
- `bridge = MagicMock()` — `register_ai_event.call_count` 로 알람 횟수 카운트
- `sys.path.insert(0, ai_agent_dir)` — pytest cwd 두 가지 (`ai_agent/` 또는 프로젝트 루트) 모두 import 보장
- `.env` 의존 없음 — Settings 는 `SimpleNamespace` stub 으로 inject

## Phase 2 Success Criteria 결과

ROADMAP Phase 2 의 4 SC:

- **SC #1 (MODEL-01)** PASS — `DETECTOR_CONFIGS` 4 detector 모두 `frames_required` 키 +
  값 정확 (fire=5, helmet=3, forklift=1, person=1). Evidence: `python -c "from
  detector_configs import DETECTOR_CONFIGS; assert {k:v['frames_required'] for k,v in
  DETECTOR_CONFIGS.items()} == {'fire':5,'helmet':3,'forklift':1,'person':1}"` exit 0.

- **SC #2 (MODEL-02)** PASS — scheduler 의 frame buffer + N 연속 룰 + 단일 spike 입력 시
  알람 0회 / N 연속 입력 시 정확히 1회. Evidence: 8 pytest 모두 PASS (특히 test 1, 2, 5).

- **SC #3 (MODEL-03)** PASS (no-op) — helmet conf_thres=0.5 + target_classes=['head'] +
  fire conf_thres=0.10 (D-19 fallback) 은 **이미 Phase 1 에서 lock 완료** (`01-SUMMARY.md`
  의 D-22 evidence: event_id 22-25 적재). Phase 2 는 D-08 의 의미 완성 — fire 0.10 conf
  + 5 cycle 누적 = 운영급 신호. 재진행 X.

- **SC #4 (E2E `--once-detect` 검증)** DEFERRED — Phase 5 EVAL 에서 본격. 본 Plan 의
  unit test 가 의미 동등성 입증 (D-03 — mocked detector 가 sequence 직접 주입).

## D-08: fire D-19 fallback + frames_required 5 결합 효과

| 시나리오 | Phase 1 단독 | Phase 1 + Phase 2 결합 |
| -------- | ------------ | ---------------------- |
| 단일 frame fire conf 0.10 | 즉시 알람 (false positive 위험) | `[no_alert_yet] frames=1/5` 무알람 |
| 5분 연속 fire 가시 (5 cycle × 0.10+) | 5회 알람 (폭증) | 1회 알람 (5번째 cycle) + cooldown 10분 |
| 1회 spike (조명·NMS 노이즈) | false positive 알람 | buffer 흡수 — 알람 없음 |

Phase 1 SUMMARY 의 baseline event_id 22-25 (단일 cycle 알람) 는 Phase 2 적용 후 동일
조건에서 **5 cycle 누적 후 1건만 적재**되도록 동작 변경됨. Phase 5 EVAL 의 baseline 비교에
사용 예정.

Statistical 의미 (CONTEXT D-08 인용):
> P(false positive 5 연속) ≈ P(false positive 1회)^5 (독립 가정 약하지만)
> → 단일 conf 0.5 임계보다 더 robust 한 시그널.

v1.1 의 AI-Hub fine-tune 으로 진정한 conf 0.5+ 도달하더라도 frames_required 룰 자체는
유지 — false positive 흡수 효과는 가중치 품질과 직교.

## Threat Model 결과

CONTEXT 의 STRIDE register 3건 모두 disposition 유지:

| ID | Category | Disposition | 검증 |
| -- | -------- | ----------- | ---- |
| T-02-01 | Tampering (`_detection_buffer` 모듈 상태) | accept | 1프로세스 PoC trusted boundary 안. v1.1 다중 프로세스 검토. |
| T-02-02 | DoS (buffer 무한 성장) | mitigate | `deque(maxlen=max(5, frames_required))` 강제 — 100 byte 상한 (4 det × 5 cam × 5 bool). 코드 라인 247 검증. |
| T-02-03 | Information disclosure (`[no_alert_yet]` log) | accept | conf + frames N/N 노출. 기존 `[DETECT]` log 와 동일 trust 레벨. |

신규 위협 없음. D-08 의 fire 0.10 + frames 5 결합은 false positive 폭증 (I/D 카테고리)
을 *완화*.

## Deviations from Plan

**None — plan 코드를 거의 그대로 적용.**

사소한 조정 (deviation 으로 분류 X):
1. **타입 어노테이션**: `dict[tuple[int, str], deque[bool]]` → `dict[tuple[int, str], deque]`.
   `deque[bool]` 은 Python 3.9+ generic syntax 가 `from __future__ import annotations`
   덕에 문자열 평가되지만, 단순 `deque` 가 더 안전 (런타임 evalution path 에서 issue 회피).
   기능 동등.
2. **`__init__.py`** 빈 파일이 아닌 한 줄 docstring (`"""ai_agent unit tests."""`) — PLAN 이 둘
   다 OK 라고 명시.

CLAUDE.md 위반 없음. Phase 1 lock (fire conf 0.10, helmet conf 0.5 + target_classes
['head']) 무손상.

## Files Touched

| Action | Path | Lines | Commit |
| ------ | ---- | ----- | ------ |
| Modified | `ai_agent/detector_configs.py` | +9 | `e9da88b` |
| Modified | `ai_agent/scheduler.py` | +33 / -1 | `1d77fae` |
| Created | `ai_agent/tests/__init__.py` | 1 | `daa0b4b` |
| Created | `ai_agent/tests/test_scheduler_buffer.py` | 240 | `daa0b4b` |

## Next Phase 진입 가능성

**Phase 3 (FUSION-01·02)** — 진입 가능. Phase 2 의 `_detection_buffer` deque 패턴 +
`frames_required` 룰은 Phase 3 의 IoU/공간 매칭 fusion 에도 동일 재사용 예정 (ROADMAP
Phase 3 SC #3). 본 Plan 이 인프라 토대를 마련.

**Phase 5 (EVAL-02)** 진입 시 baseline event_id 22-25 (Phase 1) → Phase 2 적용 후 5 cycle
누적 알람 동작 차이를 정량 평가. 본 Plan 의 unit test 가 의미 동등성 입증.

## Self-Check: PASSED

- [x] `ai_agent/detector_configs.py` 존재 + frames_required 4개 키 정확 (verify command exit 0)
- [x] `ai_agent/scheduler.py` 존재 + syntax OK + `_detection_buffer` import 가능
- [x] `ai_agent/tests/__init__.py` 존재
- [x] `ai_agent/tests/test_scheduler_buffer.py` 존재 + 8 tests 모두 PASS
- [x] commit `e9da88b` (Task 1) 존재 — `git log` 확인
- [x] commit `1d77fae` (Task 2) 존재 — `git log` 확인
- [x] commit `daa0b4b` (Task 3) 존재 — `git log` 확인
- [x] Phase 1 conf lock 무손상 (fire 0.10, helmet 0.5 + ['head']) — grep 확인
- [x] git diff --diff-filter=D HEAD~3 HEAD 빈 출력 (deletion 없음)

---

*Phase: 02-vision-frames-required*
*Completed: 2026-05-07*
*Executor: Claude Opus 4.7 (1M context)*
