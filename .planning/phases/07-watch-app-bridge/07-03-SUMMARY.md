---
phase: 07-watch-app-bridge
plan: 03
subsystem: android (UI — supabase-kt Realtime + ComposeView 임베드 + watch 패키지 + FCM 라우팅)
tags: [phase-7, watch, android, compose, realtime, supabase, fcm, ui, T-7-04, T-7-07, T-7-08]

# Dependency graph
requires:
  - phase: 07-watch-app-bridge
    provides: 07-01 (supabase-kt 2.2.0 + ktor-cio 2.3.9 + desugar 2.0.4 + BuildConfig.SUPABASE_URL/ANON_KEY + 011 RLS narrowing + supabase_realtime publication 4 테이블 ADD)
  - phase: 07-watch-app-bridge
    provides: 07-02 (Edge Function notifications case 'watch-ack' / 'watch-pair' 운영 배포 — payload 계약 + ownership SQL + idempotency)
  - phase: 04-watch-j2208a-pipeline
    provides: 010_watch_pipeline.sql 스키마 (device_watches snapshot + wear_state_events + safety_alerts.ack_at + devices.mac_address) + Phase 4 D-09 알림 전이 원칙 + Phase 4 D-11 FCM data payload 컨벤션
provides:
  - "MyApp.supabase = SupabaseClient by-lazy 싱글톤 (Realtime + Postgrest install + ktor-cio engine, anon key only)"
  - "watch/ 패키지 9 main 파일 (1217 lines total — 9 main + 4 test 합산) — Repository / 5 Composable / Models / Validator / Retrofit / SupabaseModule"
  - "HomeWorkerActivity.setupWatchCard() — main_home_worker.xml 의 ComposeView (@+id/watch_card_compose, profile_bar 와 섹션 1 사이) 에 WatchCardComposable 임베드. DisposeOnLifecycleDestroyed 로 Realtime channel lifecycle 보호."
  - "SafetyAlertsActivity (신규 ComponentActivity, AIEventActivity 와 별도) — paired device_id 조회 → SafetyAlertsScreen LazyColumn — 미해결 alert acknowledge 버튼 + Edge Function 'watch-ack' POST + 의료기기 면책 fine print"
  - "PairWatchSection (Compose) — DeviceManageScreen 하단 J2208A 워치 섹션 — MAC TextField + 정규식 검증 + Edge Function 'watch-pair' POST + WatchStatus 3-상태 badge (UNPAIRED/CONNECTED/DISCONNECTED)"
  - "MyFirebaseMessagingService — data.type='watch_alert' 분기 — pendingIntent → SafetyAlertsActivity (alert_id extras 신뢰 X — DB 재조회). watch_alerts 채널 + IMPORTANCE_HIGH"
  - "Wave 4 (07-04) E2E 시연 화면 본체 (HomeWorker 카드 / SafetyAlertsActivity / SettingDeviceManagement 워치 섹션) 모두 활성"
affects: [07-04-poc-e2e, phase-6-demo]

