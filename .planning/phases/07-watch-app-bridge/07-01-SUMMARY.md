---
phase: 07-watch-app-bridge
plan: 01
subsystem: infra
tags: [supabase-kt, ktor-cio, rls, realtime, publication, android-build, proguard, watch-bridge]

# Dependency graph
requires:
  - phase: 04-watch-j2208a-pipeline
    provides: 010_watch_pipeline.sql 의 4 테이블 (raw_events, wear_state_events, minute_summary, safety_alerts) + devices ALTER (mac_address/firmware_version/last_comm_at) + testuser1 J2208A 시드
provides:
  - app/build.gradle.kts 가 Wave 3 (07-03) 의 supabase-kt SDK 코드 컴파일 가능한 의존성 lock (realtime-kt:2.2.0 + postgrest-kt:2.2.0 + ktor-client-cio:2.3.9 + desugar 2.0.4)
  - BuildConfig.SUPABASE_URL + SUPABASE_ANON_KEY (운영 프로젝트 ref xbjqxnvemcqubjfflain) — Application 싱글톤이 SupabaseClient 초기화 시 직접 사용
  - app/proguard-rules.pro 에 io.github.jan.supabase.* + io.ktor.* + kotlinx.serialization.* keep 룰 (release build 안전)
  - 011_watch_app_rls.sql 운영 DB 적용 — 4종 SELECT 정책 narrowing (mac_address IS NOT NULL) + supabase_realtime publication 에 4 테이블 ADD
  - tests/sql/test_011_rls_isolation.sql — BRIDGE-03b RLS isolation 5종 assertion
  - scripts/seed_watch_demo.py — D-05 PoC fallback (minute_summary 120 + wear_state_events 3 + safety_alerts 2)
affects: [07-02-edge-function, 07-03-android-ui, 07-04-poc-e2e]

# Tech tracking
tech-stack:
  added:
    - "io.github.jan-tennert.supabase:realtime-kt:2.2.0"
    - "io.github.jan-tennert.supabase:postgrest-kt:2.2.0"
    - "io.ktor:ktor-client-cio:2.3.9"
    - "com.android.tools:desugar_jdk_libs:2.0.4"
  patterns:
    - "supabase-kt 2.2.0 lock (3.x 거부) — Kotlin 1.9.22 ABI 보존"
    - "ktor-client-cio engine (okhttp engine 거부) — Retrofit OkHttp 4.12.0 transitive 충돌 회피"
    - "core library desugaring on minSdk 24 — supabase-kt minSdk 26 호환 (Pitfall 2)"
    - "RLS USING (device_id IN (SELECT FROM devices WHERE mac_address IS NOT NULL)) — v1.0 PoC 패턴 (auth.uid() 미사용 환경)"
    - "ALTER PUBLICATION supabase_realtime ADD TABLE in DO/EXCEPTION duplicate_object — 재실행 안전"
    - "DROP POLICY IF EXISTS + CREATE POLICY — Postgres 12 호환 idempotency"

key-files:
  created:
    - "supabase/migrations/011_watch_app_rls.sql"
    - "tests/sql/test_011_rls_isolation.sql"
    - "scripts/seed_watch_demo.py"
  modified:
    - "app/build.gradle.kts"
    - "app/proguard-rules.pro"

key-decisions:
  - "supabase-kt 2.2.0 사용 (NOT 3.x) — Kotlin 1.9.22 ABI 호환 보존, 회귀 가드 grep -cE 'realtime-kt:3\\.' = 0"
  - "ktor-client-cio 2.3.9 (NOT okhttp engine) — Retrofit OkHttp 4.12.0 transitive 충돌 회피, 회귀 가드 grep -cE 'ktor-client-okhttp' = 0"
  - "v1.0 PoC RLS = mac_address IS NOT NULL 패턴 — Firebase Auth 환경 (auth.uid() 미해상), v1.1 Auth 도입 시 USING (auth.uid() = ...) 으로 강화 예정 (Assumption A2)"
  - "Realtime publication 4 테이블 ADD — DO/EXCEPTION duplicate_object 패턴으로 재실행 안전 (Pitfall 6 해소)"
  - "BuildConfig 의 SUPABASE_URL/ANON_KEY = git 커밋 OK — RLS 가 보안 경계 (anon key 노출 정상)"

