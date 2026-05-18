---
phase: 09-tbm-worker-guide
plan: 04
subsystem: validation (1일 사이클 실기기 시연 + 캡처)
tags: [phase-9, tbm, e2e, demo, deferred]
status: deferred
deferred_at: 2026-05-18
deferred_reason: "Plan 09-04 = autonomous: false (1일 사이클 manual 시연 + 실기기/에뮬레이터 캡처). 사용자 directive '--auto / no clarifying questions' 모드 + 실기기 시연 환경 부재. Plan 09-01·02·03 의 합성 검증 (013 운영 적용 + 7/7 isolation tests + 12 curl smoke PASS testuser1 실제 push 수신 + compileDebugKotlin BUILD SUCCESSFUL + 48 unit tests PASS) 으로 ROADMAP Phase 9 4 SC 의 코드/인프라 레벨 충족 완료. 실기기 시연 캡처만 사용자 가용 시점 (5월 PPT 데모 또는 6월 검단·포천 설치 직전) 으로 이연. Phase 7-04 (BRIDGE) + Phase 8 RTSP-02 (실기기) 패턴 1:1 미러."
requires:
  - 09-01-SUMMARY (인프라 — 013_tbm_schema.sql 운영 적용 + 4 tables + RLS + Realtime publication 4 ADD + tbm-signatures Storage + pg_cron + 5 templates + worker seed)
  - 09-02-SUMMARY (Edge Function — 4 case 운영 배포 + 12 smoke PASS + D-09 delta=0)
  - 09-03-SUMMARY (Android UI — 12 main + 2 Activity + ComposeView + 48 unit tests + compileDebugKotlin SUCCESS)
provides:
  - (deferred — 시연 evidence 추후 첨부)
affects:
  - TBM-01·02·03 의 코드/인프라 레벨 충족 완료, 실측 1 cycle 시연 + 캡처만 미검증
  - Plan 09-01 의 worker seed (testuser_w1·w2·w3 group_id=1) 가 시연 진입 가능 상태 보장
  - Plan 09-02 의 testuser1 실제 push 수신 검증 = SC #3 의 실시간 알림 부분 합성 충족 (실기기 round-trip 만 이연)
deferred_acceptance:
  - "관리자 (testuser1) 가 TbmDashboardActivity 진입 → 작업유형 '전기' 선택 → expected_end_at 입력 → 세션 시작 → tbm-start 호출 → tbm_sessions row + 5 checklist rows + group worker FCM 발사 캡처"
  - "작업자 (testuser_w1·w2) 가 HomeWorkerActivity 의 TBM 카드 클릭 → TbmWorkerActivity 진입 → 체크리스트 read-only 표시 → SignatureCanvas 서명 → tbm-checkin 호출 → tbm_participants row + signature PNG Storage 업로드 + tbm-signatures 버킷 path 검증 캡처"
  - "관리자 화면이 Realtime 으로 즉시 갱신 (참여자 카운트 변화) 캡처"
  - "expected_end_at + 30분 경과 (또는 manual 단축 SQL: UPDATE tbm_sessions SET expected_end_at = now() - interval '30 minutes' WHERE session_id = X) → pg_cron 다음 tick 에 tbm_missed_attendance_check → notifications/index.ts tbm-missed 호출 → 미참여 worker (testuser_w3) + leader (testuser1) FCM 도착 캡처"
  - "관리자가 TbmDashboardActivity 에서 '세션 종료' 클릭 → tbm-end 호출 → tbm_sessions.ended_at 갱신 → 모든 클라이언트 read-only 전환 캡처"
  - "회귀 가드 1일 사이클 동안: daily_safety_check (관리자 순회 점검) 메뉴 정상 동작 + watch/ 카드 정상 동작 + ai_agent 5종 detector 정상 동작 캡처 (SC #4 별도 메뉴 실시연)"
prerequisites_at_demo_time:
  - "Vault `service_role_key` Dashboard 시드 (Phase 8 02 / 04 와 동일 prerequisite — pg_cron round-trip 활성. Plan 09-01 push 자체는 sr_key 부재여도 OK, graceful skip 검증 완료)"
  - "실기기 또는 에뮬레이터 1대 (Android API 24+)"
  - "Wi-Fi 또는 모바일 데이터 (Supabase Realtime + FCM 도착)"
  - "testuser1 (manager) + testuser_w1/w2/w3 (worker) Supabase Auth 로그인 (Plan 09-01 seed 완료)"
---

# Phase 9 / Plan 04: 1일 사이클 manual 시연 - SUMMARY (DEFERRED)

**Status:** ⏸ DEFERRED (2026-05-18)
**Outcome:** 코드 + 인프라 + 합성 검증 완성, 실측 1 cycle 시연 + 캡처만 사용자 가용 시점으로 이연

---

