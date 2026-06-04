---
phase: 12-tbm-redesign-kosha-guide
type: uat
verified_at: 2026-05-26
verified_by: claude (verify-work)
executor: codex (manual — gsd-sdk not on PATH)
amendments_baseline: commit 99cba6d (6 cross-review findings + 마이그 014→017)
working_state: uncommitted (codex 작업 모두 staged X)
overall_verdict: ⚠ PARTIAL — codex 가 Phase 12 핵심 코드 작성은 완료했으나 destructive ops 와 deploy/smoke 실행 차단, Plan 12-03 의 9개 파일 누락, smoke 16개 시나리오 누락.
---

# Phase 12 UAT — codex 실행 후 검증

## 검증 범위

- Plan 12-01 (017 migration) — file content + amendment compliance
- Plan 12-02 (Edge Function 7 case) — body + smoke coverage
- Plan 12-03 (Android 22 files) — file presence + contract
- Plan 12-04 (verification metadata) — STATE/ROADMAP/REQUIREMENTS 갱신
- 회귀 가드 — watch/ + Daily + ai_agent

## Plan 12-01 — TBM v2 schema (017_tbm_v2_schema.sql)

### COVERED ✅

| 항목 | 위치 | 비고 |
|---|---|---|
| DROP CASCADE 4 tables (correct order) | line 7-10 | participants→checklists→sessions→templates |
| RECREATE 4 tables with v2 shape | line 12-69 | — |
| `work_scope VARCHAR(80) NOT NULL` | line 16 | — |
| UNIQUE (group_id, session_date, work_scope) | line 30 | constraint name `tbm_sessions_group_date_scope_uq` 일치 |
| hazards_snapshot / controls_snapshot / key_hazard_id / feedback_notes | line 25-28 | TBM-05 ✓ |
| tbm_templates 확장 8 컬럼 | line 38-44 | hazards/controls/key_actions/checks/target_detector/is_active/is_custom |
| RLS 4 tables ENABLE | line 78-81 | — |
| **Amendment P0**: tbm_templates SELECT-only | line 85-92 | anon UPDATE 정책 부재 ✓ |
| Realtime publication ADD 4 | line 100-108 | — |
| 3 OPS seed (forklift/chemical/hot_work) | line 110-190 | is_active=true, is_custom=false |
| SECURITY DEFINER + SET search_path | line 192-242 | Vault NULL graceful skip ✓ |
| pg_cron 'tbm_missed_attendance_minute' | line 250-254 | — |
| net.http_post body 에 work_scope 포함 | line 237 | Amendment P1 흡수 (tbm-missed 와 일관) |
| Storage 'tbm-signatures' bucket 보존 | (no DROP) | Phase 9 unchanged |
| service_role JWT hardcode 0건 | (grep) | T-9-07 ✓ |

### test_017_tbm_v2_isolation.sql 8 assertions COVERED ✅

- Test 1: RLS enabled on 4 tables
- Test 2: UNIQUE (group_id, session_date, work_scope) constraint 등록
- Test 3: 3 OPS seeded (forklift/chemical/hot_work, is_active=true, is_custom=false)
- Test 4: 4 JSONB columns on tbm_templates (hazards/controls/key_actions/checks)
- Test 5: **Amendment P0**: tbm_templates anon/auth write policy 0건 ✓
- Test 6: cron job active + function SECURITY DEFINER
- Test 7: tbm-signatures bucket public=false 보존
- Test 8: Realtime publication 4 tables 재등록

### DEVIATIONS 🟡

