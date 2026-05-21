---
phase: 09-tbm-worker-guide
plan: 03
subsystem: android
tags: [supabase-kt, realtime, storage-kt, signature-canvas, compose, fcm, ui, tdd-tasks, T-9-01, T-9-06, T-9-12, T-9-13, T-9-14, T-9-15]
requirements: [TBM-02]

# Dependency graph
requires:
  - phase: 04-watch-j2208a-pipeline
    provides: D-09 알림 전이 원칙 (FCM payload data.type 분기 + Activity 진입 패턴)
  - phase: 07-watch-app-bridge
    provides: watch/ 9 main 파일 (tbm/ 패키지 1:1 미러 원형) + ComposeView 임베드 + SupabaseModule 싱글톤 + MyApp.supabase by-lazy
  - phase: 08-rtsp-camera
    provides: fcm_default_channel 재사용 패턴 (Option B, channel_id 신규 X)
  - phase: 09-tbm-worker-guide-plan-01
    provides: 013_tbm_schema.sql + tbm-signatures Storage 버킷 Option A + 4 테이블 RLS
  - phase: 09-tbm-worker-guide-plan-02
    provides: notifications/index.ts 4 case (tbm-start/checkin/end/missed) 운영 배포
provides:
  - app/.../tbm/ 패키지 12 main 파일 + 4 TDD test 파일
  - TbmDashboardActivity (manager only, T-9-13 권한 가드)
  - TbmWorkerActivity (worker FCM extras hint + DB 재조회)
  - HomeActivity 첫 ComposeView 임베드 (TBM 대시보드 카드, Pitfall 12 Theme 래핑)
  - HomeWorkerActivity 추가 ComposeView 임베드 (TBM 카드, watch_card 아래)
  - MyFirebaseMessagingService tbm_alert 분기 + showTbmAlertNotification + UserRole 분기
  - app/build.gradle.kts storage-kt:2.2.0 의존성 lock
  - MyApp.supabase install(Storage) 추가 (변경 1줄)
affects: [phase-9-plan-04]

# Tech tracking
tech-stack:
  added:
    - "io.github.jan-tennert.supabase:storage-kt:2.2.0 (Phase 7 realtime + postgrest 2.2.0 ABI 일관)"
  patterns:
    - "Dynamic session_id 2-stage Realtime — Stage A (group_id eq, todaySessionFlow) + Stage B (session_id eq, participantsFlow + checklistsFlow)"
    - "tbm_templates 채널 미구독 anti-pattern (RESEARCH C2) — seed-only, PostgREST 1회 fetch 만"
    - "JVM unit test 호환 SignatureState.toPngBytes() — canvasSize=0 OR isEmpty 시 ByteArray(0) early-return (Bitmap native heap 미접근)"
    - "Pitfall 1·2 명시 적용 — Bitmap.recycle finally + state.currentPath = state.currentPath setter 강제"
    - "Pitfall 8 강제 — ExpectedEndAtValidator 의 DateTimeFormatter.ISO_OFFSET_DATE_TIME (offset 누락 거부)"
    - "Pitfall 12 강제 — HomeActivity 첫 ComposeView 의 Smart_Safety_ManagementTheme 명시 래핑"
    - "Option B (Pitfall 3 회피) — fcm_default_channel 재사용, channel_id 신규 X, Android 코드 변경 0"

key-files:
  created:
    - app/src/main/java/com/example/smart_safety_management/tbm/TbmModels.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/WorkTypeValidator.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/ExpectedEndAtValidator.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/TbmParticipantsReducer.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/SignatureCanvas.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/TbmRetrofitApi.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/TbmRepository.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/TbmWorkerCardComposable.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardCardComposable.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/TbmStartSection.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt
    - app/src/main/java/com/example/smart_safety_management/tbm/TbmWorkerScreen.kt
    - app/src/main/java/com/example/smart_safety_management/TbmDashboardActivity.kt
    - app/src/main/java/com/example/smart_safety_management/TbmWorkerActivity.kt
    - app/src/test/java/com/example/smart_safety_management/tbm/WorkTypeValidatorTest.kt
    - app/src/test/java/com/example/smart_safety_management/tbm/ExpectedEndAtValidatorTest.kt
    - app/src/test/java/com/example/smart_safety_management/tbm/TbmParticipantsReducerTest.kt
    - app/src/test/java/com/example/smart_safety_management/tbm/SignatureStateTest.kt
    - .planning/phases/09-tbm-worker-guide/09-03-SUMMARY.md
  modified:
    - app/build.gradle.kts (storage-kt:2.2.0 추가)
    - app/proguard-rules.pro (gotrue + ktor-server dontwarn)
    - app/src/main/java/com/example/smart_safety_management/MyApp.kt (install(Storage) 1줄)
    - app/src/main/java/com/example/smart_safety_management/MyFirebaseMessagingService.kt (tbm_alert 분기 + showTbmAlertNotification)
    - app/src/main/java/com/example/smart_safety_management/HomeActivity.kt (setupTbmDashboardCard 추가)
    - app/src/main/java/com/example/smart_safety_management/HomeWorkerActivity.kt (setupTbmCard 추가)
    - app/src/main/AndroidManifest.xml (TbmDashboardActivity + TbmWorkerActivity 등록)
    - app/src/main/res/layout/main_home.xml (tbm_dashboard_compose ComposeView 추가)
    - app/src/main/res/layout/main_home_worker.xml (tbm_card_compose ComposeView 추가)

