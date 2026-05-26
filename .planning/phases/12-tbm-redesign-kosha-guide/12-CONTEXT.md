# Phase 12: TBM 재설계 (KOSHA 가이드 흡수) - Context

**Gathered:** 2026-05-23
**Status:** Ready for planning
**Milestone:** v1.1 앱 전체 완성도
**Predecessor:** Phase 9 (TBM 현장 작업자 가이드, SHIPPED 2026-05-18) — schema·Edge Function·Android 패키지 모두 재사용 가능, 단 schema 핵심 제약 (`UNIQUE (group_id, date)`) 은 제거

---

<domain>
## Phase Boundary

KOSHA `230209 작업 전 안전점검회의 가이드` 의 작업·공정별 다중 세션 + 회의록 양식 핵심 필드를 흡수하고, 도금/금속가공 도메인 OPS 를 시드하여, 검단·포천 현장 관리자/작업자가 실제 사용 가능한 TBM 운영 시스템을 갖춘다.

**In scope (REQ TBM-04~08)**:
- `tbm_sessions` UNIQUE 제거 + `work_scope` 컬럼 추가 → 작업·공정별 다중 세션
- KOSHA 회의록 양식 핵심 필드 schema mapping — 잠재위험요인 list / 중점위험 1개 / 대책 (제거→대체→통제) / 자율점검 항목 (yes/no) / 환류 조치
- Android UI 재구성 — 세션 list view + 공정명 입력 + 위험요인 입력 + SLAM 행동요령
- 도금/금속가공 도메인 OPS 3종 (지게차·화학물질·고온) seed + prefill 하이브리드
- 관리자 OPS 카탈로그 관리 UI (활성/비활성 토글 + 신규 추가)

**Out of scope (v1.2+ 또는 별 milestone)**:
- 가이드 12종 OPS 中 도금 도메인 외 11종 (크레인·컨베이어·후크·샤클·혼합기 등) — 6월 현장 조사 후 카탈로그 추가
- 가이드의 4 카테고리 구분 (관리적 사항 / 안전장치 설치 / 작업 중 / 준수사항) — UI 복잡도 비례 가치 부족, 자율점검은 flat list
- AI 비전 detector 동적 연계 (conf threshold 조정·알림 우선순위 hint) — v1.2 별도 검토 (현재는 work_scope 만 기록)
- 외국인 작업자 통·번역 — 6월 현장 조사 시 외국인 비율 확인 후 v1.2
- 회의록 PDF/이미지 export — v1.2 별도 (v1.1 은 앱 안 view 만)

</domain>

<decisions>
## Implementation Decisions

### D-01 OPS prefill 동작 (Area A-1, D7)
- 사용자가 작업 종류 (예: 지게차) 선택 시 잠재위험·대책이 폼 칸에 **자동 채워짐 (prefill)**
- 사용자는 수정·삭제·신규 추가 자유
- 자체 추가한 항목은 `is_custom = true` 태그로 구분 (DB row 또는 JSONB 필드)
- 가이드의 "TBM 은 마지막 위험성평가" 원칙 + 사용자 부담 절감 사이의 절충

### D-02 OPS template 깊이 (Area A-2, D8)
- OPS row 의 schema = **잠재위험 list + 대책 list (제거/대체/통제) + 핵심 안전조치 3개 + 자율점검 항목 list (yes/no)**
- 가이드의 4 카테고리 분류 (관리적 사항 / 안전장치 / 작업 중 / 준수사항) 는 **흡수 안 함** — flat list 로
- Phase 9 의 `tbm_templates.checklist_template` (JSONB) 를 자율점검 항목 컨테이너로 재활용
- 잠재위험·대책·핵심조치는 별도 JSONB 필드로 신규 추가

### D-03 TBM ↔ AI 비전 detector 연동 (Area A-3, D9)
- TBM 세션 row 의 `work_scope` 만 DB 적재. AI 비전 시스템 동작 변화 **없음**
- v1.2 에서 'work_scope 별 detection_events 교차 조회 대시보드' 같은 후속 작업
- detector hint (conf threshold 동적 조정 등) 은 v1.2 로 이연

