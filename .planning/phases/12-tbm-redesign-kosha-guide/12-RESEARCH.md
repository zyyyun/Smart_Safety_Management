# Phase 12: TBM 재설계 (KOSHA 가이드 흡수) — Research

**Researched:** 2026-05-26
**Domain:** Supabase Postgres schema migration + Deno Edge Function amend + Android Compose UI refactor + 도금/금속가공 OPS seed
**Confidence:** HIGH (Phase 9 코드/문서 직접 검증) · LOW for 화학물질·고온 OPS 내용 (가이드 외 자체 정의)

---

## TL;DR

Phase 9 의 4 테이블 + Edge Function 4 case + Android `tbm/` 패키지 + manager Dashboard/worker Activity 는 **80% 재사용**. 핵심 변경 4가지:

1. **Schema** — `tbm_sessions.UNIQUE (group_id, session_date)` 제거, `work_scope TEXT` 컬럼 추가, replacement UNIQUE = `(group_id, session_date, work_scope)`. `tbm_templates` 에 `hazards JSONB`, `controls JSONB`, `key_actions JSONB`, `is_active BOOLEAN`, `target_detector VARCHAR(20)`, `is_custom BOOLEAN` 컬럼 추가.
2. **Edge Function** — `tbm-start` 의 23505→409 dedup pattern shape 유지하되 트리거 컬럼이 3-튜플로 바뀜. checklist snapshot 위에 **잠재위험/대책 snapshot 추가** (OPS 비활성화돼도 진행중 세션 보존). FCM `tbm_alert` payload 에 `work_scope` 추가.
3. **Android** — `WorkTypeValidator.ALLOWED` 하드코딩 제거 (dynamic from templates) · `todaySessionFlow` shape `TbmSessionRow?` → `List<TbmSessionRow>` 변경 · `GroupSessionCard` 1개 → N개 inner list · `TbmStartSection` 에 work_scope 입력 + OPS prefill hybrid (잠재위험/대책 list state). 신규 `SettingOpsCatalogActivity` (manager-only, ComposeView in XML scaffold per Phase 11 token).
4. **Seed** — 5종 generic templates DROP, 3종 도금 도메인 OPS INSERT (지게차 = KOSHA 28p [CITED], 화학물질·고온 = [ASSUMED] 자체 초안).

**Primary recommendation:** Phase 9 의 autonomous wave chain (Plan 1 schema → Plan 2 Edge Function ∥ Plan 3 Android → Plan 4 검증) 그대로 미러. Plan 3 는 Plan 1 commit 후 시작 (WorkTypeValidator rewrite 가 새 seed work_types 알아야 함).

---

## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01 ~ D-09)
- **D-01** OPS prefill 하이브리드 — 잠재위험·대책 자동 채움 후 사용자가 수정/삭제/신규 추가 자유, `is_custom = true` 태그로 구분.
- **D-02** OPS schema depth — 잠재위험 list + 대책 list + 핵심 안전조치 3개 + 자율점검 항목 list. **4 카테고리 분류 안 함** (flat list).
- **D-03** TBM ↔ AI detector — `work_scope` DB 적재만, detector 동작 변화 0 (v1.2 로 이연).
- **D-04** 마이그레이션 = **DROP + RECREATE** (destructive). 017_tbm_v2_schema.sql 신규.
- **D-05** Home UI = 요약만 ("오늘 N개·진행 M개·미참여 K명"). 자세한 list 는 TbmDashboardActivity 안.
- **D-06** OPS 관리 UI = **SettingOpsCatalogActivity 신규** (Setting* 시리즈 안, manager-only).
- **D-07** 신규 OPS 필수 = 이름 + 잠재위험(≥1) + 대책(≥1). 핵심조치·자율점검·detector hint 는 선택.
- **D-08** 리더 = `user_role IN ('manager','general_manager')` 만 가능, UNIQUE 제약 없음 (한 리더 N 세션 OK).
- **D-09** 작업자 = N 세션 참여 가능. `tbm_participants UNIQUE (session_id, user_id)` 만 유지.

### Claude's Discretion
- `work_scope` 컬럼 type — TEXT (free) vs ENUM (산세/도금조/후처리/검사/운반/기타) — **본 RESEARCH 추천: TEXT** (운영 후 패턴 정착 시 v1.2 enum 화).
- 잠재위험·대책 list schema — JSONB array vs child table — **본 RESEARCH 추천: JSONB** (Phase 9 의 `tbm_templates.checklist` 패턴 일관성 + N=3~10 항목 정도라 정규화 가치 낮음).
- SLAM 행동요령 UI 표시 — modal · info icon · inline tooltip — **본 RESEARCH 추천: info icon → modal** (Phase 11 token 잠금 후 token 적용).
- OPS 비활성 시 진행중 세션 OPS 참조 — snapshot vs live — **본 RESEARCH 추천: snapshot** (Phase 9 `tbm_checklists.item_text` snapshot 패턴 미러 — `tbm_sessions` 에 `hazards_snapshot`/`controls_snapshot` JSONB 추가).

### Deferred Ideas (OUT OF SCOPE)
detector hint 동적 연계 · 회의록 PDF export · 외국인 통·번역 · 가이드 외 11종 OPS · work_scope ENUM 화 · SLAM 학습 모듈 · 회의록 백업·검색·통계 — 모두 v1.2+.

---

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TBM-04 | tbm_sessions UNIQUE 제거 + work_scope 컬럼 + Edge Function dedup 분기 변경 | §Schema (D-04 DROP+RECREATE 패턴) + §Edge Function (Pitfall 5 23505→409 shape 유지) |
| TBM-05 | KOSHA 회의록 양식 schema mapping | §Schema (tbm_sessions 에 hazards_snapshot/controls_snapshot/key_action_id JSONB · tbm_templates 에 hazards/controls/key_actions/checks JSONB) |
| TBM-06 | Android UI 재구성 (N 세션 list + work_scope 입력 + SLAM) | §Android UI (todaySessionFlow shape 변경 + TbmStartSection 에 work_scope/hazards prefill state + SLAM info icon) |
| TBM-07 | 도금 도메인 OPS 3종 seed | §OPS Seed Content (지게차 CITED + 화학물질·고온 ASSUMED) |
| TBM-08 | 관리자 OPS 카탈로그 UI | §Android UI (SettingOpsCatalogActivity 신규 · SettingCctvManagementActivity 패턴 미러 · tbm_templates 직접 PATCH for is_active 토글) |

---

## Schema (Postgres)

### Key shape changes (DDL fragments — planner 가 017 마이그레이션 작성 시 참조)

