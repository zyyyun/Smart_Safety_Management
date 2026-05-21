---
phase: 04-watch-j2208a-pipeline
plan: 02
subsystem: j2208a (pure logic package)
tags: [phase-4, watch, j2208a, validate, aggregate, state-machine, derive, supabase-writer, WATCH-02, WATCH-03, WATCH-04, WATCH-05]
status: complete
backfilled: 2026-05-12
backfill_note: "SUMMARY.md 작성이 누락된 상태로 04-03·04 가 후속 진행됐다. 본 파일은 git log + 현재 pytest 결과 (39 tests pass) 를 기반으로 backfill."
requires:
  - 04-01-SUMMARY (devices ALTER + 4 신규 테이블 컬럼 계약)
  - scripts/j2208a_sensor_reader.py (S1 Decode 원본 — parse_packet)
  - 04-CONTEXT.md D-04~D-17 (모든 룰 + 임계값)
provides:
  - j2208a/__init__.py (Python 패키지 marker)
  - j2208a/decode.py (parse_packet + CRC + cmd builders — sensor_reader 에서 이전)
  - j2208a/validate.py (validate_sample — per-field quality ∈ {GOOD/WARMUP/NOISY/INVALID})
  - j2208a/aggregate.py (aggregate_minute — 5s HR median / 30s temp median+IQR / 1min steps delta / good_ratio<0.30 NULL)
  - j2208a/state_machine.py (StateMachine + classify_state — 5 wear-state + 5초 sliding window 다수결)
  - j2208a/derive.py (derive_alerts — TACHY/REMOVED/COMMS_LOST 전이 추적, WORN 60s 게이트, pre-receipt grace)
  - j2208a/supabase_writer.py (insert_* + call_watch_alert_edge_function + D-14 0x28 silent drop)
  - j2208a/tests/ 5 test 파일 (validate / aggregate / state_machine / derive / supabase_writer)
affects:
  - scripts/j2208a_sensor_reader.py 의 S1 코드는 04-03 에서 import 로 전환 예정
  - 04-03 가 import 할 6개 함수 시그니처 = 본 plan 의 계약
  - 04-04 24h 운용에서 D-14 silent drop + D-17 30% 임계가 적용됨
tech-stack:
  added: ["pytest 9.0.3", "unittest.mock.MagicMock (mocked supabase client)", "supabase-py (lazy import — 단위 테스트는 미설치 환경에서도 동작)"]
  patterns:
    - "dataclass + 모듈 상수 (Python 상수, v1.1 외부화 deferred)"
    - "lazy import (try/except ImportError) — SDK 미설치 환경에서도 모듈 로딩 가능"
    - "프로세스 메모리 dict 로 상태 전이 추적 (DB 조회 X)"
    - "fluent chain mocking — client.table().upsert().execute() 호출 횟수 assertion"
key-files:
  created:
    - j2208a/__init__.py
    - j2208a/decode.py
    - j2208a/validate.py
    - j2208a/aggregate.py
    - j2208a/state_machine.py
    - j2208a/derive.py
    - j2208a/supabase_writer.py
    - j2208a/tests/__init__.py
    - j2208a/tests/test_validate.py
    - j2208a/tests/test_aggregate.py
    - j2208a/tests/test_state_machine.py
    - j2208a/tests/test_derive.py
    - j2208a/tests/test_supabase_writer.py
  modified:
    - .gitignore (j2208a 관련 임시 파일 제외)
decisions:
  - "D-14 (CONTEXT 확정 + 코드 반영): cmd=0x28 (CMD_HRV_BLOOD_PRESSURE) 은 raw_events 적재 X — silent drop. 0x09 stream 이 superset (HR+temp+steps). 실측 dual-stream 10–12Hz 가 canonical 5–6Hz 로 절반 감축. raw_events 24h 추정치 80k → 40k. SW-1·SW-2 회귀 가드 추가."
  - "D-17 (CONTEXT 확정 + 코드 반영): MIN_GOOD_RATIO = 0.30 (옛 0.50 아님). 20s 마다 cmd_health_measure(2,True) 재시작 직후 1-2 sample HR=0 PPG 재 락온 → 1분당 GOOD 비율 3-5% 깎임. 0.50 임계는 정상 운용 중에도 결측 표기 위험. AG-5·AG-6·AG-7·AG-8 boundary 가드 4건 추가."
  - "Pre-receipt grace (D-09 보정): derive_alerts 의 last_raw_ts=None 인 cold-start 첫 호출에서 COMMS_LOST AlertEvent 미발사. 첫 BLE notify 도착 전 phantom WARNING → FCM 푸시 폭주 false-positive 차단. D-5b 회귀 가드 추가."
  - "D-15 confirmed (변경 없음): same-value dedup 정책 = 1초 내 동일 raw 만 차단 (04-01 의 UNIQUE 그대로)."
  - "D-16 confirmed (변경 없음): trend-based NOISY 룰 추가 X — wear-state state machine 의 누적 강하 분류가 catch."