key-decisions:
  - "advisor — JUnit4 일관 채택 (Plan 의 JUnit5 syntax 정정, libs.versions.toml = junit 4.13.2, watch/ test 패턴 미러)"
  - "advisor — UserSession.userRole 가드는 MANAGER 단일 분기 (UserRole.GENERAL_MANAGER 존재 X, UserRole enum = MANAGER|WORKER only)"
  - "advisor — MyApp.kt 의 install order/engine syntax 보존 — Plan 의 engine(CIO) 가 아닌 기존 CIO.create() 그대로 + Storage 1줄 추가"
  - "advisor — fcm_default_channel 재사용 일관 (Phase 8 Option B 패턴, watch_alerts 새 채널 생성 X)"
  - "advisor — SignatureState JVM unit test 호환을 위해 toPngBytes() 가 canvasSize=0 OR isEmpty 시 ByteArray(0) early-return (ImageBitmap native 미접근)"
  - "C2 anti-pattern 적용 — tbm_templates 채널 구독 X (seed-only), fetchTemplates() 만 PostgREST 1회 fetch"
  - "Dynamic session_id 2-stage Realtime — Stage A todaySessionFlow(groupId) 가 session 변화 시 emit, Stage B participantsFlow/checklistsFlow 가 collectAsState(sessionId) key 로 자동 재구독"
  - "C3 Option A Storage 업로드 — supabase.storage.from('tbm-signatures').upload(path, bytes) anon INSERT, key prefix = {session_id}/{user_id}_{epoch_ms}.png (T-9-01 mitigation)"

patterns-established:
  - "Pattern: TBM Android UI 모듈 = Phase 7 watch/ 패턴 1:1 미러 + Dynamic session_id 2-stage Realtime + SignatureCanvas (Compose + Bitmap recycle Pitfall 1 + Path setter 강제 Pitfall 2)"
  - "Pattern: HomeActivity 첫 ComposeView 임베드 — Smart_Safety_ManagementTheme 래핑 강제 (Pitfall 12), Phase 7 setupWatchCard 패턴 미러"
  - "Pattern: TDD 4 test files (JUnit4) — Phase 7 watch/ test 26 cases 패턴 직접 미러, Korean repo path workaround layout.buildDirectory.set('D:/ssm-app-build') 적용"

metrics:
  duration: 약 50분
  completed: 2026-05-18T07:15:00Z
  tasks_completed: 5
  files_created: 19
  files_modified: 9
  commit_count: 5
---

# Phase 9 Plan 09-03: Android tbm/ 패키지 + 2 신규 Activity + FCM 분기 Summary

**One-liner:** Android `tbm/` 패키지 12 main + 4 TDD test 파일 (Phase 7 watch/ 1:1 미러 + Dynamic session_id 2-stage Realtime + Compose SignatureCanvas) + storage-kt:2.2.0 의존성 + MyApp.install(Storage) + HomeActivity·HomeWorkerActivity ComposeView 임베드 (첫 manager + 추가 worker) + TbmDashboardActivity·TbmWorkerActivity 신규 + MyFirebaseMessagingService tbm_alert 분기 + UserRole 분기 + compileDebugKotlin BUILD SUCCESSFUL + 21 TDD cases ALL PASS + watch/ 26 cases + ai_agent 28/28 회귀 유지.

## Tasks 실행 결과

### Task 1: storage-kt 2.2.0 + MyApp.install(Storage) (커밋 `c94ee1c`)

3 파일 수정:

#### `app/build.gradle.kts`

기존 realtime-kt + postgrest-kt 2.2.0 옆에 storage-kt 1 줄 추가:

```kotlin
implementation("io.github.jan-tennert.supabase:realtime-kt:2.2.0")
implementation("io.github.jan-tennert.supabase:postgrest-kt:2.2.0")
implementation("io.github.jan-tennert.supabase:storage-kt:2.2.0")  // Phase 9 09-03
implementation("io.ktor:ktor-client-cio:2.3.9")
```