```sql
-- (1) tbm_sessions — work_scope 추가 + UNIQUE 재정의
CREATE TABLE public.tbm_sessions (
    session_id        BIGSERIAL PRIMARY KEY,
    group_id          INTEGER NOT NULL REFERENCES public.groups(group_id),
    session_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    work_scope        VARCHAR(80) NOT NULL,            -- D-04 신규 (자유 TEXT, ENUM 화 v1.2)
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at          TIMESTAMPTZ,
    expected_end_at   TIMESTAMPTZ NOT NULL,
    leader_user_id    VARCHAR(50) NOT NULL,
    work_type         VARCHAR(40) NOT NULL,            -- OPS template 참조 (forklift/chemical/hot_work)
    location          VARCHAR(255),
    notes             TEXT,
    missed_alert_at   TIMESTAMPTZ,
    -- 회의록 양식 핵심 필드 (D-02 + TBM-05) — OPS 비활성화돼도 진행중 세션 보존
    hazards_snapshot  JSONB NOT NULL DEFAULT '[]'::jsonb,
        -- [{id:"h1", text:"…", is_custom:false}, …]  잠재위험요인 list (D-01)
    controls_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
        -- [{id:"c1", text:"…", hazard_id:"h1", level:"제거|대체|통제", is_custom:false}, …]
    key_hazard_id     VARCHAR(20),                     -- 중점위험요인 1개 선정 (hazards_snapshot 의 id 참조)
    feedback_notes    TEXT,                            -- 환류 조치 (작업 후 종료 미팅)
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 새 UNIQUE: 같은 group·날짜에 같은 work_scope 만 중복 차단 (다른 scope OK)
    CONSTRAINT tbm_sessions_group_date_scope_uq
        UNIQUE (group_id, session_date, work_scope)
);

-- (2) tbm_templates — OPS 카탈로그로 확장
CREATE TABLE public.tbm_templates (
    template_id     SERIAL PRIMARY KEY,
    work_type       VARCHAR(40) NOT NULL UNIQUE,        -- 'forklift', 'chemical', 'hot_work'
    title           VARCHAR(100) NOT NULL,
    description     TEXT,
    hazards         JSONB NOT NULL,                      -- [{id, text}, …]   D-02
    controls        JSONB NOT NULL,                      -- [{id, text, hazard_id, level}, …]
    key_actions     JSONB NOT NULL DEFAULT '[]'::jsonb,  -- [{text}, …] 핵심 안전조치 3개 (선택)
    checks          JSONB NOT NULL DEFAULT '[]'::jsonb,  -- ["…","…"] 자율점검 항목 list (Phase 9 checklist 호환)
    target_detector VARCHAR(20),                          -- 'forklift'|'fire'|'helmet'|'person'|'fall' (D-03)
    is_active       BOOLEAN NOT NULL DEFAULT true,        -- D-08 토글
    is_custom       BOOLEAN NOT NULL DEFAULT false,       -- D-01 (관리자 신규 추가 row)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- (3) tbm_checklists / tbm_participants — 013 schema 그대로 유지 (테이블만 RECREATE)
--      tbm_checklists.checklist  = template.checks 의 snapshot (Phase 9 패턴 유지)
```

**Why JSONB over child table:** N (잠재위험·대책) = 3~10 항목, 쿼리 패턴은 "특정 세션 전체 view" 단일 dominant. 정규화 시 join overhead 가치 낮음. Phase 9 의 `tbm_templates.checklist` JSONB 패턴과 일관 — Reducer·Realtime decode 코드도 재사용 가능. 운영 6개월 후 patterns 정착 시 v2.0 으로 정규화 검토.

**Why snapshot:** Phase 9 의 `tbm_checklists.item_text` 가 template 변경에도 이력 보존하는 핵심 패턴 (013_tbm_schema.sql:72 + 09-CONTEXT D-01 "snapshot at session start"). OPS 활성/비활성 토글이 진행중 세션의 잠재위험·대책을 흔들면 안 됨 → 같은 패턴 적용. Phase 9 D-01 evidence: `item_text TEXT NOT NULL  -- snapshot (템플릿 변경에도 이력 보존)`.

**Why replacement UNIQUE = (group, date, work_scope):** 작업자 안전 — 같은 group 의 같은 날 같은 scope 를 두 번 시작하지 못하게 (실수로 두 번 클릭). 다른 scope 면 OK. Edge Function 의 23505→409 응답 패턴 shape 유지 (Pitfall 5).

### Realtime publication (DROP TABLE 의 side effect)

DROP TABLE 은 `supabase_realtime` publication 에서 해당 테이블을 **자동으로 제거**. 014 migration 의 RECREATE 후 반드시 `ALTER PUBLICATION supabase_realtime ADD TABLE …` 4개 재등록 필요. 패턴: 013_tbm_schema.sql:136-153 그대로 미러.

### RLS 패턴
- SELECT — Phase 9 의 `USING (true)` v1.0 PoC 패턴 4 테이블 모두 재등록.
- WRITE — `tbm_sessions`/`tbm_checklists`/`tbm_participants` 는 service_role (Edge Function) 만, Phase 9 13:188 패턴.
- `tbm_templates` 의 **is_active 토글 직접 PATCH** 는 Phase 9 의 `updateChecklistItem` (TbmRepository.kt:213) 패턴 따라 anon/authenticated UPDATE 정책 추가 권장 — SettingOpsCatalogActivity 가 supabase-kt 직접 PATCH. (alternative: 신규 Edge Function `ops-toggle` 케이스 추가. planner 결정.)
- `tbm_templates.INSERT` (신규 OPS 추가) — manager 가 자주 추가 안 함 → Edge Function `ops-create` 신규 케이스 권장 (input validation 강제).

### Storage bucket
`tbm-signatures` 버킷은 `storage.objects` 안 — DROP TABLE 영향 없음. Phase 9 시드 세션의 서명 PNG 가 orphan 으로 남지만 cost 작고 격리됨. **planner 결정**: 그대로 유지 (cheap) vs 014 안에서 `DELETE FROM storage.objects WHERE bucket_id='tbm-signatures'` 한 번 정리. **본 RESEARCH 추천**: 유지 (D-04 destructive 의 단순성 우선).

### pg_cron
`tbm_missed_attendance_check()` 함수 + `tbm_missed_attendance_minute` cron 은 014 migration 안에서 CREATE OR REPLACE FUNCTION + unschedule/schedule 재등록. body 자체는 변경 0 (work_scope 무관, expected_end_at + 30min 임계).

---

## Edge Function (`supabase/functions/notifications/index.ts`)

### Changed cases

