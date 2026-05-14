# Phase 7: 워치-앱 양방향 연동 - Research

**Researched:** 2026-05-14
**Domain:** Android (Kotlin/Compose mixed) ↔ Supabase Realtime + PostgREST + Edge Function (existing)
**Confidence:** HIGH (모든 핵심 결정에 1차 출처 검증 완료)
**Deadline:** 2026-05-20 (수요일, D-6)

## Summary

본 phase 의 핵심 기술 리스크는 **3개의 가정**에서 발생한다 — (a) supabase-kt SDK 버전이 현 Kotlin/Compose 컴파일러와 호환되는가, (b) `auth.uid()` 없이 anon key 만으로 Realtime 구독이 동작하는가, (c) HomeWorkerActivity 가 Compose 인가 (실제로는 XML/View). 본 research 가 1차 출처(Maven Central GAV metadata, supabase-kt 2.2.0 sources jar, Android Compose-Kotlin compatibility map)로 3개를 모두 해소했다.

**핵심 발견:** supabase-kt **3.6.0 (latest)** 은 `kotlin-stdlib:2.3.21` 을 강제하므로 본 프로젝트의 Kotlin 1.9.22 (Compose Compiler 1.5.10) 와 ABI 호환되지 않는다. **2.2.0** 이 정확한 stdlib 매칭(1.9.22) + Ktor 2.3.9 로 Kotlin/Compose 업그레이드 0건. CONTEXT.md D-01 의 "3.0.0" 버전 표기는 자동 채택할 수 없고, 본 phase 에서는 **2.2.0 으로 다운그레이드 + state of the art 노트** 가 정답.

**Primary recommendation:**
1. supabase-kt 2.2.0 (realtime-kt + postgrest-kt) + Ktor 2.3.9 (cio engine) — Kotlin/Compose 변경 없음
2. HomeWorkerActivity 의 워치 카드는 `ComposeView` 임베드 (XML 레이아웃 유지 + Compose 컴포넌트 재사용)
3. Realtime 구독은 anon key + 명시적 `device_id=eq.{N}` 필터 (RLS 는 `device_watches`/`safety_alerts`/`wear_state_events` 의 SELECT 정책을 worker 본인 device 로 제한, 단 v1.0 PoC 는 testuser1 단일 사용자라 기존 `USING (true)` 정책도 보안적 손실 거의 없음 — 강화 권장)
4. **D-04 페어링 UPDATE 는 D-03 패턴 따라 Edge Function 경유 권장** (anon UPDATE 는 RLS 우회 위험, 본 phase 는 Supabase auth 미도입 상태)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01: Supabase-kt Realtime SDK 도입** — `app/build.gradle.kts` 에 신규 의존성 추가, 3개 채널 구독 (`wear_state_events`, `safety_alerts`, `device_watches`), `raw_events` 구독 X (5–6Hz 트래픽 과다), HomeWorkerActivity onStart/onStop lifecycle 바인딩, anon key + RLS 인증, SDK 연결 실패 시 5초 polling 폴백.
- **D-02: HomeWorkerActivity 카드 1개 (가벼운 통합)** — 카드 필드 = HR + temp 한 줄 / wear-state 라벨 / 마지막 알림 1건 제목. 데이터 소스 = D-01 의 3채널. 탭 → 신규 SafetyAlertsActivity. 빈 상태 = "워치 등록 필요" 프롬프트. AIEventActivity 는 비전 5종 알림 전용으로 유지.
- **D-03: 기존 `notifications/index.ts` 에 case 'watch-ack' 추가** — Phase 4 03 에서 watch-alert 추가한 동일 파일. Payload `{action, alert_id, user_id}`. Edge Function 내부에서 `now()` 로 ack_at 세팅. SQL = `UPDATE safety_alerts SET ack_at = now() WHERE alert_id = $1 AND device_id IN (SELECT device_id FROM devices WHERE user_id = $2)`. Retrofit POST 호출. ack 자체는 새 알림 발생 X (D-09 Phase 4).
- **D-04: SettingDeviceManagementActivity 에 'J2208A 워치' 섹션 추가** — MAC 입력 TextField + "등록" 버튼 + status badge (paired/connected/disconnected). MAC 정규식 `^([0-9A-F]{2}:){5}[0-9A-F]{2}$`. 1인=1워치. **Edge Function 신규 X — supabase-kt PostgREST 직접 UPDATE** + 신규 RLS 마이그레이션. (⚠ 본 research 의 Open Questions OQ-1 참조 — Edge Function 경유 권장으로 amendment 필요할 수 있음)
- **D-05: Phase 4 04-04 단축 PoC 먼저** — testuser1 가 J2208A 워치 2시간 PoC 1회 실행 (30분 안정 → 5분 탈착 → 60분 추가). 산출물 = minute_summary ≈ 120 행, wear_state_events ≥ 3, safety_alerts ≥ 2, raw_events ≈ 40k. 본 phase 의 데이터 소스.

### Claude's Discretion

- Realtime SDK **정확한 버전** — planner 가 jan-tennert/supabase-kt latest stable 확인 후 결정 → **본 research = 2.2.0 권장 (Kotlin 1.9.22 호환)**. CONTEXT.md "3.0.0" 표기는 ABI 비호환 (아래 State of the Art 참조).
- Realtime channel name 컨벤션 — SDK default 사용
- 카드 색상 테마 — 기존 Material3 ColorScheme 활용
- acknowledge 버튼 위치 — SafetyAlertsActivity 카드 우측 하단, 미해결 alert 만 노출
- MAC 입력 UX — 직접 입력 default + "BLE 스캔" 버튼 placeholder
- status badge 색상 — connected=초록 / disconnected=노랑 / unpaired=회색
- Realtime 구독 실패 시 재시도 — exponential backoff (1s→2s→4s→8s, 최대 30s)
- Retrofit OkHttp + supabase-kt ktor-okhttp 공존 — **본 research = Ktor cio engine 권장** (OkHttp 충돌 회피, 별도 client 인스턴스)

### Deferred Ideas (OUT OF SCOPE)

