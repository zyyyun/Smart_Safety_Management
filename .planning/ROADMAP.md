---
milestone: v1.0
name: "5월 PPT 데모 + 수요일 추가 (워치-앱·RTSP·TBM)"
phases_total: 9
requirements_total: 28
created: 2026-04-29
updated: 2026-05-18 (Phase 8 Wave 4 ✓ COMPLETE — 08-04 mediamtx 합성 E2E + backoff + recovery 검증. Phase 8 종결, RTSP-02 실기기 측정 + Vault sr_key 시드 부분만 deferred)
---

# Smart Safety Management — Roadmap (v1.0 5월 PPT 데모 + 수요일 추가)

> **Goal**: 5월 중순 PPT 데모에서 (1) 5종 AI 비전이 운영급 임계 (conf 0.5+) 와 룰
> 충실도 (frames 연속·bbox 겹침/공간 매칭) 로 동작 + (2) J2208A 워치 1인
> 파이프라인이 S2~S4 (Validate→Aggregate→Derive) 를 거쳐 wear-state 분류·1차 위험
> 알림·Supabase 적재까지 완결 — 두 트랙이 단일 시연 흐름 + 캡처 + PPT 자료로 통합.
>
> **2026-05-14 추가**: 수요일(2026-05-20)까지 (1) 워치-앱 양방향 연동, (2) Drift
> X3 RTSP 실시간 카메라 연동, (3) TBM 현장 작업자 가이드를 추가 — Phase 7·8·9 신설.
>
> **Critical path**: ≈ 3주 (W1~W3, 5월 중순 마감) + Phase 7 수요일 추가 (2026-05-20)

---

## Phases

- [x] **Phase 1: 비전 — 데모 영상 교체** ✓ 2026-05-06 (D-19 fallback: fire conf 0.10) —
  helmet/fire 영상이 운영급 conf (helmet 0.5+, fire 0.10 v0.5 baseline) 로 검출되며
  cameras 매핑이 갱신됨
- [x] **Phase 2: 비전 — frames 연속 룰** ✓ 2026-05-07 — 단일 frame conf 변동으로 인한
  false positive 가 N 프레임 연속 룰로 흡수되고, Phase 1 의 D-19 fallback 의 운영급
  의미를 frames_required (fire 5, helmet 3, forklift 1, person 1) 가 완성
- [x] **Phase 3: 비전 — bbox 겹침/공간 매칭** ✓ 2026-05-14 — 지게차 충돌 위험·안전모 미착용이
  단일 detector 결과 대신 다중 모델 fusion (IoU + hardhat_is_on) 으로 판정됨, 22/22 pytest PASS
- [ ] **Phase 4: 워치 — J2208A 1인 파이프라인** — BLE notification 이 S2~S4 를
  거쳐 wear-state 분류·1차 위험 알림·Supabase 적재까지 24h 연속 동작
- [ ] **Phase 5: 평가 — 2단계 정량 지표** — 5월 PPT 슬라이드에 들어갈 비전+워치
  정량 지표가 정의되고 자동 측정 가능
- [ ] **Phase 6: 데모 빌드 — 통합 시연·캡처·PPT** — 5월 PPT 자료가 비전 5종 +
  워치 1인을 단일 시연 흐름 + 캡처 + 1·2단계 자산 통합으로 완결됨
- [ ] **Phase 7: 워치-앱 양방향 연동** (⚠ 2026-05-20 수요일 마감) — J2208A 워치
  데이터가 Android 앱에 실시간 표시 + 앱→워치 명령 양방향 채널 + 작업자별 페어링
- [x] **Phase 8: Drift X3 RTSP 실시간 카메라** ✓ 2026-05-18 (RTSP-02 실기기 측정 부분 deferred → v1.1 LP-3) — `ai_agent` 가 mp4 대신 RTSP
  스트림 직접 추론, mediamtx 합성 검증 + 안정성 (재연결·헬스체크) 완성
- [ ] **Phase 9: TBM 현장 작업자 가이드** — 작업 시작 전 TBM (Tool Box Meeting)
  세션에 현장 작업자가 직접 참여하는 가이드 — 체크리스트·서명·미참여 알림

## Phase Summary

