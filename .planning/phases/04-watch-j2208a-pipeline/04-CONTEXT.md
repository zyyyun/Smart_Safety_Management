# Phase 4: 워치 — J2208A 1인 파이프라인 - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

`scripts/j2208a_sensor_reader.py` 의 S1 Decode 위에 S2 (Validate) → S3 (Aggregate)
→ wear-state state machine → S4 (Derive) 파이프라인을 추가하고, Supabase 에 raw 와
집계·상태 전이·위험 알림을 적재하며, 1차 위험 알림 (탈착·통신두절·빈맥) 을 기존
FCM 훅으로 발송한다. 1인 (testuser1, MAC `21:02:02:06:01:69`) 24h 연속 운용으로
무손실 동작을 입증하는 것이 phase 의 종점.

**Out of scope** (다른 phase / future):
- 비전 5종 detector (Phase 1·2·3)
- 비전 + 워치 정량 평가 (Phase 5 — EVAL-03 가 워치 24h 측정 결과를 입력으로 받음)
- 워치 대시보드 UI (Phase 6 — DEMO-03)
- 다중 작업자, BLE 게이트웨이, 옥외 LTE 중계 (J2208A 플랜 Phase 2~4 — v1.1+)
- wear-state 임계값 §8 추가 실험 보정 (v1.1)
- 작업자↔디바이스 매핑 (MAC vs QR/NFC), 알림 채널 SMS/카카오톡, 개인정보 정책,
  의료기기 면책 정책, 충전 운용, 펌웨어 동결 (J2208A 플랜 §11 미해결 결정 — v1.1)

</domain>

<decisions>
## Implementation Decisions

### 스키마 (Supabase 마이그레이션)
- **D-01: 기존 `devices` 테이블 확장 + 4 신규 테이블 + `workers` 별도 X**
  - **ALTER `devices`**: `mac_address VARCHAR(17) UNIQUE`, `firmware_version VARCHAR(20)`,
    `last_comm_at TIMESTAMP` 컬럼 추가. 기존 컬럼 (`battery_level`, `user_id`,
    `device_type='WATCH'`, `serial_number`) 그대로 활용.
  - **기존 `device_watches` 테이블**: 그대로 유지 (current snapshot 용 — body_temp,
    heart_rate). minute_summary 와 의미 분리 — device_watches = "마지막 본 값"
    (UI 카드 표시), minute_summary = "1분 평균 시계열".
  - **신규 4 테이블** (Phase 4 마이그레이션 = `010_watch_pipeline.sql`):
    - `raw_events` (raw_id BIGSERIAL, device_id FK, ts TIMESTAMPTZ, cmd SMALLINT,
      raw_hex TEXT, parsed JSONB) — 7일 TTL, 1초 dedup
    - `wear_state_events` (event_id BIGSERIAL, device_id FK, ts TIMESTAMPTZ,
      from_state TEXT, to_state TEXT, reason JSONB) — 영구
    - `minute_summary` (device_id FK, minute_ts TIMESTAMPTZ, hr_median INT,
      temp_median NUMERIC(4,1), temp_iqr NUMERIC(4,1), steps_delta INT,
      dominant_state TEXT, good_ratio NUMERIC(3,2), PRIMARY KEY (device_id,
      minute_ts)) — 영구
    - `safety_alerts` (alert_id BIGSERIAL, device_id FK, alert_type TEXT
      (TACHY|REMOVED|COMMS_LOST), severity TEXT (CAUTION|WARNING|DANGER),
      raised_at TIMESTAMPTZ, resolved_at TIMESTAMPTZ, ack_at TIMESTAMPTZ,
      reason JSONB) — 영구
  - **`workers` 별도 테이블 X**: 기존 `profiles` (user_role='worker', group_id)
    + `devices.user_id` 로 작업자↔디바이스 매핑 충분. v1.1 다중작업자 진입 시
    재검토 (J2208A 플랜 §11).