# Tech tracking
tech-stack:
  added:
    - "org.jetbrains.kotlin.plugin.serialization 1.9.22 (build.gradle.kts plugins block) — supabase-kt 2.2.0 의 @Serializable data class 컴파일러 플러그인"
  patterns:
    - "ComposeView 임베드 패턴 (D-02): XML/AppCompatActivity 그대로 유지 + 신규 코드만 Compose. setViewCompositionStrategy(DisposeOnLifecycleDestroyed(activity)) 로 Realtime channel.unsubscribe() 보장."
    - "by-lazy SupabaseClient 싱글톤 (MyApp.supabase): app 시작 시 zero-cost — 첫 사용 (Realtime/PostgREST) 시점에 WSS 연결. Realtime.Status.CONNECTED 가 SDK push 분기, 그 외 polling fallback (5초)."
    - "신호=상태신호 원칙 (PROJECT.md key decision): HR=0 또는 wear-state ∈ {WARMUP, OFF} 시 HR/temp 회색 + '—' 표기. WatchCardComposable line 75-80 — `snapshot?.heartRate?.takeIf { it > 0 && !isWarming }`"
    - "의료기기 면책 ('측정값' 단어 사용 0건 + '1차 경고용, 의료기기 아님' fine print 1회): WearStateLabel/WatchCardComposable/SafetyAlertsScreen 모두에 적용"
    - "SafetyAlertReducer pure object — Realtime PostgresAction → List<SafetyAlertRow> reducer 분리 → SupabaseClient mock 없이 단위 테스트 6 케이스 (Insert/Update/Delete/duplicate-id/unknown-id/empty-list)"
    - "FCM data payload routing (Phase 4 D-11 호환): MyFirebaseMessagingService.onMessageReceived 에서 data['type']='watch_alert' 분기 → SafetyAlertsActivity intent. extras 의 alert_id 는 신뢰 X (T-7-07 mitigation — DB 재조회 가 source of truth)."

key-files:
  created:
    - "app/src/main/java/com/example/smart_safety_management/watch/SupabaseModule.kt (15 lines)"
    - "app/src/main/java/com/example/smart_safety_management/watch/WatchModels.kt (85 lines, 8 @Serializable data classes)"
    - "app/src/main/java/com/example/smart_safety_management/watch/MacAddressValidator.kt (15 lines)"
    - "app/src/main/java/com/example/smart_safety_management/watch/WearStateLabel.kt (62 lines)"
    - "app/src/main/java/com/example/smart_safety_management/watch/WatchRealtimeRepository.kt (153 lines, 3 channel flow + SafetyAlertReducer)"
    - "app/src/main/java/com/example/smart_safety_management/watch/WatchAckRetrofitApi.kt (42 lines)"
    - "app/src/main/java/com/example/smart_safety_management/watch/WatchCardComposable.kt (137 lines)"
    - "app/src/main/java/com/example/smart_safety_management/watch/SafetyAlertsScreen.kt (136 lines)"
    - "app/src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt (228 lines)"
    - "app/src/main/java/com/example/smart_safety_management/SafetyAlertsActivity.kt (49 lines)"
    - "app/src/test/java/com/example/smart_safety_management/watch/MacAddressValidatorTest.kt (62 lines, 9 @Test)"
    - "app/src/test/java/com/example/smart_safety_management/watch/WearStateLabelTest.kt (75 lines, 8 @Test)"
    - "app/src/test/java/com/example/smart_safety_management/watch/WatchAckIdempotencyTest.kt (110 lines, 3 @Test, hand-rolled fake)"
    - "app/src/test/java/com/example/smart_safety_management/watch/WatchRealtimeRepositoryTest.kt (97 lines, 6 @Test, SafetyAlertReducer)"
    - ".planning/phases/07-watch-app-bridge/07-03-SUMMARY.md"
  modified:
    - "app/build.gradle.kts (+33 lines: kotlin.plugin.serialization 1.9.22 plugin + layout.buildDirectory.set('D:/ssm-app-build') Korean-path workaround)"
    - "app/src/main/java/com/example/smart_safety_management/MyApp.kt (+15 lines: SupabaseClient by-lazy + imports)"
    - "app/src/main/AndroidManifest.xml (+6 lines: SafetyAlertsActivity 등록)"
    - "app/src/main/res/layout/main_home_worker.xml (+8 lines: ComposeView watch_card_compose 임베드)"
    - "app/src/main/java/com/example/smart_safety_management/HomeWorkerActivity.kt (+71 lines: setupWatchCard() + FCM intent routing + 13 imports)"
    - "app/src/main/java/com/example/smart_safety_management/MyFirebaseMessagingService.kt (+58 lines: showWatchAlertNotification + data.type 분기)"
    - "app/src/main/java/com/example/smart_safety_management/DeviceManage.kt (+8 lines: PairWatchSection 통합 + LocalContext import)"

