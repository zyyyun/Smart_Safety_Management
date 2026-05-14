---
milestone: v1.0
name: "5월 PPT 데모"
status: in_progress
progress:
  phases_total: 6
  phases_done: 2
  phases_in_progress: 2
  requirements_total: 19
  requirements_validated: 10
last_activity: "2026-05-14 — Phase 3 PLANNED: 2 plans / 2 waves. Wave 1 = foundation (detect_all + fusion_helpers + fusion_configs + test stubs 1-7). Wave 2 = integration (scheduler wiring + migration 009 + test case 8 + DB push). D-04 suppression Option A locked (disabled flag). 다음 = /gsd-execute-phase 3."
---

# Smart Safety Management — State

## Current Position

Phase 1: ✓ COMPLETE (2026-05-06, A1 batch scan + D-19 fallback, 커밋 `559b90a`)
Phase 2: ✓ COMPLETE (2026-05-07, frames_required + pytest 8/8 PASS, 커밋 `954bb19`)
Phase 3: PLANNED ✓ (2026-05-14, 2 plans / 2 waves). 다음 = /gsd-execute-phase 3.
Phase 4: Wave 1·2 완료 (04-01·02·03), Wave 3 (04-04) = 사용자 24h 워치 착용 결정 대기
  - 04-01 ✓: 010_watch_pipeline.sql 운영 DB 적용 완료 (UTC immutability fix)
  - 04-02 ✓: j2208a/ 패키지 (8 모듈, 843 lines) + pytest 39 pass (31 + 04-03 의 8 integration)
  - 04-03 ✓: BLE wiring + watch-alert Edge Function 배포 + .env 보호 + curl smoke 200
  - 04-04 ⏸ : 24h 실측 — non-autonomous, 사용자 결정 대기 (5월 시연 전 진행 vs v1.1 이연)
Phase 5·6: not started (의존성 풀린 시점에 plan)
Status: Phase 3 CONTEXT 준비 완료. Phase 1/2 의 패턴 (`_detection_buffer` + N 연속 + cooldown) 을 fusion 으로 직교 확장. helmet 단독 알람은 fusion 이 인수 (Phase 1 D-05 의도된 부분 revert). 다음 단계 = planner 가 detect_all/_process_fusion_for_camera/fusion_helpers/FUSION_CONFIGS/마이그레이션 009/test_fusion 의 plan task 분해.
Last activity: 2026-05-14 — Phase 3 CONTEXT.md + DISCUSSION-LOG.md 작성 (autonomous).

## Accumulated Context

### Decisions

- 2026-04-28: YOLO26 마이그레이션 시점 = 5월 PPT 후 (6월~). v1.0 은 현 detector 그대로.
- 2026-04-28: 우선 트랙 = 모델 + 데이터. 후순위 = 인프라·통합.
- 2026-04-29: J2208A BLE 워치 v1.0 포함 결정. wear-state 임계값 잠정값 진행, 추가
  실험은 v1.1.
- 2026-04-29: GSD `.planning/` 부트스트랩 — `docs/PROJECT_SPEC.md` + 메모리 +
  `iridescent-percolating-fox.md` + `J2208A_안전관리_시스템_PLAN.md` 합성. PROJECT.md
  가 단일 source of truth, `docs/` 는 v0 보존본.
- 2026-04-29: Roadmap 6 phase 결정 — Phase 1·2·3 비전 chain (데이터 → frames → fusion),
  Phase 4 워치 병렬 (코드베이스 분리), Phase 5·6 통합 평가/데모. 19/19 REQs 매핑.
- 2026-05-02: Phase 1 컨텍스트 — 영상 소스 = 레거시 `발표자료용 영상/detection(fire,
  helmet).mp4` (39MB), fire/helmet 동일 mp4 매핑, Storage 새 키 (`source_v2.mp4`),
  detector_configs.py 영구 변경 (MODEL-03 흡수), 검증은 `--once-detect` 1회 + SQL.