### TTL · 중복 제거
- **D-02: pg_cron 주기 cleanup + UNIQUE constraint dedup**
  - `raw_events` 에 UNIQUE (`device_id`, `ts_truncated_to_second`, `raw_hash`)
    constraint + INSERT 시 `ON CONFLICT DO NOTHING`. `ts_truncated_to_second` 는
    generated column (`date_trunc('second', ts)`), `raw_hash` 는 generated column
    (`md5(raw_hex)`).
  - pg_cron 1시간 주기 cleanup job: `delete from raw_events where ts < now() -
    interval '7 days'`. v0.1 의 `001_extensions.sql` 에서 pg_cron 활성화됨.

### RLS
- **D-03: 새 4 테이블 RLS = service_role 전용 (v0.1 패턴)**
  - 4 테이블 모두 RLS ENABLE + 정책 = 없음 (service_role 만 접근). v1.0 은 BLE
    클라이언트만 insert, 대시보드는 v1.1 (Phase 6 DEMO-03 도 service_role 또는
    Edge Function 경유). v0.1 `003_rls_policies.sql` 의 시스템 테이블 패턴 동일.

### 파이프라인 호스팅
- **D-04: `scripts/j2208a_sensor_reader.py` 인라인 (단일 Python 프로세스)**
  - 기존 `_on_notify` callback 위치에서 `parse_packet` (S1) 출력을 그대로 S2 → S3
    → state machine → S4 → DB insert 로 흘림. BLE 클라이언트와 동일 프로세스.
  - 모듈 분리: `j2208a/` 패키지 신규 — `decode.py` (기존 코드 이전), `validate.py`
    (S2), `aggregate.py` (S3), `state_machine.py` (wear-state), `derive.py` (S4),
    `supabase_writer.py` (DB insert). `scripts/j2208a_sensor_reader.py` 는 BLE
    + 데몬 골격만 유지.
  - 근거: 5–6Hz 단일 디바이스 PoC 라 인프라 분리 비용 > 이점. 5월 PPT 데모는 단일
    PC 에서 1 프로세스로 시연. v1.1 다중 작업자 진입 시 BLE→raw insert / S2~S4 분리
    재고.

### 신호 해석 — Wear-State State Machine
- **D-05: J2208A 플랜 §3 의 5 상태 + 5초 sliding window 다수결, 임계값 잠정값**
  - 5 상태: `OFF`, `WARMUP`, `TRANSIENT`, `WORN`, `ABNORMAL` (정확 표기 J2208A
    플랜 §3 표 그대로)
  - 잠정 임계값 (J2208A 플랜 §3 + 사용자 결정): `T_off = 33.5°C`, `T_warm = 35.5°C`,
    `N₁ (OFF 진입) = 30s 동안 HR=0 + temp ≤ T_off`, `N₂ (WORN 진입) = HR>0 안정 +
    temp ≥ T_warm 가 30s 유지`. 5초 sliding window 다수결 (≥ 3/5 동일 분류 시
    state 결정). v1.1 §8 추가 실험으로 보정.
- **D-06: 임계값 = Python 코드 상수**
  - `j2208a/state_machine.py` 의 `THRESHOLDS = {...}` dict 또는 `@dataclass`.
    config 외부화는 v1.1 보정 시 도입. v1.0 1인 PoC 는 변경 빈도 낮음.

### S2 Validate
- **D-07: per-field `quality ∈ {GOOD, WARMUP, NOISY, INVALID}` 룰**
  - `HR=0` → WARMUP
  - `HR<30` 또는 `HR>220` → INVALID
  - `temp<25` 또는 `temp>43` → INVALID
  - `|Δtemp|>1.5°C/sec` (직전 sample 대비) → NOISY
  - 그 외 → GOOD
  - 단위 테스트 6개 경계 케이스 (J2208A 플랜 §4 S2 그대로).

### S3 Aggregate
- **D-08: 5초 / 30초 / 1분 윈도우 집계, 1분마다 `minute_summary` insert**
  - HR 5초 median → 1분 12개 sample 의 median (1분 단위 적재)
  - temp 30초 median + IQR → 1분 2개 sample 의 median + IQR (1분 단위 적재)
  - steps 1분 delta = `현재 steps - 1분전 steps`
  - GOOD 비율 < 50% 인 1분 윈도우 → `good_ratio < 0.5` 로 표기, 집계값은 NULL
    (또는 sentinel) 처리

