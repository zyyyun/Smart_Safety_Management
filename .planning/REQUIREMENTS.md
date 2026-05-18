# Smart Safety Management — Milestone v1.0 Requirements

> **Milestone**: v1.0 5월 PPT 데모 + 수요일 추가 (워치-앱·RTSP·TBM)
> **Goal**: 5월 중순 PPT 데모에서 5종 AI 비전 + J2208A 워치 1인 파이프라인이 단일
> 시연 흐름으로 통합 동작. **2026-05-14 추가**: 수요일(2026-05-20)까지 (1) 워치-앱
> 양방향 연동, (2) Drift X3 RTSP 실시간 카메라, (3) TBM 현장 작업자 가이드.
> **Total**: 9 카테고리 / 28 요구사항
> **Sources**: `iridescent-percolating-fox.md` (W1-W3 비전 큐), `docs/J2208A_안전관리_시스템_PLAN.md` (Phase 1), 2026-05-14 사용자 추가 요청 (Phase 7·8·9)

---

## v1.0 Requirements

### 1. 데이터 트랙 (DATA)

- [x] **DATA-01** (PASS 2026-05-04): helmet 데모 영상 교체 — H0/L2_D2023-08-31-09-08_001
  시퀀스 (298 frames × 3 loop = 30s mp4 `helmet_h0_demo.mp4`), 87% frames head 검출,
  max head conf 0.871, seek=10s 시 head conf 0.697. `reference-videos/helmet/source_v2.mp4`
  업로드 + `cameras.live_url_detail` (camera_id=5) 갱신. **detection_events ev=24/25
  acc=0.687 label='head' 적재 검증**.
- [x] **DATA-02** (PASS 2026-05-04, D-19 fallback): fire 데모 영상 교체 — 사용자 제공
  화재현상/불꽃/0087 (360 frames @ 30fps = 12s mp4 `fire_aihub_0087.mp4`), seek=10s 시
  conf 0.142. **fire 가중치 한계로 D-04 의 0.5 미달 → D-19 fallback 발동** (conf 0.10
  v0.5 baseline). Phase 2 frames_required (5 연속) 결합 시 운영급 의미. v1.1 fine-tune
  으로 진정한 0.5+ 도달 예정. `reference-videos/fire/source_v2.mp4` 업로드 + cameras
  갱신. **detection_events ev=22/23 acc=0.134 적재 검증**.
- [x] **DATA-03** (PASS 2026-05-04): `scripts/upload_reference_videos.py` SOURCES dict
  갱신 (`AI_HUB_FIRE_MP4` + `SELF_SHOT_HELMET_MP4` 두 별도 상수), `--only fire,helmet`
  부분 재업로드 검증 (성공 2 / 실패 0). forklift/person 무손상.

### 2. 모델 트랙 단계 1 (MODEL — frames 연속 룰)

- [x] **MODEL-01** (PASS 2026-05-07): `ai_agent/detector_configs.py` 에 `frames_required`
  키 추가 — fire 5, helmet 3, forklift 1, person 1 (커밋 `e9da88b`)
- [x] **MODEL-02** (PASS 2026-05-07): `ai_agent/scheduler.py` 의 `_detection_buffer:
  dict[(camera_id, event_key), deque[bool]]` + `_process_detection_for_camera` 의
  buffer push + N 연속 검사 + 알람 후 `buffer.clear()` (커밋 `1d77fae` + `daa0b4b`).
  pytest 8 unit tests PASS (단발 spike → 알람 0회, N 연속 → 알람 1회 + reset 등 전 케이스).
- [x] **MODEL-03** (PASS 2026-05-04, partial — D-19 fallback): helmet `conf_thres = 0.5` +
  `target_classes = ['head']` **완전 복원 (D-04 정상)**. fire `conf_thres = 0.10`
  **D-19 fallback** (가중치 한계). Phase 1 에 흡수 완료.