| #  | Phase                              | REQs | Criteria | Depends on   | Est. duration |
|----|------------------------------------|------|----------|--------------|---------------|
| 1  | 비전 — 데모 영상 교체              | 3    | 4        | —            | W1, ~3일      |
| 2  | 비전 — frames 연속 룰              | 3    | 4        | Phase 1      | W1~W2, ~3일   |
| 3  | 비전 — bbox 겹침/공간 매칭         | 2    | 3        | Phase 2      | W2, ~3일      |
| 4  | 워치 — J2208A 1인 파이프라인       | 5    | 5        | — (병렬)     | 1~2주         |
| 5  | 평가 — 2단계 정량 지표             | 3    | 4        | Phase 1·2·3·4| W2, ~1일 + 24h 운용 |
| 6  | 데모 빌드 — 통합 시연·캡처·PPT     | 4    | 4        | Phase 5      | W3, ~2일      |
| 7  | 워치-앱 양방향 연동 (수요일 마감)  | 3    | 4        | Phase 4 (백엔드) | ~5일, 2026-05-20 마감 |
| 8  | Drift X3 RTSP 실시간 카메라        | 3    | 4        | Phase 1 (cameras 매핑) | ~3일      |
| 9  | TBM 현장 작업자 가이드             | 3    | 4        | — (병렬)     | ~3일          |

**Dependency graph**: 1 → 2 → 3, 4 병렬, 5 ← (1·2·3·4), 6 ← 5, 7 ← 4 (수요일 마감 critical), 8 ← 1, 9 병렬 (시연 흐름은 3·4·7·8 활용)

---

## Phase Details

### Phase 1: 비전 — 데모 영상 교체
**Goal**: helmet/fire 데모 영상이 head 검출 가능 / fire conf 0.5+ 검출 가능 영상으로
교체되어, `reference-videos` 버킷과 `cameras.live_url_detail` 이 새 영상을 가리키고
`scripts/upload_reference_videos.py` 가 새 SOURCES dict 로 부분 재업로드를 통과한다.
**Depends on**: 없음 (첫 phase)
**Requirements**: DATA-01, DATA-02, DATA-03
**Success Criteria** (what must be TRUE):
  1. `reference-videos` 버킷에 helmet 신규 영상 1개와 fire 신규 영상 1개가 업로드되어
     있고, 두 객체 모두 30초 이상 재생 가능한 mp4 (`scripts/upload_reference_videos.py
     --only fire,helmet` 실행 후 `cameras` 테이블에서 `live_url_detail` 의 helmet/fire
     URL 이 새 객체를 가리킴 — `select id, type, live_url_detail from cameras where
     type in ('helmet','fire')`).
  2. helmet 신규 영상에 대해 `--once-detect` 1회 실행 시 `target_classes=['head']`,
     `conf_thres ≥ 0.5` 로도 head bbox 가 ≥ 1프레임에서 검출됨 (DATA-01).
  3. fire 신규 영상에 대해 `--once-detect` 1회 실행 시 `conf_thres ≥ 0.5` 에서 fire
     bbox 가 ≥ 1프레임에서 검출됨 (DATA-02).
  4. `scripts/upload_reference_videos.py` 의 SOURCES dict 가 신규 파일을 포함하도록
     갱신되고, `--only fire,helmet` 옵션이 fire/helmet 만 선택 재업로드함 (다른 3종은
     건드리지 않음) — 명령 종료 코드 0, log 에 "skipped: fall, forklift, person"
     계열 메시지 또는 동등 출력 (DATA-03).
**Plans**: 1 plan
  - [ ] 01-01-PLAN.md — detector_configs.py 운영급 임계 영구 적용 (MODEL-03 흡수) +
    upload_reference_videos.py SOURCES/remote_path 갱신 + 부분 재업로드 +
    `--once-detect` 1회 실행 + detection_events SQL 검증

### Phase 2: 비전 — frames 연속 룰
**Goal**: 단일 frame conf 변동으로 인한 false positive 가 N 프레임 연속 룰로
흡수되고, 1단계 임시조치 (fire/helmet conf 0.10 + helmet target_classes None) 가
운영값 (fire/helmet conf 0.5+ + helmet `['head']`) 으로 복원된다.
**Depends on**: Phase 1 (새 영상 위에서만 conf 0.5+ 복원 검증 가능)
**Requirements**: MODEL-01, MODEL-02, MODEL-03
**Success Criteria** (what must be TRUE):
  1. `ai_agent/detector_configs.py` 의 `DETECTOR_CONFIGS` 에 `frames_required` 키가
     추가되고 fire=5 / helmet=3 / forklift=1 / person=1 로 설정됨 — 파일을 grep 하면
     5개 detector 모두 해당 키를 가진다 (MODEL-01).
  2. `ai_agent/scheduler.py` (또는 `yolo_detector.py`) 에 frame buffer 가 추가되어,
     마지막 N=`frames_required` 프레임이 *모두* `is_detected=True` 일 때만 알람을
     생성한다. detect 강제 모드에서 단일 프레임 spike 입력 시 알람이 발생하지
     않고, N 프레임 연속 입력 시 정확히 1회 알람이 발생함을 단위 테스트로 검증
     (MODEL-02).
  3. `detector_configs.py` 에서 fire `conf_thres ≥ 0.5`, helmet `conf_thres ≥ 0.5`,
     helmet `target_classes = ['head']` 로 복원됨 — 파일 diff 가 0.10 → 0.5+ 및
     None → `['head']` 변경을 보임 (MODEL-03).
  4. Phase 1 의 신규 helmet/fire 영상에 대해 `--once-detect` 실행 시, MODEL-03
     복원된 임계값으로도 `detection_events` 가 정상 생성됨 (Phase 1 SC #2/#3 와
     일관성 유지).
