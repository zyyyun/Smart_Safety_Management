# Phase 9: TBM 현장 작업자 가이드 — Research

**Researched:** 2026-05-18
**Domain:** Android Compose Canvas signature → Supabase Storage (private bucket + signed URL) + supabase-kt 4 채널 Realtime + pg_cron 1분 + 4-case Edge Function (Phase 4·7·8 패턴 1:1 미러)
**Confidence:** HIGH (storage-kt 2.2.0 ABI + dependencies Maven Central GAV 직접 검증, 나머지는 Phase 7·8 patterns 가 운영 검증)
**Deadline:** 없음 (ROADMAP estimate 3일, Phase 7 Wave 4 deferred + Phase 4 04-04 24h 대기 동안 평행 진행 가능)

## Summary

CONTEXT.md 의 9개 결정 (D-01~D-09) 는 모두 valid. Phase 9 는 Phase 4 (알림 전이 원칙) + Phase 7 (Edge Function-mediated write + ComposeView 임베드 + supabase-kt) + Phase 8 (pg_cron + Vault + sendPushToUsers plural) 의 패턴 union — 신규 기술 surface 는 **3개 신규 영역**으로 한정:

1. **Compose Canvas signature pad** — Path 누적 + drawPath + Canvas → ImageBitmap → PNG ByteArray. 본 코드베이스 최초 사용.
2. **`storage-kt:2.2.0` 모듈** — 본 코드베이스 최초 사용 (Phase 7 은 realtime + postgrest 만). Maven GAV `kotlin-stdlib:1.9.22` + `gotrue-kt:2.2.0` (신규 transitive) 검증 완료.
3. **Private Storage 버킷 + signed URL 60s** — 본 코드베이스 최초 사용 (004 는 4개 버킷 모두 `public=true`). 신규 RLS 정책 템플릿.

**나머지 모든 영역은 Phase 4·7·8 의 패턴 80%+ 기계적 미러** — 마이그레이션 013 = 010 (4 테이블 + Realtime publication) + 011 (RLS narrowing) + 012 (Vault + pg_cron + SECURITY DEFINER) 의 도메인 swap. Edge Function `tbm-start/checkin/end/missed` = Phase 7 `watch-pair` (ownership 검증) + Phase 8 `camera-down/recovered` (sendPushToUsers plural) 의 union. Android `tbm/` 패키지 = `watch/` 패키지 1:1 미러 + signature 모듈 추가.

**Primary recommendation:** Day 1 — 마이그레이션 013 작성 + 적용 + `tbm-signatures` 버킷 생성 + 단위 테스트 (`WorkTypeValidator`, `TbmParticipantsReducer`). Day 2 — Edge Function 4 case 작성 + deploy + curl smoke 12 cases. Day 3 — Android `tbm/` 패키지 (SupabaseModule 재사용, 4채널 Repository, SignatureCanvas, 2 Activities) + HomeActivity/HomeWorkerActivity 카드 임베드 + 실기기/에뮬레이터 1 cycle 캡처.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01**: `supabase/migrations/013_tbm_schema.sql` — 4 테이블 (`tbm_sessions`/`tbm_templates`/`tbm_checklists`/`tbm_participants`) + 인덱스 + 5종 work_type 시드 + RLS (v1.0 USING (true) 패턴 Phase 7 011 미러) + Realtime publication 4 테이블 등록 + `test_013_tbm_isolation.sql`.
- **D-02**: Manager-led 세션 / Worker-joins 참여. UNIQUE `(group_id, session_date)` v1.0 잠금 (1 group × 1 day × 1 session). 흐름 = manager `tbm-start` → group worker 전원 FCM `tbm-started` → worker `tbm-checkin` (서명) → manager `tbm-end`.
- **D-03**: 체크인 방법 = **수기 서명 단일** (Compose Canvas → PNG → `tbm-signatures` 버킷 private + service_role write only + signed URL 60s). 키 컨벤션 `{session_id}/{user_id}_{timestamp}.png`. NFC/QR 옵션 deferred.
- **D-04**: 미참여 대상 = `profiles.group_id = session.group_id AND user_role IN ('worker','general_manager') AND user_id NOT IN participants AND user_id != leader_user_id`. v1.0 한정 출근(attendance) 시스템 부재 → 단순 정의 채택.
- **D-05**: pg_cron 1분 주기 + `expected_end_at + 30분` 임계 + `tbm_sessions.missed_alert_at` dedup. Vault `service_role_key` + `edge_function_base_url` 재사용 (Phase 8 시드됨). 알림 수신자 = 미참여 worker + leader (1:N sendPushToUsers).
- **D-06**: HomeActivity 신규 카드 "오늘 TBM 현황" (ComposeView 임베드 — manager 측 최초) + `TbmDashboardActivity` (Compose, manager only 권한 가드 `UserSession.userRole == MANAGER`). 3 채널 Realtime (sessions/checklists/participants).
- **D-07**: HomeWorkerActivity 워치 카드 아래 "오늘 TBM" 카드 + `TbmWorkerActivity`. 카드 상태 4종 (세션 없음/active 미참여/active 참여완료/종료) — Phase 7 D-04 status badge 3색상 패턴 확장.
- **D-08**: `notifications/index.ts` 에 4 case 추가 (`tbm-start`/`tbm-checkin`/`tbm-end`/`tbm-missed`). `tbm-start` = `tbm_templates` 조회 → `tbm_sessions` insert (UNIQUE 충돌 409) → `tbm_checklists` bulk insert → group worker FCM. `tbm-checkin` = ownership 검증 (`profiles.group_id == session.group_id`) + UNIQUE 충돌 200 idempotent. 재배포 1회.
- **D-09**: `fcm_default_channel` 재사용 (Phase 8 Option B). `data.type='tbm_alert'` + `action_in_app` 분기. MyFirebaseMessagingService 의 `watch_alert` 옆에 `tbm_alert` 분기 추가. extras 신뢰 X — Activity 진입 시 DB 재조회.

### Claude's Discretion

- 체크리스트 항목 텍스트 워딩 (KOSHA 표준 참조)
- `expected_end_at` default = "15분 후" 권장
- SignatureCanvas 색상/굵기 — onSurface 4dp stroke default
- TbmDashboardActivity 시계열 그래프 (v1.0 = 오늘 1일 view 만)
- 세션 종료 후 read-only mode 시점 (`ended_at IS NOT NULL` 즉시)
- 참여 알림 (entry, not missed) — v1.0 한정 X
- 다중 세션/다중 작업장 — v1.0 UNIQUE 잠금

### Deferred Ideas (OUT OF SCOPE)

- 명시적 출근(attendance) 체크인 시스템 → v1.1+ 별도 phase
- TBM NFC / QR 체크인 옵션 → v1.1+
- TBM 사진 첨부 (안전장구 착용 사진) → v1.1+
- 다중 작업장(workplace) 동시 운용 → v1.1+
- 세션 종료 후 manager 승인 워크플로우 → v1.1+
- 체크리스트 정규화 (`tbm_template_items` 분리, LP-5 룰 seed 연동) → v1.1
- TBM 통계 / 월별 참여율 / 시계열 chart → Phase 6 DEMO 또는 v1.x
- 카카오톡 알림톡 / SMS 채널 → Next-7
- TTS 음성 가이드 → Next-5 PTT
- 다국어 체크리스트 → v1.x
- `tbm_alerts` Android NotificationChannel 분리 → v1.1
- 체크리스트 자동 위험도 매핑 (LP-5) → v1.1
- 휴가 / 외부근무 작업자 제외 캘린더 → v1.1+
- 참여 알림 (entry) → v1.1+
- 세션 시작 자동 (cron 09:00) → v1.1+

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TBM-01 | 4 신규 테이블 (`tbm_sessions`/`tbm_checklists`/`tbm_participants`/`tbm_templates`) + RLS + Realtime publication | §"마이그레이션 013 검증" + §"SQL 검증" + Phase 4 010 + Phase 7 011 의 미러. `\d+` 4 테이블 + RLS ENABLE + publication 4 등록 확인. |
| TBM-02 | Android TBM 가이드 화면 — 오늘 세션 시작 → 작업유형 선택 → 템플릿 체크리스트 → 수기 서명 체크인 → 세션 종료 + Supabase 적재. 1 cycle 캡처. | §"Compose Canvas signature 패턴" + §"supabase-kt storage 통합" + §"Realtime 4채널 lifecycle (dynamic session_id)". |
| TBM-03 | 관리자 화면 — 일자별 참여 여부 + 미참여 작업자 FCM (관리자 지정 시각 + 30분). 1일 사이클 검증. | §"미참여 대상 SQL 검증" + §"tbm_missed_attendance_check 검증" + §"Edge Function tbm-missed 검증". |

</phase_requirements>

## Project Constraints (from CLAUDE.md)

`./CLAUDE.md` 파일 부재. 컨벤션은 기존 코드베이스 + `.planning/` 누적 결정에서 추출:

- **마이그레이션 번호** — 012 까지 사용, 본 phase = `013_tbm_schema.sql`.
- **Edge Function action-routing** — 단일 `notifications/index.ts` 안 switch (Phase 4·7·8 정착).
- **service_role 분리** — Edge Function 만 service_role, 클라이언트는 anon. service_role key 는 Vault.
- **알림 전이 원칙** — 정상 ↔ 경고 전이 시점에만 1회 (PROJECT.md key decision, Phase 4·8 일관).
- **신호 = 상태 신호 원칙** — TBM 카드의 "5/8 참여" 가 추상 상태 라벨, raw signed_at 은 hidden.
- **수기 서명 PII** — `tbm-signatures` private + signed URL 60s. public read 절대 X.
- **commit prefix** — `phase($num): ...` 또는 `docs($num): ...`.
- **CONTEXT.md `<deferred>` 절대 준수** — out of scope 항목은 plan 에 포함 X.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 세션 생성/종료 (manager) | Supabase Edge Function `notifications` (case `tbm-start`/`tbm-end`) | Postgres UNIQUE constraint | service_role 권한 + UNIQUE `(group_id, session_date)` idempotency. Phase 7 watch-pair 패턴. |
| 체크리스트 bulk insert | Supabase Edge Function `tbm-start` (template snapshot → N rows) | tbm_templates JSONB 조회 | template 변경에도 이력 보존. UNIQUE `(session_id, item_idx)` 충돌 차단. |
| 작업자 체크인 + 서명 업로드 | Android (Compose Canvas → PNG bytes) + Supabase Storage (`tbm-signatures` 버킷) | Edge Function `tbm-checkin` (DB row + ownership 검증) | 2-step: (a) 클라이언트가 storage 에 PNG 업로드 후 path 받음, (b) Edge Function 에 path 포함 POST. ownership 검증은 server-side. |
| 미참여 임계 평가 | Supabase Postgres pg_cron 1분 + `tbm_missed_attendance_check()` 함수 | Vault `service_role_key` | 시간 의존 (`expected_end_at + 30분`) — DB 가 권한 있는 곳. Phase 8 012 1:1 미러. |
| 미참여 FCM 발송 | Supabase Edge Function `tbm-missed` | `_shared/fcm.ts` `sendPushToUsers` | 1:N (미참여 worker N명 + leader 1명). Phase 8 `camera-down` 패턴 (plural). |
| 관리자 대시보드 (Realtime) | Android Compose (`TbmDashboardActivity`) | supabase-kt Realtime 3 채널 (sessions/checklists/participants) | Phase 7 `WatchRealtimeRepository` 패턴. participants insert push 가 실시간 참여 갱신. |
| 작업자 카드 + Activity (Realtime) | Android Compose (`TbmWorkerActivity` + HomeWorkerActivity 카드) | supabase-kt Realtime 2 채널 (sessions filter group_id, participants filter session_id) | 동적 session_id — 2단계 구독 (아래 §"Pattern 2"). |
| FCM → Activity 진입 | Android `MyFirebaseMessagingService` | `data.type='tbm_alert'` + `action_in_app` 분기 | 기존 watch_alert 분기 옆에 tbm_alert 추가. extras 신뢰 X. |
| 서명 표시 (manager 대시보드) | Android (Coil 또는 Glide) | supabase-kt `bucket.createSignedUrl(path, 60.seconds)` | private bucket → signed URL 60s 만료. 클릭 시 새 URL 발급. |

