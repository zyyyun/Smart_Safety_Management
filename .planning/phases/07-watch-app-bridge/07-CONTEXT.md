# Phase 7: 워치-앱 양방향 연동 - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning
**Deadline:** 2026-05-20 (수요일, D-6)

<domain>
## Phase Boundary

Phase 4 가 적재한 J2208A 워치 데이터(`raw_events` / `wear_state_events` /
`safety_alerts`)가 Android 앱 화면에 ≤3초 지연으로 실시간 표시되고, 앱→DB
명령(`safety_alerts.ack_at` 갱신) 양방향 채널이 동작하며, 작업자가 본인 워치
MAC 을 등록하여 status(connected/disconnected/paired)를 확인할 수 있다.

**가벼운 통합 방향**: HomeWorkerActivity 에 워치 카드 1개 + SettingDevice
Management 의 워치 섹션 추가 + Edge Function `notifications` 에 case 'watch-ack'
추가. Phase 6 DEMO-03 (워치 대시보드 §9 3층) 의 본격 화면 구현은 별도 phase.

**Out of scope** (다른 phase / future):
- 워치 백엔드 적재 자체 (Phase 4 — Wave 1·2 완료, Wave 3 24h 실측은 v1.1 또는
  본 phase 진입 직전 단축 PoC 로 대체)
- 워치 대시보드 §9 3층 본격 구현 (1시간 line chart + 이벤트 타임라인 풀 화면) — Phase 6 DEMO-03
- 다중 작업자 매핑, QR/NFC 페어링, BLE 게이트웨이 — J2208A §11 v1.1+
- BLE write 양방향(시계 진동 등) — D-11 deferred, v1.1+
- SMS/카카오톡 알림 채널 — Next-7 별도 마일스톤

</domain>

<spec_lock>
## Locked Requirements (from ROADMAP.md)

3 requirements (BRIDGE-01·02·03), 4 Success Criteria — 본 phase 의 acceptance
는 ROADMAP.md Phase 7 섹션을 직접 참조한다.

- **BRIDGE-01**: Realtime 구독 → 화면 카드 + 알림 실시간 갱신, ≤3초 지연
- **BRIDGE-02**: 알림 acknowledge → Edge Function → `safety_alerts.ack_at` 갱신
  ≤5초 사이클
- **BRIDGE-03**: MAC 등록 페어링 화면 + status 표시 + `workers`/`devices` 매핑

**SC #4 (deadline)**: 2026-05-20 (수요일) 까지 Phase 7 완료.

</spec_lock>

<decisions>
## Implementation Decisions

### 실시간 전송 채널 (BRIDGE-01)
- **D-01: Supabase-kt Realtime SDK 도입**
  - `app/build.gradle.kts` 에 신규 의존성 추가:
    ```
    implementation("io.github.jan-tennert.supabase:realtime-kt:3.0.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.0.0")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    ```
    (정확한 버전은 planner 가 jan-tennert/supabase-kt latest stable 확인 후 결정)
  - **3개 채널 구독**:
    - `wear_state_events` (filter: `device_id=eq.{paired_device_id}`) — wear-state 라벨 갱신
    - `safety_alerts` (filter: `device_id=eq.{paired_device_id}`) — 카드 마지막 알림 + FCM 와 별개의 in-app 갱신
    - `device_watches` (filter: `device_id=eq.{paired_device_id}`) — HR/temp current snapshot (Phase 4 D-01 의 "마지막 본 값" 테이블)
  - **raw_events 는 구독 X** — 5–6Hz 트래픽이 모바일 클라이언트에 과다. 1분 집계
    `device_watches` snapshot 으로 충분.
  - **lifecycle**: HomeWorkerActivity onStart() 에서 channel.subscribe(),
    onStop() 에서 unsubscribe(). 화면 background 시 트래픽 중단 (배터리 보호).
  - **인증**: anon key + RLS 로 worker 본인 device_id 만 SELECT 허용 (D-04 RLS 추가).
  - **fallback**: SDK 연결 실패 시 (네트워크 두절 등) 5초 polling 폴백 — 사용자
    체감 다운그레이드 X. planner 가 SDK 의 `realtime.status` flow 활용.
  - 근거: BRIDGE-01 의 ≤3초 SC 를 SDK push (≤1초) 로 여유 있게 충족 + 이후 phase
    (RTSP, 워치 대시보드) 재사용. 폴링은 ≤5초 SC 위반, FCM-trigger 만은
    raw 데이터 미실시간 (알림 있을 때만 갱신).

