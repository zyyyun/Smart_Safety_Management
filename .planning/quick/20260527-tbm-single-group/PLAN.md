---
slug: tbm-single-group
type: quick
created: 2026-05-27
status: in-progress
---

# Quick Task — TBM 다중 그룹 기능 삭제, 단일 그룹 가정

## 사용자 요청

> "그룹 하나로만 돌아갈 수 있게, 다중 그룹 기능을 삭제해줘. 그룹을 선택하는 기능도 같이 없애야 해."

배경: 검단·포천 도금 PoC 환경에서 매니저 1명 = 그룹 1개 가정. 다중 그룹 UI 는 시각 노이즈 + 사용자 혼란.

## Scope

### 변경 범위

| 파일 | 변경 |
|---|---|
| `TbmStartSection.kt` | `selectedGroupIds: Set<Int>` state + Group Checkbox UI (line 264-289) + multi-group async 호출 (line 318-) 제거. 매니저의 첫(유일) 그룹 자동 사용. 버튼 "${selectedGroupIds.size}개 세션 시작" → "세션 시작" |
| `TbmDashboardScreen.kt` | `groups.forEach { group -> GroupSessionsSection(...) }` (line 118) 제거. 단일 group 만 호출. `GroupSessionsSection` → `SessionsSection` rename. 그룹 헤더 "그룹 #${id} (${code})" 제거. |

### 비변경 (의도된 보존)

| 항목 | 이유 |
|---|---|
| `TbmRepository.fetchGroupsForManager(): List<GroupRow>` | Schema 는 group_id FK — future hook. v1.2 에서 multi-group 복원 시 그대로 사용 |
| `TbmRepository.todaySessionsFlow(groupIds: List<Int>): Flow<Map<Int, List<TbmSessionRow>>>` | 동일 — backward compat |
| `tbm_sessions.group_id` schema | 운영 DB 컬럼 그대로. 다중 그룹 ready |
| `profiles.group_id` | 그대로. worker/manager 의 group 소속 |
| TbmWorkerScreen / TbmWorkerCardComposable | worker 는 group 선택 UI 자체 없음 (Phase 9 이래 — workers 의 group_id 가 그대로 사용됨). 변경 X |

## 구현 전략

1. **`fetchGroupsForManager(userId).firstOrNull()` 로 단일 그룹 선택** — 빈 list 면 null, UI 에 "그룹 정보 없음" 표시
2. **Repository 메서드는 그대로 호출**하되 caller 가 `.firstOrNull()` 또는 `.firstOrNull()?.groupId?.let { repo.todaySessionFlow(it) }` 식으로 단일 group 만 사용
3. 매니저가 운영 DB 에서 2 group 의 manager 인 경우 → **첫 그룹만** 사용 (정렬 ORDER BY group_id ASC). data 손실 X, UI 만 좁힘.

## Out of Scope

- DB schema 변경 0 (group_id 컬럼·FK·UNIQUE 모두 유지)
- Edge Function 변경 0 (tbm-start payload 의 group_id 그대로 받음)
- worker UI 변경 0 (이미 단일 group)
- Realtime 채널 변경 0 (group_id 별 채널 그대로)

## Risk

- 운영 DB 에 manager 가 2 group 가진 경우 둘째 그룹 세션이 안 보임 → **의도된 행동** (사용자가 명시 요청)
- v1.2 에서 다중 그룹 복원 시 caller 만 forEach 로 되돌리면 됨 (Repository 무변경)

## Success Criteria

1. TbmStartSection 의 group checkbox 영역 코드 0건
2. `selectedGroupIds` 변수 본문 0건
3. TbmDashboardScreen 의 `groups.forEach` 0건
4. "그룹 #${" 그룹 헤더 Text 0건
5. 버튼 텍스트 "세션 시작" (count 없음)
6. `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
7. `./gradlew :app:testDebugUnitTest` 54/54 cases PASS 유지
8. 단말에서 매니저 진입 → TBM 대시보드 → 그룹 헤더 없이 진행중·종료 섹션만 표시

## Next Steps

- [ ] TbmStartSection.kt 단순화
- [ ] TbmDashboardScreen.kt 단순화 + GroupSessionsSection → SessionsSection rename
- [ ] build + test
- [ ] SUMMARY.md + STATE.md #04 등록 + commit
