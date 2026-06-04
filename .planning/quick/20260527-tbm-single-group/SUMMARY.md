---
slug: tbm-single-group
type: quick
created: 2026-05-27
completed: 2026-05-27
status: complete
---

# Quick Task Summary — TBM 다중 그룹 기능 삭제

## 사용자 요청

> "그룹 하나로만 돌아갈 수 있게, 다중 그룹 기능을 삭제해줘. 그룹을 선택하는 기능도 같이 없애야 해."

검단·포천 도금 PoC 환경 = 매니저 1명 / 그룹 1개 가정. 다중 그룹 UI 가 시각 노이즈.

## 적용 범위

### TbmStartSection.kt

| 변경 |
|---|
| `groups: List<GroupRow>` → `managerGroup: GroupRow?` (단일) |
| `selectedGroupIds: Set<Int>` state 완전 제거 |
| `perGroupResults: Map<Int, String>` → `submitResult: String?` |
| Group Checkbox UI (이전 line 263-294, "대상 그룹" / "전체" 체크 / 그룹별 row) 전부 제거 |
| validation `selectedGroupIds.isEmpty()` → `managerGroup == null` (메시지: "그룹 정보 없음 — 관리자 권한 확인 필요") |
| `coroutineScope { selectedGroupIds.map { async { ... } }.awaitAll() }` multi-group call → 단일 `api.callTbmStart(...)` 호출 |
| 버튼 텍스트 `"${selectedGroupIds.size}개 세션 시작"` → `"세션 시작"` |
| 버튼 enabled `groups.isNotEmpty()` → `managerGroup != null` |
| 응답 처리: 단일 `submitResult` 텍스트 (성공 / 409 / 오류 / 네트워크) |
| unused import 정리 — `Checkbox`, `async`, `awaitAll`, `coroutineScope` 4건 제거 |

### TbmDashboardScreen.kt

| 변경 |
|---|
| `groups: List<GroupRow>` + `sessionsByGroup: Map<Int, List<TbmSessionRow>>` → `managerGroup: GroupRow?` + `todaySessions: List<TbmSessionRow>` |
| `repo.todaySessionsFlow(groupIds: List<Int>)` (multi) → `repo.todaySessionFlow(gid: Int)` (single) 호출로 교체 — Repository 의 single 메서드는 이미 존재 (line 21) |
| `groups.forEach { group -> GroupSessionsSection(...) }` 완전 제거 |
| `GroupSessionsSection` (group: GroupRow 받음) → `SessionsSection` (group 없이) rename |
| 그룹 헤더 `Text("그룹 #${group.groupId} (${group.inviteCode})", ...)` 제거 |
| 빈 상태 메시지 `"그룹 불러오는 중..."` → `"그룹 정보 불러오는 중..."` (managerGroup == null 분기) |

### 비변경 (의도된 보존)

- `TbmRepository.fetchGroupsForManager(): List<GroupRow>` — schema 가 multi-ready 라 future hook 보존
- `TbmRepository.todaySessionsFlow(groupIds: List<Int>)` — backward compat, 다른 caller (없음) 보호
- `tbm_sessions.group_id` 컬럼 + FK — DB schema 무변경
- `profiles.group_id` — 무변경
- Edge Function `tbm-start` payload 의 `group_id` — 그대로 받음
- worker UI (TbmWorkerScreen / TbmWorkerCardComposable) — worker 는 그룹 선택 UI 자체 없었음, 변경 0
- Realtime 채널 — group_id 별 채널 그대로 (단일 그룹만 구독)

## 검증

| Task | 결과 |
|---|---|
| TbmStartSection: `groups.forEach` 잔존 | 0건 ✅ |
| TbmStartSection: `Checkbox` 본문 사용 | 0건 ✅ |
| TbmStartSection: `selectedGroupIds` 잔존 | 0건 ✅ |
| TbmDashboardScreen: `groups.forEach` 잔존 | 0건 ✅ |
| TbmDashboardScreen: "그룹 #ID" 헤더 텍스트 | 0건 ✅ |
| TbmDashboardScreen: `GroupSessionsSection` 잔존 | 0건 ✅ (→ `SessionsSection` 2건 declaration + invocation) |
| TbmDashboardScreen: `managerGroup` state | 5건 ✅ |
| `:app:assembleDebug` | ✅ BUILD SUCCESSFUL (1m 0s) |
| `:app:testDebugUnitTest` | ✅ **54/54 cases PASS** (failures=0) |

## 시각적 결과 (예상)

매니저 (testuser1) 진입 시:

```
📋 TBM 대시보드

[ TBM 세션 시작 ]              ← 시작 폼 (Group Checkbox 영역 통째로 사라짐)
  작업 범위
  작업 종류 (OPS)
  예상 종료 시각
  위치 / 메모
  위험요인 / 대책
  [세션 시작]                  ← 단일 버튼 (count 없음)
─────────────────────────────
⏰ 진행중 (1개)                ← 그룹 헤더 없이 바로 진행중 섹션
   ┌─ 2dp orange border ─┐
   │ ⌄ Forklift bay B    │
   │   ...               │
   └─────────────────────┘

✅ 종료 (1개)                  ← 그룹 헤더 없이 바로 종료 섹션
   ┌─ 회색 배경 ─────────┐
   │ › Forklift bay A    │
   └─────────────────────┘
```

이전과 비교:
- "그룹 #1 (TEST)" 헤더 노이즈 제거
- "대상 그룹 / 전체 체크박스 / 각 그룹별 체크박스 row" 3-줄 영역 통째로 제거
- 버튼 텍스트 "2개 세션 시작" → "세션 시작" — 직관적

## v1.2 복원 시나리오 (참고)

추후 다중 그룹 매니저가 실제 needed 시:
1. `TbmStartSection.managerGroup` → `groups: List<GroupRow>` 복원
2. Group Checkbox UI 다시 작성 (커밋 e557fb4 이전 패턴 참조)
3. `coroutineScope { ... async ... awaitAll() }` 패턴 복원
4. `TbmDashboardScreen.managerGroup` → `groups + sessionsByGroup` 복원
5. `SessionsSection` → `GroupSessionsSection` rename + group: GroupRow parameter 복원
6. Repository 메서드는 **변경 0** — 이미 multi-ready

총 변경량: 단일 commit revert 가능 (~50 lines).

## 회귀 가드

- watch/ / Daily*.kt / ai_agent/ 무관 (git diff 0)
- 운영 DB schema / Edge Function / Realtime 채널 모두 무변경
- 54/54 unit tests PASS 유지

## Touched files

- `app/src/main/java/com/example/smart_safety_management/tbm/TbmStartSection.kt`
- `app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt`