**Plans**: 1 plan
  - [ ] 02-01-PLAN.md — DETECTOR_CONFIGS frames_required 키 (4 detector × MODEL-01) + scheduler.py _detection_buffer + N 연속 검사 분기 (MODEL-02) + ai_agent/tests/test_scheduler_buffer.py 단위 테스트 (D-03 8 케이스). MODEL-03 은 Phase 1 에서 흡수 완료 (D-08).

### Phase 3: 비전 — bbox 겹침/공간 매칭
**Goal**: 지게차 충돌 위험과 안전모 미착용이 단일 detector 결과 대신 다중 모델
fusion (지게차+사람 IoU, 사람 head 영역 + helmet 객체 매칭) 으로 판정된다.
**Depends on**: Phase 2 (frames_required 룰 위에 fusion 적용)
**Requirements**: FUSION-01, FUSION-02
**Success Criteria** (what must be TRUE):
  1. `scheduler.py` 에 `_process_collision_check(forklift_result, person_result)`
     helper 가 추가되어, 지게차 bbox 와 사람 bbox 의 IoU 를 계산하고 IoU > 0.3 이
     N=`frames_required` 프레임 연속일 때 "지게차 충돌 위험" 이벤트를
     `detection_events` 에 insert 한다. IoU > 0.3 인 합성 입력으로 단위 테스트
     통과 (FUSION-01).
  2. 사람 bbox 의 head 영역과 helmet 객체를 매칭하는 `hardhat_is_on` 패턴이 레거시
     `사람 탐지/main.py` 에서 이식되어, 사람이 있고 head 영역에 helmet 이 없을 때
     "안전모 미착용" 이벤트를 생성한다 — 단일 detector 의 head/no_helmet 클래스
     출력에 의존하지 않음 (FUSION-02).
  3. Phase 2 의 frames 연속 룰이 FUSION-01/02 에도 동일하게 적용됨 — 1프레임 spike
     로는 알람이 발생하지 않고, N 프레임 연속 조건 충족 시 1회 발생.
**Plans**: 2 plans (2 waves)

  **Wave 1**
  - [x] 03-01-PLAN.md — GenericYoloDetector.detect_all() + fusion_helpers.py (iou_xyxy/hardhat_is_on/compute_fusion_collision/compute_fusion_helmet_missing) + fusion_configs.py (FUSION_CONFIGS) + test_fusion.py cases 1-7 — COMPLETE 2026-05-14 (커밋 729a1b4·cd2528a·40a9224, 12/12 pytest PASS)

  **Wave 2** *(blocked on Wave 1 completion)*
  - [x] 03-02-PLAN.md — detector_configs.py D-04 helmet `disabled=True` + scheduler.py _fusion_buffer + _process_fusion_for_camera + fusion loop + migration 009 + `supabase db push` [BLOCKING] + test case 8 — COMPLETE 2026-05-14 (커밋 769a0fc·a2a31c8·d546fb5, 22/22 pytest PASS, DB 적용 완료)

  **Cross-cutting constraints:**
  - `_fusion_buffer` (D-06) and `_detection_buffer` (Phase 2) operate as separate module dicts — never share keys
  - helmet detector `disabled=True` must be set before fusion loop runs (both in Wave 2)
