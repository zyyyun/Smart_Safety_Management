# Smart Safety Management — Milestone v1.1 Requirements

> **Milestone**: v1.1 앱 전체 완성도
> **Goal**: 검단·포천 6월 설치 전, 앱이 사용자에게 "완성된 제품" 으로 보이도록 UX 일관성·신뢰성·TBM 정합성 끌어올림.
> **Total**: 4 카테고리 / 13 요구사항
> **Sources**: 2026-05-22 ~ 2026-05-23 `/office-hours` brainstorm (7 backlog items), KOSHA `230209 작업 전 안전점검회의 가이드`
> **Predecessor**: v1.0 5월 PPT 데모 SHIPPED 2026-05-22 (REQUIREMENTS.v1.0.md / ROADMAP.v1.0.md / MILESTONES.md 참조)

---

## v1.1 Requirements

### 1. UX 일관성 (UX)

- [ ] **UX-01**: 입구 흐름 (Splash → SignUp 1·2·3·4 → LogIn → Home 첫 진입) 의 키보드 표시·error 문구·시각 일관성·로딩 인터랙션 단일 규약 적용 (Phase 11)
- [ ] **UX-02**: Home 화면 카드 4종 (프로필바, 워치·카메라 미니카드, 일일점검 카드, 알림 카드, TBM 카드) 의 시각 언어 통일 — 아키텍처·레이아웃·상태·아이콘 일관 (Phase 11)
- [ ] **UX-03**: Setting* Activity 시리즈 (인원·기기·CCTV·현장·초대 관리) 패턴 정립 — 헤더·여백·버튼 위치·뒤로가기·진입/이탈 일관 (Phase 11)

### 2. TBM 재설계 (TBM, Phase 9 결과물 위 작업)

- [ ] **TBM-04**: `tbm_sessions` 의 `UNIQUE (group_id, date)` 제거 + `work_scope` 컬럼 추가 + Edge Function `tbm-start` 의 dedup 분기 변경 — 작업·공정별 다중 세션 가능 (Phase 12)
- [ ] **TBM-05**: KOSHA 회의록 양식 핵심 필드 schema mapping — 잠재위험요인 list / 중점위험 1개 / 대책 (제거→대체→통제) / 작업 후 종료 미팅 / 환류 조치 (Phase 12)
- [ ] **TBM-06**: Android UI 재구성 — 세션 1개 카드 → 오늘의 N 세션 list view + 공정명 입력 + 위험요인 입력 UI + SLAM (Stop·Look·Assess·Manage) 행동요령 안내 (Phase 12)
- [ ] **TBM-07**: 도금/금속가공 도메인 OPS seed — 지게차 (가이드 #1 기반) + 화학물질 (산세·도금액) + 고온·열처리 — `tbm_templates` 신규 row + 비전 5종 detector 자동 연계 hint (Phase 12)
- [ ] **TBM-08**: 관리자 OPS 카탈로그 관리 UI — `tbm_templates` 의 활성/비활성 토글 + 신규 OPS 추가 (6월 현장 조사 후 추가 대비) (Phase 12)

### 3. 데이터 신뢰성 + 정보구조 (DATA / INFO)

- [ ] **DATA-04**: 일일 안전점검 등록 날짜 mismatch fix — `DailyDetailActivity` 의 selectedDate state 가 server-side default (now()) 대신 사용자 선택 날짜 그대로 INSERT (Phase 13)
- [ ] **INFO-01**: 실시간 카메라 화면 전경/현장 분리 → 단일 화면 통합 송출 (Phase 13)

### 4. 6월 설치 사전 UAT (UAT)

- [ ] **UAT-01**: Phase 11·12·13 변경분 + v1.0 의 deferred 항목 (Phase 4·04 / 7·04 / 9·04) 종합 회귀 — ai_agent · j2208a · Android unit test 전체 PASS (Phase 14)
- [ ] **UAT-02**: 검단·포천 현장 환경 사전 점검표 — 네트워크 (WiFi / 셀룰러 / Drift X3 도달성) · 기기 (Android 폰 spec · J2208A 워치 · Drift X3 카메라) · 계정 (관리자/작업자 시드) (Phase 14)
- [ ] **UAT-03**: 사용자 시나리오 walkthrough — manager / worker / general_manager 3 역할 1일 사이클 캡처 (Phase 14)

---

## Out of Scope (v1.1 명시 제외)

- **v1.0 의 deferred phases** (Phase 4·04 24h 워치 실측 / Phase 5 평가 지표 / Phase 6 PPT 자료 / Phase 7·04 시연 / Phase 9·04 1일 사이클 시연) → v1.2 또는 별 milestone. UAT-01 의 회귀 대상에는 포함되나 신규 작업 X.
- **가이드 12종 OPS 中 도금/금속가공 비도메인** (크레인·컨베이어·후크·샤클·혼합기·굴착기·사출성형기·분쇄기·이동식 사다리·산업용 로봇·화물운반트럭·지붕대들보) → 6월 현장 조사 후 OPS 카탈로그 추가 결정 (TBM-08 의 관리자 UI 로 추가 가능)
- **YOLO26 전면 마이그레이션** (FIRE-ADV BACKLOG, 5 sub-task) → v2.0
- **워치 트랙 Phase 2** (J2208A 다중 작업자 사무실 3~5명) → v1.2
- **외국인 작업자 통·번역** (KOSHA 가이드 권장) → 현장 조사 시 외국인 비율 확인 후 v1.2 또는 별 milestone

---

## Traceability (Phase mapping)

| REQ ID | Description | Phase |
|--------|-------------|-------|
| UX-01 | 입구 흐름 일관성 | 11 |
| UX-02 | Home 카드 4종 시각 통일 | 11 |
| UX-03 | Setting* 패턴 정립 | 11 |
| TBM-04 | UNIQUE 제거 + work_scope 추가 | 12 |
| TBM-05 | 회의록 양식 schema mapping | 12 |
| TBM-06 | 세션 list UI + SLAM | 12 |
| TBM-07 | 도금 도메인 OPS seed | 12 |
| TBM-08 | OPS 관리자 UI | 12 |
| DATA-04 | 일일점검 날짜 mismatch | 13 |
| INFO-01 | 실시간 카메라 통합 | 13 |
| UAT-01 | 회귀 검증 | 14 |
| UAT-02 | 현장 환경 사전 점검표 | 14 |
| UAT-03 | 3 역할 walkthrough | 14 |

**Coverage**: 13/13 REQ 모두 phase mapping ✓

---

## v1.0 Historical Reference

v1.0 의 28 requirements 와 phase 매핑은 `REQUIREMENTS.v1.0.md` 참조. v1.1 의 UAT-01 은 v1.0 의 누적 코드 + Phase 11·12·13 의 변경분 합산을 회귀 대상으로 한다.