key-decisions:
  - "Realtime.Status enum 정정: 원안 의 'SUBSCRIBED' 는 supabase-kt 2.2.0 에 없음 — 실제 enum = DISCONNECTED/CONNECTING/CONNECTED. WatchCardComposable 가 CONNECTED 분기로 SDK push 사용, 그 외 polling fallback (D-01 의도 그대로 유지)."
  - "ComposeView 임베드 위치: profile_bar 와 섹션 1 (오늘 날짜 / rv_daily_check) 사이 — main_home_worker.xml 의 NestedScrollView > MaterialCardView > LinearLayout 첫 자식으로 추가. CONTEXT D-02 의 'profile_bar 아래' 권장 충족."
  - "FCM 라우팅 위치 = MyFirebaseMessagingService (NOT MainActivity): 기존 FCM service 가 push 수신 진입점이고 MainActivity 는 FCM 미사용. data.type='watch_alert' 분기는 service 안에 추가 — 기존 일반 알림 흐름 (notification.title/body) 영향 0."
  - "PairWatchSection 위치 = DeviceManage.kt DeviceManageScreen 하단 (배터리 현황 아래, Divider 로 구분): 기존 BatteryItem 흐름 영향 0. SettingDeviceManagementActivity 는 변경 X (DeviceManage.kt 의 Composable 호출만)."
  - "alertTitle/alertTitleFor 함수 분리: WatchCardComposable 와 SafetyAlertsScreen 모두에 동일 매핑 (TACHY→'빈맥 의심', REMOVED→'워치 미착용', COMMS_LOST→'통신 두절') — 향후 d.r.y. refactor 가능하나 현 phase 는 inline 유지 (scope creep 방지)."
  - "환경적 deviation [Rule 3 - environmental block]: Korean repo path (D:\\\\2026_산업안전\\\\...) 가 forked unit-test JVM 의 sun.jnu.encoding=CP949 와 충돌 → ClassNotFoundException. -D 플래그로는 fix 불가능 (JEP 400 한계 — argv 파싱이 -D 적용 전). Workaround: layout.buildDirectory.set(file('D:/ssm-app-build')) — 같은 D: 드라이브 ASCII path 로 buildDir 만 redirect. 같은 드라이브 유지 = Kotlin 컴파일러 relativize() OK. ExampleUnitTest + 4 watch tests 모두 PASS."

requirements-completed: [BRIDGE-01, BRIDGE-02, BRIDGE-03]

# Metrics
duration: 2h15min
completed: 2026-05-14
tasks_completed: 3
tasks_total: 3
files_created: 14
files_modified: 7
total_lines_kotlin: 1217  # main + test
test_count: 26  # MacAddressValidator 9 + WearState 8 + Ack 3 + Reducer 6
test_passed: 26
test_failed: 0
unit_test_runtime_ms: 116  # 0.013 + 0.084 + 0.005 + 0.014 = 0.116s sum
threats_mitigated: [T-7-07, T-7-08]
threats_accepted: [T-7-04]
---

# Phase 7 Plan 03: Android 워치 UI Summary

ComposeView 임베드 패턴 + supabase-kt 2.2.0 Realtime 3채널 구독 + Edge Function watch-ack/watch-pair Retrofit 호출 + FCM watch_alert 라우팅 — BRIDGE-01·02·03 SC 충족.

## Tasks Completed

### Task 1 (commit `c20d0dd`) — SupabaseClient 싱글톤 + watch/ 패키지 + 4 unit test

