---
phase: 09-tbm-worker-guide
plan: 01
subsystem: database
tags: [supabase, migration, postgres, pg-cron, rls, realtime-publication, storage, vault, security-definer, tbm, schema-push, blocking, T-9-01, T-9-02, T-9-05, T-9-07, T-9-08]
requirements: [TBM-01]

# Dependency graph
requires:
  - phase: 04-watch-j2208a-pipeline
    provides: D-09 알림 전이 원칙 1회 발사 (missed_alert_at dedup 패턴 원천)
  - phase: 07-watch-app-bridge
    provides: 011_watch_app_rls.sql RLS USING(true) v1.0 PoC + Realtime publication ADD 패턴
  - phase: 08-rtsp-camera
    provides: 012_cameras_health.sql Vault sr_key + pg_cron + SECURITY DEFINER + EXCEPTION OTHERS 흡수 패턴 1:1 미러
provides:
  - 013_tbm_schema.sql (운영 DB 적용 완료)
  - 4 신규 테이블 — tbm_sessions / tbm_templates / tbm_checklists / tbm_participants
  - RLS USING(true) v1.0 PoC 4 정책 + Realtime publication ADD TABLE 4
  - Storage bucket tbm-signatures (public=false + Option A anon INSERT path 가드)
  - 5 templates 시드 (fire/electric/height/heavy/general)
  - tbm_missed_attendance_check() SECURITY DEFINER + Vault NULL graceful skip
  - pg_cron tbm_missed_attendance_minute 1분 주기
  - scripts/seed_tbm_demo.py (group_id=1 worker 3명 시드)
  - tests/sql/test_013_tbm_isolation.sql (7 assertions PASS)
affects: [phase-9-plan-02, phase-9-plan-03, phase-9-plan-04]

# Tech tracking
tech-stack:
  added: [pg_cron job tbm_missed_attendance_minute, Storage bucket tbm-signatures]
  patterns: [Auth Admin API → handle_new_user 트리거 → UPSERT by id 시드 패턴 (Phase 9 신규)]

key-files:
  created:
    - supabase/migrations/013_tbm_schema.sql
    - scripts/seed_tbm_demo.py
    - tests/sql/test_013_tbm_isolation.sql
    - .planning/phases/09-tbm-worker-guide/09-01-SUMMARY.md
  modified: []

key-decisions:
  - "C2 (Realtime publication) — 4 테이블 모두 ADD (단순성 우선, Phase 7 011 1:1 미러)"
  - "C3 (Storage Option A) — anon INSERT WITH CHECK bucket_id + key prefix '^[0-9]+$' + .png suffix 가드"
  - "T-9-05 (Vault 미시드 DoS) accept — IF sr_key IS NULL THEN RAISE WARNING + RETURN graceful skip (Phase 8 04 검증 패턴)"
  - "auth.users + handle_new_user 트리거 → UPSERT by id 시드 패턴 (Rule 3 plan correction, Phase 7 test_user workaround 진화형)"

patterns-established:
  - "Pattern: 신규 테이블 시드 시 트리거 부재 컬럼 (NOT NULL) 처리 — auth.users Admin API + user_metadata.user_id 채널 + UPSERT by id"
  - "Pattern: psql 미설치 환경에서 회귀 가드 SQL 실행 — sed/python 로 \\echo 메타-명령 제거 후 supabase db query --linked -f"

metrics:
  duration: 약 40분
  completed: 2026-05-18T06:30:32Z
---

# Phase 9 Plan 09-01: TBM 스키마 + Storage + cron 인프라 운영 DB 적용 Summary

**One-liner:** TBM 4 신규 테이블 + RLS + Realtime publication 4 ADD + tbm-signatures Storage Option A + 5 templates 시드 + pg_cron tbm_missed_attendance_minute 운영 DB 적용 + auth.users 트리거 채널을 통한 worker 3명 시드 + 7/7 isolation 회귀 가드 PASS.

## Tasks 실행 결과

### Task 1: 013_tbm_schema.sql 작성 (커밋 `f044fac`)

신규 마이그레이션 파일 `supabase/migrations/013_tbm_schema.sql` (292 lines). 8 섹션 — (A) 4 테이블, (B) 인덱스, (C) RLS, (D) Realtime publication, (E) Storage 버킷, (F) 5 templates 시드, (G) tbm_missed_attendance_check() 함수, (H) pg_cron 등록. 모든 `<done>` 조건 충족:

