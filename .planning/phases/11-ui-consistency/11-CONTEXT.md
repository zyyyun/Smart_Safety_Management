---
phase: 11-ui-consistency
milestone: v1.1
title: "일관 시각 언어 정립"
requirements: [UX-01, UX-02, UX-03]
generated_by: /gsd-discuss-phase
created: 2026-05-27
status: discussed
---

# Phase 11 — 일관 시각 언어 정립 CONTEXT

## Phase Goal

앱 전체에 흩어진 UI 패턴 (입구 흐름 · Home 카드 4종 · Setting* 시리즈) 의 시각 언어를 **단일 규약** 으로 정립하여 사용자가 "완성된 제품" 으로 인식하도록 한다.

Quick #02 (한국어) + Quick #03 (TBM 대시보드 redesign) + Quick #04 (single-group) 에서 검증된 패턴을 **공통 컴포넌트** 로 추출 후 28+ 화면에 일괄 적용.

## Success Criteria (ROADMAP 기준)

1. **입구 흐름** (Splash → SignUp 1·2·3·4 → LogIn → Home 첫 진입) 의 키보드 표시·error 문구·시각 일관성·로딩 인터랙션이 단일 규약 (공통 ViewModel + 공통 ErrorBanner Composable + 동일 typography token) 으로 적용됨 (UX-01)
2. **Home 화면 카드 5종** (프로필바, 워치·카메라 미니카드, 일일점검 카드, 알림 카드, TBM 카드) 의 시각 언어 통일 — corner radius·elevation·padding·아이콘 위치·상태 표시가 동일 token 으로 통제됨 (UX-02)
3. **Setting\* Activity 시리즈** (16 Activity) 가 헤더·여백·버튼 위치·뒤로가기 일관 패턴 (공통 SettingScaffold) 으로 정립됨 (UX-03)
4. **회귀**: 기존 `detection_events` · `watch_alerts` · `tbm_sessions` 적재 동작 0 변경 (DB 검증 + ai_agent 31/31 + j2208a 43/43 + Android unit test PASS)

## Decisions (사용자 답변 잠금)

### D1. Ambition Level → **B) 공통 컴포넌트 추출**

Polish-only (A) 는 즉시 끝나지만 코드 중복 + 추후 변경 시 16+ 화면 일일이 수정.
재설계 (C) 는 6월 검단·포천 설치 일정과 충돌.

**B 선택 이유**: quick #03 (TbmDashboardScreen) 에 이미 7 Material Icons + Card border helper + COLOR_ACTIVE_ORANGE/COLOR_ENDED_BG/COLOR_TEXT_MUTED 등 token 후보가 **inline 정의** 되어 있음. 추출 비용 낮음. 추출 후 추후 Phase 13/14/15 또는 v1.2 이원 시 재사용.

### D2. Plan 구조 → **B) 2 plan: 공통 + 일괄 적용**

- **Plan 11-01**: 공통 컴포넌트 추출 (Tokens + ScreenScaffold + StateCard + SectionHeader). TBM 의 기존 inline 사용처를 새 공통 컴포넌트 호출로 refactor (동작 변경 0, 회귀 가드).
- **Plan 11-02**: 28 화면 일괄 적용. 3 sub-task 로 분리:
  - Task 1: **Home 카드 5종** (우선 — D3)
  - Task 2: 입구 흐름 7 Activity
  - Task 3: Setting* 16 Activity (XML 화면은 헤더만 통일, D4)

### D3. 우선 영역 → **C) Home 카드 5종**

- **이유**: 메인 화면. 사용자가 가장 자주 보는 곳. quick #03 의 TBM 카드와 같은 톤으로 watch / camera / daily / alert / profile 카드 정리하면 즉각적 일관성 효과 가장 큼.
- 입구 흐름 (B) 는 첫 인상 결정이지만 1-2회만 사용. Setting* (A) 는 수가 많지만 종속적 화면.

### D4. Setting* 의 XML 기반 화면 → **A) XML 유지 + 헤더만 공통화**

- **16 Setting\* 중 일부는 Compose, 일부는 View XML + Activity** 혼재. Compose 마이그레이션 (B) 은 시간 + 회귀 risk 큼.
- **방식**: XML 화면은 그대로. 공통 Toolbar / 헤더 패턴만 `style` 또는 `<include layout="@layout/common_toolbar"/>` 로 통일. Compose 화면은 새 `SettingScaffold` Composable 사용.
- 가장 안전 + 6월 일정 안전.

