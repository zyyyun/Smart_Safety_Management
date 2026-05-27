---
slug: tbm-dashboard-redesign
type: quick
created: 2026-05-26
completed: 2026-05-26
status: complete
generated_by: /office-hours → /gsd-quick
---

# Quick Task Summary — TBM 대시보드 minimal redesign

## 적용 범위

PLAN.md 의 5 개 변경 항목을 단일 파일 `app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt` 에 일괄 적용. Approach A (Minimal Pragmatic).

## 적용된 변경

### 1. 위계 reorder

- 헤더 ("TBM 대시보드" + Assignment icon)
- → 1단: TbmStartSection (시작 폼, D1 의 "아침 우선" 반영)
- → HorizontalDivider
- → 2단: 그룹별 GroupSessionsSection
  - 진행중 섹션 (Schedule icon, orange)
  - 종료 섹션 (CheckCircle icon, 회색)

### 2. Material Icons 도입

`androidx.compose.material.icons.filled.*` 7 icons:
- `Assignment` — 헤더
- `Schedule` — 진행중 섹션 라벨 + orange tint
- `CheckCircle` — 종료 섹션 라벨 + 회색 tint
- `KeyboardArrowDown` / `KeyboardArrowRight` — 카드 펼침/접힘
- `AccessTime` — 예상 종료 시각
- `Place` — 위치
- `People` — 참여자 섹션

이전 `Text(if (expanded) "v" else ">")` plain-text 토글 0건.

### 3. active/ended 톤 분리

- 진행중 카드: `border = BorderStroke(2.dp, COLOR_ACTIVE_ORANGE)` + 기본 배경
- 종료 카드: border 0 + `containerColor = COLOR_ENDED_BG` (#F3F4F6) + 본문 텍스트 muted gray
- 진행중 default expanded = true
- **종료 default expanded = false** (정보 폭주 차단, premise #5)

### 4. 한국어 잔존 정리

| 이전 (영어) | 변경 후 (한국어) |
|---|---|
| `"Today sessions ($N)"` | (제거됨 — 진행중·종료 섹션 헤더로 대체) |
| `"Loading groups..."` | `"그룹 불러오는 중..."` |
| `"#${groupId} (${inviteCode})"` | `"그룹 #${groupId} (${inviteCode})"` |
| `"active" / "ended"` | `"진행중" / "종료"` |
| `"${workType} / $status / ${size} participant(s)"` | `"${workTypeKorean(workType)} · $statusKor · 참여자 ${size}명"` |
| `"Expected end: $time"` | `"예상 종료 $time"` |
| `"Location: $loc"` | `"위치: $loc"` |
| `"Checklist (N/M)"` | `"점검 항목 (N/M)"` |
| `"Ended (N)"` (button result) | `"종료 완료 (참여자 N명)"` |
| `"Error $code"` / `"Network: $msg"` | `"오류 $code"` / `"네트워크 오류: $msg"` |

### 5. 섹션 분리

기존 `GroupSessionListCard` 가 모든 세션을 같은 톤으로 stack 했던 구조를 `GroupSessionsSection` 으로 교체:
- `activeSessions = sessions.filter { it.endedAt == null }`
- `endedSessions = sessions.filter { it.endedAt != null }`
- 진행중·종료 각각 별도 섹션 헤더 + 카드 stack
- 0개 섹션은 자동 hide

## 검증

| Task | 결과 |
|---|---|
| plain text toggle (`"v"`/`">"`) 잔존 | **0** ✅ |
| 영어 string (Today sessions / Loading groups / Expected end / Location: / active / ended / Checklist (N/M) / participant(s) / Error $ / Network: $ / Ended () 잔존 | **0** ✅ |
| Material Icons 등장 | **7** (목표 5+) ✅ |
| `COLOR_ACTIVE_ORANGE` 사용 | 3건 ✅ |
| `COLOR_ENDED_BG` 사용 | 2건 ✅ |
| `mutableStateOf(isActive)` (default expanded 분기) | 1건 ✅ |
| 섹션 헤더 ("진행중 (N개)" + "종료 (N개)") | 2건 ✅ |
| `:app:assembleDebug` | ✅ BUILD SUCCESSFUL (1m 1s) |
| `:app:testDebugUnitTest` | ✅ **54/54 cases PASS** (failures=0, errors=0) |

## 회귀 가드

- 데이터 flow 무변경 (TbmRepository / decodeList / Realtime 모두 그대로)
- 외부 컴포넌트 (TbmDashboardCardComposable / TbmWorkerScreen / TbmStartSection) git diff = 0
- watch/ / Daily*.kt / ai_agent/ (RTSP WIP) 무관

## 시각적 결과 (예상)

매니저 testuser1 로 대시보드 진입 시:

```
📋 TBM 대시보드

[ TBM 세션 시작 ]  ← 시작 폼 (orange info icon, recommendations 폼)
  work_scope / OPS / 시각 / 위치 / 위험·대책 / 그룹 / [N개 세션 시작]
────────────────────────────────────
그룹 #1 (TEST)

⏰ 진행중 (1개)
┌─ 2dp orange border ───────────────┐
│ ⌄ Forklift bay B                  │
│   지게차 · 진행중 · 참여자 0명     │
│   ⏰ 예상 종료 17:46               │
│   위험요인 / 대책 / 점검 항목 / ...│
│   [중점위험 id] [환류 조치 메모]   │
│   [세션 종료]                      │
└─────────────────────────────────────┘

✅ 종료 (1개)
┌─ 회색 배경 ────────────────────────┐
│ › Forklift bay A                  │
│   지게차 · 종료 · 참여자 0명       │
└─────────────────────────────────────┘
```

한 눈에 "진행중 1개 + 종료 1개" 식별. 종료 카드는 collapsed → 정보 폭주 차단.

## Out of Scope (v1.2 이연)

- 참여자 avatar grid (이니셜 동그라미)
- Progress ring (참여 N/M 시각화)
- 미참여 인원 inline Chip
- 예상 종료까지 남은 시간 countdown
- 종료 임박 (예: 30분 전) 시각 강조

## Touched files

- `app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt` (단일 파일)