## Standard Stack

### Core (신규 추가 — Phase 7 위에 추가됨)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.github.jan-tennert.supabase:storage-kt` | **2.2.0** | Storage 버킷 `tbm-signatures` PNG 업로드 + signed URL 생성 | **Maven GAV 직접 검증**: `kotlin-stdlib.requires=1.9.22` (Phase 7 realtime/postgrest 2.2.0 과 ABI 일치). `gotrue-kt:2.2.0` 신규 transitive. [VERIFIED: `https://repo1.maven.org/maven2/io/github/jan-tennert/supabase/storage-kt/2.2.0/storage-kt-2.2.0.module` 직접 fetch 2026-05-18] |
| (transitive) `gotrue-kt:2.2.0` | 자동 | storage-kt 가 강제 (auth bucket policy 검증용). 본 phase 는 직접 사용 X. | 본 코드베이스 Firebase Auth 사용 → gotrue session 생성 X. 단순 ABI 호환 deps. |

### Supporting (기존 자산 재사용 — 변경 0)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `realtime-kt` + `postgrest-kt` | 2.2.0 (기존, Phase 7) | 4 채널 구독 + 초기 fetch | TbmWorkerRepository / TbmManagerRepository [VERIFIED: `app/build.gradle.kts:144-145`] |
| `ktor-client-cio` | 2.3.9 (기존, Phase 7) | Ktor WSS/HTTP engine | 자동 (supabase-kt 의존) [VERIFIED: `app/build.gradle.kts:146`] |
| Retrofit 2.9.0 + OkHttp 4.12.0 | 기존 | Edge Function 4 case POST | 기존 `RetrofitClient` 패턴 — `tbm-start/checkin/end/missed` 모두 같은 URL `/functions/v1/notifications`. |
| Material3 + Navigation Compose 2.8.5 | 기존 | `TbmDashboardActivity`/`TbmWorkerActivity` UI | AIEventActivity + SafetyAlertsActivity 패턴. |
| Firebase FCM | 기존 (BoM) | tbm_alert 수신 | `MyFirebaseMessagingService` 의 `watch_alert` 옆에 `tbm_alert` 분기 추가. Android 코드 변경 1줄 (분기). |
| `core-library-desugaring 2.0.4` | 기존 (Phase 7) | minSdk 24 + supabase-kt minSdk 26 | 변경 0 — Phase 7 에서 활성화됨. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| **supabase-kt storage-kt 2.2.0** | Retrofit POST `/storage/v1/object/{bucket}/{path}` (Bearer anon + Content-Type: image/png + ByteArray body) | (a) storage-kt 2.2.0 = `kotlin-stdlib:1.9.22` 검증 ✓ Phase 7 ABI 일관, (b) Retrofit fallback 은 anon RLS 통과 못함 — service_role 필요 → Edge Function 우회 필요. **storage-kt 추가가 정답** (gotrue-kt transitive 1개만 늘어남). |
| **신규 `tbm-signatures` 버킷** | 기존 `check-images` 버킷 reuse | check-images 는 `public=true` (004_storage.sql:20). PII (수기 서명) 정책상 private 필요 — 신규 버킷 강제. |
| **Compose Canvas + Path** | XML View `android.gesture.SignatureView` (deprecated) | Compose Canvas 가 Android 표준 (M3 native), Path + drawPath 단순. lifecycle = Compose state. |
| **Realtime 4 채널 동시 구독** | 3 채널 (templates 제외) | tbm_templates 는 seed-only (manager 가 시작 시 1회 조회). 변경 reactive 필요 없음. → **templates 채널 제외 권장 (3 채널)**. Corrections §C2 참조. |
| **D-08 4 case 모두 notifications row insert** | 일부만 insert | Phase 7 `watch-alert` = insert + push. Phase 8 `camera-down` = push-only. 본 phase = `tbm-started` insert (manager-triggered 시작 이벤트) + 나머지 push-only. Corrections §C1. |
| **단일 cron 함수** (Phase 8 같은) | 다중 함수 분리 | 단일 함수 = 1 cron job + 1 SECURITY DEFINER. Phase 8 패턴 1:1. |

**Installation (Gradle):**

```kotlin
// app/build.gradle.kts dependencies block — Phase 7 의 2개 줄 옆에 1줄 추가
implementation("io.github.jan-tennert.supabase:realtime-kt:2.2.0")    // 기존
implementation("io.github.jan-tennert.supabase:postgrest-kt:2.2.0")   // 기존
implementation("io.github.jan-tennert.supabase:storage-kt:2.2.0")     // ← 신규 Phase 9
implementation("io.ktor:ktor-client-cio:2.3.9")                       // 기존
```

**Version verification:**
- storage-kt 2.2.0 `kotlin-stdlib.requires=1.9.22` ✓ [VERIFIED: Maven GAV `storage-kt-2.2.0.module` 직접 fetch]
- gotrue-kt 2.2.0 `kotlin-stdlib.requires=1.9.22` + `ktor-server-cio:2.3.9` 신규 transitive [VERIFIED: `gotrue-kt-2.2.0.module`]
- `./gradlew app:dependencies --configuration releaseRuntimeClasspath | grep -E "(kotlin-stdlib|ktor|gotrue|storage-kt)"` — Plan 1 Wave 0 의 첫 검증 단계.

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│  MANAGER (testuser1, HomeActivity)                                      │
│   └─ "오늘 TBM 현황" ComposeView 카드 (NEW)                              │
│       └─ tap → TbmDashboardActivity (Compose)                           │
│            ├─ 작업유형 선택 + expected_end_at + 위치 → "세션 시작"        │
│            │   └─ Retrofit POST /functions/v1/notifications              │
│            │       (action=tbm-start, leader_user_id, group_id,         │
│            │        work_type, expected_end_at, location?)              │
│            ├─ 참여자 grid (Realtime tbm_participants)                    │
│            ├─ 미참여자 회색 + "수동 알림" 버튼                            │
│            └─ "세션 종료" → tbm-end                                      │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   ▼ (action=tbm-start)
┌─────────────────────────────────────────────────────────────────────────┐
│  EDGE FUNCTION  notifications/index.ts (case 'tbm-start')               │
│   1. SELECT * FROM tbm_templates WHERE work_type=$1 (checklist JSONB)   │
│   2. INSERT tbm_sessions ... ON CONFLICT (group_id, session_date)       │
│      DO NOTHING → if 0 rows → 409 "이미 오늘 세션 존재"                 │
│   3. INSERT tbm_checklists (session_id, item_idx, item_text) × N        │
│      from template.checklist[i]                                         │
│   4. SELECT user_id FROM profiles                                       │
│      WHERE group_id=$1 AND user_role IN ('worker','general_manager')    │
│        AND user_id != leader_user_id                                    │
│   5. sendPushToUsers(supabase, workerIds, {                             │
│        data: { type:'tbm_alert', action_in_app:'tbm-started',           │
│                session_id, work_type } })                                │
│   6. (optional) INSERT notifications row (Corrections §C1)              │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   ▼ (FCM push)
┌─────────────────────────────────────────────────────────────────────────┐
│  WORKER (worker-A, B, C 폰)                                              │
│   └─ MyFirebaseMessagingService (data.type='tbm_alert')                 │
│       → showNotification → tap pendingIntent → HomeWorkerActivity       │
│           └─ "오늘 TBM" ComposeView 카드 (NEW, 워치 카드 아래)          │
│               ├─ 세션 active + 미참여 (노랑): tap → TbmWorkerActivity  │
│               │   ├─ 세션 정보 + 체크리스트 (read-only)                 │
│               │   ├─ Compose Canvas signature pad (NEW)                 │
│               │   └─ "참여 확인" 버튼                                    │
│               │       ├─ 1. Canvas → ImageBitmap → PNG ByteArray        │
│               │       ├─ 2. supabase.storage.from('tbm-signatures')     │
│               │       │     .upload("{sid}/{uid}_{ts}.png", bytes)      │
│               │       └─ 3. Retrofit POST notifications                 │
│               │           (action=tbm-checkin, session_id, user_id,     │
│               │            signature_url=storage_path)                  │
│               ├─ 세션 active + 참여 완료 (초록): tap → 본인 참여 표시    │
│               └─ 세션 없음/종료 (회색)                                   │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   ▼ (action=tbm-checkin)
┌─────────────────────────────────────────────────────────────────────────┐
│  EDGE FUNCTION  notifications/index.ts (case 'tbm-checkin')             │
│   1. SELECT * FROM tbm_sessions WHERE session_id=$1                     │
│      AND ended_at IS NULL (404/410 if invalid)                          │
│   2. ownership: SELECT group_id FROM profiles WHERE user_id=$user_id    │
│      → must == session.group_id (T-7-03 spoofing 차단 패턴 미러)        │
│   3. INSERT tbm_participants (session_id, user_id, signature_url)       │
│      ON CONFLICT (session_id, user_id) DO NOTHING                       │
│      → if 0 rows → 200 idempotent "이미 참여"                           │
│   4. push-only (no notifications row, Corrections §C1)                   │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   ▼ (Realtime push)
┌─────────────────────────────────────────────────────────────────────────┐
│  MANAGER 대시보드 (TbmDashboardActivity)                                  │
│   - tbm_participants INSERT → 참여자 grid 즉시 갱신 (Realtime channel)   │
│   - 서명 thumbnail 클릭 → supabase.storage.from('tbm-signatures')        │
│       .createSignedUrl(path, 60.seconds) → Coil/Glide load               │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  SUPABASE POSTGRES (pg_cron 1분 주기, 별도 트랙)                          │
│                                                                         │
│  cron.schedule('tbm_missed_attendance_minute', '* * * * *', ...)        │
│   └─ tbm_missed_attendance_check() (SECURITY DEFINER)                   │
│       1. Vault SELECT sr_key + base_url (Phase 8 시드됨)                │
│          (둘 다 NULL → RAISE WARNING + RETURN)                           │
│       2. FOR session IN (                                                │
│            SELECT FROM tbm_sessions                                      │
│            WHERE expected_end_at + interval '30 min' < now()            │
│              AND missed_alert_at IS NULL                                 │
│              AND ended_at IS NULL                                        │
│          ) LOOP                                                          │
│            UPDATE tbm_sessions SET missed_alert_at = now()              │
│              WHERE session_id = r.session_id  -- D-09 1회 dedup          │
│            net.http_post(                                                │
│              url := base_url || '/notifications',                       │
│              headers := {Bearer sr_key, Content-Type: json},            │
│              body := { action:'tbm-missed', session_id, group_id,       │
│                        leader_user_id })                                 │
│          END LOOP;                                                       │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   ▼ (action=tbm-missed)
┌─────────────────────────────────────────────────────────────────────────┐
│  EDGE FUNCTION  notifications/index.ts (case 'tbm-missed')              │
│   1. 미참여 worker SELECT (D-04 SQL)                                    │
│   2. recipientIds = [...missedWorkerIds, leader_user_id]                │
│   3. sendPushToUsers(supabase, recipientIds, {                          │
│        data: { type:'tbm_alert', action_in_app:'tbm-missed',            │
│                session_id, missed_count } })                             │
│   4. push-only (no notifications row, no DB transition —                 │
│      missed_alert_at 은 이미 cron 함수가 UPDATE 했음, 회귀 가드 D-09)   │
└─────────────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
supabase/
├── migrations/
│   └── 013_tbm_schema.sql                ← NEW (4 테이블 + RLS + Realtime publication
│                                             + tbm-signatures bucket + INSERT/SELECT policies
│                                             + tbm_missed_attendance_check() + cron schedule)
├── migrations/tests/sql/
│   └── test_013_tbm_isolation.sql        ← NEW (anon UPDATE 차단 / 5 templates 시드 /
│                                             cron 등록 / Vault graceful skip / UNIQUE 충돌)
└── functions/notifications/
    └── index.ts                           ← MODIFY (4 case 추가 안 switch)