**`tbm-start`** — 핵심 변경:
1. payload 에 `work_scope` 필수 인자 추가 (`if (!work_scope) return err(...)`).
2. INSERT row 에 `work_scope` 추가.
3. **OPS prefill 흡수**: template 조회 시 `checklist` (현 Phase 9) 외 `hazards`, `controls`, `key_actions` 도 같이 SELECT. session INSERT 본문에 `hazards_snapshot = tmpl.hazards`, `controls_snapshot = tmpl.controls` 추가 (snapshot 패턴).
4. **D-01 사용자 수정 반영**: 클라이언트가 prefill 후 수정한 `hazards`/`controls` array 를 payload 로 보내면 → server 가 받아서 INSERT 시 그것을 우선 사용 (없으면 template 의 raw 그대로). pseudocode:
   ```ts
   hazards_snapshot: body.hazards ?? tmpl.hazards,
   controls_snapshot: body.controls ?? tmpl.controls,
   ```
5. **23505 dedup shape 유지** (Pitfall 5 mitigation): UNIQUE 트리거 컬럼이 3-튜플로 바뀌지만 catch shape 동일. response text 만 갱신: `"이미 오늘 ${work_scope} 세션이 존재합니다"`.
6. **FCM payload 에 work_scope 추가** (Pitfall 신규 — worker 가 N 세션 중 어느 것 알림인지 식별):
   ```ts
   data: {
     type: "tbm_alert", action_in_app: "tbm-started",
     session_id: String(session.session_id),
     work_scope,                                // 신규
     work_type,
   }
   ```

**`tbm-checkin`** — payload 변경 없음 (session_id 가 work_scope 정보 보유). 단 ownership check 의 `tbm_sessions UNIQUE` 가정 깨지므로, `maybeSingle` 호출 그대로 OK (session_id 가 PK).

**`tbm-end`** — payload 변경 없음. 단 `key_hazard_id`/`feedback_notes` 가 추가됐으므로, manager 가 종료 시점에 환류 조치를 입력하면 같이 PATCH 하도록 payload 확장 고려 (planner 결정 — 회의록 양식 7번 핵심 필드).

**`tbm-missed`** — `tbm_missed_attendance_check()` 함수 (013_tbm_schema.sql:211) 이 SELECT 하는 컬럼 변화 0 (session_id/group_id/expected_end_at/leader_user_id 그대로). work_scope 무관. body 변경 0.

### New cases (D-08 관리자 OPS 카탈로그 — planner 결정)

**Option A (권장):** 신규 `ops-create` 케이스 — manager 가 새 OPS 추가. 입력 검증 (이름·잠재위험≥1·대책≥1 D-07), `is_custom=true` 강제, `service_role` 만 write.

**Option B:** `tbm_templates` 에 anon/authenticated INSERT/UPDATE 정책 + 클라이언트 supabase-kt 직접 PATCH (Phase 9 의 `updateChecklistItem` 패턴 미러). 단점 — server-side 검증 약함, RLS 조건식 길어짐.

**본 RESEARCH 추천**: `is_active` 토글 (단순 UPDATE) 만 Option B, 신규 OPS 추가 (`ops-create`) + 수정 (`ops-update`) 은 Option A. Phase 9 의 분리 패턴과 일관.

### 12 smoke test 시나리오 (Phase 9 의 4-case 12 시나리오 재작성 필요)

Phase 9 의 plan 09-02 안 smoke test 12개 시나리오를 다시 작성해야 함 (각 case 의 happy/edge/auth 패스). 신규 시나리오:
- `tbm-start` 같은 group·date·work_scope 중복 호출 → 409 (Pitfall 5 회귀).
- `tbm-start` 다른 work_scope 같은 group·date → 200 (UNIQUE 완화 확인).
- `tbm-start` payload 에 `hazards` array 보내면 → snapshot 에 그대로 INSERT (D-01 수정 반영).
- `ops-create` 잠재위험 빈 array → 400 (D-07).
- `tbm-end` 시 key_hazard_id 있는 세션 종료 → ended_at 갱신 (회의록 완성 검증).

---

## Android UI

### File-level change inventory (planner 가 wave 분할 시 참조)

**Modify** (Phase 9 파일):
- `tbm/WorkTypeValidator.kt` — `ALLOWED` 하드코딩 제거. Option: (a) dynamic from `tbm_templates` (Repository fetch 후 set 채움) (b) 새 seed 5종 → 3종 ('forklift','chemical','hot_work') 으로 교체. **추천 (b)** — validator 의 안정성 (Phase 7 패턴) 유지, dynamic 은 정합성 위험.
- `tbm/TbmRepository.kt:42` — `todaySessionFlow` 반환 type `Flow<TbmSessionRow?>` → `Flow<List<TbmSessionRow>>`. 초기 `decodeSingleOrNull` → `decodeList`. Realtime 갱신 시 재조회도 list.
- `tbm/TbmRepository.kt:179` — `todaySessionsFlow` 반환 type `Map<Int, TbmSessionRow?>` → `Map<Int, List<TbmSessionRow>>`. callbackFlow body 수정.
- `tbm/TbmModels.kt` — `TbmSessionRow` 에 `workScope`/`hazardsSnapshot`/`controlsSnapshot`/`keyHazardId`/`feedbackNotes` 필드 추가. `TbmTemplateRow` 에 `hazards`/`controls`/`keyActions`/`checks`/`targetDetector`/`isActive`/`isCustom` 추가. 양 dual annotation (`@SerialName` + `@SerializedName`) 유지 (TbmModels.kt:89-93 의 fix 유지 — Pitfall 신규 Gson/kotlinx mismatch).
- `tbm/TbmDashboardScreen.kt` — `GroupSessionCard` 가 단일 `session: TbmSessionRow?` → `sessions: List<TbmSessionRow>` 받고 내부에서 LazyColumn 또는 forEach 로 N 카드 렌더. 빈 list 면 "오늘 세션 없음" UI.
- `tbm/TbmStartSection.kt:75` — `selectedWorkType = "fire"` → 'forklift' (또는 templates[0].workType). work_scope OutlinedTextField 추가. OPS 선택 후 hazards/controls list state 추가 (`var hazards by remember mutableStateOf<List<TemplateHazard>>(...)` + add/edit/delete). is_custom 태그.
- `TbmDashboardActivity.kt` — manager 권한 가드 복원 (현재 PoC `?: "test_user"` fallback 제거, D-08 + Phase 9 T-9-13 mitigation).
- `tbm/TbmStartSection.kt:119` (work_type dropdown) — `WorkTypeValidator.ALLOWED.toList().sorted()` → templates 에서 `is_active=true` 만 표시.
- `SettingActivity.kt` — `item_ops_catalog` LinearLayout 진입점 추가 (Phase 9 패턴, SettingCctvManagementActivity 등록 (74-76) 미러). `setting.xml` layout 도 함께.
- `HomeActivity.kt` `setupTbmDashboardCard` + `HomeWorkerActivity.kt` `setupTbmCard` — 요약 카드로 refactor (D-05). "오늘 N개 세션·진행 M개·미참여 K명" 텍스트 fetch + 표시.
- `AndroidManifest.xml` — `SettingOpsCatalogActivity` 신규 등록 (`android:exported="false"`).