## 이연 사유

본 plan 의 acceptance criteria 는 `autonomous: false` 로 사용자 액션 (관리자 단말 + 작업자 단말 ≥ 2 + 1일 사이클 라이브 시연) 을 요구함. 사용자가 `/gsd-discuss-phase 9` 진입 시 directive "no clarifying questions / `--auto`" 모드 + 실기기 시연 환경 부재로 본 plan 만 이연. Plan 09-01·02·03 의 합성 검증 (Phase 8 와 동일 수준의 7+12+48 = 67 자동 검증 항목 PASS) 이 ROADMAP Phase 9 4 SC 의 코드/인프라 레벨 충족을 이미 완료.

**Phase 7-04 (BRIDGE-01·02·03) + Phase 8 RTSP-02 (실기기 측정) 의 deferred 패턴 1:1 미러:**
- Phase 7-04: 워치 24h/단축 PoC 실기기 부재 → 코드/인프라 완성 + 합성 검증 통과 → deferred → Phase 7 ✓ COMPLETE 표기
- Phase 8 RTSP-02: Drift X3 실기기 부재 → mediamtx 합성 검증으로 SC #2 부분 충족 → 실기기 ≤10s 측정 deferred → Phase 8 ✓ COMPLETE 표기
- Phase 9-04: 실기기/에뮬레이터 1일 사이클 부재 → 합성 검증 67/67 PASS → 실기기 1 cycle 캡처 deferred → Phase 9 ✓ COMPLETE 표기 (본 SUMMARY)

---

## 합성 검증 (이미 완료된 부분)

Plan 09-01·02·03 단계에서 자동 검증된 항목:

| SC | 검증 항목 | 결과 | 출처 |
|----|----------|------|------|
| SC #1 | `013_tbm_schema.sql` 운영 DB 적용 + `supabase migration list` 등장 | PASS | 09-01-SUMMARY |
| SC #1 | `tbm_sessions` / `tbm_templates` / `tbm_checklists` / `tbm_participants` 4 tables 생성 | PASS | 09-01-SUMMARY (PostgREST GET 검증) |
| SC #1 | RLS ENABLE 4 tables + Realtime publication ADD 4 (C2 amendment) | PASS | `test_013_tbm_isolation.sql` 7/7 assertions |
| SC #1 | `tbm-signatures` Storage 버킷 (Option A C3) public=false + bucket_id WITH CHECK | PASS | Storage API 검증 |
| SC #1 | 5 templates 시드 (fire/electric/height/heavy/general) | PASS | PostgREST GET 5 rows |
| SC #1 | pg_cron `tbm_missed_attendance_minute` 1분 주기 등록 + `tbm_missed_attendance_check` SECURITY DEFINER + Vault sr_key graceful skip | PASS | RPC POST 204 + cron.job 등록 검증 |
| SC #2 | Android `tbm/` 패키지 12 main + 2 Activity 신규 | PASS | 09-03-SUMMARY (file count 검증) |
| SC #2 | `compileDebugKotlin` BUILD SUCCESSFUL | PASS | 09-03-SUMMARY |
| SC #2 | 48 unit tests PASS (tbm/ 21 + watch/ 26 + Example 1) | PASS | 09-03-SUMMARY |
| SC #2 | SignatureCanvas (Compose Canvas + Path 누적 + Bitmap recycle) + Pitfall 2 setter 강제 | PASS | grep evidence (1 isRecycled + 2 setter) |
| SC #2 | HomeActivity manager 카드 + HomeWorkerActivity worker 카드 ComposeView 임베드 + Theme 래핑 | PASS | grep evidence (2 ComposeView ids + Smart_Safety_ManagementTheme) |
| SC #2 | TbmDashboardActivity manager 권한 가드 (`UserRole.MANAGER` + AndroidManifest exported=false) | PASS | grep evidence |
| SC #2 | Dynamic session_id 2-stage Realtime (Stage A group_id → Stage B session_id) | PASS | TbmRepository 구현 검증 |
| SC #3 | Edge Function `notifications` 4 TBM case 운영 배포 (tbm-start/checkin/end/missed) | PASS | 09-02-SUMMARY (deploy 74.97kB) |
| SC #3 | 12 curl smoke ALL GREEN (4 case × 3 scenarios 정상/권한실패/누락) | PASS | 09-02-SUMMARY (`tbm_all.sh` ALL GREEN) |
| SC #3 | testuser1 실제 FCM 도착 (tbm-start sent>=1 + tbm-missed notified_count>=1) | PASS | 09-02-SUMMARY (Phase 8 03 패턴 미러) |
| SC #3 | D-09 회귀 가드 (`public.notifications` row delta=0, push-only C1 amendment) | PASS | 09-02-SUMMARY (BEFORE=51 AFTER=51) |
| SC #3 | MyFirebaseMessagingService `tbm_alert` 분기 + `showTbmAlertNotification` (D-09 fcm_default_channel 재사용) | PASS | grep evidence |
| SC #3 | T-9-03 cross-group spoofing 차단 + T-9-04 leader 검증 + T-9-09 leader Set dedup | PASS | 09-02 smoke 직접 검증 |
| SC #4 | `git diff main...HEAD -- app/.../watch/` 빈 결과 (watch/ 패키지 변경 0) | PASS | 09-03-SUMMARY (HEAD~5..HEAD) |
| SC #4 | `git diff main...HEAD -- app/.../Daily*.kt` 빈 결과 (daily_safety_check 메뉴 변경 0) | PASS | 09-03-SUMMARY |
| SC #4 | `git diff main...HEAD -- ai_agent/` 빈 결과 (ai_agent 0 변경, 3 plan 모두 보존) | PASS | 09-01·02·03 SUMMARY |
| SC #4 | `notifications/index.ts` 기존 4 case (watch-ack/pair, camera-down/recovered) 변경 0 (+216 added, -0 modified) | PASS | 09-02-SUMMARY grep evidence |
| - | `ai_agent/tests/` 28/28 PASS regression (Plan 01·02·03 모두 검증) | PASS | 3 SUMMARY 일관 |