ABI 일관성 (Pitfall 1): realtime + postgrest 와 동일 2.2.0 버전 강제. supabase-kt 3.x 거부 — `grep -c 'storage-kt:3\.' app/build.gradle.kts` = 0.

Transitive (Pitfall 10): gotrue-kt:2.2.0 + ktor-server-core/cio:2.3.9 자동 추가, APK +50KB acceptable.

#### `app/proguard-rules.pro`

```pro
# Phase 9 / 09-03 — storage-kt 2.2.0 transitive (gotrue-kt + ktor-server-*).
-dontwarn io.github.jan.supabase.gotrue.**
-dontwarn io.ktor.server.**
```

현재 `isMinifyEnabled = false` 라 즉시 효력 없음 — v1.1 minify 활성화 대비 선반영.

#### `app/src/main/java/com/example/smart_safety_management/MyApp.kt`

`install(Storage)` 1줄 추가 + import:

```kotlin
import io.github.jan.supabase.storage.Storage  // 신규 import

val supabase: SupabaseClient by lazy {
    createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
        install(Realtime)
        install(Postgrest)
        install(Storage)   // Phase 9 신규 1줄
        httpEngine = CIO.create()
    }
}
```

advisor 정정 적용: Plan 의 `engine(CIO)` 시도 안 함 — 기존 `CIO.create()` 호출 보존 + 1줄 추가만.

### Task 2: tbm/ Models + Validators + Reducer + SignatureCanvas + 4 TDD tests (커밋 `8df2ced`)

5 main + 4 test 파일. RED → GREEN 사이클.

#### 5 main 파일 (`tbm/`)

1. **`TbmModels.kt`** (110 lines) — 4 @Serializable row classes (Phase 7 WatchModels 패턴 1:1 미러):
   - `TbmSessionRow` (session_id, group_id, session_date, started_at, ended_at, expected_end_at, leader_user_id, work_type, location, notes, missed_alert_at, created_at)
   - `TbmTemplateRow` (template_id, work_type, title, checklist: List<String>)
   - `TbmChecklistRow` (checklist_id, session_id, item_idx, item_text, is_checked, note, checked_at)
   - `TbmParticipantRow` (participant_id, session_id, user_id, signed_at, signature_url, method)
   - `ChangeKind` enum (INSERT/UPDATE/DELETE)
   - 3 Retrofit request/response pairs — TbmStart, TbmCheckin, TbmEnd (Plan 09-02 의 4 Edge Function 계약, tbm-missed 는 cron 호출만이라 Android 미사용)

2. **`WorkTypeValidator.kt`** (15 lines) — RESEARCH Example A 그대로:
   - `ALLOWED = setOf("fire", "electric", "height", "heavy", "general")`
   - `isValid(workType): Boolean` — case-sensitive
   - `normalize(input): String = input.lowercase().trim()`

3. **`ExpectedEndAtValidator.kt`** (32 lines) — Pitfall 8 timezone:
   - `parse(input): Result<ZonedDateTime>` — `DateTimeFormatter.ISO_OFFSET_DATE_TIME` 강제, offset 누락 거부
   - `nowKst()` — `ZonedDateTime.now(ZoneId.of("Asia/Seoul"))`
   - `formatForServer(z)` — `z.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)` → "2026-05-18T09:15:00+09:00"

4. **`TbmParticipantsReducer.kt`** (60 lines) — RESEARCH Example B + Phase 7 SafetyAlertReducer 패턴:
   - `apply(current, PostgresAction)` — INSERT/UPDATE/DELETE 분기, `decodeRecord<TbmParticipantRow>()` 사용
   - `applyDirect(current, ChangeKind, row)` — pure function, unit test 직접 호출 (mock 불필요)
   - 추가 `TbmChecklistsReducer.applyDirect` — manager UPDATE 시 worker 측 갱신용

5. **`SignatureCanvas.kt`** (150 lines) — RESEARCH Pattern 1 + Pitfall 1·2 모두 적용:
   - `class SignatureState` — paths + currentPath (mutableStateOf<Path?>) + canvasSize + isEmpty + clear() + toPngBytes()
   - `toPngBytes()` JVM unit test 호환 early-return: `if (canvasSize == IntSize.Zero || isEmpty) return ByteArray(0)` — Android Bitmap native heap 미접근 (advisor 권고, NoClassDefFoundError 회피).
   - Pitfall 1 mitigation: `Bitmap.createBitmap()` 후 finally 블록의 `if (!androidBitmap.isRecycled) androidBitmap.recycle()` 강제.
   - `@Composable SignatureCanvas` — `detectDragGestures` + `state.currentPath = state.currentPath` setter 강제 (Pitfall 2).
   - Stroke = 4dp Color.Black + RoundCap + RoundJoin + AntiAlias.