**Create:**
- `SettingOpsCatalogActivity.kt` (`ComponentActivity` + `setContent { Smart_Safety_ManagementTheme { OpsCatalogScreen(…) } }` — Pitfall 12 mitigation, Phase 9 TbmDashboardActivity.kt:36-43 패턴 그대로). manager-only 가드.
- `tbm/OpsCatalogScreen.kt` + `OpsCatalogRepository.kt` (또는 TbmRepository 확장) — LazyColumn of templates, is_active 토글 switch, "+ 신규 추가" 버튼 → `OpsEditScreen.kt`.
- `tbm/OpsEditScreen.kt` — 이름·잠재위험 list (add/del)·대책 list (add/del + level dropdown)·핵심조치 list·자율점검 list·target_detector dropdown·is_active toggle. D-07 검증 (이름 + hazards≥1 + controls≥1 빈 차단).

**Existing tests to fix:**
- `app/src/test/.../tbm/` 디렉토리 안 Phase 9 의 4 TDD test — `WorkTypeValidatorTest` (ALLOWED 변경) · `ExpectedEndAtValidatorTest` (변경 없음 — ISO_OFFSET_DATE_TIME 그대로) · `TbmParticipantsReducerTest` (변경 없음 — Reducer body 같음) · `TbmChecklistsReducerTest` (변경 없음). 신규: `OpsCatalogReducer`/`HazardsListReducer` 가 있으면 TDD 추가.
- `ai_agent/tests` 31/31 · `j2208a/tests` 43/43 — TBM 비의존 영역, 회귀 0 보장.

### SLAM 행동요령 UI (Claude's discretion)

가이드의 "Stop / Look / Assess / Manage" 4 단계 안내. Phase 11 의 design token (corner radius·typography) 잠금 후 결정. **추천**: `TbmStartSection` 상단에 info icon (Icons.Default.Info) → 클릭 시 AlertDialog 로 4 단계 본문 표시. 이미 Phase 9 `TbmStartSection.kt:295` 에 AlertDialog 패턴 (TimePicker) 있음 — 1:1 미러.

### Phase 11 의존성
디자인 token (corner radius·elevation·padding·typography) 가 Phase 11 에서 잠겨야 SettingOpsCatalogActivity 의 UI 가 따라감. **현황**: Phase 11 가 ∥ 병렬 진행 중. Plan 3 (Android) 시작 시점에 Phase 11 token 초안이라도 잡혀 있으면 따르고, 아니면 Phase 9 의 기존 token (corner=12dp, elevation=2dp 등) 유지 + Phase 14 UAT 직전 token 통합.

---

## OPS Seed Content (Plan 1 의 014 migration INSERT 본문 초안)

### Template 1: 지게차 (CITED: KOSHA 가이드 28페이지)

```sql
INSERT INTO public.tbm_templates (
  work_type, title, description, hazards, controls, key_actions, checks, target_detector
) VALUES (
  'forklift', '지게차 작업',
  '도금 라인 자재·완제품 운반 시 사용. 운전자격·출입통제·안전벨트 3대 핵심조치.',
  '[
    {"id":"h1","text":"보행자와 충돌 (좁은 통로·시야 사각)"},
    {"id":"h2","text":"적재물 낙하 (포크 과적·불균형 적재)"},
    {"id":"h3","text":"전도 (속도 과다·급정거·경사로)"},
    {"id":"h4","text":"협착 (포크·마스트·차체와의 끼임)"}
  ]'::jsonb,
  '[
    {"id":"c1","text":"좁은 통로에 지게차 진입 금지 (대체 통로 사용)","hazard_id":"h1","level":"대체"},
    {"id":"c2","text":"보행자 출입통제 라인 및 유도자 배치","hazard_id":"h1","level":"통제"},
    {"id":"c3","text":"포크 적재 한도 표시 + 운전자 적재 전 확인","hazard_id":"h2","level":"통제"},
    {"id":"c4","text":"제한속도 5km/h 이하 + 급정거 금지","hazard_id":"h3","level":"통제"},
    {"id":"c5","text":"안전벨트 착용 확인 (전도 시 차체 이탈 방지)","hazard_id":"h3","level":"통제"},
    {"id":"c6","text":"포크 작업반경 1m 내 출입금지선 표시","hazard_id":"h4","level":"통제"}
  ]'::jsonb,
  '[
    {"text":"❶ 운전자격 확인 (지게차 면허 + 사업장 내 운전 허가)"},
    {"text":"❷ 출입통제 라인 + 유도자 배치 (보행자 분리)"},
    {"text":"❸ 안전벨트 착용 확인 (전도 시 차체 이탈 방지)"}
  ]'::jsonb,
  '[
    "운전자격 확인 (면허 + 사내 허가증)",
    "지게차 작업계획서 비치",
    "안전수칙 미준수 시 작업중지 권한 명시",
    "후방경광등 / 후진경보음 동작 확인",
    "포크·체인·마스트 외관 점검",
    "안전벨트 동작 확인",
    "타이어 마모·공기압 점검",
    "작업장 통로 폭 확보 + 노면 평탄",
    "출입통제 라인·표지 설치",
    "유도자 1명 이상 배치 (사각지대 보행자 안내)",
    "적재물 결속·균형 확인",
    "포크 1~2단 (지면 가까이) 이동",
    "운전자 안전벨트 착용",
    "급정거·급회전 금지"
  ]'::jsonb,
  'forklift'
)
ON CONFLICT (work_type) DO NOTHING;
```

### Template 2: 화학물질 (ASSUMED — 도금 도메인 자체 정의, 가이드 외)