- `CREATE TABLE IF NOT EXISTS public.tbm_{sessions,templates,checklists,participants}` 4개 모두
- `CONSTRAINT tbm_sessions_group_date_uq UNIQUE (group_id, session_date)` (D-02)
- `CONSTRAINT tbm_participants_session_user_uq UNIQUE (session_id, user_id)` (idempotent 체크인)
- `tbm_participants.method CHECK IN ('signature','nfc','qr','manual')` default 'signature' (D-03)
- `tbm_sessions.missed_alert_at TIMESTAMPTZ` (D-05 알림 전이 1회 dedup)
- 4 ENABLE ROW LEVEL SECURITY + 4 SELECT USING(true) v1.0 PoC (Phase 7 011 미러)
- `ALTER PUBLICATION supabase_realtime ADD TABLE` × 4 (research C2 amendment)
- Storage bucket 'tbm-signatures' public=false + allowed_mime_types=['image/png'] + file_size_limit=524288
- Option A RLS — `tbm_signatures_insert_anon` WITH CHECK (bucket_id + foldername regex + .png suffix)
- 5 templates 시드 — fire/electric/height/heavy/general 모두 ON CONFLICT (work_type) DO NOTHING
- `tbm_missed_attendance_check()` SECURITY DEFINER + SET search_path = public, extensions, net (Pitfall 7)
- Vault sr_key/base_url NULL 가드 (RAISE WARNING + RETURN graceful skip — T-9-05 mitigation)
- `expected_end_at + INTERVAL '30 minutes' < now() AND missed_alert_at IS NULL AND ended_at IS NULL` (D-05)
- `net.http_post` action='tbm-missed' (plan 09-02 신규 case 와 1:1)
- `cron.schedule('tbm_missed_attendance_minute', '* * * * *', ...)` idempotent (010/012 패턴)
- service_role JWT hardcode 0건 (`grep -v '^--' | grep -c 'eyJ' == 0` — T-9-07 mitigation)

### Task 2: supabase db push + seed_tbm_demo.py + test_013_tbm_isolation.sql (커밋 `20d2c7f`)

#### [BLOCKING] supabase db push --linked --yes — 운영 DB 적용 성공

```
$ supabase db push --linked --yes
Connecting to remote database...
Applying migration 013_tbm_schema.sql...
Finished supabase db push.
```

`supabase migration list --linked` 출력:
```
   013   | 013    | 013
```
Local + Remote + Time = 모두 013 표시. 013 운영 적용 완료.

#### PostgREST + Storage API 검증

| 검증 | 결과 |
|------|------|
| GET `/rest/v1/tbm_templates?select=work_type,title&order=work_type` | 5 rows (electric/fire/general/heavy/height) |
| GET `/rest/v1/tbm_sessions?select=session_id` | `[]` (RLS USING(true) + 데이터 0 — anon SELECT 200 빈 배열) |
| POST `/rest/v1/rpc/tbm_missed_attendance_check` | HTTP 204 (SECURITY DEFINER void 호출 OK, Vault sr_key 미시드 시 graceful skip path) |
| GET `/storage/v1/bucket/tbm-signatures` | `{"id":"tbm-signatures","public":false,"file_size_limit":524288,"allowed_mime_types":["image/png"]}` HTTP 200 |

#### scripts/seed_tbm_demo.py — group_id=1 worker 3명 시드

```
[SEED] testuser_w1     (409e1221-2bc7-4fb8-bcc9-bba7171f9fd7) -> profile UPSERT HTTP 201
[SEED] testuser_w2     (65b2f100-1e9e-4485-b71b-46f13c9fe2df) -> profile UPSERT HTTP 200
[SEED] testuser_w3     (a1e705f6-5bb1-4e6c-a0a0-147e036f4107) -> profile UPSERT HTTP 200
seed_tbm_demo: inserted=3, skipped=0, workers=3
[VERIFY] group_id=1 worker count = 3
  - testuser_w1     작업자 1      role=worker
  - testuser_w2     작업자 2      role=worker
  - testuser_w3     작업자 3      role=worker
[OK] Phase 9 PoC prerequisite 충족 (worker count=3 >= 3)
```

#### tests/sql/test_013_tbm_isolation.sql — 7/7 assertions PASS

```
$ supabase db query --linked -f build/tmp_sql/test_013_no_echo.sql --output table
┌─────────────────────────────────────────────┐
│                   result                    │
├─────────────────────────────────────────────┤
│ test_013_tbm_isolation: 7/7 assertions PASS │
└─────────────────────────────────────────────┘
```