app/src/main/java/com/example/smart_safety_management/
├── MyApp.kt                               ← MODIFY (SupabaseModule 의 storage install)
├── HomeActivity.kt                        ← MODIFY (TbmDashboardCard ComposeView 임베드 — manager 측 최초)
├── HomeWorkerActivity.kt                  ← MODIFY (TbmWorkerCard ComposeView, 워치 카드 아래)
├── TbmDashboardActivity.kt                ← NEW (Compose, manager only 가드)
├── TbmWorkerActivity.kt                   ← NEW (Compose, worker 측)
├── MyFirebaseMessagingService.kt          ← MODIFY (data.type='tbm_alert' 분기 1개 추가)
└── tbm/                                   ← NEW 패키지 (watch/ 1:1 미러 + signature)
    ├── SupabaseModule.kt                  ← (watch/SupabaseModule reuse 또는 storage install 추가)
    ├── TbmModels.kt                       ← (TbmSessionRow, TbmTemplateRow, TbmChecklistRow, TbmParticipantRow)
    ├── TbmRepository.kt                   ← (4채널 Realtime — manager / worker variants)
    ├── TbmDashboardCardComposable.kt      ← (manager HomeActivity 카드 — 3줄 상태/카운트/알림)
    ├── TbmWorkerCardComposable.kt         ← (worker HomeWorkerActivity 카드 — 4 상태)
    ├── TbmDashboardScreen.kt              ← (TbmDashboardActivity 본체)
    ├── TbmWorkerScreen.kt                 ← (TbmWorkerActivity 본체)
    ├── SignatureCanvas.kt                 ← NEW (Compose Canvas Path 누적 + drawPath
    │                                          + Canvas → ImageBitmap → PNG ByteArray)
    ├── TbmStartSection.kt                 ← (manager 작업유형 선택 + expected_end_at)
    ├── WorkTypeValidator.kt               ← (5종 enum 검증 — 단위 테스트 대상)
    ├── TbmParticipantsReducer.kt          ← (Realtime INSERT/UPDATE 리듀서 — Phase 7 SafetyAlertReducer 패턴)
    ├── TbmRetrofitApi.kt                  ← (4 action POST helpers)
    └── (tests/) TbmRepositoryTest.kt + WorkTypeValidatorTest.kt + SignatureCanvasTest.kt
```

### Pattern 1: Compose Canvas signature pad → PNG ByteArray

**What:** Path 누적 + drawPath + ImageBitmap 오프스크린 렌더 + Bitmap.compress PNG.

**When to use:** `TbmWorkerActivity` 의 서명 영역.

**Example:**

```kotlin
// SignatureCanvas.kt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import java.io.ByteArrayOutputStream

/**
 * Phase 9 / D-03 — 수기 서명 캔버스. Path 누적 + drawPath.
 * 상태 = paths (List<Path>). 호출자가 toPngBytes() 로 export.
 */
class SignatureState {
    val paths: MutableList<Path> = mutableStateListOf()
    var currentPath: Path? by mutableStateOf(null)
    var canvasSize: IntSize by mutableStateOf(IntSize.Zero)
    val isEmpty: Boolean get() = paths.isEmpty() && currentPath == null

    fun clear() { paths.clear(); currentPath = null }

    /**
     * 오프스크린 ImageBitmap 으로 paths 재렌더 → android.graphics.Bitmap → PNG bytes.
     * canvasSize 가 0 이면 빈 byte array (호출자가 isEmpty 체크 후 호출).
     */
    fun toPngBytes(strokeColor: Color = Color.Black, strokeWidthPx: Float = 8f): ByteArray {
        if (canvasSize == IntSize.Zero) return ByteArray(0)
        val imageBitmap = ImageBitmap(canvasSize.width, canvasSize.height)
        val drawScope = CanvasDrawScope()
        drawScope.draw(
            density = androidx.compose.ui.unit.Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = androidx.compose.ui.graphics.Canvas(imageBitmap),
            size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        ) {
            drawRect(Color.White)  // 흰 배경
            paths.forEach { drawPath(it, color = strokeColor, style = Stroke(strokeWidthPx)) }
        }
        val androidBitmap: Bitmap = imageBitmap.asAndroidBitmap()
        val baos = ByteArrayOutputStream()
        try {
            androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            return baos.toByteArray()
        } finally {
            // imageBitmap 의 underlying Bitmap 은 GC 대상이지만, 보수적으로 recycle (Pitfall 1).
            if (!androidBitmap.isRecycled) androidBitmap.recycle()
            baos.close()
        }
    }
}

@Composable
fun SignatureCanvas(
    state: SignatureState,
    modifier: Modifier = Modifier,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface,
    strokeWidthDp: Dp = 4.dp,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidthDp.toPx() }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val p = Path().apply { moveTo(offset.x, offset.y) }
                        state.currentPath = p
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        state.currentPath?.lineTo(change.position.x, change.position.y)
                        // mutableStateOf<Path?> 의 setter 트리거 (path object 변경 감지 X — 강제)
                        state.currentPath = state.currentPath
                    },
                    onDragEnd = {
                        state.currentPath?.let { state.paths.add(it) }
                        state.currentPath = null
                    },
                )
            }
            .onSizeChanged { state.canvasSize = it }
    ) {
        state.paths.forEach { drawPath(it, color = strokeColor, style = Stroke(strokeWidthPx)) }
        state.currentPath?.let { drawPath(it, color = strokeColor, style = Stroke(strokeWidthPx)) }
    }
}
```

**핵심 메모리 안전성:**
- `CanvasDrawScope` 오프스크린 렌더 = Compose 표준 API (developer.android.com/develop/ui/compose/graphics/draw/overview).
- `androidBitmap.recycle()` finally 블록에서 명시적 호출 → API 24~28 에서 native heap 즉시 회수.
- `paths` 는 `mutableStateListOf` → Composition 종료 시 GC.
- `ByteArrayOutputStream.close()` → 200dp × 화면너비 × 4 byte ARGB ≈ 수백 KB, PNG 압축 후 ~10 KB.

[Source: developer.android.com/develop/ui/compose/graphics/draw/overview (CanvasDrawScope) + Medium "How to Create a Signature Draw View in Jetpack Compose and Save It as a Drawable" 2024 + Compose Canvas 표준 API]

### Pattern 2: Dynamic session_id Realtime — 2단계 구독

**What:** Phase 7 의 `WatchRealtimeRepository` 와 달리, `session_id` 는 dynamic — 세션 시작 후에야 알 수 있음. 2단계 구독 패턴.

**Why architecturally different from Phase 7:** Phase 7 의 `deviceId` 는 Activity 시작 시 PostgREST 1회 query (`SELECT FROM devices WHERE user_id=...`) 로 즉시 알 수 있고 변하지 않음. Phase 9 의 `session_id` 는 (a) 오늘 세션이 없을 수 있음 (NULL), (b) 시작/종료에 따라 변함, (c) checklists/participants 의 filter 가 동적이어야 함.

**When to use:** `TbmWorkerRepository` 와 `TbmManagerRepository` 모두.

**Example:**

```kotlin
// TbmRepository.kt — Worker 측 2단계 구독
class TbmWorkerRepository(private val supabase: SupabaseClient) {

    /** Stage A: group_id 로 오늘 세션 구독 (session_id 미상 상태). */
    fun todaySessionFlow(groupId: Int): Flow<TbmSessionRow?> = flow {
        // 초기 fetch — 오늘 세션 존재 여부
        val today = LocalDate.now().toString()  // ISO "2026-05-18"
        val initial = supabase.from("tbm_sessions").select {
            filter {
                eq("group_id", groupId)
                eq("session_date", today)
            }
        }.decodeSingleOrNull<TbmSessionRow>()
        emit(initial)
        var current = initial

        val channel = supabase.channel("tbm_sessions:group_$groupId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "tbm_sessions"
            filter("group_id", FilterOperator.EQ, groupId)
        }
        channel.subscribe()
        try {
            changes.collect { action ->
                val row = when (action) {
                    is PostgresAction.Insert -> action.decodeRecord<TbmSessionRow>()
                    is PostgresAction.Update -> action.decodeRecord<TbmSessionRow>()
                    is PostgresAction.Delete -> null
                    else -> current
                }
                // 오늘 날짜 + 종료 안 됨 = active
                if (row?.sessionDate == today) {
                    current = row
                    emit(row)
                } else if (row == null) {
                    current = null
                    emit(null)
                }
            }
        } finally {
            channel.unsubscribe()
        }
    }

    /** Stage B: session_id 가 정해진 후 participants 구독. */
    fun participantsFlow(sessionId: Long): Flow<List<TbmParticipantRow>> = flow {
        val initial = supabase.from("tbm_participants").select {
            filter { eq("session_id", sessionId) }
            order("signed_at", Order.ASCENDING)
        }.decodeList<TbmParticipantRow>()
        emit(initial)
        var current = initial

        val channel = supabase.channel("tbm_participants:session_$sessionId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "tbm_participants"
            filter("session_id", FilterOperator.EQ, sessionId)
        }
        channel.subscribe()
        try {
            changes.collect { action ->
                current = TbmParticipantsReducer.apply(current, action)
                emit(current)
            }
        } finally {
            channel.unsubscribe()
        }
    }

    /** Stage B: session_id 가 정해진 후 checklist 구독. */
    fun checklistsFlow(sessionId: Long): Flow<List<TbmChecklistRow>> = flow {
        // 동일 패턴 — channel.postgresChangeFlow + filter("session_id", EQ, sessionId)
        // ... (생략, Phase 7 SafetyAlertReducer 패턴 미러)
    }
}

// TbmWorkerScreen 안에서 2단계 collect
@Composable
fun TbmWorkerScreen(groupId: Int, supabase: SupabaseClient) {
    val repo = remember { TbmWorkerRepository(supabase) }
    val session by repo.todaySessionFlow(groupId).collectAsState(initial = null)

    if (session == null) {
        // "오늘 TBM 미시작" UI
        return
    }

    val sid = session!!.sessionId
    val participants by repo.participantsFlow(sid).collectAsState(initial = emptyList())
    val checklist by repo.checklistsFlow(sid).collectAsState(initial = emptyList())

    // ... TBM UI
}
```

**lifecycle 안전성:**
- 각 `flow { ... }` 는 collector cancellation 시 finally 의 `channel.unsubscribe()` 호출 (Phase 7 `WatchRealtimeRepository:48-51` 검증된 패턴).
- Stage A 구독 (sessions) 은 화면 전체 lifecycle 에 묶임.
- Stage B 구독 (participants/checklists) 은 `collectAsState(sid)` recomposition 으로 sid 변경 시 자동 cancel + 재시작 (LaunchedEffect 패턴).
- 채널 leak 방지: Stage B 의 sid 가 null → flow 자체가 emit 안 됨 (Stage A null 분기).

[Source: Phase 7 `WatchRealtimeRepository.kt:31-103` 검증 패턴 + supabase-kt 2.2.0 `RealtimeChannel.kt`]

### Pattern 3: supabase-kt storage 업로드 + signed URL

**What:** Compose Canvas PNG ByteArray → `tbm-signatures` 버킷 업로드 → 키 반환 → manager 측에서 signed URL 생성.

**When to use:** Worker `tbm-checkin` 직전 (업로드), Manager 대시보드 (signed URL load).

**Example (Worker 측 업로드):**

```kotlin
// TbmWorkerScreen.kt 내부
import io.github.jan.supabase.storage.storage
import java.time.Instant
import java.time.format.DateTimeFormatter