#### 4 TDD test 파일 (`tbm/test/`)

JUnit4 (`org.junit.Test` + `org.junit.Assert.*`) — advisor 정정 적용 (Plan 의 JUnit5 syntax → 기존 watch/ test 패턴 1:1 미러).

- **`WorkTypeValidatorTest`** (8 cases): valid fire / all 5 types / empty / unknown / uppercase before normalize / normalize uppercase / normalize trim / normalize then isValid combined.
- **`ExpectedEndAtValidatorTest`** (5 cases): KST offset / reject no offset / reject invalid string / accept Z UTC marker / accept past timestamp (parse-only).
- **`TbmParticipantsReducerTest`** (6 cases): insert into empty / insert duplicate id updates / update preserves unrelated / insert appends new / delete removes / update unknown id noop.
- **`SignatureStateTest`** (2 cases): empty state isEmpty / canvasSize zero yields empty bytes.

**총 21 cases ALL PASS** (실측 `D:/ssm-app-build/test-results/testDebugUnitTest/TEST-com.example.smart_safety_management.tbm.*.xml`):

```
tbm.ExpectedEndAtValidatorTest    tests="5" failures="0" errors="0"
tbm.SignatureStateTest             tests="2" failures="0" errors="0"
tbm.TbmParticipantsReducerTest     tests="6" failures="0" errors="0"
tbm.WorkTypeValidatorTest          tests="8" failures="0" errors="0"
```

### Task 3: TbmRepository + TbmRetrofitApi + 2 Card Composables + TbmStartSection (커밋 `f4d3f3b`)

5 main 파일.

#### `TbmRetrofitApi.kt` (50 lines)

`TbmFunctionsApi` interface — `@POST` + `@Url` dynamic URL (`${BuildConfig.SUPABASE_URL}/functions/v1/notifications`) + Bearer anon header. 3 메서드: `callTbmStart`, `callTbmCheckin`, `callTbmEnd`. Phase 7 `NotificationsFunctionsApi` 패턴 1:1 미러.

#### `TbmRepository.kt` (130 lines)

**3 채널 Realtime + Dynamic session_id 2-stage 패턴** (RESEARCH §Pattern 2):

- **Stage A**: `todaySessionFlow(groupId): Flow<TbmSessionRow?>`
  - 초기 PostgREST fetch: `tbm_sessions WHERE group_id=$1 AND session_date=$today LIMIT 1`
  - Realtime channel `tbm_sessions:group_$groupId` + `filter("group_id", EQ, groupId)`
  - 변경 발생 시 PostgREST 재조회 (UNIQUE `(group_id, session_date)` 보장으로 단건)
- **Stage B (a)**: `participantsFlow(sessionId): Flow<List<TbmParticipantRow>>`
  - 초기 fetch + `tbm_participants:session_$sessionId` 채널
  - `TbmParticipantsReducer.apply` 사용
- **Stage B (b)**: `checklistsFlow(sessionId): Flow<List<TbmChecklistRow>>`
  - 초기 fetch + `tbm_checklists:session_$sessionId` 채널
  - `TbmChecklistsReducer.applyDirect` 사용

**tbm_templates 채널 미구독** (C2 anti-pattern, line 805): seed-only — `fetchTemplates()` PostgREST 1회 fetch only.

회귀 가드: `grep -c 'channel.*tbm_templates' TbmRepository.kt` = **0**.

#### `TbmWorkerCardComposable.kt` (130 lines, D-07)

4 상태 (sealed class `TbmWorkerCardState`):
- `NoSession` — 회색 "세션 없음"
- `NeedsCheckin` — 노랑 "⚠ 참여 필요" (session != null && endedAt == null && joined == false)
- `AlreadyJoined` — 초록 "✓ 참여 완료" (joined)
- `Ended` — 회색 "종료됨" (endedAt != null)

`computeWorkerCardState(session, participants, userId)` pure function 으로 분리 가능.

#### `TbmDashboardCardComposable.kt` (110 lines, D-06)

4 상태 (sealed class `TbmDashboardCardState`):
- `NoSession` — 회색
- `InProgress(checked, total)` — 노랑 "진행 중 (3/5)"
- `Completed` — 초록 "완료"
- `MissedAlertSent` — 빨강 "⚠ 미참여 알림 발사됨"

3 채널 모두 collect (todaySessionFlow + participantsFlow + checklistsFlow). 카드 클릭 → `onClickDashboard()` → TbmDashboardActivity 진입.