### S4 Derive — 위험 판정
- **D-09: `WORN` 60초 이상 지속 후에만 평가, 3종 1차 위험**
  - **빈맥** = `HR median(60s) ≥ 220 - TEST_USER_AGE × 0.85` (Karvonen)
  - **탈착** = wear-state `OFF` 가 5분 (300초) 이상 지속
  - **통신두절** = 마지막 raw_event 가 N=120초 이상 부재 (BLE 끊김 자체 감지)
  - **알림 전이 원칙**: 정상↔주의↔경보 *전이* 시점에만 1회. 같은 등급 지속
    중 반복 알림 X. 경보→정상 종료 알림 포함 (J2208A 플랜 §6).
- **D-10: TEST_USER_AGE = Python 상수 (하드코딩)**
  - `j2208a/derive.py` 의 `TEST_USER_AGE = 30` (또는 testuser1 실제 나이). v1.0
    1인 PoC 이므로 외부화 불필요. v1.1 다중 작업자 시 profiles 에 birth_year
    컬럼 추가 검토.

### 알림 발송
- **D-11: FCM only (`supabase/functions/_shared/fcm.ts` 재사용)**
  - safety_alerts insert → Supabase Edge Function (`notifications` 또는
    `system`) trigger → `_shared/fcm.ts` 호출 → testuser1 의 `profiles.fcm_token`
    으로 푸시 발송. v0.3 의 RS256 self-sign 흐름 그대로.
  - **시계 진동 (`cmd_vibrate`) 은 v1.0 범위 X** — BLE write 경로 추가 비용 vs
    PoC 가치 낮음. SMS Solapi 는 v0.9 별도 마일스톤.
- **D-12: 알림 발송 = BLE 클라이언트가 service_role 로 Edge Function 호출**
  - `j2208a/supabase_writer.py` 가 safety_alerts insert 후 같은 함수 내에서 기존
    Edge Function (`/functions/v1/notifications/send` 또는 신규 `watch-alert`)
    호출. 또는 pg trigger 로 Edge Function 자동 호출. **권장 = 명시적 호출**
    (디버깅 용이). 정확한 함수명은 planner 가 기존 Edge Function 구조 보고 결정.

### 24h 무중단 검증
- **D-13: 실측 24h 착용 (사용자 본인)**
  - testuser1 가 실제 24h 시계 착용 + PC 가 BLE 마스터로 상시 연결.
  - 검증 SQL: `select count(*) from minute_summary where device_id = X and
    minute_ts >= now() - interval '24 hours'` → 1440 행 ± 결측 표기 행만 존재.
  - 추가 검증: `select count(*) from raw_events where device_id = X and ts >=
    now() - interval '24 hours'` → ≥ 95% raw 수신율 (5–6Hz × 86400s × 0.95
    ≈ 410k 이상, dedup 후 ≈ 80k 이상).

### Claude's Discretion
- pg_cron job 스케줄 (1시간 주기 vs 6시간 주기 vs 매일 자정) — 1시간 주기 default
- `safety_alerts.alert_type` 의 ENUM vs CHECK constraint vs 자유 문자열 — CHECK
  로 시작
- BLE 클라이언트의 재연결 backoff 정책 — 기존 `demo()` 의 3s → 30s 백오프 유지
- TIMESTAMPTZ vs TIMESTAMP — TIMESTAMPTZ 사용 (Supabase 컨벤션)
- raw_events.parsed JSONB 의 schema validation — 우선 풀어두고 v1.1 에서 구체화

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 4 직접 입력
- `.planning/REQUIREMENTS.md` §4 워치 트랙 (WATCH-01·02·03·04·05) — 본 phase 의 요구
- `.planning/ROADMAP.md` Phase 4 섹션 — Goal · 5 Success Criteria · Depends on
- `docs/J2208A_안전관리_시스템_PLAN.md` — **워치 트랙 v1.0 합성 원천**:
  - §3 Wear-State State Machine (5 상태 표 + 5초 sliding window 다수결 원칙)
  - §4 4-stage 파이프라인 (S1 Decode → S2 Validate → S3 Aggregate → S4 Derive)
  - §5 저장 스키마 (6 테이블 — 본 plan 의 D-01 가 일부 reuse 결정)
  - §6 위험 판정 규칙 표 (D-09 의 1차 임계 = 일부 항목만)
  - §8 임계값 보정 실험 표 (v1.0 잠정값 → v1.1 보정)
  - §11 미해결 결정 (Out of Scope 의 출처)