```sql
INSERT INTO public.tbm_templates (
  work_type, title, description, hazards, controls, key_actions, checks, target_detector
) VALUES (
  'chemical', '화학물질 취급 (산세·도금액)',
  '황산·염산·질산 등 산세조, 니켈·크롬·시안화물 도금조 취급 시 노출·튐·증기 흡입 위험 관리.',
  '[
    {"id":"h1","text":"산·알칼리 튐 → 피부·눈 손상"},
    {"id":"h2","text":"유증기 흡입 → 호흡기·점막 손상"},
    {"id":"h3","text":"시안화물 누출 → 청산 가스 생성 (산 접촉 시)"},
    {"id":"h4","text":"폐액 혼합 → 발열·돌비"}
  ]'::jsonb,
  '[
    {"id":"c1","text":"무인 자동공급 라인으로 대체 (인력 투입 제거)","hazard_id":"h1","level":"제거"},
    {"id":"c2","text":"산·도금조 분리 격벽 + 펌프 자동공급","hazard_id":"h1","level":"대체"},
    {"id":"c3","text":"내산 장갑·고글·앞치마·안면보호구 착용","hazard_id":"h1","level":"통제"},
    {"id":"c4","text":"국소배기장치 후드 위치·풍속 점검 (0.4m/s 이상)","hazard_id":"h2","level":"통제"},
    {"id":"c5","text":"시안조와 산조 동일 동선 금지 (혼입 사고 차단)","hazard_id":"h3","level":"통제"},
    {"id":"c6","text":"폐액 분류 보관·혼합 작업 금지 (지정 처리업체 위탁)","hazard_id":"h4","level":"통제"}
  ]'::jsonb,
  '[
    {"text":"❶ MSDS 비치 + 작업자 교육 이수 확인"},
    {"text":"❷ 보호구 완전착용 (내산 장갑·고글·앞치마·호흡보호구)"},
    {"text":"❸ 비상샤워·세안기 가동 확인 + 5m 이내 위치"}
  ]'::jsonb,
  '[
    "MSDS 게시 + 작업자 숙지",
    "보호구 (내산 장갑·고글·앞치마·호흡보호구) 착용",
    "비상샤워·세안기 동작 확인 + 5m 이내 위치",
    "국소배기장치 가동 (후드 풍속 0.4m/s 이상)",
    "산조·도금조·시안조 동선 분리",
    "약품 농도·온도 표시판 부착",
    "폐액 분리 보관 + 라벨링",
    "환기 상태 (체류 가스 측정 50ppm 이하)",
    "비상연락망 + 응급조치 절차 게시",
    "출입통제 + 외부인 진입 차단"
  ]'::jsonb,
  'fire'
)
ON CONFLICT (work_type) DO NOTHING;
```

### Template 3: 고온·열처리 (ASSUMED — 도금 도메인 자체 정의, 가이드 외)

```sql
INSERT INTO public.tbm_templates (
  work_type, title, description, hazards, controls, key_actions, checks, target_detector
) VALUES (
  'hot_work', '고온·열처리 작업',
  '전기도금 라인 발열·열처리로·건조로 운영 시 화상·화재·증기 위험 관리.',
  '[
    {"id":"h1","text":"고온 표면 접촉 → 화상"},
    {"id":"h2","text":"가연성 도금액 인접 → 발화·화재"},
    {"id":"h3","text":"고온 증기·미스트 흡입 → 호흡기 손상"},
    {"id":"h4","text":"열처리로 폭발 (가스 누출·과압)"}
  ]'::jsonb,
  '[
    {"id":"c1","text":"열처리로 자동 인터록 (도어 열림 시 가열 차단)","hazard_id":"h4","level":"제거"},
    {"id":"c2","text":"고온부 단열재 피복 + 안전망 설치","hazard_id":"h1","level":"대체"},
    {"id":"c3","text":"내열 장갑·앞치마·안면보호구 착용","hazard_id":"h1","level":"통제"},
    {"id":"c4","text":"열처리로 주변 2m 가연물 비치 금지","hazard_id":"h2","level":"통제"},
    {"id":"c5","text":"강제배기 + 외부 토출 (실내 농도 25ppm 이하)","hazard_id":"h3","level":"통제"},
    {"id":"c6","text":"가스 누출 감지기 + 자동 차단밸브","hazard_id":"h4","level":"통제"}
  ]'::jsonb,
  '[
    {"text":"❶ 가연물 격리 + 소화기 5m 이내 비치"},
    {"text":"❷ 내열 보호구 완전착용 (장갑·앞치마·안면보호구)"},
    {"text":"❸ 가스 누출 감지기 정상 + 비상정지 버튼 위치 숙지"}
  ]'::jsonb,
  '[
    "열처리로·건조로 외관·인터록 점검",
    "고온부 단열 피복 + 안전망 상태",
    "내열 장갑·앞치마·안면보호구 착용",
    "주변 2m 가연물 제거",
    "소화기·소화전 5m 이내 비치 + 점검필 확인",
    "가스 누출 감지기 동작 (가연성 25%LEL 이하)",
    "강제배기·국소배기 가동",
    "비상정지 버튼 위치·동작 확인",
    "온도 표시판 + 작업 한계 온도 게시",
    "통신수단 + 비상연락망"
  ]'::jsonb,
  'fire'
)
ON CONFLICT (work_type) DO NOTHING;
```

**Note for planner:** 화학물질·고온 OPS content 는 `[ASSUMED]` — 도금/금속가공 도메인 일반 지식 기반 초안. 실제 검단·포천 현장 작업 내용 + 사업주 안전관리자 검토 후 seed.sql commit 권장. 회의록 양식 적합도는 OK (잠재위험·대책 level·핵심조치 3개 구조 일관).

---

## Pitfalls

Phase 9 의 검증된 12 pitfall + Phase 12 신규 surface:

### 유지 (Phase 9 evidence — 코드 그대로 재사용)
- **P1·P2 (Compose Canvas signature)** — SignatureCanvas.kt 변경 0. Bitmap.recycle finally + currentPath setter 그대로.
- **P3 (FCM channel)** — Option B `fcm_default_channel` 재사용, channel_id 명시 X. Android 코드 변경 0.
- **P8 (ISO_OFFSET_DATE_TIME)** — ExpectedEndAtValidator 변경 0.
- **P9 (sendPushToUsers Set dedup)** — fcm.ts:253 그대로. `tbm-missed` 의 recipients = missedIds + leader 패턴 유지.
- **P11 (JSONB array order)** — Postgres JSONB array 순서 보존 보장. `hazards_snapshot`/`controls_snapshot` 도 동일 적용. `tbm_checklists` bulk insert 시 map index 강제 패턴 (notifications/index.ts:483-485) 그대로.
- **P12 (ComposeView Theme 래핑)** — SettingOpsCatalogActivity 의 setContent block 안에 `Smart_Safety_ManagementTheme { … }` 래핑 필수. TbmDashboardActivity.kt:37 패턴 미러.
- **P-Gson (snake_case dual annotation)** — TbmModels.kt:89-93 의 fix 유지. 신규 model class (`OpsCreateRequest`, `OpsToggleRequest`) 도 동일 dual annotation 필수.

