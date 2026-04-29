# Smart Safety Management — Milestone v1.0 Requirements

> **Milestone**: v1.0 5월 PPT 데모
> **Goal**: 5월 중순 PPT 데모에서 5종 AI 비전 + J2208A 워치 1인 파이프라인이 단일
> 시연 흐름으로 통합 동작.
> **Total**: 6 카테고리 / 19 요구사항
> **Sources**: `iridescent-percolating-fox.md` (W1-W3 비전 큐), `docs/J2208A_안전관리_시스템_PLAN.md` (Phase 1)

---

## v1.0 Requirements

### 1. 데이터 트랙 (DATA)

- [ ] **DATA-01**: helmet 데모 영상 교체 — head 검출 가능한 영상 확보 (AI-Hub
  안전모 미착용 데이터셋 / 자체 30s mp4 / 레거시 `발표자료용 영상/detection(fire,
  helmet).mp4` 중 택일), `reference-videos` 버킷에 업로드, `cameras.live_url_detail`
  갱신
- [ ] **DATA-02**: fire 데모 영상 교체 — fire conf 0.5+ 검출 가능한 영상으로
  (동일 옵션 또는 AI-Hub 화재 데이터셋), 동일 업로드/갱신
- [ ] **DATA-03**: `scripts/upload_reference_videos.py` SOURCES dict 갱신 + `--only
  fire,helmet` 옵션으로 부분 재업로드 검증

### 2. 모델 트랙 단계 1 (MODEL — frames 연속 룰)

- [ ] **MODEL-01**: `ai_agent/detector_configs.py` 에 `frames_required` 키 추가 —
  fire 5, helmet 3, forklift 1, person 1 (메모리 검증된 룰 임계치 그대로)
- [ ] **MODEL-02**: `ai_agent/scheduler.py` 또는 `yolo_detector.py` 에 frame
  buffer 로직 — 마지막 N 개 모두 is_detected=True 면 알람, 아니면 no_detect 처리;
  detect 강제 모드 테스트 추가
- [ ] **MODEL-03**: 임시조치 복원 — fire/helmet `conf_thres` 0.5 (또는 그 이상),
  helmet `target_classes` ['head'] 로 복원 (DATA-01/02 완료 후)

### 3. 모델 트랙 단계 2 (FUSION — 다중 모델 cross-reference)

- [ ] **FUSION-01**: 지게차+사람 bbox IoU 계산 — IoU > 0.3 이 N 프레임 연속이면
  "지게차 충돌 위험" 알람. `_process_collision_check(forklift_result,
  person_result)` scheduler helper 추가
- [ ] **FUSION-02**: 사람 bbox 의 head 영역 helmet 객체 매칭 — 없으면 "안전모
  미착용". 레거시 `사람 탐지/main.py` 의 `hardhat_is_on` 패턴 이식

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
  1분 delta. 윈도우 내 GOOD 비율 < 50% 면 결측 표기. `minute_summary` 적재 (Edge
  Function 또는 Python agent insert)
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

- **LP-3 RTSP 실카메라** (Drift X3 실기기 부재) — v1.1 6월 설치 직전
- **LP-5 룰 seed DB / `risk_level` 매핑** — v1.1
- **Next-5/6/7/8/9** (PTT 음성·JWT 활성화·SMS Solapi·릴리즈 빌드·main PR) — v0.9
  또는 v1.1 별도 마일스톤
- **환경센서·평면도 자동화·화재 3단계 매뉴얼** — LP-6 이후 / v2.x
- **워치 다중 동시 연결, BLE 게이트웨이, 옥외 LTE 중계** — Future Requirements
  참조

---

## Traceability

> 19/19 v1.0 requirements mapped to phases · 0 orphans · 0 duplicates
> Source: `.planning/ROADMAP.md` (created 2026-04-29)

| REQ-ID    | Phase   | Phase Name                          | Status  |
|-----------|---------|-------------------------------------|---------|
| DATA-01   | Phase 1 | 비전 — 데모 영상 교체               | Pending |
| DATA-02   | Phase 1 | 비전 — 데모 영상 교체               | Pending |
| DATA-03   | Phase 1 | 비전 — 데모 영상 교체               | Pending |
| MODEL-01  | Phase 2 | 비전 — frames 연속 룰               | Pending |
| MODEL-02  | Phase 2 | 비전 — frames 연속 룰               | Pending |
| MODEL-03  | Phase 2 | 비전 — frames 연속 룰               | Pending |
| FUSION-01 | Phase 3 | 비전 — bbox 겹침/공간 매칭          | Pending |
| FUSION-02 | Phase 3 | 비전 — bbox 겹침/공간 매칭          | Pending |
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