- `docs/J2208A_BLE_사용가이드.md` — BLE 명령·응답 포맷 (CRC, 16-byte 패킷)

### 기존 자산 / 재사용
- `scripts/j2208a_sensor_reader.py` — 현재 S1 Decode (CRC, parse_packet,
  J2208AClient, _on_notify hook). D-04 인라인 통합의 진입점
- `supabase/functions/_shared/fcm.ts` — RS256 JWT self-sign FCM 발송 (D-11)
- `supabase/functions/_shared/supabase.ts` — service_role client (D-12)
- `supabase/migrations/002_tables.sql` — `devices` (line 142-150),
  `device_watches` (line 152-157) 기존 스키마 (D-01 ALTER 대상)
- `supabase/migrations/003_rls_policies.sql` — RLS 정책 패턴 (D-03 system table 패턴)
- `supabase/migrations/001_extensions.sql` — pg_cron 활성화 확인 (D-02)

### v0.5 시점 데이터·테스트 사용자
- testuser1 (`profiles.user_id`) — Firebase 신 프로젝트 푸시 검증된 사용자
- MAC `21:02:02:06:01:69` — 메모리 `project_legacy_assets.md` 검증된 J2208A 디바이스

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`scripts/j2208a_sensor_reader.py:143-213`** `parse_packet()`: S1 Decode 그대로
  사용. `_on_notify(_sender, data)` (line 326-330) 가 S2~S4 진입점. raw_hex 는
  `data.hex(" ")` 로 이미 가공.
- **`scripts/j2208a_sensor_reader.py:219-318`** `J2208AClient`: BLE 연결·재연결·
  notify 구독. D-04 의 단일 프로세스 데몬으로 그대로 활용. `demo()` 의 3s→30s
  백오프, `_on_disconnect` 콜백 패턴 보존.
- **`supabase/functions/_shared/fcm.ts`** RS256 JWT self-sign + FCM HTTP v1
  발송. D-11 알림 발송이 동일 흐름 재사용.
- **`supabase/functions/_shared/supabase.ts`** `createClient` (service_role) +
  `createUserClient(req)`. D-12 의 BLE 클라이언트 → Edge Function 호출 시
  Authorization 헤더 패턴.
- **`supabase/migrations/001_extensions.sql`**: pg_cron extension 활성화 확인
  필요 (없으면 D-02 의 cron job 추가 시 동시 활성화).

### Established Patterns
- **마이그레이션 파일 번호 컨벤션**: 001~008 까지 순차. Phase 4 = `010_watch_pipeline.sql`
  (009 는 비어있을 가능성 높음 — planner 가 확인 후 채택).
- **테이블 RLS 패턴 (v0.1)**: `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` +
  service_role 전용 시스템 테이블은 정책 X. 사용자 접근 테이블 (`profiles`,
  `notifications`) 만 정책 추가. D-03 4 신규 테이블 = 시스템 패턴.
- **device_id FK 패턴**: 모든 device 자식 테이블 (`device_watches`, `device_helmets`,
  `device_event_logs`) 가 `device_id INTEGER REFERENCES devices(device_id) ON DELETE
  CASCADE`. D-01 신규 4 테이블 동일 패턴.
- **BLE 1대1 마스터 점유 한계** (J2208A 플랜 §2.1): PC + 폰 동시 연결 불가. v1.0
  데모 시 PC = BLE 마스터, 시연용 휴대폰은 Supabase 데이터만 표시 (PROJECT.md
  Risk 섹션).

### Integration Points
- **BLE → Supabase**: `j2208a/supabase_writer.py` 신규 모듈에서 service_role client
  로 직접 insert. Edge Function 경유 X (raw_events 5–6Hz 적재가 Edge Function
  cold start 비용 큼).