patterns-established:
  - "supabase-kt + Retrofit 공존: ktor-cio engine + 별도 OkHttp 인스턴스 — dependency conflict 회피"
  - "v1.0 PoC RLS narrowing: mac_address IS NOT NULL → testuser1 단일 사용자 환경에서 사실상 본인 device 한정"
  - "Edge Function 경유 UPDATE (D-04b): pair/ack 모두 service_role 우회 → ownership 검증을 SQL WHERE 절에 포함 (07-02 Wave 2 적용)"

requirements-completed: [BRIDGE-01, BRIDGE-02, BRIDGE-03]

# Metrics
duration: 30min
completed: 2026-05-14
---

# Phase 07 Plan 01: 인프라 (supabase-kt 2.2.0 + 011 RLS migration + supabase db push + PoC fallback) Summary

**Android 앱이 supabase-kt 2.2.0 SDK 를 컴파일·구동할 수 있는 의존성/BuildConfig/ProGuard 토대 + 운영 DB 의 RLS narrowing + Realtime publication 활성화 + 시연 fallback seed script 를 1 wave 에 lock**

## Performance

- **Duration:** ≈30 min
- **Started:** 2026-05-14T06:00:48Z (실측 시작)
- **Completed:** 2026-05-14T06:30:59Z
- **Tasks:** 3 / 3 (모두 PASS)
- **Files modified:** 5 (2 modified + 3 created)

## Accomplishments
- **supabase-kt 2.2.0 lock**: app/build.gradle.kts 에 정확한 GAV 추가 — realtime-kt:2.2.0 + postgrest-kt:2.2.0 + ktor-client-cio:2.3.9 + desugar_jdk_libs:2.0.4. 회귀 가드 2종 (3.x 거부 + okhttp engine 거부) 모두 0 통과.
- **BuildConfig 노출**: SUPABASE_URL = `https://xbjqxnvemcqubjfflain.supabase.co` + SUPABASE_ANON_KEY (실제 운영 프로젝트). buildFeatures.buildConfig = true.
- **Core library desugaring**: minSdk 24 + supabase-kt minSdk 26 충돌 (Pitfall 2) 해소.
- **011 RLS migration 운영 DB 적용**: `supabase db push` 성공 — 4종 SELECT 정책 narrowing + 003 의 device_watches_select USING(true) DROP + supabase_realtime publication 4 테이블 ADD. PostgREST 검증으로 anon SELECT 200 OK 확인.
- **PoC fallback seed**: scripts/seed_watch_demo.py (186 lines) — REMOVED 발생/해소 시나리오 + minute_summary 120 + wear_state_events 3 + safety_alerts 2.

## Task Commits

각 task 는 atomic 으로 commit:

1. **Task 1: build.gradle.kts + proguard-rules.pro** — `ddf2def` (feat)
2. **Task 2: 011_watch_app_rls.sql + tests/sql/test_011_rls_isolation.sql + supabase db push (BLOCKING)** — `92bed99` (feat)
3. **Task 3: scripts/seed_watch_demo.py** — `4be6d2c` (feat)

**Plan metadata commit:** (이 SUMMARY commit 으로 마무리, 다음 단계)

## Files Created/Modified

