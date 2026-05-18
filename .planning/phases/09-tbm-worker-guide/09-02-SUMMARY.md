---
phase: 09-tbm-worker-guide
plan: 02
subsystem: edge-function
tags: [supabase, edge-function, deno, fcm, send-push-to-users, action-routing, tbm, push-only, T-9-02, T-9-03, T-9-04, T-9-09, T-9-10]
requirements: [TBM-03]

# Dependency graph
requires:
  - phase: 04-watch-j2208a-pipeline
    provides: D-09 알림 전이 원칙 (push-only 회귀 가드 패턴 원천)
  - phase: 07-watch-app-bridge
    provides: notifications/index.ts case watch-pair (ownership 검증 패턴 미러) + watch-ack (idempotent 가드 패턴)
  - phase: 08-rtsp-camera
    provides: notifications/index.ts case camera-down / camera-recovered (sendPushToUsers plural 패턴 1:1 미러)
  - phase: 09-tbm-worker-guide-plan-01
    provides: 013_tbm_schema.sql 운영 DB 적용 (4 테이블 + 5 templates 시드 + UNIQUE 제약 + Storage 버킷)
provides:
  - notifications/index.ts case 'tbm-start' (관리자 → 세션 + checklist + 워커 push)
  - notifications/index.ts case 'tbm-checkin' (작업자 → 참여 ownership 검증 + idempotent)
  - notifications/index.ts case 'tbm-end' (관리자 → 종료 leader 검증)
  - notifications/index.ts case 'tbm-missed' (cron 호출 → missed worker push + Pitfall 9 dedup)
  - supabase functions deploy notifications 운영 배포 (script 74.97kB)
  - tests/smoke/tbm_{start,checkin,missed,end}.sh + tbm_all.sh orchestrator
  - D-09 push-only 회귀 가드 검증 (notifications BEFORE=AFTER, delta=0)