- **safety_alerts → FCM**: BLE 클라이언트가 alert insert 직후 Edge Function
  (`/functions/v1/notifications/send` 또는 신규 `watch-alert` 추가) 호출. Edge
  Function 가 `_shared/fcm.ts` 로 푸시. 이 흐름은 v0.3 의 detection→FCM 패턴 미러.
- **profiles ↔ devices**: `devices.user_id = profiles.user_id` (이미 v0.1 에서
  연결됨). v1.0 시드 = `devices.user_id = 'testuser1'` 으로 1행 INSERT.
- **wear-state 표 구조** (J2208A 플랜 §3): WORN 60s+ 후에만 위험 평가는 D-09 와
  D-05 가 함께 책임. state_machine 이 결정한 dominant_state 가 minute_summary
  에도 적재되고 derive 가 그 결과 사용.

</code_context>

<specifics>
## Specific Ideas

- **신호 = 상태 신호 원칙** (PROJECT.md Key Decision): HR=0 은 결측 X = "PPG
  락온 전" 상태 신호. temp 33°C ≈ 케이스 외부 평형, 36-37°C = 피부 밀착 상태.
  raw 를 측정치로 표시 X — wear-state 분류 후 표기.
- **알림 전이 원칙** (PROJECT.md Key Decision): 정상↔주의↔경보 *전이* 시점에만 1회.
  동일 등급 지속 중 반복 X. 경보→정상 종료 알림 포함. 본 원칙은 비전·워치 모두
  일관 적용 — Phase 3·5 의 비전 알림과 동일 룰.
- **24h 운용 무손실 의미** (D-13): 1440 minute_summary 행 ± 결측 표기 행만 존재.
  결측 행은 row 자체는 존재하되 `good_ratio < 0.5` + 집계값 NULL. 행 자체가
  누락되면 phase 실패.
- **Storage 폭증 위험** (PROJECT.md Risk): dedup 후 1인/1일 ≈ 80k raw_events 행,
  7일 = 560k 행. pg_cron cleanup 검증 누락 시 v1.1 다중작업자에서 사고. D-02 가
  핵심.

</specifics>

<deferred>
## Deferred Ideas

- **워치 트랙 Phase 2~4** (J2208A 플랜): wear-state 임계값 §8 추가 실험 보정,
  3~5인 사무실 검증, BLE 게이트웨이 (Cassia X1000/X2000, Minew G1/G2 등) +
  MQTT/HTTP 백홀, 다중 작업자 운용 → v1.1 (사무실) / v1.2 (게이트웨이) / v2.x (옥외)
- **워치 미해결 결정** (J2208A 플랜 §11): 작업자↔디바이스 매핑 (MAC 고정 vs
  QR/NFC 매일 체크인), 게이트웨이 벤더 비교, 알림 채널 (SMS/카카오톡 알림톡),
  개인정보 정책 (보관·익명화·열람권), 충전 운용 절차, 펌웨어 동결 정책 → v1.1
  별도 결정 phase
- **시계 진동 (`cmd_vibrate`) 알림 채널** — BLE write 경로 추가 비용. v1.1+
- **SMS Solapi 알림 채널** → v0.9 별도 마일스톤 (Next-7)
- **wear-state 임계값 외부화** (config 파일 또는 DB 테이블) — v1.1 보정 단계
- **HR_max_age (Karvonen) 의 birth_year 외부화** — profiles 컬럼 추가 또는 workers
  테이블 — v1.1 다중작업자
- **워치 대시보드 UI 3층 구조** (J2208A 플랜 §9 a/b/c) — Phase 6 DEMO-03
- **운영자 SQL UPDATE 로 wear-state 임계 보정** — DB 테이블 패턴, v1.1
- **워치 검증셋 100장 라벨링 자동 평가** — Phase 5 EVAL-03 의 input 으로 들어감,
  본 phase 는 raw 적재까지

</deferred>

---

*Phase: 4-watch-j2208a-pipeline*
*Context gathered: 2026-05-02*