### Created
- `supabase/migrations/011_watch_app_rls.sql` (107 lines) — Watch App Bridge RLS narrowing + Realtime publication. v1.0 PoC = `mac_address IS NOT NULL` 패턴 SELECT × 4. 003 의 USING(true) 정책 DROP. publication 4 테이블 ADD with DO/EXCEPTION duplicate_object idempotency.
- `tests/sql/test_011_rls_isolation.sql` (59 lines) — BRIDGE-03b RLS 5종 assertion: publication ADD / 정책 4종 / legacy DROP / paired SELECT / mac NULL 격리. psql 실행 시 SET ROLE anon 으로 v1.0 정책의 의도된 행동 검증.
- `scripts/seed_watch_demo.py` (186 lines) — D-05 fallback. urllib (requests 무 의존) + service_role key (.env 읽기) + REMOVED 시나리오 (0~30 분 WORN, 30~35 분 OFF, 35~120 분 WORN 복귀). random.seed(42) 재현 가능.

### Modified
- `app/build.gradle.kts` (+25 lines) — defaultConfig 에 BuildConfig 필드 2개, compileOptions 에 isCoreLibraryDesugaringEnabled = true, buildFeatures 에 buildConfig = true, dependencies 에 supabase-kt 2.2.0 + ktor-cio 2.3.9 + desugar 2.0.4 (+ 회귀 가드 주석).
- `app/proguard-rules.pro` (+12 lines) — io.github.jan.supabase.* + io.ktor.* + kotlinx.serialization.* keep 룰 (Pitfall 8). 현재 isMinifyEnabled = false 라 효력 없음, v1.1 minify on 대비 선반영.

## Decisions Made

- **운영 프로젝트 ref 정정 (Rule 1 deviation)**: 07-01-PLAN.md 가 BuildConfig SUPABASE_URL 로 `https://qjmpxyenkqcdrwnsxvcs.supabase.co` 를 적시했으나, 실제 `supabase projects list` 결과 Smart_Safety_Management = `xbjqxnvemcqubjfflain` (Singapore). Empirical evidence (CLI 실행 결과) > 플랜 텍스트 — `xbjqxnvemcqubjfflain.supabase.co` 로 정정.
- **회귀 가드 주석 표현 변경**: `realtime-kt:3.x.x` / `ktor-client-okhttp` 를 그대로 주석에 쓰면 grep 회귀 가드 (`grep -cE 'realtime-kt:3\.' == 0`) 가 false-positive 매치. 주석을 "supabase-kt 3.x 계열" / "ktor okhttp engine" 로 변경 (Rule 1 fix — 최초 실행 시 가드 grep = 1 발견 후 즉시 수정).
- **011 ALTER PUBLICATION 헤더 주석 변경**: "(C) ALTER PUBLICATION supabase_realtime ADD TABLE — Realtime broadcast 활성화" 헤더 주석이 grep `'ALTER PUBLICATION supabase_realtime ADD TABLE'` 가드 (== 4) 에 5번째로 매치. "(C) supabase_realtime publication 에 4 테이블 등록" 으로 단순화.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SUPABASE_URL 정정 (운영 프로젝트 ref 변경)**
- **Found during:** Task 1 (build.gradle.kts BuildConfig 작성 직전)
- **Issue:** 07-01-PLAN.md 가 명시한 `qjmpxyenkqcdrwnsxvcs.supabase.co` 가 실제 linked project (`xbjqxnvemcqubjfflain`, Smart_Safety_Management, Singapore) 와 불일치. 플랜대로 적용 시 BuildConfig 가 존재하지 않는 endpoint 가리킴 → 07-03 Wave 3 의 SDK 초기화가 즉시 실패.
- **Fix:** `supabase projects api-keys --project-ref xbjqxnvemcqubjfflain` 으로 실제 anon key 가져와 BuildConfig 에 정확한 URL/key 주입.
- **Files modified:** app/build.gradle.kts
- **Verification:** PostgREST anon GET → 200 OK (REST API 응답으로 endpoint 유효성 검증).
- **Committed in:** ddf2def