- **MyApp.kt**: `val supabase: SupabaseClient by lazy { createSupabaseClient(...) { install(Realtime); install(Postgrest); httpEngine = CIO.create() } }`
- **watch/ 패키지 6 신규 main 파일**: SupabaseModule, WatchModels (8 @Serializable data classes), MacAddressValidator (정규식 `^([0-9A-F]{2}:){5}[0-9A-F]{2}$`), WearStateLabel (5 상태 매핑 + 의료기기 면책), WatchRealtimeRepository (3 채널 postgresChangeFlow + SafetyAlertReducer), WatchAckRetrofitApi (Retrofit interface)
- **4 unit tests 26 cases — ALL PASS**:
  - MacAddressValidatorTest: 9 cases (valid uppercase/lowercase/spaces, invalid length/separator/hex, normalize, empty)
  - WearStateLabelTest: 8 cases (WORN/OFF/ABNORMAL/WARMUP/TRANSIENT/null/unknown + 의료기기 면책 grep)
  - WatchAckIdempotencyTest: 3 cases (firstAck=확인됨, secondAck=이미 확인됨 no exception, 5xx=오류)
  - WatchRealtimeRepositoryTest: 6 cases (Insert prepend, Insert duplicate replace, Update by id, Update unknown noop, Delete remove, empty Insert)
- **Test gate** (BLOCKING): `./gradlew app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.*"` → BUILD SUCCESSFUL — 26 tests, 0 failures, 0.116s sum

### Task 2 (commit `ebcd623`) — HomeWorker ComposeView + SafetyAlertsActivity + FCM 라우팅

- **main_home_worker.xml**: `<androidx.compose.ui.platform.ComposeView android:id="@+id/watch_card_compose" .../>` 추가 — profile_bar 와 섹션 1 (오늘 날짜) 사이에 위치
- **HomeWorkerActivity.setupWatchCard()**: ViewCompositionStrategy.DisposeOnLifecycleDestroyed + paired device_id 조회 → WatchCardComposable / EmptyWatchPrompt 분기
- **HomeWorkerActivity FCM 라우팅**: intent.getStringExtra("alert_type")=="watch_alert" 시 SafetyAlertsActivity 직행
- **WatchCardComposable**: HR/temp 한 줄 + WearStateLabel + last alert. Realtime.Status.CONNECTED 시 3 채널 collectLatest, 그 외 5초 polling. HR=0 or wear-state WARMUP/OFF 시 회색 "—" (의료기기 면책)
- **SafetyAlertsScreen**: LazyColumn — 미해결 alert (resolved_at IS NULL AND ack_at IS NULL) 만 acknowledge 버튼. POST `/functions/v1/notifications` watch-ack — 200 → "확인됨", 404 → "이미 확인됨" (idempotent), 5xx → "오류"
- **SafetyAlertsActivity**: ComponentActivity + paired device_id 조회 → SafetyAlertsScreen 호출. alert_id extras 만 받음 (신뢰 X)
- **AndroidManifest**: `<activity android:name=".SafetyAlertsActivity" .../>` 추가
- **MyFirebaseMessagingService**: showWatchAlertNotification — watch_alerts 채널 (IMPORTANCE_HIGH) + pendingIntent → SafetyAlertsActivity. notification id = alert_id (D-09 알림 전이 — 같은 alert 재push 시 갱신만)
- **Compile gate** (BLOCKING): `./gradlew app:compileDebugKotlin` PASS

### Task 3 (commit `d3d3baf`) — PairWatchSection + DeviceManage 통합

- **PairWatchSection**: WatchStatus enum 3 상태 — UNPAIRED (회색 "미등록") / CONNECTED (초록 "연결됨", last_comm < 5분) / DISCONNECTED (노랑 "끊김", last_comm >= 5분 또는 NULL)
- **MAC TextField + 등록 버튼**: MacAddressValidator.normalize + isValid → callWatchPair op="pair"
- **해제 버튼** (paired 상태에서만): callWatchPair op="unpair"
- **Realtime devices 채널 구독**: `supabase.channel("devices_user:$userId").postgresChangeFlow<PostgresAction.Update>` filter user_id=eq.{userId} → status badge 자동 갱신
- **DeviceManage.kt**: DeviceManageScreen 끝 (배터리 현황 아래, Divider 로 구분) 에 PairWatchSection 통합. LocalContext import 추가. 기존 BatteryItem 흐름 영향 0.
- **Compile gate** (BLOCKING): `./gradlew app:compileDebugKotlin` PASS + 4 unit tests still PASS (regression 0)