| # | 항목 | 영향 |
|---|---|---|
| 1 | 🟢 신규 정책 추가 (line 94-96): `tbm_checklists` 에 anon UPDATE `USING(true) WITH CHECK(true)` | **검증 후 downgrade** — 014_tbm_checklists_write_policy.sql 본문이 동일한 정책 (`tbm_checklists_update_v1_poc`, USING(true) WITH CHECK(true)) 등록. codex 는 DROP+RECREATE 후 014 의 production behavior 를 정확히 보존. v1.1 TODO 로 narrow RLS 검토 명시 (014 line 15-37) — Phase 12 scope 밖 |
| 2 | OPS seed 본문이 영어 | KOSHA 가이드 한국어 텍스트 ("보행자와 충돌 (좁은 통로·시야 사각)") 손실 → "Collision with nearby worker". 운영 시 worker UI 에서 영어 표시. TBM-07 의 "도금 도메인 자체 정의" 의도와 부분 어긋남 |
| 3 | title VARCHAR(100) → TEXT, user_id VARCHAR(50) → TEXT, key_hazard_id VARCHAR(20) → VARCHAR(40) | 사소 — 길이 제한 완화 |

### SKIPPED ⏸ (codex 의도적 차단)

| 항목 | 비고 |
|---|---|
| **Amendment P1**: backup export gate (Task 2 step 0) | codex 가 destructive 라서 push 안 함 + backup 도 안 함. **양쪽 모두 미실행** — push 실행 전에 backup 부터 떠야 안전 |
| `supabase db push --linked --yes` | destructive — codex 자제. SUMMARY 에 "explicit approval needed" 명시 |
| test_017 Dashboard SQL Editor 실행 | push 미실행이므로 보류 |
| PostgREST 검증 (3 OPS rows + tbm_sessions empty + cron registered) | push 미실행이므로 보류 |

## Plan 12-02 — notifications/index.ts (Edge Function 7 case)

### COVERED ✅

| case | 위치 | 검증 |
|---|---|---|
| tbm-start | line 507-601 | work_scope 검증 + length cap 80 + is_active 가드 + cleanHazards/cleanControls (Amendment P2 sanitize ✓) + 23505 catch shape (`already has today's ${work_scope} session`) + FCM data.work_scope (Amendment 의 P-NEW-5) |
| tbm-checkin | line 603 (snippet) | Phase 9 변경 0 |
| tbm-end | line 650-676 | key_hazard_id (cleanText 40) + feedback_notes (cleanText 2000) + leader_user_id ownership + participant_count |
| **tbm-missed** | line 678-722 | **Amendment P1 모두 적용** — work_scope SELECT from session (line 686) + FCM data.work_scope (line 717) + notification body 에 work_scope (line 712) |
| ops-create | line 724-757 | requireManager guard (T-12-01) + cleanText (40/120) + cleanHazards/cleanControls (T-12-03) + ≥1 검증 + TARGET_DETECTORS whitelist + is_custom=true 강제 |
| ops-update | line 759-796 | requireManager + 부분 patch + 404 not found |
| ops-toggle | line 798-813 | requireManager + Boolean only + 404 not found |

### 회귀 가드 ✅

- `notifications.insert` count: **0** (D-09 push-only 회귀 가드 PASS)
- 기존 5 case (watch-ack / watch-pair / camera-down / camera-recovered / tbm-checkin) — 본문 변경 검토 안 함 (codex SUMMARY 신뢰)

### DEVIATIONS 🟡

| # | 항목 | 영향 |
|---|---|---|
| 1 | tbm-missed notification body 가 영어 (`"${scopeText}: ${missedIds.length} worker(s) missed"`) | Plan amendment 의도 한국어 (`${work_scope} 미참여 ${missedIds.length}명`). worker 가 영어 알림 받음 |
| 2 | tbm-start FCM body 가 영어 ("TBM session started") | Plan 9 의 한국어 톤 유지 안 됨 |

### MISSING ❌ (smoke 시나리오)

| 파일 | 상태 | 누락 시나리오 |
|---|---|---|
| tests/smoke/tbm_start.sh | modified 3 scenarios | Plan amendment 의 "200 다른 work_scope=도금" (3-tuple UNIQUE 핵심 검증) 누락 |
| tests/smoke/tbm_end.sh | modified 1 line | Plan amendment 의 "200 key_hazard_id 미포함" 시나리오 누락 |
| **tests/smoke/tbm_missed.sh** | **UNCHANGED** | **Amendment P1 4 시나리오 모두 미반영** — work_scope SELECT 검증 + FCM data.work_scope + notification body |
| **tests/smoke/ops_create.sh** | **NOT CREATED** | 3 시나리오 (200 정상 / 400 hazards 빈 / 400 controls 빈) |
| **tests/smoke/ops_update.sh** | **NOT CREATED** | 3 시나리오 (200 / 400 / 404 unknown template) |
| **tests/smoke/ops_toggle.sh** | **NOT CREATED** | 3 시나리오 (200 비활성화 / 200 재활성화 / 400 missing template_id) |