commits:
  - "e3a559c — Phase 4 / WATCH-02..05 (1/2): j2208a/ 패키지 모듈 — S2 Validate + S3 Aggregate + state machine + S4 Derive + supabase_writer (+843 lines, 8 modules)"
  - "2e28532 — test(04-02): j2208a/ 패키지 31 unit tests — D-07 quality + D-08/D-17 aggregate + D-05/D-06 state machine + D-09/D-10 derive + D-14 silent drop (+490 lines)"
  - "aabd4e3 — docs(04): mark Phase 4 Wave 1 complete (STATE.md)"
---

# Phase 4 / Plan 02: j2208a 패키지 (순수 로직 + 단위 테스트) - SUMMARY

**Status:** ✓ COMPLETE (2026-05-04 / SUMMARY backfilled 2026-05-12)
**Outcome:** PASS — WATCH-02·03·04·05 (로직 절반) 충족. 단위 테스트 31건 PASS (현재는 04-03 의 runtime 통합 테스트 추가로 j2208a/tests/ 누적 **39 tests pass**, 2026-05-12 재확인)

---

## What was built

### 8 module files (843 lines)
- **`j2208a/__init__.py`** — 패키지 marker + docstring
- **`j2208a/decode.py`** (205 lines) — S1 Decode (`parse_packet`, `_crc`, `build_cmd`, `cmd_*` builders, `_u8`/`_bcd2int`/`_le_int`) — `scripts/j2208a_sensor_reader.py` 에서 이전. UUID / CMD 상수 (`CMD_HEALTH_MEASURE = 0x09`, `CMD_HRV_BLOOD_PRESSURE = 0x28`) 포함.
- **`j2208a/validate.py`** (78 lines) — D-07 quality 룰:
  - `HR_INVALID_MIN = 30`, `HR_INVALID_MAX = 220`
  - `TEMP_INVALID_MIN = 25.0`, `TEMP_INVALID_MAX = 43.0`
  - `DTEMP_NOISY_PER_SEC = 1.5`
  - `validate_sample(sample, prev) -> dict[field, quality]`
- **`j2208a/aggregate.py`** (85 lines) — D-08 + **D-17 보정**:
  - `MIN_GOOD_RATIO = 0.30` (옛 0.50 → 0.30)
  - `MinuteResult` dataclass (hr_median / temp_median / temp_iqr / steps_delta / good_ratio / dominant_state)
  - `aggregate_minute(samples, dominant_state)` 순수 함수
- **`j2208a/state_machine.py`** (85 lines) — D-05·D-06:
  - `T_OFF = 33.5`, `T_WARM = 35.5`, `N1/N2 = 30.0`, `SLIDING_WINDOW_SEC = 5.0`
  - `STATES = ("OFF","WARMUP","TRANSIENT","WORN","ABNORMAL")`
  - `StateMachine.update(ts, hr, temp)` + `classify_state(samples)` 다수결 (동률 → TRANSIENT)
- **`j2208a/derive.py`** (164 lines) — D-09·D-10 + **pre-receipt grace**:
  - `TEST_USER_AGE = 30`, `WORN_GATE_SEC = 60.0`, `OFF_REMOVED_SEC = 300.0`, `COMMS_LOST_SEC = 120.0`, `TACHY_KARVONEN = 0.85`
  - `Severity` enum (NORMAL/CAUTION/WARNING/DANGER)
  - `DeriveContext` (last_severity dict + worn_since_ts + off_since_ts)
  - `derive_alerts(ctx, now_ts, wear_state, hr_60s_median, last_raw_ts)` → 전이 시점 1회만
  - **last_raw_ts=None 시 COMMS_LOST 미발사** (cold-start grace, phantom WARNING 차단)
- **`j2208a/supabase_writer.py`** (214 lines) — D-11·D-12·**D-14**:
  - Lazy `from supabase import create_client` (ImportError 흡수)
  - `get_client()` 캐시 + service_role 인증
  - `insert_raw_event(client, device_id, ts_iso, cmd, raw_hex, parsed)` — **`cmd == 0x28` silent drop** (D-14, return early)
  - `insert_minute_summary`, `insert_wear_state_event`, `insert_safety_alert`, `update_safety_alert_resolved` (4 함수)
  - `call_watch_alert_edge_function(user_id, alert_type, severity, alert_id, title, body)` — urllib POST → `/functions/v1/notifications` (action=watch-alert)

