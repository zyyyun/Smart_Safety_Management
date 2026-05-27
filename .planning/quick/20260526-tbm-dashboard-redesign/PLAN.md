---
slug: tbm-dashboard-redesign
type: quick
created: 2026-05-26
status: APPROVED
mode: builder
generated_by: /office-hours
---

# Design — TBM 대시보드 minimal redesign

Branch: feature_rtps_test
Repo: zyyyun/Smart_Safety_Management

## Problem Statement

매니저 (testuser1) 가 TBM 대시보드 화면을 열 때:

1. 같은 그룹에 진행중 세션이 2개 (work_scope="Forklift bay A" ended + "Forklift bay B" active) 나란히 표시되는데, 두 카드가 동일 톤·계층이라 *그게 정상 동작인지 / 버그인지* 시각적 단서 0
2. 펼침·접힘 토글이 plain text `"v" / ">"` (TbmDashboardScreen.kt:159). Material Icons 미사용
3. 모든 세션 카드가 default expanded (line 137) → 한 화면에 정보 폭주
4. "Today sessions" / "active" / "ended" / "Expected end" / "Location" / "N participant(s)" 영어 잔존 (quick-02 한국어 작업 누락분)
5. TbmStartSection 폼이 대시보드 본문에 항상 펼쳐져 있음 → 매니저가 "오후 점검" 으로 들어와도 빈 시작 폼이 시야 위쪽 차지
6. 참여자 수 "3 participant(s)" 평문 → 시각화 (avatar grid / progress ring) 0

## What Makes This Cool

이 redesign 의 핵심은 **wow** 가 아니라 **clarity**. KOSHA 가이드 흡수 + 한국어 도메인 + 검단·포천 도금 현장 매니저의 *손가락이 가는 곳* 과 *눈이 가는 곳* 을 일치시킴. "한 번에 본다" 가 delight.

## Constraints

- 6월 검단·포천 설치 일정 안에 polish 가능해야 함
- Phase 12 의 17 schema (work_scope 3-튜플 UNIQUE) 와 Edge Function 7 case 모두 운영 적용 완료 — schema·API 변경 X
- 54/54 unit test 그대로 유지 (회귀 가드)
- 기존 데이터 flow (TbmRepository / decodeList / Realtime) 무변경
- v1.2 enhancement 분리 가능해야 함 (avatar grid · progress ring · countdown 은 후속)

## Premises

1. 매니저 첫 진입 빈도: **아침 > 점검 > 종료** (사용자 D1 답변)
2. 같은 그룹의 동시 다중 work_scope 세션은 **버그 아닌 spec** — 시각 단서 (active=강조색, ended=회색) 로 분리만 하면 됨
3. plain text 토글 `"v"/">"` 는 명백한 디자인 부채 — Material Icons 교체
4. 한국어 일관성: 영어 잔존은 quick-02 누락분, 본 작업으로 일괄 처리
5. 종료 세션은 default collapsed, 진행중만 default expanded (정보 폭주 차단)

## Approaches Considered

### Approach A: Minimal Pragmatic (CHOSEN)
1 파일 (TbmDashboardScreen.kt) 수정. S 규모 (human: 2-3시간 / CC: 30분).

### Approach B: Visual-rich Hero Cards
A + avatar grid + progress ring + countdown. M 규모 (human: 1일).

### Approach C: Multi-screen Split
TbmStartActivity 분리 + FAB. L 규모. D1 선택 ("아침 우선") 과 충돌이라 폐기.

## Recommended Approach

**Approach A — Minimal Pragmatic.** D1 의 "아침=시작 제일 위" 선택과 일관 + 6월 일정 안전 + commit 1개로 마무리 가능. B 의 visual delight 는 v1.2 enhancement 로 분리.

### 구체적 변경 사항

**대시보드 위계 (top→bottom):**