- 워치 대시보드 §9 3층 풀 화면 (1시간 line chart + 이벤트 타임라인) → Phase 6 DEMO-03
- BLE write 양방향 (시계 진동 cmd_vibrate, 시계 측 acknowledge) → v1.1+
- 다중 작업자 매핑 (1 manager → N workers' watches 동시 모니터) → v1.1
- QR / NFC 페어링 → v1.1 결정 phase
- Manager 화면 모든 작업자 워치 grid → v1.1 또는 Phase 6 manager 변형
- 워치 차트 sparkline → Phase 6 DEMO-03
- 알림 음성 / TTS 안내 → Next-5 별도 마일스톤
- SMS / 카카오톡 알림 채널 → Next-7 별도 마일스톤
- 수동 mock 시드 스크립트 — 본 phase 는 단축 PoC 데이터 사용 default, mock 은 fallback
- 개인정보 정책, 충전 운용 절차, 펌웨어 동결 정책 → J2208A §11 v1.1
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BRIDGE-01 | 워치 데이터 (HR/temp/wear-state/safety_alerts) → Android 앱 실시간 표시 (≤3초 지연), 카드 + 1시간 차트 + 알림 타임라인 | supabase-kt 2.2.0 `Realtime#channel` + `postgresChangeFlow<PostgresAction.Update>` + filter (`device_id=eq.{N}`). Ktor WebSocket 기반, 평균 latency < 1초 (네트워크 정상 시). 1시간 차트는 본 phase out of scope (Phase 6 DEMO-03). |
| BRIDGE-02 | 앱 측 알림 acknowledge → Edge Function `notifications`(action='watch-ack') → `safety_alerts.ack_at` 갱신 ≤5초 사이클 | 기존 `notifications/index.ts` action-routing 패턴 재사용 (Phase 4 03 에서 watch-alert 추가됨). 본 phase 는 case 'watch-ack' 1개만 추가. ack_at 갱신은 Realtime SUBSCRIBE 로 카드에 즉시 반영. |
| BRIDGE-03 | 페어링 화면 + status (connected/disconnected/paired) + `devices.user_id` + `mac_address` 매핑 | `devices` 테이블은 Phase 4 010 마이그레이션에서 mac_address/last_comm_at 추가됨. UPDATE 경로 = (a) Edge Function 신규 case 'pair-watch' (권장, OQ-1 참조) 또는 (b) supabase-kt PostgREST 직접 UPDATE + 신규 RLS (CONTEXT.md D-04 원안). |

> **SC #4 (deadline)**: 2026-05-20 수요일 — 본 research 의 Day 별 task allocation 은 CONTEXT.md `<specifics>` 의 6일 plan 그대로 권장.
</phase_requirements>

## Project Constraints (from CLAUDE.md)

`./CLAUDE.md` 파일 부재 (확인됨). 프로젝트 컨벤션은 기존 코드베이스 + `.planning/` 의 누적 결정으로 추출:

- **마이그레이션 번호** — 010 까지 사용, 본 phase 신규 = `011_watch_app_rls.sql`
- **Edge Function action-routing** — 단일 함수 + switch (action) 패턴 (Phase 4 03 정착)
- **service_role 분리** — Edge Function 만 service_role, 클라이언트는 anon + (선택) user JWT
- **commit prefix** — `phase($num): ...` 또는 `docs($num): ...` (git log 패턴)
- **의료기기 면책 1줄** — "1차 경고용, 의료기기 아님" SafetyAlertsActivity 하단 fine print 필수 (PROJECT.md key decision)
- **알림 전이 원칙** — 정상↔주의↔경보 전이 시점에만 1회 (PROJECT.md key decision, 비전·워치 일관 적용)
- **신호 = 상태 신호 원칙** — HR=0 은 결측 X = "PPG 락온 전" 상태 신호. 카드의 HR 숫자 옆 wear-state 라벨이 WARMUP/OFF 일 때 회색 처리 또는 "—" 표기.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 실시간 푸시 (HR/temp/wear/alert) | Supabase Realtime (Postgres WAL → WSS) | supabase-kt SDK on Android | Postgres logical replication 이 단일 진실 소스, SDK 는 WSS subscriber 일 뿐 — UI 는 push 받기만 |
| 카드 표시 (HR/temp/wear/last alert) | Android UI (Compose, ComposeView 임베드) | Application 레벨 SupabaseClient 싱글톤 | Compose state 가 Realtime Flow 에 collect, 화면 lifecycle 와 결합 |
| 알림 acknowledge | Supabase Edge Function `notifications` | Android Retrofit POST | 이미 service_role 로 권한 검증 + ack_at 갱신, 클라이언트는 단순 트리거 |
| 페어링 UPDATE | (권장) Edge Function `notifications` case 'pair-watch' | (대안) supabase-kt PostgREST 직접 UPDATE | Edge Function 경유 시 ownership 검증 + service_role 로 RLS 우회 안전. 직접 UPDATE 시 RLS 강제 + auth.uid() 의존 |
| 페어링 상태 표시 (paired/connected/disconnected) | Android UI (Compose) | Supabase PostgREST GET (or Realtime device_watches) | 1초 단위 라이브 X, 5초 polling 또는 Realtime 충분 |
| FCM 푸시 → 앱 진입 | Android FCM service (기존) | NotificationCompat pendingIntent → HomeWorkerActivity | 기존 FCM 흐름 재사용 (Phase 4 03 ANDROID_CHANNEL_ID="watch_alerts"), data payload 의 alert_type=watch_alert 분기 |

## Standard Stack

### Core (신규 추가)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.github.jan-tennert.supabase:realtime-kt` | **2.2.0** | Postgres changes 실시간 구독 (WSS) | Kotlin Multiplatform 공식 Supabase 클라이언트, jan-tennert 가 supabase-community 메인테이너. 2.2.0 = 프로젝트 Kotlin 1.9.22 와 정확 매칭 [VERIFIED: Maven Central GAV metadata `realtime-kt-2.2.0.module` `kotlin-stdlib.requires=1.9.22`] |
| `io.github.jan-tennert.supabase:postgrest-kt` | **2.2.0** | PostgREST direct query (페어링 UPDATE 대안 경로 + 마지막 알림 1회 fetch) | 같은 BOM/버전. select/update/insert builder DSL [VERIFIED: 2.2.0 sources jar `Postgrest.kt`] |
| `io.ktor:ktor-client-cio` | **2.3.9** | Ktor HTTP/WSS engine (supabase-kt 의존) | CIO = pure Kotlin, OkHttp 충돌 없음. supabase-kt 2.2.0 이 Ktor 2.3.9 명시 의존 [VERIFIED: Maven Central GAV metadata `realtime-kt-2.2.0.module` `ktor-client-websockets.requires=2.3.9`] |

### Supporting (기존 자산 재사용)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Retrofit 2.9.0 + OkHttp 4.12.0 | 기존 | 기존 REST API + Edge Function 호출 (watch-ack POST) | 본 phase 의 ack 트리거. 기존 `RetrofitClient` 패턴 그대로 [VERIFIED: app/build.gradle.kts:110-112] |
| Material3 + Navigation Compose 2.8.5 | 기존 | SafetyAlertsActivity 의 화면 구성 | AIEventActivity 와 동일 패턴 [VERIFIED: app/build.gradle.kts:77,79] |
| Firebase FCM (`firebase-messaging-ktx`) | 기존 (BoM) | 워치 알림 FCM 수신 | 본 phase 변경 X — Phase 4 03 의 watch_alerts 채널 그대로 재사용 [VERIFIED: app/build.gradle.kts:90] |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 기존 | DisposableEffect / lifecycleScope 활용 | 기존 import [VERIFIED: app/build.gradle.kts:66] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| **supabase-kt 2.2.0** | supabase-kt 3.6.0 (latest) | Kotlin 2.3.21 stdlib 강제 → 프로젝트 Kotlin 1.9.22 + Compose Compiler 1.5.10 ABI 비호환. Compose Compiler Gradle plugin 마이그레이션 + Kotlin 2.x bump = D-6 deadline 외 별도 phase. **3.6.0 거부** [VERIFIED: realtime-kt-3.6.0.module `kotlin-stdlib.requires=2.3.21`] |
| **Ktor cio engine** | Ktor okhttp engine (CONTEXT.md `<code_context>` 함정 기재) | OkHttp engine 사용 시 Ktor 2.3.9 가 기존 OkHttp 4.12.0 과 클래스로더 공존 — Retrofit 의 OkHttp 와 동일 instance pool 공유 어려움. cio = pure Kotlin coroutine, deps 격리. WSS 지원 ✓ [VERIFIED: ktor.io/docs/http-client-engines.html] |
| **Direct PostgREST UPDATE for pairing (D-04)** | Edge Function `notifications` case 'pair-watch' | Direct UPDATE 는 anon key + RLS 의존 = `auth.uid()` 없는 본 프로젝트 (Firebase Auth + UserSession.userId) 에서 **RLS bypass 불가능**. Edge Function 경유는 D-03 패턴 그대로, ownership 검증 가능. **OQ-1 amendment 권장** |

**Installation:**

```kotlin
// app/build.gradle.kts dependencies block
implementation("io.github.jan-tennert.supabase:realtime-kt:2.2.0")
implementation("io.github.jan-tennert.supabase:postgrest-kt:2.2.0")
implementation("io.ktor:ktor-client-cio:2.3.9")
// (선택) BOM 사용 시:
// implementation(platform("io.github.jan-tennert.supabase:bom:2.2.0"))
// implementation("io.github.jan-tennert.supabase:realtime-kt")
// implementation("io.github.jan-tennert.supabase:postgrest-kt")
```

**Version verification (planner 실행 권장):**
```bash
# Kotlin/Ktor 충돌 점검
./gradlew app:dependencies --configuration releaseRuntimeClasspath | grep -E "(kotlin-stdlib|ktor-client|okhttp)"
# Expected: kotlin-stdlib 1.9.22, ktor-client-* 2.3.9, okhttp 4.12.0 (Retrofit 만)
```

**minSdk note:** supabase-kt 의 안내 — *"the minimum Android SDK version is 26. For lower versions, you need to enable core library desugaring."* 프로젝트 minSdk = 24 → **core library desugaring 필수** (아래 Common Pitfalls 참조).

[VERIFIED: github.com/supabase-community/supabase-kt README (Installation 섹션) — desugaring requirement]

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│ J2208A 워치 (BLE) → PC (j2208a/sensor_reader.py, Phase 4 04-03)        │
│   └─ Ble notify → process_sample → INSERT (raw/wear/safety_alerts)     │
└─────────────────────────────────────┬───────────────────────────────────┘
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Supabase Postgres                                                       │
│   - device_watches (HR/temp 마지막 본 값, snapshot)                    │
│   - wear_state_events (전이 시 1행)                                     │
│   - safety_alerts (위험 발생/해소/ack 시점)                             │
│   - devices (mac_address, user_id, last_comm_at)                        │
│   - WAL → logical replication slot → Realtime broadcast                 │
└──────────────────┬──────────────────────────┬───────────────────────────┘
                   │                          │
       (Realtime WSS, 3 channels)     (Edge Function HTTP)
                   │                          │
                   ▼                          ▼
┌──────────────────────────────────┬─────────────────────────────────────┐
│ Android App (testuser1)          │ supabase/functions/notifications     │
│ ┌──────────────────────────────┐ │  switch(action) {                    │
│ │ MyApp (Application)          │ │    case 'watch-alert': ✓ existing    │
│ │  └─ SupabaseClient (singleton)│ │    case 'watch-ack': NEW             │
│ └──────────────┬───────────────┘ │    case 'pair-watch': NEW (OQ-1)     │
│                │                  │  }                                   │
│      ┌─────────┴─────────┐        │  → service_role + ownership check    │
│      ▼                   ▼        │  → UPDATE / FCM via _shared/fcm.ts   │
│ HomeWorker        SafetyAlerts   └─────────────────────────────────────┘
│ Activity (XML)    Activity (Compose)         ▲
│  └─ ComposeView    └─ LazyColumn             │
│     └─ WatchCard       └─ AlertItem(ack btn) │
│        Composable                            │
│        (Realtime    (Realtime collect +      │
│         collect)     Retrofit POST ack) ─────┘
└──────────────────┬───────────────────────────┘
                   │
           (FCM Push, existing path)
                   ▼
        Android Notification Tray
        → tap → HomeWorkerActivity
```

데이터 흐름:
1. **워치→DB**: BLE PC daemon 이 raw/wear/safety_alerts INSERT (Phase 4 03 완료)
2. **DB→앱 (Realtime)**: 3 channel 구독 (`device_watches`, `wear_state_events`, `safety_alerts`) — `device_id=eq.{paired}` filter
3. **앱→DB (ack)**: SafetyAlertsActivity ack 버튼 → Retrofit POST → Edge Function `notifications` case 'watch-ack' → UPDATE `safety_alerts.ack_at`
4. **DB→앱 (ack 반영)**: Realtime UPDATE 이벤트 → 카드 하이라이트 / 리스트 색상 변경

### Recommended Project Structure

```
app/src/main/java/com/example/smart_safety_management/
├── MyApp.kt                              # SupabaseClient 싱글톤 추가 (Application onCreate)
├── HomeWorkerActivity.kt                 # XML Activity 그대로, watch_card_compose ComposeView 추가
├── SafetyAlertsActivity.kt              # NEW (Compose, Material3, AIEventActivity 패턴)
├── SettingDeviceManagementActivity.kt   # 기존 Compose, J2208A 워치 섹션 추가
├── DeviceManage.kt                       # 기존 Compose 패턴 base
├── watch/                                # NEW 패키지
│   ├── SupabaseModule.kt                 # SupabaseClient 초기화 + URL/anon key
│   ├── WatchRealtimeRepository.kt        # 3 channel subscribe + Flow expose
│   ├── WatchCardComposable.kt            # ComposeView 안에 들어갈 카드 Composable
│   ├── SafetyAlertsScreen.kt             # SafetyAlertsActivity 의 Composable
│   ├── PairWatchSection.kt               # SettingDeviceManagement 안의 J2208A 섹션
│   ├── MacAddressValidator.kt            # 정규식 검증 (단위 테스트 대상)
│   └── WatchAckRetrofitApi.kt            # Retrofit POST `/functions/v1/notifications`
└── ui/theme/                             # 기존 — 그대로 활용
    ├── Color.kt                          # MainOrange / StatusBlue / StatusRed (기존 이름)
    └── Type.kt                           # Pretendard (기존 폰트)

supabase/
├── functions/notifications/index.ts     # case 'watch-ack' (필수) + case 'pair-watch' (OQ-1) 추가
└── migrations/
    └── 011_watch_app_rls.sql            # NEW (devices/safety_alerts/wear_state_events/device_watches RLS 정책)
```

### Pattern 1: SupabaseClient 싱글톤 (Application 레벨)

**What:** SupabaseClient 는 process 단일 인스턴스로 유지 (Realtime WSS 연결 비용 + 재연결 backoff state 보존).

**When to use:** 본 phase 의 모든 화면에서 동일 client 사용.

**Example:**

```kotlin
// MyApp.kt (Application 클래스에 추가)
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.cio.CIO

class MyApp : Application() {
    val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,    // BuildConfig 또는 strings.xml
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY // anon key, service_role 절대 X
        ) {
            install(Realtime)
            install(Postgrest)
            // (선택) httpEngine 명시: CIO 가 default 가 아닐 때 강제
            httpEngine = CIO.create()
        }
    }

    override fun onCreate() {
        super.onCreate()
        printKeyHash()
        KakaoMapSdk.init(this, "...")
    }
}