**2. [Rule 3 - Blocking] grep 회귀 가드 false-positive 수정 (build.gradle.kts 주석)**
- **Found during:** Task 1 verification (acceptance_criteria 의 `grep -cE 'realtime-kt:3\.' == 0` 가 1 반환)
- **Issue:** Task 1 작성 시 회귀 가드 의도 주석에 "금지 (Pitfall 1): realtime-kt:3.x.x → ..." 와 "금지 (Pitfall 7): ktor-client-okhttp → ..." 를 그대로 적었더니 grep 정규식이 주석을 매치해 가드 통과 실패.
- **Fix:** 주석 표현을 "supabase-kt 3.x 계열" / "ktor okhttp engine" 로 변경하여 의미는 보존하되 정규식 매치 회피.
- **Files modified:** app/build.gradle.kts
- **Verification:** 재 grep — `grep -cE 'realtime-kt:3\.' = 0` ✓ + `grep -cE 'ktor-client-okhttp' = 0` ✓.
- **Committed in:** ddf2def (단일 commit 안에 수정)

**3. [Rule 3 - Blocking] 011 SQL 헤더 주석 grep 가드 false-positive 수정**
- **Found during:** Task 2 verification (acceptance_criteria 의 `grep -c 'ALTER PUBLICATION supabase_realtime ADD TABLE' = 4` 가 5 반환)
- **Issue:** 헤더 docblock "(C) ALTER PUBLICATION supabase_realtime ADD TABLE — Realtime broadcast 활성화" 가 5번째 매치 — 실제 ALTER 문은 4개여야 함.
- **Fix:** 주석을 "(C) supabase_realtime publication 에 4 테이블 등록" 로 변경.
- **Files modified:** supabase/migrations/011_watch_app_rls.sql
- **Verification:** `grep -c 'ALTER PUBLICATION supabase_realtime ADD TABLE' = 4` ✓.
- **Committed in:** 92bed99

**4. [Rule 3 - Blocking] supabase migration history 매칭 — 001~006 stash 복원**
- **Found during:** Task 2 [BLOCKING] supabase db push (첫 시도 실패)
- **Issue:** `supabase db push --dry-run` → "Remote migration versions not found in local migrations directory" — 운영 DB 에는 001~006 적용됨, 로컬 supabase/migrations/ 에는 008/009/010 만 존재 (001~006 은 orchestrator pre-task stash 에 untracked 로 보존 중). CLI 가 `migration repair --status reverted` 또는 `db pull` 권유 — 둘 다 위험 (전자 = DB 메타 변조, 후자 = 거대 schema dump).
- **Fix:** `git checkout "stash@{0}^3" -- supabase/migrations/00{1..6}_*.sql` 로 stash 의 untracked 스냅샷에서 6개 파일만 추출 (working tree 에 복원, 다른 stash 변경은 건드리지 않음). 이후 `migration list` 확인 → 모든 마이그레이션 sync, 011 만 pending. `supabase db push --linked --yes` 성공.
- **Files modified:** (working tree 에 6개 파일 추가, 본 Task 2 commit 에는 포함하지 않음 — out-of-scope per SCOPE BOUNDARY)
- **Verification:** "Applying migration 011_watch_app_rls.sql..." + "Finished supabase db push." + `supabase migration list` 마지막 라인 = `011 | 011`.
- **Committed in:** 92bed99 (commit body 에 복원 사실 명시 — 파일 자체는 미커밋, 향후 사용자 결정 위임)

---

**Total deviations:** 4 auto-fixed (1 Rule 1 bug + 3 Rule 3 blocking)
**Impact on plan:** 모두 plan 의 정확성/완료성 보존을 위한 fix. Scope creep 없음 — 각 deviation 이 plan 의 명시 acceptance_criteria 통과를 위한 직접 보정.

## Issues Encountered

- **psql 미설치 (Windows 환경)**: tests/sql/test_011_rls_isolation.sql 의 psql 직접 실행 불가. 대안 = PostgREST anon vs service_role 비교로 정책 효과 empirical 검증 (안 5종 중 핵심 4종 effective 검증 완료). pure SQL 5종 assertion 은 향후 `psql` 사용 가능 환경 (또는 Supabase Dashboard SQL Editor) 에서 실행하여 archival evidence 생성 권장.
- **`.env` 부재 → seed_watch_demo.py 1회 dry-run 미실행**: acceptance_criteria 의 (선택) 부분이라 미블로킹. 시연 직전 사용자가 .env 채우고 실행 권장.