## ComposeView 임베드 anchor

`main_home_worker.xml` 구조:
```
NestedScrollView (home_scroll, paddingTop=135dp)
└─ FrameLayout
   └─ MaterialCardView (orange 배경)
      └─ LinearLayout (orientation=vertical, paddingTop=24dp)
         ├─ ⭐ ComposeView watch_card_compose (NEW — marginHorizontal=24dp marginBottom=16dp)
         ├─ 섹션 1 — tv_worker_day + list_header + rv_daily_check
         ├─ Divider
         ├─ 섹션 2 — 조치요청 + rv_worker_event
         ├─ Divider
         └─ 섹션 3 — emergency_contact
```

profile_bar 는 CoordinatorLayout level (NestedScrollView 와 별도 absolute layer), 그래서 NestedScrollView 의 첫 시각 자식 = 워치 카드 = "profile_bar 아래 섹션 1 위" 의 의도 충족.

## Acceptance Criteria 검증

| Criterion | 상태 | 증거 |
|-----------|------|------|
| watch/ 패키지 9 main 파일 모두 존재 | PASS | `ls app/src/main/.../watch/` = 9 files (Plan 의 8 file 보다 +1 — Plan 의 "+1 future Composable" 자리에 SafetyAlertsScreen 채움) |
| MyApp.kt SupabaseClient 싱글톤 + onCreate 활성화 | PASS | `grep -c 'createSupabaseClient' MyApp.kt` = 2 (import + 호출) + `install(Realtime)` 1 + `install(Postgrest)` 1 + `CIO.create()` 1 |
| HomeWorkerActivity setupWatchCard + main_home_worker.xml ComposeView 임베드 | PASS | `grep -c 'setupWatchCard'` = 2 (선언 + 호출) + `grep -c 'DisposeOnLifecycleDestroyed'` = 2 (import + use) + xml `grep -c 'watch_card_compose'` = 1 |
| SafetyAlertsActivity 신규 + AndroidManifest 등록 | PASS | 신규 파일 + `grep -c 'SafetyAlertsActivity' AndroidManifest.xml` = 1 |
| PairWatchSection 가 DeviceManageScreen 에 통합 | PASS | `grep -c 'PairWatchSection' DeviceManage.kt` = 1 |
| 4 unit test PASS | PASS | 26/26 cases, 0.116s sum (test xml `failures="0"` × 4) |
| compileDebugKotlin 빌드 성공 | PASS | BUILD SUCCESSFUL (3회 — Task 1·2·3 each gate) |
| '측정값' 단어 사용 0건 (watch/ 전체) | PASS | `grep -rE '측정값' watch/` = 0 |
| 'acknowledged_at' 사용 0건 (watch/ 전체) | PASS | `grep -rE 'acknowledged_at' watch/` = 0 |
| '의료기기 아님' fine print 1회 | PASS | SafetyAlertsScreen 의 `Text("1차 경고용, 의료기기 아님" ...)` 한 줄 |

## 회귀 가드 결과

```
grep -rE 'acknowledged_at' app/src/main/.../watch/      # 0 (Pitfall 5 회귀 가드)
grep -rE '측정값' app/src/main/.../watch/                # 0 (의료기기 면책)
grep -rE 'realtime-kt:3\.' app/build.gradle.kts          # 0 (07-01 의 supabase-kt 2.2.0 lock 보존)
```

## Wave 4 (07-04) 시연 가능 화면 목록

1. **HomeWorkerActivity** — 워치 카드 (HR/temp/wear-state/last-alert) + 정상 운용 중 메시지 + 탭 → SafetyAlertsActivity
2. **SafetyAlertsActivity** — 워치 알림 LazyColumn + acknowledge 버튼 + 의료기기 면책 fine print
3. **SettingDeviceManagementActivity** — DeviceManageScreen 하단 J2208A 워치 섹션 (MAC 등록 + status badge 3 색상)
4. **FCM watch_alert push** — 채널 watch_alerts (IMPORTANCE_HIGH) → 알림 클릭 → SafetyAlertsActivity (alert_id extras + DB 재조회)