```
┌─────────────────────────────────────────┐
│ 📋 TBM 대시보드                          │ ← 헤더 (icon 추가)
├─────────────────────────────────────────┤
│ ┌───────────────────────────────────┐  │
│ │ ➕ TBM 세션 시작                   │  │ ← 시작 폼 (CardElevated, primary)
│ │   work_scope · OPS · 시간 · ...   │  │
│ │   [3개 세션 시작]                  │  │
│ └───────────────────────────────────┘  │
├─────────────────────────────────────────┤
│ 🟠 진행중 (1)                            │ ← 섹션 헤더 (icon + count)
│ ┌──────────────────────── 2dp border ─┐│
│ │ Forklift bay B · 지게차              ││ ← 진행중 카드 (orange left border)
│ │ ⏰ 예상 종료 17:46  👥 참여자 0명     ││
│ │ ▼ (KeyboardArrowDown — expanded)     ││
│ │   위험요인 / 대책 / 점검 / 참여자... ││
│ └──────────────────────────────────────┘│
├─────────────────────────────────────────┤
│ ✅ 종료 (1)                              │ ← 섹션 헤더 (icon + count)
│ ┌──────────────────────────────────────┐│
│ │ Forklift bay A · 지게차   🔘 종료     ││ ← 종료 카드 (회색 톤, collapsed)
│ │ ▶ (KeyboardArrowRight)               ││
│ └──────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

**변경 항목:**

1. **순서 reorder** (TbmDashboardScreen.kt:64-99 본문)
   - 헤더 → 시작 폼 → 진행중 섹션 → 종료 섹션
   - 진행중/종료 두 섹션 헤더 사이에 16dp Spacer + 섹션 타이틀 "🟠 진행중 (N)" · "✅ 종료 (N)"

2. **Material Icons 도입** (line 159 + 카드 본문)
   - `"v"` → `Icons.Default.KeyboardArrowDown`
   - `">"` → `Icons.Default.KeyboardArrowRight`
   - "Expected end:" → `Icons.Default.AccessTime` + 텍스트
   - "Location:" → `Icons.Default.Place` + 텍스트
   - "참여자 N명" → `Icons.Default.People` + N
   - "TBM 대시보드" 헤더 → `Icons.Default.Assignment`

3. **active/ended 톤 분리** (SessionDetailCard line 153-)
   - 진행중: `Card(border = BorderStroke(2.dp, Color(0xFFF59E0B)))` — Phase 12 의 InProgress orange 와 일치
   - 종료: `Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)))` + 본문 텍스트 `color = Color(0xFF6B7280)`
   - 진행중 default expanded = true (기존)
   - 종료 default expanded = false (NEW — `expanded = session.endedAt == null`)

4. **한국어 잔존 정리**
   - line 82: `"Today sessions ($sessionCount)"` → `"오늘 세션 ($sessionCount 개)"` (또는 섹션 분리 후 제거)
   - line 86: `"Loading groups..."` → `"그룹 불러오는 중..."`
   - line 112: `"#${group.groupId} (${group.inviteCode})"` → `"그룹 #${group.groupId} (${group.inviteCode})"`
   - line 163: `if (session.endedAt != null) "ended" else "active"` → `if (session.endedAt != null) "종료" else "진행중"`
   - line 165: `"${session.workType} / $status / ${participants.size} participant(s)"` → workType 한국어 변환 (`workTypeKorean()` 활용) + `"참여자 ${participants.size}명"`
   - line 174: `"Expected end: ${formatTimeShort(...)}"` → `"예상 종료 ${formatTimeShort(...)}"`
   - line 175: `"Location: $it"` → `"위치: $it"`

5. **섹션 분리** (GroupSessionListCard 내부 또는 새 컴포넌트)
   - sessions.filter { it.endedAt == null } → 진행중 섹션
   - sessions.filter { it.endedAt != null } → 종료 섹션
   - 각 섹션 헤더: "🟠 진행중 (N)" · "✅ 종료 (N)" (이모지 또는 Material Icon)

### Out of Scope (v1.2 이연)

- 참여자 avatar grid (이니셜 동그라미 N개 grid)
- Progress ring (참여 N/M 시각화)
- 미참여 인원 inline Chip list
- 예상 종료까지 남은 시간 countdown
- 종료 임박 (예: 종료 30분 전) 시각 강조

### 영향받는 파일

```
app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt
```

단일 파일. 다른 컴포넌트 (TbmDashboardCardComposable, TbmWorkerScreen, TbmStartSection) 영향 0.

## Open Questions

- 종료 카드의 키 해저드 ID / 환류 메모 입력은 종료 직후 (펼친 상태) 만 가능하게 할까, 아니면 후속에서도 collapsed → 펼침으로 수정 가능하게 할까? → **현재는 collapsed default 라서 사용자가 펼치면 수정 가능. 그대로 유지.**
- "🟠" / "✅" 이모지를 쓸지, Material Icons (Pending / CheckCircle) 를 쓸지? → **Material Icons 권장 (일관성). 이모지는 fallback.**

## Success Criteria

1. 진행중·종료 세션이 visual 톤으로 분리되어, 매니저가 1초 안에 어떤 게 active 인지 식별 가능
2. plain text `"v"/">"` 가 코드에서 0회 등장 (grep evidence)
3. 영어 문자열 "Today sessions / active / ended / Expected end / Location / participant(s)" 가 TbmDashboardScreen.kt 에서 0회 등장
4. `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
5. `./gradlew :app:testDebugUnitTest` 54/54 PASS 유지 (회귀 가드)
6. 매니저로 진입 → 진행중 1개 (orange border) + 종료 1개 (회색, collapsed) → 두 세션이 "정상 동작" 임이 시각으로 즉답

## Distribution Plan

별도 distribution 없음 — 같은 APK 의 일부. /gsd-quick 또는 직접 commit 후 `:app:assembleDebug` → 단말 설치.

## Next Steps

- [ ] TbmDashboardScreen.kt 5개 변경 항목 적용 (위계 reorder, Material Icons, 톤 분리, 한국어 정리, 섹션 분리)
- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest` 통과
- [ ] commit message: `feat(quick-03): TBM 대시보드 minimal redesign — 위계·아이콘·톤 분리·한국어`
- [ ] STATE.md "Quick Tasks Completed" #03 등록
- [ ] 단말에 설치 후 매니저 진입 → 진행중·종료 시각 분리 확인

## What I noticed about how you think

- 사용자가 "구려" 라고 표현한 frustration 은 **두 가지 다른 문제** 가 한 번에 보이는 데서 옴 — "왜 2개야?" (정보 부족) + "디자인이 구려" (UX). 두 가지를 한 단어로 묶었지만, 실제로는 *separable issues*. 묶을수록 해결이 더디는 패턴.
- D1 답변에서 "아침 = 세션 시작이 제일 위" 를 골랐는데, 이건 직관 기반 빠른 선택. PoC 1주차 사용 로그가 이 가설을 검증·반박할 데이터를 줄 것. 검단·포천 설치 후 첫 1주 동안 매니저의 화면 진입 시점 로깅하면 D1 재검토 가능.
- 모든 화면이 default expanded 인 코드 패턴은 흔히 "정보 다 보여주고 사용자가 알아서 접게" 라는 무책임 위계의 산물. 사용자는 default 를 신뢰 — default 가 곧 추천. 종료된 것을 default 펼침으로 두는 건 매니저에게 "이 종료 세션도 중요해" 라고 잘못 신호를 보냄.
