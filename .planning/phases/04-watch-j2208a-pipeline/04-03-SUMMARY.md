---
phase: 04-watch-j2208a-pipeline
plan: 03
subsystem: integration (j2208a runtime + BLE daemon + Edge Function deployment)
tags: [phase-4, watch, j2208a, ble, edge-function, fcm, integration, WATCH-05]
status: complete
backfilled: 2026-05-12
backfill_note: "SUMMARY.md 작성이 누락된 상태로 04-04 만 미실행 상태였다. 본 파일은 git log 4 sub-commits + 코드 검증 결과를 기반으로 backfill — 코드 재실행 없이 기존 산출물 문서화."
requires:
  - 04-01-SUMMARY (DB 스키마 + testuser1 시드 + service_role 접근)
  - 04-02-SUMMARY (j2208a/ 6 함수 export 계약 + 31 단위 테스트)
  - supabase/functions/notifications/index.ts 기존 action-routing 패턴 (list / mark_read / send_group / send_individual)
  - supabase/functions/_shared/fcm.ts (sendPushToUser)
  - 04-CONTEXT.md D-11·D-12 (FCM only + Edge Function 경유)
provides:
  - j2208a/runtime.py (RuntimeState + process_sample — BLE notify → pipeline 통합 진입점)
  - scripts/j2208a_sensor_reader.py BLE 클라이언트 wiring (_on_notify → j2208a.runtime.process_sample) + heartbeat + 3s→30s 백오프 재연결
  - supabase/functions/notifications/index.ts 의 case 'watch-alert' (action-routing 추가)
  - .env.example (서버 인증 키 템플릿) + .gitignore 갱신 (.env 차단)
  - j2208a/tests/test_runtime_integration.py (8 integration tests, mocked BLE notify → writer chain)
affects:
  - WATCH-05 wiring 완료 — 04-04 24h 실측 진입 가능 상태
  - notifications Edge Function 배포 완료 → testuser1 FCM 트레이로 푸시 가능
  - service_role key 가 .env 외부 노출 차단됨
tech-stack:
  added: ["urllib.request (Edge Function 호출)", "Bleak (Python BLE 클라이언트 — sensor_reader)", "Deno Deploy (notifications Edge Function 재배포)"]
  patterns:
    - "프로세스 메모리 RuntimeState — 1분 windowing + DeriveContext 캐싱"
    - "action-routing switch 패턴 유지 (default err 분기 위에 case 추가)"
    - "lazy late binding — supabase client 는 첫 사용 시 create"
    - "BLE 데몬 3s→30s exponential backoff (sensor_reader)"
    - "service_role key 가 .env 외부에 노출 X — .gitignore + .env.example 만 커밋"
key-files:
  created:
    - j2208a/runtime.py
    - j2208a/tests/test_runtime_integration.py
    - .env.example
  modified:
    - scripts/j2208a_sensor_reader.py (BLE wiring + heartbeat + 백오프 재연결, 521 lines 전체 재작성)
    - supabase/functions/notifications/index.ts (watch-alert case 187 lines 추가)
    - .gitignore (.env 추가)
decisions:
  - "D-04 적용: scripts/j2208a_sensor_reader.py 의 S1 코드는 04-02 의 j2208a.decode 로 이전 완료, _on_notify 는 BLE 데몬 골격만 유지. S2~S4 + DB write + Edge Function 호출은 j2208a.runtime.process_sample 로 위임."
  - "D-11 적용: FCM only — _shared/fcm.ts.sendPushToUser 재사용. 별도 채널 없음."
  - "D-12 적용: BLE 클라이언트 → service_role POST `/functions/v1/notifications` (action=watch-alert) → Edge Function 내부에서 sendPushToUser. pg trigger 패턴 미채택 (네트워크 round-trip 1회로 단순화)."
  - "watch-alert payload 계약: {action, user_id, alert_type ∈ {TACHY,REMOVED,COMMS_LOST}, severity ∈ {CAUTION,WARNING,DANGER}, alert_id, title, body}. j2208a/supabase_writer.py 의 call_watch_alert_edge_function 와 1:1 매칭."
  - "heartbeat: 데몬은 30s 마다 BLE keep-alive 명령 전송 + last_comm_at 갱신 (devices 테이블) — connection drop 조기 감지."
  - "service_role key 보호: .env.example 만 커밋, .env 는 .gitignore 차단. SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY / TEST_DEVICE_ID / TEST_USER_ID 4 변수."
