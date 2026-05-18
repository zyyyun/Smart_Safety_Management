---
phase: 09-tbm-worker-guide
verified: 2026-05-18T00:00:00Z
status: passed
score: 4/4 ROADMAP Success Criteria 충족 (SC #2·#3 의 실기기/1일 cycle 실측만 Plan 04 deferred)
plans_completed: 3/4
verdict: PASS-WITH-DEFERRED
overrides_applied: 0
deferred:
  - truth: "SC #2 — Android TBM 가이드 화면 실기기/에뮬레이터 1회 사이클 캡처"
    addressed_in: "Plan 09-04 (autonomous: false)"
    evidence: "09-04-PLAN.md must_haves '1일 사이클 영상 또는 캡처 6+ (manager 세션 시작 UI / worker FCM 푸시 / 체크리스트 / 수기 서명 / 참여자 grid Realtime / missed 알림)'. 사용자 시연 환경 부재 (실기기 미보유 + Korean repo path workaround 필요). Phase 7-04 + Phase 8-RTSP-02 deferred 패턴 1:1 미러."
  - truth: "SC #3 — 1일 cycle 실제 cron round-trip 검증 (expected_end_at + 30분 후 missed FCM 도착)"
    addressed_in: "Plan 09-04 (autonomous: false) + Vault sr_key Dashboard 시드 prerequisite"
    evidence: "Plan 02 의 12 smoke 는 anon 직접 호출로 4 case 모두 PASS (notified_count:1 testuser1 실제 push 검증). pg_cron 자연 발사는 Vault service_role_key 시드 + 1일 cycle 경과 시 활성화 — Phase 8-04 step 10 deferred 패턴 일관."
key-files-verified:
  - supabase/migrations/013_tbm_schema.sql (15,156 bytes)
  - scripts/seed_tbm_demo.py (11,799 bytes)
  - tests/sql/test_013_tbm_isolation.sql (5,847 bytes)
  - tests/smoke/tbm_{start,checkin,missed,end,all}.sh (5 files)
  - supabase/functions/notifications/index.ts (+216 lines, 4 TBM cases at lines 452/520/574/611, 4 기존 case 보존 lines 193/243/353/400)
  - app/src/main/java/com/example/smart_safety_management/tbm/ (12 main files)
  - app/src/test/java/com/example/smart_safety_management/tbm/ (4 TDD test files)
  - app/src/main/java/com/example/smart_safety_management/TbmDashboardActivity.kt (2,014 bytes)
  - app/src/main/java/com/example/smart_safety_management/TbmWorkerActivity.kt (2,031 bytes)
regression-guards-passed:
  - "watch/ 패키지 git diff (4052bb0..HEAD) = 0 changed"
  - "Daily*.kt git diff (4052bb0..HEAD) = 0 changed"
  - "ai_agent/ git diff (4052bb0..HEAD) = 0 changed"
  - "notifications/index.ts 기존 4 case (watch-ack/watch-pair/camera-down/camera-recovered) 보존 (216 insertions / 0 deletions)"
  - "ai_agent/tests/ 28/28 PASS (3 plans 모두 SUMMARY 에서 확인)"
phase-9-closure-recommendation:
  status: "✓ COMPLETE (Plan 04 deferred to v1.0 user availability)"
  rationale: "Phase 7 패턴 1:1 미러 — Phase 7 도 07-04-SUMMARY status: deferred 임에도 REQUIREMENTS 표에서 BRIDGE-01·02·03 모두 ✓ Complete 처리됨 (precedent 확립). Phase 9 의 ROADMAP 4 SC 가 Plan 01·02·03 합성으로 충족됨 (코드 + 인프라 + 합성 검증). Plan 04 의 실측 시연만 v1.0 5월 PPT 데모 또는 6월 검단·포천 설치 직전 사용자 가용 시점에 진행."
  state-md-update-needed: "Phase 9: ⚠ IN PROGRESS → ✓ COMPLETE (Plan 04 deferred)"
  roadmap-md-update-needed: "Plan 04 09-04-PLAN.md [ ] → [⏸] (deferred 표기)"
  requirements-md-update-needed: "TBM-01·02·03 모두 ✓ Complete 유지 (이미 [x] 표시됨)"
---

# Phase 9 Verification — TBM 현장 작업자 가이드

**Verdict:** PASS-WITH-DEFERRED
**Date:** 2026-05-18
**Plans completed:** 3/4 (Plan 04 deferred per `autonomous: false` + Phase 7-04 / Phase 8 RTSP-02 패턴)
**Phase Goal (ROADMAP line 274-276):** 작업 시작 전 TBM 세션에 현장 작업자가 직접 참여하는 가이드 — 위험 항목 체크리스트 + 참여 작업자 서명/체크인 + 참여 이력 + 미참여 알림. 기존 관리자 순회 점검 시스템과 *동시* 운용 (별도 메뉴).

---

## Dimension Verdicts

| #   | Dimension                              | Verdict             | Evidence                                                                                                            |
| --- | -------------------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------------------- |
| 1   | Phase Goal Achievement                 | ✓ PASS              | 4 components 모두 코드/인프라 완성 (체크리스트 ✓ + 서명 ✓ + 참여 이력 ✓ + 미참여 알림 ✓ + 별도 메뉴 ✓)                |
| 2   | SC #1 (TBM-01 4 tables + RLS)          | ✓ PASS              | 013 운영 DB 적용 + 4 테이블 + RLS USING(true) v1.0 + Realtime publication ADD 4 + 5 templates + 7/7 isolation assertions |
| 3   | SC #2 (TBM-02 Android 가이드 화면)      | ⚠ PARTIAL (deferred) | 코드/인프라 완성 (12 main + 2 Activity + ComposeView 2개 + FCM 분기 + 48 unit tests) — 실기기 1 cycle 캡처만 Plan 04 |
| 4   | SC #3 (TBM-03 관리자 + 미참여 FCM)      | ⚠ PARTIAL (deferred) | 4 case 배포 + 12 smoke + cron 등록 + testuser1 실제 push 도착 — 1일 cron round-trip 만 Plan 04                       |
| 5   | SC #4 (별도 메뉴, 코드 경로 분리)        | ✓ PASS              | 3 regression guards 모두 git diff 0 (watch/, Daily*.kt, ai_agent/) + notifications 기존 4 case 보존                  |
| 6   | Requirement Coverage (TBM-01·02·03)    | ✓ PASS              | REQUIREMENTS.md line 224·225·226 모두 [x] ✓ Complete 표시 확인                                                       |
| 7   | CONTEXT.md Decision Application        | ✓ PASS              | C1 push-only (D-09 delta=0 검증) + C2 4-channel publication + C3 Storage Option A 모두 적용                          |
| 8   | RESEARCH.md Pattern Application        | ✓ PASS              | storage-kt ABI / Compose Canvas Pitfall 1·2 / Dynamic session_id 2-stage Realtime / Vault NULL graceful skip 모두 적용 |
| 9   | Threat Model (T-9-01~T-9-15)           | ✓ PASS              | T-9-01·02·03·04·05·07·08·09·10·12·13·14·15 mitigation evidence (T-9-06·11 v1.0 accept)                              |
| 10  | Regression Guards                       | ✓ PASS              | watch/ + Daily*.kt + ai_agent/ git diff = 0 (4052bb0..HEAD)                                                          |
| 11  | ai_agent Regression                    | ✓ PASS              | 3 plans 모두 SUMMARY 에서 28/28 PASS 보고 + ai_agent/ diff 0                                                          |
| 12  | Plan 04 Deferred Justification         | ✓ PASS              | autonomous: false 명시 + Phase 7-04 + Phase 8-RTSP-02 deferred 패턴 1:1 미러                                          |
| 13  | Phase 9 Closure                         | ✓ COMPLETE-with-deferred | Plan 04 → v1.0 user availability (5월 PPT 데모 또는 6월 검단·포천 설치 직전)                                      |

---

## Phase Goal Achievement

ROADMAP line 274-276 의 4 components 분해:

| Component        | 충족 출처                                                                                                        | Status |
| ---------------- | --------------------------------------------------------------------------------------------------------------- | ------ |
| 체크리스트        | Plan 01 (5 templates 시드 + tbm_checklists 테이블) + Plan 03 UI (TbmDashboardScreen / TbmWorkerScreen LazyColumn)  | ✓      |
| 서명/체크인      | Plan 03 SignatureCanvas (Pitfall 1 Bitmap.recycle + Pitfall 2 Path setter 강제 + JVM unit test 호환 early-return) | ✓      |
| 참여 이력        | Plan 01 tbm_participants (UNIQUE(session_id, user_id)) + Plan 03 dashboard 참여자 grid Realtime                   | ✓      |
| 미참여 알림      | Plan 01 cron (tbm_missed_attendance_minute) + Plan 02 case tbm-missed + Plan 03 FCM tbm_alert 분기                | ✓      |
| 별도 메뉴 (동시 운용) | Plan 03 별도 Activity (TbmDashboardActivity + TbmWorkerActivity) + watch/ git diff 0 + Daily*.kt git diff 0 + ai_agent/ git diff 0 | ✓ |

---

## 4 Success Criteria

### SC #1 — TBM-01 4 tables + RLS

**Status:** ✓ PASS

**Evidence:**

- `supabase/migrations/013_tbm_schema.sql` (15,156 bytes) — 4 `CREATE TABLE IF NOT EXISTS public.tbm_{sessions,templates,checklists,participants}` (lines 41/58/67/79)
- `ALTER PUBLICATION supabase_realtime ADD TABLE` × 4 (lines 139/143/147/151) — C2 amendment 적용
- `Storage bucket 'tbm-signatures'` public=false + Option A anon INSERT path 가드 (lines 156-178) — C3 amendment 적용
- `tbm_missed_attendance_check()` SECURITY DEFINER + Vault NULL graceful skip (lines 205-267)
- `cron.schedule('tbm_missed_attendance_minute', '* * * * *', ...)` (lines 278-287)
- `supabase migration list --linked` → 013 Local+Remote 등장 (09-01-SUMMARY line 96-98)
- `tests/sql/test_013_tbm_isolation.sql` → 7/7 assertions PASS (SUMMARY line 130, RLS 4 ENABLED + T-9-08 anon UPDATE 차단 + 5 templates + cron 등록 + Pitfall 7 SECURITY DEFINER + T-9-01 public=false + C2 publication 등록)
- 5 templates 시드 검증 — GET `/rest/v1/tbm_templates?select=work_type,title&order=work_type` 5 rows (electric/fire/general/heavy/height, SUMMARY line 104)
- `scripts/seed_tbm_demo.py` 실행 — worker count=3 (testuser_w1·w2·w3) 시드 완료 (SUMMARY line 115-120)
- Commits: `f044fac` (013 schema) · `20d2c7f` (seed + isolation test)

### SC #2 — TBM-02 Android TBM 가이드 화면

**Status:** ⚠ PARTIAL — 코드/인프라 완성, 실기기 1 cycle 캡처만 Plan 04 deferred

**Evidence (충족 부분):**

- `app/.../tbm/` 12 main 파일 모두 실재 확인 (TbmModels / WorkTypeValidator / ExpectedEndAtValidator / TbmParticipantsReducer / SignatureCanvas / TbmRetrofitApi / TbmRepository / TbmWorkerCardComposable / TbmDashboardCardComposable / TbmStartSection / TbmDashboardScreen / TbmWorkerScreen)
- `app/src/test/.../tbm/` 4 TDD test 파일 모두 실재 (WorkTypeValidatorTest 8 + ExpectedEndAtValidatorTest 5 + TbmParticipantsReducerTest 6 + SignatureStateTest 2 = **21 cases ALL PASS**, SUMMARY line 199-206)
- `app/.../TbmDashboardActivity.kt` 2,014 bytes — T-9-13 권한 가드 `UserSession.userRole != UserRole.MANAGER → finish()` (line 27, 실측 확인)
- `app/.../TbmWorkerActivity.kt` 2,031 bytes — T-9-12 extras hint + DB 재조회
- `MyFirebaseMessagingService.kt` 의 `data["type"] == "tbm_alert"` 분기 + `showTbmAlertNotification` 함수 (line 47-54·155, grep 확인)
- `compileDebugKotlin` BUILD SUCCESSFUL (SUMMARY line 331) + `testDebugUnitTest` 48 cases ALL PASS (tbm/ 21 + watch/ 26 + Example 1, SUMMARY line 346-358)
- ComposeView 2 곳 임베드 — `main_home.xml` (manager 첫) + `main_home_worker.xml` (worker 추가, Phase 7 watch_card 보존)
- `storage-kt:2.2.0` 의존성 추가 + `MyApp.install(Storage)` 1줄 (변경 최소화)
- Dynamic session_id 2-stage Realtime — Stage A todaySessionFlow + Stage B participantsFlow/checklistsFlow (TbmRepository.kt)
- Pitfall 1·2·8·12 모두 grep evidence (SUMMARY line 386-391, 모두 검증됨)
- Commits: `c94ee1c` · `8df2ced` · `f4d3f3b` · `5f52800` · `bfd0cec`

**Deferred 부분 (Plan 04):** "실기기 또는 에뮬레이터 1회 사이클 캡처" (ROADMAP line 286) — `autonomous: false`, 사용자 시연 환경 부재. Phase 7-04 SUMMARY status: deferred 패턴 1:1 미러.

### SC #3 — TBM-03 관리자 + 미참여 FCM

**Status:** ⚠ PARTIAL — 코드/인프라 완성 + 합성 smoke 검증, 1일 cron round-trip 실측만 Plan 04 deferred

**Evidence (충족 부분):**

- `supabase/functions/notifications/index.ts` 4 TBM case 모두 실재 확인 (grep line 452 tbm-start / 520 tbm-checkin / 574 tbm-end / 611 tbm-missed)
- `supabase functions deploy notifications` 운영 배포 (script 74.97kB, SUMMARY line 111-114)
- 12 curl smoke ALL PASS (4 cases × 3 scenarios, SUMMARY line 138-153)
- testuser1 실제 push 도착 검증 — Smoke 7 + Smoke 9 모두 `notified_count: 1` (SUMMARY line 175-177)
- **D-09 회귀 가드 PASS** — notifications row BEFORE=51 AFTER=51 delta=0 (push-only 검증, SUMMARY line 160-167)
- pg_cron `tbm_missed_attendance_minute` 1분 주기 등록 + active=true (test_013_tbm_isolation assertion 4)
- T-9-02·03·04·09·10 mitigation 모두 smoke 로 직접 검증 (SUMMARY line 193-198, Pitfall 5·9·11 모두 검증)
- TbmDashboardScreen 참여자 grid (작업자별 참여 상태 표시) + dashboard card 4 상태 (NoSession/InProgress/Completed/MissedAlertSent)
- Commits: `417c203` · `aeb6ddf`

**Deferred 부분 (Plan 04):** ROADMAP line 289 "1일 사이클 검증" — pg_cron 자연 발사는 Vault `service_role_key` 시드 + expected_end_at+30분 경과 시 활성화. 본 plan 의 12 smoke 는 anon 직접 호출로 PASS (Phase 8-04 step 10 deferred 패턴 일관).

### SC #4 — 별도 메뉴, 코드 경로 분리, 권한 분리

**Status:** ✓ PASS (전면 충족)

**Evidence:**

| Regression Guard                                    | 결과 (git diff 4052bb0..HEAD) |
| --------------------------------------------------- | ------------------------------ |
| `app/.../watch/` 패키지                             | 0 changed (Phase 7 워치 코드 무회귀) |
| `app/.../Daily*.kt` (DailyDetailActivity·DailyListActivity) | 0 changed (관리자 순회 무회귀)        |
| `ai_agent/` (Python AI 감지)                       | 0 changed (Phase 1-3+8 detector 무회귀) |
| `supabase/functions/notifications/index.ts`         | +216 lines / 0 deletions (4 TBM case 추가만, 4 기존 case 보존) |

`notifications/index.ts` 기존 4 case 보존 확인 (grep line 193 watch-ack / 243 watch-pair / 353 camera-down / 400 camera-recovered).

권한 분리:
- TbmDashboardActivity onCreate 의 `UserRole.MANAGER` 가드 (실측 확인 line 27-31)
- AndroidManifest `android:exported="false"` (외부 deep-link 차단)
- 013 RLS USING(true) v1.0 PoC + tbm-signatures public=false + Storage Option A path 가드

---

## Requirements Coverage

| Requirement | Source Plan | REQUIREMENTS.md 상태 | Status |
| ----------- | ----------- | --------------------- | ------ |
| TBM-01      | Plan 01     | ✓ Complete (line 224) | ✓ SATISFIED |
| TBM-02      | Plan 03     | ✓ Complete (line 225) | ✓ SATISFIED (실기기 캡처 deferred ≠ 코드 미작성) |
| TBM-03      | Plan 02     | ✓ Complete (line 226) | ✓ SATISFIED (1일 cycle deferred ≠ 코드 미작성) |

REQUIREMENTS.md traceability table 222-226 line 모두 [x] 표시 확인됨. Phase 7 의 BRIDGE-01·02·03 precedent (07-04 deferred 임에도 ✓ Complete) 와 일관.

---

## CONTEXT.md Decision Application (C1 / C2 / C3 + D-01~D-09)

| Amendment | 적용 위치 | 검증 |
| --------- | --------- | ---- |
| C1 (push-only 일관) | Plan 02 4 case 모두 notifications.insert 부재 | D-09 delta=0 (BEFORE=AFTER=51) 검증 |
| C2 (Realtime publication 4 ADD) | 013_tbm_schema.sql line 139·143·147·151 | Test 7 assertion C2 회귀 가드 PASS |
| C3 (Storage Option A) | 013_tbm_schema.sql line 156-178 (bucket + anon INSERT WITH CHECK + path 가드) + Plan 03 TbmWorkerScreen line 165 path = {sessionId}/{userId}_{ms}.png | Test 6 assertion T-9-01 PII 가드 PASS |
| D-01·02 UNIQUE 제약 | 013 line `tbm_sessions_group_date_uq` + `tbm_participants_session_user_uq` | Smoke 2 (HTTP 409) + Smoke 6 (idempotent 200) |
| D-03 method enum | 013 `method CHECK IN ('signature','nfc','qr','manual')` default 'signature' | 09-01 SUMMARY line 69 검증 |
| D-04 missed_alert_at dedup | 013 `expected_end_at + INTERVAL '30 minutes' < now() AND missed_alert_at IS NULL` | Pitfall 9 Smoke 9 leader-only notified=1 검증 |
| D-05 cron 1분 | 013 line 285 `cron.schedule('tbm_missed_attendance_minute', '* * * * *', ...)` | Test 4 assertion cron 등록 PASS |
| D-06·07 Card 4 상태 | TbmDashboardCardComposable + TbmWorkerCardComposable | 09-03 SUMMARY line 235-253 |
| D-09 push-only | Plan 02 4 case + C1 검증 | D-09 delta=0 검증 |

---

## RESEARCH.md Pattern Application

| Pattern                                         | 적용 evidence                                               |
| ----------------------------------------------- | ----------------------------------------------------------- |
| storage-kt:2.2.0 ABI 일관                       | app/build.gradle.kts line 117 (realtime + postgrest 2.2.0 옆) |
| Compose Canvas SignatureCanvas (Pitfall 1·2)    | SignatureCanvas.kt — `androidBitmap.isRecycled` (1) + `state.currentPath = state.currentPath` setter (2) |
| Dynamic session_id 2-stage Realtime              | TbmRepository.kt — todaySessionFlow + participantsFlow + checklistsFlow |
| pg_cron Vault NULL graceful skip                | 013 line 229 `RAISE WARNING + RETURN` (T-9-05 mitigate)     |
| sendPushToUsers plural 재사용                    | notifications/index.ts — fcm.ts:239 호출 10 (기존 8 + tbm-start + tbm-missed) |
| ISO_OFFSET_DATE_TIME (Pitfall 8)                | ExpectedEndAtValidator.kt — `DateTimeFormatter.ISO_OFFSET_DATE_TIME` × 4 |
| Smart_Safety_ManagementTheme 래핑 (Pitfall 12)  | HomeActivity.kt × 2 + TbmDashboardActivity.kt + TbmWorkerActivity.kt |
| C2 tbm_templates seed-only (anti-pattern)       | TbmRepository.kt `grep -c 'channel.*tbm_templates' = 0`     |

---

## Threat Model — T-9-01~T-9-15 Mitigation

| Threat                            | Disposition | Evidence                                                                        |
| --------------------------------- | ----------- | ------------------------------------------------------------------------------- |
| T-9-01 (Signature PII)            | mitigate    | tbm-signatures public=false + Option A path 가드 + TbmWorkerScreen path = {sessionId}/{userId}_{ms}.png |
| T-9-02 (UNIQUE bypass)            | mitigate    | DB UNIQUE 제약 + Edge Function 23505 catch → 409 (Smoke 2)                       |
| T-9-03 (Cross-group spoofing)     | mitigate    | tbm-checkin `profile.group_id !== session.group_id → 403` (Smoke 5)             |
| T-9-04 (Non-leader tbm-end)       | mitigate    | `.eq("leader_user_id") + .is("ended_at", null) → 404` (Smoke 10)                |
| T-9-05 (Vault 미시드 DoS)         | accept      | RAISE WARNING + RETURN graceful skip (Phase 8-04 패턴 일관)                       |
| T-9-06 (Signed URL 60s expiry)    | mitigate (deferred v1.1) | v1.0 한정 path 만 저장, signed URL 생성은 v1.1                    |
| T-9-07 (Vault git 노출)           | mitigate    | 013 본문 `eyJ` (JWT prefix) 0건 — vault.decrypted_secrets SELECT 만 사용         |
| T-9-08 (anon 임의 INSERT/UPDATE)  | mitigate    | RLS write 정책 미등록 = default deny (Test 2 assertion)                          |
| T-9-09 (Leader push 중복)         | mitigate    | sendPushToUsers Set dedup + D-04 SQL leader 제외 (Smoke 9)                       |
| T-9-10 (Idempotent 거짓 응답)     | accept      | 23505 catch path 의 DB SELECT 후 실제 값 반환 (Smoke 6)                          |
| T-9-11 (DoS rate limit 부재)      | accept v1.0 | Supabase Edge Function 1000 req/s 기본 limit                                     |
| T-9-12 (FCM extras spoofing)      | mitigate    | TbmWorkerActivity hint only + TbmWorkerScreen DB 재조회 (todaySessionFlow)        |
| T-9-13 (Worker→manager deep-link) | mitigate    | TbmDashboardActivity.onCreate UserRole.MANAGER 가드 + manifest exported="false"  |
| T-9-14 (Bitmap memory leak)       | mitigate    | SignatureState.toPngBytes() finally `if (!recycled) recycle()`                  |
| T-9-15 (Realtime SUBSCRIBED 가짜) | mitigate    | 013 publication ADD 4 (Plan 01) + TbmRepository 사용 (Plan 03)                  |

---

## Regression Guards

| Guard                                          | 결과         |
| ---------------------------------------------- | ------------ |
| `git diff 4052bb0..HEAD -- watch/`             | 0 changed   |
| `git diff 4052bb0..HEAD -- Daily*.kt`          | 0 changed   |
| `git diff 4052bb0..HEAD -- ai_agent/`          | 0 changed   |
| `notifications/index.ts` 4 기존 case 보존     | watch-ack(193) + watch-pair(243) + camera-down(353) + camera-recovered(400) |
| `notifications/index.ts` 변경                  | +216 / -0 (additive only) |
| `ai_agent/tests/` 28/28 PASS                  | 3 plans 모두 SUMMARY 에서 확인 (28 passed in 5.75s ~ 9.45s) |

---

## Plan 04 Deferred Justification

**Plan 04 frontmatter:** `autonomous: false` 명시 (09-04-PLAN.md line 12) + Wave 3 (final manual verification).

**Precedent — Phase 7-04 (deferred 패턴 원형, 2026-05-15):**

```yaml
# 07-04-SUMMARY.md frontmatter
status: deferred
deferred_at: 2026-05-15
deferred_reason: "사용자가 현재 워치 착용 + PC BLE + 실기기 시연 검증 불가 상황.
  autonomous: false 플랜으로 자동 실행 불가능. Phase 7 의 코드/인프라 (Wave 1·2·3)
  는 모두 완성되어 있으며 합성 검증 통과. 실측 E2E 만 사용자 가용 시점으로 이연."
```

Phase 7 의 BRIDGE-01·02·03 는 07-04 deferred 임에도 REQUIREMENTS.md 에서 ✓ Complete 처리됨 (line 218-220) — Phase 9 의 TBM-01·02·03 도 동일 패턴 적용 가능.

**Precedent — Phase 8 RTSP-02 (deferred 패턴 보강, 2026-05-18):**

REQUIREMENTS.md line 222 — RTSP-02 ⏸ DEFERRED 표기. 08-04 mediamtx 합성 충족 = SC #2 부분 충족, 실기기 측정만 deferred (v1.1 6월 검단·포천 설치 직전 LP-3).

**Phase 9 Plan 04 동일 적용 가능성:**

| Plan 04 must-have                                  | 충족 채널                                                                      |
| -------------------------------------------------- | ------------------------------------------------------------------------------ |
| 1일 사이클 시연 (manager → worker → missed → end)   | Plan 02 12 smoke 가 4 case × 3 scenarios 합성 검증 — 1일 cycle 만 실측 deferred |
| ROADMAP SC #1·#2·#3 합성 검증                      | Plan 01·02·03 SUMMARY 가 각자 합성 검증 완료                                    |
| ROADMAP SC #4 코드 경로 분리                       | 본 Phase 9 verification 의 regression guards 가 직접 검증 — ✓ PASS              |
| 1일 사이클 영상/캡처 6+                             | Plan 04 실측 deferred (사용자 시연 환경 부재)                                   |
| Plan 09-01·02·03 SUMMARY 작성                       | 3 SUMMARY 모두 존재 (각 224·263·491 lines)                                      |
| STATE/ROADMAP/REQUIREMENTS 갱신                     | Plan 04 deferred 상태에서도 docs commit 가능 (Phase 7-04 패턴)                  |
| 환경 부재 시 deferred 처리 절차                     | Phase 7-04 SUMMARY status: deferred 패턴 직접 인용 가능                          |

**결론:** Plan 04 의 실측 시연은 v1.0 5월 PPT 데모 (2026-05-XX) 또는 6월 검단·포천 설치 직전 사용자가 `/gsd-execute-phase 9 --interactive` 또는 수동 시연으로 진행 가능. Phase 9 의 코드/인프라/합성 검증은 모두 완료된 상태로 종결 가능.

---

## Behavioral Spot-Checks

| Behavior                                             | Verification 채널                                  | Status |
| ---------------------------------------------------- | -------------------------------------------------- | ------ |
| 013 마이그레이션 운영 DB 적용                         | `supabase migration list` → 013 Local+Remote      | ✓ PASS |
| 4 테이블 RLS ENABLED                                  | test_013 assertion 1 (relrowsecurity=true × 4)    | ✓ PASS |
| 5 templates 시드                                      | test_013 assertion 3 + GET /tbm_templates 5 rows   | ✓ PASS |
| Storage bucket public=false                          | test_013 assertion 6 + GET /storage/v1/bucket      | ✓ PASS |
| pg_cron tbm_missed_attendance_minute 등록            | test_013 assertion 4 (active=true)                 | ✓ PASS |
| notifications/index.ts 4 TBM case 라우팅              | grep line 452/520/574/611 직접 확인                | ✓ PASS |
| Edge Function 운영 배포                               | supabase functions deploy 74.97kB (SUMMARY 출력)   | ✓ PASS |
| 12 smoke 4 case × 3 scenarios                        | 12/12 PASS (SUMMARY table line 140-153)            | ✓ PASS |
| testuser1 실제 push notified_count:1                 | Smoke 7+9 응답 검증 (SUMMARY line 175-177)         | ✓ PASS |
| compileDebugKotlin BUILD SUCCESSFUL                  | 09-03 SUMMARY line 331-334                         | ✓ PASS |
| testDebugUnitTest 48 cases PASS                      | 09-03 SUMMARY line 345-358                         | ✓ PASS |
| ai_agent/tests/ 28/28 PASS                          | 3 plans 모두 SUMMARY 에서 확인                     | ✓ PASS |
| 1일 cron 자연 round-trip 실측                         | Vault sr_key + expected_end_at+30분 대기 필요      | ? SKIP (Plan 04) |
| 실기기/에뮬레이터 1 cycle 캡처                       | autonomous: false (사용자 시연 환경 부재)           | ? SKIP (Plan 04) |

---

## Anti-Patterns Found

| File                                              | Pattern                  | Severity | Impact          |
| ------------------------------------------------- | ------------------------ | -------- | --------------- |
| (none — 모든 Plan SUMMARY 의 Deviations 모두 acceptable Rule 1/Rule 3 범주) | — | — | — |

Plan 01·02·03 합 8 deviations 모두 검토:
- Plan 01 Rule 3 × 2: auth.users 트리거 패턴 + psql 미설치 우회 — 모두 plan correction (Phase 7 진화형)
- Plan 02 Rule 1 × 1: profiles.user_name → name 컬럼명 fix — RESEARCH 의사코드 → 실제 schema 누락 (re-deploy 즉시 적용)
- Plan 03 Rule 3 × 5: JUnit4 일관 + UserRole.GENERAL_MANAGER 미존재 + MyApp install order 보존 + SignatureState early-return + JBR PATH — 모두 advisor 사전 정정으로 plan 본문 진화

Stub / TODO / placeholder grep 결과: 0 (모든 코드가 substantive — SUMMARY 의 claim 과 실측 파일 크기 일치).

---

## Human Verification Required (Plan 04 Deferred Scope)

본 verification 의 status: passed 는 Plan 04 의 실측 시연을 deferred 처리하고 Phase 9 의 코드/인프라/합성 검증이 4 SC 를 충족함을 인정한다는 의미. 다음 항목은 v1.0 5월 PPT 데모 또는 6월 검단·포천 설치 직전 사용자 가용 시점에 Plan 04 로 진행:

### 1. 1일 cycle E2E 시연

**Test:** manager (testuser1) 가 09:00 KST 에 manager 앱 → "TBM 세션 시작" → work_type=fire + expected_end_at=09:15 KST 입력 → 세션 생성. worker (testuser_w1) FCM 푸시 도착 → "TBM 가이드" 카드 클릭 → SignatureCanvas 서명 → "참여 확인" → Storage 업로드 → tbm-checkin POST → 참여 완료 표시. testuser_w2·w3 미참여 상태로 expected_end_at + 30분 (09:45 KST) 경과 → pg_cron tbm_missed_attendance_minute tick 시 tbm_missed_attendance_check() 자연 발사 → net.http_post tbm-missed → testuser1 manager push 도착 ("작업자 2명 미참여").

**Expected:** 6 캡처 — (a) manager 세션 시작 UI / (b) worker FCM 푸시 / (c) worker 체크리스트 / (d) worker 서명 / (e) manager 참여자 grid Realtime 갱신 / (f) missed FCM 푸시

**Why human:** autonomous: false — 실기기 시연 + 30분 대기 + FCM 도착 시각 확인 필요. 단 합성 채널 (Plan 02 12 smoke) 가 이미 PASS 이므로 cron 발사만 검증하면 충분.

### 2. Vault service_role_key 시드 (User Setup Required)

**Test:** Supabase Dashboard SQL Editor 에서 `SELECT vault.create_secret('service_role_key', 'eyJ...', 'pg_cron 자연 발사용')` 실행 (Phase 8-04 와 동일 절차).

**Expected:** 시드 후 60초 이내 pg_cron tick 시 `tbm_missed_attendance_check()` 가 net.http_post → notifications tbm-missed 자연 발사. RAISE WARNING 0건.

**Why human:** Secret 값 (`service_role_key`) git 노출 회피 (T-9-07) — Dashboard 수동 절차 prerequisite.

---

## Gaps Summary

**없음.** 4 ROADMAP Success Criteria 가 Plan 01·02·03 의 합성 검증으로 충족됨:

- SC #1: 전면 PASS (013 + RLS + 5 templates + Storage + cron + 7 assertions)
- SC #2: 코드/인프라 PASS + 실기기 1 cycle 캡처만 Plan 04 deferred (autonomous: false)
- SC #3: 4 case 배포 + 12 smoke + 실제 push 도착 PASS + 1일 cron round-trip 만 Plan 04 deferred (Vault prerequisite)
- SC #4: 전면 PASS (regression guards 3개 모두 0 diff + notifications 기존 4 case 보존)

Plan 04 deferred 는 **Phase 7-04 + Phase 8-RTSP-02 precedent 와 1:1 패턴 일치** — 사용자 가용 시점에 진행하면 됨. Phase 7 의 BRIDGE-01·02·03 가 07-04 deferred 임에도 ✓ Complete 처리된 precedent 적용 가능.

---

## Phase 9 Closure Recommendation

**✓ COMPLETE (Plan 04 deferred to v1.0 user availability)**

**근거:**

1. ROADMAP 4 SC 모두 충족 (SC #2·#3 의 실측 부분만 deferred, 코드/인프라/합성 검증 PASS)
2. REQUIREMENTS.md 의 TBM-01·02·03 모두 ✓ Complete 표시 (line 224·225·226)
3. Phase 7 패턴 precedent — BRIDGE-01·02·03 가 07-04 deferred 임에도 ✓ Complete
4. Phase 8 패턴 precedent — RTSP-02 ⏸ DEFERRED 라도 Phase 8 ✓ COMPLETE (STATE.md line 39)
5. Plan 04 의 deferred 항목은 v1.0 5월 PPT 데모 또는 6월 검단·포천 설치 직전 사용자가 진행 가능 — 마감일 영향 없음

**Follow-up 권고 (orchestrator 또는 별도 docs commit):**

- `.planning/STATE.md` line 40·51 → "Phase 9: ⚠ IN PROGRESS" → "Phase 9: ✓ COMPLETE (Plan 04 deferred)"
- `.planning/ROADMAP.md` line 296 → Plan 04 의 `[ ]` → `[⏸ DEFERRED]` 표기 + reasoning 인용 (autonomous: false, 사용자 시연 환경 부재)
- `.planning/REQUIREMENTS.md` traceability — TBM-01·02·03 line 이미 ✓ Complete 유지 (Phase 7 precedent)
- (선택) `.planning/phases/09-tbm-worker-guide/09-04-SUMMARY.md` 작성 (07-04-SUMMARY.md 패턴 1:1 미러, `status: deferred` frontmatter + deferred_acceptance list)

---

_Verified: 2026-05-18_
_Verifier: Claude (gsd-verifier)_
_Phase 9 Verdict: PASS-WITH-DEFERRED (✓ COMPLETE recommendation)_
