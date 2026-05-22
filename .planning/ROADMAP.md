---
milestone: v1.1
name: "앱 전체 완성도"
status: planning
phases_total: 4
phases_start: 11
phases_end: 14
requirements_total: 13
created: 2026-05-23
predecessor: v1.0 (SHIPPED 2026-05-22, ROADMAP.v1.0.md)
---

# Smart Safety Management — Roadmap (v1.1 앱 전체 완성도)

> **Goal**: 검단·포천 6월 설치 전, 앱이 사용자에게 "완성된 제품" 으로 보이도록
> UX 일관성·신뢰성·TBM 정합성 끌어올림.
>
> **Sources**: 2026-05-22 ~ 2026-05-23 `/office-hours` brainstorm 7 backlog items
> (입구 흐름·Home·Setting·실시간 카메라·일일점검 날짜·TBM 재설계·OPS 관리)
> + KOSHA `230209 작업 전 안전점검회의 가이드` (TBM 단계별 활동·회의록 양식·12종 OPS 中
> 도메인 정합 1종).
>
> **Critical path**: ≈ 2~3주 (6월 검단·포천 설치 이전 마감)
>
> **Numbering**: v1.0 이 Phase 10 에서 종결되어 v1.1 은 Phase 11~14.

---

## Phases

- [ ] **Phase 11: 일관 시각 언어 정립** — 입구·Home·Setting* 의 UI 패턴 통일
- [ ] **Phase 12: TBM 재설계 (KOSHA 가이드 흡수)** — 작업·공정별 다중 세션 + 회의록 양식 매핑 + 도메인 OPS + 관리자 UI
- [ ] **Phase 13: 데이터 신뢰성 + 정보구조 정리** — 일일점검 날짜 mismatch + 실시간 카메라 통합
- [ ] **Phase 14: 6월 설치 사전 UAT** — 변경분 회귀 + 현장 환경 사전 점검 + 3 역할 walkthrough

## Phase Summary

| #  | Phase                          | REQs | Criteria | Depends on | Est. duration |
|----|--------------------------------|------|----------|------------|---------------|
| 11 | 일관 시각 언어 정립            | 3    | 4        | —          | 1주          |
| 12 | TBM 재설계                     | 5    | 5        | —          | 1~1.5주      |
| 13 | 데이터 신뢰성 + 정보구조       | 2    | 3        | —          | 2~3일        |
| 14 | 6월 설치 사전 UAT             | 3    | 4        | 11·12·13   | 3~5일        |

**Dependency graph**: 11 ∥ 12 ∥ 13 (모두 병렬 가능) → 14 (회귀 검증)

**Total**: 13 requirements, 16 success criteria, ≈ 2.5~3주 critical path

---

## Phase Details

### Phase 11: 일관 시각 언어 정립
**Goal**: 앱 전체에 흩어진 UI 패턴 (입구 흐름·Home 카드 4종·Setting* 시리즈) 의 시각 언어를 단일 규약으로 정립하여 사용자가 "완성된 제품" 으로 인식하도록 한다.
**Depends on**: 없음 (병렬 진입 가능)
**Requirements**: UX-01, UX-02, UX-03
**Success Criteria** (what must be TRUE):
  1. 입구 흐름 (Splash → SignUp 1·2·3·4 → LogIn → Home 첫 진입) 의 키보드 표시·error 문구·시각 일관성·로딩 인터랙션이 단일 규약 (예: 공통 ViewModel + 공통 ErrorBanner Composable + 동일 typography token) 으로 적용됨 (UX-01).
  2. Home 화면 카드 4종 (프로필바, 워치·카메라 미니카드, 일일점검 카드, 알림 카드, TBM 카드) 의 시각 언어 통일 — corner radius·elevation·padding·아이콘 위치·상태 표시가 동일 token 으로 통제됨 (UX-02).
  3. Setting* Activity 시리즈가 헤더·여백·버튼 위치·뒤로가기 일관 패턴 (예: 공통 SettingScaffold) 으로 정립됨 (UX-03).
  4. 회귀: 기존 `detection_events` · `watch_alerts` · `tbm_sessions` 적재 동작 0 변경 (DB 검증 + ai_agent 31/31 + j2208a 43/43 + Android unit test PASS).
**Plans**: TBD (`/gsd-discuss-phase 11` 후 결정)