### 워치 화면 위치 / 구조
- **D-02: HomeWorkerActivity 카드 1개 (가벼운 통합)**
  - **카드 필드** (세로 배치):
    1. **HR 숫자** (예: `82 bpm`) + **temp 숫자** (예: `36.4°C`) — 한 줄 좌우 배치
    2. **wear-state 라벨** (예: `WORN` / `OFF` / `WARMUP` / `ABNORMAL`) — 색상
       구분 (WORN=초록, OFF/ABNORMAL=빨강, WARMUP/TRANSIENT=노랑)
    3. **마지막 알림 1건 제목** (예: `⚠ 빈맥 의심 09:42`) — 24h 내 활성 alert
       중 최신 1건만. 없으면 "정상 운용 중" 표시.
  - **데이터 소스**: D-01 의 `device_watches` (HR/temp) + `wear_state_events`
    (state) + `safety_alerts` (마지막 1건). 모두 Realtime 구독.
  - **탭 동작**: 카드 클릭 → **신규 SafetyAlertsActivity** (워치 알림 전용 리스트
    + 각 항목에 acknowledge 버튼). AIEventActivity 와 별도 — AIEventActivity 는
    비전 5종 알림 전용으로 유지.
  - **빈 상태**: 페어링 안 됨 시 카드 자리에 "워치 등록 필요" 프롬프트 +
    SettingDeviceManagementActivity 진입 버튼.
  - **Phase 6 DEMO-03 와의 관계**: 본 phase 의 카드 + SafetyAlertsActivity 는
    가벼운 v1 — Phase 6 에서 §9 3층 (1시간 line chart + 이벤트 타임라인 풀
    화면) 추가 시 SafetyAlertsActivity 가 base 가 됨.

### Acknowledge 채널 (BRIDGE-02)
- **D-03: 기존 `notifications/index.ts` 에 case 'watch-ack' 추가**
  - Phase 4 03 에서 `watch-alert` case 추가한 동일 파일에 case 'watch-ack' 추가.
  - **Payload 계약**: `{action: "watch-ack", alert_id: number, user_id: string}`.
    `ack_at` 은 Edge Function 내부에서 `now()` 로 세팅 (클라이언트 시계 신뢰 X).
  - **SQL**: `UPDATE safety_alerts SET ack_at = now() WHERE alert_id = $1 AND
    device_id IN (SELECT device_id FROM devices WHERE user_id = $2)` —
    ownership 검증을 SQL WHERE 절에 포함 (RLS 보강).
  - **응답**: `{ok: true, ack_at: "2026-05-20T09:42:13Z"}` 또는 `{error}`.
  - **재배포**: 1회 (`supabase functions deploy notifications`) + curl smoke
    test 200 (Phase 4 03 패턴 동일).
  - **클라이언트**: SafetyAlertsActivity 의 acknowledge 버튼 → Retrofit POST
    `/functions/v1/notifications` (Authorization: Bearer anon, x-supabase-auth
    user JWT). UPDATE 결과 Realtime 구독으로 `ack_at` 갱신이 카드에 반영됨
    (취소선 또는 회색 처리).
  - **알림 전이 원칙 (D-09 from Phase 4)**: ack 자체는 새 알림을 발생시키지 않음
    — 단순 상태 갱신.