## Carry-forward (이미 결정된 항목, 재논의 X)

| 항목 | 출처 | 적용 방법 |
|---|---|---|
| 한국어 일관성 (UI 본문 + FCM body) | quick #02 (commit 477ac38) | 신규 컴포넌트 한국어 default, 영어 잔존 grep 검증 |
| Material Icons 7종 사용 패턴 | quick #03 (commit e557fb4) | KeyboardArrowDown/Right, AccessTime, Assignment, CheckCircle, People, Place, Schedule, Info — 공통 Tokens.kt 에 typealias |
| 카드 톤 분리 (active orange / ended gray / state badge) | quick #03 (commit e557fb4) | `StateCard(state: CardState)` Composable 로 추출 — CardState = Active / Ended / InProgress / Error / Info |
| 정보 위계 (Section Header + count badge + default expanded 분기) | quick #03 (commit e557fb4) | `SectionHeader(icon, label, count?)` Composable |
| Single-group 가정 (UI 본문) | quick #04 (commit 43ec14e) | Group 선택 UI 0건 — Repository multi 메서드는 future hook |
| Compose `return@<Lambda>` 금지 (lesson) | b2d8745 debug session | 모든 신규 Composable 에서 if/else 양분 패턴 강제. Code review 시 grep 검증. |
| Default expanded 분기 (active=true, ended=false) | quick #03 | StateCard 의 expanded default 가 state 에 따라 자동 결정 |
| Korean repo path workaround | Phase 7 03 / Phase 9 / 12 | `layout.buildDirectory.set("D:/ssm-app-build")` 그대로 |
| Debug build 한정 dev account picker | commit 80983df | 변경 0 — SplashActivity 진입점 그대로 |

## 공통 컴포넌트 추출 대상 (Plan 11-01 의 input)

### `ui/Tokens.kt` (object)

```kotlin
object SsmColors {
    val ActiveOrange = Color(0xFFF59E0B)   // 진행중 강조
    val EndedBg = Color(0xFFF3F4F6)         // 종료/비활성 배경
    val TextMuted = Color(0xFF6B7280)
    val TextInfo = Color(0xFF2563EB)
    val TextDanger = Color(0xFFEF4444)
    val SuccessGreen = Color(0xFF22C55E)    // checkin / completed
}

object SsmSpacing {
    val cardPadding = 12.dp
    val sectionSpacing = 16.dp
    val rowSpacing = 8.dp
}

object SsmTypography {
    // 기존 typography token 보존 — 본 phase 에서 신규 typography overhaul 안 함
}
```

### `ui/components/ScreenScaffold.kt`

```kotlin
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
)
```

- 헤더 (좌측 뒤로가기 아이콘 + title + 우측 actions slot)
- 본문 Column with verticalScroll
- Compose 화면 진입점 통일

### `ui/components/StateCard.kt`

```kotlin
sealed class CardState {
    object Active : CardState()
    object Ended : CardState()
    object InProgress : CardState()
    object Success : CardState()
    object Error : CardState()
    object Neutral : CardState()
}

@Composable
fun StateCard(
    state: CardState,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

- state 에 따라 border / background / text color 자동 결정
- Active → 2dp orange border
- Ended → gray background + muted text
- 등

### `ui/components/SectionHeader.kt`

```kotlin
@Composable
fun SectionHeader(
    icon: ImageVector,
    label: String,
    count: Int? = null,
    iconTint: Color = SsmColors.TextMuted,
)
```

- quick #03 의 SectionHeader 그대로 추출 + count parameter 추가 (예: "진행중 (1개)")

### Toolbar / 헤더 공통 XML (UX-03 의 XML 화면용)

```xml
<!-- res/layout/common_toolbar.xml -->
<androidx.appcompat.widget.Toolbar
    android:id="@+id/toolbar"
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    app:navigationIcon="@drawable/ic_arrow_back"
    style="@style/Widget.MaterialComponents.Toolbar.Surface" />