### Phase 4: 워치 — J2208A 1인 파이프라인
**Goal**: J2208A BLE notification 이 S2 (Validate) → S3 (Aggregate) → wear-state
state machine → S4 (Derive) 을 거쳐 1인 24h 연속 운용 시 wear-state 분류 + 1차 위험
알림 (탈착·통신두절·빈맥) + Supabase 적재 (`raw_events`/`wear_state_events`/
`minute_summary`/`safety_alerts`) 가 무손실로 동작한다.
**Depends on**: 없음 — Phase 1·2·3 (비전) 과 코드베이스 분리 (`scripts/j2208a_*` +
신규 Edge Function/Python pipeline vs `ai_agent/*`) 로 병렬 진행 가능.
**Requirements**: WATCH-01, WATCH-02, WATCH-03, WATCH-04, WATCH-05
**Success Criteria** (what must be TRUE):
  1. Supabase 마이그레이션이 적용되어 `devices`, `workers`, `raw_events` (7일 TTL +
     1초 내 동일 raw dedup), `wear_state_events`, `minute_summary` (1인 × 1분 ×
     1행), `safety_alerts` 6 테이블이 생성되고 RLS 정책이 설정됨 — `\d+` 또는
     마이그레이션 파일 검토로 6 테이블 모두 확인 가능 (WATCH-01).
  2. typed event 의 per-field `quality ∈ {GOOD, WARMUP, NOISY, INVALID}` 분류가
     단위 테스트를 통과한다 — HR=0 → WARMUP, HR<30/>220 → INVALID, temp<25/>43 →
     INVALID, |Δtemp|>1.5°C/sec → NOISY 의 경계 케이스 6개 모두 기대값 일치
     (WATCH-02).
  3. S3 Aggregate 가 HR 5초 median, temp 30초 median+IQR, steps 1분 delta 를 산출
     하고 GOOD 비율 < 50% 윈도우는 결측 표기, `minute_summary` 에 1인 × 1분 = 1행
     으로 적재됨 — 24h 운용 시 1440 행 ± 결측 표기 행만 존재 (WATCH-03).
  4. Wear-state state machine (OFF/WARMUP/TRANSIENT/WORN/ABNORMAL) 이 5초 sliding
     window 다수결로 분류되고, 상태 전이 시점에만 `wear_state_events` insert —
     같은 등급 지속 중 중복 insert 없음 (WATCH-04, J2208A 플랜 §6 알림 전이 원칙).
  5. S4 Derive 가 `WORN` 60초 이상 지속 후에만 평가하고, 탈착 (OFF 5분 지속) /
     통신두절 (raw_event 부재 N분) / 빈맥 1차 임계 (HR median(60s) ≥ 220-age × 0.85)
     트리거 시 `safety_alerts` insert + 기존 FCM 훅 (`_shared/fcm.ts`) 통해 푸시
     발송. 정상↔주의↔경보 *전이* 시점에만 알림 1회, 경보→정상 종료 알림 포함
     (WATCH-05).
**Plans**: 4 plans (3 waves)
  - [x] 04-01-PLAN.md — Supabase 마이그레이션 010_watch_pipeline.sql (ALTER devices + 4 신규 테이블 + RLS + pg_cron + testuser1 시드) — 운영 DB 적용 완료 (`8a67962` · `a5dec5f` · `dcf52e4`)
  - [x] 04-02-PLAN.md — j2208a/ Python 패키지 (decode/validate/aggregate/state_machine/derive/supabase_writer) + unit tests — **39 pytest pass** (31 원본 + 8 integration) (`e3a559c` · `2e28532` · `aabd4e3`)
  - [x] 04-03-PLAN.md — BLE notify → pipeline wiring + notifications/index.ts watch-alert action + .env.example — Edge Function 배포 + curl smoke 200 (`7e8cac1` · `8be85da` · `1936ee6` · `1e9e51a`)
  - [ ] 04-04-PLAN.md — 24h 실측 운용 + verify_watch_24h.sql + VERIFICATION.md (non-autonomous, 사용자 24h 워치 착용 필요)

### Phase 5: 평가 — 2단계 정량 지표
**Goal**: 5월 PPT 슬라이드에 들어갈 비전 (precision/recall/FP율/지연/무중단) + 워치
(raw 수신율/wear-state 정확도/알림 응답) 정량 지표가 정의되고, 자동 평가 스크립트
+ 1인 24h 운용 결과로 측정 수치가 산출된다.
**Depends on**: Phase 1·2·3 (비전 측정 입력) + Phase 4 (워치 측정 입력)
**Requirements**: EVAL-01, EVAL-02, EVAL-03
**Success Criteria** (what must be TRUE):
  1. 비전 정량 지표가 문서화됨 — 5종 평균 precision ≥ 0.85, recall ≥ 0.80, 일별 FP율
     ≤ 10%, 168h 무중단, 감지~FCM 지연 ≤ 10s. 워치 정량 지표 — raw 수신율 ≥ 95%
     (1인 24h), wear-state 분류 정확도 ≥ 90% (운영자 판정 대비), 알림 응답 ≤ 30s.
     PPT 슬라이드 1차 안 (EVAL-01).
  2. 비전 검증셋 100장이 5종 카테고리에 걸쳐 라벨링되고 (`reference-videos` 또는
     자체 캡처), 자동 평가 스크립트가 precision/recall 을 산출함 — 스크립트 1회
     실행으로 5종 detector 별 수치 표 출력 (EVAL-02).
  3. J2208A 1인 24시간 연속 운용 검증 결과 — raw 수신율 측정값 (≥ 95% 목표),
     dedup 효과 (1인/일 ≈ 50만 → 8.6만 행), `minute_summary` 1440 행 무손실 적재
     검증, wear-state 전이 이벤트 ≥ 1건 수동 검증 (EVAL-03).
  4. 비전 + 워치 측정 수치가 단일 PPT 슬라이드 포맷에 채워질 준비 완료 (Phase 6
     DEMO-04 의 입력으로 사용 가능).