// 사용처에서
val supabase = (applicationContext as MyApp).supabase
```

[Source: github.com/supabase-community/supabase-kt README + 2.2.0 sources `RealtimeImpl.kt`]

### Pattern 2: Realtime channel — `postgresChangeFlow` + Compose lifecycle binding (pure Compose)

**What:** Compose 화면에서 Realtime UPDATE/INSERT/DELETE 를 Flow 로 collect, 화면 lifecycle 따라 자동 unsubscribe.

**When to use:** SafetyAlertsActivity (Compose), SettingDeviceManagementActivity 의 워치 섹션.

**Example:**

```kotlin
// SafetyAlertsScreen.kt (Composable)
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

@Composable
fun SafetyAlertsScreen(deviceId: Int, supabase: SupabaseClient) {
    var alerts by remember { mutableStateOf<List<SafetyAlertRow>>(emptyList()) }

    DisposableEffect(deviceId) {
        val channel = supabase.channel("safety_alerts:device_$deviceId")

        // 초기 fetch (Realtime 은 미래 변경만 push)
        val fetchJob = CoroutineScope(Dispatchers.IO).launch {
            alerts = supabase.from("safety_alerts").select {
                filter { eq("device_id", deviceId) }
                order("raised_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList()
        }

        // Realtime UPDATE/INSERT 구독
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "safety_alerts"
            filter("device_id", FilterOperator.EQ, deviceId)
        }
        val collectJob = flow.onEach { action ->
            when (action) {
                is PostgresAction.Insert -> {
                    val newRow = action.decodeRecord<SafetyAlertRow>()
                    alerts = listOf(newRow) + alerts
                }
                is PostgresAction.Update -> {
                    val updated = action.decodeRecord<SafetyAlertRow>()
                    alerts = alerts.map { if (it.alertId == updated.alertId) updated else it }
                }
                else -> {}
            }
        }.launchIn(CoroutineScope(Dispatchers.Main))

        // 채널 join (WSS connect + 구독 활성화)
        CoroutineScope(Dispatchers.IO).launch { channel.subscribe() }

        onDispose {
            fetchJob.cancel()
            collectJob.cancel()
            CoroutineScope(Dispatchers.IO).launch { channel.unsubscribe() }
        }
    }

    LazyColumn { items(alerts) { AlertRow(it, onAck = { ackAlert(it.alertId) }) } }
}
```

[Source: 2.2.0 sources `RealtimeChannel.kt:42,52,145` + `PostgresChangeFilter.kt:50`]

### Pattern 3: Realtime channel — ComposeView 임베드 (HomeWorkerActivity XML)

**What:** XML/View 기반 Activity 안에 Compose 컴포넌트 1개를 ComposeView 로 임베드. lifecycle 은 Activity 의 onStart/onStop 에 묶임 (`ViewCompositionStrategy.DisposeOnLifecycleDestroyed`).

**When to use:** HomeWorkerActivity (XML, AppCompatActivity 유지) 안의 워치 카드.

**Example:**

```xml
<!-- res/layout/main_home_worker.xml — 기존 레이아웃 안에 추가 -->
<androidx.compose.ui.platform.ComposeView
    android:id="@+id/watch_card_compose"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginTop="12dp" />
```

```kotlin
// HomeWorkerActivity.kt — initUI() 끝에 추가
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

private fun setupWatchCard() {
    val composeView = findViewById<ComposeView>(R.id.watch_card_compose)
    composeView.setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this)  // Activity destroy 시 자동 dispose
    )
    val supabase = (application as MyApp).supabase
    composeView.setContent {
        Smart_Safety_ManagementTheme {
            val pairedDeviceId by produceState<Int?>(null) {
                // PostgREST 1회 query: SELECT device_id FROM devices WHERE user_id = $current AND device_type = 'WATCH'
                value = supabase.from("devices").select {
                    filter {
                        eq("user_id", UserSession.userId ?: return@filter)
                        eq("device_type", "WATCH")
                    }
                }.decodeSingleOrNull<DeviceRow>()?.deviceId
            }
            if (pairedDeviceId != null) {
                WatchCardComposable(deviceId = pairedDeviceId!!, supabase = supabase)
            } else {
                EmptyWatchPrompt(onClick = {
                    startActivity(Intent(this@HomeWorkerActivity, SettingDeviceManagementActivity::class.java))
                })
            }
        }
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    subscribeWorkerTopic()
    setContentView(R.layout.main_home_worker)
    initUI()
    checkLocationPermission()
    setupWatchCard()  // ← 추가
}
```

ComposeView 내부의 DisposableEffect 가 화면 lifecycle (Activity onStart/onStop 가 ComposeView attach/detach 와 동기화) 따라 channel.unsubscribe() 를 보장. 별도 onStart/onStop override 불필요.

[Source: developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views — `ViewCompositionStrategy.DisposeOnLifecycleDestroyed`]

### Pattern 4: Edge Function action-routing 확장 (case 'watch-ack')

**What:** 기존 `notifications/index.ts` 에 case 1개 추가, watch-alert 와 동일 위치/패턴.

**When to use:** BRIDGE-02 acknowledge 사이클.

**Example:**

```typescript
// supabase/functions/notifications/index.ts — 기존 switch 안에 case 추가
case "watch-ack": {
  const { user_id, alert_id } = body;
  if (!user_id || !alert_id) {
    return err("user_id, alert_id are required");
  }

  // ownership 검증 + ack_at 갱신을 한 SQL 로 (RLS 보강)
  const { data, error } = await supabase
    .from("safety_alerts")
    .update({ ack_at: new Date().toISOString() })
    .eq("alert_id", alert_id)
    .in("device_id", (
      await supabase.from("devices").select("device_id").eq("user_id", user_id)
    ).data?.map(d => d.device_id) ?? [])
    .is("ack_at", null)  // 이미 ack 된 경우 idempotent (no-op)
    .select();

  if (error) return err(error.message, 500);
  if (!data || data.length === 0) {
    return err("alert not found or already acknowledged or not owned by user", 404);
  }

  return ok({ ok: true, ack_at: data[0].ack_at, alert_id });
}
```

**Idempotency:** `.is("ack_at", null)` 가드로 같은 alert 두 번째 ack 시 0 행 영향 (응답 404, 클라이언트 에러 처리 무시 가능).

[Source: 기존 supabase/functions/notifications/index.ts:141-179 (case 'watch-alert' 패턴) + Phase 4 04-03-SUMMARY.md 의 `_shared/supabase.ts` 사용 규약]

### Pattern 5: RLS 정책 (worker 본인 device 데이터만 SELECT)

**What:** anon key + Realtime broadcast 가 본인 device 의 행만 worker 에게 push 되도록 RLS 정책 추가.

**When to use:** 신규 마이그레이션 `011_watch_app_rls.sql`.

**Example:**

```sql
-- 011_watch_app_rls.sql
-- Phase 7 / BRIDGE-01: worker 본인 device 의 watch 데이터만 SELECT 허용 (anon role)
-- 기존 010 의 service_role-only 정책 → worker 가 직접 SELECT 가능하도록 정책 추가.
-- 단 v1.0 PoC 는 testuser1 단일 사용자 — auth.uid() 미사용 (UserSession.userId 만 존재).
-- 따라서 RLS = device_id 가 (현재 anon role 이 query 한 device) 인 경우 통과.
-- v1.1 다중 작업자 진입 시 Supabase Auth 도입 + auth.uid() 기반 정책으로 강화.

