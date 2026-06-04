---
phase: 11-ui-consistency
type: discussion-log
created: 2026-05-27
---

# Phase 11 — Discussion Log

## Decisions

### D1. Ambition level

**Question:** Phase 11 어느 정도까지 작업할까요?

**Options:**
- A) 가볍게 — 화면별 복붙 (1-2일)
- B) 공통 컴포넌트 추출 (5-7일, 추천)
- C) 전면 재설계 (2주+, 6월 일정 위험)

**User chose:** B

**Rationale:** quick #03 (commit e557fb4) 의 TbmDashboardScreen 에 이미 SsmColors / Material Icons / SectionHeader / StateCard 후보 패턴이 inline 정의되어 있어 추출 비용 낮음. 6월 검단·포천 설치 일정 안전 + 추후 Phase 13·14·15 / v1.2 이원 재사용 가능. C 는 일정 risk.

---

### D2. Plan 구조

**Question:** Plan 분리 구조는 어떻게?

**Options:**
- A) 4 plan: token + 3 영역 (입구/Home/Setting*)
- B) 2 plan: 공통 + 일괄 적용
- C) 3 plan: token + 우선순위 분할

**User chose:** B

**Rationale:** 공통 컴포넌트 추출 (Plan 11-01) 과 일괄 적용 (Plan 11-02) 이 자연스러운 dependency 흐름. Plan 11-02 안에서 3 sub-task (Home / 입구 / Setting*) 로 자체 분할 가능. 4-plan 은 plan-checker 부담 ↑.

---

### D3. 우선 영역

**Question:** UX-01/02/03 중 가장 먼저 적용할 우선 영역은?

**Options:**
- A) Setting* 16 Activity
- B) 입구 흐름 (Splash + SignUp + LogIn)
- C) Home 카드 5종

**User chose:** C

**Rationale:** Home 화면이 메인. 사용자 (manager / worker) 가 가장 자주 보는 곳. quick #03 의 TBM 카드와 같은 톤으로 watch / camera / daily / alert / profile 카드 정리하면 즉각적 일관성 효과 가장 큼. Plan 11-02 의 Task 1.

---

### D4. Setting* 의 XML 기반 화면 처리

**Question:** Setting* 중 XML 기반 화면 처리 방식은?

**Options:**
- A) XML 유지 + 헤더만 공통화 (추천)
- B) Compose 마이그레이션
- C) Hybrid — 한두개만 Compose

**User chose:** A

**Rationale:** 16 Setting* 중 일부는 Compose, 일부는 View XML 혼재. Compose 마이그 (B) 는 시간 + 회귀 risk 큼. A 는 가장 안전 + 6월 일정 안전. XML 화면은 그대로 두고 `<include layout="@layout/common_toolbar"/>` 패턴으로 헤더만 통일.

---

## Skipped (Carry-forward — 이미 다른 phase / quick task 에서 결정)

| 항목 | 출처 | 적용 |
|---|---|---|
| 한국어 일관성 | quick #02 (477ac38) | 신규 컴포넌트 default 한국어 |
| Material Icons 패턴 | quick #03 (e557fb4) | Tokens.kt typealias |
| 카드 톤 분리 | quick #03 | StateCard sealed class |
| Section header 패턴 | quick #03 | SectionHeader Composable 그대로 추출 |
| Single-group 가정 | quick #04 (43ec14e) | Group 선택 UI 0건 |
| Compose `return@<Lambda>` 금지 | b2d8745 | code review checklist |
| Korean repo path workaround | Phase 7/9/12 | layout.buildDirectory.set("D:/ssm-app-build") |
| Debug build dev account picker | 80983df | SplashActivity 그대로 |

---

## Open Questions (Plan 단계 결정 예정)

- OQ-1: 입구 흐름 의 ErrorBanner / 키보드 처리 패턴 — SignUp1Activity 코드 분석 후 결정
- OQ-2: Home 카드 5종 의 카드 본문 layout — Plan 11-02 Task 1 작성 시 inventory
- OQ-3: typography token 추출 여부 — Plan 11-01 에서 grep frequency 측정
- OQ-4: 검증 전략 — build + preview screenshot + 단말 사용자 검증