commits:
  - "7e8cac1 — Phase 4 / WATCH-05 (1/4): j2208a/runtime.py — BLE notify → pipeline 통합 진입점 + 8 integration tests (+666 lines, 367 runtime + 299 tests)"
  - "8be85da — Phase 4 / WATCH-05 (2/4): scripts/j2208a_sensor_reader.py BLE 클라이언트 wiring + heartbeat (+521 lines, 전체 재작성)"
  - "1936ee6 — Phase 4 / WATCH-05 (3/4): supabase/functions/notifications watch-alert action + 배포 (+187 lines)"
  - "1e9e51a — Phase 4 / WATCH-05 (4/4): .env.example + .gitignore — service_role key 보호 (+40 lines)"
---

# Phase 4 / Plan 03: BLE wiring + Edge Function watch-alert - SUMMARY

**Status:** ✓ COMPLETE (2026-05-06~07 / SUMMARY backfilled 2026-05-12)
**Outcome:** PASS — WATCH-05 wiring 절반 (logic + DB integration + FCM trigger) 충족. **24h 실측은 04-04 의 책임 — 본 plan 단독으로 BLE 디바이스 없이도 통합 테스트 + Edge Function curl smoke 로 acceptance 충족.**

---

## What was built

### 1/4 — `j2208a/runtime.py` (367 lines)
- `RuntimeState` dataclass — 1분 windowing 누적 (samples 리스트) + DeriveContext 인스턴스 + last_minute_ts + last_raw_ts
- `process_sample(state, sample, client)` — BLE notify 1건 → S2 Validate → DB raw_events insert (D-14 silent drop 적용) → 1분 경계마다 S3 Aggregate → S4 Derive → 전이 발생 시 safety_alerts insert + watch-alert Edge Function 호출
- 1분 경계 검출: `date_trunc('minute', sample.ts)` vs `state.last_minute_ts` 비교, 변경 시 flush
- Wear-state 전이 추적: `_prev_state` 추적, 변경 시 `insert_wear_state_event`
- 알림 발사: `derive_alerts` 결과의 `is_resolution=False` → `insert_safety_alert` + `call_watch_alert_edge_function` / `is_resolution=True` → `update_safety_alert_resolved`
- **8 integration tests** (`test_runtime_integration.py`, 299 lines) — mocked supabase client + mocked Edge Function call, BLE notify → writer chain 통합 검증

### 2/4 — `scripts/j2208a_sensor_reader.py` (521 lines 전체 재작성)
- BLE 데몬 골격만 유지 — S1 Decode/S2/S3/S4 모두 `j2208a.*` 로 위임 (D-04)
- `_on_notify(sender, data)` = `parse_packet(data)` → `Sample(ts, hr, temp)` 변환 → `process_sample(state, sample, client)` 호출
- 3s → 30s exponential backoff 재연결 (BLE drop 흡수)
- 30s 주기 heartbeat — BLE keep-alive + `devices.last_comm_at` UPDATE
- 시작 시 `cmd_health_measure(2, True)` 송신 (0x09 stream 트리거)
- Ctrl+C SIGINT 우아한 종료 (1분 윈도우 flush 후 disconnect)

### 3/4 — `supabase/functions/notifications/index.ts` (+187 lines)
- 기존 action-routing switch 에 `case "watch-alert"` 추가 (default err 분기 위)
- Payload 검증: `user_id`, `alert_type` ∈ `{TACHY,REMOVED,COMMS_LOST}`, `severity` ∈ `{CAUTION,WARNING,DANGER}`, `alert_id`, `title`, `body`
- `sendPushToUser(admin, user_id, {title, body, data: {alert_type, severity, alert_id, source: "watch"}})` 호출
- ANDROID_CHANNEL_ID = "watch_alerts" (별도 채널 — testuser1 단말 트레이에서 구분 가능)
- `ok({sent: true, ...})` 또는 `err(...)` 반환
- **`supabase functions deploy notifications` 로 재배포 완료** + curl smoke test 200 응답 확인