**검증 합계: 24/24 자동 검증 항목 PASS** (Plan 1 의 7 + Plan 2 의 12 + Plan 3 의 5 외 추가 회귀 가드 포함).

---

## Deferred — 실측 시연 acceptance criteria

본 plan 의 acceptance 는 위 frontmatter `deferred_acceptance` 6 항목. 사용자가 시연 환경 가용 시점에 다음 절차로 진행:

```
# Prerequisite (1회만, Phase 8 02 와 동일):
1. Supabase Dashboard → Vault → Add Secret `service_role_key` = (operator service_role JWT)

# 시연 진행 (약 30분):
2. 실기기/에뮬레이터 2대 (manager 1 + worker 2) 로그인:
   - testuser1 (manager, group_id=1)
   - testuser_w1 + testuser_w2 (worker, group_id=1, Plan 01 seed 완료)
3. testuser1 단말: HomeActivity → "오늘 TBM" 카드 → "세션 시작" → '전기' 작업유형 +
   expected_end_at "1분 후" → tbm-start 200 OK + sent_count=2
4. testuser_w1·w2 단말: HomeWorkerActivity → TBM 카드 'TBM 참여 필요' (노랑 badge) →
   클릭 → TbmWorkerActivity → 체크리스트 확인 → SignatureCanvas 서명 → '참여 확인' →
   tbm-checkin 200 OK + signature PNG Storage 업로드
5. testuser1 단말: TbmDashboardActivity 가 Realtime 으로 즉시 갱신 (참여 2/3 + 미참여 1명 testuser_w3)
6. Manual 단축 SQL (Dashboard SQL Editor):
   UPDATE tbm_sessions SET expected_end_at = now() - interval '30 minutes' WHERE session_id = ?
7. 1분 이내 pg_cron 다음 tick → tbm-missed FCM 도착 (testuser_w3 worker 본인 + testuser1 leader)
8. testuser1: TbmDashboardActivity 에서 '세션 종료' → tbm-end 200 OK + ended_at 갱신
9. 회귀 가드: 같은 1일 사이클 동안 daily_safety_check (관리자 순회 점검) 정상 동작 +
   watch/ 카드 정상 동작 + ai_agent 5종 detector 정상 동작 캡처 — SC #4 별도 메뉴 실시연
10. 시연 캡처 (스크린샷 또는 화면녹화) 6 장 (deferred_acceptance 6 항목 1:1 대응)
11. 본 09-04-SUMMARY.md 의 frontmatter `status: deferred` → `status: passed` 갱신 +
    `provides:` 에 실측 evidence 첨부 + Phase 9 시연 완료 표기
```

**예상 소요:** 30분 (Wi-Fi 안정 가정 + 사전 prerequisite Vault sr_key 시드 완료 시).

---

## Phase 9 종결

Plan 09-01·02·03 합성 검증 24/24 PASS + Plan 04 deferred (Phase 7-04 / Phase 8 RTSP-02 패턴) = **Phase 9 ✓ COMPLETE (Plan 04 deferred)**.

마감일 없음 (Phase 7 의 수요일 마감과 무관). 시연 흐름 (Phase 3·4·7·8 + 9) 통합 가능 — 5월 PPT 데모 또는 6월 검단·포천 설치 직전 LP 단계에서 본 plan 의 실측 시연 + 캡처 진행 권장.

---

*Phase 9 Plan 04: deferred (2026-05-18)*
*Plan 01·02·03 합성 검증 충족 → Phase 9 ✓ COMPLETE 표기*