### 3. 모델 트랙 단계 2 (FUSION — 다중 모델 cross-reference)

- [x] **FUSION-01**: 지게차+사람 bbox IoU 계산 — IoU > 0.3 이 N 프레임 연속이면
  "지게차 충돌 위험" 알람. `_process_collision_check(forklift_result,
  person_result)` scheduler helper 추가 ✓ 2026-05-14 (Phase 3 Plan 02)
- [x] **FUSION-02**: 사람 bbox 의 head 영역 helmet 객체 매칭 — 없으면 "안전모
  미착용". 레거시 `사람 탐지/main.py` 의 `hardhat_is_on` 패턴 이식 ✓ 2026-05-14 (Phase 3 Plan 02)

### 4. 워치 트랙 (WATCH — J2208A 플랜 Phase 1: 1인 PoC 파이프라인)

- [ ] **WATCH-01**: Supabase 스키마 — `devices` (MAC·펌웨어·마지막 통신·배터리),
  `workers` (작업자↔디바이스↔부서 매핑, v1.0 은 1인 MAC 직접 매핑), `raw_events`
  (7일 TTL, 1초 내 동일 raw dedup), `wear_state_events` (영구), `minute_summary`
  (1인 × 1분 × 1행, HR/temp 집계 + steps delta + dominant state, 영구),
  `safety_alerts` (발생/해소/확인 시각·등급·근거, 영구) 마이그레이션 추가
- [ ] **WATCH-02**: S2 Validate — typed event 에 per-field `quality ∈ {GOOD,
  WARMUP, NOISY, INVALID}` 부여. 룰 초안: HR=0 → WARMUP, HR<30 또는 >220 →
  INVALID, temp<25 또는 >43 → INVALID, |Δtemp|>1.5°C/sec → NOISY. 단위 테스트로
  잠정 임계 검증
- [ ] **WATCH-03**: S3 Aggregate — HR 5초 median, temp 30초 median+IQR, steps
  1분 delta. 윈도우 내 GOOD 비율 < 30% 면 결측 표기 (D-17 — 20s 재시작 명령으로
  인한 sample 단절 흡수). `minute_summary` 적재 (Python agent insert, D-04 인라인)
- [ ] **WATCH-04**: Wear-state state machine — OFF/WARMUP/TRANSIENT/WORN/ABNORMAL
  분류, 5초 sliding window 다수결. T_off (예: 33.5°C), T_warm (예: 35.5°C),
  N₁/N₂ 잠정값 사용. 상태 전이 시 `wear_state_events` insert
- [ ] **WATCH-05**: S4 Derive 최소 위험 판정 — `WORN` 60초 이상 지속 후에만 평가,
  (a) 탈착 = OFF 5분 지속, (b) 통신두절 = raw_event 부재 N분, (c) 빈맥 1차 임계 =
  HR median(60s) ≥ 220-age × 0.85. 상태 전이 시 1회만 알림 (정상→주의/경보,
  경보→정상 종료 알림 포함). `safety_alerts` insert + 기존 FCM 훅
  (`_shared/fcm.ts`) 연동

### 5. 평가 트랙 (EVAL)

- [ ] **EVAL-01**: 2단계 정량 지표 정의 — **비전**: 5종 평균 precision ≥ 0.85,
  recall ≥ 0.80, 일별 FP율 ≤ 10%, 168h 무중단, 감지~FCM 지연 ≤ 10s. **워치**:
  raw 수신율 ≥ 95% (1인 24h 기준), wear-state 분류 정확도 ≥ 90% (운영자 판정
  대비), 알림 응답 ≤ 30s. PPT 슬라이드 포맷 1차 안.
- [ ] **EVAL-02**: 비전 검증셋 100장 라벨링 + 자동 평가 스크립트 (precision/recall
  산출)