7 assertions (Dashboard SQL Editor 또는 `supabase db query --linked -f` 둘 다 통과):

1. **RLS 4 ENABLED** — tbm_sessions / tbm_templates / tbm_checklists / tbm_participants 모두 `relrowsecurity=true`
2. **T-9-08 anon UPDATE 차단** — `SET LOCAL ROLE anon` + UPDATE tbm_sessions = 0 rows (RLS default deny)
3. **5 templates 시드** — work_type IN ('fire','electric','height','heavy','general') count=5
4. **cron tbm_missed_attendance_minute** — active=true, schedule='* * * * *'
5. **Pitfall 7 회귀 가드** — tbm_missed_attendance_check `prosecdef=true` (SECURITY DEFINER)
6. **T-9-01 PII 가드** — tbm-signatures bucket public=false
7. **C2 회귀 가드** — 4 tbm_* 테이블 모두 `supabase_realtime` publication 등록

#### SC #4 회귀 가드 — ai_agent/tests/ 28/28 PASS

```
$ cd ai_agent && python -m pytest tests/ -q
............................                                             [100%]
28 passed in 9.45s
```

013 마이그레이션은 `supabase/migrations/` + `scripts/` + `tests/sql/` 만 추가, `ai_agent/` 코드 변경 0 — SC #4 (4 detector 진입점 capture() zero-change) 보존 검증.

## Threat Mitigations

| Threat ID | Disposition | Mitigation 검증 |
|-----------|-------------|-----------------|
| T-9-01 (signature PII) | mitigate | tbm-signatures public=false (storage API 검증) + Option A path 가드 (CREATE POLICY tbm_signatures_insert_anon WITH CHECK bucket_id + foldername regex + .png) + SELECT 정책 미등록 = default deny |
| T-9-02 (UNIQUE bypass) | mitigate | DB-level `CONSTRAINT tbm_sessions_group_date_uq UNIQUE (group_id, session_date)` + tbm_participants UNIQUE (session_id, user_id) (Plan 09-02 의 23505 catch 가 409 응답) |
| T-9-05 (Vault 미시드 DoS) | accept (graceful skip) | `IF sr_key IS NULL OR base_url IS NULL THEN RAISE WARNING + RETURN` — RPC POST 204 + cron 1분마다 정상 종료. Phase 8 04 검증 패턴 일관. |
| T-9-07 (Vault git 노출) | mitigate | 마이그 본문 `eyJ` (JWT prefix) 0건 (코멘트 제외 후 grep) — vault.decrypted_secrets SELECT 만 사용 |
| T-9-08 (anon 임의 INSERT/UPDATE) | mitigate | RLS write 정책 미등록 = default deny. Test 2 (anon UPDATE tbm_sessions) 0 rows 확인. Plan 09-02 의 tbm-checkin Edge Function 만 service_role 로 INSERT. |

T-9-06 (60s 서명 URL bypass) 는 Plan 09-02 의 service_role 발급 signed URL 책임 (본 plan scope 외).

## Deviations from Plan

### Rule 3 — plan correction × 2

**1. [Rule 3 - plan correction] auth.users + handle_new_user 트리거 + UPSERT by id 시드 패턴**

- **Found during:** Task 2 (scripts/seed_tbm_demo.py 첫 실행 시)
- **Issue:** plan Task 2 NOTE (line 464-467) 는 "profiles.id 부재 NOT NULL → bcrypt placeholder 또는 schema 호환 default 처리" 또는 "SQL fallback 직접 INSERT" 를 제시. 실제 PostgREST 시도 시 `id` 자동 default 부재 + `id REFERENCES auth.users` FK 위반 (HTTP 400, code=23502 / 23503).
- **Fix:** 더 깨끗한 path 발견 — `supabase/migrations/002_tables.sql:254` 의 `handle_new_user()` 트리거가 `auth.users INSERT 시 자동으로 profiles row 생성 (id, user_id, email, created_at)`. user_id 는 `COALESCE(NEW.raw_user_meta_data->>'user_id', NEW.id::text)` 패턴. 따라서:
  1. Supabase Auth Admin API `/auth/v1/admin/users` POST with `user_metadata: {user_id: 'testuser_w1', ...}` (service_role 필요)
  2. 트리거가 자동으로 profiles row insert (id=auth_uuid, user_id='testuser_w1', email='testuser_w1@tbm-poc.local')
  3. PostgREST POST `/rest/v1/profiles?on_conflict=id` with `Prefer: resolution=merge-duplicates` 로 `name`, `user_role`, `group_id` 채움
