# Phase 7: 워치-앱 양방향 연동 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 07-CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-14
**Phase:** 07-watch-app-bridge
**Deadline:** 2026-05-20 (수요일, D-6)
**Areas discussed:** 실시간 전송, 화면 위치/구조, Acknowledge 채널, 데모 데이터 소스, 카드 필드, 페어링 위치, 단축 PoC 시나리오, FCM 알림 탭

---

## 실시간 전송 채널 (BRIDGE-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Supabase-kt Realtime SDK 도입 | jan-tennert SDK + 3채널 구독 (raw_events / wear_state_events / safety_alerts), 진짜 push (≤1초), 신규 의존성 + SDK 학습 | ✓ |
| FCM 알림 트리거 + REST fetch | 알림 있을 때만 갱신, raw 실시간 X. 신규 의존성 0 | |
| 5초 폴링 | 가장 단순, 프로토타이핑 적합. ≤3초 SC 위반 가능 | |

**User's choice:** Supabase-kt Realtime SDK
**Notes:** 이후 phase (RTSP 실시간 카메라, 워치 대시보드 §9 풀 화면) 재사용 가능성으로 채택. raw_events 는 트래픽 과다로 구독 X — `device_watches` snapshot 으로 우회 결정.

---

## 워치 화면 위치 / 구조

| Option | Description | Selected |
|--------|-------------|----------|
| 신규 WatchDashboardActivity 전용 화면 | 1시간 chart + 카드 + 타임라인 3층, Phase 6 DEMO-03 와 합치 | |
| HomeWorkerActivity 카드 1개 | 가벼운 통합, BRIDGE-01 SC 만 충족, Phase 6 가 풀화면 책임 | ✓ |
| DeviceManage_worker 화면 확장 | 디바이스 + 워치 한 화면, 메뉴 절약 | |

**User's choice:** HomeWorkerActivity 카드 1개
**Notes:** 수요일 마감 우선, 본격 워치 대시보드는 Phase 6 DEMO-03 위임. SafetyAlertsActivity 는 Phase 7 에서 신규 추가하되 Phase 6 의 base 가 되도록 설계.

---

## Acknowledge 채널 (BRIDGE-02)

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 notifications/index.ts 에 case 'watch-ack' 추가 | Phase 4 03 패턴 일관, deploy 1회 | ✓ |
| 신규 watch-command Edge Function | 역할 분리 명확, Edge Function 1개 추가 | |
| Android 직접 PostgREST UPDATE | 서버 로직 X, 가장 단순. RLS 만으로 ownership 강제 | |

**User's choice:** 기존 notifications/index.ts 에 case 'watch-ack' 추가
**Notes:** action-routing 패턴 일관성 우선. Payload 계약 = `{action, alert_id, user_id}`, ack_at 은 서버 시계.

---

## 데모 데이터 소스 (Phase 4 04-04 미실행 상황)

| Option | Description | Selected |
|--------|-------------|----------|
| Mock 시드 INSERT 스크립트 | scripts/seed_watch_demo.py — Phase 7 독립 가능 | |
| Phase 4 04-04 단축 PoC 먼저 | 2~4시간 자기 착용, 실측 데이터로 Phase 7 검증 | ✓ |
| UI 만 빈 상태 + 수동 골델 INSERT | 데모 약함, DB Studio 로 1건씩 INSERT | |

**User's choice:** Phase 4 04-04 단축 PoC 먼저
**Notes:** Phase 7 가 Phase 4 04-04 단축 PoC 에 의존성 추가. 시연 스토리 = "2시간 착용 중 5분 탈착 → 앱이 즉시 알림 → ack 후 정상 복귀". mock 시드 스크립트는 fallback 으로만 (planner 결정).

---

## HomeWorkerActivity 워치 카드 필드 + 탭 동작

| Option | Description | Selected |
|--------|-------------|----------|
| 세로 세가지: HR + temp + wear-state + 마지막 알림 1건 제목 | 정보 풍부, 탭 → 신규 SafetyAlertsActivity | ✓ |
| wear-state + 마지막 알림 1건 만 (극단적 축소) | HR/temp X (의료기기 면책 경계). 최소 구조 | |
| HR/temp/state + 1시간 sparkline | 카드 안에 mini chart, 스코프 최대 | |