- [ ] **EVAL-03**: 워치 1인 24시간 연속 운용 검증 — raw 수신율, dedup 효과 (1인/일
  ≈ 50만 행 → 8.6만 행), `minute_summary` 적재 무손실, wear-state 전이 이벤트
  수동 검증

### 6. 데모 빌드 (DEMO)

- [ ] **DEMO-01**: 5종 비전 이벤트가 시연 흐름 1회에 모두 발생하도록 영상/카메라
  매핑 정렬 (camera 1·2·3·4·5 × 5종 detector × DATA-01/02 의 새 영상)
- [ ] **DEMO-02**: 비전 Android 화면 캡처 — AI감지 탭 → 5종 카드 → 상세 → FCM
  푸시 알림 트레이까지 1 사이클 (스마트폰 실기기 또는 에뮬레이터)
- [ ] **DEMO-03**: 워치 대시보드 — J2208A 플랜 §9 의 3층 (a) 작업자 카드 (1인=1
  카드, 색상·이름·HR/temp·갱신 시각·wear-state 라벨) (b) 1시간 line chart (WORN
  진하게, 그 외 음영, 5초 median) (c) 이벤트 타임라인 (알림 발생/해소 + raw
  드릴다운). Android 또는 웹 — 둘 중 PPT 시연에 빠른 쪽 택일.
- [ ] **DEMO-04**: PPT 슬라이드 — 1단계 자산 (KOLAS 17025·논문·특허) + 2단계
  지표 (EVAL-01: 비전+워치) + 5월 데모 화면 (DEMO-02 비전 캡처 + DEMO-03 워치
  캡처) + 의료기기 면책 1줄 ("1차 경고용, 의료기기 아님") 통합

### 7. 워치-앱 양방향 연동 (BRIDGE — 2026-05-20 수요일 마감)

- [x] **BRIDGE-01**: 워치 데이터 (HR/temp/wear-state/safety_alerts) 가 Android
  앱에 실시간 표시 ✓ 2026-05-14 (07-03 ebcd623) — Supabase Realtime 3 채널 구독
  (`device_watches` Update / `wear_state_events` Insert / `safety_alerts`
  Insert+Update+Delete) via WatchRealtimeRepository.postgresChangeFlow.
  WatchCardComposable (HomeWorker ComposeView 임베드) — HR/temp 한 줄 +
  WearStateLabel (5 상태 한글 매핑, 의료기기 면책 '측정값' 단어 0건) + 마지막
  활성 alert 1건. Realtime.Status.CONNECTED 시 SDK push, 그 외 5초 polling
  fallback (D-01). HR=0 또는 wear-state WARMUP/OFF 시 회색 + '—' 표기 (신호=
  상태신호 PROJECT.md key decision). 1시간 차트는 Phase 6 DEMO-03 으로 이연.
  Wave 4 (07-04) 단축 PoC 에서 ≤3초 지연 실측 검증.
- [x] **BRIDGE-02**: 앱 → 워치 명령 양방향 채널 ✓ 2026-05-14 (07-02 e2298a2 백엔드 + 07-03 ebcd623 UI) —
  notifications/index.ts 의 case 'watch-ack' 가 `safety_alerts.ack_at` 컬럼 갱신
  (010 스키마 컬럼명, REQ 텍스트의 `acknowledged_at` 표기는 오기). T-7-02 ownership
  SQL + idempotency `.is('ack_at', null)` + 서버측 `new Date().toISOString()`
  (T-7-05). watch_ack.sh 3 smoke (정상/idempotency/ownership) PASS. **UI 측 (07-03)**:
  SafetyAlertsActivity 신규 + SafetyAlertsScreen LazyColumn — 미해결 alert 만
  acknowledge 버튼 노출, Retrofit POST `/functions/v1/notifications` 호출 → 200
  '확인됨' / 404 '이미 확인됨' (idempotent 무예외) / 5xx '오류'. WatchAckIdempotencyTest
  3 cases PASS. 하단 fine print '1차 경고용, 의료기기 아님'.
