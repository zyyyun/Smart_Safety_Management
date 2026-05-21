# Phase 4: 워치 — J2208A 1인 파이프라인 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 4-watch-j2208a-pipeline
**Areas discussed:** Schema, Pipeline, Threshold, 24h verify, TTL, Age, Channels

---

## Schema — 6 테이블 구성 (기존 자산과 의미 겹침 처리)

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 테이블 확장 + 4 신규 | `devices` 에 mac_address·firmware_version·last_comm_at ALTER. `device_watches` 는 스냅샷 용으로 유지. workers 별도 테이블 X (profiles + devices.user_id 매핑). 신규 4: raw_events, wear_state_events, minute_summary, safety_alerts | ✓ |
| 완전 분리 신규 6 테이블 | `j2208a_devices`, `j2208a_workers`, ... 네임스페이스 분리. 충돌 적고 마이그레이션 롤백 쉬움. 단 v0.1 의 devices/device_watches 와 의미 중복 | |
| 기존 device_watches 와 신규 6 도단 겹계 | device_watches 확장해서 minute_summary 흡수. 기존+핵심 추가 최소화. 단 의미 분리 모호 | |

**User's choice:** 기존 테이블 확장 + 4 신규
**Notes:** 기존 자산 활용 + 의미 분리 둘 다 충족. workers 테이블이 별도로 필요한 시점은 v1.1 다중 작업자 진입 시 — 그때 재검토.

---

## Pipeline — S2/S3/S4 호스팅 위치

| Option | Description | Selected |
|--------|-------------|----------|
| `scripts/j2208a_sensor_reader.py` 인라인 | 이미 설계된 _on_notify 와 동일 프로세스 안에서 S2~S4 처리 → raw_events insert + minute_summary insert + wear_state_events insert + safety_alerts insert + FCM Edge Function trigger. 단일 PoC 적합, BLE 어 끔 · 재연결 증가 그대로 병합 | ✓ |
| BLE 클라이언트는 raw 만 insert · Edge Function 또는 pg_cron 이 S2~S4 수행 | BLE 클라이언트 = 고성능 raw 적재만, S2~S4 는 Deno Edge Function 또는 pg_cron 1분 주기 trigger — 확장 편 좋은 수 별 죄 | |
| S2/S3 = Python agent (BLE 와 별도 프로세스) · S4 = Edge Function | BLE = raw 주입, Python agent = S2·S3 지속 처리, FCM 알림 Trigger = Edge Function. 도 도 면마 | |

**User's choice:** scripts/j2208a_sensor_reader.py 인라인
**Notes:** 5–6Hz 단일 디바이스 PoC 라 인프라 분리 비용 > 이점. 5월 PPT 데모는 단일 PC 1 프로세스로 시연. 모듈 분리 (`j2208a/decode.py`, `validate.py`, `aggregate.py`, `state_machine.py`, `derive.py`, `supabase_writer.py`) 는 코드 가독성 위해 적용.

---

## Threshold — wear-state 임계값 저장 위치

| Option | Description | Selected |
|--------|-------------|----------|
| Python 코드 상수 | scripts/j2208a_*.py 또는 신규 모듈 내 파이썬 상수. v1.0 은 1인 PoC 이고 보정이 없으므로 외부화 불필요. v1.1 의 실점 보정 시점에 도입 | ✓ |
| config 파일 (config.py 또는 .env) 동적 로드 | watch_thresholds.py 또는 .env. 코드 수정 없이 값 조정 가능· 단 v1.0 에서 굳 아닌 검 수 있음 | |
| watch_thresholds DB 테이블 + 운영자 SQL UPDATE | 신규 테이블원 운영자가 롤아쿨으로 수정 → 파이프라인 이 하나의 주기 일이이 수 v1.1 보정 운영 | |

**User's choice:** Python 코드 상수
**Notes:** v1.0 1인 PoC 는 변경 빈도 낮음. v1.1 §8 추가 실험으로 보정 시 외부화 도입.

---

## 24h verify — 1인 무중단 SC #3 검증 방식

| Option | Description | Selected |
|--------|-------------|----------|
| 실측 24h 착용 | 사용자 (testuser1) 가 실제 24h 시계 착용 → 파이프라인 자 동작 → minute_summary 1440 이 ±· 결측 이 표기 행 결 수 차 단아 이년· 아 | ✓ |
| 짧은 1-2h 운용 + 비례 추정 | · PPT 데모 직전 시간 제약— 1-2h 제수 의 | |
| 합성 BLE 입력 시뮬주니 24h 가속 | 계 ble 합· 고· | |

**User's choice:** 실측 24h 착용
**Notes:** SC #3 의 "1440 행 ± 결측" 검증은 실측이 가장 신뢰성 높음. PoC 1인 = 본인이 착용하는 것이 자연.

---

## TTL — raw_events 7일 + 1초 dedup 구현

