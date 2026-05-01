---
milestone: v1.0
name: "5월 PPT 데모"
phases_total: 6
requirements_total: 19
created: 2026-04-29
---

# Smart Safety Management — Roadmap (v1.0 5월 PPT 데모)

> **Goal**: 5월 중순 PPT 데모에서 (1) 5종 AI 비전이 운영급 임계 (conf 0.5+) 와 룰
> 충실도 (frames 연속·bbox 겹침/공간 매칭) 로 동작 + (2) J2208A 워치 1인
> 파이프라인이 S2~S4 (Validate→Aggregate→Derive) 를 거쳐 wear-state 분류·1차 위험
> 알림·Supabase 적재까지 완결 — 두 트랙이 단일 시연 흐름 + 캡처 + PPT 자료로 통합.
>
> **Critical path**: ≈ 3주 (W1~W3, 5월 중순 마감)

---

## Phases

- [ ] **Phase 1: 비전 — 데모 영상 교체** — helmet/fire 영상이 운영급 conf (0.5+) 로
  검출되며 cameras 매핑이 갱신됨
- [ ] **Phase 2: 비전 — frames 연속 룰** — 단일 frame conf 변동으로 인한 false
  positive 가 N 프레임 연속 룰로 흡수되고, 임시 임계가 운영값으로 복원됨
- [ ] **Phase 3: 비전 — bbox 겹침/공간 매칭** — 지게차 충돌 위험·안전모 미착용이
  단일 detector 결과 대신 다중 모델 fusion 으로 판정됨
- [ ] **Phase 4: 워치 — J2208A 1인 파이프라인** — BLE notification 이 S2~S4 를
  거쳐 wear-state 분류·1차 위험 알림·Supabase 적재까지 24h 연속 동작
- [ ] **Phase 5: 평가 — 2단계 정량 지표** — 5월 PPT 슬라이드에 들어갈 비전+워치
  정량 지표가 정의되고 자동 측정 가능
- [ ] **Phase 6: 데모 빌드 — 통합 시연·캡처·PPT** — 5월 PPT 자료가 비전 5종 +
  워치 1인을 단일 시연 흐름 + 캡처 + 1·2단계 자산 통합으로 완결됨

## Phase Summary

| #  | Phase                              | REQs | Criteria | Depends on   | Est. duration |
|----|------------------------------------|------|----------|--------------|---------------|
| 1  | 비전 — 데모 영상 교체              | 3    | 4        | —            | W1, ~3일      |
| 2  | 비전 — frames 연속 룰              | 3    | 4        | Phase 1      | W1~W2, ~3일   |
| 3  | 비전 — bbox 겹침/공간 매칭         | 2    | 3        | Phase 2      | W2, ~3일      |
| 4  | 워치 — J2208A 1인 파이프라인       | 5    | 5        | — (병렬)     | 1~2주         |
| 5  | 평가 — 2단계 정량 지표             | 3    | 4        | Phase 1·2·3·4| W2, ~1일 + 24h 운용 |
| 6  | 데모 빌드 — 통합 시연·캡처·PPT     | 4    | 4        | Phase 5      | W3, ~2일      |

**Dependency graph**: 1 → 2 → 3, 4 병렬, 5 ← (1·2·3·4), 6 ← 5 (시연 흐름은 3·4 활용)

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
**Plans**: TBD

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
**Plans**: TBD

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
  - [ ] 04-01-PLAN.md — Supabase 마이그레이션 010_watch_pipeline.sql (ALTER devices + 4 신규 테이블 + RLS + pg_cron + testuser1 시드)
  - [ ] 04-02-PLAN.md — j2208a/ Python 패키지 (decode/validate/aggregate/state_machine/derive/supabase_writer) + unit tests (≥23)
  - [ ] 04-03-PLAN.md — BLE notify → pipeline wiring + notifications/index.ts watch-alert action + .env.example
  - [ ] 04-04-PLAN.md — 24h 실측 운용 + verify_watch_24h.sql + VERIFICATION.md (non-autonomous)

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
     의료기기 면책 1줄 ("1차 경고용, 의료기기 아님") 이 단일 슬라이드 deck 으로
     통합됨 (DEMO-04).
**Plans**: TBD

---

## Coverage Validation

**Total v1.0 requirements**: 19
**Mapped**: 19/19 ✓
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

---

## Progress

| Phase                                | Plans Complete | Status      | Completed |
|--------------------------------------|----------------|-------------|-----------|
| 1. 비전 — 데모 영상 교체             | 0/1            | Not started | —         |
| 2. 비전 — frames 연속 룰             | 0/0            | Not started | —         |
| 3. 비전 — bbox 겹침/공간 매칭        | 0/0            | Not started | —         |
| 4. 워치 — J2208A 1인 파이프라인      | 0/0            | Not started | —         |
| 5. 평가 — 2단계 정량 지표            | 0/0            | Not started | —         |
| 6. 데모 빌드 — 통합 시연·캡처·PPT    | 0/0            | Not started | —         |