### 신규 (Phase 12 surface)
- **P-NEW-1 (UNIQUE 트리거 컬럼 변경)** — `tbm-start` 의 23505 catch 가 (group_id, session_date) 가정으로 작성되어 있음 (notifications/index.ts:473). 컬럼이 3-튜플로 바뀌면 catch shape 자체는 동일 (Postgres 의 23505 는 any unique violation) 이지만 error message 가 변경 — 응답 텍스트 갱신: `"이미 오늘 ${work_scope} 세션이 존재합니다"`. **회귀 가드**: Phase 9 의 12 smoke test 중 `same group same date same scope → 409` 시나리오 신규 추가.
- **P-NEW-2 (todaySessionFlow shape mismatch)** — TbmRepository.kt:42 `decodeSingleOrNull` 가 N rows 만나면 throw 가 아닌 `null` 반환 (kotlinx-supabase 동작), 실제로는 첫 row 잠재 silent skip. **반드시 `decodeList` 로 교체** + UI 가 LazyColumn 으로 받음.
- **P-NEW-3 (Compose state hoisting — hazards/controls list)** — `var hazards by remember mutableStateOf<List<TemplateHazard>>(...)` 의 immutable list 패턴. add/edit/delete 시 `hazards = hazards + new` 또는 `hazards.filterIndexed { i,_ -> i != idx }` 으로 새 list 생성 (Compose recomposition trigger). MutableList 직접 mutate 하면 recomposition 안 됨 — Phase 9 의 `TbmStartSection.kt` 의 `selectedGroupIds by remember mutableStateOf<Set<Int>>` 패턴 (line 74) 미러.
- **P-NEW-4 (OPS 비활성화 race)** — manager A 가 OPS 비활성화 + 동시에 manager B 가 그 OPS 로 세션 시작. 결과 — 세션은 INSERT 되고 `hazards_snapshot`/`controls_snapshot` 은 보존 (snapshot 패턴이 자연 mitigation). 단 `tbm_templates` 의 `is_active=false` row 가 dropdown 에서 disappear 시점이 manager B 의 client cache 와 race — Edge Function 의 `tbm-start` 가 `is_active=true` 마지막 검증 (`tmpl.is_active === false → 400 "OPS 비활성화됨"`) 추가 권장.
- **P-NEW-5 (worker FCM 알림 disambiguation)** — N 세션 동시 진행 시 worker 가 어느 세션 알림인지 모름. FCM `data.work_scope` 추가 + MyFirebaseMessagingService 의 `tbm_alert` action-routing 이 `work_scope` 표시 (또는 deep-link 에 session_id 전달).
- **P-NEW-6 (지게차 자체 ALLOWED 변경에 의한 unit test break)** — `WorkTypeValidatorTest` 가 'fire'/'electric'/etc 의 isValid=true 를 가정. 'forklift'/'chemical'/'hot_work' 로 바뀌면 test 도 함께. Plan 3 시작 전 Plan 1 commit 후 test 갱신 필수.

---

## Threat Model Updates

Phase 9 의 T-9-01 ~ T-9-15 중 Phase 12 에 영향:

| ID | 위협 | Phase 12 변화 |
|----|-----|--------------|
| T-9-01 | signature PII | 변경 없음 (Storage 버킷 동일) |
| T-9-02 | UNIQUE bypass | **트리거 컬럼 변경** — `(group, date, work_scope)` 로 재정의, Edge Function 23505 catch 그대로, 응답 텍스트만 |
| T-9-03 | cross-group spoofing | 변경 없음 (`tbm-checkin` 의 profile.group_id 검증 유지) |
| T-9-04 | leader-impersonation | 변경 없음 (D-08 — manager/general_manager 권한 가드 유지) |
| T-9-05 | Vault DoS | 변경 없음 (graceful skip 패턴 유지) |
| T-9-07 | Vault git 노출 | 변경 없음 |
| T-9-08 | anon 임의 INSERT | **tbm_templates 의 write 정책 변경 시 재검토** — Option B 채택 시 RLS 조건식 narrowing 필요 |
| T-9-13 | worker 가 manager 행세 | **SettingOpsCatalogActivity 신규** — manager-only 진입 가드 (Phase 9 TbmDashboardActivity 패턴 미러) |

### 신규 위협 (Phase 12 surface)

| ID | 위협 | 시나리오 | 완화 |
|----|-----|---------|------|
| **T-12-01** | OPS 카탈로그 권한 우회 | worker 가 SettingOpsCatalogActivity 직접 deep-link 또는 인텐트 호출로 진입 → 모든 OPS 카탈로그 노출 | `android:exported="false"` + Activity onCreate 첫 줄 `UserSession.userRole != MANAGER → finish()`. Phase 9 T-9-13 mitigation 패턴 1:1 미러 |
| **T-12-02** | work_scope SQL injection | manager 가 work_scope 입력에 SQL 메타문자 (`;DROP TABLE …`) 주입 | Supabase Postgrest 가 parameterized query 강제 (raw SQL 미사용) — 자연 mitigation. 단 input length cap 80자 권장 (DoS) |
| **T-12-03** | JSONB nested injection | manager 가 OPS 신규 추가 시 hazards array 안 객체에 추가 키 (`{"id":"h1","text":"…","admin":true}`) 주입 → 향후 자체 권한 escalation | Edge Function `ops-create` 에서 JSON schema 화이트리스트 강제 (`{id, text}` 만 통과) |
| **T-12-04** | is_active 토글 race | 두 manager 가 동시에 is_active 토글 → 마지막 쓰기 승 | Phase 9 D-09 알림 전이 패턴 유사 — last-writer-wins acceptable (안전 critical 아님). v1.2 에서 optimistic locking 검토 |
| **T-12-05** | OPS 비활성화 + 진행중 세션 | manager 가 OPS 비활성화하면 진행중 세션의 hazards/controls 가 사라짐 | Snapshot 패턴 (D-04 추천) 으로 자연 mitigation — `hazards_snapshot`/`controls_snapshot` 이 session row 안 |
| **T-12-06** | tbm_templates anon UPDATE 남용 | Option B (직접 PATCH) 채택 시 anon 이 임의 row 변경 | `WITH CHECK (target_user_role = ANY('{manager,general_manager}'))` 또는 service_role-only 패턴 |

---

## Wave Recommendation

**Phase 9 의 autonomous wave chain 그대로 미러:**