### 페어링 (BRIDGE-03)
- **D-04: SettingDeviceManagementActivity 에 'J2208A 워치' 섹션 추가**
  - **신규 섹션**: 화면 하단에 "J2208A 워치" 카드 1개 — MAC 입력 TextField +
    "등록" 버튼 + status badge.
  - **status**: `paired` (workers.device_mac 채워짐) / `connected` (paired +
    `devices.last_comm_at >= now() - interval '5 minutes'`) / `disconnected`
    (paired but last_comm_at < 5분).
  - **DB write**: 등록 클릭 → `UPDATE devices SET mac_address = $mac,
    user_id = $current_user WHERE device_type = 'WATCH'` (1인=1워치 단순 매핑,
    J2208A §11 v1.0 결정). MAC validation: `^([0-9A-F]{2}:){5}[0-9A-F]{2}$`
    정규식.
  - **unpair**: status badge 옆 "해제" 버튼 → `UPDATE devices SET
    mac_address = NULL, user_id = NULL`.
  - **Edge Function**: 신규 X — Android 가 supabase-kt PostgREST 로 직접
    UPDATE (D-01 SDK 도입했으므로 가능). RLS 정책 = worker 가 본인 user_id 의
    devices 만 UPDATE 허용 (D-04b 추가).
  - **Phase 4 D-01 와 호환**: `devices.mac_address` 컬럼은 Phase 4 010 마이그레이션
    에서 ALTER 로 추가됨. `workers` 테이블 별도 X (Phase 4 결정 — `profiles` +
    `devices.user_id` 로 매핑 충분).

### 데모 데이터 소스 (Phase 4 04-04 의존)
- **D-05: Phase 4 04-04 단축 PoC 먼저 (2시간 + REMOVED 시나리오)**
  - **선행 의존성**: 본 phase 실행 전 사용자가 J2208A 워치 2시간 착용 PoC 1회
    실행 — PC BLE 마스터 연결, 30분 안정 운용 → 5분 탈착 (REMOVED CAUTION
    발생) → 재착용 (REMOVED 해제) → 60분 추가 운용 → 종료.
  - **검증 산출물**:
    - `minute_summary` ≈ 120 행 (2시간 × 60분, good_ratio ≥ 0.30)
    - `wear_state_events` ≥ 3 행 (WARMUP→WORN, WORN→OFF, OFF→WORN)
    - `safety_alerts` ≥ 2 행 (REMOVED 발생 1, 해제 1 — `resolved_at` 채워짐)
    - `raw_events` ≈ 40k 행 (D-14 적용 후 cmd=0x09 만 적재)
  - **Phase 7 까지 영향**: 본 데이터로 카드 라이브 갱신 + SafetyAlertsActivity
    리스트 + acknowledge 사이클 검증 가능. 시연 스토리 = "2시간 착용 중 5분
    탈착 → 앱이 즉시 알림 → ack 후 정상 복귀" 로 PPT 활용.
  - **시연 시 재현**: 수요일 데모 직전 PC + 워치 다시 켜서 5분 탈착 라이브
    재현 가능 (또는 PoC 데이터 그대로 표시).
  - **mock 시드 옵션**: 단축 PoC 가 실패할 경우의 fallback 으로 `scripts/
    seed_watch_demo.py` 가 실측 PoC 결과를 dump 한 SQL 을 보관 (planner 가 결정).

### Claude's Discretion
- **Realtime SDK 정확한 버전** — jan-tennert/supabase-kt 의 latest stable
  (planner 가 release notes 확인). 3.0.x 계열 권장.
- **Realtime channel name 컨벤션** — `realtime:public:{table}:device_id=eq.{N}`
  default (SDK 컨벤션 따름).
- **카드 색상 테마** — 기존 HomeWorkerActivity Compose Material3 ColorScheme
  활용. wear-state 색상 매핑은 planner 가 디자인 토큰으로.
- **acknowledge 버튼 위치** — SafetyAlertsActivity 카드 우측 하단 default,
  미해결 alert (resolved_at IS NULL AND ack_at IS NULL) 만 노출.