### 5 test files (490 lines, 31 unit tests)
| File | Tests | What |
|---|---|---|
| `test_validate.py` | 7 | HR=0 → WARMUP / HR<30/>220 → INVALID / temp<25/>43 → INVALID / |Δtemp|>1.5/s → NOISY / 정상 → GOOD |
| `test_state_machine.py` | 5 | OFF 진입 / WORN 진입 / WARMUP / 5s sliding 다수결 / TRANSIENT (동률) |
| `test_aggregate.py` | 8 | HR 5s median / good_ratio<0.30 NULL / steps delta / temp 30s median+IQR / **D-17 boundary 0.29·0.31 / restart pause / 옛 0.50 회귀 가드** |
| `test_derive.py` | 8 | Karvonen TACHY 진입·미진입 / REMOVED 진입·재진입 방지 / COMMS_LOST stale / **D-5b pre-receipt grace 회귀 가드** / WORN 60s 게이트 (TACHY 한정) / 해소 알림 |
| `test_supabase_writer.py` | 3 | **D-14 0x28 silent drop 회귀 가드** / **D-14 0x09 keep 대조군** / lazy import |

**현재 pytest 결과 (2026-05-12 재확인):** `python -m pytest j2208a/tests/ -q` → **39 passed in 1.58s** (31 original + 8 integration tests from 04-03 의 `test_runtime_integration.py`).

---

## Acceptance criteria results

| Plan AC | Result | Evidence |
|---|---|---|
| `j2208a/tests/test_validate.py` 7/7 PASS | ✓ PASS | 7 tests collected, all passed |
| `j2208a/tests/test_state_machine.py` 5/5 + `test_aggregate.py` 8/8 PASS | ✓ PASS | 13/13 passed |
| `j2208a/tests/test_derive.py` 8/8 + `test_supabase_writer.py` 3/3 PASS | ✓ PASS | 11/11 passed (D-5b + SW-1/SW-2 회귀 가드 포함) |
| `from j2208a import ...` 6 함수 export | ✓ PASS | get_client / insert_raw_event / insert_minute_summary / insert_wear_state_event / insert_safety_alert / update_safety_alert_resolved / call_watch_alert_edge_function 모두 export |
| **D-14 핵심**: `grep "if cmd == 0x28\|cmd == CMD_HRV_BLOOD_PRESSURE" supabase_writer.py` ≥ 1 | ✓ PASS | `if cmd == CMD_HRV_BLOOD_PRESSURE: return` 1건 |
| **D-14 sentinel**: `CMD_HRV_BLOOD_PRESSURE = 0x28` | ✓ PASS | 모듈 상수 1건 |
| **D-17 30% 임계**: `MIN_GOOD_RATIO = 0.30` | ✓ PASS | aggregate.py 모듈 상수 |
| **D-17 회귀 가드 (no stale 0.50)**: `grep -v '^#' aggregate.py \| grep "0.5[^0-9]"` = 0 | ✓ PASS | 0 lines |
| **Pre-receipt grace 회귀**: `pytest -k pre_receipt` exit 0 | ✓ PASS | `test_d5b_pre_receipt_grace_no_comms_lost` PASS |
| 5 wear-state 모두 STATES 에 등장 | ✓ PASS | OFF·WARMUP·TRANSIENT·WORN·ABNORMAL |

---

## Truths check (per frontmatter)

- ✓ S2 Validate 가 6 경계 케이스에서 기대값 (GOOD/WARMUP/NOISY/INVALID) 반환
- ✓ S3 Aggregate 가 5s HR median / 30s temp median+IQR / 1min steps delta / good_ratio<0.30 NULL 처리 (D-17)
- ✓ Wear-state state machine 5 상태 + 5초 sliding window 다수결 구현
- ✓ S4 Derive WORN ≥ 60s 후 평가 + 빈맥/탈착/통신두절 3종 + 전이 시점 1회만
- ✓ supabase_writer 가 4 테이블 insert + watch-alert Edge Function 호출 export
- ✓ cmd=0x28 sample 적재 skip — silent drop (SW-1 테스트로 회귀 가드)
- ✓ 모든 unit test 가 mocked supabase client — DB 연결 불필요

---

## Self-Check

- Verifications run: ✓ all acceptance_criteria + Truths + 현재 시점 pytest 재실행 (39 pass)
- Outcome: PASSED — WATCH-02·03·04 + WATCH-05 (로직 절반) 충족. 04-03 (BLE wiring + Edge Function deploy) 진입 가능 — 실제로 완료됨.