- [x] **BRIDGE-03**: 앱 사용자별 워치 매핑 + 페어링 ✓ 2026-05-14 (07-02 3eb872d 백엔드 + 07-03 d3d3baf UI) —
  notifications/index.ts 의 case 'watch-pair' 가 devices 테이블의 user_id +
  mac_address UPDATE/INSERT. MAC 정규식 재검증 (T-7-03 client validation 우회
  차단) + 다른 worker paired → 409 + unpair-aware 3-tier 룩업 (mac eq → serial
  fallback → INSERT). watch_pair.sh 5 smoke (정상/MAC invalid/spoofing/unpair/
  re-pair) PASS. v1.0 1인=1 워치 매핑 — testuser1 + 21:02:02:06:01:69 운영 DB
  검증 완료. **UI 측 (07-03)**: PairWatchSection (DeviceManageScreen 하단 통합) —
  MAC TextField + 정규식 클라이언트 검증 + 등록/해제 버튼 + WatchStatus 3-색상
  badge (UNPAIRED 회색 / CONNECTED 초록 last_comm<5분 / DISCONNECTED 노랑).
  Realtime devices 채널 (filter user_id) 구독으로 status 자동 갱신.
  MacAddressValidatorTest 9 cases PASS.

### 8. Drift X3 RTSP 실시간 카메라 (RTSP)

- [ ] **RTSP-01**: `ai_agent/scheduler.py` 가 RTSP 스트림을 직접 추론 — 기존 mp4
  파일 경로 대신 `rtsp://...` URL 처리. `cameras.live_url_detail` 의 RTSP URL
  지원 + `cv2.VideoCapture` 또는 `ffmpeg-python` 으로 frame 추출. mp4 fallback
  유지 (영상 없을 때 데모 모드).
- [ ] **RTSP-02**: Drift X3 카메라 ≥ 1대 실기기 RTSP 연동 검증 — `cameras`
  테이블에 Drift X3 RTSP URL 등록 + 1 detection cycle 실측 (forklift 또는
  person detector) + `detection_events` 적재 확인. SUMMARY 에 실측 frame rate
  / 지연 (감지~insert ≤ 10s) 기록.
- [ ] **RTSP-03**: RTSP 안정성 — 끊김 시 재연결 (최대 N=3 회 backoff), 헬스체크
  (마지막 frame 수신 시각 추적), 끊김 N분 지속 시 운영 알림 (FCM 또는 로그).
  `cameras.last_frame_at` 컬럼 추가 + 헬스체크 SQL.

### 9. TBM (Tool Box Meeting) 현장 작업자 가이드 (TBM)

- [x] **TBM-01**: TBM 스키마 — `tbm_sessions` (일자·시각·작업장·리더), 
  `tbm_checklists` (세션별 위험 항목·체크 상태·근거), `tbm_participants`
  (세션 참여 작업자·서명·체크인 시각), `tbm_templates` (작업 유형별 체크리스트
  템플릿) 마이그레이션 추가. ✓ 2026-05-18 (Phase 9 09-01, 커밋 f044fac · 20d2c7f).
- [ ] **TBM-02**: TBM 가이드 화면 (Android) — 오늘 TBM 세션 시작 → 작업 유형
  선택 → 템플릿 기반 위험 항목 체크리스트 (예: 화재 위험·전기·고소·중량물 등)
  → 참여 작업자 체크인 (NFC/QR/수기 서명 중 1) → 세션 종료 + Supabase 적재.
  관리자 순회 점검과 별도 메뉴.