- **MAC 입력 UX** — 직접 입력 default. 향후 BLE 스캔 추가 여지 위해
  TextField + "BLE 스캔" 버튼 placeholder (v1.1 활성화).
- **status badge 색상** — connected=초록 / disconnected=노랑 / unpaired=회색.
- **Realtime 구독 실패 시 재시도** — exponential backoff (1s→2s→4s→8s, 최대 30s).
- **Retrofit OkHttp 와 supabase-kt ktor-okhttp 공존** — OkHttp client 인스턴스
  공유 또는 별도 — planner 가 결정 (별도가 단순).

</decisions>

<specifics>
## Specific Ideas

- **"가벼운 통합" 정신** — 본 phase 는 BRIDGE 3종 SC 충족이 목표지 워치 대시보드
  본격 구현이 아님. 카드 1개 + 알림 리스트 1개 + 페어링 섹션 1개 = 화면 3개
  추가가 한계. Phase 6 DEMO-03 에서 풀 화면 구현.
- **알림 전이 원칙 일관 적용** (PROJECT.md Key Decision): 비전 알림과 동일하게
  워치 알림도 정상↔주의↔경보 *전이* 시점에만 1회. 카드의 "마지막 알림 1건"
  표시도 같은 alert 가 ack/resolve 될 때까지 그대로 (반복 표시 X).
- **신호 = 상태 신호 원칙** (PROJECT.md Key Decision): 카드 HR=0 표시는
  "측정 0" 이 아닌 "PPG 락온 전" — wear-state 라벨이 WARMUP/OFF 일 때 HR
  숫자 옆에 회색 처리 또는 "—" 표기. planner 가 UX 디테일 결정.
- **의료기기 면책** — 카드 어디에도 "심박수 측정값" 으로 표기 X. wear-state
  + 알림 중심 표시. PPT 슬라이드 같은 면책 1줄 ("1차 경고용, 의료기기 아님")
  은 SafetyAlertsActivity 하단에 fine print 로.
- **수요일 마감 우선순위** (D-6 = 6일):
  - **Day 1-2**: 단축 PoC 실행 + Realtime SDK 도입 + 카드 1개 (HomeWorker)
  - **Day 3-4**: SafetyAlertsActivity + acknowledge + watch-ack Edge Function
  - **Day 5**: SettingDeviceManagement 의 워치 섹션 (BRIDGE-03)
  - **Day 6 (수요일)**: 통합 시연 + 캡처 + buffer

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 7 직접 입력
- `.planning/REQUIREMENTS.md` §7 워치-앱 양방향 연동 (BRIDGE-01·02·03) — 본 phase 의 요구
- `.planning/ROADMAP.md` Phase 7 섹션 — Goal · 4 Success Criteria · Depends on (line 224-242)
- `.planning/phases/04-watch-j2208a-pipeline/04-CONTEXT.md` — Phase 4 의 모든 D-01~D-17 이 본 phase 의 데이터 계약 (특히 D-01 스키마, D-09 알림 전이, D-11 FCM only, D-12 Edge Function 호출 패턴)
- `.planning/phases/04-watch-j2208a-pipeline/04-03-SUMMARY.md` — `notifications/index.ts` 의 watch-alert action-routing 패턴 (본 phase D-03 가 동일 패턴 재사용)
- `.planning/phases/04-watch-j2208a-pipeline/04-01-SUMMARY.md` — `devices.mac_address` 컬럼 + `device_watches` snapshot 테이블 + `safety_alerts.ack_at` 컬럼 정의 (본 phase D-04, D-02, D-03 가 활용)

### Watch 도메인 원천
- `docs/J2208A_안전관리_시스템_PLAN.md` §6 알림 전이 원칙 — 본 phase D-03 acknowledge 가 위반 안 하도록 적용
- `docs/J2208A_안전관리_시스템_PLAN.md` §11 미해결 결정 — v1.0 = MAC 고정 매핑 (본 phase D-04 의 결정 근거)
- `docs/J2208A_안전관리_시스템_PLAN.md` §9 3층 대시보드 — Phase 6 DEMO-03 의 본격 구현 (본 phase 는 가벼운 v1 — 카드 1개 + 알림 리스트)