## Threats

### T-7-04 (Realtime channel hijacking) — accepted v1.0
원안 그대로 유지. RLS USING(true) v1.0 한계 (07-01 mitigate). 본 phase 의 클라이언트는 본인 user_id 의 device_id 만 query (HomeWorkerActivity setupWatchCard / SafetyAlertsActivity onCreate / PairWatchSection LaunchedEffect 모두 `eq("user_id", UserSession.userId)`). 적극 공격자는 디버거로 다른 device_id 보낼 수 있으나 RLS 가 0행 push. v1.1 Supabase Auth 도입 시 강화.

### T-7-07 (FCM payload 위조) — mitigated
MyFirebaseMessagingService 의 showWatchAlertNotification 이 alert_id extras 로 SafetyAlertsActivity 진입 → SafetyAlertsScreen 의 LaunchedEffect 가 DB 재조회 (Realtime safetyAlertsFlow 50건 fetch + push). extras 의 alert_type 은 highlight 용으로만 사용 (현재 미구현, v1.1 LazyColumn scrollToItem). 가짜 alert_id 보내도 DB 에 없으면 화면에 노출 안 됨.

### T-7-08 (anon key 노출) — accepted
RLS 가 보안 경계. anon key 가 git/decompile 노출 = 정상 (Supabase 설계). 본 phase 의 BuildConfig 에 anon 임베드 = git 커밋 OK. service_role key 는 Edge Function 의 Deno.env 에만 (07-02 책임).

## Deviations from Plan

### Auto-fixed Issues (Rule 3 - environmental block)

**1. [Rule 3 - environmental block] Korean repo path 가 forked unit-test JVM 의 ClassNotFoundException 야기**

- **Found during:** Task 1 BLOCKING test gate (compile PASS 후 testDebugUnitTest 4 class 모두 ClassNotFoundException at initializationError)
- **Root cause:** Repo path `D:\2026_산업안전\Smart_Safety_Management` 의 한글이 forked test worker JVM 의 sun.jnu.encoding (Windows ACP=CP949) 으로 디코딩 안 되어 URLClassLoader 가 classpath dir 의 한글 segment 를 잘못 디코딩 → AppClassLoader.loadClass 실패. JEP 400 한계 — `-Dfile.encoding=UTF-8` 과 `-Dsun.jnu.encoding=UTF-8` 모두 argv 파싱 (sun.jnu.encoding 초기화) 후 적용되므로 fix 불가능. JAVA_TOOL_OPTIONS 도 동일 (JVM startup 전 set 되어도 native-encoding 결정은 OS ACP).
- **Discriminator:** ExampleUnitTest (워치 테스트와 무관한 pre-existing 테스트) 도 동일 ClassNotFoundException → 환경적 issue 확정 (워치 패키지 specific X).
- **Fix:** `app/build.gradle.kts` 끝에 `layout.buildDirectory.set(file("D:/ssm-app-build"))` 추가. 같은 D: 드라이브 ASCII path 로 buildDir redirect — Kotlin compiler 의 relativize() 가 cross-drive 실패 안 함. argfile classpath 의 모든 entry 가 ASCII 만 포함 → JVM argv 디코딩 문제 우회.
- **검증 사이클:** 1) `-Dsun.jnu.encoding=UTF-8` in gradle.jvmargs (FAIL — daemon 에만 적용) → 2) `testOptions.unitTests.all.jvmArgs` (FAIL — argv 파싱 후) → 3) `JAVA_TOOL_OPTIONS=...` env (FAIL — 위와 동일) → 4) `layout.buildDirectory.set(file("C:/temp/..."))` (FAIL — Kotlin cross-drive relativize) → 5) `D:/ssm-app-build` (PASS).
- **Files modified:** app/build.gradle.kts (+11 lines comment + 1 line setter)
- **Commit:** c20d0dd (Task 1 단일 commit 안에 포함)
- **향후 영향:** CI/dev 셋업 시 동일 워크어라운드 필요. v1.1 에서 repo 를 ASCII path 로 이동 검토. Reverted dead-end attempts (gradle.properties 의 sun.jnu.encoding + testOptions jvmArgs) 는 commit 전 정리 — gradle.properties 는 원상 복귀.