#### `TbmStartSection.kt` (170 lines, manager only)

`ExposedDropdownMenuBox` (Material3) — 5 work_type 선택 (title 한글 = tbm_templates fetched 또는 workTypeKorean fallback). `OutlinedTextField` for expected_end_at (default = `nowKst().plusMinutes(15)` ISO_OFFSET_DATE_TIME). "세션 시작" 버튼 → `callTbmStart` → 200/409/400 분기 + Toast.

### Task 4: 2 신규 Activity + ComposeView 임베드 + Manifest + layouts + HomeActivity·HomeWorker integration (커밋 `5f52800`)

#### 2 신규 Screen (`tbm/`)

- **`TbmDashboardScreen.kt`** (160 lines, D-06):
  - 세션 없음 → `TbmStartSection` (시작 폼)
  - 세션 active 또는 ended → 세션 정보 Card + 체크리스트 LazyColumn (read-only ✓/○ + item_text) + 참여자 grid LazyColumn (user_id + signed_at + signature_url path) + "세션 종료" 버튼 (active 시만)
- **`TbmWorkerScreen.kt`** (180 lines, D-07):
  - 세션 정보 Card (work_type, leader, expected_end_at, ended_at?)
  - 체크리스트 LazyColumn (read-only)
  - 본인 미참여 + 세션 active → `SignatureCanvas` + "지우기" / "참여 확인" 버튼
  - 참여 확인 흐름: `signatureState.toPngBytes()` → `supabase.storage.from("tbm-signatures").upload(path, bytes)` (path = `{sessionId}/{userId}_{System.currentTimeMillis()}.png`, T-9-01 mitigation) → `callTbmCheckin(signatureUrl=path)` → 200/403/404/410 분기
  - 본인 참여 완료 → "✓ 참여 완료 {time}" + 버튼 disable
  - 세션 ended → "종료되어 더 이상 참여할 수 없습니다"

#### 2 신규 Activity (`smart_safety_management/`)

- **`TbmDashboardActivity.kt`** (60 lines):
  - **T-9-13 권한 가드**: `UserSession.userRole != UserRole.MANAGER` → Toast + finish() (advisor 정정: `UserRole.GENERAL_MANAGER` 존재 X, MANAGER 단일 분기).
  - `UserSession.groupId.toIntOrNull()` 가드 (그룹 미가입 시 finish + Toast).
  - `Smart_Safety_ManagementTheme { TbmDashboardScreen(leaderUserId, groupId, supabase) }` (Pitfall 12 Theme 래핑).
- **`TbmWorkerActivity.kt`** (50 lines):
  - userId + groupId 가드 (없으면 finish).
  - FCM extras `EXTRA_SESSION_ID` 받지만 hint 로만 사용 — `TbmWorkerScreen.sessionHintFromFcm` 으로 전달, 내부에서 DB 재조회 (T-9-12 mitigation).

#### Manifest + layouts

- **`AndroidManifest.xml`**: 2 activity 등록, `android:exported="false"` (외부 deep-link 차단, T-9-13).
- **`main_home.xml`**: `<ComposeView android:id="@+id/tbm_dashboard_compose">` 추가 — manager 홈의 첫 ComposeView (캘린더 위).
- **`main_home_worker.xml`**: `<ComposeView android:id="@+id/tbm_card_compose">` 추가 — `watch_card_compose` 아래 (Phase 7 카드 보존).

#### HomeActivity / HomeWorkerActivity integration

- **`HomeActivity.setupTbmDashboardCard()`** (Pitfall 12 첫 ComposeView Theme 래핑):
  ```kotlin
  composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this))
  composeView.setContent {
      Smart_Safety_ManagementTheme {
          TbmDashboardCardComposable(groupId, supabase, onClickDashboard = {
              startActivity(Intent(this, TbmDashboardActivity::class.java))
          })
      }
  }
  ```
- **`HomeWorkerActivity.setupTbmCard()`** (Phase 7 setupWatchCard 1:1 미러):
  ```kotlin
  TbmWorkerCardComposable(groupId, userId, supabase, onClickGuide = { sessionId ->
      startActivity(Intent(this, TbmWorkerActivity::class.java).apply {
          putExtra(TbmWorkerActivity.EXTRA_SESSION_ID, sessionId)
      })
  })
  ```

### Task 5: MyFirebaseMessagingService tbm_alert 분기 + 최종 검증 (커밋 `bfd0cec`)

#### `MyFirebaseMessagingService.kt` 분기 추가

기존 `watch_alert` 분기 옆에 `tbm_alert` 분기 1개 추가. `showTbmAlertNotification(title, body, sessionId, actionInApp)` 함수 신규.