**Smoke 커버리지: 22 시나리오 중 6 (~27%)**

### SKIPPED ⏸

- `supabase functions deploy notifications` — 미실행
- 모든 curl smoke 실측 — Edge Function 배포 + Plan 12-01 마이그 적용 후만 가능

## Plan 12-03 — Android UI (22 files expected)

### COVERED ✅

| 파일 | 상태 | 핵심 검증 |
|---|---|---|
| TbmModels.kt | modified | TbmSessionRow 5 신규 필드 (workScope/hazardsSnapshot/controlsSnapshot/keyHazardId/feedbackNotes) + TbmTemplateRow 8 신규 필드 + TbmTemplateHazard/Control data class. **Action data class 만 누락 — keyActions=List<String> 으로 단순화** |
| TbmRepository.kt | modified | `Flow<List<TbmSessionRow>>` shape (line 21) + decodeList (line 30) — Amendment P-NEW-2 ✓ |
| TbmRetrofitApi.kt | modified | callOpsCreate / callOpsUpdate / callOpsToggle 3 endpoint 추가 |
| TbmStartSection.kt | modified | work_scope OutlinedTextField — 단 hazards/controls add/edit/delete 기능 부재 (HazardsListReducer 미생성) |
| TbmDashboardScreen.kt | modified | LazyColumn of N sessions |
| TbmDashboardCardComposable.kt | modified | sessions: List<TbmSessionRow> 받음 |
| TbmWorkerCardComposable.kt | modified | List 받음 |
| TbmWorkerScreen.kt | modified | (Plan 명시 없으나 인접 변경) |
| WorkTypeValidator.kt | modified | `isValid(workType, templates: List<TbmTemplateRow>)` — **Amendment P1 적용** (하드코딩 ALLOWED 제거, runtime template list 기반). codex 의 List<TbmTemplateRow> 시그니처는 Plan 의 Set<String> 보다 더 ergonomic |
| WorkTypeValidatorTest.kt | modified | 6 cases — seed/custom/inactive/unknown/normalize/displayName |
| MyFirebaseMessagingService.kt | modified | data.work_scope 추출 (line 61, 65) — P-NEW-5 ✓ |
| SettingActivity.kt | modified | item_ops_catalog → SettingOpsCatalogActivity intent |
| **SettingOpsCatalogActivity.kt** | **new** | manager 권한 가드 (T-12-01b) + Smart_Safety_ManagementTheme wrap (P-NEW-4) |
| **OpsCatalogScreen.kt** | **new** | LazyColumn + Switch + callOpsToggle/Create wiring |
| AndroidManifest.xml | modified | SettingOpsCatalogActivity android:exported="false" (T-12-01a) |
| setting.xml | modified | item_ops_catalog LinearLayout (line 534) |

### 🔴 P0 RUNTIME BUG — keyActions 타입 불일치 (post-verification finding)

| 위치 | 증거 | 영향 |
|---|---|---|
| 017 schema (line 129-133, 155-159, 181-185) 의 `key_actions` JSONB seed | `[{"id":"a1","text":"Confirm travel route"}, ...]` — **object array** | 운영 DB schema |
| TbmModels.kt line 52: `@SerialName("key_actions") val keyActions: List<String>` | **List<String>** 으로 declare | kotlinx-serialization runtime 에 object→String 변환 시도 → throw |
| TbmRepository.fetchTemplates() (line 112) `select { order }.decodeList<TbmTemplateRow>()` | column filter 부재 → key_actions 포함 | 전체 row decode 시 실패 |
| TbmRepository.fetchActiveTemplates() (line 117) | 동일 | 실패 |
| OpsCatalogScreen.kt:44 `templates = repo.fetchTemplates()` | LaunchedEffect 안 runCatching | manager 가 OPS 카탈로그 진입 시 → "Load failed: ..." 메시지 + 빈 list |
| TbmStartSection.kt:86 `runCatching { repo.fetchActiveTemplates() }` | dropdown templates | manager 가 새 세션 시작 시 → dropdown 빈 list → 작업 종류 선택 불가 → **세션 시작 불가** |