### Plan-driven adjustments

**2. [Plan correction] Realtime.Status enum 값 정정 — SUBSCRIBED → CONNECTED**

- **Found during:** Task 2 첫 compile (3 errors — Cannot find parameter 'initial', Unresolved reference 'UNSUBSCRIBED'/'SUBSCRIBED')
- **Root cause:** Plan 의 가상 코드는 SUBSCRIBED/UNSUBSCRIBED 사용 — supabase-kt 2.2.0 의 실제 `Realtime.Status` enum 은 `DISCONNECTED / CONNECTING / CONNECTED` (javap 확인). 또한 `realtime.status` 는 `StateFlow<Status>` 라 `collectAsState()` (initial 인자 없이) 호출.
- **Fix:** WatchCardComposable.kt 의 `.collectAsState(initial = Realtime.Status.UNSUBSCRIBED)` → `.collectAsState()` + `realtimeStatus == Realtime.Status.SUBSCRIBED` → `realtimeStatus == Realtime.Status.CONNECTED`
- **D-01 의도 보존:** "Realtime SUBSCRIBED 일 때 SDK push, 그 외 polling" → "CONNECTED 일 때 SDK push, 그 외 polling". subscribe() 가 trigger 하는 connect() 가 CONNECTED 도달 시 SDK push 활성화.
- **Files modified:** WatchCardComposable.kt (3 line edit)
- **Commit:** ebcd623 (Task 2 commit 안에 포함)

### Required field corrections

**3. [Plan grep gate compliance] 코멘트의 '측정값' 단어 제거**

- **Found during:** Task 2 grep gate verification (`grep -c '측정값' WatchCardComposable.kt` = 1 — but acceptance == 0)
- **Root cause:** WatchCardComposable.kt KDoc 에 `'측정값' 단어 사용 X` 라는 메타-코멘트. 의료기기 면책 의도는 정확하지만 grep 정규식이 단순 substring count → fail.
- **Fix:** `'측정값' 단어 사용 X` → `의료기기 측정 표현 사용 금지 (PROJECT.md key decision)`. 의도 동일, grep PASS.
- **Files modified:** WatchCardComposable.kt (1 line comment)
- **Commit:** ebcd623

## Self-Check: PASSED

**Files created/modified verified on disk:**
- ✅ All 14 created files present (9 watch source + 4 watch test + 1 SafetyAlertsActivity)
- ✅ All 7 modified files present (build.gradle.kts, MyApp.kt, AndroidManifest.xml, main_home_worker.xml, HomeWorkerActivity.kt, MyFirebaseMessagingService.kt, DeviceManage.kt)

**Commits verified in git log:**
- ✅ c20d0dd `feat(07-03): MyApp Supabase singleton + watch package + 4 unit tests (Task 1)`
- ✅ ebcd623 `feat(07-03): HomeWorker ComposeView card + SafetyAlertsActivity + FCM intent routing (Task 2)`
- ✅ d3d3baf `feat(07-03): PairWatchSection + DeviceManage J2208A 워치 섹션 (Task 3)`

**Test gate verified:**
- ✅ 26 unit tests PASS (MacAddressValidator 9 + WearState 8 + Ack 3 + Reducer 6)
- ✅ compileDebugKotlin BUILD SUCCESSFUL (final, after Task 3)

**Regression grep gates verified:**
- ✅ `acknowledged_at` count = 0 in watch/
- ✅ `측정값` count = 0 in watch/
- ✅ `realtime-kt:3.` count = 0 in build.gradle.kts