- **Files modified:** `scripts/seed_tbm_demo.py` (`create_auth_user`, `lookup_auth_user_by_email`, `wipe_workers`, `seed_workers` 함수)
- **Commit:** `20d2c7f`
- **근거:** plan 의 fallback 보다 훨씬 깨끗 — Supabase Auth 생태계와 일관 (Phase 7 의 test_user workaround 진화형). PoC 한정 — testuser_w1·w2·w3 는 실제 로그인 X (fcm_token NULL, password='tbm-poc-no-login').

**2. [Rule 3 - 환경 우회] psql 미설치 환경에서 회귀 가드 SQL 실행**

- **Found during:** Task 2 (test_013_tbm_isolation.sql 실행 시)
- **Issue:** plan 의 verify 는 "Dashboard SQL Editor 또는 psql" 을 제안. 본 Windows 환경에 psql 미설치 (Phase 8 02 deviation 와 동일).
- **Fix:** `supabase db query --linked -f <file>` CLI 가 Management API 경유 SQL 실행 지원. 단 psql `\echo` 메타-명령 미지원 → python 스크립트로 `\echo` 7 lines 제거 후 `build/tmp_sql/test_013_no_echo.sql` (gitignored) 로 저장 후 실행. 결과: `test_013_tbm_isolation: 7/7 assertions PASS`. 원본 파일은 그대로 유지 (Dashboard SQL Editor 호환).
- **Files modified:** 없음 (실행 우회만, 원본 SQL 파일 보존)
- **근거:** plan 의 verify 의도는 "7 assertions 실제 운영 DB 에서 PASS" 충족. 실행 채널은 psql / supabase db query / Dashboard 모두 동등 (RAISE EXCEPTION 동작 동일).

### 없음 (Rule 1 / Rule 2)

코드 버그 / 보안 보강은 모두 plan 단계에서 사전 적용 (T-9-05/07/01 의 graceful skip + JWT 노출 회피 + Storage path 가드 모두 plan 본문에 명시). 실행 단계 fix 0건.

## Vault sr_key prerequisite 인지

본 plan 의 `RPC tbm_missed_attendance_check` 204 응답은 두 가지 path 중 어느 쪽인지 구분 X:
- (a) Vault sr_key+base_url 둘 다 시드됨 → FOR 루프 진입 (세션 없음 → 즉시 RETURN)
- (b) Vault sr_key 미시드 → RAISE WARNING + RETURN graceful skip path

Phase 8 04 SUMMARY 와 일관: Vault `service_role_key` 는 Dashboard 수동 시드 prerequisite (T-9-07 / T-8-01 git 노출 회피). Plan 09-02 의 Edge Function tbm-missed 가 배포 + Vault sr_key 시드 + 첫 세션 + expected_end_at+30분 경과 후 1분 cron tick 시점에 실제 round-trip 활성화 (자연 동작). 본 plan 의 PostgREST/Storage/SQL assertion 검증과는 직교.

## Self-Check: PASSED

**Files created:**
- `supabase/migrations/013_tbm_schema.sql` — FOUND (292 lines, git tracked)
- `scripts/seed_tbm_demo.py` — FOUND (236 lines, git tracked, 실행 → worker count=3)
- `tests/sql/test_013_tbm_isolation.sql` — FOUND (140 lines, git tracked, 7/7 PASS)
- `.planning/phases/09-tbm-worker-guide/09-01-SUMMARY.md` — 본 파일

**Commits:**
- `f044fac` — feat(09-01): add 013_tbm_schema.sql (Task 1)
- `20d2c7f` — feat(09-01): seed_tbm_demo.py + test_013_tbm_isolation.sql (Task 2)
- (next) docs(09-01): metadata commit

**Migration state:**
- `supabase migration list --linked` → 013 Local+Remote 모두 등장

**Regression:** ai_agent/tests/ 28/28 PASS

**Verification:** 모든 plan `<verification>` 조건 충족 (013 파일 존재 + push 성공 + migration list + 5 templates + tbm_sessions empty + cron 등록 + storage 버킷 + 7 assertions PASS + eyJ count=0 + ai_agent 28/28).

---

*Plan 09-01 — TBM 스키마 + Storage + cron 인프라 운영 DB 적용 완료*
*Phase 9 Wave 2 진입 가능 (Plan 02 Edge Function ∥ Plan 03 Android UI)*
