---
quick_task: camera-unify-and-detection-thumbnails
status: complete
created: 2026-05-27
completed: 2026-05-27
commits_total: 10
issues_addressed:
  - "Issue 1 (전경/현장 강제 split, fully resolved — APK install 후 즉시 visible)"
  - "Issue 2 (AI감지 list thumbnail, client wiring complete — backend follow-up 후 visible)"
files_created:
  - app/src/test/java/com/example/smart_safety_management/screens/detail/CctvSplitVisibilityTest.kt
  - app/src/test/java/com/example/smart_safety_management/EventDataMappingTest.kt
  - app/src/test/java/com/example/smart_safety_management/EventItemThumbnailCharacterizationTest.kt
  - .planning/quick/20260527-camera-unify-and-detection-thumbnails/BACKEND-CHECK.md
files_modified:
  - app/src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt
  - app/src/main/java/com/example/smart_safety_management/SignUpService.kt
  - app/src/main/java/com/example/smart_safety_management/AIEventDetect.kt
tests_passed:
  jvm_new:
    CctvSplitVisibilityTest: 6 methods / 7 assertions PASS
    EventDataMappingTest: 3 assertions PASS
    EventItemThumbnailCharacterizationTest: 3 assertions PASS
  jvm_regression: BUILD SUCCESSFUL (Phase 11 + TBM + auth + watch + ui 모두 GREEN)
  assemble_debug: BUILD SUCCESSFUL
  ai_agent: 28/28 PASS (committed tests)
  j2208a: 43/43 PASS
backend_check_verdict: follow-up-needed
backend_followup_owner: Phase 13 (데이터 신뢰성) 흡수 권장
---

# Quick Task: 전경/현장 통합 + AI감지 list 썸네일

**일자:** 2026-05-27
**브랜치:** feature_rtps_test
**총 commit 수:** 10 (5 RED→GREEN pair + 1 backend doc + 1 SUMMARY/STATE)

## 사용자 보고 (2026-05-27)

1. **Issue 1 — 전경/현장 강제 split**: CCTV 상세화면에서 같은 카메라일 때도 '전경' + '현장' 두 섹션 카드가 무조건 렌더됨. 같은 영상이 두 번 보임.
2. **Issue 2 — AI감지 list 이미지 미표시**: AI 감지 list 의 각 event row 가 detector icon 만 보이고, 실제 감지된 capture 이미지가 표시되지 않음.

## 해결 결과

### Issue 1 (fully resolved — APK install 즉시 사용자에게 visible)

`InternalDetail.kt` 의 `InternalDetailScreen` 안 카드 섹션 로직을 conditional rendering 으로 전환:

- `shouldShowSiteSection(overviewUrl, siteUrl)` pure helper 신규 (top-level, `internal` visibility, 회귀 가드 7 assertions)
- 같은 카메라(`siteUrl == overviewUrl`, `null`, blank — 모두 trim 후 비교) → 1 카드만 표시, 라벨은 "실시간 화면"
- 다른 URL → 기존처럼 '전경' + '현장' 2 카드 (회귀 0)

### Issue 2 (client wiring fully complete — backend follow-up needed)

⚠ **중요**: APK 만 install 해도 thumbnail 이 보이지는 **않음**. backend 가 `image_url` 을 응답에 포함하지 않기 때문. Client side 는 backend 가 반환 시작하면 즉시 표시되도록 모든 wiring 이 완료된 상태.

수행 변경:

- `SignUpService.kt`: `DetectionEventDTO` 에 `@SerializedName("image_url") val imageUrl: String? = null` 필드 추가 (default null → backward compat)
- `AIEventDetect.kt`: `EventData` data class 에 `val imageUrl: String? = null`
- `AIEventDetect.kt`: `toEventData()` mapping 에 `imageUrl = this.imageUrl`
- `AIEventDetect.kt`: `EventItem` Composable 의 inner Row 시작 위치에 `AsyncImage` 추가 (56dp × 56dp, `ContentScale.Crop`, `RoundedCornerShape(6.dp)`, `event.imageUrl.isNullOrBlank()` 가드)
- `AIEventDetect.kt`: imports — `coil.compose.AsyncImage`, `androidx.compose.ui.layout.ContentScale`, `androidx.compose.ui.draw.clip` (3개)

backend 측 현황 (자세한 내용은 `BACKEND-CHECK.md`):

- `detection_events` 테이블 schema 에 `image_url` 칼럼 자체가 부재 (camera_captures.image_url 만 존재, capture_id FK 로 join 가능)
- 로컬 source tree 에 `supabase/functions/detection/` Edge function 폴더 부재 (remote deploy 추적 필요)
- Legacy `server/get_recent_detection_events.js` SELECT 절에도 image_url 없음

**Follow-up 권장 위치:** Phase 13 (데이터 신뢰성 + 정보구조) 또는 검단·포천 6월 설치 전 별도 quick task.

## TDD Discipline

모든 production 변경은 RED 먼저 commit → GREEN 으로 전환. 무예외 (사용자 directive 2026-05-27 TDD Iron Law).

| # | Task | RED commit | GREEN commit |
|---|------|------------|--------------|
| 1 | shouldShowSiteSection helper | 14fb0a9 | 2fe7697 |
| 2 | InternalDetail conditional wiring | (Task 1 test 가 가드) | de908f8 |
| 3 | DetectionEventDTO + EventData + mapping imageUrl | 8868a36 | 17bebdf |
| 4 | EventItem AsyncImage thumbnail | 01d660a | 9986799 |
| 5 | Backend 검증 (no TDD — investigation) | — | 060655b |

