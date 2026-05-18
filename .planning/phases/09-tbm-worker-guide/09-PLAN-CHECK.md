# Phase 9 Plan Check - TBM 현장 작업자 가이드

**Verdict:** PASS-WITH-WARNINGS
**Date:** 2026-05-18
**Plans verified:** 4 (09-01, 09-02, 09-03, 09-04)
**Checker:** gsd-plan-checker (goal-backward verification, FORCE adversarial)

---

## Dimension Verdicts

| # | Dimension | Verdict | Notes |
|---|-----------|---------|-------|
| 1 | Goal Achievement (4 SC) | PASS | SC #1, #2, #3, #4 모두 4 plans 합성으로 충족 가능 |
| 2 | Requirement Coverage (TBM-01·02·03) | PASS | Plan 01 [TBM-01] / Plan 02 [TBM-03] / Plan 03 [TBM-02] / Plan 04 [TBM-01·02·03] |
| 3 | CONTEXT.md Decisions (D-01~D-09 + C1·C2·C3) | PASS | 9 결정 + 3 amendment 모두 plan 본문 mapping |
| 4 | Wave / Dependency / Autonomy | PASS | Plan 01 wave1, Plan 02·03 wave2 parallel (zero file overlap), Plan 04 wave3 autonomous false |
| 5 | Scope Sanity (Anti-Shallow) | WARN | Plan 03 = 5 tasks + 27 files 두 threshold 초과. Phase 7-03 precedent 보다 큼 |
| 6 | Schema Push Gate (Plan 01) | PASS | Plan 01 Task 2 BLOCKING supabase db push 명시 + tags blocking |
| 7 | Threat Model | PASS | T-9-01·02·03·04·05·06 모두 분산 + T-9-07~17 보강 |
| 8 | Validation Architecture (Nyquist) | BLOCKER | Plan 01 Task 2 verify line 543 grep underscore 가 test_013 본문 hyphen 와 불일치 |
| 9 | Plan Quality (Phase 7·8 baseline) | PASS | must_haves user-observable + artifacts + key_links 모두 Phase 8 수준 |
| 10 | Regression Guards (SC #4) | PASS | watch/ git diff 0 + daily_safety_check 무변경 + Edge Fn 기존 4 case 무변경 + ai_agent 0 변경 |
| 11 | Research Resolution (#1602) | BLOCKER | RESEARCH.md line 1155 Open Questions 가 (RESOLVED) suffix 없음 |
| 12 | CLAUDE.md Compliance | SKIPPED | ./CLAUDE.md 부재 (RESEARCH 명시) |
| 13 | Cross-Plan Data Contracts | PASS | tbm_sessions / tbm_participants 공유. Plan 01 schema + Plan 02 INSERT + Plan 03 SELECT. 변환 충돌 0 |

---

## D-01~D-09 + C1·C2·C3 매핑표

| 결정 | Plan | Section | 충족 evidence |
|-----|------|---------|--------------|
| D-01 (4 tables + RLS + Realtime + 5 templates) | 09-01 | Task 1 ABCE | 4 CREATE TABLE + RLS ENABLE 4 + ALTER PUBLICATION + 5 templates INSERT |
| D-02 (Manager-led + UNIQUE) | 09-02 | Task 1 A tbm-start | sErr.code 23505 -> 409 Pitfall 5 |
| D-03 (SignatureCanvas + tbm-signatures) | 09-01 + 09-03 | 01 Task 1 D + 03 Task 2·3 | bucket public=false + Option A + storage-kt 2.2.0 + Compose Canvas |
| D-04 (missed worker SQL) | 09-01 + 09-02 | 01 Task 1 F + 02 Task 1 D | tbm_missed_attendance_check + Edge Fn missed worker filter |
| D-05 (pg_cron 1min + 30min + missed_alert_at) | 09-01 | Task 1 FG | expected_end_at + interval 30 minutes + missed_alert_at IS NULL |
| D-06 (HomeActivity + TbmDashboardActivity) | 09-03 | Task 4 | TbmDashboardActivity manager guard + setupTbmDashboardCard ComposeView |
| D-07 (HomeWorkerActivity + TbmWorkerActivity) | 09-03 | Task 3·4 | TbmWorkerCardComposable 4 states + TbmWorkerActivity |
| D-08 (Edge Function 4 case) | 09-02 | Task 1 ABCD | tbm-start / tbm-checkin / tbm-end / tbm-missed |
| D-09 (fcm_default_channel + data.type=tbm_alert) | 09-02 + 09-03 | 02 must_haves + 03 Task 5 | data.type=tbm_alert + MyFirebaseMessagingService branch |
| C1 (push-only, notifications insert 0) | 09-02 | Task 2 step 6 | notifications row delta = 0 + grep notifications.insert = 0 |
| C2 (4 channels publication) | 09-01 | Task 1 C | ALTER PUBLICATION supabase_realtime ADD TABLE 4 tables |
| C3 (Storage Option A) | 09-01 | Task 1 D | CREATE POLICY tbm_signatures_insert_anon + foldername regex |

12/12 결정 + amendment 모두 plan 본문에 명시 매핑됨. **스코프 reduction 0건** — 모든 결정 full 충족.

---

## Critical Findings

### BLOCKERS (must fix before execution)

#### BLOCKER 1 — Plan 09-01 Task 2 verify grep bug (Dimension 8)

**문제:**

- Plan 09-01 line 543 의 automated verify 가 `grep -c tbm_signatures` (underscore) 를 tests/sql/test_013_tbm_isolation.sql 에 적용
- test_013 본문 (Plan 09-01 line 517-522) 는 `tbm-signatures` (hyphen) 만 사용
- `grep -c` 0 반환 -> exit code 1 -> AND chain fail -> 자동 verify 항상 실패

**증거:**

- Line 543 verify: `grep -c tbm_signatures tests/sql/test_013_tbm_isolation.sql`
- Line 517 test body: `-- (f) tbm-signatures bucket 존재 검증`
- Line 519: `IF NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id=tbm-signatures AND public=false)`

**Fix:** Plan 09-01 line 543 의 `grep -c tbm_signatures` 를 `grep -c tbm-signatures` (hyphen) 으로 1글자 변경

**severity:** blocker (Nyquist Dimension 8a — automated verify 가 항상 fail)

---

#### BLOCKER 2 — RESEARCH.md Open Questions 미해결 표기 (Dimension 11)

**문제:**

- RESEARCH.md line 1155 `## Open Questions` 가 canonical `(RESOLVED)` suffix 없음
- 5 questions 모두 inline RESOLVED 마커 없음 (권장 prefix 만 등장)
- Dimension 11 엄격 적용 시 fail

**증거:**

- Line 1155: `## Open Questions` — (RESOLVED) suffix 부재
- grep -c RESOLVED 09-RESEARCH.md = 0
- 각 question 의 권장 prefix 가 canonical marker 가 아님

**참고:** 각 question 의 권장 답변은 실제 의사결정 완료를 반영 (예 v1.0 = Option A 채택) — 내용상 resolved 이지만 형식만 미준수

**Fix:** RESEARCH.md line 1155 heading 을 `## Open Questions (RESOLVED)` 로 변경 + 각 question 의 권장 을 RESOLVED 로 변경. 콘텐츠 변경 0, 마커만 추가

**severity:** blocker (Dimension 11 정의대로 적용 시)

---

### WARNINGS (should fix, but execution can proceed)

#### WARNING 1 — Plan 09-03 scope 상한 초과 (Dimension 5)

**문제:**

- Plan 09-03 = **5 tasks + 27 files_modified**
- Dimension 5 thresholds: tasks 5+ = blocker, files 15+ = blocker
- Phase 7-03 precedent (3 tasks + 21 files, COMPLETE shipped) 보다 +2 tasks +6 files 큼

**완화 요인:**

- 작업 성격이 mechanical mirror (Phase 7 watch/ 9 main + 4 test 를 tbm/ 13 main + 4 test 1:1 mirror) + SignatureCanvas 단일 신규 + 2 validator
- Phase 7-03 precedent 존재 (07-03-SUMMARY.md shipped successfully)
- 5 tasks 가 명확히 5 tier 분리됨 (build infra + TDD models + Repository/Card + Activity/UI + FCM/BUILD)
- Multi-tier task 분리가 reasonable

**권장 (선택):**

- 옵션 A (PASS-WITH-WARNINGS 채택): precedented + mechanical -> execution 진행. Task 5 의 BUILD + 14+ tests 가 sanity check 역할
- 옵션 B (split): Plan 09-03 -> 09-03a (Task 1+2 build+models+TDD) + 09-03b (Task 3+4+5 repository+UI+FCM). 단 wave shift 발생

**severity:** warning (precedented + mechanical work + multi-tier 분리 합리적)

---

#### WARNING 2 — Plan 09-04 시연 시각 불일치 (참고)

**문제:**

- Plan 09-04 Task 1 how-to-verify 의 시나리오에 expected_end_at = 현재+15분 (정상) 와 1~2분 후 (단축) 두 가지로 명시
- Demo execution 시 어느 값으로 시연할지 ambiguous

**완화:**

- Plan 04 는 autonomous false + checkpoint:human-verify -> 사용자 즉석 결정으로 해결 가능
- Plan 04 가 옵션 A/B/C 3가지 시연 모드 명시 (A 정상 / B deferred / C 단축)

**severity:** warning (자명한 사용자 결정으로 즉시 해결)

---

## Plan-by-Plan Summary

### Plan 09-01 (Wave 1, TBM-01) — 34kB, 2 tasks, 3 files_modified

- frontmatter 완전 (requirements TBM-01, depends_on [], wave 1, autonomous true)
- must_haves.truths 16개 모두 user-observable
- Task 1: 013_tbm_schema.sql ABCDEFG 7 sub-section + 정확한 SQL 본문
- Task 2: BLOCKING supabase db push --linked --yes + tags blocking
- Threat T-9-01·02·05·06·07·08 mitigation 명시
- **BLOCKER 1** — Task 2 verify line 543 의 grep underscore 버그

### Plan 09-02 (Wave 2, TBM-03) — 32kB, 2 tasks, 5 files_modified

- frontmatter 완전 (requirements TBM-03, depends_on [09-01], wave 2, autonomous true)
- must_haves.truths 11개 + 4 artifacts (smoke 4 + index.ts)
- Task 1: 4 case 전체 코드 ABCD + D-09 회귀 가드 + 기존 4 case 무변경
- Task 2: 12 curl smoke (4x3 scenarios) + notifications insert delta=0 검증
- Threat T-9-02·03·04·09·10·11 mitigation 명시
- Pitfall 5 (23505 catch) + Pitfall 9 (leader dedup) smoke 로 검증

### Plan 09-03 (Wave 2, TBM-02) — 62kB, 5 tasks, 27 files_modified

- frontmatter 완전 (requirements TBM-02, depends_on [09-01], wave 2, autonomous true)
- must_haves.truths 22개 + 17 artifacts
- Task 1: storage-kt 2.2.0 + install(Storage) + 3.x 거부 회귀
- Task 2: 4 TDD test files + 5 main files + Pitfall 1·2·8 mitigation
- Task 3: TbmRepository 2-stage + 3 channels (tbm_templates 채널 X anti-pattern)
- Task 4: 2 Activity + ComposeView 임베드 2곳 + Pitfall 12 Theme 래핑
- Task 5: FCM 분기 + compileDebugKotlin BUILD SUCCESSFUL + 14+ tests
- Threat T-9-01·06·12·13·14·15 mitigation 명시
- watch/ git diff 0 회귀 가드 (SC #4)
- **WARNING 1** — scope (5 tasks + 27 files) precedented but at upper limit

### Plan 09-04 (Wave 3, verification) — 21kB, 3 tasks, 4 files_modified

- frontmatter 완전 (requirements TBM-01·02·03, depends_on [09-01·02·03], wave 3, autonomous false)
- Task 1 checkpoint:human-verify gate:blocking — 3 옵션 A/B/C 분기 명시
- Task 2: SUMMARY 작성 + frontmatter status (complete/deferred/partial)
- Task 3: STATE/ROADMAP/REQUIREMENTS 갱신 + Phase 9 종결 commit
- Phase 7 04-04 deferred 패턴 1:1 mirror (옵션 B 경우)
- **WARNING 2** — expected_end_at 시연 값 모호

---

## Cross-Plan Goal Coverage Trace

| ROADMAP SC | 충족 Plans | Evidence |
|-----------|----------|----------|
| SC #1 (4 tables + RLS) | 09-01 | Task 1 ABE + Task 2 push + test_013 7 assertions |
| SC #2 (Android UI 1 cycle) | 09-03 + 09-04 | 09-03 compileDebugKotlin + 14+ tests (code side), 09-04 실기기 1 cycle 캡처 |
| SC #3 (manager dashboard + missed FCM) | 09-01·02·03·04 | 09-01 cron + 09-02 tbm-missed + 09-03 manager dashboard + 09-04 1일 시연 |
| SC #4 (code path separation) | 09-01·02·03 | watch/ git diff 0 + daily_safety_check 무변경 + Edge Fn 기존 4 case 무변경 + ai_agent 0 변경 |

모든 4 SC 가 plan 합성으로 충족 가능 — execution 후 verifier 가 합성 검증

---

## Recommendation

**PASS-WITH-WARNINGS — 2 BLOCKERS 1회 revision 후 execution 진행**

### 필수 revision (BLOCKER):

1. **Plan 09-01 line 543** — `grep -c tbm_signatures` 를 `grep -c tbm-signatures` 로 1글자 fix
2. **RESEARCH.md line 1155** — `## Open Questions` 를 `## Open Questions (RESOLVED)` 로 + 각 권장 prefix 를 RESOLVED 로 변경

### 선택 권장 (WARNING):

3. **Plan 09-03 split (옵션 B)** — 단 precedented + mechanical -> 옵션 A (현재 그대로 진행) 도 acceptable

**Total fix effort:** BLOCKER 2건 모두 textual edit 1줄 이내. 약 2분 작업

Revision 후 자동 진입 — Wave 1 Plan 09-01 execution 즉시 가능

---

*Phase: 09-tbm-worker-guide*
*Plan check date: 2026-05-18*
*Mode: goal-backward verification (12 dimensions, FORCE adversarial stance)*
*Plans verified: 4 (09-01, 09-02, 09-03, 09-04)*