**UserRole 분기**:
- `UserSession.userRole == UserRole.MANAGER` → `TbmDashboardActivity` (extras 부재)
- 그 외 (WORKER) → `TbmWorkerActivity` + `EXTRA_SESSION_ID` 전달

**Option B (Pitfall 3 회피)**: `channel_id = "fcm_default_channel"` 재사용 — 신규 채널 X, Android 코드 변경 0 원칙 (Phase 8 일관). v1.1 에서 `tbm_alerts` 채널 분리.

**알림 전이 (D-09)**: `notificationManager.notify(sessionId.toInt(), ...)` — 같은 세션 재push 시 갱신만 (Phase 7 alertId 패턴 미러).

**extras 신뢰 X (T-9-12)**: sessionId 는 hint 로만 사용. TbmWorkerActivity 진입 시 `TbmWorkerScreen` 의 `todaySessionFlow(groupId)` 가 DB 재조회로 실제 sessionId 결정.

#### 검증 (compileDebugKotlin + 48 tests + 회귀 가드)

```
$ ./gradlew app:compileDebugKotlin
BUILD SUCCESSFUL in 4s
17 actionable tasks: 2 executed, 15 up-to-date
```

Korean repo path workaround (`layout.buildDirectory.set('D:/ssm-app-build')`) 재사용 — Phase 7 03 패턴 그대로. compileDebugKotlin BUILD SUCCESSFUL, 신규 경고 0.

```
$ ./gradlew app:testDebugUnitTest
BUILD SUCCESSFUL in 6s
```

Test 결과 (`D:/ssm-app-build/test-results/testDebugUnitTest/TEST-*.xml`):

| Test Class                             | Tests | Failures | Errors |
| -------------------------------------- | ----- | -------- | ------ |
| tbm.WorkTypeValidatorTest              | 8     | 0        | 0      |
| tbm.ExpectedEndAtValidatorTest         | 5     | 0        | 0      |
| tbm.TbmParticipantsReducerTest         | 6     | 0        | 0      |
| tbm.SignatureStateTest                 | 2     | 0        | 0      |
| **tbm/ 소계**                          | **21**| **0**    | **0**  |
| watch.MacAddressValidatorTest          | 9     | 0        | 0      |
| watch.WatchAckIdempotencyTest          | 3     | 0        | 0      |
| watch.WatchRealtimeRepositoryTest      | 6     | 0        | 0      |
| watch.WearStateLabelTest               | 8     | 0        | 0      |
| **watch/ 소계**                        | **26**| **0**    | **0**  |
| ExampleUnitTest                        | 1     | 0        | 0      |
| **TOTAL**                              | **48**| **0**    | **0**  |

**회귀 가드** (Plan 09-03 의 5 commit 범위 = HEAD~5 → HEAD):

```bash
$ git diff HEAD~5 HEAD --stat -- app/src/main/java/com/example/smart_safety_management/watch/
(empty)
$ git diff HEAD~5 HEAD --stat -- app/src/main/java/com/example/smart_safety_management/DailyDetailActivity.kt
$ git diff HEAD~5 HEAD --stat -- app/src/main/java/com/example/smart_safety_management/DailyListActivity.kt
(empty)
$ git diff HEAD~5 HEAD --stat -- ai_agent/
(empty)
```