### Phase 12: TBM 재설계 (KOSHA 가이드 흡수)
**Goal**: KOSHA `230209 작업 전 안전점검회의 가이드` 의 작업·공정별 다중 세션 + 회의록 양식 핵심 필드를 흡수하고, 도금/금속가공 도메인 OPS 를 시드하여, 검단·포천 현장 관리자/작업자가 실제 사용 가능한 TBM 운영 시스템을 갖춘다.
**Depends on**: 없음 (병렬 진입 가능, Phase 9 결과물 위 작업)
**Requirements**: TBM-04, TBM-05, TBM-06, TBM-07, TBM-08
**Success Criteria** (what must be TRUE):
  1. `tbm_sessions` 의 `UNIQUE (group_id, date)` 제거 + `work_scope` 컬럼 추가 + Edge Function `tbm-start` 의 dedup 분기 변경으로 같은 그룹이 하루에 N 개 세션 (예: 오전 산세 / 오후 도금조 / 야간 검사) 생성 가능 (TBM-04).
  2. 회의록 양식의 7개 핵심 필드 (잠재위험요인 list / 중점위험 1개 / 대책 / 환류 조치 등) 가 schema + UI 양측에 모두 매핑되어 회의록 PDF 출력 또는 export 시 가이드 양식과 1:1 대응 가능 (TBM-05).
  3. Android UI 가 "오늘의 N 세션 list view + 공정명 입력 + 위험요인 입력 + SLAM 행동요령 안내" 의 새 구조로 동작하며, 이전 단일 카드 UI 는 제거 또는 N 세션 list 의 default empty state 로 흡수 (TBM-06).
  4. `tbm_templates` 에 도금/금속가공 도메인 OPS 최소 3종 (지게차·화학물질·고온) seed + 비전 5종 detector (`fire`·`helmet`·`forklift`·`person`·`fall`) 자동 연계 hint 가 OPS row 의 metadata 에 표시됨 (TBM-07).
  5. 관리자가 OPS 카탈로그 활성/비활성 토글 + 신규 OPS 추가 (6월 현장 조사 후 추가 대비) 가 관리자 전용 UI 에서 가능, RLS 정책 분리 검증 (TBM-08).
**Plans**: TBD (`/gsd-discuss-phase 12` 후 결정)

### Phase 13: 데이터 신뢰성 + 정보구조 정리
**Goal**: 사용자가 입력한 데이터가 "선택한 그대로 저장" 되고, 중복된 정보구조 (실시간 카메라 전경/현장 분리) 가 단일 화면으로 정리되어 신뢰성과 가독성을 확보한다.
**Depends on**: 없음 (병렬 진입 가능)
**Requirements**: DATA-04, INFO-01
**Success Criteria** (what must be TRUE):
  1. 일일 안전점검 등록 화면에서 사용자가 다른 날짜 (예: 어제 누락분 보충) 를 선택하면 INSERT row 의 날짜도 그 날짜 (UI 선택 == DB row 날짜) — server-side default (now()) override 차단 (DATA-04).
  2. 실시간 카메라 메뉴가 전경/현장 분리 없이 단일 화면 으로 송출 — 이전 두 화면의 진입점 1개로 합치고 라우팅 정리 (INFO-01).
  3. 회귀: `detection_events.capture_id` ↔ `camera_captures` 매핑 0 변경, `daily_safety_check` 의 기존 row 영향 없음, RealTimeActivity 가 사용하던 두 camera_id (전경·현장) 가 사용자 view 에서 단일 화면으로 통합되어도 백엔드 capture 흐름은 유지.
**Plans**: TBD (`/gsd-discuss-phase 13` 후 결정)

### Phase 14: 6월 설치 사전 UAT
**Goal**: Phase 11·12·13 변경분 + v1.0 의 deferred 항목들을 종합 회귀하고, 검단·포천 현장 환경 사전 점검 + 사용자 3 역할 1일 사이클 walkthrough 를 통해 설치 직전 마지막 신뢰성 확보.
**Depends on**: Phase 11, 12, 13
**Requirements**: UAT-01, UAT-02, UAT-03
**Success Criteria** (what must be TRUE):
  1. Phase 11·12·13 변경분 + v1.0 의 deferred 항목 (Phase 4·04 / 7·04 / 9·04) 종합 회귀 PASS — ai_agent · j2208a · Android unit test 전체 GREEN + Phase 8 RTSP-02 baseline (person conf 0.92 / latency 3.16s) 유지 (UAT-01).
  2. 검단·포천 현장 환경 사전 점검표 작성 + 모든 항목 확인 — 네트워크 (WiFi SSID / 셀룰러 신호 / Drift X3 RTSP 도달성) + 기기 (Android 폰 spec / J2208A 워치 / Drift X3 카메라 펌웨어) + 계정 (manager + worker 시드 / 그룹 매핑) (UAT-02).
  3. 사용자 3 역할 (manager / worker / general_manager) 의 1일 사이클 walkthrough 캡처 — 가입 → 로그인 → 일일점검 → TBM 세션 N개 → 위험 감지 → 알림 → 조치 → 이력 — 영상 또는 스크린샷 시퀀스 (UAT-03).
  4. 모든 SC 충족 후 v1.1 milestone SHIPPED 선언 가능 (MILESTONES.md 의 v1.1 Shipped 엔트리 작성 + 6월 설치 일정 확정).
**Plans**: TBD (`/gsd-discuss-phase 14` 후 결정)

---

## Coverage Validation

**Total v1.1 requirements**: 13
**Mapped**: 13/13 ✓

**REQ → Phase**:
- Phase 11: UX-01, UX-02, UX-03 (3)
- Phase 12: TBM-04, TBM-05, TBM-06, TBM-07, TBM-08 (5)
- Phase 13: DATA-04, INFO-01 (2)
- Phase 14: UAT-01, UAT-02, UAT-03 (3)

**Phase → REQ 일관성**: 모든 phase 의 Success Criteria 가 해당 REQ 와 1:1 또는 1:N 매핑됨. UAT phase 는 회귀 검증 phase 라 별도 신규 REQ 없이 다른 phase 의 결과물을 검증.

---

## v1.0 Historical Reference

v1.0 의 10 phases 와 phase-별 traceability 는 `ROADMAP.v1.0.md` 참조. v1.1 의 Phase 14 (UAT) 는 v1.0 의 누적 코드 + Phase 11·12·13 변경분 합산을 회귀 대상으로 한다.
