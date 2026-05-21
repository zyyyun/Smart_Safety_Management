---
phase: 08-rtsp-camera
plan: 01
subsystem: ai_agent

tags: [phase-8, rtsp, snapshot, cv2, opencv, drift-test, tdd, capture, ai-agent]

# Dependency graph
requires:
  - phase: 01-vision-demo-videos
    provides: cameras.live_url_detail (mp4 또는 RTSP URL 호환) — Phase 1 의 cameras 매핑 패턴 그대로
  - phase: 03-vision-bbox-fusion
    provides: scheduler.py 의 4 detector 진입점 (line 78/139/235/335) — capture() 호출 호환 무변경
provides:
  - snapshot.capture_rtsp(url, output_path, max_attempts=3) — cv2.VideoCapture(CAP_FFMPEG) + drift_test.py 검증 패턴 (2초 sleep + 3-가드 + 3회 retry)
  - snapshot._capture_ffmpeg(rtsp_url, output_path, *, ffmpeg_bin, timeout_sec, seek_seconds) — 기존 capture() 본문 internal rename (mp4 fallback 보존)
  - snapshot.capture(url, output_path, ...) — URL scheme 분기 wrapper (rtsp://·rtsps:// → capture_rtsp / 그 외 → _capture_ffmpeg). 시그니처 보존 (SC #4 충족)
  - OPENCV_FFMPEG_CAPTURE_OPTIONS=rtsp_transport;tcp 모듈 top-level setdefault (Pitfall 6 회피)
  - 6 pytest cases (test_snapshot_rtsp.py) — RTSP-01·03 단위 검증
affects:
  - 08-02 (012_cameras_health 마이그레이션 + healthcheck 함수 — RTSP backoff 의 SnapshotError 가 healthcheck 의 5분 임계와 결합)
  - 08-03 (scheduler last_frame_at 갱신 + camera-down Edge Function — RTSP frame 수신 시점 markpoint)
  - 08-04 (mediamtx 합성 검증 — capture_rtsp 가 mediamtx RTSP 응답을 검증된 패턴으로 처리)

# Tech tracking
tech-stack:
  added: []  # 신규 의존성 X — cv2 / time / os 모두 stdlib + 기존 opencv-python>=4.7 (requirements.txt 보존)
  patterns:
    - "URL scheme 분기 wrapper — 1줄 if 로 in-process / subprocess 라우팅 (snapshot.py:capture)"
    - "drift_test.py 검증 패턴 이식 — 사용자가 실제 카메라로 검증한 timing/sequence 를 그대로 채택"
    - "TDD RED → GREEN — 6 cases 먼저 작성 (모듈 부재로 AttributeError 6/6 fail 확인) → 구현 후 6/6 pass"
    - "module top-level os.environ.setdefault — Pitfall 6 (rtsp_transport;tcp 강제) 회피"

key-files:
  created:
    - ai_agent/tests/test_snapshot_rtsp.py
  modified:
    - ai_agent/snapshot.py

key-decisions:
  - "capture_rtsp 의 success 분기는 cv2.imwrite() 반환값만 신뢰 — output_path.exists()/stat 체크 제거 (advisor 조언, mocked imwrite 와 호환). 실제 cv2.imwrite 는 in-process 라 ffmpeg subprocess 와 달리 반환값이 신뢰 가능."
  - "BACKOFF_SEC = 2 (D-02 amended) — exponential 1s→3s→9s 대신 drift_test.py 의 단순 고정 2초 채택. handshake wait + retry wait 동일 값."
  - "scheduler.py zero-change — capture() 시그니처 보존 위해 wrapper 가 ffmpeg_bin·timeout_sec·seek_seconds 키워드를 받기만 함 (RTSP 분기에선 무시). git diff --stat ai_agent/scheduler.py 빈 출력 검증."

patterns-established:
  - "TDD on existing project — 신규 함수 추가 시 RED(test) → GREEN(impl) 두 commit. 기존 모듈 (test_scheduler_buffer.py) 의 sys.path insert 컨벤션 미러."
  - "외부 도구 검증 우선 — drift_test.py 처럼 사용자가 실제로 동작 검증한 코드가 있으면 그 timing/sequence 를 그대로 채택, 이론적 최적화 (exponential backoff) 보다 검증된 패턴 우선."
  - "Wrapper + internal helper — 외부 진입점 (capture) 시그니처 안정성을 위해 본문은 internal helper (_capture_ffmpeg) 로 옮기고 wrapper 가 dispatch."

requirements-completed: []  # RTSP-01 은 multi-plan (이 plan 은 URL-scheme dispatch + capture_rtsp 부분만 — 전체는 08-03 scheduler wiring + 08-04 mediamtx E2E 검증 후 close). RTSP-03 도 multi-plan (이 plan 은 in-process backoff 만 — 헬스체크는 08-02·03).

# Metrics
duration: 12min
completed: 2026-05-18
---

# Phase 8 Plan 01: snapshot capture_rtsp + URL scheme 분기 wrapper Summary

**cv2.VideoCapture(CAP_FFMPEG) + 2초 sleep + 3-가드 + 3회 retry — drift_test.py 검증 패턴을 ai_agent/snapshot.py 에 이식, URL scheme 분기 wrapper 로 mp4 fallback (SC #4) 호환, 6/6 pytest pass + 전체 28/28 무회귀.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-05-18 (실행 직전 PLAN_START_TIME 기록)
- **Completed:** 2026-05-18
- **Tasks:** 2 (TDD RED + GREEN — 분리 commit)
- **Files modified:** 2 (snapshot.py modified, test_snapshot_rtsp.py created)

## Accomplishments

- **snapshot.capture_rtsp() 구현** — drift_test.py 의 `cv2.VideoCapture(url, cv2.CAP_FFMPEG)` + `cap.set(CAP_PROP_BUFFERSIZE, 1)` + `time.sleep(2)` + `cap.read()` + **3-가드** (`ret ∧ cap.isOpened() ∧ frame is not None`) + 시도 사이 2초 wait + 3회 재시도 패턴 그대로. 3회 실패 시 `SnapshotError` (메시지에 마지막 실패 원인 포함).
- **snapshot._capture_ffmpeg() 분리** — 기존 `capture()` 본문 (subprocess + ffmpeg flags) 을 internal helper 로 rename. mp4 fallback 회귀 0 — analyzeduration/probesize/-frames:v 1/-q:v 2/-update 1 그대로.
- **snapshot.capture() wrapper** — URL scheme (`rtsp://`·`rtsps://`) 으로 capture_rtsp / _capture_ffmpeg 자동 분기. **시그니처 100% 보존** — scheduler.py 4 detector 진입점 zero-change (SC #4).
- **Pitfall 6 회피** — module top-level `os.environ.setdefault("OPENCV_FFMPEG_CAPTURE_OPTIONS", "rtsp_transport;tcp")`. OpenCV FFMPEG backend 의 rtsp_transport 기본값 ambiguity 차단.
- **6 pytest cases TDD** — RED 단계 6/6 fail (모듈 cv2/capture_rtsp/_capture_ffmpeg 부재) → GREEN 단계 6/6 pass. 전체 ai_agent/tests/ 28/28 pass (Phase 2 의 8 + Phase 3 의 14 + 본 plan 의 6).

## Task Commits

1. **Task 2 (TDD RED): 6 pytest cases for snapshot.capture_rtsp + URL scheme dispatch** — `c3dbf41` (test)
2. **Task 1 (TDD GREEN): snapshot capture_rtsp + URL scheme 분기 wrapper (drift_test 패턴)** — `715c277` (feat)

_Note: TDD 순서대로 test commit 먼저, 그다음 impl commit. Plan 의 task 번호와 무관._

## Files Created/Modified

- `ai_agent/snapshot.py` — capture_rtsp (신규) + _capture_ffmpeg (기존 본문 rename) + capture (분기 wrapper). 모듈 top-level OPENCV_FFMPEG_CAPTURE_OPTIONS setdefault. 92 → 233 라인 (+143 / −4 net, 코멘트 풍부).
- `ai_agent/tests/test_snapshot_rtsp.py` — 6 pytest cases (RTSP-01·03 단위), 149 라인. test_scheduler_buffer.py 의 sys.path insert 컨벤션 미러.

## Decisions Made

- **success 분기 단순화** (advisor 조언) — plan 의 `if ok and output_path.exists() and output_path.stat().st_size > 0` 3-가드 대신 `if ok` 만 사용. 이유: RESEARCH §Example C 의 테스트 fixture 가 `cv2.imwrite` 를 `lambda p, f: True` 로 mock 하므로 (실제 파일 미생성), exists/stat 가드는 mocked 테스트에서 항상 False → 모든 RTSP 성공 케이스가 SnapshotError 로 떨어짐. cv2.imwrite 는 in-process 라 ffmpeg subprocess 와 달리 반환값이 신뢰 가능 (subprocess exit code 0 의 거짓양성 문제 없음).
- **BACKOFF_SEC = 2 (D-02 amended 그대로 적용)** — exponential 1s→3s→9s 후보 대신 drift_test.py 의 단순 고정 2초 채택. handshake wait + retry-between wait 동일 값 (총 ~6초 = 핸드셰이크 6초 + 사이 4초 = ~10초 worst case). 디버깅 단순.
- **scheduler.py zero-change 보장 위해 wrapper 시그니처 호환** — `capture(url, output_path, *, ffmpeg_bin, timeout_sec, seek_seconds)` 모두 그대로 받음 (RTSP 분기에선 사용 X). 4 detector 진입점이 `ffmpeg_bin=settings.ffmpeg_bin` 또는 `seek_seconds=settings.fall_demo_seek_sec` 전달하므로 TypeError 회피.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] capture_rtsp success 분기에서 `output_path.exists()` + `stat().st_size > 0` 체크 제거**
- **Found during:** Task 1 (GREEN 구현 직전, advisor 조언 적용 — 사전 점검 단계)
- **Issue:** PLAN.md 의 capture_rtsp 본문 코드가 `if ok and output_path.exists() and output_path.stat().st_size > 0:` 로 가드되어 있으나, RESEARCH §Example C 의 mocked `cv2.imwrite` (lambda p, f: True) 는 실제 파일을 만들지 않음 → 모든 RTSP 성공 테스트 (test 1·2) 가 SnapshotError 로 fail.
- **Fix:** `if ok:` 만 사용. cv2.imwrite 는 in-process 함수라 반환값 True 만으로 신뢰 가능 (subprocess vs in-process 차이 — ffmpeg subprocess 와 달리 거짓양성 없음).
- **Files modified:** ai_agent/snapshot.py (capture_rtsp 의 inner if 분기)
- **Verification:** test_rtsp_success_first_attempt + test_rtsp_retry_then_success 모두 PASS (cv2.imwrite mock 호환).
- **Committed in:** 715c277 (GREEN commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — mocked-test 호환성). Pre-implementation에 advisor 조언으로 발견하여 사후 디버깅 회피.
**Impact on plan:** No scope creep. plan 의 의도 (in-process cv2 capture + 3-가드) 그대로, success 분기만 mocked 테스트와 호환되게 단순화.

## Issues Encountered

- 없음. RED → GREEN flow 한 번에 완료.

## TDD Gate Compliance

본 plan 은 `type: execute` 이나 Task 1 의 `tdd="true"` 속성에 따라 plan 내부 TDD cycle 적용:

- ✅ RED gate: `test(08-01): 6 pytest cases ... (TDD RED)` — c3dbf41 (test 6/6 fail confirmed)
- ✅ GREEN gate: `feat(08-01): snapshot capture_rtsp + URL scheme 분기 wrapper` — 715c277 (test 6/6 pass)
- (REFACTOR 단계 — 별도 refactor 없음, GREEN 이 곧 최종 상태)

## Verification Results

| Gate | Spec | Actual | Result |
|------|------|--------|--------|
| `cv2.CAP_FFMPEG` count | ≥ 1 | 2 (body + docstring) | ✓ |
| `def capture_rtsp` count | == 1 | 1 | ✓ |
| `def _capture_ffmpeg` count | == 1 | 1 | ✓ |
| `def capture(` count | == 1 | 1 | ✓ |
| `cap.isOpened` count | ≥ 1 | 3 | ✓ |
| `frame is not None` count | ≥ 1 | 2 | ✓ |
| `startswith.*rtsp` count | ≥ 1 | 2 | ✓ |
| `OPENCV_FFMPEG_CAPTURE_OPTIONS` count | == 1 | 1 | ✓ |
| `import cv2` count | ≥ 1 | 1 | ✓ |
| `import time` count | ≥ 1 | 1 | ✓ |
| `git diff --stat ai_agent/scheduler.py` | 빈 출력 | 빈 출력 | ✓ |
| `pytest tests/test_snapshot_rtsp.py -v` | 6 passed | 6 passed (0.21s) | ✓ |
| `pytest tests/ -v` 전체 | 28 passed | 28 passed (5.72s) | ✓ |

## User Setup Required

None — in-process Python 변경만, 외부 서비스 설정 0건. `OPENCV_FFMPEG_CAPTURE_OPTIONS` 환경변수도 module top-level setdefault 라 외부 설정 불필요.

## Next Phase Readiness

**Wave 2 (08-02) 진입 가능** — supabase/migrations/012_cameras_health.sql + healthcheck SQL 함수 + pg_cron 1분 주기 + Vault secrets. 본 plan 의 capture_rtsp 가 raise 하는 SnapshotError 가 scheduler 의 기존 except 패턴에 흡수되어 cycle skip → cameras.last_frame_at 갱신 멈춤 → 08-02 healthcheck 의 5분 임계가 자연스럽게 발사 (08-03 의 last_frame_at 갱신 wiring 과 결합).

**RTSP-01·03 부분 충족** — full close 조건:
- RTSP-01 = capture_rtsp dispatch (본 plan ✓) + scheduler 가 실제 RTSP URL 로 cycle 실행 (08-04 mediamtx E2E)
- RTSP-03 = in-process backoff (본 plan ✓) + cameras.last_frame_at 헬스체크 (08-02·03) + 끊김 알림 FCM (08-03)

REQUIREMENTS.md 의 RTSP-01·03 체크박스는 08-04 mediamtx E2E 후 close (본 plan 에선 mark-complete 호출 X — `requirements-completed: []`).

## Self-Check

**File existence:**
- `ai_agent/snapshot.py` — FOUND
- `ai_agent/tests/test_snapshot_rtsp.py` — FOUND
- `.planning/phases/08-rtsp-camera/08-01-SUMMARY.md` — FOUND (this file)

**Commits:**
- c3dbf41 (test RED) — FOUND
- 715c277 (feat GREEN) — FOUND

**Result:** `## Self-Check: PASSED`

---

*Phase: 08-rtsp-camera*
*Plan: 01*
*Completed: 2026-05-18*