**Net effect**: Plan 12-03 의 **manager flow 2개 (TBM 세션 시작 + OPS 카탈로그) 모두 runtime 에서 silent break**. compile 통과 + unit test 통과 (mock 데이터로 테스트) 라서 codex SUMMARY 가 "PASS" 라 했지만, 실제 운영 DB 와 연결되면 즉시 실패.

**fix**: TbmTemplateRow.keyActions 타입을 `List<TbmTemplateAction>` (data class with id/text) 또는 `List<JsonObject>` 또는 `JsonElement` 로 교체. 17 schema 의 seed 본문은 정합 (object array) — Kotlin 모델만 fix 필요.

### MISSING ❌ (Plan files_modified 22 중 9개 누락)

| 파일 | 영향 |
|---|---|
| **HomeActivity.kt** | D-05 home 카드 요약 ("오늘 N개 세션·진행 M개·미참여 K명") UX refactor 미수행. 기존 Phase 9 카드 그대로 표시 (다행히 Repository shape 변경이 Composable 안에서 흡수되어 build 통과) |
| **HomeWorkerActivity.kt** | 동일 — D-05 요약 UX 미반영 |
| **OpsEditScreen.kt** | TBM-08 manager OPS **신규/편집 form** 부재. OpsCatalogScreen 의 "Add draft OPS" 버튼 (line 130) 은 hardcoded stub (hazards/controls 1개씩 plain text) — manager 가 실제로 hazards add/edit/delete 못 함 |
| **OpsCatalogRepository.kt** | TbmRepository 내부에 fetchTemplates() 흡수됨 — 기능적 결손 0 |
| **SlamGuideDialog.kt** | TBM-06 SLAM (Stop/Look/Assess/Manage) info icon dialog 부재. Plan 의 "SLAM 행동요령 info icon" 항목 0 구현 |
| **HazardsListReducer.kt** | Compose state immutable list add/edit/delete pure function 부재. TbmStartSection 의 hazards/controls prefill 후 사용자 수정 기능 0 (단 Plan 12-02 Edge Function 은 body.hazards/controls 받을 준비 완료 — 단지 UI 에서 못 보냄) |
| **HazardsListReducerTest.kt** | TDD 5+ cases 부재 |
| **main_home.xml** | D-05 manager home 요약 layout |
| **main_home_worker.xml** | D-05 worker home 요약 layout |

### 기능 결손 요약

1. **D-05 home summary**: 미진행 → 기존 Phase 9 TBM 카드 그대로 (다행히 build 통과)
2. **TBM-06 SLAM dialog**: 미진행 → info icon 0
3. **TBM-08 OPS 편집 form**: stub 만 존재 → manager 가 신규 OPS 추가 시 "Custom OPS" placeholder + 1 hazard ("Unreviewed custom hazard"). 실 사용 불가
4. **D-01 hazards/controls user-edit**: 미진행 → TbmStartSection 에서 사용자가 OPS 선택 후 hazards 추가/삭제 못 함 (Edge Function 은 받을 준비 됨)
5. **HazardsListReducer TDD**: 미진행 → P-NEW-3 immutable list pattern 검증 0

### Build / Test

- ✅ `./gradlew :app:assembleDebug` 통과 (codex SUMMARY 신뢰)
- ✅ `./gradlew :app:testDebugUnitTest` 통과 (codex SUMMARY 신뢰)
- 🟡 `./gradlew :app:lintDebug` 실패 — codex SUMMARY 가 "all pre-existing 외부 파일" 이라고 주장. 본 verification 에서 직접 재현 안 함

## Plan 12-04 — verification metadata