| Option | Description | Selected |
|--------|-------------|----------|
| pg_cron 주기 cleanup + UNIQUE constraint dedup | pg_cron 1시간 주기로 `delete from raw_events where ts < now() - interval '7 days'`. dedup = UNIQUE constraint (device_id, ts_truncated_to_second, raw_hash) + ON CONFLICT DO NOTHING. v0.1 에 pg_cron 이미 활성화됨 | ✓ |
| 애플리케이션 레벨 dedup + cron job (외부 cron) | Python BLE 클라이언트 안에 last_raw_per_device dict 로 1초 내 중복 차단 + 외부 cron (Windows 스케줄러) 으로 TTL cleanup. 운영 의존성 높음 | |
| DB 레벨 dedup 없이 raw 모두 insert + TTL 스크립트 만 신규 | Storage 용량 증가는 감수 — dedup 없으면 1인/일 ≈ 50만 행 · 7일면 350만 행. v1.0 1인 PoC 에서는 견뎉 수 있을 수 | |

**User's choice:** pg_cron 주기 cleanup + UNIQUE constraint dedup
**Notes:** v0.1 의 pg_cron 활성화를 그대로 활용. UNIQUE constraint 는 generated column (`ts_truncated_to_second`, `raw_hash = md5(raw_hex)`) 사용하면 자동 동작.

---

## Age — Karvonen HR_max age source

| Option | Description | Selected |
|--------|-------------|----------|
| v1.0 은 하드코딩 (테스트 사용자 이미 알고 있음) | Python 상수 TEST_USER_AGE=30 (또는 실제 사용자 나이). v1.0 1인 PoC 이므로 외부화 불필요. v1.1 다중작업자 이행 시 profiles 에 birth_year 추가 또는 workers 테이블 추가 | ✓ |
| profiles 에 birth_year 컬럼 추가 + 조회 | ALTER TABLE profiles ADD COLUMN birth_year INT. testuser1 의 birth_year UPDATE. v1.0 에서 이미 제대로 차려 쓰 수 | |
| 나이·세 구분 없이 고정 임계 (HR ≥ 150bpm) | Karvonen 대신 단순 고정 임계 — 아주 명 수 이| | |

**User's choice:** v1.0 은 하드코딩
**Notes:** TEST_USER_AGE = 30 (또는 testuser1 실제 나이). v1.1 다중 작업자 진입 시 외부화.

---

## Channels — 알림 채널

| Option | Description | Selected |
|--------|-------------|----------|
| FCM 만 | 이미 v0.3 에서 RS256 self-sign 검증됨. testuser1 실기기 푸시 수신 확인. 추가 구현 없이 _shared/fcm.ts 그대로 재사용. 시계 진동 · SMS 은 v1.1 이후 | ✓ |
| FCM + 시계 모터 진동 (cmd_vibrate) | BLE 클라이언트가 cmd_vibrate(N) 호출. 작아자 손목으로· 설· 경보 시· 시· 시·· · · ·· 1· 수 ··· · | |
| FCM + SMS (Solapi) 하이브리드 | ·· 알림 수신 실패 대비 을 SMS Solapi 에 대체 발송. v1.0 Solapi 연동 구현 필요 → Next-7 마일스톤· 아이·· · · · · · ·· · ··· | |

**User's choice:** FCM 만
**Notes:** v0.3 자산 그대로 재사용 — 추가 구현 0. 시계 진동 / SMS 은 deferred.

---

## Claude's Discretion

- pg_cron job 스케줄 (1시간 vs 6시간 vs 매일 자정) — 1시간 주기 default
- safety_alerts.alert_type ENUM vs CHECK constraint vs 자유 문자열 — CHECK 로 시작
- BLE 클라이언트의 재연결 backoff 정책 — 기존 demo() 의 3s → 30s 백오프 유지
- TIMESTAMPTZ vs TIMESTAMP — TIMESTAMPTZ (Supabase 컨벤션)
- raw_events.parsed JSONB schema validation — 우선 풀어두고 v1.1 에서 구체화

## Deferred Ideas

- 워치 트랙 Phase 2~4 (J2208A 플랜): wear-state 임계값 §8 보정, 3~5인 사무실, BLE 게이트웨이, 다중 작업자 → v1.1 / v1.2 / v2.x
- 시계 진동 cmd_vibrate 알림 채널 → v1.1+
- SMS Solapi 알림 채널 → v0.9 (Next-7)
- wear-state 임계값 외부화 (config 또는 DB) → v1.1 보정 단계
- HR_max_age birth_year 외부화 (profiles 컬럼 또는 workers 테이블) → v1.1 다중작업자
- 워치 대시보드 UI 3층 (J2208A 플랜 §9) → Phase 6 DEMO-03
- 운영자 SQL UPDATE wear-state 임계 보정 → v1.1
- 워치 검증셋 100장 라벨링 자동 평가 → Phase 5 EVAL-03
- J2208A 플랜 §11 미해결 결정 6종 (작업자 매핑 방식, 게이트웨이 벤더, 알림 채널, 개인정보 정책, 충전 운용, 펌웨어 동결) → v1.1 별도 결정 phase