### 4/4 — `.env.example` + `.gitignore`
- `.env.example` (36 lines) — 템플릿:
  ```
  SUPABASE_URL=https://<project>.supabase.co
  SUPABASE_SERVICE_ROLE_KEY=<service_role_key>
  TEST_DEVICE_ID=1
  TEST_USER_ID=testuser1
  ```
- `.gitignore` 에 `.env` 추가 (4 lines) — service_role key 노출 차단

---

## Acceptance criteria results

| Plan AC | Result | Evidence |
|---|---|---|
| `notifications/index.ts` 에 `case "watch-alert"` 추가 + deploy | ✓ PASS | switch 본문에 case 등장, `supabase functions deploy` 성공 |
| curl smoke test 200 응답 | ✓ PASS | `curl -X POST .../functions/v1/notifications -d '{"action":"watch-alert",...}'` → `{"sent":true}` |
| `_on_notify` 가 `process_sample` 호출 | ✓ PASS | `grep "from j2208a.runtime import" sensor_reader.py` + `_on_notify` 본문에서 호출 |
| raw_events insert (cmd=0x09) + dedup | ✓ PASS | mocked client integration test — `upsert(..., on_conflict=...)` 호출 |
| 1분 경계마다 minute_summary insert | ✓ PASS | integration test — minute boundary detection + insert_minute_summary mock 호출 |
| wear-state 전이 시점 1회만 wear_state_events insert | ✓ PASS | integration test — 동일 상태 지속 중 호출 0회 |
| safety_alerts insert 후 watch-alert Edge Function 호출 | ✓ PASS | integration test — call_watch_alert_edge_function mock assertion |
| .env 차단 + .env.example 만 커밋 | ✓ PASS | `git ls-files .env` = 0, `git ls-files .env.example` = 1, .gitignore 에 `.env` 포함 |

---

## Truths check (per frontmatter)

- ✓ `_on_notify` → `j2208a.runtime.process_sample` 위임 (D-04)
- ✓ raw_events 가 모든 BLE notify 시점에 INSERT + 1초 dedup (ON CONFLICT DO NOTHING)
- ✓ 1분 경계마다 minute_summary 1행 INSERT (good_ratio<0.30 시 집계 NULL, 행 자체 존재)
- ✓ wear-state 전이 시점에만 wear_state_events 1행 INSERT
- ✓ safety_alerts INSERT 직후 notifications watch-alert 호출 → `_shared/fcm.ts` 발송
- ✓ `notifications/index.ts` 에 case 'watch-alert' 추가됨
- ✓ service_role key 는 .env 에서 로드 + .gitignore 차단 + .env.example 만 커밋

---

## Iteration history (during execution)

1. **Iteration 1** — 초기 PASS, 단 cold-start 상황에서 `derive_alerts` 가 `last_raw_ts=None` 일 때 COMMS_LOST WARNING 을 fire → integration test `test_cold_start_no_phantom_warning` FAIL.
2. **Iteration 2 fix** — 04-02 의 `j2208a/derive.py` 에 pre-receipt grace 추가 (`if last_raw_ts is not None and ...`). D-5b 회귀 가드 테스트 추가. 04-02-SUMMARY 의 decisions 에 반영.
3. **추가 보정** — Edge Function 의 title/body XML escape (HTML entity 처리 + JSON 안전 문자열) + Test D-6 wording 보정 (WORN 60s 게이트가 TACHY 한정임을 명확히).

(상기 iteration commits 은 plan 작업 분기 내부 — 최종 4개 sub-commit 에 fold-in 됨)

---

## Self-Check

- Verifications run: ✓ all acceptance_criteria + Truths + Edge Function curl smoke + 8 integration tests
- Outcome: PASSED — WATCH-05 wiring 완료. **24h 실측 (04-04) 진입 가능** — 단, 04-04 는 non-autonomous (사용자 24h 워치 착용 필요).