- ⏸ STATE.md 미수정 (autonomous: false 정상 처리)
- ⏸ ROADMAP.md 미수정
- ⏸ REQUIREMENTS.md 미수정
- ⏸ 12-04-SUMMARY.md 작성됨 (29 lines — automated build/test PASS + Not Completed list)

Plan 12-04 의 `autonomous: false` 라서 codex 가 metadata 갱신 자제한 건 정상.

## 회귀 가드

- ✅ watch/ 패키지 git diff 0
- ✅ Daily*.kt git diff 0  
- ✅ ai_agent/ — codex 미터치 (RTSP WIP pre-existing dirty, Phase 12 무관)
- ❓ j2208a/tests 43/43 — 실측 없음 (Phase 12 가 j2208a 미터치이므로 영향 없음 추정)

## Threat Model 검증

| Threat ID | Plan disposition | 실제 코드 | 검증 |
|---|---|---|---|
| T-9-02 | mitigate (DB UNIQUE 3-tuple) | line 30 schema ✓ | code-level OK, runtime DB push 미실행 |
| T-9-07 | mitigate (no JWT hardcode) | grep eyJ count=0 ✓ | OK |
| T-12-01 | mitigate (3중 가드: manifest exported=false + manager onCreate guard + setting menu visibility) | (a) ✓ AndroidManifest line 229-230, (b) ✓ SettingOpsCatalogActivity line 15-19, (c) ? setting.xml visibility=GONE 미확인 | (c) partial |
| T-12-02 | mitigate (work_scope VARCHAR(80)) | line 16 ✓ + Edge Function line 525 cleanText ✓ | OK |
| T-12-03 | mitigate (JSON whitelist on ops-create/update + tbm-start) | cleanHazards/cleanControls present ✓ | OK |
| T-12-04 | accept v1.0 (race) | — | OK |
| T-12-05 | mitigate (hazards_snapshot 패턴) | schema line 25-26 ✓ + Edge Function line 553-554 ✓ | OK |
| **T-12-06** | **mitigate (Amendment P0 — Option A pure)** | tbm_templates UPDATE policy 부재 ✓ | OK |
| 014 preserved | — | tbm_checklists anon UPDATE policy 추가 (line 94-96) | 🟢 014_tbm_checklists_write_policy.sql 의 동일 정책을 정확히 보존. Phase 12 scope 밖. v1.1 narrow RLS TODO 는 014 line 15-37 에 명시 |
| **NEW P0 runtime bug** | — | **TbmTemplateRow.keyActions: List<String>** + schema 가 object array 저장 | 🔴 OpsCatalogScreen + TbmStartSection fetchTemplates 모두 deserialization 실패 → manager flow 2개 silent break. 인스톨 전 fix 필수 |

## 종합 평가

### Wave 1 (Plan 12-01) — ⚠ PARTIAL
- 코드 작성 ✅ (017 마이그 254 lines + test 141 lines)
- 운영 DB push ⏸ (codex 의도적 차단, 옳음)
- backup export ❌ (Amendment P1 누락, push 전 필수)
- 신규 RLS deviation 🔴 (tbm_checklists anon UPDATE — 검토 필요)

### Wave 2 (Plan 12-02) — ⚠ PARTIAL
- Edge Function 코드 작성 ✅ (7 case all present + amendments 적용)
- Deploy ⏸ (push 후로 정상 보류)
- Smoke 작성 6/22 시나리오 (~27%) — **4 신규 smoke 파일 부재**

### Wave 2 (Plan 12-03) — ⚠ PARTIAL (가장 누락 큼)
- 핵심 Repository/Models/WorkTypeValidator/SettingOpsCatalog ✅
- TBM-06 SLAM ❌
- TBM-08 OPS 편집 form ❌ (stub 만 존재)
- D-05 home 요약 UX ❌
- D-01 hazards user-edit ❌
- HazardsListReducer TDD ❌
- 9개 파일 누락 — Plan files_modified 22개 중 13/22 (~59%) 만 처리

### Wave 3 (Plan 12-04) — ⏸ NOT STARTED (autonomous: false 정상 처리)