affects: [phase-9-plan-03, phase-9-plan-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "단일 orchestrator (tbm_all.sh) + 환경변수 SESSION_ID 전달 (advisor 권고 패턴 A)"
    - "D-09 회귀 가드 service_role + Prefer:count=exact + Range:0-0 + content-range 헤더 추출 (advisor 권고 — anon RLS narrow 회피)"

key-files:
  created:
    - tests/smoke/tbm_start.sh
    - tests/smoke/tbm_checkin.sh
    - tests/smoke/tbm_missed.sh
    - tests/smoke/tbm_end.sh
    - tests/smoke/tbm_all.sh
    - .planning/phases/09-tbm-worker-guide/09-02-SUMMARY.md
  modified:
    - supabase/functions/notifications/index.ts

key-decisions:
  - "C1 (push-only 일관) — 4 case 모두 notifications.insert() 부재. notifications row 변화 0 검증 (BEFORE=51 AFTER=51 delta=0)"
  - "advisor — orchestrator 패턴 A (단일 tbm_all.sh + SESSION_ID env) 채택 (B temp file 보다 cleaner)"
  - "advisor — cross-group fixture = 기존 group_id=2 manager younseu 재사용 (group_id=99 신규 시드 불필요, plan 의 testuser_other 보다 단순)"
  - "advisor — D-09 count 가드는 service_role 사용 (anon RLS narrow → 모두 0 반환 위험 회피)"
  - "advisor — Korean repo path 의 esm.sh transient 대비 deploy 1회 fallback retry 패턴 (실제 발생 X, 1차 시도 성공)"

patterns-established:
  - "Pattern: TBM 4 case action-routing 추가 — Phase 7·8 패턴 1:1 미러, 신규 helper 0건, 기존 _shared/fcm.ts:239 sendPushToUsers 재사용"
  - "Pattern: 12 smoke orchestrator with SESSION_ID handoff — tbm_start stdout 마지막 line 'SESSION_ID=<N>' emit → orchestrator export → 후속 sub-script 가 env 로 수신"

metrics:
  duration: 약 25분
  completed: 2026-05-18T07:00:00Z
---

# Phase 9 Plan 09-02: notifications 4 TBM cases + 12 smoke + D-09 회귀 가드 Summary

**One-liner:** notifications/index.ts 에 4 TBM case 추가 (tbm-start/checkin/end/missed) + supabase functions deploy notifications 운영 배포 (script 74.97kB) + 12 curl smoke ALL PASS (3 scenarios × 4 cases) + D-09 push-only 회귀 가드 (notifications row delta=0) + T-9-02·03·04·09 STRIDE mitigation 모두 검증.

## Tasks 실행 결과

### Task 1: notifications/index.ts 4 TBM case 추가 + deploy (커밋 `417c203`)

`supabase/functions/notifications/index.ts` 의 switch 마지막 (default 위) 에 4 case 추가, 215 lines insert. RESEARCH §Pattern 4 의 skeleton 그대로 미러 + Phase 7 watch-pair + Phase 8 camera-down 1:1 패턴.

#### (A) `case "tbm-start"` — 관리자 → 세션 생성 + checklist + worker push

4 단계:
1. `tbm_templates` 로부터 `work_type` 별 `checklist` (JSONB array) + `title` 조회. 미존재 → 400 `unknown work_type`.
2. `tbm_sessions` INSERT — UNIQUE `(group_id, session_date)` 충돌 시 `sErr.code === "23505"` 분기 → 409 `"이미 오늘 세션이 존재합니다"` (T-9-02 mitigation, Pitfall 5).
3. `tbm_checklists` bulk INSERT — `template.checklist.map((text, idx) => ({session_id, item_idx, item_text}))` (JSONB array order 보장, Pitfall 11).
4. group worker 전원 SELECT — `group_id=$group_id AND user_role IN ('worker','general_manager') AND user_id != $leader_user_id` → `sendPushToUsers` (plural, fcm.ts:239) with `data.type='tbm_alert', action_in_app='tbm-started'`.

응답: `{ok, session_id, checklist_count, notified_count}`.

#### (B) `case "tbm-checkin"` — 작업자 → 참여 + ownership 검증

3 단계 (T-9-03 spoofing 차단):
1. `tbm_sessions` SELECT + `ended_at IS NULL` 검증 → 404/410.
2. `profiles.group_id == session.group_id` 검증 (T-9-03 핵심) → 미일치 시 403 `"user not in session group"`. Phase 7 watch-pair T-7-03 mitigation 1:1 미러.
3. `tbm_participants` INSERT — UNIQUE `(session_id, user_id)` 23505 catch → 200 idempotent (existing row 의 `participant_id` + `signed_at` 재조회 후 반환, T-9-10 응답 fabrication accept).

응답: `{ok, participant_id, signed_at, idempotent?}`.

#### (C) `case "tbm-end"` — 관리자 → 종료 + leader 검증

T-9-04 mitigation: `.eq("leader_user_id", $leader_user_id).is("ended_at", null)` 매치 0 rows → 404 `"session not found or already ended or not led by user"`. 정상 매치 시 `ended_at = new Date().toISOString()` (서버측 시계, T-7-05 clock spoofing 차단).

응답: `{ok, ended_at, participant_count}`.

#### (D) `case "tbm-missed"` — cron 호출 (pg_cron tbm_missed_attendance_minute)

4 단계 (Pitfall 9 dedup):
1. `tbm_sessions` SELECT (defensive — cron 이 이미 `ended_at IS NULL` 필터).
2. group worker 전체 SELECT (leader 제외).
3. `tbm_participants` 의 `user_id` Set 계산 → groupWorkers 중 미참여 = `missedIds`.
4. `recipientIds = [...missedIds, leader_user_id]` → `sendPushToUsers` (Set dedup, fcm.ts:253). data.type='tbm_alert', action_in_app='tbm-missed'.

응답: `{ok, missed_count, notified_count}`.

#### Deploy + Regression Guard

```
$ supabase functions deploy notifications
Bundling Function: notifications
Deploying Function: notifications (script size: 74.93kB)  ← 1차 (Task 1)
Deploying Function: notifications (script size: 74.97kB)  ← 2차 (Rule 1 column fix 후)
Deployed Functions on project xbjqxnvemcqubjfflain: notifications
```

| 회귀 가드 | 결과 |
|----------|------|
| `case "watch-ack"` 보존 | 1 (변경 0) |
| `case "watch-pair"` 보존 | 1 (변경 0) |
| `case "camera-down"` 보존 | 1 (변경 0) |
| `case "camera-recovered"` 보존 | 1 (변경 0) |
| `case "tbm-*"` 4개 | 4 (start/checkin/end/missed) |
| `sendPushToUsers` 호출 count | 10 (기존 8 + tbm-start + tbm-missed 2) |
| `group_id !== session.group_id` count | 1 (tbm-checkin only, T-9-03) |
| TBM cases 내부 `notifications.insert` | 0 (D-09 push-only 회귀 가드 통과) |

### Task 2: 12 curl smoke + D-09 회귀 가드 (커밋 `aeb6ddf`)

#### Artifacts

- `tests/smoke/tbm_start.sh` (3 scenarios — SESSION_ID emit)
- `tests/smoke/tbm_checkin.sh` (3 scenarios — SESSION_ID env 수신)
- `tests/smoke/tbm_missed.sh` (3 scenarios — w2·w3 prep checkin 내장)
- `tests/smoke/tbm_end.sh` (3 scenarios — session 종료)
- `tests/smoke/tbm_all.sh` — orchestrator (cleanup + BEFORE count + 4 sub-scripts 순차 + AFTER count + delta 검증)

#### 12 Smoke 결과 표 (ALL GREEN)

| # | Action | Scenario | HTTP | Response (발췌) | 검증 |
|---|--------|----------|------|-----------------|------|
| 1 | tbm-start | 정상 (testuser1 + group=1 + electric + +15min) | **200** | `{"ok":true,"session_id":3,"checklist_count":5,"notified_count":0}` | PASS — session_id emit |
| 2 | tbm-start | UNIQUE 23505 → 409 (같은 day 재호출) | **409** | `{"error":"이미 오늘 세션이 존재합니다"}` | PASS — T-9-02 mitigation |
| 3 | tbm-start | payload 누락 (expected_end_at 생략) | **400** | `{"error":"... are required"}` | PASS |
| 4 | tbm-checkin | 정상 (SESSION_ID=3, user_id=testuser_w1) | **200** | `{"ok":true,"participant_id":3,"signed_at":"...+00:00"}` | PASS |
| 5 | tbm-checkin | ownership 위반 (user_id=younseu, group_id=2) | **403** | `{"error":"user not in session group"}` | PASS — T-9-03 mitigation |
| 6 | tbm-checkin | idempotent 재호출 (w1) | **200** | `{"ok":true,"participant_id":3,...,"idempotent":true}` | PASS — Pitfall 5 |
| 7 | tbm-missed | 정상 (w1 checked, w2·w3 missed) | **200** | `{"ok":true,"missed_count":2,"notified_count":1}` | PASS — leader push만 (w2·w3 fcm_token null) |
| 8 | tbm-missed | payload 누락 (session_id 생략) | **400** | `{"error":"... are required"}` | PASS |
| 9 | tbm-missed | leader-only (w2·w3 prep checkin → 0 missed) | **200** | `{"ok":true,"missed_count":0,"notified_count":1}` | PASS — Pitfall 9 dedup (leader 중복 0) |
| 10 | tbm-end | leader 불일치 (w1 시도) | **404** | `{"error":"... not led by user"}` | PASS — T-9-04 mitigation |
| 11 | tbm-end | payload 누락 (leader_user_id 생략) | **400** | `{"error":"... are required"}` | PASS |
| 12 | tbm-end | 정상 (testuser1 종료) | **200** | `{"ok":true,"ended_at":"2026-05-18T06:45:04.555+00:00","participant_count":3}` | PASS |

12/12 PASS. orchestrator stdout 마지막 line: `TBM-02 ALL GREEN: 12 smoke PASS + D-09 delta=0`.

#### D-09 회귀 가드 (research C1 검증)

```
[0b] D-09 baseline: notifications row count BEFORE (service_role)
  notifications BEFORE=51
... (12 smoke 실행)
[5] D-09 가드: notifications row count AFTER (service_role)
  notifications BEFORE=51  AFTER=51  delta=0
```

**delta=0 검증 통과**. 4 case 모두 push-only — 상태 전이 책임은 `tbm_sessions` (UNIQUE / ended_at) + `tbm_participants` (UNIQUE) + `missed_alert_at` (cron) 이 가짐. notifications 테이블 0 변화 = Phase 8 03 의 D-09 회귀 가드 패턴 1:1 미러 (08-03 SUMMARY 패턴).

**advisor 권고 적용**: anon SELECT 시 RLS narrow 로 모두 0 반환 → false-pass 위험. service_role + `Prefer: count=exact` + `Range: 0-0` + `content-range` 헤더 추출 패턴으로 진짜 count 가져옴.

#### Pitfall 9 (leader dedup) 검증

Smoke 9 (tbm-missed leader-only): `missed_count=0` + `notified_count=1` = `recipients = [] + leader_user_id` → `sendPushToUsers([testuser1])` → Set unique 1명 → push 1건. leader 가 missed list 에 중복 등장 0 (D-04 SQL 의 `neq("user_id", leader_user_id)` 가 자연 제외 + Set 가드 이중 안전).

#### testuser1 실제 push 검증

Smoke 7 + Smoke 9 모두 `notified_count: 1` — testuser1 manager 의 `fcm_token` 시드되어 있어 실제 FCM 도착 (Phase 8 03 의 sent:1 검증 패턴 일관). w1·w2·w3 worker 는 fcm_token null (`seed_tbm_demo.py` 의 PoC 시드 — auth.users 채널만 채움, FCM 미등록 의도된 동작).

#### SC #4 회귀 가드 — ai_agent/tests/ 28/28 PASS

```
$ cd ai_agent && python -m pytest tests/ -q
............................                                             [100%]
28 passed in 5.75s
```

Edge Function (Deno) 변경은 Python `ai_agent/` 코드 0 변경 — SC #4 (4 detector 진입점 zero-change) 보존.

## Threat Mitigations

| Threat ID | Disposition | Mitigation 검증 |
|-----------|-------------|-----------------|
| T-9-02 (UNIQUE bypass) | mitigate | DB-level `CONSTRAINT tbm_sessions_group_date_uq` + Edge Function `sErr.code === "23505" → 409`. Smoke 2: HTTP 409 + "이미 오늘 세션" message. |
| T-9-03 (cross-group spoofing) | mitigate | tbm-checkin `if (profile.group_id !== session.group_id) return err(403)`. Smoke 5: `user_id=younseu` (group_id=2) + SESSION_ID (group_id=1) → HTTP 403 "user not in session group". |
| T-9-04 (non-leader tbm-end) | mitigate | `.eq("leader_user_id", $leader_user_id) + .is("ended_at", null)` → 매치 0 → 404. Smoke 10: `leader_user_id=testuser_w1` (worker, not leader) → HTTP 404. |
| T-9-09 (leader push 중복) | mitigate | sendPushToUsers 내부 `[...new Set(userIds)]` (fcm.ts:253) + D-04 SQL `neq leader_user_id` 자연 제외. Smoke 9 leader-only: missed=0 + notified=1 (leader 1명) 검증. |
| T-9-10 (idempotent 거짓 응답) | accept | tbm-checkin 23505 catch path 가 DB 의 existing row SELECT 후 participant_id + signed_at 반환 — Smoke 6 응답에 actual values (participant_id:3, 1차 signed_at 그대로) 확인. |
| T-9-11 (DoS rate limit 부재) | accept v1.0 | Supabase Edge Function 1000 req/s 기본 limit 충분. v1.1 별도 throttle. |

## Deviations from Plan

### Rule 1 — bug fix × 1

**1. [Rule 1 - Bug] tbm-missed case 의 profiles.select("user_id, user_name") → "user_id, name"**

- **Found during:** Task 2 Smoke 7 (tbm-missed 첫 정상 호출)
- **Issue:** PostgREST 응답 `{"error":"column profiles.user_name does not exist"}` HTTP 500. 실제 profiles 컬럼명은 `name` (Plan 09-01 의 seed_tbm_demo.py 가 UPSERT 한 컬럼명 확인 시 명확). RESEARCH §Pattern 4 line 622 는 의사코드 변수명을 사용했으나 실제 DB 컬럼명 검증을 빠뜨림.
- **Fix:** `select("user_id, user_name")` → `select("user_id, name")` 1자 정정. (TBM-MISSED case 외부에는 영향 0 — `name` 변수는 본 case 내에서 미사용, 단순 SELECT projection 정정.)
- **Files modified:** `supabase/functions/notifications/index.ts` (line 184 부근)
- **Commit:** `aeb6ddf` (Task 2 commit 에 동시 포함)
- **Re-deploy:** `supabase functions deploy notifications` 1회 재배포 (script 74.93kB → 74.97kB, +0.04kB 주석 추가분).
- **근거:** RESEARCH skeleton 의사코드 → 실제 schema 매핑 시 누락. 운영 DB 의 profiles 정확한 컬럼명은 `name` (seed_tbm_demo.py 가 사용한 키와 일치). 본 case 의 missed worker push 본문에 name 미사용이므로 SELECT 만 정정.

### 없음 (Rule 2 / Rule 3)

- Rule 2 보안 보강: plan 의 ownership/leader/UNIQUE 가드는 모두 사전 적용 (research C1·C2·C3 amendment + Phase 7·8 미러 1:1).
- Rule 3 환경/correction: plan 본문이 정확하여 실행 단계 fix 0건. 단 advisor 권고 3건 사전 흡수 — (a) orchestrator 패턴 A 채택, (b) cross-group fixture 기존 younseu 재사용 (group_id=99 신규 시드 회피), (c) D-09 count 가드 service_role 채택 (anon RLS 회피).

## Pitfalls 검증

| Pitfall | 검증 결과 |
|---------|----------|
| Pitfall 5 (UNIQUE 23505 catch) | tbm-start Smoke 2 (HTTP 409) + tbm-checkin Smoke 6 (HTTP 200 idempotent) — 2 분기 모두 정확히 처리 |
| Pitfall 9 (leader dedup) | tbm-missed Smoke 9 leader-only: missed=0 + notified=1 (Set 자연 dedup + D-04 SQL leader 제외) |
| Pitfall 11 (JSONB array order) | tbm-start Smoke 1 checklist_count=5 — `tbm_templates.checklist.map((text, idx) => ...)` 가 array order 그대로 item_idx 매핑 |

## Self-Check: PASSED

**Files created:**
- `tests/smoke/tbm_start.sh` — FOUND
- `tests/smoke/tbm_checkin.sh` — FOUND
- `tests/smoke/tbm_missed.sh` — FOUND
- `tests/smoke/tbm_end.sh` — FOUND
- `tests/smoke/tbm_all.sh` — FOUND
- `.planning/phases/09-tbm-worker-guide/09-02-SUMMARY.md` — 본 파일

**Files modified:**
- `supabase/functions/notifications/index.ts` — FOUND (215 lines added + 1 line column fix)

**Commits:**
- `417c203` — feat(09-02): notifications 4 TBM cases + deploy (Task 1)
- `aeb6ddf` — test(09-02): 12 curl smoke + D-09 회귀 가드 + Rule 1 fix profiles.name (Task 2)
- (next) docs(09-02): metadata commit

**Edge Function:**
- `supabase functions deploy notifications` 재배포 성공 (script 74.97kB)
- 4 TBM case + 4 기존 case (watch-ack/watch-pair/camera-down/camera-recovered) + 5 기존 case (list/mark_read/send_group/send_individual/watch-alert) 모두 운영 적용

**Verification:**
- 12/12 smoke PASS (4 cases × 3 scenarios)
- D-09 회귀 가드 delta=0 (BEFORE=51 AFTER=51)
- ai_agent/tests/ 28/28 PASS (SC #4 zero-change)
- T-9-02·03·04·09 mitigation 모두 smoke 로 직접 검증
- Pitfall 5·9·11 모두 검증

## Vault sr_key prerequisite 인지

본 plan 의 4 case 는 모두 anon key 로 직접 호출되어 검증 (12 smoke). pg_cron 의 `tbm_missed_attendance_check` 가 운영 환경에서 자연 발사하려면 Plan 09-01 SUMMARY 의 Vault `service_role_key` 시드 prerequisite 충족 시 자동 동작 — Phase 8 04 의 step 10 deferred 와 동일 패턴. 본 plan 의 12 smoke 와 무관 (anon key 직접 POST 로 검증).

---

*Plan 09-02 — notifications 4 TBM cases + 12 smoke + D-09 회귀 가드 완료*
*Phase 9 Wave 2 절반 완료 (∥ Plan 03 Android UI 가 평행 진행 가능)*
