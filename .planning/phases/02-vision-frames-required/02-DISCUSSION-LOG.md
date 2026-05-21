# Phase 2: 비전 — frames 연속 룰 - Discussion Log

> **Audit trail only.** Decisions captured in CONTEXT.md.

**Date:** 2026-05-06
**Phase:** 02-vision-frames-required
**Areas discussed:** Buffer 위치/자료구조, 알람 후 buffer 처리, 검증 방법

---

## Buffer — 자료구조 + 위치

| Option | Description | Selected |
|--------|-------------|----------|
| scheduler 모듈 상태 dict | scheduler.py 에 `_detection_buffer: dict[(camera_id, event_key), deque[bool]]` 추가. 기존 `_detection_cooldown` 와 동일 패턴. maxlen = max(frames_required) = 5. 1프로세스 PoC 적합. | ✓ |
| GenericYoloDetector 인스턴스 attribute | detector 안에 self._buffer = deque. detector 별 캡슐화. 단 같은 detector 가 여러 camera 처리 시 키 분리 필요 → dict 회귀. | |
| DB row 적재 (raw_events 패턴) | detection_buffer 테이블 신규. 영속화 + 디버깅 용이. 1프로세스 PoC 에 과한 오버헤드. | |

**User's choice**: scheduler 모듈 상태 dict
**Notes**: 기존 `_detection_cooldown` 패턴 그대로 미러. PoC 휘발성 OK.

---

## Post-alert — 알람 발사 후 buffer 처리

| Option | Description | Selected |
|--------|-------------|----------|
| reset (clear 후 다시 N cycle 누적) | 알람 후 `buffer.clear()`. 다음 알람 = N cycle 누적 + cooldown 만료. false positive 흡수 + 알람 폭증 방지. | ✓ |
| sliding (deque 그대로, cooldown 만 빈도 조절) | buffer 안 비우고 cooldown 만 의존. 매 cycle 마지막 N 검사. cooldown 만료 직후 즉시 알람 가능 → 더 자주. | |
| 마지막 N-1 만 유지 (sliding-shorter) | popleft() 한 번 → 다음 cycle 1개만 더 들어와도 즉시 알람. 거의 sliding 동등. | |

**User's choice**: reset
**Notes**: cooldown 10분 + N cycle 누적 둘 다 충족해야 알람 → 알람 간격 = max(N분, cooldown_min). 운영 안정성 우선.

---

## Verify — 검증 방법

| Option | Description | Selected |
|--------|-------------|----------|
| pytest unit test (mocked detector) | scheduler 의 buffer 동작을 mocked DetectionResult 시퀀스로 검증. 결정적, 빠름. cooldown 와 상호작용도 mock time. | ✓ |
| --once-detect 반복 + DB 검증 | 실제 영상 + scheduler cycle 5회 반복 후 detection_events 카운트. 영상 의존, 비결정적, 5분+. | |
| 둘 다 (unit + e2e) | unit + end-to-end 둘 다. confidence 높지만 시간 두 배. | |

**User's choice**: pytest unit test
**Notes**: end-to-end 검증은 Phase 5 EVAL-02 에서 본격. Phase 2 의 검증 범위는 룰 동작 자체.

---

## Claude's Discretion (CONTEXT.md 에 명시됨)

- buffer maxlen 동적 vs 상수 — 동적 (`max(cfg.frames_required)`)
- N 연속 검사 slicing — `list(buffer)[-N:]` (가독성)
- forklift/person N=1 도 buffer push — push (코드 단순화)
- 신규 log prefix `[no_alert_yet]` — 기존 `[no_detect]` 와 분리

## Deferred Ideas (CONTEXT.md 에 명시됨)

- 시간 기반 윈도우 ("5 frames within 10 min" vs "consecutive") → v1.x cycle 변경 시
- buffer 영속화 → v1.1 다중 프로세스
- detector 별 cooldown 차등 → v1.1
- frames_required 동적 조정 (야간/주간) → v1.x
- frame buffer UI 시각화 → v1.1 Android
- end-to-end 영상 입력 검증 → Phase 5 EVAL
- `--once-detect` 강제 알람 옵션 → v1.x 데모 도구