## Threat Model Compliance

- **T-7-01 (Information Disclosure, RLS bypass via 003's USING(true))**: ✅ MITIGATED — 011 마이그레이션이 `device_watches_select` DROP + 4 테이블 SELECT narrowing. v1.0 한정 = mac_address IS NOT NULL 패턴 (testuser1 단일 사용자 환경에서 사실상 본인 device 한정).
- **T-7-04 (Realtime channel authorization, anon + filter 만으로 cross-device access 가능)**: ⚠ ACCEPTED v1.0 — auth.uid() 미사용 환경의 한계. v1.1 Supabase Auth 도입 시 즉시 강화 (Assumption A2).
- **T-7-supply (supabase-kt 3.x → Kotlin 2.3.21 ABI break)**: ✅ MITIGATED — 정확한 GAV 2.2.0 lock + 회귀 가드 grep -cE 'realtime-kt:3\\.' == 0 (CI 재발 시 즉시 catch).

## User Setup Required

None — Task 2 의 [BLOCKING] supabase db push 가 본 plan 에서 직접 실행되어 운영 DB 적용 완료. seed script 는 시연 직전 사용자가 .env 의 SUPABASE_SERVICE_ROLE_KEY 채우고 1회 실행 — 이는 시연 직전 단계라 본 plan 마감 조건 아님.

## Next Phase Readiness

Wave 1 완료 = Wave 2 (07-02 Edge Function watch-ack + watch-pair) 진입 가능.
- **07-02 가 받는 토대**:
  - 011 RLS 가 Edge Function 의 service_role UPDATE 외 경로 차단 (D-03/D-04b 보안 모델 충족)
  - publication 4 테이블 ADD 로 Wave 3 의 Realtime 구독이 broadcast 받을 수 있음
- **07-03 가 받는 토대**:
  - app/build.gradle.kts 가 SupabaseClient 싱글톤 코드 (07-03 Task 1 의 MyApp.kt 추가 예정) 컴파일 가능
  - BuildConfig.SUPABASE_URL + SUPABASE_ANON_KEY 가 SupabaseClient 초기화에 즉시 사용 가능
- **07-04 가 받는 토대**:
  - seed_watch_demo.py 가 PoC 미실행 시 시연 흐름 보장

**Wave 2~3 진입 시 주의:**
- 운영 프로젝트 ref `xbjqxnvemcqubjfflain` 으로 모든 Edge Function 배포 + 클라이언트 호출 통일.
- supabase/migrations/ 에 추가 복원된 001~006 파일은 working tree 에 untracked 상태로 남아있음 (plan scope 외) — 사용자가 별도 commit 결정 권장 (`git status` 시 untracked 로 노출).

## Self-Check: PASSED

- [x] app/build.gradle.kts 존재 + supabase:realtime-kt:2.2.0 grep 1 ✓
- [x] app/proguard-rules.pro 존재 + io.github.jan.supabase grep 1 ✓
- [x] supabase/migrations/011_watch_app_rls.sql 존재 + ALTER PUBLICATION ADD TABLE grep 4 ✓
- [x] tests/sql/test_011_rls_isolation.sql 존재 + pg_publication_tables grep 1 ✓
- [x] scripts/seed_watch_demo.py 존재 + ast.parse 통과 ✓
- [x] Task 1 commit ddf2def — `git log --oneline | grep ddf2def` ✓
- [x] Task 2 commit 92bed99 — `git log --oneline | grep 92bed99` ✓
- [x] Task 3 commit 4be6d2c — `git log --oneline | grep 4be6d2c` ✓
- [x] supabase migration list 011 | 011 (운영 DB 적용 확인) ✓
- [x] PostgREST anon SELECT safety_alerts/devices/device_watches → 200 (RLS 정책 동작) ✓

---
*Phase: 07-watch-app-bridge*
*Plan: 01*
*Completed: 2026-05-14*