suspend fun uploadSignatureAndCheckin(
    supabase: SupabaseClient,
    sessionId: Long,
    userId: String,
    signatureBytes: ByteArray,
): Result<String> = runCatching {
    require(signatureBytes.isNotEmpty()) { "signature is empty" }

    val ts = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(java.time.ZoneOffset.UTC).format(Instant.now())
    val path = "$sessionId/${userId}_$ts.png"

    // 1. Storage 업로드 (anon key — 정책이 anon INSERT 허용해야 함; D-03 에 따라
    //    service_role 만 허용 시 Edge Function 우회 필요. Corrections §C3 참조)
    supabase.storage.from("tbm-signatures").upload(
        path = path,
        data = signatureBytes,
        upsert = false
    )

    // 2. Edge Function 호출
    val response = RetrofitClient.instance.callNotifications(
        TbmCheckinRequest(
            action = "tbm-checkin",
            session_id = sessionId,
            user_id = userId,
            signature_url = path  // Storage 키 (URL 아닌 path) — D-03 명시
        )
    )
    if (!response.isSuccessful) {
        throw IllegalStateException("checkin failed: ${response.errorBody()?.string()}")
    }
    path
}
```

**Example (Manager 측 signed URL load):**

```kotlin
// TbmDashboardScreen.kt — 서명 thumbnail 클릭
import kotlin.time.Duration.Companion.seconds

suspend fun signedUrlForSignature(
    supabase: SupabaseClient,
    path: String,
): String = supabase.storage.from("tbm-signatures")
    .createSignedUrl(path = path, expiresIn = 60.seconds)
```

**핵심:** signed URL 은 60s 만료 — UI 가 표시 시점에 새로 발급. Coil 이미지 캐시는 URL 자체를 키로 하므로 만료된 URL 도 다시 fetch 되지 않을 위험 → Coil 의 `disk_cache_policy = DISABLED` 또는 cache key override 권장 (Plan 3 의 detail).

[Source: supabase.com/docs/reference/kotlin/storage-from-upload + supabase.com/docs/reference/kotlin/storage-from-createsignedurl + Maven GAV 2.2.0 검증]

### Pattern 4: Edge Function 4 case (action-routing 확장)

**What:** `notifications/index.ts` 의 switch 안에 4 case 추가. Phase 7 `watch-pair` + Phase 8 `camera-down` 패턴 union.

**When to use:** D-08 4 actions.

**Skeleton (`tbm-start`):**

```typescript
// supabase/functions/notifications/index.ts — 기존 switch 안
case "tbm-start": {
  const { leader_user_id, group_id, work_type, expected_end_at, location, notes } = body;
  if (!leader_user_id || !group_id || !work_type || !expected_end_at) {
    return err("leader_user_id, group_id, work_type, expected_end_at are required");
  }
  // 1. 작업유형 검증 + checklist 조회
  const { data: tmpl, error: tErr } = await supabase
    .from("tbm_templates").select("checklist, title")
    .eq("work_type", work_type).maybeSingle();
  if (tErr) return err(tErr.message, 500);
  if (!tmpl) return err(`unknown work_type: ${work_type}`, 400);

  // 2. session insert (UNIQUE (group_id, session_date) 충돌 → 23505 → 409)
  const { data: session, error: sErr } = await supabase
    .from("tbm_sessions").insert({
      group_id, session_date: new Date().toISOString().slice(0, 10),
      expected_end_at, leader_user_id, work_type,
      location: location ?? null, notes: notes ?? null,
    }).select().maybeSingle();
  if (sErr) {
    if (sErr.code === "23505") return err("이미 오늘 세션이 존재합니다", 409);
    return err(sErr.message, 500);
  }

  // 3. checklists bulk insert
  const items = (tmpl.checklist as string[]).map((text, idx) => ({
    session_id: session!.session_id, item_idx: idx, item_text: text,
  }));
  const { error: cErr } = await supabase.from("tbm_checklists").insert(items);
  if (cErr) return err(cErr.message, 500);

  // 4. group worker 전원 SELECT (leader 제외)
  const { data: workers, error: wErr } = await supabase
    .from("profiles").select("user_id")
    .eq("group_id", group_id)
    .in("user_role", ["worker", "general_manager"])
    .neq("user_id", leader_user_id);
  if (wErr) return err(wErr.message, 500);

  const workerIds = (workers ?? []).map((w) => w.user_id);
  const r = await sendPushToUsers(supabase, workerIds, {
    title: "TBM 세션 시작",
    body: `${tmpl.title} — ${workerIds.length}명 대상`,
    data: { type: "tbm_alert", action_in_app: "tbm-started",
            session_id: String(session!.session_id), work_type },
  });
  return ok({ ok: true, session_id: session!.session_id,
              checklist_count: items.length, notified_count: r.sent });
}
```

**`tbm-checkin` ownership 검증 (Phase 7 T-7-03 미러):**

```typescript
case "tbm-checkin": {
  const { session_id, user_id, signature_url } = body;
  if (!session_id || !user_id) return err("session_id, user_id are required");

  // 1. session 유효성 (ended_at NULL)
  const { data: session, error: sErr } = await supabase
    .from("tbm_sessions").select("session_id, group_id, ended_at")
    .eq("session_id", session_id).maybeSingle();
  if (sErr) return err(sErr.message, 500);
  if (!session) return err("session not found", 404);
  if (session.ended_at) return err("session already ended", 410);

  // 2. ownership 검증 (T-7-03 spoofing 차단 — Phase 7 watch-pair 패턴 미러)
  const { data: profile, error: pErr } = await supabase
    .from("profiles").select("group_id").eq("user_id", user_id).maybeSingle();
  if (pErr) return err(pErr.message, 500);
  if (!profile) return err("user not found", 404);
  if (profile.group_id !== session.group_id) {
    return err("user not in session group", 403);
  }

  // 3. participant insert (idempotent on UNIQUE (session_id, user_id))
  const { data: p, error: insErr } = await supabase
    .from("tbm_participants").insert({
      session_id, user_id, signature_url: signature_url ?? null, method: "signature",
    }).select().maybeSingle();
  if (insErr) {
    if (insErr.code === "23505") {
      // 이미 참여 — idempotent 200
      const { data: existing } = await supabase
        .from("tbm_participants")
        .select("participant_id, signed_at")
        .eq("session_id", session_id).eq("user_id", user_id).maybeSingle();
      return ok({ ok: true, participant_id: existing?.participant_id,
                  signed_at: existing?.signed_at, idempotent: true });
    }
    return err(insErr.message, 500);
  }
  return ok({ ok: true, participant_id: p!.participant_id, signed_at: p!.signed_at });
}
```

**`tbm-missed` (Phase 8 camera-down 미러):**

```typescript
case "tbm-missed": {
  const { session_id, group_id, leader_user_id } = body;
  if (!session_id || group_id === undefined || group_id === null) {
    return err("session_id, group_id are required");
  }

  // 미참여 worker (D-04 SQL)
  const { data: missed, error: mErr } = await supabase.rpc("tbm_missed_workers", {
    p_session_id: session_id, p_group_id: group_id, p_leader_user_id: leader_user_id,
  });
  // 또는 직접 query — manager 결정 (RPC 가 더 안전).
  if (mErr) return err(mErr.message, 500);

  const missedIds = (missed ?? []).map((m: { user_id: string }) => m.user_id);
  const recipientIds = [...missedIds, leader_user_id];  // D-05 leader 포함

  const r = await sendPushToUsers(supabase, recipientIds, {
    title: "TBM 미참여 작업자 알림",
    body: `예정 종료 + 30분 — ${missedIds.length}명 미참여`,
    data: { type: "tbm_alert", action_in_app: "tbm-missed",
            session_id: String(session_id), missed_count: String(missedIds.length) },
  });
  return ok({ ok: true, missed_count: missedIds.length, notified_count: r.sent });
}
```

**`tbm-end` (단순):**

```typescript
case "tbm-end": {
  const { session_id, leader_user_id } = body;
  if (!session_id || !leader_user_id) return err("session_id, leader_user_id required");

  const { data, error } = await supabase
    .from("tbm_sessions")
    .update({ ended_at: new Date().toISOString() })
    .eq("session_id", session_id)
    .eq("leader_user_id", leader_user_id)
    .is("ended_at", null)
    .select().maybeSingle();
  if (error) return err(error.message, 500);
  if (!data) return err("session not found or already ended or not led by user", 404);

  const { count } = await supabase
    .from("tbm_participants").select("*", { count: "exact", head: true })
    .eq("session_id", session_id);
  return ok({ ok: true, ended_at: data.ended_at, participant_count: count ?? 0 });
}
```

[Source: 기존 `supabase/functions/notifications/index.ts:243-337` (watch-pair) + `:353-436` (camera-down/recovered) 패턴 + Phase 7 T-7-03 mitigation]

### Pattern 5: Storage 버킷 — private + service_role write + anon SELECT (signed URL only)

**What:** 본 코드베이스 최초 private bucket — 004_storage.sql 의 4 버킷은 모두 `public=true`. 신규 RLS 정책 템플릿.

**When to use:** `tbm-signatures` 버킷 (수기 서명 PII).

**Example (013 마이그 안 또는 별도 SQL):**

```sql
-- ─── tbm-signatures bucket (private + service_role write + signed URL read) ───
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'tbm-signatures',
  'tbm-signatures',
  false,            -- public=false → 직접 GET 차단, signed URL 필수
  524288,           -- 512KB (PNG 작음)
  ARRAY['image/png']
) ON CONFLICT (id) DO NOTHING;

-- INSERT: service_role only (Edge Function 또는 Worker 클라이언트가 anon key 사용 시 거부)
-- ⚠ Corrections §C3 — Worker 가 직접 storage 업로드 시 RLS 통과 못함. 옵션:
--   Option A: anon INSERT 허용 (PII 우려, 검증 1회 짚어야 함)
--   Option B: Edge Function 경유 (signature bytes 를 multipart 또는 base64 로 Edge 에 전송 후 service_role 업로드)
-- Phase 9 권장 = Option A (단순, 5월 PPT 데모 + 6월 현장 즉시성) + bucket_id + key prefix 가드.
CREATE POLICY "tbm_signatures_insert_anon"
  ON storage.objects FOR INSERT
  TO anon, authenticated
  WITH CHECK (
    bucket_id = 'tbm-signatures'
    AND (storage.foldername(name))[1] ~ '^[0-9]+$'  -- {session_id}/ prefix 강제
    AND lower(right(name, 4)) = '.png'              -- 확장자 강제
  );