```

- 모든 Setting* XML Activity 에 `<include layout="@layout/common_toolbar"/>` 추가
- Activity 의 `setSupportActionBar(findViewById(R.id.toolbar))` 패턴 통일

## Open Questions (Plan 11-01 / 11-02 작성 시 결정)

### OQ-1: 입구 흐름의 ErrorBanner / 키보드 처리 패턴

- SignUp 1-4 각 단계에서 검증 실패 시 inline error 표시 패턴 (Snackbar vs OutlinedTextField error vs ErrorBanner)
- 키보드 표시 timing (focus 시 자동 vs 사용자 tap)
- **결정 방식**: 현재 SignUp1Activity 코드 본문 분석 후 가장 일관된 패턴 채택

### OQ-2: Home 카드 5종의 카드 본문 layout

- 프로필바 / 워치·카메라 미니카드 / 일일점검 카드 / 알림 카드 / TBM 카드 각각의 본문 layout 이 매우 다름
- StateCard 의 content slot 으로 그대로 흡수 가능 vs 별도 sub-template
- **결정 방식**: Plan 11-02 Task 1 작성 시 각 카드 본문 inventory + decide

### OQ-3: typography token 추출 여부

- 본 phase 의 ambition 은 "공통 컴포넌트" 까지. typography overhaul 은 out of scope.
- 단, `Text(fontSize = X.sp, fontWeight = ...)` 가 화면별로 산재. 최소한의 typography token (TitleLarge / BodyMedium / Caption) 만 추출할지?
- **결정 방식**: Plan 11-01 에서 grep 으로 typography 빈도 측정 후 frequency-based 추출

### OQ-4: 검증 전략

- Plan 11-01 의 회귀 가드: TBM 의 기존 동작 0 변경 (사용자 단말 검증 + ai_agent 31/31 + j2208a 43/43 + Android unit test PASS)
- Plan 11-02 의 검증: Compose preview screenshot diff vs 단말 진입 사용자 검증
- **결정 방식**: build + preview screenshot 으로 1차, 단말 검증으로 최종

## Threats (사전 식별, Plan 단계 mitigation)

| Threat ID | 항목 | mitigation 방향 |
|---|---|---|
| T-11-01 | 공통 컴포넌트 추출 시 TBM 기존 동작 회귀 | Plan 11-01 의 TBM refactor 부분이 zero-behavior-change 검증 (Android unit test 54/54 + 단말 매니저 진입 동일 UI) |
| T-11-02 | XML Setting* 의 Toolbar include 가 기존 menu/back 동작 깨뜨림 | 각 Activity 의 onBackPressed / setSupportActionBar override 보존, Plan 11-02 Task 3 에 사전 검증 |
| T-11-03 | Compose 의 `return@<Lambda>` 패턴 재발 (b2d8745 lesson) | code review checklist + grep `return@.*Section\|return@Card\|return@Box` 자동 검증 |
| T-11-04 | quick #02 한국어 작업 누락분 재발 | 본 phase 의 모든 신규 컴포넌트 default 한국어 + 영어 잔존 grep 0건 검증 |
| T-11-05 | 28 화면 일괄 적용 중 한두 화면 빠뜨림 | Plan 11-02 의 each Task 마다 grep 으로 잔존 inline 패턴 (`Color(0xFF`, `Icons.Default.\b`, 등) 0건 검증 |
| T-11-06 | 6월 검단·포천 설치 일정 안 polish 완료 불가 | Plan 11-02 의 3 sub-task 가 우선순위 순 (Home → 입구 → Setting*). 시간 부족 시 Task 3 (Setting*) 만 다음 milestone 이연 |

## Next Steps

1. **`/gsd-plan-phase 11`** — Plan 11-01 + Plan 11-02 작성. Plan 11-01 의 input = 본 CONTEXT 의 "공통 컴포넌트 추출 대상" 섹션 + quick #03 의 TbmDashboardScreen.kt 본문 reference. Plan 11-02 의 input = D3 우선순위 (Home 카드 우선) + D4 (XML 화면 헤더만).
2. Plan 단계에서 Open Questions (OQ-1·2·3·4) 해결.
3. 이후 `/gsd-execute-phase 11` — Wave 1 (Plan 11-01 공통 컴포넌트, autonomous) → Wave 2 (Plan 11-02 일괄 적용, autonomous chain 가능).

## 관련 참고

- quick #02: `.planning/quick/20260526-tbm-ui-korean/`
- quick #03: `.planning/quick/20260526-tbm-dashboard-redesign/`
- quick #04: `.planning/quick/20260527-tbm-single-group/`
- debug session (Compose lesson): `.planning/debug/resolved/tbm-dashboard-crash.md`
- Phase 12 CONTEXT (인접 phase): `.planning/phases/12-tbm-redesign-kosha-guide/12-CONTEXT.md`
- TbmDashboardScreen.kt (reference): `app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt`