-- ---- safety_alerts: 본인 device 의 alert SELECT ----
DROP POLICY IF EXISTS "safety_alerts_select_own_device" ON public.safety_alerts;
CREATE POLICY "safety_alerts_select_own_device" ON public.safety_alerts
    FOR SELECT
    TO anon, authenticated
    USING (
        device_id IN (
            SELECT device_id FROM public.devices
            WHERE user_id = current_setting('request.jwt.claim.sub', true)
              OR mac_address IS NOT NULL  -- v1.0 PoC: 1개 워치 매핑된 device 모두
        )
    );

-- ---- wear_state_events: 같은 정책 ----
DROP POLICY IF EXISTS "wear_state_events_select_own_device" ON public.wear_state_events;
CREATE POLICY "wear_state_events_select_own_device" ON public.wear_state_events
    FOR SELECT
    TO anon, authenticated
    USING (
        device_id IN (
            SELECT device_id FROM public.devices
            WHERE mac_address IS NOT NULL  -- v1.0 PoC
        )
    );

-- ---- device_watches: 기존 USING (true) 가 위치하지만 device_id 제한 추가 ----
DROP POLICY IF EXISTS "device_watches_select" ON public.device_watches;  -- 기존 003 정책 교체
CREATE POLICY "device_watches_select_own_device" ON public.device_watches
    FOR SELECT
    TO anon, authenticated
    USING (
        device_id IN (
            SELECT device_id FROM public.devices
            WHERE mac_address IS NOT NULL
        )
    );

-- ---- (선택, OQ-1 Path B 의 경우) devices UPDATE 정책 ----
-- 페어링을 supabase-kt PostgREST 로 직접 UPDATE 할 때만 필요.
-- OQ-1 Path A (Edge Function 경유) 채택 시 본 정책 SKIP.
-- DROP POLICY IF EXISTS "devices_update_pair_own" ON public.devices;
-- CREATE POLICY "devices_update_pair_own" ON public.devices
--     FOR UPDATE
--     TO anon, authenticated
--     USING (device_type = 'WATCH')  -- ⚠ user_id 검증 불가능 (auth 미사용) — anon UPDATE 위험
--     WITH CHECK (device_type = 'WATCH');
```

⚠ **RLS 보안 평가** (planner 가 수용 여부 확정 필요):
- v1.0 PoC = testuser1 단일 사용자 → 위 정책의 "mac_address IS NOT NULL" 조건은 사실상 모든 paired device 노출. 단일 사용자 환경에서는 영향 없음.
- v1.1 다중 작업자 진입 시 Supabase Auth 도입 (`auth.signInWith(Email)` 등) + `auth.uid()` 기반 정책으로 즉시 교체 필요. 본 phase 는 v1.0 PoC 한정 잠정 정책.
- 본 정책은 003_rls_policies.sql:295 의 `device_watches_select USING (true)` 보다는 **명시적 narrowing** — 후퇴 아님.

[Source: 003_rls_policies.sql 의 `get_my_user_id()` 패턴 + supabase.com/docs/guides/database/postgres/row-level-security]

### Pattern 6: MAC 입력 + 검증 + (Edge Function 경유) 등록

**What:** Compose TextField → 정규식 검증 → Retrofit POST → Edge Function `pair-watch` (OQ-1 Path A).

**When to use:** SettingDeviceManagementActivity 의 J2208A 워치 섹션.

**Example:**

```kotlin
// MacAddressValidator.kt
object MacAddressValidator {
    private val MAC_REGEX = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    fun isValid(mac: String): Boolean = MAC_REGEX.matches(mac.uppercase())
    fun normalize(mac: String): String = mac.uppercase().trim()
}