| Wave | Plan | Scope | Depends on | 병렬 가능 |
|------|------|-------|-----------|----------|
| 1 | **Plan 1: Schema (014 migration)** | DROP+RECREATE 4 tables + 신규 컬럼 (work_scope, hazards/controls/snapshot/feedback) + RLS + Realtime publication re-ADD + pg_cron 재등록 + 3종 도금 OPS seed | — | — (먼저 commit) |
| 2 | **Plan 2: Edge Function** | notifications/index.ts amend (tbm-start hazards snapshot + work_scope · tbm-end key_hazard_id/feedback · ops-create/ops-update/ops-toggle 신규 case) | Plan 1 | Plan 3 와 ∥ |
| 2 | **Plan 3: Android UI** | WorkTypeValidator rewrite · TbmModels 컬럼 추가 · TbmRepository shape 변경 · TbmDashboardScreen N-session list · TbmStartSection work_scope+hazards prefill · SettingOpsCatalogActivity+OpsCatalogScreen+OpsEditScreen 신규 · SettingActivity 진입점 추가 · Home 카드 summary refactor (D-05) · AndroidManifest 등록 | Plan 1 | Plan 2 와 ∥ |
| 3 | **Plan 4: 합성 검증 + 시연** | 12 smoke test (4 case × 3 시나리오) · WorkTypeValidatorTest 갱신 · ai_agent 31/31 + j2208a 43/43 회귀 · 합성 검증 cycle (synthetic session + checkin + end) · 1일 사이클 시연 (deferred per Phase 7-04/8 RTSP-02 패턴) | Plans 1,2,3 | — |

**Rationale:**
- Plan 1 (schema) 가 Plan 2·3 의 contract 정의. Plan 1 commit 후에야 Plan 3 의 WorkTypeValidator 가 새 work_types 알 수 있음.
- Plan 2·3 은 contract 만 합의되면 ∥ — Edge Function 의 payload shape 와 Android 의 Retrofit body 가 양쪽 owner.
- Plan 4 는 모든 commit 후 합성 검증. Phase 9 의 24/24 PASS 패턴 유지.

**Single PLAN.md 추천 안 함** — Phase 12 는 4 영역 (SQL/TS/Kotlin/Seed) 모두 touch + threat model 신규 6개. autonomous wave chain 의 격리 가치 (각 plan 의 회귀 surface 명확) 큼.

---

## Open Questions (planner 결정 OR discuss-phase 재확인)

1. **OPS 활성/비활성 변경 시 진행중 세션 — snapshot vs live** — 본 RESEARCH 추천 snapshot (D-04 + Phase 9 패턴). planner 가 동의하면 014 schema 안 `hazards_snapshot`/`controls_snapshot` 추가. live 채택 시 child table 분리 권장.
2. **tbm_templates write policy — Option A (Edge Function) vs B (직접 PATCH)** — 본 RESEARCH 추천 Hybrid (toggle=B, create/update=A). planner 가 단일 패턴으로 통일하려면 A 채택.
3. **work_scope ENUM vs TEXT** — 본 RESEARCH 추천 TEXT (D 결정 영역, v1.2 ENUM 화 검토). planner 가 도금 도메인 5종 (산세/도금조/후처리/검사/운반/기타) 이미 lock 가능하다 판단하면 ENUM.
4. **SLAM UI 표현 — info icon→modal vs inline vs Phase 11 token 대기** — 본 RESEARCH 추천 info icon→modal (AlertDialog 재활용). Phase 11 의 modal token 잠금 후 통합.
5. **회의록 양식 7번 핵심 필드 (작업 후 종료 미팅 + 환류 조치)** — `tbm-end` payload 에 추가 vs 별도 `tbm-feedback` 신규 case. 본 RESEARCH 추천: `tbm-end` 확장 (key_hazard_id + feedback_notes 인자 추가) — 단일 transaction.
6. **Storage 의 Phase 9 orphan signature 처리** — keep (cheap) vs 014 안 `DELETE FROM storage.objects WHERE bucket_id='tbm-signatures'`. 본 RESEARCH 추천 keep.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Supabase remote project | 014 migration + Edge Function 배포 | ✓ | linked | — |
| Deno (Edge Function) | notifications/index.ts amend | ✓ (Supabase 호스팅) | — | — |
| Android Studio + Kotlin/Compose | tbm/ 패키지 refactor + SettingOpsCatalogActivity | ✓ | (D:/ssm-app-build 워크어라운드) | — |
| psql | 014 migration 로컬 검증 | ✗ | — | `supabase db push --linked --yes` 단일 path (CLAUDE.md 명시) |
| pdftotext | KOSHA 가이드 28페이지 재추출 (CITED 확인) | ✓ | poppler | — (이미 추출함, CONTEXT.md 인용) |

**Missing dependencies with no fallback:** 없음 — 모두 fallback 또는 가능.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 (Android unit test, Phase 9 의 4 TDD test) + pytest (ai_agent 31, j2208a 43) |
| Config file | `app/src/test/` (Android) · `pytest.ini` (Python) |
| Quick run command | `./gradlew -p D:/ssm-app-build test` (Android only, ~30s) |
| Full suite command | Android unit + ai_agent + j2208a + smoke 12 case |

### Phase 12 Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TBM-04 | UNIQUE 트리거 변경 (group+date+scope) | smoke | `curl -X POST notifications` × 3 시나리오 | ❌ Wave 4 |
| TBM-04 | WorkTypeValidator new ALLOWED | unit | `gradlew test --tests "*WorkTypeValidatorTest"` | ⚠️ 갱신 (Phase 9 파일 존재) |
| TBM-05 | hazards_snapshot insert | smoke | `curl -X POST tbm-start --data '{...hazards:[…]}'` | ❌ Wave 4 |
| TBM-06 | N-session list rendering | manual | TbmDashboardActivity 실기기 진입 시 N 카드 표시 | manual-only (Compose UI) |
| TBM-07 | 3종 도금 OPS seed 검증 | sql | `SELECT count(*) FROM tbm_templates WHERE work_type IN ('forklift','chemical','hot_work')` = 3 | ❌ Wave 1 (014 적용 직후) |
| TBM-08 | SettingOpsCatalogActivity manager-only | manual | worker 계정으로 진입 시도 → finish() | manual-only |

### Sampling Rate
- **Per task commit:** `gradlew test` (Android unit, ~30s)
- **Per wave merge:** Android unit + ai_agent 31/31 + j2208a 43/43 (Phase 9 baseline)
- **Phase gate:** 12 smoke + 합성 검증 24/24 (Phase 9 패턴 미러) + 1일 사이클 시연 (Phase 7-04/8 RTSP-02 패턴 — deferred 가능)

### Wave 0 Gaps
- [ ] `WorkTypeValidatorTest` ALLOWED set 신규 work_types 로 갱신
- [ ] 신규 `OpsCatalogReducerTest` (hazards list add/edit/del state) — `TbmParticipantsReducerTest.kt` 패턴 미러
- [ ] 12 smoke test scenarios 신규 작성 (Phase 9 의 4-case 12 시나리오 base + work_scope 분기 추가)

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Supabase service_role JWT (Edge Function path) + anon key (client) — Phase 9 패턴 유지 |
| V3 Session Management | partial | `UserSession.userRole` 기반 (Supabase Auth 부재 v1.0 PoC 그대로) |
| V4 Access Control | yes | manager-only Activity 가드 (T-12-01) + `is_active` 토글 (Option B 시 RLS narrowing) |
| V5 Input Validation | yes | work_scope length cap + JSONB schema 화이트리스트 (T-12-02·T-12-03) — Edge Function 안 |
| V6 Cryptography | no | 신규 crypto 사용 없음 (Storage signature PNG 는 Phase 9 그대로) |