- ✓ `watch/` 패키지 변경 0 (Phase 7 보존, SC #4 코드 경로 분리)
- ✓ `Daily*.kt` 변경 0 (daily_safety_check 코드 경로 별도 보존)
- ✓ `ai_agent/` 변경 0 (Python 코드 0 변경, Phase 8 SC #4 일관)

**ai_agent 28/28 PASS** (regression):
```
$ cd ai_agent && python -m pytest -q
............................                                             [100%]
28 passed in 8.86s
```

## Pitfall 적용 evidence (grep counts)

| Pitfall | Pattern | File | Count |
|---------|---------|------|-------|
| 1 (Bitmap.recycle) | `androidBitmap.isRecycled` | `tbm/SignatureCanvas.kt` | 1 |
| 2 (Path setter 강제) | `state.currentPath = state.currentPath` | `tbm/SignatureCanvas.kt` | 2 |
| 8 (timezone ISO offset) | `ISO_OFFSET_DATE_TIME` | `tbm/ExpectedEndAtValidator.kt` | 4 |
| 12 (Theme 래핑) | `Smart_Safety_ManagementTheme` | `HomeActivity.kt` | 2 |

## T-9-* STRIDE Mitigation Evidence

| Threat | Disposition | Evidence |
|--------|-------------|----------|
| T-9-01 (Storage path Information Disclosure) | mitigate | `path = "${sessionId}/${userId}_${System.currentTimeMillis()}.png"` (TbmWorkerScreen line 165, 클라이언트 강제 key prefix, 013 RLS Option A 가드 충족) |
| T-9-06 (signed URL 60s expiry) | mitigate (deferred) | v1.0 한정 signatureUrl path 만 저장. signed URL 생성은 v1.1 (manager dashboard 모달 + AsyncImage cache key 30s) |
| T-9-12 (FCM extras spoofing) | mitigate | `TbmWorkerActivity.sessionHintFromFcm` 신뢰 X — `TbmWorkerScreen.todaySessionFlow(groupId)` 가 DB 재조회로 실제 sessionId 결정 (Phase 7 D-02 anti-pattern 회피) |
| T-9-13 (worker deep-link to manager dashboard) | mitigate | `TbmDashboardActivity.onCreate` 의 `UserSession.userRole != UserRole.MANAGER → Toast + finish()` + manifest `android:exported="false"` |
| T-9-14 (Compose Canvas Bitmap memory leak) | mitigate | `SignatureState.toPngBytes()` finally 블록의 `if (!androidBitmap.isRecycled) androidBitmap.recycle()` (Pitfall 1) |
| T-9-15 (Realtime SUBSCRIBED 가짜) | mitigate (by Plan 09-01) | 013 마이그가 supabase_realtime publication 에 4 테이블 ADD (Plan 09-01 의 must_have, 검증 완료). 본 plan 의 TbmRepository 는 그 publication 을 사용. |

## HomeActivity ComposeView 임베드 위치 결정

advisor 권고: **manager 홈의 가장 상단 (캘린더 위)** — 사용자 manager 홈 진입 시 TBM 상태를 첫 화면에서 즉시 시인. CONTEXT D-06 "planner UX 결정" 사항.

```xml
<LinearLayout ... android:orientation="vertical" ...>
    <!-- Phase 9 / 09-03 TBM-02 — manager TBM 대시보드 카드 (첫 ComposeView 임베드) -->
    <androidx.compose.ui.platform.ComposeView
        android:id="@+id/tbm_dashboard_compose"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="24dp"
        android:layout_marginBottom="16dp" />

    <!-- 캘린더 섹션 -->
    ...
</LinearLayout>
```

## Deviations from Plan

### Rule 3 (plan correction) — JUnit4 일관 채택

- **Found during:** Task 2 RED 단계 작성 직전 (advisor 정정)
- **Issue:** Plan 의 test 예제가 JUnit5 syntax (`org.junit.jupiter.api.Test` + `Assertions.*`) — `libs.versions.toml` 의 `junit = "4.13.2"` 및 기존 `watch/` test 26 cases (모두 `org.junit.Test` + `org.junit.Assert.*`) 와 불일치.
- **Fix:** 4 신규 test 파일을 JUnit4 syntax 로 작성. `org.junit.Test`, `org.junit.Assert.assertTrue/assertFalse/assertEquals/assertNotNull/assertNull` 사용.
- **Files affected:** 4 신규 test 파일 모두.
- **Commit:** `8df2ced`.
- **근거:** 만약 JUnit5 syntax 로 작성 시 `compileDebugUnitTestKotlin` 단계에서 `org.junit.jupiter` 클래스 미발견 → 컴파일 실패. dependency 추가 부담 회피 + watch/ test 패턴 일관.

### Rule 3 (plan correction) — UserRole.GENERAL_MANAGER 존재 X

- **Found during:** Task 4 TbmDashboardActivity 권한 가드 작성 (advisor 정정)
- **Issue:** Plan 의 `must_haves` line 55 에 `UserSession.userRole != UserRole.MANAGER && UserSession.userRole != UserRole.GENERAL_MANAGER` 코드 예제 — 실제 `UserSession.kt:62` 의 `userRole` 은 `MANAGER` 또는 `WORKER` 단일 분기 (UserRole enum 이 2 value only, `GENERAL_MANAGER` 미존재).
- **Fix:** `TbmDashboardActivity` 의 권한 가드를 `UserSession.userRole != UserRole.MANAGER → finish` 단일 분기로 작성.
- **Files affected:** `TbmDashboardActivity.kt`.
- **Commit:** `5f52800`.
- **근거:** `UserRole.kt` 의 실제 enum 값은 MANAGER + WORKER 만 — Rule 4 invent X, Rule 3 plan correction.

### Rule 3 (plan correction) — MyApp.kt install order + engine syntax 보존

- **Found during:** Task 1 (advisor 정정)
- **Issue:** Plan 의 must_haves line 42 코드 예제가 `install(Postgrest); install(Realtime); httpEngine = engine(CIO)` 이지만 기존 MyApp.kt 는 `install(Realtime); install(Postgrest); httpEngine = CIO.create()`.
- **Fix:** 기존 호출 스타일 그대로 보존 + `install(Storage)` 1줄 추가만. Postgrest/Realtime 순서 변경 X. `CIO.create()` 그대로 유지.
- **Files affected:** `MyApp.kt`.
- **Commit:** `c94ee1c`.
- **근거:** Phase 7 검증된 supabase-kt 초기화 호출이 정상 동작 — Plan 의 예제 syntax 가 ABI 차이를 가져올 가능성 (advisor 권고). 변경 1줄 원칙 일관.

### Rule 3 (plan correction) — JVM unit test 의 SignatureState.toPngBytes() early-return 추가

- **Found during:** Task 2 SignatureState 설계 단계 (advisor 권고)
- **Issue:** Plan 예제의 `SignatureStateTest` 가 `canvasSize=0 → ByteArray(0)` 를 검증 — but JVM unit 환경에서 `Bitmap.createBitmap()` 또는 `ImageBitmap` 접근 시 `NoClassDefFoundError`. Plan 의 must_haves 는 "PNG bytes 검증은 instrumented test 에서 skip" 이라고만 명시.
- **Fix:** `SignatureState.toPngBytes()` 함수 본문 첫 줄에 `if (canvasSize == IntSize.Zero || isEmpty) return ByteArray(0)` early-return guard 추가 — JVM unit test 환경에서 native Bitmap 미접근. `canvasSize > 0 + isEmpty == false` 일 때만 Bitmap.createBitmap() 도달 (instrumented test 또는 manual 시연 영역).
- **Files affected:** `tbm/SignatureCanvas.kt`.
- **Commit:** `8df2ced`.
- **근거:** advisor 사전 권고 — early-return 이 없으면 SignatureStateTest 의 `test_canvasSizeZero_yields_emptyBytes` 가 `NoClassDefFoundError: android/graphics/Bitmap` 으로 fail. early-return 으로 JVM 호환 + 실 환경 동작 둘 다 보장.

### Rule 3 (환경) — Java JBR PATH 명시

- **Found during:** Task 2 첫 gradle 실행 시 (`ERROR: JAVA_HOME is not set`)
- **Issue:** PowerShell 환경에서 `java` 가 PATH 에 없음. Android Studio JBR 만 설치됨.
- **Fix:** 모든 gradle 호출을 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" PATH="C:/Program Files/Android/Android Studio/jbr/bin:$PATH" ./gradlew ...` 로 prefix.
- **Files affected:** 없음 (runtime 환경 변수만).
- **Commit:** 없음 (no source change).
- **근거:** Korean repo path workaround (Phase 7 03) 와 별개. 향후 CI 셋업 시 동일 prefix 필요.

## Self-Check: PASSED

- ✓ All 19 created files exist:
  - 14 main files (12 `tbm/` + 2 root Activities)
  - 4 TDD test files
  - 1 SUMMARY.md (this file)
- ✓ All 9 modified files exist (build.gradle.kts, proguard-rules.pro, MyApp.kt, MyFirebaseMessagingService.kt, HomeActivity.kt, HomeWorkerActivity.kt, AndroidManifest.xml, main_home.xml, main_home_worker.xml)
- ✓ All 5 commits exist:
  - `c94ee1c` feat(09-03): storage-kt 2.2.0 + MyApp Storage plugin install
  - `8df2ced` feat(09-03): tbm/ Models + Validators + Reducer + SignatureCanvas + 4 TDD tests
  - `f4d3f3b` feat(09-03): TbmRepository + TbmRetrofitApi + 2 card Composables + TbmStartSection
  - `5f52800` feat(09-03): 2 신규 Activity + ComposeView 임베드 (manager 첫 + worker 추가)
  - `bfd0cec` feat(09-03): MyFirebaseMessagingService tbm_alert 분기 + showTbmAlertNotification
- ✓ All <done> criteria met for 5 tasks
- ✓ Pitfall 1·2·8·12 grep evidence collected
- ✓ T-9-01·06·12·13·14·15 mitigation 모두 evidence 포함
- ✓ 회귀 가드 통과 (watch/ + Daily*.kt + ai_agent/ 모두 git diff 0)
- ✓ 48 unit tests ALL PASS (tbm/ 21 + watch/ 26 + Example 1)
- ✓ ai_agent 28/28 PASS

## Threat Flags

(None — 본 plan 의 변경은 Plan 의 `<threat_model>` 의 6 threats 와 정합. 신규 trust boundary 또는 surface 도입 X.)