// PairWatchSection.kt (Composable)
@Composable
fun PairWatchSection(supabase: SupabaseClient) {
    var macInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf(false) }
    var pairResult by remember { mutableStateOf<String?>(null) }

    Column {
        Text("J2208A 워치", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = macInput,
            onValueChange = { macInput = it; isError = false },
            label = { Text("MAC 주소 (예: 21:02:02:06:01:69)") },
            isError = isError,
            singleLine = true
        )
        Button(
            onClick = {
                val normalized = MacAddressValidator.normalize(macInput)
                if (!MacAddressValidator.isValid(normalized)) {
                    isError = true
                    return@Button
                }
                pairing = true
                CoroutineScope(Dispatchers.IO).launch {
                    val req = PairWatchRequest(
                        action = "pair-watch",
                        user_id = UserSession.userId,
                        mac_address = normalized
                    )
                    val res = RetrofitClient.instance.callNotifications(req)
                    withContext(Dispatchers.Main) {
                        pairing = false
                        pairResult = if (res.isSuccessful) "✓ 등록됨" else "✗ 실패: ${res.errorBody()?.string()}"
                    }
                }
            },
            enabled = !pairing && macInput.isNotBlank()
        ) { Text(if (pairing) "등록 중…" else "등록") }
        pairResult?.let { Text(it) }
    }
}
```

**대안 (OQ-1 Path B — supabase-kt PostgREST 직접 UPDATE):**

```kotlin
val updated = supabase.from("devices").update({
    set("mac_address", normalized)
    set("user_id", UserSession.userId)
}) {
    filter {
        eq("device_type", "WATCH")
        // ⚠ 추가 ownership 필터 불가능 — anon 으로 UPDATE
    }
}
```

⚠ Path B 는 RLS 가 anon UPDATE 를 허용해야 하고 `auth.uid()` 미사용 환경에서 user_id 검증을 SQL WHERE 로 강제할 수 없음 → **권장하지 않음, OQ-1 참조**.

### Anti-Patterns to Avoid

- **Realtime 채널을 Activity onCreate 에서 1회 subscribe + onDestroy 에서 unsubscribe** — Activity recreation (회전) 시 채널 leak. 대신 Compose `DisposableEffect` 또는 `ViewCompositionStrategy.DisposeOnLifecycleDestroyed` 사용.
- **SupabaseClient 를 Activity scope 으로 매번 생성** — WSS 재연결 비용 + 다른 화면과 connection 공유 안 됨. 반드시 Application 싱글톤.
- **service_role key 를 Android 앱에 임베드** — 절대 금지. anon key 만 사용. service_role 은 Edge Function 의 `Deno.env` 에서만.
- **raw_events 채널 구독** — 5–6Hz × 60초 = 300+ 메시지/분, WSS 트래픽 + UI 갱신 폭주. CONTEXT.md D-01 의 "raw_events 구독 X" 결정 위반.
- **클라이언트 시계로 ack_at 세팅** — Edge Function 내부 `now()` 만 사용 (CONTEXT.md D-03 명시). 클라이언트 시계 신뢰 X.
- **Realtime 으로 모든 데이터 fetch** — 초기 표시는 PostgREST 1회 query, 이후 변경은 Realtime push. Realtime 은 "현재 상태" 를 안 주고 "미래 변경"만 broadcast 함.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WSS 연결 + 재연결 + heartbeat | OkHttp WebSocket + 직접 구현 | supabase-kt Realtime | Postgres WAL ↔ replication slot ↔ broadcast 프로토콜 (`phx_join`, `phx_reply`, `presence`) 직접 구현은 수백 줄 + 테스트 어려움 |
| Postgres logical replication 구독 | pg_cron + custom polling | Realtime postgres_changes | Replication slot 관리 + WAL position 추적 + filter 변환 = Supabase 가 인프라로 관리 |
| RLS bypass + ownership 검증 | 클라이언트에서 user_id WHERE 강제 | Edge Function (service_role) + WHERE 절 검증 | 클라이언트 신뢰 = 보안 사고. Edge Function 내부 검증 = 단일 신뢰 경계 |
| MAC 주소 검증 | 자체 파서 | `^([0-9A-F]{2}:){5}[0-9A-F]{2}$` 정규식 (CONTEXT.md 명시) | IEEE 802 표준 형식. 제조사 OUI 검증은 v1.1 |
| FCM 푸시 → Activity 진입 | 자체 broadcast receiver + Intent 라우팅 | NotificationCompat.Builder + pendingIntent + intent extras (`alert_id`, `alert_type=watch_alert`) | Android 표준. 기존 비전 5종 알림 패턴 그대로 (Phase 4 03 ANDROID_CHANNEL_ID="watch_alerts" 채널 분리됨) |

**Key insight:** 본 phase 는 "기존 자산을 얼마나 적게 건드리고 BRIDGE-3종 SC 를 충족하는가" 가 핵심 — 새 SDK 1개 + 새 화면 1개 + Edge Function case 2개 + RLS 마이그레이션 1개 = 변경 surface 최소화.

## Runtime State Inventory

> 본 phase 는 **rename/refactor 가 아닌 신규 기능 추가** — 카테고리별 영향 매핑:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `safety_alerts.ack_at` 컬럼은 010 마이그레이션에 이미 존재 (NULL default). 본 phase 는 ack 시점에만 갱신. | 데이터 마이그레이션 불필요 |
| Live service config | Supabase Realtime 은 default ON (Postgres extension `supabase_realtime` publication). 본 phase 의 3 테이블 (`device_watches`, `wear_state_events`, `safety_alerts`) 이 publication 에 포함되어야 broadcast 됨 — **planner 가 Supabase 콘솔 또는 SQL `ALTER PUBLICATION supabase_realtime ADD TABLE ...` 확인 필요** |
| OS-registered state | 없음 — Android 앱 신규 화면만 추가 | 없음 |
| Secrets/env vars | `BuildConfig.SUPABASE_URL` + `BuildConfig.SUPABASE_ANON_KEY` 가 새로 필요 (App 측). service_role key 는 기존 .env 에 있음 (Phase 4 03 의 .env.example) | `app/build.gradle.kts` 의 `defaultConfig` 에 `buildConfigField("String", "SUPABASE_URL", "\"...\"")` 추가 + `buildFeatures { buildConfig = true }` 활성화. anon key 는 git 커밋 OK (RLS 가 보안 경계) |
| Build artifacts | 없음 (빌드 후 .apk 만 영향) | 없음 |

## Common Pitfalls

### Pitfall 1: Kotlin/Compose Compiler 버전 ABI 비호환

**What goes wrong:** supabase-kt 3.x 추가 후 빌드 실패 — `kotlin-stdlib 2.3.21 vs 1.9.22 conflict`, 또는 `Compose Compiler 1.5.10 incompatible with Kotlin 2.x`.
**Why it happens:** supabase-kt 3.0.0+ 가 Kotlin 2.x stdlib 강제. CONTEXT.md D-01 의 "3.0.0" 표기는 사용자 가정값 (`(정확한 버전은 planner 가 ... 확인 후 결정)`).
**How to avoid:** **2.2.0 으로 다운그레이드** (Kotlin 1.9.22 매칭). State of the Art 섹션 + Standard Stack 의 alternative 표 참조.
**Warning signs:** `Module was compiled with an incompatible version of Kotlin` 빌드 에러, 또는 `IllegalStateException: Compose Compiler version mismatch`.

### Pitfall 2: minSdk 24 + supabase-kt 의 minSdk 26 요구

**What goes wrong:** 빌드 OK, 하지만 런타임 (API 24/25 단말) 에서 `NoClassDefFoundError: java.time.*` 또는 `java.nio.file.*`.
**Why it happens:** supabase-kt 가 java.time / java.util.concurrent.Flow 등 API 26+ 클래스 사용. 프로젝트 minSdk = 24.
**How to avoid:** core library desugaring 활성화 — `app/build.gradle.kts`:

```kotlin
android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
```

**Warning signs:** API 24/25 단말에서 앱 실행 시 즉시 크래시. CI 테스트가 API 26+ 만 돌 경우 발견 안 됨 — 실기기 테스트 필수.

[Source: developer.android.com/studio/write/java8-support#library-desugaring + supabase-kt README "minimum Android SDK version is 26"]

### Pitfall 3: Auth 모델 mismatch — `auth.uid()` 비활성

**What goes wrong:** Realtime 채널이 데이터 0행만 push. PostgREST UPDATE 가 RLS 거부 (403).
**Why it happens:** 본 프로젝트는 Firebase Auth + UserSession.userId 만 사용, Supabase auth (gotrue) 미도입. anon key 로 query 시 `auth.uid()` = NULL → 003_rls_policies.sql 의 `get_my_user_id()` = NULL → 모든 정책 USING 조건 false.
**How to avoid:**
- **읽기**: 011 마이그레이션의 RLS 가 `auth.uid()` 사용 X — `mac_address IS NOT NULL` + `device_id IN (...)` 패턴 사용 (위 Pattern 5).
- **쓰기 (페어링)**: Edge Function 경유 (OQ-1 Path A, 권장). service_role 이 RLS 우회 + 내부에서 user_id ownership 검증.
**Warning signs:** Realtime 구독 OK, 데이터 도착 0건. 또는 PostgREST UPDATE 응답 `{ "code": "42501", "message": "permission denied" }`.

### Pitfall 4: 기존 `device_watches_select USING (true)` 정책

**What goes wrong:** 003_rls_policies.sql:295 의 `device_watches_select USING (true)` 가 anon 에게 모든 device 의 HR/temp 노출.
**Why it happens:** v0.1 시점 (테스트 편의) 정책 — 본 phase 가 명시적으로 narrowing 안 하면 v1.0 출시 시점에도 그대로.
**How to avoid:** 011 마이그레이션이 기존 정책 DROP + 신규 정책으로 교체 (Pattern 5 SQL).
**Warning signs:** 코드 리뷰 시점에서만 발견 — 동작 정상이라 자동 테스트로 잡히지 않음.

### Pitfall 5: ack_at 컬럼명 vs `acknowledged_at` (REQUIREMENTS 표기)

**What goes wrong:** Edge Function 또는 클라이언트가 `acknowledged_at` 컬럼 UPDATE → `column does not exist` 에러.
**Why it happens:** REQUIREMENTS.md §7 BRIDGE-02 가 "`acknowledged_at` 컬럼 갱신" 으로 표기. 010 마이그레이션 + 본 phase 가 사용하는 실제 컬럼명은 **`ack_at`** (010_watch_pipeline.sql:105).
**How to avoid:** **`ack_at`** 으로 통일. REQUIREMENTS.md 본문도 SUMMARY 단계에서 `ack_at` 으로 정정 (또는 의미 동일성 명시).
**Warning signs:** Edge Function 배포 후 첫 ack 시도에서 `column "acknowledged_at" of relation "safety_alerts" does not exist`.

### Pitfall 6: Realtime publication 미포함

**What goes wrong:** 채널 subscribe 성공 (`SUBSCRIBED`), 하지만 INSERT/UPDATE 가 push 되지 않음.
**Why it happens:** Postgres logical replication publication `supabase_realtime` 에 신규 4 테이블이 자동 추가되지 않을 수 있음 (010 마이그레이션은 ALTER PUBLICATION 안 함).
**How to avoid:** 011 마이그레이션 또는 별도 SQL:

```sql
ALTER PUBLICATION supabase_realtime
    ADD TABLE public.device_watches, public.wear_state_events, public.safety_alerts;