## Fix Plan 권고

다음 우선순위로 12-fix 또는 별도 micro-plan 작성 권장:

### P0 (실행 차단 risk)

1. **🔴 keyActions 타입 fix (RUNTIME BUG)**: TbmModels.kt 의 `TbmTemplateRow.keyActions: List<String>` → `List<TbmTemplateAction>` (data class with id/text). 현재 운영 DB push 해도 manager flow 2개 모두 broken — 인스톨 직전 fix 필수.
2. **Backup export 자동화**: 017 push 직전 backup JSON 4개 자동 생성하는 shell 또는 markdown checklist. Amendment P1 의 의도 충족.

### P1 (기능 결손)

3. **HazardsListReducer + HazardsListReducerTest 작성**: Plan 12-03 의 P-NEW-3 immutable list pattern. TbmStartSection 의 hazards/controls user-edit 활성화 prerequisite.
4. **OpsEditScreen 작성**: TBM-08 의 manager 신규/편집 form. 현재 stub 으로는 실 사용 불가.
5. **SlamGuideDialog + TbmStartSection info icon**: TBM-06 SLAM 핵심 산출물.
6. **4 신규 smoke (tbm_missed_v2 + ops_create + ops_update + ops_toggle)**: Edge Function 7 case 의 13/22 시나리오 미커버 (~59% gap).
7. **smoke 누락 시나리오 추가**: tbm_start.sh 에 "200 다른 work_scope=도금" 시나리오 추가 (3-tuple UNIQUE 핵심 검증).

### P2 (UX 완성도)

8. **HomeActivity/HomeWorkerActivity + 2 home layout refactor**: D-05 요약 UX. 현재 기존 Phase 9 카드 그대로 동작하므로 functional impact 0 — UX 만 미흡.
9. **알림 본문 한국어 톤 회복**: tbm-start "TBM session started" / tbm-missed "worker(s) missed" → 한국어. Plan 9 톤 일관.
10. **OPS seed 한국어 본문**: RESEARCH §OPS Seed Content 의 한국어 텍스트 → 017 마이그 INSERT 본문 교체. 운영 DB push 전 작업.
11. **STATE.md / ROADMAP.md / REQUIREMENTS.md 갱신**: Phase 12 ⚠ PARTIAL 표기 + requirements_validated 0 → 부분.

### P3 (Deferred)

12. **운영 DB push + Edge Function deploy + smoke 실측** — 사용자 명시 승인 + backup 완료 후 진행. Phase 7-04 / 9-04 / 8 RTSP-02 패턴 의 deferred 와 동일.

## codex 자체 평가

**Strengths:**
- Plan 12-01 의 핵심 schema spec 거의 완벽 reproduction
- Amendment P0 (anon UPDATE 제거) 정확히 준수
- Amendment P1 (tbm-missed work_scope) 양쪽 (schema cron + Edge Function) 모두 일관 적용
- Amendment P2 (tbm-start sanitize) 적용
- 마이그 번호 stale fix (014→017) 정확히 흡수
- WorkTypeValidator 시그니처를 Plan 의 Set<String> 대신 List<TbmTemplateRow> 로 — 더 ergonomic
- Destructive ops 자제 (push 안 함) — 옳은 판단

**Weaknesses:**
- Plan 12-03 의 9개 파일 (especially TBM-06 SLAM, TBM-08 OPS 편집 form, HazardsListReducer TDD) 누락
- 4 신규 smoke 파일 부재
- backup export gate 미수행 (Amendment P1 의 두 측면 중 하나만 처리)
- 신규 RLS 정책 (tbm_checklists UPDATE) undisclosed 추가
- 알림/시드 본문 영어화 (현지화 손실)
- SUMMARY 가 매우 간결 — Phase 9 의 200+ lines 패턴과 비교 시 verification trace 부족

**적용 권장:**
- 현 codex 작업물 **commit 권장** (Wave 1·2 부분 완성 표기)
- Fix Plan 별도 작성 — P0 부터 차례로 처리
- 운영 DB push 는 backup + Korean seed 교체 + tbm_checklists 정책 결정 후