### 기존 자산 / 재사용
- `app/src/main/java/com/example/smart_safety_management/HomeWorkerActivity.kt` — 워치 카드 추가 위치 (D-02)
- `app/src/main/java/com/example/smart_safety_management/AIEventActivity.kt` — 알림 리스트 패턴 참조 (SafetyAlertsActivity base)
- `app/src/main/java/com/example/smart_safety_management/SettingDeviceManagementActivity.kt` — J2208A 워치 섹션 추가 위치 (D-04)
- `app/src/main/java/com/example/smart_safety_management/DeviceManage.kt` / `DeviceManageWorkerActivity.kt` — 디바이스 카드 패턴 참조
- `app/build.gradle.kts` — supabase-kt 의존성 추가 위치 (D-01)
- `supabase/functions/notifications/index.ts` — case 'watch-ack' 추가 위치 (D-03), 기존 watch-alert case 의 패턴 그대로
- `supabase/functions/_shared/fcm.ts` — FCM 발송 (Phase 4 D-11), 본 phase 변경 X
- `supabase/migrations/010_watch_pipeline.sql` (Phase 4 04-01) — `devices.mac_address`, `device_watches`, `safety_alerts.ack_at` 컬럼 정의 확인

### RLS 정책 (D-04 추가 마이그레이션 필요)
- `supabase/migrations/003_rls_policies.sql` — 기존 RLS 패턴 참조
- 신규: `supabase/migrations/011_watch_app_rls.sql` (planner 가 결정) — worker 본인 user_id 의 devices/safety_alerts SELECT/UPDATE 허용

### testuser1 시드
- testuser1 (`profiles.user_id`) — 단축 PoC 실행자 (Phase 4 D-13 동일 사용자)
- MAC `21:02:02:06:01:69` — Phase 4 검증된 J2208A 디바이스

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`HomeWorkerActivity.kt`**: 작업자 홈 화면 — 카드 추가 진입점 (D-02). Compose
  LazyColumn 또는 Column 안에 신규 워치 카드 Composable.
- **`AIEventActivity.kt` / `AIEventDetail.kt`**: 알림 리스트 + 상세 패턴 — 신규
  SafetyAlertsActivity 의 base. 단 비전 5종 알림 전용은 그대로 유지, 워치는
  별도.
- **`SettingDeviceManagementActivity.kt`**: 기존 디바이스 관리 화면 — J2208A 섹션
  추가 진입점 (D-04). 기존 카드 패턴 따라 워치 카드 추가.
- **`supabase/functions/notifications/index.ts`**: action-routing switch 패턴
  (Phase 4 03 에서 watch-alert 추가됨). case 'watch-ack' 같은 위치에 추가.
- **`supabase/functions/_shared/supabase.ts`**: service_role client + JWT
  검증 헬퍼 — D-03 Edge Function 에서 ownership 검증에 활용.
- **`MyApp.kt`**: Application 클래스 — supabase-kt SupabaseClient 싱글톤 초기화
  진입점.

### Established Patterns
- **Compose Material3 + Navigation Compose 2.8.5**: 화면 추가는 navigation graph
  에 destination + NavHost 패턴 (기존 다수 Activity 가 navigation 사용).
- **Retrofit + OkHttp + Gson**: REST API 호출 표준. supabase-kt 와 공존 가능
  (별도 client 권장).
- **Firebase FCM**: 푸시 수신은 기존 FCM service 그대로. data payload 의
  `alert_type=watch_alert` 으로 분기, pendingIntent → HomeWorkerActivity
  (D-02 의 알림 탭 정책).
- **Edge Function action-routing**: 단일 함수 안에 switch (action) — Phase 4 03
  에서 정착. 본 phase 가 동일 패턴 재사용 (D-03).