```

또는 Supabase 콘솔의 Database → Replication 탭에서 토글.
**Warning signs:** SDK status = SUBSCRIBED, 하지만 Postgres 에서 직접 INSERT 해도 클라이언트에 도착 0건.

### Pitfall 7: OkHttp 버전 충돌 (Retrofit + Ktor)

**What goes wrong:** Gradle build 시 OkHttp 버전 conflict, 또는 런타임 `NoSuchMethodError`.
**Why it happens:** Ktor okhttp engine 사용 시 Ktor 2.3.9 가 OkHttp 4.12.x 의존, Retrofit 도 OkHttp 4.12.0 의존. 두 의존이 single resolved version 으로 결정되지만 transitive 가 mismatch.
**How to avoid:** **Ktor cio engine 사용** (OkHttp 미의존, pure Kotlin). 또는 OkHttp BOM 으로 단일 버전 강제. Stack 표 참조.
**Warning signs:** `./gradlew app:dependencies | grep okhttp` 가 2개 이상 버전 출력.

### Pitfall 8: ProGuard/R8 — supabase-kt + Ktor reflection

**What goes wrong:** release build 에서만 SDK 동작 안 함 (debug 는 OK), `kotlinx.serialization` 직렬화 실패 또는 `ClassNotFoundException`.
**Why it happens:** R8 가 supabase-kt 의 `@Serializable` 데이터 클래스 + Ktor 의 reflection 사용 클래스 strip.
**How to avoid:** `app/proguard-rules.pro` 에 추가:

```proguard
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
```

기존 `app/build.gradle.kts:31` 는 `isMinifyEnabled = false` — release 빌드도 R8 비활성. 본 phase 한정 그대로 OK, v1.1 minify 활성 시 위 룰 필요.
**Warning signs:** debug 빌드 OK, release 빌드에서 첫 SDK 호출 시 stack trace.

## Code Examples

### Realtime channel — `device_watches` (HR/temp snapshot) 구독

```kotlin
// WatchRealtimeRepository.kt
class WatchRealtimeRepository(private val supabase: SupabaseClient) {
    fun deviceWatchFlow(deviceId: Int): Flow<DeviceWatchSnapshot> = flow {
        // 초기 fetch
        supabase.from("device_watches").select {
            filter { eq("device_id", deviceId) }
            order("updated_at", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<DeviceWatchSnapshot>()?.let { emit(it) }

        // Realtime UPDATE 구독
        val channel = supabase.channel("device_watches:$deviceId")
        val flow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "device_watches"
            filter("device_id", FilterOperator.EQ, deviceId)
        }
        channel.subscribe()
        try {
            flow.collect { action ->
                emit(action.decodeRecord<DeviceWatchSnapshot>())
            }
        } finally {
            channel.unsubscribe()
        }
    }
}

@Serializable
data class DeviceWatchSnapshot(
    @SerialName("device_id") val deviceId: Int,
    @SerialName("heart_rate") val heartRate: Int?,
    @SerialName("body_temp") val bodyTemp: Float?,
    @SerialName("battery_level") val batteryLevel: Int?,
    @SerialName("updated_at") val updatedAt: String
)
```

[Source: 2.2.0 sources `RealtimeChannel.kt:145` `postgresChangeFlow` + 본 phase 의 device_watches 컬럼 (002_tables.sql:152-157, planner 가 컬럼 확인 권장)]

### Wear-state 라벨 + 색상 매핑

```kotlin
// WatchCardComposable.kt
@Composable
fun WearStateLabel(state: String?) {
    val (text, color) = when (state) {
        "WORN" -> "착용 중" to Color(0xFF22C55E)
        "OFF" -> "미착용" to Color(0xFFEF4444)
        "ABNORMAL" -> "비정상" to Color(0xFFEF4444)
        "WARMUP" -> "측정 준비 중" to Color(0xFFFBBF24)
        "TRANSIENT" -> "전이 중" to Color(0xFFFBBF24)
        null -> "알 수 없음" to Color.Gray
        else -> state to Color.Gray
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
```

기존 `Color.kt` 의 `MainOrange`/`StatusRed`/`StatusBlue`/`StatusGreenDark` 토큰 활용 권장 (DeviceManage.kt:283-288 패턴).

### Status badge (paired/connected/disconnected)

```kotlin
// PairWatchSection.kt
fun computeStatus(device: DeviceRow): WatchStatus {
    if (device.macAddress.isNullOrBlank()) return WatchStatus.UNPAIRED
    val lastComm = device.lastCommAt?.let { Instant.parse(it) } ?: return WatchStatus.DISCONNECTED
    val staleness = Duration.between(lastComm, Instant.now())
    return if (staleness.toMinutes() < 5) WatchStatus.CONNECTED else WatchStatus.DISCONNECTED
}

enum class WatchStatus(val label: String, val color: Color) {
    UNPAIRED("미등록", Color.Gray),
    CONNECTED("연결됨", Color(0xFF22C55E)),
    DISCONNECTED("끊김", Color(0xFFFBBF24)),
    PAIRED("등록됨 (오프라인)", Color.Gray);
}
```

### Polling fallback (D-01 명시)

```kotlin
// SDK 연결 실패 시 5초 polling
@Composable
fun WatchCardWithFallback(deviceId: Int, supabase: SupabaseClient) {
    var snapshot by remember { mutableStateOf<DeviceWatchSnapshot?>(null) }
    val realtimeStatus by supabase.realtime.status.collectAsState(initial = Realtime.Status.UNSUBSCRIBED)

    LaunchedEffect(deviceId, realtimeStatus) {
        if (realtimeStatus == Realtime.Status.SUBSCRIBED) {
            // Realtime path: WatchRealtimeRepository 사용
            WatchRealtimeRepository(supabase).deviceWatchFlow(deviceId)
                .collect { snapshot = it }
        } else {
            // Polling fallback: 5초 마다 PostgREST query
            while (true) {
                snapshot = supabase.from("device_watches").select {
                    filter { eq("device_id", deviceId) }
                    order("updated_at", Order.DESCENDING)
                    limit(1)
                }.decodeSingleOrNull()
                delay(5_000)
            }
        }
    }
    snapshot?.let { WatchCardUI(it) }
}
```

[Source: 2.2.0 sources `Realtime.kt` `Status` enum]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| supabase-kt 2.x (Kotlin 1.9.x) | **supabase-kt 3.x (Kotlin 2.x)** | 2024-12 (3.0.0 출시) | Ktor 3.x + WASM target. 본 프로젝트 = Kotlin 1.9.22 미상위로 인해 **2.2.0 잔존 채택** — Kotlin bump 시 3.x 즉시 마이그레이션 가능 |
| `gotrue-kt` 모듈 | `auth-kt` 모듈 | 3.0.0 | 본 phase 는 auth 미사용 → 무관 |
| `Realtime#createChannel` | `Realtime#channel` | 2.0.0 | 2.2.0 에 이미 적용됨 |
| `RealtimeChannel#join`/`leave` | `subscribe`/`unsubscribe` | 2.0.0 | 2.2.0 에 이미 적용됨 |
| Compose Compiler 1.5.10 (kotlinCompilerExtensionVersion) | Compose Compiler Gradle plugin | Kotlin 2.0+ | 본 프로젝트 Kotlin 1.9.22 → 1.5.10 그대로. v1.1 이후 Kotlin bump 시 마이그레이션 |

**Deprecated/outdated:**

- (없음 — 본 phase 는 신규 기능 추가)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | **D-04 페어링 UPDATE 는 Edge Function 경유로 amend** (CONTEXT.md 원안의 "supabase-kt PostgREST 직접 UPDATE" 변경) | OQ-1, Standard Stack alternatives | 사용자가 D-04 원안 고집 시 RLS bypass 위험 + 보안 사고. discuss-phase 에서 사용자 확인 필요 |
| A2 | v1.0 PoC 의 RLS 정책이 `mac_address IS NOT NULL` 패턴으로 narrowing — 단일 사용자 환경에서는 영향 없지만 v1.1 다중 작업자 진입 시 **즉시 Supabase Auth 도입 필요** | Pattern 5 RLS, Pitfall 4 | 사용자가 v1.1 시점을 명확히 인지 못 할 경우 보안 노출 지속 |
| A3 | Realtime publication `supabase_realtime` 에 4 신규 테이블 자동 포함 안 됨 → 011 마이그레이션 또는 콘솔 토글 필요 | Pitfall 6 | 코드는 정상, 데이터 도착 0건 → 디버깅 시간 손실 |
| A4 | 본 프로젝트 minSdk 24 → core library desugaring 필수 (실기기 API 24/25 검증 안 하면 발견 안 됨) | Pitfall 2 | 시연 단말이 API 26+ 면 영향 없으나 일반 사용자 단말에서 크래시 가능 |
| A5 | `safety_alerts` 컬럼명은 `ack_at` (REQUIREMENTS.md 의 `acknowledged_at` 표기는 wording 차이) | Pitfall 5 | 잘못된 컬럼명 사용 시 첫 ack 시도에서 SQL 에러 |
| A6 | Phase 4 04-04 단축 PoC 가 본 phase 진입 전 또는 동시에 실행됨 (CONTEXT.md D-05) | Phase Requirements 표 | PoC 미실행 시 카드 라이브 갱신 시연 불가능 — mock seed 스크립트 폴백 필요 |

**discuss-phase 가 확인해야 할 가장 중요한 항목 = A1.** 나머지 A2-A6 는 planner 가 plan/wave 문서에 반영 가능한 implementation detail.

## Open Questions

1. **OQ-1: D-04 페어링 UPDATE — Edge Function 경유 vs supabase-kt PostgREST 직접 UPDATE**
   - **What we know:** CONTEXT.md D-04 = "supabase-kt PostgREST 직접 UPDATE + 신규 RLS". 그러나 본 프로젝트는 Supabase Auth 미사용 (Firebase Auth + UserSession.userId), 따라서 anon key 로 UPDATE 시 `auth.uid()` 미해상 → RLS 가 user_id ownership 강제 불가능.
   - **What's unclear:** 사용자가 (a) Edge Function 경유로 amend 수용할지, (b) 원안 고수 + RLS 를 `device_type='WATCH' AND mac_address IS NULL` 같은 약한 조건으로 보호할지, (c) Supabase Auth 도입까지 포함할지.
   - **Recommendation:** (a) Edge Function 경유 — `notifications/index.ts` 에 case 'pair-watch' 추가. D-03 의 watch-ack 와 같은 파일 + 같은 패턴이라 변경 surface 최소. RLS 마이그레이션은 SELECT 정책만 추가, UPDATE 정책 불필요. discuss-phase 에서 사용자 결정 후 lock.

2. **OQ-2: 단축 PoC (D-05) 실행 책임자 + 시점**
   - **What we know:** CONTEXT.md D-05 = "본 phase 실행 전 사용자가 J2208A 워치 2시간 PoC 1회 실행". 데이터가 본 phase 의 카드 라이브 갱신 + ack 사이클 검증 입력.
   - **What's unclear:** 사용자가 5/14~5/15 중에 실행할지, 아니면 5/19 시연 직전 실행할지. mock 시드 스크립트를 폴백으로 만들 필요가 있는지.
   - **Recommendation:** Day 1-2 (5/14-15) 에 PoC 실행 + 결과를 PostgreSQL dump 로 저장 → 본 phase 의 모든 task 가 이 데이터셋을 입력으로 사용 (planner 가 verify SQL 에 dump 행 수 명시 가능).

3. **OQ-3: HomeWorkerActivity 의 워치 카드 위치 — 일일안전점검 위/아래/사이?**
   - **What we know:** `main_home_worker.xml` 레이아웃 구조 = profile_bar / emergency 영역 / 일일안전점검 헤더+RecyclerView / 조치요청 RecyclerView. 워치 카드를 "어디에" 끼울지 미정.
   - **What's unclear:** UX 우선순위.
   - **Recommendation:** profile_bar 바로 아래, emergency 영역 위 (작업자가 가장 먼저 보는 위치). planner 가 디자인 기획 (현장 매니저 검토) 받으면 변경 가능.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Maven Central (supabase-kt 2.2.0 + ktor 2.3.9) | 의존성 추가 | ✓ | 2.2.0 / 2.3.9 | — |
| Supabase Postgres + Realtime (운영) | BRIDGE-01 채널 구독 | ✓ | (운영 DB 활성, Phase 4 010 마이그레이션 적용됨) | — |
| Supabase Edge Functions runtime (Deno Deploy) | BRIDGE-02 watch-ack 배포 | ✓ | (Phase 4 03 에서 watch-alert 배포 검증됨) | — |
| Android SDK 26+ 또는 desugaring | supabase-kt 런타임 | ✓ | minSdk 24 + desugaring 활성화 필요 | desugar_jdk_libs:2.0.4 |
| Compose Compiler 1.5.10 (Kotlin 1.9.22) | UI | ✓ | 기존 (build.gradle.kts:49) | 변경 불필요 |
| J2208A 워치 (단축 PoC 데이터) | BRIDGE-01 라이브 검증 | ⚠ | testuser1 마스터 BLE 연결 필요 | mock seed 스크립트 (CONTEXT.md `<deferred>`) |
| FCM 푸시 (워치 알림 수신 검증) | BRIDGE-01 통합 | ✓ | Phase 4 03 + Firebase 신 프로젝트 검증됨 | — |
| Supabase realtime publication 토글 | 채널 broadcast 활성화 | ⚠ | 기본 활성, 신규 4 테이블 publication 추가 필요 | SQL `ALTER PUBLICATION` (Pattern 5 + Pitfall 6) |

**Missing dependencies with no fallback:**
- 없음

**Missing dependencies with fallback:**
- J2208A 워치 단축 PoC 데이터 → mock seed 스크립트 (단, 시연 가치 ↓)

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework (Android unit) | JUnit 4 (`testImplementation libs.junit`) — 기존 |
| Framework (Android instrumentation) | AndroidX Test + Espresso — 기존 (use 빈도 낮음) |
| Framework (Compose UI) | `androidx.ui.test.junit4` — 기존 (use 빈도 0) |
| Config file | `app/build.gradle.kts:100-105` (테스트 의존성), 별도 config 파일 없음 |
| Quick run command | `gradlew app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.*"` |
| Full suite command | `gradlew app:testDebugUnitTest app:assembleDebug` |
| 추가: Edge Function smoke | `curl -X POST .../functions/v1/notifications -d '{"action":"watch-ack",...}'` (Phase 4 03 패턴) |
| 추가: SQL ownership | `psql $DATABASE_URL -f tests/sql/test_011_rls.sql` (planner 가 작성) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BRIDGE-01a | Realtime 채널 subscribe/unsubscribe lifecycle | unit (mocked WSS) | `gradlew app:testDebugUnitTest --tests "*WatchRealtimeRepositoryTest*"` | ❌ Wave 0 |
| BRIDGE-01b | wear-state 라벨 매핑 (5 상태 → 색상/한글) | unit | `gradlew app:testDebugUnitTest --tests "*WearStateLabelTest*"` | ❌ Wave 0 |
| BRIDGE-01c | E2E: 단축 PoC 데이터 → 카드 라이브 갱신 ≤3초 | manual (실기기 + 워치) | `(시연 흐름 — 영상 캡처)` | manual only |
| BRIDGE-02a | watch-ack idempotency (같은 alert_id 두 번 ack 시 두 번째 no-op) | unit (mocked Edge Function response) | `gradlew app:testDebugUnitTest --tests "*WatchAckIdempotencyTest*"` | ❌ Wave 0 |
| BRIDGE-02b | watch-ack Edge Function curl smoke (200 OK) | integration | `bash tests/smoke/watch_ack.sh` (Phase 4 03 패턴) | ❌ Wave 0 |
| BRIDGE-02c | E2E: ack 버튼 → 카드 색상 변경 ≤5초 | manual | `(시연 흐름)` | manual only |
| BRIDGE-03a | MAC validation regex 단위 | unit | `gradlew app:testDebugUnitTest --tests "*MacAddressValidatorTest*"` | ❌ Wave 0 |
| BRIDGE-03b | RLS 정책 — worker A 가 worker B 의 alert SELECT 못 하는지 | SQL test (psql) | `psql $DATABASE_URL -f tests/sql/test_011_rls_isolation.sql` | ❌ Wave 0 |
| BRIDGE-03c | E2E: MAC 입력 → devices.mac_address 갱신 → status badge 변경 | manual | `(시연 흐름)` | manual only |

### Sampling Rate

- **Per task commit:** `gradlew app:testDebugUnitTest --tests "<task-related-package>.*"` — 변경 패키지만 수초 내
- **Per wave merge:** `gradlew app:testDebugUnitTest app:assembleDebug` — 약 30초~1분
- **Phase gate:** 위 + Edge Function curl smoke + 운영 DB SQL ownership 테스트 + 1회 실기기 manual E2E

### Wave 0 Gaps

- [ ] `app/src/test/java/com/example/smart_safety_management/watch/MacAddressValidatorTest.kt` — BRIDGE-03a
- [ ] `app/src/test/java/com/example/smart_safety_management/watch/WearStateLabelTest.kt` — BRIDGE-01b
- [ ] `app/src/test/java/com/example/smart_safety_management/watch/WatchRealtimeRepositoryTest.kt` (Mockk + TestCoroutineDispatcher) — BRIDGE-01a
- [ ] `app/src/test/java/com/example/smart_safety_management/watch/WatchAckIdempotencyTest.kt` (mocked Retrofit response) — BRIDGE-02a
- [ ] `tests/smoke/watch_ack.sh` (curl smoke, Phase 4 03 패턴 그대로) — BRIDGE-02b
- [ ] `tests/sql/test_011_rls_isolation.sql` (psql, 다른 user_id 로 SELECT 시 0행) — BRIDGE-03b
- [ ] `app/src/test/java/com/example/smart_safety_management/watch/` 패키지 신규 — 위 4개 unit test 컨테이너
- [ ] (선택) `Mockk` 의존성 추가 — `testImplementation("io.mockk:mockk:1.13.8")` (현 프로젝트 미사용)

*1회 manual E2E 는 시연 직전 실행 + 영상 캡처가 SUMMARY 의 evidence.*

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | partial | 본 phase 는 신규 인증 도입 X. UserSession (Firebase Auth) 그대로 사용. anon key 만 클라이언트에 임베드 — RLS 가 보안 경계 |
| V3 Session Management | no | (위와 동일) |
| V4 Access Control | **yes** | RLS 정책 (011 마이그레이션) — worker 본인 device 의 데이터만 SELECT. Edge Function 의 service_role 검증 (case 'watch-ack' 의 ownership 체크 SQL WHERE) |
| V5 Input Validation | **yes** | MAC regex (`^([0-9A-F]{2}:){5}[0-9A-F]{2}$`), Edge Function payload 검증 (existing watch-alert 패턴: `ALLOWED_TYPES` whitelist) |
| V6 Cryptography | no | TLS 만 의존 (WSS, HTTPS) — supabase-kt + Ktor 의 default TLS 사용 |

### Known Threat Patterns for Android + Supabase + 워치

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| anon key 가 git/decompile 로 노출 | Information Disclosure | RLS 정책 강제 (보안 경계는 RLS, anon key 는 라우팅 토큰일 뿐). service_role 절대 클라이언트에 X |
| 다른 worker 의 alert ack | Tampering | Edge Function `case 'watch-ack'` 의 `device_id IN (SELECT FROM devices WHERE user_id = $user_id)` ownership 검증 SQL |
| 페어링 시 다른 user_id 가짜 주입 | Spoofing | Edge Function `case 'pair-watch'` 가 `user_id` payload 을 무시하고 JWT/세션의 sub 또는 Retrofit 헤더로 받은 검증된 user_id 만 사용. (또는 본 phase 는 testuser1 단일 — 1차 잠정 신뢰 + v1.1 강화) |
| MAC 주소 무효 형식 → SQL injection 시도 | Tampering | Compose 측 정규식 검증 + Edge Function 측 동일 정규식 재검증 + supabase-js parameterized query |
| Realtime 채널 hijacking (다른 device_id filter 시도) | Information Disclosure | RLS 정책이 행 수준에서 차단 — 클라이언트가 잘못된 filter 보내도 0행 push |
| FCM data payload 위조 (가짜 alert_id) | Tampering | NotificationCompat pendingIntent → HomeWorkerActivity 가 alert_id 만 받음 + DB 재조회로 검증 (직접 신뢰 X) |

## Sources

### Primary (HIGH confidence)

- **Maven Central GAV metadata** — `realtime-kt-2.2.0.module` (kotlin-stdlib 1.9.22 + ktor-client-websockets 2.3.9), `realtime-kt-3.6.0.module` (kotlin-stdlib 2.3.21) — 직접 fetch
- **supabase-kt 2.2.0 sources jar** (`/tmp/realtime-kt-2.2.0-src/`) — `RealtimeChannel.kt:42 subscribe`, `52 unsubscribe`, `145 postgresChangeFlow` API 시그니처 직접 확인
- **github.com/supabase-community/supabase-kt** README + MIGRATION.md (raw GitHub fetch)
- **github.com/supabase-community/supabase-kt/Realtime/README.md** (raw GitHub fetch) — install + Realtime plugin 설치 패턴
- **developer.android.com/jetpack/androidx/releases/compose-kotlin** — Compose Compiler ↔ Kotlin 매핑 (1.5.10 → 1.9.22 확정)
- **공통 본 프로젝트 코드** — `app/build.gradle.kts`, `MyApp.kt`, `HomeWorkerActivity.kt`, `AIEventActivity.kt`, `DeviceManage.kt`, `SettingDeviceManagementActivity.kt`, `supabase/functions/notifications/index.ts`, `supabase/migrations/003_rls_policies.sql`, `supabase/migrations/010_watch_pipeline.sql`
- **`.planning/phases/04-watch-j2208a-pipeline/04-CONTEXT.md` + `04-03-SUMMARY.md`** — Phase 4 carry-forward decisions

### Secondary (MEDIUM confidence)

- **supabase.com/docs/guides/realtime/postgres-changes** (Kotlin 예제 섹션) — `postgresChangeFlow` 사용법 (WebFetch — 일부 anon key 명시 안 함)
- **github.com/supabase/discussions** — anon role + RLS 패턴 (WebSearch 검증)

### Tertiary (LOW confidence)

- (없음 — 모든 핵심 클레임이 1차 또는 2차 출처로 검증됨)

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — Maven Central GAV metadata + sources jar 직접 검증
- Architecture: HIGH — 기존 코드베이스 (HomeWorkerActivity XML / AIEventActivity Compose / DeviceManage Compose / notifications/index.ts) 직접 read + Phase 4 SUMMARY 인용
- Pitfalls: HIGH — 모든 함정이 1차 출처로 root cause 명시 (Maven 의존성, Compose 호환 표, RLS 정책 코드, 실 코드의 minSdk/auth 모델)
- 보안: MEDIUM — v1.0 PoC 한정 잠정 정책 (RLS) 채택 결정은 사용자 확인 필요 (Assumptions A1·A2)

**Research date:** 2026-05-14
**Valid until:** 2026-05-25 (Kotlin/Compose/supabase-kt 버전 빠르게 변동 — Phase 7 종료까지만 유효, v1.1 진입 시 재검증 필요)