## 회귀 가드 결과

- `./gradlew :app:testDebugUnitTest` (full): **BUILD SUCCESSFUL** — Phase 11 / TBM / auth / watch / ui / 신규 3 test 모두 GREEN
- `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL** (Task 4 직후 30s)
- `ai_agent pytest tests/`: **28/28 PASS** (committed tests). 3 ERROR 는 `tests/test_scheduler_rtsp_autodetect.py` 라는 untracked 파일 (본 task 시작 전 이미 git status 에 `??` 로 존재, scope 밖)
- `j2208a pytest tests/`: **43/43 PASS**
- `git diff HEAD~9 -- ai_agent/ j2208a/`: **0 lines** (cross_cutting_truth #4 충족)

## Deviations from Plan

### [Rule 3 - Blocking issue auto-fix] 추가 import 1개

Plan 의 Task 4 `<action>` 은 `import coil.compose.AsyncImage` + `import androidx.compose.ui.layout.ContentScale` 만 명시했으나, `Modifier.clip(RoundedCornerShape(6.dp))` 가 compile 되려면 `androidx.compose.ui.draw.clip` import 도 필요. AIEventDetect.kt 의 기존 imports 에 부재. 한 줄 추가하여 GREEN 으로 전환. 의미적/UX 영향 0.

### [Acceptance criteria literal grep 차이 1건]

Plan Task 3 의 acceptance criteria: "grep `val imageUrl: String?` in AIEventDetect.kt → 2 hits (data class + mapping)". 실제로는 mapping line 이 `imageUrl = this.imageUrl` (no `val`) 이라 grep hit 1 개. 기능적 정확성은 `EventDataMappingTest` 3/3 PASS 로 검증됨. Plan 의 grep 카운트 오기재.

### 그 외 deviation 없음

ai_agent / j2208a / supabase 디렉터리 코드 변경 0. Compose `return@<Lambda>` 신규 코드 0. 한국어 default 유지.

## Issue Resolution 명시 — 사용자 user-visible 영향

### Issue 1 → 즉시 visible (APK install 후 확인 가능)

같은 카메라(`liveUrlDetail == liveUrl` 또는 null) 상세화면 진입 시:
- 변경 전: '전경' + '현장' 카드 2개 + 같은 영상 두 번
- 변경 후: '실시간 화면' 카드 1개

다른 URL 케이스는 기존 동작 유지 (회귀 0).

### Issue 2 → backend follow-up 후 visible

현재 변경분만으로는 사용자 단말에서 thumbnail 보이지 **않음**. backend 가 `image_url` 반환 시작하면 코드 변경 0 으로 즉시 표시됨.

backend 변경 (별도 plan):
1. `supabase/functions/detection/index.ts` (remote deployed) 의 `recent_events` action SELECT 절에 `camera_captures.image_url` join + alias
2. `migrations/018_detection_events_image_url.sql` 신규 (선택)
3. Legacy `server/get_recent_detection_events.js` 도 동시 수정 (선택)

## Self-Check: PASSED

### 신규 파일 존재 확인
- (yes) app/src/test/java/com/example/smart_safety_management/screens/detail/CctvSplitVisibilityTest.kt
- (yes) app/src/test/java/com/example/smart_safety_management/EventDataMappingTest.kt
- (yes) app/src/test/java/com/example/smart_safety_management/EventItemThumbnailCharacterizationTest.kt
- (yes) .planning/quick/20260527-camera-unify-and-detection-thumbnails/BACKEND-CHECK.md

### 신규 commit 존재 확인 (10건)
- (yes) 14fb0a9 test(quick-camera-thumb): RED add CctvSplitVisibilityTest
- (yes) 2fe7697 feat(quick-camera-thumb): GREEN add shouldShowSiteSection helper
- (yes) de908f8 feat(quick-camera-thumb): InternalDetail 현장 섹션 conditional rendering
- (yes) 8868a36 test(quick-camera-thumb): RED add EventDataMappingTest
- (yes) 17bebdf feat(quick-camera-thumb): GREEN add imageUrl to DTO/EventData/mapping
- (yes) 01d660a test(quick-camera-thumb): RED add EventItemThumbnailCharacterizationTest
- (yes) 9986799 feat(quick-camera-thumb): GREEN EventItem 에 AsyncImage thumbnail 추가
- (yes) 060655b docs(quick-camera-thumb): backend check log — image_url 반환 여부 follow-up needed
- (+ 본 SUMMARY/STATE 커밋 1건 = 10 총)

### acceptance criteria 충족 확인
- (yes) fun shouldShowSiteSection in InternalDetail.kt: 1 hit
- (yes) if (showSite) in InternalDetail.kt: 1 hit
- (yes) shouldShowSiteSection(finalOverviewUrl, finalSiteUrl): 1 hit
- (yes) @SerializedName("image_url") in SignUpService.kt: 1 hit
- (yes) import coil.compose.AsyncImage in AIEventDetect.kt: 1 hit
- (yes) event.imageUrl in AIEventDetect.kt: 1+ hit
- (yes) isNullOrBlank in AIEventDetect.kt: 1 hit (event.imageUrl 가드)