-- SELECT: signed URL 발급은 service_role 권한 (createSignedUrl 가 내부에서 service_role JWT 발급).
-- anon 직접 SELECT 차단 = 정책 미등록 (RLS 가 default deny).
-- (즉 SELECT 정책 DROP/없음 = anon 차단 = bucket public=false 와 일관)
```

⚠ **Option A vs Option B 선택 (Corrections §C3):**
- **Option A** (현 권장, 단순): anon INSERT 허용 + bucket_id + key prefix 가드. PII 위험은 (a) 인증된 worker app 만 anon key 접근, (b) key prefix 가 `{session_id}/` 강제로 임의 경로 차단.
- **Option B** (강건): Edge Function `tbm-checkin` 이 multipart body 로 PNG 수신 → service_role 로 storage upload + tbm_participants insert atomically. 단점: Edge Function 크기 ↑, deno multipart parsing.
- v1.0 = Option A 채택, v1.1 = Option B 마이그.

[Source: supabase.com/docs/guides/storage/security/access-control + Maven GAV 2.2.0 storage-kt API + 004_storage.sql 패턴]

### Anti-Patterns to Avoid

- **`session_id` 를 Activity Intent extras 로만 신뢰** — Phase 7 D-02 anti-pattern. FCM extras 는 session_id 만 신뢰 + DB 재조회 (ended_at, group_id ownership).
- **Realtime channel 을 Activity onCreate/onDestroy 에 묶음** — Configuration change 시 leak. Compose flow finally 패턴 (Phase 7 검증).
- **클라이언트가 signature_url 을 임의로 보냄** — Edge Function 이 storage 의 object 존재 검증 X. Option B 가 안전하지만 v1.0 = key prefix 가드 + UNIQUE (session_id, user_id) 충돌로 abuse 제한.
- **storage public=true 로 단순화** — PII (수기 서명). v1.0 한정 단축 데모라도 절대 X. private + signed URL 의무.
- **tbm_templates 채널 구독** — seed-only 데이터. v1.0 변경 안 됨. 구독 비용 낭비. **3 채널만 권장** (Corrections §C2).
- **D-04 SQL 의 leader_user_id 제외 생략** — `user_role IN ('worker','general_manager')` 가 manager 를 자연 제외하지만, general_manager 가 leader 인 변종 가능 → defensive `!= leader_user_id` 유지.
- **Compose Canvas Path 를 immutable 로 가정** — Path 는 mutable, mutableStateOf<Path?> 의 setter 트리거 위해 `state.currentPath = state.currentPath` 강제 필요 (Pattern 1 코드 참조).
- **Bitmap.recycle() 생략** — Pitfall 1 참조.
- **세션 종료 후 missed_alert_at reset** — D-09 알림 전이 원칙 위반. 같은 session 의 missed_alert 는 영구 1회 (다음 날 = 다른 session = 다른 session_id 가 자연 dedup).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 수기 서명 영역 | XML View + 자체 GestureDetector | Compose Canvas + Path + detectDragGestures | Compose 표준 API, M3 native, lifecycle = state |
| Canvas → PNG | manual Bitmap.createBitmap + Canvas (View) | `CanvasDrawScope` 오프스크린 + `imageBitmap.asAndroidBitmap()` + `bitmap.compress(PNG)` | Compose 공식 API (developer.android.com), reflection 없음 |
| Storage 업로드 | Retrofit POST `/storage/v1/object/...` | `supabase.storage.from(bucket).upload(path, bytes)` | Maven GAV 2.2.0 ABI 호환 검증, retry + multipart 표준화 |
| Signed URL | 자체 HMAC-SHA256 + JWT 서명 | `supabase.storage.from(bucket).createSignedUrl(path, expiresIn)` | service-side 서명, expiry 검증 표준 |
| pg_cron 1분 healthcheck | systemd timer + Python script | `cron.schedule(...)` + SECURITY DEFINER plpgsql | Postgres 내부, agent 죽어도 실행. Phase 8 012 검증 |
| HTTP from SQL | trigger + Edge Function poller | `net.http_post()` (pg_net) | Supabase 표준, async, Phase 8 검증 |
| service_role key 보관 | env var, postgresql.conf GUC | Supabase Vault (`vault.decrypted_secrets`) | dashboard managed, 암호화, Phase 8 시드 재사용 |
| Multi-recipient FCM | for loop sendPushToUser × N | `sendPushToUsers(supabase, ids, payload)` | `_shared/fcm.ts:239` Promise.allSettled 검증 |
| MAC 정규식 (Phase 7) | 자체 파서 | `^([0-9A-F]{2}:){5}[0-9A-F]{2}$` | Phase 9 무관 — TBM 은 MAC 없음. (Worker validator 는 5종 work_type enum) |
| UNIQUE 충돌 응답 | 자체 SELECT 먼저 + INSERT race | INSERT + 23505 → 409 또는 200 idempotent | Phase 7 watch-pair `existing` 패턴 적용 가능, 단 race window 작아서 23505 catch 가 더 안전 |

**Key insight:** Phase 9 신규 작성 = (a) Compose SignatureCanvas ~80 라인, (b) 013_tbm_schema.sql ~250 라인 (4 테이블 + bucket + RLS + cron + 시드), (c) Edge Function 4 case ~250 라인, (d) Android tbm/ 패키지 ~700 라인 (Activity 2 + Compose 5 + Repository + Models). **총 ~1,300 라인 신규**. CONTEXT.md "가벼운 통합" 정신 충족.

## Runtime State Inventory

> Phase 9 는 신규 테이블 + 신규 cron + 신규 storage 버킷 + 신규 Edge Function action. Rename/refactor 아님. 일부 카테고리만 적용.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | (a) **profiles 시드 부족** — group_id=1 에 testuser1 manager 외 worker 2~3명 시드 필요 (CONTEXT `<canonical_refs>` 명시). (b) `tbm-signatures` 버킷의 PNG 파일은 신규 데이터. v1.0 PoC 한정 retention 정책 부재 (v1.1 cleanup cron 검토). | (a) Plan 1 Wave 0 첫 단계 = SQL 또는 Python script 로 worker 시드 (testuser_w1/w2/w3). (b) v1.0 retention X — 별도 cron 안 등록. v1.1 30일 자동 삭제 검토. |
| Live service config | 없음 — Phase 9 는 Supabase managed (cron + Edge Function + Storage 모두 Supabase). | 없음. |
| OS-registered state | 없음 — Android 앱 신규 화면 2개 + 카드 2개만 추가. | 없음. |
| Secrets/env vars | Vault `service_role_key` + `edge_function_base_url` 은 Phase 8 dashboard 시드 완료 (재사용). FCM_SERVICE_ACCOUNT + FCM_PROJECT_ID 도 Phase 4 시드됨. **신규 Vault entry 0개**. | 없음. 단 SUMMARY 에 prerequisite 명시 — Vault 미시드 환경에서 cron silent skip (graceful, Phase 8 검증). |
| Build artifacts | 신규 `gotrue-kt:2.2.0` + `ktor-server-core/cio:2.3.9` + `multiplatform-settings:1.1.1` 의존성 자동 다운로드 (Gradle resolve). Phase 7 빌드 캐시 invalidate 1회. | `./gradlew app:dependencies` 로 resolve 검증. Plan 3 Wave 0 첫 단계. |

**중요:** profiles worker 시드 (Plan 1 Wave 0) 가 없으면 (a) `tbm-start` 가 0명 push (no error 지만 의미 없음), (b) `tbm-missed` 도 0명 대상 — 데모 시연 불가. **시드 강제 prerequisite**.

## Common Pitfalls

### Pitfall 1: Compose Canvas Bitmap memory leak (recycle 누락)

**What goes wrong:** `imageBitmap.asAndroidBitmap()` 가 반환한 Bitmap 의 underlying native heap (Android < P 에서는 native memory pool) 가 GC 안 됨 → 반복 서명 시 OOM.
**Why it happens:** Compose `ImageBitmap` 의 lifecycle 은 Composition GC 와 묶이지만, 명시적 `recycle()` 호출 권장. Android API 28+ 는 ART managed, < 28 은 native pool.
**How to avoid:** `toPngBytes()` 의 finally 블록에서 `if (!androidBitmap.isRecycled) androidBitmap.recycle()` (Pattern 1 코드 반영됨). minSdk 24 + desugar 환경에서 보수적으로 명시.
**Warning signs:** 반복 서명 후 LogCat `GC_FOR_ALLOC freed ... bitmap` 폭증. OOM crash.

### Pitfall 2: Compose Canvas Path 가 mutableStateOf 갱신 안 됨

**What goes wrong:** `state.currentPath?.lineTo(x, y)` 호출해도 Compose 가 변경 감지 X (Path object reference 동일).
**Why it happens:** `mutableStateOf<Path?>` 는 reference equality 기반. Path 내부 변경은 emit 안 됨.
**How to avoid:** drag 콜백 끝에 `state.currentPath = state.currentPath` (setter 강제 호출) — Pattern 1 코드 반영됨. 또는 매 drag 마다 `Path().apply { addPath(prev); lineTo(x, y) }` 신규 Path 생성 (메모리 부담).
**Warning signs:** 드래그 시 화면에 stroke 가 그려지지 않다가 손 떼면 한 번에 나타남.

### Pitfall 3: Storage 업로드가 RLS 거부 (anon)

**What goes wrong:** Worker 가 `supabase.storage.from('tbm-signatures').upload(...)` 호출 → 403 "row-level security policy violated".
**Why it happens:** Phase 9 의 신규 패턴 — Pattern 5 의 RLS 정책 `tbm_signatures_insert_anon` 가 등록 안 되었거나 prefix 조건 실패.
**How to avoid:** (a) 013 마이그 안 RLS 정책 등록 검증 (smoke 1: anon 으로 정상 path PNG 업로드 200 + 비정상 path 403). (b) Option B 채택 시 (Edge Function 경유) anon INSERT 정책 불필요.
**Warning signs:** Worker app 의 "참여 확인" 버튼 클릭 후 토스트 "Storage error: 403". `supabase.storage` 로그에 `policy denied`.

### Pitfall 4: signed URL 60s 만료 후 Coil 이미지 표시 안 됨

**What goes wrong:** Manager 가 서명 thumbnail 클릭 → 새 URL 발급 → 60s 후 화면 닫지 않고 보고 있으면 URL 만료. Coil 캐시는 URL 자체를 key 로 사용 → 재발급 안 함.
**Why it happens:** signed URL 의 expires 파라미터 60s, Coil DiskCache 가 expired URL 도 캐시 hit.
**How to avoid:** AsyncImage `key(System.currentTimeMillis() / 30000)` 같이 30s 주기로 cache invalidate. 또는 `disk_cache_policy = DISABLED`. 더 단순: thumbnail 클릭 시 모달 다이얼로그 → 닫으면 dispose → 다음 클릭 시 새 URL.
**Warning signs:** Manager 가 화면 오래 보면 thumbnail 영구 깨짐 (재진입 시 정상).

### Pitfall 5: UNIQUE (group_id, session_date) 23505 catch 실패

**What goes wrong:** Edge Function `tbm-start` 가 같은 날 두 번째 호출 시 unhandled exception 던지면 500 (요구사항은 409).
**Why it happens:** Supabase JS client 가 PostgrestError 의 `code` 필드 (`23505`) 를 정확히 노출 X — `.code` 또는 `.details` 확인 필요.
**How to avoid:** Pattern 4 `tbm-start` 코드의 `if (sErr.code === "23505") return err("이미 오늘 세션이 존재합니다", 409)` 검증. curl smoke 첫 시나리오로 검증 (manager 가 같은 날 두 번 시작).
**Warning signs:** 두 번째 시작 응답 status 500, body `duplicate key value violates unique constraint`.

### Pitfall 6: `tbm_missed_attendance_check` 의 Vault 미시드 silent skip

**What goes wrong:** Vault 의 `service_role_key` 또는 `edge_function_base_url` 미시드 → 함수가 RAISE WARNING + RETURN → 알림 영구 발사 안 됨. cron logs 만 보지 않으면 발견 어려움.
**Why it happens:** Phase 8 dashboard 수동 시드 prerequisite. 본 phase 도 동일 secret 재사용 (신규 시드 0개).
**How to avoid:** (a) Phase 8 04-04-SUMMARY 의 Vault 시드 절차가 완료된 환경 가정. (b) 013 마이그 적용 후 첫 cron tick 의 net.http_post 결과를 `net._http_response` 에서 확인 (smoke 2). (c) 함수 본문 LOG 추가 (`RAISE NOTICE 'tbm_missed: sr_key_present=%, ...'`).
**Warning signs:** 09:45 시점에 알림 영구 0건. Supabase Logs → "vault secret missing".

### Pitfall 7: Realtime publication 미등록

**What goes wrong:** Worker 가 체크인했는데 manager 대시보드의 참여자 grid 안 갱신. 채널 status = SUBSCRIBED.
**Why it happens:** `supabase_realtime` publication 에 4 신규 테이블 자동 추가 X.
**How to avoid:** 013 마이그 안 `ALTER PUBLICATION supabase_realtime ADD TABLE public.tbm_sessions, ...` (Phase 7 011 패턴, EXCEPTION duplicate_object 흡수). smoke 3 = INSERT 후 채널 메시지 도착 검증.
**Warning signs:** SDK 로그 `SUBSCRIBED ✓`, 하지만 INSERT 직후 메시지 0건. Postgres `SELECT * FROM pg_publication_tables WHERE pubname='supabase_realtime' AND tablename LIKE 'tbm_%'` 가 0행.

### Pitfall 8: timezone — expected_end_at TIMESTAMPTZ 일관성

**What goes wrong:** Android 가 `LocalDateTime.now()` (no offset) 전송 → server interprets as UTC → 9시간 차이 → 09:15 입력했는데 18:15 로 저장 → cron 이 18:45 까지 안 깨움.
**Why it happens:** TIMESTAMPTZ 가 입력 타임존 부재 시 UTC 가정 (Phase 4 010 의 UTC immutability 교훈).
**How to avoid:** Android 측 `ZonedDateTime.now(ZoneId.of("Asia/Seoul"))` + `DateTimeFormatter.ISO_OFFSET_DATE_TIME` 으로 `2026-05-18T09:15:00+09:00` 형식 전송. Edge Function 은 그대로 PostgREST 에 패스 (Postgres 가 정확히 UTC 변환).
**Warning signs:** Manager 가 09:15 입력했는데 DB 확인 시 `expected_end_at = 2026-05-18 00:15:00+00`. cron 이 09:45 발사 안 함.

### Pitfall 9: leader 가 자기 자신에게 push 받음

**What goes wrong:** `tbm-missed` 의 recipientIds = `[...missedIds, leader_user_id]` — leader 가 missedIds 에도 포함되면 중복 push. 또한 leader 가 자기 자신에게 "TBM 미참여" 알림 받으면 UX 어색.
**Why it happens:** D-04 SQL 의 `!= leader_user_id` 가드가 missed worker 산출에서 leader 자연 제외 → recipientIds 에서 중복 X. 단 `sendPushToUsers` 가 내부 dedup `[...new Set(userIds)]` 보장 (fcm.ts:253 verified).
**How to avoid:** ✓ 이미 안전. `sendPushToUsers` 가 unique 보장 + D-04 SQL 가 leader 자연 제외. 단 leader 본인이 미참여인 변종 (general_manager 변경) 검증 — Plan 2 의 smoke 4.
**Warning signs:** Leader 가 missed 알림 2건 받음. 또는 leader 본인이 missedIds 에 포함된 경우 (D-04 SQL 검증 실패).

### Pitfall 10: gotrue-kt 신규 transitive — Ktor server 모듈 충돌

**What goes wrong:** storage-kt 2.2.0 가 가져오는 `gotrue-kt:2.2.0` 이 `ktor-server-core:2.3.9` + `ktor-server-cio:2.3.9` 신규 transitive — Ktor server 는 클라이언트 앱에 불필요한 클래스 + reflection 의존성 추가.
**Why it happens:** Maven GAV `gotrue-kt-2.2.0.module` 직접 검증 — ktor-server-* 명시 의존 [VERIFIED].
**How to avoid:** (a) ProGuard rules 에 `-dontwarn io.ktor.server.**` 추가, (b) R8 가 server-side 클래스 자동 strip (release build), (c) APK size 증가는 ~50 KB 수준 (gotrue + server 모듈) — acceptable.
**Warning signs:** `./gradlew app:dependencies | grep ktor-server` 가 2개 모듈 출력. release APK lint warnings.

### Pitfall 11: JSONB checklist array 순서 보장

**What goes wrong:** `tbm_templates.checklist::jsonb` 가 `["a","b","c"]` 인데 `tbm-start` 의 bulk insert 가 순서를 보장 못해서 `item_idx=0` 이 "c" 가 됨.
**Why it happens:** PostgreSQL JSONB array 는 순서 보존하지만, JS 의 `tmpl.checklist as string[]` cast 후 `.map((text, idx) => ...)` 가 안전.
**How to avoid:** ✓ JS Array 의 `.map((_, idx) => idx)` 가 결정적. 단 `tbm_checklists.item_idx` UNIQUE 제약이 race window 방지. Plan 2 smoke 5 = 5개 시드 작업유형의 item_text 와 item_idx 일치 검증.
**Warning signs:** 체크리스트 표시 순서가 사용자 직관과 다름. SQL `SELECT * FROM tbm_checklists WHERE session_id=X ORDER BY item_idx` 의 item_text 가 template.checklist 순서와 다름.

### Pitfall 12: HomeActivity 의 ComposeView 첫 임베드 — Theme 적용

**What goes wrong:** HomeActivity 는 AppCompatActivity + XML, Phase 9 D-06 가 첫 ComposeView 임베드 → ComposeView 내부에서 Material3 theme 미적용 (텍스트가 default Android 흑백).
**Why it happens:** ComposeView 는 부모 Activity 의 theme 자동 상속 안 함 — `composeView.setContent { Smart_Safety_ManagementTheme { ... } }` 명시 필요 (Phase 7 HomeWorkerActivity:361 검증).
**How to avoid:** Pattern 3 (HomeWorkerActivity) 의 `setupWatchCard` 1:1 미러 — `Smart_Safety_ManagementTheme { ... }` 래핑. Plan 3 Wave 2 의 작업.
**Warning signs:** HomeActivity 의 TBM 카드만 색상 어색, 다른 카드와 폰트 불일치.

## Code Examples

### Example A: WorkTypeValidator (단위 테스트 대상 — Phase 7 MacAddressValidator 미러)

```kotlin
// app/src/main/java/com/example/smart_safety_management/tbm/WorkTypeValidator.kt
object WorkTypeValidator {
    val ALLOWED: Set<String> = setOf("fire", "electric", "height", "heavy", "general")
    fun isValid(workType: String): Boolean = workType in ALLOWED
    fun normalize(input: String): String = input.lowercase().trim()
}