### D-04 마이그레이션 path (Area B-1, D10)
- 새 migration `014_tbm_v2_schema.sql` 에서 기존 `tbm_*` 4 테이블 **DROP + RECREATE** (destructive)
- 기존 Phase 9 시드 데이터 (5 templates fire/electric/general/heavy/height + 시드 세션 row + 참여자 row) 전부 제거
- 도금 도메인 OPS 3종 신규 시드: **지게차 + 화학물질 + 고온**
- 운영 DB 의 기존 row 가 적어 안전한 path. generic 5 templates 가 도메인 부적합 noise 였음

### D-05 Home 의 TBM UI (Area B-2, D11)
- Home 카드 (manager + worker 양쪽) 는 **요약만 표시** — "오늘 N개 세션·진행 중 M개·미참여 K명"
- 자세한 list 와 신규 세션 시작 버튼은 `TbmDashboardActivity` 진입 후
- Home 공간 절약 + 다른 카드 (워치/카메라/일일점검/알림) 와 일관 시각 언어 유지 (Phase 11 의 UX-02 와 일치)

### D-06 관리자 OPS 카탈로그 UI 위치 (Area C-1, D12)
- 신규 `SettingOpsCatalogActivity` 추가 — 기존 `Setting*` 시리즈 안 신규 항목
- 진입점: SettingActivity (manager 전용) 의 메뉴 list 에 "OPS 관리" 추가
- Phase 11 의 UX-03 (Setting* 일관 패턴) 과 통합 디자인

### D-07 신규 OPS 작성 시 필수 필드 (Area C-2, D13)
- **필수**: 이름 (work_type 명) + 잠재위험 list (≥1) + 대책 list (≥1)
- **선택**: 핵심 안전조치 3개 + 자율점검 항목 list + 설명 + detector hint metadata
- 빈 필수 필드 있으면 저장 차단, 선택 필드는 비어도 OK
- 추후 사용자가 수정 가능 (편집 UI 동일 form 재활용)

### D-08 리더 모델 (Area E-1, D14)
- **자유** — 한 리더가 여러 세션 리딩 OR 세션마다 다른 리더 둘 다 허용
- 단 권한 가드: `user_role IN ('manager','general_manager')` 만 리더 가능 (worker 는 리더 불가, Phase 9 T-9-13 동일)
- `tbm_sessions.leader_user_id` UNIQUE 같은 제약 X — 같은 사용자가 N 세션 leader 가능

### D-09 작업자 참여 모델 (Area E-2, D15)
- 한 작업자가 하루 N 세션 참여 가능 (예: 오전 산세 + 오후 도금 + 야간 검사)
- `tbm_participants` UNIQUE (session_id, user_id) 는 유지 — 같은 세션 중복 참여만 차단
- 다른 세션 간에는 제약 없음

### Claude's Discretion (downstream 결정)
- `work_scope` 컬럼의 정확한 type — TEXT (free) vs ENUM (산세/도금조/후처리/검사/운반/기타) — planner 단계 결정
- 잠재위험·대책 list 의 schema — JSONB array vs 별도 child table — planner 결정 (성능·쿼리 패턴 따라)
- SLAM 행동요령 UI 표시 — 모달·info icon·inline tooltip 중 택1 — Phase 11 token 확정 후 결정
- 관리자가 OPS 비활성 시 진행 중 세션의 OPS 참조는 그대로 보존 (snapshot) vs OPS 활성 변경 동기화 (live) — planner 결정 (Phase 9 의 tbm_checklists session-snapshot 패턴 재활용 권장)

</decisions>

<specifics>
## Specific References

- KOSHA 회의록 양식 (가이드 42페이지) 의 7개 핵심 필드:
  1. TBM 일시 (작업날짜와 동일 여부)
  2. 작업명 + 작업내용 + TBM 장소 + 위험성평가 실시여부
  3. 잠재위험요인 list + 대책 (제거→대체→통제 순서)
  4. 중점위험요인 1개 선정 + 대책
  5. TBM 리더 정보 (소속·직책·성명·서명)
  6. 작업 전 안전조치 확인 (잠재위험요소별 yes/no + 조치 내용)
  7. 작업 후 종료 미팅 + 참석자 확인 + 불참자 추적