**Plans**: TBD

### Phase 6: 데모 빌드 — 통합 시연·캡처·PPT
**Goal**: 5월 PPT 자료가 (a) 5종 비전 + 워치 1인을 단일 시연 흐름 1회로 모두
보이고 (b) Android(또는 웹) 캡처가 비전·워치 양쪽 갖춰져 있고 (c) 1단계 자산
(KOLAS 17025·논문·특허) + 2단계 정량 지표 + 5월 데모 캡처가 통합 슬라이드 1세트로
완결됨.
**Depends on**: Phase 5 (정량 지표 + 측정 수치) — 시연 흐름은 Phase 3 (비전 fusion)
+ Phase 4 (워치) 산출물을 활용.
**Requirements**: DEMO-01, DEMO-02, DEMO-03, DEMO-04
**Success Criteria** (what must be TRUE):
  1. 카메라 1·2·3·4·5 가 각각 5종 detector × Phase 1 의 새 영상에 매핑되어, 시연
     흐름 1회 실행 (`--once-detect` 또는 운영 스케줄 1 사이클) 에 5종 비전 이벤트가
     모두 발생한다 — `detection_events` 에 5종 type 이 같은 시연 윈도우 내 모두
     기록됨 (DEMO-01).
  2. 비전 Android 캡처 — AI감지 탭 → 5종 카드 → 상세 → FCM 푸시 알림 트레이까지
     1 사이클 캡처 (이미지/영상) 가 확보됨 (실기기 또는 에뮬레이터). 모든 5종이
     화면에 표시됨 (DEMO-02).
  3. 워치 대시보드 캡처 — J2208A 플랜 §9 의 3층 (작업자 카드 + 1시간 line chart +
     이벤트 타임라인) 이 Android 또는 웹 중 PPT 시연에 빠른 쪽으로 구현되어
     캡처됨 (DEMO-03).
  4. PPT 슬라이드 — 1단계 자산 (KOLAS 17025·논문·특허) + 2단계 지표 (Phase 5
     EVAL-01 비전+워치 수치) + 5월 데모 캡처 (DEMO-02 비전 + DEMO-03 워치) +
     의료기기 면책 1줄 ("1차 경고용, 의료기기 아님") 이 단일 slide deck 으로
     통합됨 (DEMO-04).
**Plans**: TBD

### Phase 7: 워치-앱 양방향 연동 ⚠ 수요일 마감 (2026-05-20)
**Goal**: J2208A 워치 백엔드 (Phase 4) 가 적재한 데이터 (HR/temp/wear-state/
safety_alerts) 가 Android 앱 화면에 실시간 표시되고, 앱→워치 명령 (acknowledge·
check-in) 양방향 채널이 동작하며, 작업자별 워치 페어링이 가능하다.
**Depends on**: Phase 4 Wave 1·2 (백엔드 적재 완료) — Wave 3 (24h 실측) 와 무관.
**Requirements**: BRIDGE-01, BRIDGE-02, BRIDGE-03
**Success Criteria** (what must be TRUE):
  1. Android 앱이 Supabase Realtime 으로 `raw_events` / `wear_state_events` /
     `safety_alerts` 를 구독, 워치 데이터가 ≤ 3초 지연으로 화면 카드 + 1시간
     차트 + 알림 타임라인 갱신됨 (BRIDGE-01). 실기기 또는 에뮬레이터 1회 캡처.
  2. 앱 측 알림 카드의 "확인 (acknowledge)" 버튼 → Supabase Edge Function
     `watch-command` → `safety_alerts.acknowledged_at` 컬럼 갱신이 1회 사이클
     (≤ 5초) 에 검증됨 (BRIDGE-02). 신규 Edge Function 배포 + curl smoke 200.
  3. 페어링 화면 — 작업자가 MAC 주소 입력/스캔 → `workers.device_mac` 저장 →
     디바이스 status (connected/disconnected/paired) 가 화면에 표시됨
     (BRIDGE-03). 1인=1 워치 매핑 1건 운영 DB 검증.
  4. 2026-05-20 (수요일) 까지 Phase 7 완료 — Phase 4 의 데이터가 앱에서
     실시간으로 보이고 양방향 명령 가능한 상태.