**User's choice:** 세로 세가지 (HR + temp + wear-state + 마지막 알림 제목)
**Notes:** 카드 클릭 → 신규 SafetyAlertsActivity (워치 알림 전용 리스트 + acknowledge 버튼). AIEventActivity 와 별도 — 비전 5종 알림과 분리 유지. 의료기기 면책은 SafetyAlertsActivity 하단 fine print.

---

## 페어링 화면 위치 (BRIDGE-03)

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 SettingDeviceManagementActivity 에 'J2208A 워치' 섹션 추가 | 한 메뉴에 모든 디바이스, 일관성, MAC 입력 + status badge | ✓ |
| 신규 SettingPairWatchActivity 단독 메뉴 | 워치 전용 화면, 스코프 ↑ | |
| HomeWorkerActivity 카드 자체에 'MAC 등록 필요' 프롬프트 | 독립 화면 X, BRIDGE-03 SC 약함 | |

**User's choice:** SettingDeviceManagementActivity 섹션 추가
**Notes:** MAC 입력 + connected/disconnected/paired status badge + unpair 버튼. devices.mac_address (Phase 4 D-01) 에 직접 UPDATE — supabase-kt PostgREST + RLS 정책으로 본인 워치만 UPDATE 가능.

---

## Phase 4 04-04 단축 PoC 시나리오

| Option | Description | Selected |
|--------|-------------|----------|
| 2시간 착용 + REMOVED 1회 시나리오 | 30분 안정 + 5분 탈착 + 60분 추가, 실측 + 라이브 시연 가능 | ✓ |
| 4시간 + REMOVED + COMMS_LOST 시나리오 | 추가 관찰치, 시간 비용 ↑ | |
| 30분 만 + REMOVED 1회 | 최소 검증, 데이터 빈약 | |

**User's choice:** 2시간 + REMOVED 1회
**Notes:** PC BLE 마스터 연결, 30분 안정 → 5분 탈착 (REMOVED CAUTION 발생) → 재착용 (REMOVED 해제) → 60분 추가. 시연 직전 5분 탈착 라이브 재현 가능 또는 PoC 데이터 그대로 표시.

---

## FCM watch-alert 푸시 탭 동작

| Option | Description | Selected |
|--------|-------------|----------|
| HomeWorkerActivity 로 돌아가서 워치 카드 하이라이트 | 카드 1개 방향 일치, 가벼운 이동 | ✓ |
| SafetyAlertsActivity 로 직접 이동 | alert 상세로 바로, acknowledge 버튼 즉시 | |
| AIEventActivity (기존 비전 5종 알림 메뉴) | 일관된 알림 아카이브 | |

**User's choice:** HomeWorkerActivity 카드 하이라이트
**Notes:** pendingIntent → MainActivity → HomeWorkerActivity, 카드 3초 깜빡 애니메이션 + acknowledge dialog 또는 SafetyAlertsActivity 진입 옵션.

---

## Claude's Discretion

- Realtime SDK 정확한 버전 (jan-tennert/supabase-kt latest stable, 3.0.x 권장)
- Realtime channel name 컨벤션 (SDK default 사용)
- 카드 색상 테마 (기존 Material3 ColorScheme 활용, wear-state 색상 매핑은 planner)
- acknowledge 버튼 위치 (SafetyAlertsActivity 카드 우측 하단 default)
- MAC 입력 UX (직접 입력 default, BLE 스캔 placeholder 만)
- status badge 색상 (connected=초록 / disconnected=노랑 / unpaired=회색)
- Realtime 구독 실패 시 재시도 (exponential backoff 1s→30s)
- OkHttp 버전 충돌 해소 (Retrofit OkHttp 4.12.0 vs ktor-okhttp — planner 가 dependency tree)

## Deferred Ideas

- 워치 대시보드 §9 3층 풀 화면 → Phase 6 DEMO-03
- BLE write 양방향 (시계 진동 등) → v1.1+
- 다중 작업자 매핑 (manager → N workers grid) → v1.1
- QR / NFC 페어링 → v1.1 결정 phase
- 워치 차트 sparkline → Phase 6 DEMO-03
- 알림 음성 / TTS 안내 → Next-5 별도 마일스톤
- SMS / 카카오톡 알림 채널 → Next-7 별도 마일스톤
- 수동 mock 시드 스크립트 (fallback) — planner 가 결정
- 개인정보 정책 (보관·익명화·열람권) → J2208A §11, v1.1
- 충전 운용 / 펌웨어 동결 정책 → J2208A §11, v1.1
