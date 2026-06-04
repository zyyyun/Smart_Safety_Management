---
slug: tbm-ui-korean
type: quick
created: 2026-05-26
completed: 2026-05-26
status: complete
---

# Quick Task Summary — TBM 화면 영어 → 한국어

## 적용 범위

### Kotlin UI (12 파일)

| 파일 | 변경 |
|---|---|
| `tbm/TbmStartSection.kt` | "Start TBM session" → "TBM 세션 시작" / "Work scope" → "작업 범위" / "OPS" → "작업 종류 (OPS)" / "Expected end time" → "예상 종료 시각" / "Location" → "위치" / "Notes" → "메모" / "hz id" → "위험 id" / "Groups" → "대상 그룹" / "All" → "전체" / 5 validation 메시지 / "Starting..." → "시작 중..." / "Start N session(s)" → "N개 세션 시작" / TimePicker "OK/Cancel" → "확인/취소" / "Pick time" → "시각 선택" / 3 응답 메시지 (Started/Already exists/Error) |
| `tbm/TbmDashboardScreen.kt` | "TBM dashboard" → "TBM 대시보드" / "No TBM session today" → "오늘 TBM 세션 없음" / "Hazards/Controls/Participants" → "위험요인/대책/참여자" / "Key hazard id" → "중점위험 id" / "Feedback notes" → "환류 조치 메모" / "Not leader" → "리더가 아니거나 이미 종료됨" / "Ending.../End session" → "종료 중.../세션 종료" / "Note" → "메모" / FREETEXT_ITEM_TEXT 상수 |
| `tbm/TbmWorkerScreen.kt` | "TBM guide" → "TBM 작업자 가이드" / "Today sessions/Hazards/Checklist" → "오늘의 세션/위험요인/점검 항목" / "OPS:/Leader:/Expected end:/Ended:" → "OPS:/리더:/예상 종료:/종료됨:" / "Sign below/Clear" → "아래에 서명하세요/지우기" / "Joining.../Join" → "참여 중.../참여하기" / 6 응답 메시지 (Already joined/Wrong group/Session not found/Session ended/Signature empty/Network) |
| `tbm/TbmWorkerCardComposable.kt` | "Today TBM" → "오늘의 TBM" / "Expected end:/sessions today" → "예상 종료:/N개 세션" / 4 state label (NoSession/NeedsCheckin/AlreadyJoined/Ended) / workTypeKorean fallback (Forklift→지게차 / Chemical→화학물질 / Hot work→고온·열처리) |
| `tbm/TbmDashboardCardComposable.kt` | "Today TBM/Participants:/sessions today/Missed alert sent/Tap to start TBM" → 한국어 / 4 state label (NoSession/InProgress/Completed/MissedAlertSent) |
| `tbm/OpsCatalogScreen.kt` | "Load failed:" → "불러오기 실패:" / "no detector" → "검출기 없음" |
| `SettingOpsCatalogActivity.kt` | "Manager only" → "관리자만 접근 가능합니다" / "Login required" → "로그인이 필요합니다" |
| `MyFirebaseMessagingService.kt` | "TBM alert" fallback (2회) → "TBM 알림" |

### Edge Function FCM body/title

| Case | 변경 전 | 변경 후 |
|---|---|---|
| camera-down | title:"Camera frame stopped" / body:"Camera ${camName} (${camArea}) has stopped delivering frames" | title:"카메라 영상 중단" / body:"${camName} (${camArea}) 영상 프레임이 들어오지 않습니다" |
| camera-recovered | title:"Camera recovered" / body:"Camera ${camName} (${camArea}) is delivering frames again" | title:"카메라 영상 복구" / body:"${camName} (${camArea}) 영상이 다시 들어옵니다" |
| tbm-start | title:"TBM session started" | title:"TBM 세션 시작" |
| tbm-missed | title:"TBM missed attendance" / body:"${scopeText}: N worker(s) missed" | title:"TBM 미참여 알림" / body:"${scopeText} 미참여 N명" |

## Out of Scope (그대로 유지)

- Kotlin 코드 주석 (개발자만 보는 부분)
- 변수명·function 이름·log 메시지
- Smoke shell scripts 의 echo 메시지
- 17 schema 의 OPS seed (이전 commit 에서 이미 한국어 적용)
- Edge Function 내부 error 메시지 (manager-only / not found 등 API 본문) — Android 가 status code 로 분기하므로 무관

## 검증

- `./gradlew :app:assembleDebug` ✅ BUILD SUCCESSFUL (57s)
- `./gradlew :app:testDebugUnitTest --rerun-tasks` ✅ BUILD SUCCESSFUL (1m 24s) — 54/54 cases PASS 유지
- ⏸ `supabase functions deploy notifications` — Docker Desktop 의 D: drive mount stale 이슈로 일시 차단 (`error while creating mount source path '/run/desktop/mnt/host/d/...': mkdir /run/desktop/mnt/host/d: file exists`). 환경 문제 — 다음 deploy 시 자동 반영.

## Manual follow-up

1. Docker Desktop 재시작 후 `supabase functions deploy notifications` 1회 — Edge Function 의 한국어 body/title 운영 반영.

## Touched files (총 9)

- app/src/main/java/com/example/smart_safety_management/MyFirebaseMessagingService.kt
- app/src/main/java/com/example/smart_safety_management/SettingOpsCatalogActivity.kt
- app/src/main/java/com/example/smart_safety_management/tbm/OpsCatalogScreen.kt
- app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardCardComposable.kt
- app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt
- app/src/main/java/com/example/smart_safety_management/tbm/TbmStartSection.kt
- app/src/main/java/com/example/smart_safety_management/tbm/TbmWorkerCardComposable.kt
- app/src/main/java/com/example/smart_safety_management/tbm/TbmWorkerScreen.kt
- supabase/functions/notifications/index.ts