- 2026-05-02: Phase 4 컨텍스트 — 기존 `devices` 확장 (mac_address·firmware·last_comm)
  + 신규 4 테이블 (raw_events·wear_state_events·minute_summary·safety_alerts);
  파이프라인 = `scripts/j2208a_sensor_reader.py` 인라인 (모듈 분리 `j2208a/`);
  wear-state 임계 = Python 상수 (v1.1 외부화); TTL = pg_cron + UNIQUE constraint
  dedup; 알림 = FCM only (`_shared/fcm.ts` 재사용); 24h 검증 = 실측 착용.
- 2026-05-02: Phase 4 plan — 4 plan / 3 wave. Wave 1 = 04-01 (마이그레이션) ∥ 04-02
  (j2208a/ 패키지 + 단위 테스트). Wave 2 = 04-03 (BLE wiring + watch-alert
  Edge Function + heartbeat). Wave 3 = 04-04 (24h 실측, non-autonomous).
  Iteration 2/3 PASSED — COMMS_LOST cold-start false-positive fix + XML escape +
  Test D-6 wording 보정 (커밋 `a54d71d` + `6998b3d`).
- 2026-05-04: Phase 1 Task 1·2 실행 완료 (커밋 `34cb4ec` detector_configs 운영급 임계,
  `022383c` upload_reference_videos SOURCES + remote_path). Storage 업로드도 성공
  (fire/source_v2.mp4 + helmet/source_v2.mp4) + cameras.live_url_detail (camera_id 1, 5)
  도 신규 URL 가리킴. **그러나 Task 3.3 검증 단계에서 empirical FAIL** — 신규 영상
  `발표자료용 영상/detection(fire, helmet).mp4` 가 기존 YOLO 가중치 (`fire_best.pt`,
  `hard_hat_best.pt`) 와 부적합.
- 2026-05-04 ~ 05-07: Phase 4 Wave 2 (04-03) 완료. 4 sub-commits — runtime.py (`7e8cac1`,
  367 lines + 8 integration tests 299 lines) + sensor_reader 재작성 (`8be85da`, 521 lines,
  BLE wiring + heartbeat + 백오프) + notifications watch-alert action 배포 (`1936ee6`,
  187 lines) + .env 보호 (`1e9e51a`, 40 lines). Iteration 중 COMMS_LOST cold-start
  false-positive fix + XML escape + Test D-6 wording 보정 (j2208a/derive.py 의
  pre-receipt grace 추가 — last_raw_ts=None 시 미발사).
- 2026-05-06: Phase 1 ✓ COMPLETE (커밋 `559b90a`) — A1 batch scan 종결 + D-19 fallback
  (fire conf 0.10) + helmet 정상 (conf 0.5+ target_classes ['head']). DATA-01·02·03 +
  MODEL-03 (partial — fire D-19 fallback) 충족.
- 2026-05-07: Phase 2 ✓ COMPLETE (커밋 `72e469b`/`5b0de16`/`954bb19`) — frames_required
  룰 + pytest 8/8 PASS. MODEL-01·02·03 모두 [x].
- 2026-05-12: Phase 4 SUMMARY backfill — git log + 코드 검증 결과 (39 pytest pass) 기반
  으로 04-01·02·03-SUMMARY.md 3건 작성. SDK 가 Wave 1·2 완료 인식하도록 정정. 04-04
  (24h 실측) 만 미실행 상태로 남김. STATE/ROADMAP Plan 체크박스 갱신.
- 2026-05-14: Phase 3 (FUSION) CONTEXT.md autonomous discuss 완료. 12개 결정 잠금 —
  `detect_all()` 신규 (D-01), fusion_helpers.py (D-02), FUSION_CONFIGS dict (D-03),
  helmet 단독 알람 fusion 대체 (D-04, Phase 1 D-05 부분 revert), `_process_fusion_for_camera`
  + `_fusion_buffer` Phase 2 패턴 (D-05·D-06), IoU>0.3 N=3 (D-07), hardhat_is_on point-in-area
  head 영역 top 25%×±width/6 N=3 (D-08), person detector cross-camera (D-09), pytest unit
  + 데몬 1회 통합 검증 (D-10), 마이그레이션 009 신규 event_type (D-11), 단독+fusion
  직교 (D-12). 검증 영상 신규 수집은 v1.1 검단·포천 이연 — v1.0 은 합성 입력 + mock.