**Plans**: 4 plans
- [x] 07-01-PLAN.md — 인프라 (supabase-kt 2.2.0 의존성 + 011 RLS/publication 마이그레이션 + seed_watch_demo.py fallback + RLS isolation SQL test) — ✓ 2026-05-14 (커밋 ddf2def·92bed99·4be6d2c, SUMMARY 07-01-SUMMARY.md)
- [x] 07-02-PLAN.md — Edge Function (notifications/index.ts 에 case watch-ack + watch-pair 추가 + 배포 + curl smoke 8종) — ✓ 2026-05-14 (커밋 e2298a2·3eb872d, SUMMARY 07-02-SUMMARY.md, 8/8 smoke PASS)
- [x] 07-03-PLAN.md — Android UI (MyApp SupabaseClient 싱글톤 + watch/ 패키지 9 파일 + HomeWorker ComposeView 카드 + SafetyAlertsActivity 신규 + DeviceManage 워치 섹션 + 4 unit test 26 cases) — ✓ 2026-05-14 (커밋 c20d0dd·ebcd623·d3d3baf, SUMMARY 07-03-SUMMARY.md, 26/26 unit test PASS, compileDebugKotlin PASS, BRIDGE-01·02·03 Android 가시 부분 완료)
- [⏸] 07-04-PLAN.md — 단축 PoC + E2E 시연 (autonomous: false). **DEFERRED 2026-05-15** — 사용자 시연 환경 부재. 코드/인프라 (Wave 1·2·3) 완성 + 합성 검증 통과 (8 curl smoke + 26 unit + assembleDebug APK). 실측 E2E 만 사용자 가용 시점 재개. 07-04-SUMMARY.md (status: deferred).

### Phase 8: Drift X3 RTSP 실시간 카메라
**Goal**: `ai_agent/scheduler.py` 가 mp4 파일 대신 RTSP 스트림을 직접 추론, Drift
X3 카메라 실기기와 연동되어 실시간 detection 이벤트가 적재되며, 안정성 (재연결·
헬스체크) 이 검증된다.
**Depends on**: Phase 1 (cameras 매핑) — RTSP URL 등록이 mp4 fallback 과 양립.
**Requirements**: RTSP-01, RTSP-02, RTSP-03
**Success Criteria** (what must be TRUE):
  1. `ai_agent` 가 `cameras.live_url_detail` 의 RTSP URL (`rtsp://...`) 을
     `cv2.VideoCapture` 또는 `ffmpeg` 으로 frame 추출 → detector 추론 → 기존
     `detection_events` insert 경로와 동일 동작 (RTSP-01). mp4 fallback 유지 —
     URL scheme 으로 분기.
  2. Drift X3 카메라 ≥ 1대 실기기 등록 + 1 detection cycle 실측, `detection_events`
     row ≥ 1건 적재 (forklift 또는 person detector) + 감지~insert 지연 ≤ 10s
     (RTSP-02).
  3. RTSP 끊김 시 재연결 backoff (최대 3회), 마지막 frame 수신 시각 추적 +
     N분 무수신 시 운영 알림 (FCM 또는 로그). `cameras.last_frame_at` 컬럼
     마이그레이션 + 헬스체크 SQL (RTSP-03).
  4. mp4 demo 모드와 RTSP 운영 모드가 동일 detector 코드로 동작 — 분기점이
     `VideoCapture` 객체 생성 1줄에 한정 (코드 가독성).