- [x] **TBM-03**: TBM 참여 이력 + 미참여 알림 — Edge Function 4 case 운영 배포 (tbm-start/checkin/end/missed) + 12 smoke PASS + Pitfall 9 leader dedup 검증. 작업자별 일자별 TBM 참여 여부 대시보드 (Plan 09-03 Android UI) + 미참여 FCM 알림 (Plan 09-01 pg_cron tbm_missed_attendance_minute 1분 주기 + 본 plan 의 tbm-missed case 가 round-trip). ✓ 2026-05-18 (Phase 9 09-02, 커밋 417c203 · aeb6ddf).

---

## Future Requirements (deferred)

- **인프라 트랙 — sys.path 분리** (fall + general detector 동시 활성) → v1.1 또는
  YOLO26 마이그레이션이 흡수
- **YOLO26 전면 마이그레이션** (5종 비전 단일화 — Ultralytics 2026-01-14 출시,
  end-to-end NMS 제거 + CPU 43% 향상 + pose 통합) → v2.0 / 6월~
- **워치 트랙 Phase 2~4** (J2208A 플랜) — wear-state 임계값 §8 추가 실험 보정,
  3~5인 사무실 검증, BLE 게이트웨이 (Cassia X1000/X2000, Minew G1/G2 등) +
  MQTT/HTTP 백홀, 다중 작업자 운용 → v1.1 (사무실) / v1.2 (게이트웨이) / v2.x
  (옥외)
- **워치 미해결 결정 (J2208A 플랜 §11)** — 작업자↔디바이스 매핑 (MAC 고정 vs
  QR/NFC 매일 체크인), 게이트웨이 벤더 비교, 알림 채널 (SMS/카카오톡 알림톡),
  개인정보 정책 (보관·익명화·열람권), 충전 운용 절차, 펌웨어 동결 정책 → v1.1
  별도 결정 phase

---

## Out of Scope (v1.0)

- ~~**LP-3 RTSP 실카메라** (Drift X3 실기기 부재) — v1.1 6월 설치 직전~~ **→
  2026-05-14 v1.0 으로 승격 (Phase 8 — RTSP-01·02·03)**
- **LP-5 룰 seed DB / `risk_level` 매핑** — v1.1
- **Next-5/6/7/8/9** (PTT 음성·JWT 활성화·SMS Solapi·릴리즈 빌드·main PR) — v0.9
  또는 v1.1 별도 마일스톤
- **환경센서·평면도 자동화·화재 3단계 매뉴얼** — LP-6 이후 / v2.x
- **워치 다중 동시 연결, BLE 게이트웨이, 옥외 LTE 중계** — Future Requirements
  참조

---

## Traceability

> 28/28 v1.0 requirements mapped to phases · 0 orphans · 0 duplicates
> Source: `.planning/ROADMAP.md` (created 2026-04-29, extended 2026-05-14)