### Blockers

- **(해소됨, 2026-05-06)** Phase 1 mp4 부적합 → A1 hybrid (AI-Hub fire + 자체촬영
  helmet) 적용 + D-19 fallback 으로 종결. Phase 1 ✓ COMPLETE.
- **(해소됨, 2026-05-04)** Phase 4 010 마이그레이션 운영 DB 적용 완료 (UTC cast fix).
- **현재 활성 blocker 없음.** 5월 PPT 데모 의제 = Phase 3 (bbox fusion) + Phase 5
  (eval 지표) + Phase 6 (시연·캡처·슬라이드). Phase 4 04-04 (24h 실측) 는 별도
  의사결정 대기.

### Awaiting User Input

- **04-04 (24h 실측) 의사결정** — non-autonomous. 다음 3 옵션 중 택일 필요:
  1. **(A) 5월 시연 전 실측** — 사용자가 J2208A 워치 24시간 연속 착용 + PC BLE
     상시 연결. 데모 슬라이드에 "24h 실측 데이터" 인용 가능 (강함). 단 5월 PPT
     일정 대비 시간 빠듯.
  2. **(B) v1.1 (6월 검단·포천 설치 직전) 으로 이연** — 5월 시연은 \"코드/단위
     테스트/Edge Function 동작\" 으로만 시연 (Wave 1·2 산출물). 6월 현장 설치 시
     실제 작업자 24h 운용으로 검증. 가장 자연스러운 일정.
  3. **(C) 짧은 PoC (2~4시간 자기 착용) 만 5월 시연용** — 1440 분 행 대신
     120~240 분 행 + REMOVED 5분 탈착 시나리오 1회 + FCM 푸시 도착 캡처. 04-04
     를 \"부분 PASS + 24h 는 v1.1\" 로 close.
- **추천:** (B) v1.1 이연 — 5월 PPT 의제 = 비전 5종 + 워치 로직 완성도 (코드 +
  39 pytest pass + Edge Function 배포). 24h 실측은 검단·포천 LP-3 RTSP 카메라
  설치와 함께 6월 진행.

### Pending Todos

- **Phase 3** (bbox 겹침/공간 매칭, FUSION-01·02) — plan 작성 후 execute. Phase
  1·2 완료된 상태라 진입 가능. `/gsd-discuss-phase 3` 또는 `/gsd-plan-phase 3`.
- **Phase 5** (평가 — 2단계 정량 지표, EVAL-01·02·03) — Phase 1·2·3·4 산출물
  의존, Phase 3 완료 후 진입.
- **Phase 6** (데모 빌드 — 통합 시연·캡처·PPT) — Phase 5 의 정량 지표 슬라이드
  완료 후 진입.
- **Phase 4 04-04** — 위 의사결정 결과에 따라 (A 즉시 실행 / B v1.1 이연 / C
  단축 PoC).

## Notes

- `gsd-sdk` CLI 미설치 → `git add` + `git commit` 직접 사용. 이후 `gsd-sdk` 설치
  시 `state.milestone-switch` / `commit` / `phases.clear` 핸들러로 전환 가능.
- GSD agents (`gsd-roadmapper`, `gsd-project-researcher`,
  `gsd-research-synthesizer`) 는 `~/.claude/agents/` 에 설치 확인됨.
- Dependency graph: Phase 1 → 2 → 3 (비전 chain), Phase 4 (워치) 병렬, Phase 5
  ← (1·2·3·4) 모두, Phase 6 ← Phase 5 (시연 흐름은 Phase 3·4 산출물 활용).