- KOSHA 가이드 III장 OPS 양식 (예: 가이드 35페이지 "굴착기"):
  - 핵심 안전조치 ❶❷❸ (3개 강조)
  - 자율점검 항목 ①~⑬ (yes/no 체크리스트)
  - 4 카테고리 (관리적 사항 / 안전장치 / 작업 중 / 근로자 준수사항) — v1.1 에서는 채택 안 함

- 도금/금속가공 도메인 OPS 3종 신규 시드:
  - **지게차**: 가이드 28페이지 (지게차 OPS) 기반, target_detector=`forklift`
  - **화학물질**: 자체 정의 — 산세·도금액 (황산·염산·니켈·크롬·시안화물) 노출·튐 위험, target_detector=`fire` (간접)
  - **고온·열처리**: 자체 정의 — 전기도금 라인 발열·열처리로 위험, target_detector=`fire`

</specifics>

<canonical_refs>
## Canonical References

### Phase 12 planning
- `C:\Users\ANNA\Downloads\230209 작업 전 안전점검회의 가이드(배포용).pdf` — KOSHA TBM 가이드, 42페이지. 회의록 양식 (42p) + 12종 OPS (28~39p) + 단계별 활동 (7~26p).
- `.planning/REQUIREMENTS.md` — v1.1 의 TBM-04·05·06·07·08 정의.
- `.planning/ROADMAP.md` — v1.1 Phase 12 Goal 및 5개 SC.

### Phase 9 결과물 (재사용)
- `supabase/migrations/013_tbm_schema.sql` — 기존 4 테이블 정의. Phase 12 의 `014_tbm_v2_schema.sql` 가 이를 DROP + RECREATE.
- `supabase/functions/notifications/index.ts` — Phase 9 의 4 case (tbm-start / tbm-checkin / tbm-end / tbm-missed). Phase 12 에서 N 세션·work_scope 인자 추가로 amend.
- `app/src/main/java/com/example/smart_safety_management/tbm/` — Phase 9 의 12 main + 4 test 파일. SignatureCanvas·Reducer·Repository 재사용, Screen·Activity 는 list view 로 refactor.
- `.planning/phases/09-tbm-worker-guide/09-CONTEXT.md` — Phase 9 의 9 gray area 결정 (특히 D-01 schema / D-03 signature / D-05 missed-attendance).

### 관련 코드
- `app/src/main/java/com/example/smart_safety_management/SettingActivity.kt` — 관리자 설정 메뉴 시리즈, SettingOpsCatalogActivity 진입점 추가 예정.
- `app/src/main/java/com/example/smart_safety_management/HomeActivity.kt` (`setupTbmDashboardCard`) + `HomeWorkerActivity.kt` (`setupTbmCard`) — 요약 카드 refactor.
- `app/src/main/AndroidManifest.xml` — TbmDashboardActivity / TbmWorkerActivity 등록 (기존) + SettingOpsCatalogActivity 신규.

### Phase 11 의존성 (병렬 진입 가능, 결과물 공유)
- Phase 11 의 design token (corner radius·elevation·padding·typography) 가 Phase 12 의 list view·OPS form UI 에서도 사용. Phase 11 이 먼저 잠기면 Phase 12 UI 작업이 token 따라감. 동시 진행 시 token 초안 (Phase 11 의 discuss CONTEXT) 잠시 후 Phase 12 UI 결정.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets (Phase 9 그대로 활용)
- `SignatureCanvas` Composable + `SignatureState` (Compose Canvas signature, Bitmap.recycle finally, currentPath setter Pitfall 1·2 mitigation)
- `TbmParticipantsReducer` (Phase 7 SafetyAlertReducer 패턴 mirror)
- `WorkTypeValidator` + `ExpectedEndAtValidator` (ISO_OFFSET_DATE_TIME) — 동일 검증 로직 유지, work_scope 가 추가될 뿐
- `MyApp.supabase` singleton (`Realtime + Postgrest + Storage + ktor-cio`) — Storage 의 tbm-signatures 버킷 재사용
- `MyFirebaseMessagingService` 의 `tbm_alert` action-routing 분기 — N 세션 alert 도 동일 분기 사용 가능 (FCM payload 에 session_id 추가)
- pg_cron `tbm_missed_attendance_minute` (1분 주기 SECURITY DEFINER) — 미참여 추적 패턴 유지, work_scope 별로 group 만 변경