| REQ-ID    | Phase   | Phase Name                          | Status  |
|-----------|---------|-------------------------------------|---------|
| DATA-01   | Phase 1 | 비전 — 데모 영상 교체               | ✓ Complete |
| DATA-02   | Phase 1 | 비전 — 데모 영상 교체               | ✓ Complete (D-19 fallback) |
| DATA-03   | Phase 1 | 비전 — 데모 영상 교체               | ✓ Complete |
| MODEL-01  | Phase 2 | 비전 — frames 연속 룰               | ✓ Complete |
| MODEL-02  | Phase 2 | 비전 — frames 연속 룰               | ✓ Complete |
| MODEL-03  | Phase 2 | 비전 — frames 연속 룰               | ✓ Complete (Phase 1 absorbed, D-08) |
| FUSION-01 | Phase 3 | 비전 — bbox 겹침/공간 매칭          | ✓ Complete |
| FUSION-02 | Phase 3 | 비전 — bbox 겹침/공간 매칭          | ✓ Complete |
| WATCH-01  | Phase 4 | 워치 — J2208A 1인 파이프라인        | Pending |
| WATCH-02  | Phase 4 | 워치 — J2208A 1인 파이프라인        | Pending |
| WATCH-03  | Phase 4 | 워치 — J2208A 1인 파이프라인        | Pending |
| WATCH-04  | Phase 4 | 워치 — J2208A 1인 파이프라인        | Pending |
| WATCH-05  | Phase 4 | 워치 — J2208A 1인 파이프라인        | Pending |
| EVAL-01   | Phase 5 | 평가 — 2단계 정량 지표              | Pending |
| EVAL-02   | Phase 5 | 평가 — 2단계 정량 지표              | Pending |
| EVAL-03   | Phase 5 | 평가 — 2단계 정량 지표              | Pending |
| DEMO-01   | Phase 6 | 데모 빌드 — 통합 시연·캡처·PPT      | Pending |
| DEMO-02   | Phase 6 | 데모 빌드 — 통합 시연·캡처·PPT      | Pending |
| DEMO-03   | Phase 6 | 데모 빌드 — 통합 시연·캡처·PPT      | Pending |
| DEMO-04   | Phase 6 | 데모 빌드 — 통합 시연·캡처·PPT      | Pending |
| BRIDGE-01 | Phase 7 | 워치-앱 양방향 연동 (수요일 마감)   | ✓ Complete (07-03, ebcd623) — Wave 4 E2E 시연 검증 예정 |
| BRIDGE-02 | Phase 7 | 워치-앱 양방향 연동 (수요일 마감)   | ✓ Complete (백엔드 07-02 e2298a2 + UI 07-03 ebcd623) |
| BRIDGE-03 | Phase 7 | 워치-앱 양방향 연동 (수요일 마감)   | ✓ Complete (백엔드 07-02 3eb872d + UI 07-03 d3d3baf) |
| RTSP-01   | Phase 8 | Drift X3 RTSP 실시간 카메라         | ✓ Complete (백엔드 08-01 capture_rtsp 715c277 + 08-03 scheduler health wiring 00aeedf + 08-04 mediamtx 합성 E2E 85370c5 — 6 cycles RTSP capture + last_frame_at 갱신 + camera_id=4 forklift detection_events 6건) |
| RTSP-02   | Phase 8 | Drift X3 RTSP 실시간 카메라         | ⏸ DEFERRED (실기기 부재 — v1.1 6월 검단·포천 설치 직전 LP-3). 08-04 mediamtx 합성 충족 = SC #2 의 "1 cycle detection_events + 추론 ≤10s" 부분 충족 (mediamtx 로컬 ≈0초). 실기기 측정 부분만 deferred. |
| RTSP-03   | Phase 8 | Drift X3 RTSP 실시간 카메라         | ✓ Complete (백엔드 08-02 cameras_healthcheck 0131ffa + 08-03 case camera-down/recovered c8c7b6d + scheduler health wiring 00aeedf + 08-04 backoff 검증 ~101s + recovery 검증). 5분 cron round-trip FCM 도착 = Vault sr_key Dashboard 시드 후 자연 동작 (08-04 SUMMARY User Setup Required). |
| TBM-01    | Phase 9 | TBM 현장 작업자 가이드              | ✓ Complete (09-01 f044fac · 20d2c7f, 2026-05-18) — 013_tbm_schema.sql 운영 DB 적용 + 4 신규 테이블 + RLS + Realtime publication ADD 4 + tbm-signatures Storage + pg_cron tbm_missed_attendance_minute + 5 templates 시드 + 7/7 isolation assertions PASS |
| TBM-02    | Phase 9 | TBM 현장 작업자 가이드              | Pending |
| TBM-03    | Phase 9 | TBM 현장 작업자 가이드              | ✓ Complete (09-02 417c203 · aeb6ddf, 2026-05-18) — notifications 4 case (tbm-start/checkin/end/missed) 운영 배포 (74.97kB) + 12 smoke PASS + D-09 delta=0 + T-9-02/03/04/09 mitigation + Pitfall 5·9·11 검증 + testuser1 실제 push notified_count:1 |
