---
phase: 04-watch-j2208a-pipeline
plan: 01
subsystem: supabase (watch pipeline schema)
tags: [phase-4, watch, j2208a, supabase, migration, WATCH-01]
status: complete
backfilled: 2026-05-12
backfill_note: "SUMMARY.md 작성이 누락된 상태로 04-02·03·04 가 후속 진행됐다. 본 파일은 git log + STATE.md 의 decision trail 을 기반으로 backfill — 코드/SQL 재실행 없이 기존 산출물 문서화."
requires:
  - 002_tables.sql (devices 기존 스키마)
  - 001_extensions.sql (pg_cron 활성화)
  - 003_rls_policies.sql (RLS 시스템 테이블 패턴 reference)
  - 04-CONTEXT.md D-01·D-02·D-03 (스키마 결정)
provides:
  - public.devices 의 mac_address / firmware_version / last_comm_at 3 컬럼
  - public.raw_events (7일 TTL + 1초 dedup UNIQUE generated columns)
  - public.wear_state_events (영구, 5 wear-state CHECK)
  - public.minute_summary (1인 × 1분 × 1행, PK = device_id × minute_ts)
  - public.safety_alerts (영구, alert_type/severity CHECK)
  - pg_cron job cleanup_raw_events_hourly (1시간 주기, 7일 TTL)
  - testuser1 J2208A 디바이스 시드 (MAC 21:02:02:06:01:69, FW 0.6.3.9)
affects:
  - 04-02 가 mock 할 컬럼·타입 계약 확정
  - 04-03 의 service_role insert 가 의존할 테이블·UNIQUE 제약 확정
  - 04-04 24h 검증 SQL 가 실행될 스키마 확정
tech-stack:
  added: ["pg_cron schedule", "Postgres GENERATED ALWAYS AS STORED 컬럼", "RLS ENABLE without policies (service_role-only)"]
  patterns:
    - "ON CONFLICT DO NOTHING dedup (generated columns + UNIQUE 조합)"
    - "ALTER TABLE IF NOT EXISTS + ADD COLUMN IF NOT EXISTS (idempotent migration)"
    - "INSERT ... ON CONFLICT (serial_number) DO UPDATE (seed 재진입 안전)"
    - "cron.unschedule (in DO block, ignore exception) → cron.schedule (재실행 안전)"
key-files:
  created:
    - supabase/migrations/010_watch_pipeline.sql
  modified:
    - .planning/STATE.md (decision log — 마이그레이션 적용 완료 표시)
decisions:
  - "D-06 (실행 중 발견): generation expression must be IMMUTABLE — `date_trunc('second', ts)` 는 ts 가 TIMESTAMPTZ 인 경우 timezone-dependent. `ts AT TIME ZONE 'UTC'` 로 캐스트 후 date_trunc → STABLE 충족. 첫 push 가 SQLSTATE 42P17 로 실패 → fix 후 재push 성공 (커밋 a5dec5f)."
commits:
  - "8a67962 — Phase 4 / WATCH-01: 010_watch_pipeline.sql — devices ALTER + 4 신규 테이블 + RLS + pg_cron + testuser1 seed (+155 lines)"
  - "a5dec5f — fix(010): generation expression must be IMMUTABLE — UTC cast (+5/-2 lines)"
  - "dcf52e4 — docs(04): mark 010 migration applied to remote DB (STATE.md)"
---

# Phase 4 / Plan 01: Watch J2208A 1인 파이프라인 스키마 - SUMMARY

**Status:** ✓ COMPLETE (2026-05-04 / SUMMARY backfilled 2026-05-12)
**Outcome:** PASS — WATCH-01 충족, 운영 DB 에 적용 검증됨

---

## What was built