- **service_role 권한 분리**: Edge Function 만 service_role, 클라이언트는 anon +
  user JWT — RLS 로 ownership 강제.

### Integration Points
- **Realtime SDK ↔ Supabase Postgres**: WSS 연결, replication slot 활용. D-01
  의 3채널 구독은 Postgres 의 WAL 기반 — 추가 인프라 불필요. anon key + RLS 만
  설정.
- **app→Edge Function**: Retrofit POST `/functions/v1/notifications` (action=
  watch-ack). Authorization: Bearer {anon_key} + apikey 헤더. Body: JSON
  payload (D-03 계약).
- **app→PostgREST**: 페어링 등록 시 supabase-kt PostgREST `from("devices")
  .update {...}.eq("device_id", X)` 직접 호출 (D-04). RLS 정책으로 본인 워치만
  UPDATE 가능.
- **FCM watch-alert → HomeWorkerActivity**: NotificationCompat.Builder
  pendingIntent + extras `{alert_id: N, alert_type: "TACHY"}` → MainActivity
  intent filter → HomeWorkerActivity → 카드 하이라이트 (3초 깜빡 애니메이션) +
  acknowledge dialog 또는 SafetyAlertsActivity 진입.
- **단축 PoC → Phase 7 데이터**: `scripts/j2208a_sensor_reader.py` (Phase 4 03)
  실행 → raw_events / minute_summary / safety_alerts 적재 → Phase 7 카드가
  Realtime 구독으로 즉시 표시.

### 잠재 함정
- **OkHttp 버전 충돌**: 기존 Retrofit OkHttp 4.12.0 vs supabase-kt 의 ktor-
  okhttp 의존성. planner 가 dependency tree 확인 필요. exclude 또는 단일 버전
  강제 가능.
- **proguard 규칙**: supabase-kt + ktor 가 reflection 사용 — release build 시
  proguard rule 추가 필요 (planner 가 jan-tennert README 확인).
- **Realtime 트래픽**: 10분 미사용 시 Postgres replication slot 누적. SDK 의
  channel.unsubscribe() 와 onStop() lifecycle 정확히 묶어야 함.
- **RLS 정책 누락 위험**: anon key 노출은 정상이지만 RLS 가 빠지면 다른
  worker 의 워치 데이터 노출. D-04 의 RLS 마이그레이션이 본 phase 의 보안
  핵심.

</code_context>

<deferred>
## Deferred Ideas

- **워치 대시보드 §9 3층 풀 화면** (1시간 line chart + 이벤트 타임라인) → Phase 6 DEMO-03
- **BLE write 양방향** (시계 진동 cmd_vibrate, 시계 측 acknowledge) → v1.1+
- **다중 작업자 매핑** (1 manager → N workers' watches 동시 모니터) → v1.1
- **QR / NFC 페어링** (J2208A §11 작업자↔디바이스 매일 체크인) → v1.1 결정 phase
- **Manager 화면에서 모든 작업자 워치 grid** (관리자 전용 대시보드) → v1.1 또는 Phase 6 manager 변형
- **워치 차트 sparkline** (카드 안에 5시간 HR/temp 추세) → Phase 6 DEMO-03
- **알림 음성 / TTS 안내** (PTT 음성 트랙) → Next-5 별도 마일스톤
- **SMS / 카카오톡 알림 채널** → Next-7 별도 마일스톤
- **수동 mock 시드 스크립트** (`scripts/seed_watch_demo.py`) — 본 phase 는 단축
  PoC 데이터 사용 default, mock 은 fallback 으로만 (planner 가 결정).
- **개인정보 정책** (워치 raw 데이터 보관·익명화·열람권) — J2208A §11 미해결, v1.1
- **충전 운용 절차 / 펌웨어 동결 정책** — J2208A §11 미해결, v1.1

</deferred>

---

*Phase: 07-watch-app-bridge*
*Context gathered: 2026-05-14*