// tests/WorkTypeValidatorTest.kt — JUnit 5
class WorkTypeValidatorTest {
    @Test fun `valid fire`() = assertTrue(WorkTypeValidator.isValid("fire"))
    @Test fun `invalid empty`() = assertFalse(WorkTypeValidator.isValid(""))
    @Test fun `invalid unknown`() = assertFalse(WorkTypeValidator.isValid("welding"))
    @Test fun `normalize FIRE to fire`() =
        assertEquals("fire", WorkTypeValidator.normalize("FIRE "))
}
```

### Example B: TbmParticipantsReducer (Phase 7 SafetyAlertReducer 미러)

```kotlin
object TbmParticipantsReducer {
    fun apply(current: List<TbmParticipantRow>, action: PostgresAction): List<TbmParticipantRow> = when (action) {
        is PostgresAction.Insert -> {
            val row = action.decodeRecord<TbmParticipantRow>()
            if (current.any { it.participantId == row.participantId }) {
                current.map { if (it.participantId == row.participantId) row else it }
            } else {
                current + listOf(row)  // 참여 순서 = signed_at ASC, append
            }
        }
        is PostgresAction.Update -> {
            val u = action.decodeRecord<TbmParticipantRow>()
            current.map { if (it.participantId == u.participantId) u else it }
        }
        else -> current
    }

    /** Test-friendly variant. */
    fun applyDirect(current: List<TbmParticipantRow>, kind: ChangeKind, row: TbmParticipantRow): List<TbmParticipantRow> =
        when (kind) {
            ChangeKind.INSERT -> if (current.any { it.participantId == row.participantId }) {
                current.map { if (it.participantId == row.participantId) row else it }
            } else current + listOf(row)
            ChangeKind.UPDATE -> current.map { if (it.participantId == row.participantId) row else it }
            ChangeKind.DELETE -> current.filterNot { it.participantId == row.participantId }
        }
}
```

### Example C: SQL — D-04 미참여 worker RPC (Edge Function 에서 호출)

```sql
-- 013_tbm_schema.sql 안 또는 별도 파일
CREATE OR REPLACE FUNCTION public.tbm_missed_workers(
    p_session_id BIGINT,
    p_group_id INTEGER,
    p_leader_user_id VARCHAR(50)
) RETURNS TABLE(user_id VARCHAR(50), user_name VARCHAR(100))
LANGUAGE sql SECURITY DEFINER
SET search_path = public
AS $$
    SELECT p.user_id, p.user_name
    FROM public.profiles p
    WHERE p.group_id = p_group_id
      AND p.user_role IN ('worker', 'general_manager')
      AND p.user_id != p_leader_user_id
      AND NOT EXISTS (
          SELECT 1 FROM public.tbm_participants pt
          WHERE pt.session_id = p_session_id AND pt.user_id = p.user_id
      );
$$;
```

**인덱스 활용성:** `idx_tbm_participants_session ON (session_id)` (D-01 명시) — `NOT EXISTS` 의 inner SELECT 가 index seek. `profiles.group_id` 는 기존 인덱스 활용 (003 RLS 의 함수가 동일 컬럼 사용).

### Example D: 데모 PoC 시드 SQL (Wave 0)

```sql
-- scripts/seed_tbm_demo.sql — Plan 1 Wave 0 첫 단계
-- group_id=1 에 worker 3명 시드 (testuser1 manager 외)
-- profiles.user_id 가 auth.users.id 와 무관 (003 RLS 가 user_id 문자열 직접 사용)
-- → auth.users 시드 없이 profiles 만 가능. supabase-kt anon SELECT 도 정상.

INSERT INTO public.profiles (user_id, user_name, user_role, group_id, fcm_token)
VALUES
    ('testuser_w1', '작업자1', 'worker', 1, NULL),
    ('testuser_w2', '작업자2', 'worker', 1, NULL),
    ('testuser_w3', '작업자3', 'worker', 1, NULL)
ON CONFLICT (user_id) DO UPDATE
    SET user_name = EXCLUDED.user_name,
        user_role = EXCLUDED.user_role,
        group_id = EXCLUDED.group_id;