### Known Threat Patterns for Supabase Postgrest + Deno Edge Function + Compose

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via work_scope | Tampering | Supabase Postgrest parameterized query 강제 (T-12-02) |
| JSONB nested injection | Tampering | Edge Function ops-create 의 JSON schema 화이트리스트 (T-12-03) |
| Deep-link bypass | Spoofing | `android:exported="false"` + role 가드 (T-12-01) |
| anon UPDATE 남용 | Tampering | RLS write policy narrowing 또는 service_role-only (T-12-06) |

---

## Project Constraints (from CLAUDE.md)

- **한글 인코딩**: `\uXXXX` escape 금지 (codepoint 환각). UTF-8 직접 입력. 본 RESEARCH 의 한글 문자열은 직접 입력 검증됨.
- **프로젝트 경로 한글**: `D:\2026_산업안전\…` — Android 빌드 output 은 `D:/ssm-app-build` redirect (Korean path workaround, app/build.gradle.kts:107 JEP 400 회피).
- **`gsd-sdk` 미설치**: 모든 GSD 명령 수동 처리, 파일 직접 edit + commit. 본 RESEARCH 도 직접 Write 로 작성.
- **`psql` 미설치**: Supabase 작업은 PostgREST + Management API 우회. 014 migration 은 `supabase db push --linked --yes` 단일 path.
- **회귀 가드**: ai_agent 31/31 + j2208a 43/43 = 74 tests 무회귀 유지. compileDebugKotlin BUILD SUCCESSFUL. watch/ + Daily*.kt + ai_agent/ git diff 0 (관련 없는 영역 보존).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 화학물질 OPS의 잠재위험 4개 (튐·증기·시안가스·폐액혼합) 가 도금 작업장의 실제 위험 우선순위와 일치 | §OPS Seed Content Template 2 | seed.sql commit 후 현장 검토에서 추가/삭제 필요 — 14 migration patch 추가. severity 낮음 (manager UI 로 신규 추가/수정 가능) |
| A2 | 고온·열처리 OPS의 가스농도 25%LEL 기준 + 환기 25ppm 이하가 도금 라인 표준 | §OPS Seed Content Template 3 | 사업장별 다름 (관리자가 사후 수정). v1.2 에서 환경 sensor 연계 시 정밀화 |
| A3 | `work_scope` TEXT 자유입력이 manager UI에 적합 (vs ENUM) | §Schema + Open Q3 | manager 가 매번 다르게 입력하면 통계·필터 약함. v1.2 ENUM 화 (운영 후 패턴 수집) |
| A4 | snapshot 패턴 (Phase 9의 `tbm_checklists.item_text` 미러) 이 OPS 비활성화 시 진행중 세션 보존 요구를 만족 | §Schema + Open Q1 | live 채택 시 진행중 세션 깨짐. snapshot 채택의 단점 = OPS 텍스트 수정이 진행중 세션에 반영 안 됨 (의도된 trade-off) |
| A5 | tbm_templates 의 직접 PATCH (is_active 토글, Option B) RLS 추가가 안전 | §Edge Function + Open Q2 | RLS 조건식이 정확치 않으면 anon 이 임의 row 토글 (T-12-06). Edge Function 단일 path (Option A) 가 안전 |
| A6 | FCM `tbm_alert` payload 에 `work_scope` 추가가 worker 가 N 세션 구분에 충분 | §Edge Function + Pitfall P-NEW-5 | worker UI 가 deep-link 로 session_id 받아도 list 화면에서 일관된 구분 필요. UI 디자인 (Phase 11 token) 협업 필요 |

**Action for discuss-phase or planner:** A1·A2 는 도금 도메인 전문가 (사업주 안전관리자) 검토 권장. A3·A4·A5 는 Open Questions §1·§2·§3 으로 planner 결정 가능.

---

## Sources

### Primary (HIGH confidence)
- `D:\2026_산업안전\Smart_Safety_Management\supabase\migrations\013_tbm_schema.sql` — 검증됨, 본 RESEARCH 의 패턴 base
- `D:\2026_산업안전\Smart_Safety_Management\supabase\functions\notifications\index.ts` — 검증됨, 4 TBM case 의 23505 dedup·snapshot·FCM·SECURITY DEFINER 패턴
- `D:\2026_산업안전\Smart_Safety_Management\app\src\main\java\com\example\smart_safety_management\tbm\*.kt` — 12 main 파일 검증, refactor surface 명확
- `D:\2026_산업안전\Smart_Safety_Management\.planning\phases\09-tbm-worker-guide\09-CONTEXT.md` — 9 gray-area 결정 + threat model T-9-01~15
- `D:\2026_산업안전\Smart_Safety_Management\.planning\phases\12-tbm-redesign-kosha-guide\12-CONTEXT.md` — 9 locked decisions
- KOSHA 가이드 28페이지 (지게차 OPS) — CONTEXT.md 인용 (pdftotext 검증)

### Secondary (MEDIUM confidence)
- CONTEXT.md 의 "회의록 양식 7개 핵심 필드" 요약 (가이드 42페이지) — 본 RESEARCH 가 schema mapping 으로 흡수

### Tertiary (LOW confidence, flagged)
- 화학물질·고온 OPS 내용 (가이드 외 자체 정의) — `[ASSUMED]` 태그, Assumptions Log A1·A2

---

## Metadata

**Confidence breakdown:**
- Schema design: HIGH — Phase 9 패턴 미러, snapshot 의 hazards/controls 확장은 logical extension
- Edge Function: HIGH — 23505 catch shape 동일, work_scope 추가는 1-line addition
- Android UI: MEDIUM — `todaySessionFlow` shape 변경이 caller 의 LazyColumn refactor 트리거, Compose state hoisting (hazards list) 의 recomposition 함정 신규
- OPS Seed Content (지게차): HIGH — KOSHA 가이드 28페이지 CITED
- OPS Seed Content (화학물질·고온): LOW — 도금 도메인 자체 초안, Assumptions Log A1·A2 flag
- Pitfalls: HIGH — Phase 9 의 12 pitfall + 6 신규 surface 식별
- Threat Model: MEDIUM — T-9-01~15 mapping + T-12-01~06 신규 식별, 단 RLS narrowing 조건식 정확도는 planner 가 014 작성 시 검증

**Research date:** 2026-05-26
**Valid until:** 2026-06-09 (2주, Phase 9 결과물 안정 + Phase 11 token 잠금 dependency)