**Plans**: 4 plans
- [x] 08-01-PLAN.md — snapshot.py capture_rtsp + URL scheme 분기 wrapper + 6 pytest cases (D-01·02 amended drift_test 패턴, RTSP-01) — ✓ 2026-05-18 (커밋 c3dbf41 test RED + 715c277 feat GREEN, SUMMARY 08-01-SUMMARY.md, 6/6 pytest pass + 전체 28/28 무회귀, scheduler.py zero-change)
- [x] 08-02-PLAN.md — 012_cameras_health.sql (pg_net + Vault `vault.create_secret` 시드 + cameras 3 컬럼 ALTER + cameras_healthcheck() SECURITY DEFINER 함수 + pg_cron `cameras_healthcheck_minute` 1분 주기) + [BLOCKING] supabase db push --linked --yes 성공 + RLS isolation SQL test (T-8-02 회귀 가드) — ✓ 2026-05-18 (커밋 0131ffa·0755f04, SUMMARY 08-02-SUMMARY.md, RPC 204 + Pitfall 4 검증 + T-8-02 PostgREST anon PATCH 빈 배열 + A4/A8 testuser1 ↔ cameras 1·5 group_id=1 정합 확인. Dashboard 의 service_role_key Vault 시드는 08-03 deploy 전 수동 절차)
- [x] 08-03-PLAN.md — notifications/index.ts case camera-down + camera-recovered (sendPushToUsers plural, 정정 #5) + deploy + 4 curl smoke + supabase_client.update_camera_health + scheduler 4 detector wiring (RTSP-01·03) — ✓ 2026-05-18 (커밋 c8c7b6d · 00aeedf, SUMMARY 08-03-SUMMARY.md, deploy 70.21kB 성공, 4/4 smoke PASS sent:1 testuser1 실제 push 수신 검증, SC #4 capture zero-change, ai_agent/tests 28/28 무회귀)
- [x] 08-04-PLAN.md — mediamtx v1.18.2 다운로드 (.gitignore 차단) + scripts/mediamtx.yml + start/stop launcher + restore_cameras_mp4.sql + 6 cycles --once-detect E2E (cameras 1·5 last_frame_at 6회 갱신 + camera_id=4 forklift detection_events 6건) + backoff 검증 (mediamtx kill → SnapshotError after 3 attempts, ~101s) + recovery 검증 + T-8-04 mitigation 0건 — ✓ 2026-05-18 (커밋 85370c5 setup, SUMMARY 08-04-SUMMARY.md). 5분 cron round-trip FCM 도착만 Vault sr_key 미시드로 step 10 deferred (Dashboard 시드 후 자연 동작). RTSP-02 실기기 측정 deferred (v1.1 LP-3).

### Phase 9: TBM 현장 작업자 가이드
**Goal**: 작업 시작 전 TBM (Tool Box Meeting) 세션에 현장 작업자가 직접 참여
하는 가이드 — 위험 항목 체크리스트 + 참여 작업자 서명/체크인 + 참여 이력 +
미참여 알림. 기존 관리자 순회 점검 시스템과 *동시* 운용 (별도 메뉴).
**Depends on**: 없음 — 비전·워치와 코드베이스 분리, 병렬 진행 가능.
**Requirements**: TBM-01, TBM-02, TBM-03
**Success Criteria** (what must be TRUE):
  1. Supabase 마이그레이션 — `tbm_sessions` (일자·시각·작업장·리더), 
     `tbm_checklists` (세션별 위험 항목·체크 상태), `tbm_participants` (참여
     작업자·서명·체크인 시각), `tbm_templates` (작업 유형별 체크리스트) 4 테이블
     생성 + RLS 정책 설정 (TBM-01). `\d+` 또는 마이그레이션 검토로 4 테이블 확인.
  2. Android TBM 가이드 화면 — 오늘 TBM 세션 시작 → 작업 유형 선택 → 템플릿
     기반 체크리스트 → 참여 작업자 체크인 (NFC/QR/수기 서명 중 1) → 세션 종료
     + Supabase 적재 (TBM-02). 실기기 또는 에뮬레이터 1회 사이클 캡처.
  3. 관리자 화면 — 작업자별 일자별 TBM 참여 여부 표시 + 출근했으나 TBM 미참여
     작업자에게 FCM 알림 (관리자 지정 시각 기준, 예: 09:00 까지 미참여 시
     09:30 푸시) (TBM-03). 1일 사이클 검증.
  4. 기존 일일 안전 점검 (관리자 순회) 메뉴와 *별도* 메뉴 — 코드 경로 분리,
     작업자 권한과 관리자 권한 분리 (RLS 정책 검증).
**Plans**: 4 plans
- [ ] 09-01-PLAN.md — 013_tbm_schema.sql (4 테이블 + RLS USING(true) v1.0 + Realtime publication ADD 4 + tbm-signatures Storage 버킷 private Option A + 5 templates 시드 + tbm_missed_attendance_check() SECURITY DEFINER + pg_cron 'tbm_missed_attendance_minute' 1분) + [BLOCKING] supabase db push --linked --yes + scripts/seed_tbm_demo.py (group_id=1 worker 3명) + tests/sql/test_013_tbm_isolation.sql (7 assertions) (TBM-01)
- [ ] 09-02-PLAN.md — notifications/index.ts 에 4 case 추가 (tbm-start / tbm-checkin / tbm-end / tbm-missed, Phase 7 watch-* + Phase 8 camera-* 패턴 union, sendPushToUsers plural 재사용, ownership 검증 T-9-03, leader 검증 T-9-04, UNIQUE 23505 catch Pitfall 5) + supabase functions deploy notifications + 12 curl smoke (4 cases × 3 scenarios) + D-09 회귀 가드 notifications insert 0 (TBM-03)
- [ ] 09-03-PLAN.md — Android tbm/ 패키지 신규 (13 main + 4 test 파일, Phase 7 watch/ 1:1 미러 + SignatureCanvas + 2 validator) + storage-kt:2.2.0 의존성 + MyApp.install(Storage) 1줄 + Dynamic session_id 2-stage Realtime 3 채널 + HomeActivity·HomeWorkerActivity ComposeView 임베드 (Pitfall 12 Theme 래핑) + TbmDashboardActivity·TbmWorkerActivity 신규 + MyFirebaseMessagingService tbm_alert 분기 + 4 TDD unit test (WorkTypeValidator + ExpectedEndAtValidator + TbmParticipantsReducer + SignatureState, 14+ cases) + compileDebugKotlin BUILD SUCCESSFUL + 회귀 watch/ git diff 0 (TBM-02)
- [ ] 09-04-PLAN.md — 1일 사이클 시연 + 캡처 (checkpoint:human-verify, autonomous: false) + Phase 9 4 SC 합성 검증 + 09-04-SUMMARY.md + STATE/ROADMAP/REQUIREMENTS 갱신 + Phase 9 종결 (✓ COMPLETE 또는 ⚠ DEFERRED, Phase 7 04-04 패턴 미러) (TBM-01·02·03 검증)

---

## Coverage Validation

**Total v1.0 requirements**: 28
**Mapped**: 28/28 ✓
**Orphans**: 0
**Duplicates**: 0

| REQ-ID    | Phase   |
|-----------|---------|
| DATA-01   | Phase 1 |
| DATA-02   | Phase 1 |
| DATA-03   | Phase 1 |
| MODEL-01  | Phase 2 |
| MODEL-02  | Phase 2 |
| MODEL-03  | Phase 2 |
| FUSION-01 | Phase 3 |
| FUSION-02 | Phase 3 |
| WATCH-01  | Phase 4 |
| WATCH-02  | Phase 4 |
| WATCH-03  | Phase 4 |
| WATCH-04  | Phase 4 |
| WATCH-05  | Phase 4 |
| EVAL-01   | Phase 5 |
| EVAL-02   | Phase 5 |
| EVAL-03   | Phase 5 |
| DEMO-01   | Phase 6 |
| DEMO-02   | Phase 6 |
| DEMO-03   | Phase 6 |
| DEMO-04   | Phase 6 |
| BRIDGE-01 | Phase 7 |
| BRIDGE-02 | Phase 7 |
| BRIDGE-03 | Phase 7 |
| RTSP-01   | Phase 8 |
| RTSP-02   | Phase 8 |
| RTSP-03   | Phase 8 |
| TBM-01    | Phase 9 |
| TBM-02    | Phase 9 |
| TBM-03    | Phase 9 |

---

## Progress

| Phase                                | Plans Complete | Status        | Completed  |
|--------------------------------------|----------------|---------------|------------|
| 1. 비전 — 데모 영상 교체             | 1/1            | COMPLETE ✓    | 2026-05-06 |
| 2. 비전 — frames 연속 룰             | 1/1            | COMPLETE ✓    | 2026-05-07 |
| 3. 비전 — bbox 겹침/공간 매칭        | 2/2            | COMPLETE ✓    | 2026-05-14 |
| 4. 워치 — J2208A 1인 파이프라인      | 3/4            | Wave 3 done   | (04-04 ⏸) |
| 5. 평가 — 2단계 정량 지표            | 0/0            | Not started   | —          |
| 6. 데모 빌드 — 통합 시연·캡처·PPT    | 0/0            | Not started   | —          |
| 7. 워치-앱 양방향 연동 ⚠ 수요일 마감 | 0/0            | Not started   | (2026-05-20 마감) |
| 8. Drift X3 RTSP 실시간 카메라       | 4/4            | COMPLETE ✓ (RTSP-02 측정 deferred) | 2026-05-18 |
| 9. TBM 현장 작업자 가이드            | 0/0            | Not started   | —          |