### Migration file
- `supabase/migrations/010_watch_pipeline.sql` — 단일 마이그레이션 파일 (155 lines + 5 fix lines):
  - **(A) ALTER devices** — `mac_address VARCHAR(17) UNIQUE`, `firmware_version VARCHAR(20)`, `last_comm_at TIMESTAMPTZ` 3 컬럼 추가 (D-01)
  - **(B) raw_events** — `BIGSERIAL` PK + `device_id FK ON DELETE CASCADE` + `ts_truncated_to_second TIMESTAMPTZ GENERATED ALWAYS AS (date_trunc('second', ts AT TIME ZONE 'UTC')) STORED` + `raw_hash TEXT GENERATED ALWAYS AS (md5(raw_hex)) STORED` + `UNIQUE (device_id, ts_truncated_to_second, raw_hash)` (D-02)
  - **(C) wear_state_events** — from_state/to_state CHECK = 5 wear-state 값 (OFF/WARMUP/TRANSIENT/WORN/ABNORMAL) (D-01)
  - **(D) minute_summary** — PK = `(device_id, minute_ts)` 로 "1인 × 1분 × 1행" enforce (D-01·D-08)
  - **(E) safety_alerts** — `alert_type CHECK IN ('TACHY','REMOVED','COMMS_LOST')` + `severity CHECK IN ('CAUTION','WARNING','DANGER')` (D-01·D-09)
  - **(F) RLS ENABLE** — 4 신규 테이블 모두 (정책 미등록 → service_role 전용, D-03)
  - **(G) pg_cron cleanup job** — `cleanup_raw_events_hourly`, `0 * * * *`, `DELETE FROM raw_events WHERE ts < now() - INTERVAL '7 days'`. 안전 패턴 (DO block 으로 unschedule + 재schedule) (D-02)
  - **(H) testuser1 시드** — serial_number = `J2208A-21:02:02:06:01:69`, mac_address `21:02:02:06:01:69`, firmware_version `0.6.3.9`, user_id `testuser1`, ON CONFLICT DO UPDATE

### Migration applied to remote DB
- `supabase db push` 첫 시도 → `SQLSTATE 42P17 generation expression is not immutable` 실패 → `ts AT TIME ZONE 'UTC'` 캐스트로 fix → 재push 성공.
- migration_repair 로 001~008 도 applied 마킹 (이전 SQL/Dashboard 적용분 반영).
- supabase-py (service_role) 검증: devices ALTER 적용, 4 신규 테이블 생성 + RLS enabled, testuser1 device seed 적재 (device_id=1).

---

## Acceptance criteria results

| Plan AC | Result | Evidence |
|---|---|---|
| ALTER devices + 3 컬럼 추가 | ✓ PASS | `\d+ devices` 출력 = mac_address VARCHAR(17) UNIQUE, firmware_version VARCHAR(20), last_comm_at TIMESTAMPTZ |
| raw_events 4 generated columns + UNIQUE | ✓ PASS | UTC cast 적용 후 STORED 가능, UNIQUE 제약 활성 |
| wear_state_events / minute_summary / safety_alerts 3 신규 테이블 | ✓ PASS | 4 테이블 모두 \dt+ 결과에 등장 |
| RLS ENABLE 4 테이블 모두 | ✓ PASS | `SELECT relname, relrowsecurity FROM pg_class WHERE relname IN (...)` = true × 4 |
| pg_cron cleanup_raw_events_hourly 등록 | ✓ PASS | `SELECT jobname FROM cron.job WHERE jobname='cleanup_raw_events_hourly'` = 1 row |
| testuser1 디바이스 시드 적재 | ✓ PASS | `SELECT device_id FROM devices WHERE serial_number='J2208A-21:02:02:06:01:69'` = device_id 1 (MAC 21:02:02:06:01:69, FW 0.6.3.9) |

---

## Truths check (per frontmatter)

- ✓ `010_watch_pipeline.sql` 적용 → devices 에 3 컬럼 추가됨
- ✓ raw_events / wear_state_events / minute_summary / safety_alerts 4 테이블 생성됨
- ✓ raw_events `UNIQUE (device_id, ts_truncated_to_second, raw_hash)` 활성 → ON CONFLICT DO NOTHING dedup 가능
- ✓ pg_cron 1시간 주기 cleanup job 등록됨
- ✓ 4 신규 테이블 모두 RLS ENABLED, 정책 미등록 → service_role 전용
- ✓ testuser1 J2208A 디바이스 시드 1행 적재 (MAC 21:02:02:06:01:69)

---

## Self-Check

- Verifications run: ✓ all acceptance_criteria + Truths
- Outcome: PASSED — WATCH-01 충족 완료, 04-02·04-03 진입 가능 (실제로 진입 + 완료됨)