```

**대안 (Python script — testuser1 처럼 실제 FCM 토큰 등록 가능):** `scripts/seed_tbm_demo.py` — supabase-py 로 INSERT + FCM 토큰 발급 (테스트 단말 ↔ profiles 매핑). Phase 4 시드 패턴 미러.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| XML View `android.gesture.GestureOverlayView` + `Bitmap` | Compose `Canvas` + `Path` + `CanvasDrawScope.draw()` 오프스크린 | Compose 1.0 (2021) + drawscope API stable 1.5 (2023) | API 단순, Composition lifecycle, M3 native theming |
| FileProvider + content:// URI 로 file upload (Retrofit multipart) | supabase-kt `storage.from(bucket).upload(path, bytes)` | supabase-kt 1.0+ | retry/multipart 표준화, signed URL API 통합 |
| postgresql.conf GUC `app.service_role_key` | Supabase Vault `vault.decrypted_secrets` | Supabase 2024 | dashboard managed, 마이그 idempotent, 암호화 |
| Edge Function self-poll (cron-style) | pg_cron + `net.http_post()` (pg_net) | Supabase 2023 | async fire-and-forget, cold-start 회피 |
| `auth.uid()` RLS 강제 | Edge Function `service_role` + ownership 검증 코드 | 본 프로젝트 — Firebase Auth + UserSession (Supabase auth 미도입) | v1.0 PoC 잠정, v1.1 Supabase Auth 도입 시 `auth.uid()` 정책 강화 |

**Deprecated/outdated (Phase 9 영향):**
- supabase-kt 3.x — Kotlin 2.3.21 강제, Phase 7 + 9 의 Compose Compiler 1.5.10 + Kotlin 1.9.22 와 ABI 비호환 [VERIFIED: Maven GAV 직접 검증]. v1.x 에서 Kotlin 2.x bump 시 함께 마이그.

## Corrections (CONTEXT.md 정정 권장)

CONTEXT.md 의 9개 결정 모두 valid 하지만, **3개 영역에서 정정/명시 권장** — 단순 누락 (assumed 영역) 이지 결정 번복 X.

| # | CONTEXT 의 상태 | Research 권장 | 영향 |
|---|---------------|---------------|------|
| **C1** | D-08 의 4 case 모두 `notifications` row insert 정책 불명시 (silent) | `tbm-started` = INSERT + push (manager-triggered, Phase 7 watch-alert 패턴), `tbm-checkin`/`tbm-end`/`tbm-missed` = push-only (Phase 8 패턴) | (a) UI 알림 history (`SafetyAlertsActivity` 와 별도 TBM 알림 history 검토 — v1.0 = SKIP, v1.1+ 추가). (b) D-09 회귀 가드: tbm-missed 의 missed_alert_at UPDATE 책임은 cron 함수 → Edge Function 은 push-only 일관. **권장 = `tbm-started` 만 insert, 나머지 push-only**. 단순화 가능: 모두 push-only (v1.0 TBM 알림 history 미사용). planner 결정. |
| **C2** | D-01 의 Realtime publication = 4 테이블 모두 등록 | **3 테이블만 권장** (tbm_sessions / tbm_checklists / tbm_participants). tbm_templates 는 seed-only, manager 가 시작 시 1회 SELECT, 변경 reactive 불필요 | (a) 트래픽 미미 (5종 seed 의 변경 < 1회/year), (b) 4 채널 구독 = 채널 connection 1개 추가 — 비용 거의 없음. **단순성을 위해 4 모두 등록 가능** (마이그 후행 ALTER 비용 회피). planner 결정. |
| **C3** | D-03 의 `tbm-signatures` 버킷 — service_role write only 표기, 하지만 Worker 클라이언트가 anon 으로 어떻게 업로드할지 불명시 | **Option A** (권장, v1.0): anon INSERT 허용 + `bucket_id='tbm-signatures'` + key prefix `^[0-9]+$/.*\.png$` 가드. **Option B** (v1.1 강건): Edge Function `tbm-checkin` 이 multipart body 로 PNG 수신 → service_role 업로드 + atomic insert | Option A = 단순 + 5월 PPT 데모 + 6월 현장 즉시성. PII 위험 = anon key 유출 시 임의 path PNG 업로드 가능 (단 `{session_id}/` prefix 가드로 abuse 제한). Option B = Edge Function 크기 ↑ + deno multipart parsing. **권장 = Option A, v1.1 → Option B 마이그**. |

**기타 명시 권장 (정정 아님, 보강):**

- **D-04 SQL 의 `!= leader_user_id` redundancy:** `user_role IN ('worker','general_manager')` 가 manager (leader) 자연 제외하지만, *general_manager 가 leader 인 변종* (v1.1 부터 가능) 가능 → defensive 가드 유지 권장. CONTEXT.md 원본 그대로 유지.

- **HomeActivity ComposeView 첫 임베드 (manager 측):** Phase 7 은 HomeWorkerActivity (worker) 만 ComposeView 추가. Phase 9 D-06 가 HomeActivity (manager) 의 첫 ComposeView 임베드. Pitfall 12 의 Theme 래핑 강조 필요. CONTEXT 결정 변경 X — 단순 visibility.

- **Phase 8 의 Vault prerequisite 재사용:** 본 phase 신규 시드 0개. SUMMARY 의 "User Setup Required" 섹션 = "Phase 8 04-04-SUMMARY 의 Vault 시드 절차가 완료된 환경 전제" 1줄로 충분.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework (Android) | **JUnit 5 (Jupiter) 5.10.x** — Phase 7 Wave 4 의 `WatchRealtimeRepositoryTest` 등 검증 |
| Framework (SQL) | psql + custom assertion DO blocks — `tests/sql/test_012_cameras_health_isolation.sql` 패턴 |
| Framework (Edge Function) | bash + curl — Phase 8 04-smoke 패턴 |
| Config file (Android) | `app/build.gradle.kts` testOptions / testImplementation (기존) |
| Config file (SQL) | 없음 (per-file psql 직접 실행) |
| Quick run (Android unit) | `./gradlew :app:testDebugUnitTest --tests "*tbm*"` |
| Quick run (Android instrumented) | `./gradlew :app:connectedDebugAndroidTest --tests "*tbm*"` |
| Quick run (SQL) | `psql $DATABASE_URL -f supabase/migrations/tests/sql/test_013_tbm_isolation.sql` |
| Quick run (Edge curl smoke) | `bash scripts/smoke_tbm.sh` (Plan 2 산출물) |
| Full suite | `./gradlew test connectedAndroidTest` + SQL test 4종 + curl smoke 12종 |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TBM-01 | 4 테이블 + RLS + Realtime publication 생성 | SQL isolation | `psql $DATABASE_URL -f test_013_tbm_isolation.sql` | ❌ Wave 0 |
| TBM-01 | UNIQUE (group_id, session_date) 충돌 차단 | SQL isolation | (위 동일, smoke 안 포함) | ❌ Wave 0 |
| TBM-01 | RLS anon UPDATE 차단 | SQL isolation | (위 동일) | ❌ Wave 0 |
| TBM-01 | 5종 work_type 시드 검증 | SQL isolation | (위 동일) | ❌ Wave 0 |
| TBM-01 | cron job 'tbm_missed_attendance_minute' 등록 | SQL isolation | (위 동일) | ❌ Wave 0 |
| TBM-02 | WorkTypeValidator 5종 enum + normalize | unit | `./gradlew :app:testDebugUnitTest --tests "WorkTypeValidatorTest"` | ❌ Wave 0 |
| TBM-02 | TbmParticipantsReducer INSERT/UPDATE/DELETE | unit | `./gradlew :app:testDebugUnitTest --tests "TbmParticipantsReducerTest"` | ❌ Wave 0 |
| TBM-02 | SignatureState.toPngBytes() — paths 누적 + PNG 출력 검증 | unit | `./gradlew :app:testDebugUnitTest --tests "SignatureStateTest"` | ❌ Wave 0 |
| TBM-02 | Edge Function tbm-start UNIQUE 충돌 409 | curl smoke | `bash scripts/smoke_tbm.sh tbm-start-conflict` | ❌ Wave 0 |
| TBM-02 | Edge Function tbm-start 정상 시 N workers push | curl smoke | `bash scripts/smoke_tbm.sh tbm-start-happy` | ❌ Wave 0 |
| TBM-02 | Edge Function tbm-checkin ownership 검증 (다른 group 403) | curl smoke | `bash scripts/smoke_tbm.sh tbm-checkin-spoof` | ❌ Wave 0 |
| TBM-02 | Edge Function tbm-checkin UNIQUE 중복 idempotent 200 | curl smoke | `bash scripts/smoke_tbm.sh tbm-checkin-dup` | ❌ Wave 0 |
| TBM-02 | Edge Function tbm-end leader 검증 + ended_at | curl smoke | `bash scripts/smoke_tbm.sh tbm-end` | ❌ Wave 0 |
| TBM-02 | 1 cycle 캡처 (manager start → worker checkin → manager end) | manual instrumented | (CHANGELOG / SUMMARY 캡처) | Plan 3 Wave 4 manual |
| TBM-03 | tbm_missed_attendance_check 함수 graceful Vault skip | SQL smoke | `psql ... -c "SELECT public.tbm_missed_attendance_check();"` (Vault 미시드 환경) | ❌ Wave 0 |
| TBM-03 | tbm-missed Edge Function FCM 발송 (missed worker + leader) | curl smoke | `bash scripts/smoke_tbm.sh tbm-missed` | ❌ Wave 0 |
| TBM-03 | 1일 사이클 = manager 09:00 시작 expected_end_at=09:15 → 09:45 cron 발사 | manual instrumented | SUMMARY 캡처 (expected_end_at 을 90s 앞으로 시드 + 30s 후 cron tick 대기) | Plan 3 Wave 4 manual |
| SC #4 | daily_safety_check Activity / Adapter / RLS 무영향 | regression | `./gradlew :app:testDebugUnitTest && ./gradlew :app:connectedDebugAndroidTest` 전체 무회귀 | 기존 |

### Sampling Rate
- **Per task commit**: `./gradlew :app:testDebugUnitTest --tests "*tbm*"` (단위 < 30s)
- **Per wave merge**: SQL isolation + 단위 + curl smoke (Plan 2 산출 후)
- **Phase gate**: 전체 무회귀 + 1일 사이클 manual 캡처

### Wave 0 Gaps

- [ ] `supabase/migrations/013_tbm_schema.sql` — 4 테이블 + bucket + cron + 시드 + RLS (TBM-01)
- [ ] `supabase/migrations/tests/sql/test_013_tbm_isolation.sql` — 5 시나리오 (TBM-01)
- [ ] `app/src/test/java/com/example/smart_safety_management/tbm/WorkTypeValidatorTest.kt` (TBM-02)
- [ ] `app/src/test/java/com/example/smart_safety_management/tbm/TbmParticipantsReducerTest.kt` (TBM-02)
- [ ] `app/src/test/java/com/example/smart_safety_management/tbm/SignatureStateTest.kt` (TBM-02 — 메모리 누수 회귀 가드)
- [ ] `scripts/smoke_tbm.sh` — 12 curl 시나리오 (TBM-02·03)
- [ ] `scripts/seed_tbm_demo.sql` — group_id=1 worker 3명 시드 (Pitfall 데모 prerequisite)

## Sources

### Primary (HIGH confidence)

- **Maven Central GAV `storage-kt-2.2.0.module`** — `https://repo1.maven.org/maven2/io/github/jan-tennert/supabase/storage-kt/2.2.0/storage-kt-2.2.0.module` — `kotlin-stdlib.requires=1.9.22`, transitive `gotrue-kt:2.2.0` [VERIFIED: 직접 curl fetch 2026-05-18]
- **Maven Central GAV `gotrue-kt-2.2.0.module`** — 동일 — `kotlin-stdlib.requires=1.9.22`, `ktor-server-core/cio:2.3.9` 신규 transitive [VERIFIED: 직접 curl fetch 2026-05-18]
- **Phase 7 `WatchRealtimeRepository.kt`** — 3 채널 Realtime + Reducer 패턴 검증 (`app/src/main/java/com/example/smart_safety_management/watch/WatchRealtimeRepository.kt`) [VERIFIED: 직접 Read]
- **Phase 8 `012_cameras_health.sql`** — Vault + pg_cron + SECURITY DEFINER + EXCEPTION OTHERS 패턴 (1:1 미러 base) [VERIFIED: 직접 Read]
- **Phase 7 `011_watch_app_rls.sql`** — Realtime publication ADD + v1.0 RLS narrowing 패턴 [VERIFIED: 직접 Read]
- **Phase 8 `notifications/index.ts:243-436`** — watch-pair + camera-down/recovered 패턴 (4 case 추가 base) [VERIFIED: 직접 Read]
- **Phase 7 `HomeWorkerActivity.kt:48-60`** — ComposeView 임베드 + setupWatchCard 패턴 [VERIFIED: 직접 Read]
- **CONTEXT.md `<canonical_refs>` 8 + 17 + 18** — 의존성 + 시드 + 정책 출처