### 신규 작성
- `014_tbm_v2_schema.sql` — 013 의 4 테이블 DROP + 새 schema RECREATE. UNIQUE 제거 + `work_scope` 컬럼 + 잠재위험·대책·핵심조치·자율점검 JSONB 또는 child table.
- `SettingOpsCatalogActivity` + Compose Screen — 관리자 전용 OPS 관리 UI (활성/비활성 토글 + 신규 추가 form + 편집 form).
- `TbmDashboardScreen` refactor — 단일 카드 view → LazyColumn (N 세션 list).
- `TbmStartSection` refactor — work_scope 입력 + OPS 선택 + prefill 후 잠재위험·대책 폼 (편집 가능).
- `SettingOpsCatalogRepository` — Supabase Postgrest CRUD for `tbm_templates`.

### Established Patterns
- 관리자 권한 가드: `UserSession.userRole != MANAGER → finish` (Phase 9 의 TbmDashboardActivity 패턴).
- Edge Function `tbm-start` 의 dedup 분기: 기존 `tbm_sessions UNIQUE (group_id, date) → 23505 → 409` 응답 패턴. Phase 12 에서 UNIQUE 제거 후 → `work_scope` 만 다르면 새 세션 OK 로 변경.
- Compose Canvas signature 의 JVM unit test 호환 — `canvasSize=0 OR isEmpty early-return` (Phase 9 의 Rule 3 deviation 그대로 유지).
- Pitfall 12 (Theme 래핑) — ComposeView 임베드 시 `Smart_Safety_ManagementTheme` 래핑 필수, Phase 9 grep evidence 동일 적용.

### Integration Points
- Phase 11 의 design token (corner radius·typography·spacing) — Phase 12 list view 와 OPS form 모두 적용
- Phase 13 의 일일점검 날짜 mismatch fix 와 무관 — TBM 의 일시/날짜 처리는 ISO_OFFSET_DATE_TIME 그대로 (Phase 9 의 ExpectedEndAtValidator)

</code_context>

<deferred>
## Deferred Ideas (v1.2+ 또는 별 milestone)

- **detector hint 동적 연계** (v1.2) — work_scope 별 conf threshold 조정 또는 알림 우선순위 boost. v1.1 에선 단순 기록만.
- **회의록 PDF/이미지 export** (v1.2) — KOSHA 양식 그대로 export 기능. v1.1 에선 앱 안 view 만.
- **외국인 작업자 통·번역** (v1.2) — 가이드 권장 사항, 6월 현장 조사 시 외국인 비율 확인 후 v1.2 또는 별 milestone.
- **가이드 12종 OPS 中 도금 외 11종 추가** (v1.2 또는 6월 현장 조사 결과 반영 — TBM-08 의 관리자 UI 로 추가 가능, 자체 schema 변경 없음).
- **work_scope ENUM 화** — 처음엔 free TEXT, 운영 후 패턴 정착 시 enum 으로 정제. 사용자 입력 패턴 분석 후 v1.2.
- **SLAM 행동요령 별도 학습 모듈** — 가이드의 "TBM 리더는 안전보건 전문교육" 권장 대응. v1.2 또는 별 phase.
- **회의록 백업·검색·통계** — N 세션 데이터 누적 후 분석. v1.2 또는 v2.0.

</deferred>

---

*Phase: 12-tbm-redesign-kosha-guide*
*Context gathered: 2026-05-23*
*Discussion: 10 decisions D-01 ~ D-09 (gray areas A·B·C·E 4개 모두 결정)*
*Next: `/gsd-plan-phase 12` — research + plan 작성*