### Secondary (MEDIUM confidence)

- supabase.com/docs/reference/kotlin/storage-from-upload — Kotlin upload signature [CITED: WebSearch result + supabase-kt 2.2.0 sources jar API 일치]
- supabase.com/docs/reference/kotlin/storage-from-createsignedurl — createSignedUrl signature [CITED]
- supabase.com/docs/guides/storage/security/access-control — private bucket RLS 패턴 [CITED: WebFetch 2026-05-18]
- developer.android.com/develop/ui/compose/graphics/draw/overview — CanvasDrawScope 오프스크린 렌더 [CITED]

### Tertiary (LOW confidence — needs validation)

- Medium "How to Create a Signature Draw View in Jetpack Compose" (Zahid Muneer) — Path 누적 + drawPath 패턴 [CITED: WebSearch — 신뢰성 medium, 코드 자체는 Compose 표준 API]
- Compose Canvas `mutableStateOf<Path?>` setter trigger 강제 — empirical observation [ASSUMED: Compose Path 가 mutable + reference equality]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Vault `service_role_key` + `edge_function_base_url` 가 Phase 8 dashboard 시드 완료 환경 | §"Runtime State Inventory" + Pitfall 6 | cron silent skip — 0건 알림. SUMMARY 의 prerequisite 체크리스트로 mitigate. |
| A2 | profiles 의 user_id 가 auth.users.id 와 무관 (003 RLS 가 user_id 문자열 직접 사용) | §"Example D 시드 SQL" | 시드 실패 시 INSERT 가 FK 위반 안 함 (가정 valid) — 검증: Plan 1 Wave 0 의 INSERT 직접 실행. |
| A3 | Compose Canvas Path mutableStateOf 갱신 = setter 강제 호출 패턴 | Pitfall 2 + Pattern 1 | Worker UI 에서 stroke 가 손 떼야 표시됨 (UX 결함, 기능 동작). Plan 3 Wave 2 manual 테스트. |
| A4 | Coil/Glide AsyncImage 가 signed URL 60s 만료 후 캐시 invalidate 안 함 | Pitfall 4 | Manager 가 thumbnail 영구 깨짐. 회피책 = key rotation 또는 모달 dispose. |
| A5 | `gotrue-kt:2.2.0` 의 `ktor-server-core` transitive 가 R8 strip 됨 (release build) | Pitfall 10 | APK size +50KB acceptable. release build size diff 측정 = Plan 3 Wave 0 검증. |
| A6 | testuser1 의 profile.user_role = 'manager' AND group_id = 1 (Phase 8 verified) | §"PoC 시드" | manager 권한 가드 통과 — 검증: Plan 1 Wave 0 `SELECT * FROM profiles WHERE user_id='testuser1'`. |
| A7 | `tbm_missed_workers` RPC 가 Edge Function service_role 호출 시 정상 동작 | §"Example C SQL" | Edge Function 이 직접 SELECT/JOIN 대체 가능. RPC 가 더 안전하지만 필수 X. |
| A8 | Edge Function `tbm-checkin` 의 anon INSERT 가 storage RLS 통과 (Option A) | §"Pattern 5" + Pitfall 3 | RLS 거부 시 Option B 마이그 필요. 검증: Plan 2 Wave 0 의 smoke 1 (정상 path 200 + 비정상 path 403). |

**If this table is empty:** All claims verified. (이 phase = 8개 assumptions, 모두 SUMMARY 의 prerequisite 또는 Wave 0 smoke 로 mitigation 가능.)

## Open Questions (RESOLVED)

1. **C1 결정 — `tbm-started` 가 notifications row insert 해야 하는가?**
   - 알려진 것: Phase 7 watch-alert = insert + push, Phase 8 camera-down = push-only. 본 phase 도 양립 가능.
   - 불명확한 것: v1.0 TBM 알림 history Activity 없음 — insert 해도 UI 노출 X.
   - **RESOLVED:** v1.0 = 모두 push-only (단순, Phase 8 일관). v1.1 에서 TBM 알림 history Activity 추가 시 insert. CONTEXT.md amendment 적용 완료 (2026-05-18, C1).

2. **C2 결정 — tbm_templates Realtime publication 등록?**
   - 알려진 것: seed-only 데이터, manager 시작 시 1회 SELECT 충분.
   - 불명확한 것: 4 채널 구독 비용 미미 — 단순성 vs 정확성 tradeoff.
   - **RESOLVED:** 4 채널 모두 등록 (마이그 단순화, 후행 ALTER 회피). CONTEXT.md amendment 적용 완료 (2026-05-18, C2).

3. **C3 결정 — Option A vs Option B?**
   - 알려진 것: A = 단순 + 즉시성, B = 강건 + 복잡.
   - 불명확한 것: anon key 유출 시 PII 위험 정량화 (단 prefix 가드로 mitigated).
   - **RESOLVED:** v1.0 = Option A, v1.1 마이그 = Option B. CONTEXT.md amendment 적용 완료 (2026-05-18, C3).

4. **데모 시연용 cron 시각 단축 — 09:00 시작 → 09:15 expected → 09:45 cron?**
   - 알려진 것: 30분 margin = realistic. 5월 PPT 데모 라이브 시연은 시간 부족.
   - 불명확한 것: 데모용 expected_end_at 을 "현재 시각 - 30분" 으로 시드하면 다음 cron tick (1분 이내) 즉시 발사.
   - **RESOLVED:** 데모 시 manual 시드 SQL — Plan 4 manual 시연 단계에서 `UPDATE tbm_sessions SET expected_end_at = now() - interval '30 minutes'` 1줄로 즉시 미참여 알림 trigger 가능. UX 측 expected_end_at default 는 Plan 3 의 planner UX 결정에 위임.

5. **Edge Function `tbm-end` 가 manager 의 leader_user_id 본인 가드 — bypass?**
   - 알려진 것: D-08 명시 `leader_user_id == session.leader_user_id` 검증.
   - 불명확한 것: 다른 manager (general_manager) 가 leader 의 휴가 시 종료 가능해야 하는가?
   - **RESOLVED:** v1.0 한정 leader 본인만 (T-9-04 threat mitigation). v1.1 group 내 manager 누구나 종료 가능 (workspace ownership) — deferred 명시.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Supabase managed Postgres + pg_cron + pg_net + Vault | 013 마이그 + cron 함수 | ✓ | (Phase 4·7·8 검증됨) | — |
| Supabase Edge Function runtime (Deno) | notifications/index.ts 4 case | ✓ | (Phase 4·7·8 검증됨) | — |
| Supabase Storage (managed) | tbm-signatures 신규 버킷 | ✓ | (Phase 1 reference-videos, Next-2 4 버킷 운영 중) | — |
| `io.github.jan-tennert.supabase:storage-kt:2.2.0` | Android 클라이언트 storage 업로드 | ✓ (Maven Central) | 2.2.0 + transitive `gotrue-kt:2.2.0` | Retrofit POST `/storage/v1/object/...` (단 anon RLS 통과 못함 — Option B 필요) |
| Vault `service_role_key` + `edge_function_base_url` | cron 함수 | ✓ (Phase 8 dashboard 시드) | — | RAISE WARNING + RETURN graceful skip (Phase 8 검증) |
| testuser1 profile (manager, group_id=1) | manager PoC | ✓ (Phase 4 시드) | — | — |
| profiles worker 시드 (group_id=1) | worker PoC 다중 참여 | ✗ | — | scripts/seed_tbm_demo.sql Plan 1 Wave 0 |
| Compose Canvas API | SignatureCanvas.kt | ✓ | Compose 1.5+ (Phase 7 검증) | — |
| Android desugar 2.0.4 | supabase-kt minSdk 26 → minSdk 24 | ✓ (Phase 7) | 2.0.4 | — |

**Missing dependencies with no fallback:** 없음 — 모든 의존성 가용 또는 fallback 보유.

**Missing dependencies with fallback:**
- profiles worker 시드 — Plan 1 Wave 0 첫 단계 SQL 시드 (검증: testuser1 manager 의 group_id=1 외 worker 3명).

## Security Domain

> security_enforcement 설정 absent → enabled treated. v1.0 PoC 한정 정책 — v1.1 강화 경로 명시.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | partial | Firebase Auth (기존). Supabase Auth 미도입. UserSession.userId 만 trust. |
| V3 Session Management | partial | 본 phase = 화면 lifecycle (Compose). 세션 토큰 = Firebase ID token (기존). |
| V4 Access Control | yes | Edge Function service_role + ownership 검증 (profile.group_id == session.group_id). Phase 7 T-7-03 패턴 미러. |
| V5 Input Validation | yes | WorkTypeValidator (5종 enum) + MAC X (본 phase 무관). expected_end_at TIMESTAMPTZ. signature_url storage path regex. |
| V6 Cryptography | partial | Storage signed URL HMAC-SHA256 (supabase-kt 내부). 본 phase 신규 X. |
| V7 Error Handling | yes | Edge Function 4 case 의 23505 → 409, 404, 410, 403 명시 응답. RAISE WARNING graceful skip (cron). |
| V8 Data Protection | yes | tbm-signatures private + signed URL only. PII (수기 서명) policy. |
| V9 Communications | yes | Supabase HTTPS only. FCM HTTP v1 RS256 (기존). |
| V13 API & Web Service | yes | Edge Function action-routing + body validation (4 case). curl smoke 12 시나리오. |

### Known Threat Patterns for {Phase 9 stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Worker spoofing (다른 group 의 session 에 checkin) | Spoofing | Edge Function `tbm-checkin` ownership: profile.group_id == session.group_id (T-7-03 미러) |
| Manager 가 다른 leader 의 session 종료 | Tampering | `tbm-end` 의 leader_user_id == session.leader_user_id 강제 |
| anon key 유출 → tbm-signatures 버킷 임의 PNG 업로드 | Tampering | RLS prefix `^[0-9]+$/` + 확장자 `.png` + file_size_limit 512KB. v1.0 한정 Option A 잠정. |
| signed URL leak (60s 안) | Information Disclosure | 60s 만료 + Coil cache invalidate 권장 (Pitfall 4) |
| FCM 알림 spoofing (extras 의 session_id 변조) | Spoofing | Activity 진입 시 DB 재조회 (extras 신뢰 X — Phase 7 D-02 일관) |
| Bulk insert race (UNIQUE 충돌) | DoS | UNIQUE (group_id, session_date) + 23505 catch → 409 명시 응답 |
| Vault sr_key 노출 | Information Disclosure | dashboard managed + SECURITY DEFINER 함수 내부만 SELECT (git noexport) — Phase 8 검증 |
| cron 함수 권한 부족 (vault.decrypted_secrets) | Elevation of Privilege | SECURITY DEFINER + SET search_path 잠금 (Phase 8 검증) |

---

*Phase: 09-tbm-worker-guide*
*Research date: 2026-05-18*
*Mode: --auto (autonomous, no clarifying questions)*
*Pattern source: Phase 4 D-09 (알림 전이) + Phase 7 D-02/D-04b (ComposeView + Edge Function-mediated write) + Phase 8 D-03 (pg_cron + Vault + sendPushToUsers plural)*
