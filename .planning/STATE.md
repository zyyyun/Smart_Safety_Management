---
milestone: v1.0
name: "5월 PPT 데모 + 수요일 추가 (워치-앱·RTSP·TBM)"
status: in_progress
progress:
  phases_total: 9
  phases_done: 3
  phases_in_progress: 1
  requirements_total: 28
  requirements_validated: 14
phases_planned: 1
last_activity: "2026-05-14 — Phase 7 Plan 02 (Edge Function notifications watch-ack/pair) ✓ COMPLETE. notifications/index.ts 에 case 'watch-ack' (T-7-02 ownership SQL + idempotency .is(ack_at,null) + T-7-05 server-side toISOString) + case 'watch-pair' (T-7-03 MAC 정규식 재검증 + 다른 worker paired → 409 + unpair-aware 3-tier 룩업) 추가. 운영 Deno Deploy 3회 배포 (script size 68.59kB final). 8 curl smoke 모두 PASS (ack 3 + pair 5 — 정상/idempotency/ownership/MAC invalid/spoofing/unpair/re-pair). Pitfall 5 회귀 가드 (acknowledged_at == 0) + D-09 알림 전이 회귀 가드 (notifications insert 5분 윈도우 0행) 모두 통과. Rule 3 deviations 3건 (_shared/{supabase,response,cors}.ts stash 복원 + .env 작성 + Pitfall 5 코멘트 우회) + Rule 1 deviation 1건 (re-pair after unpair unique constraint → serial_number fallback 추가) 모두 해소. 커밋 e2298a2·3eb872d. Wave 3 (07-03 Android UI) 진입 가능."
---

# Smart Safety Management — State

## Current Position

Phase 1: ✓ COMPLETE (2026-05-06, A1 batch scan + D-19 fallback, 커밋 `559b90a`)
Phase 2: ✓ COMPLETE (2026-05-07, frames_required + pytest 8/8 PASS, 커밋 `954bb19`)
Phase 3: ✓ COMPLETE (2026-05-14) — Plan 01 ✓ (detect_all + fusion_helpers + fusion_configs + 12/12 tests, 커밋 729a1b4·cd2528a·40a9224) + Plan 02 ✓ (scheduler fusion wiring + D-04 disabled + migration 009 DB push + 22/22 tests, 커밋 769a0fc·a2a31c8·d546fb5). FUSION-01·02 완료.
Phase 4: Wave 1·2 완료 (04-01·02·03), Wave 3 (04-04) = 사용자 24h 워치 착용 결정 대기
  - 04-01 ✓: 010_watch_pipeline.sql 운영 DB 적용 완료 (UTC immutability fix)
  - 04-02 ✓: j2208a/ 패키지 (8 모듈, 843 lines) + pytest 39 pass (31 + 04-03 의 8 integration)
  - 04-03 ✓: BLE wiring + watch-alert Edge Function 배포 + .env 보호 + curl smoke 200
  - 04-04 ⏸ : 24h 실측 — non-autonomous, 사용자 결정 대기 (5월 시연 전 진행 vs v1.1 이연)
Phase 5·6: not started (의존성 풀린 시점에 plan)
Phase 7: ⚠ IN PROGRESS (2026-05-14) — 워치-앱 양방향 연동 (BRIDGE-01·02·03). 수요일 2026-05-20 마감.
  - 07-01 ✓ COMPLETE (2026-05-14): supabase-kt 2.2.0 + ktor-cio 2.3.9 + desugar 2.0.4 의존성 lock + BuildConfig (실제 linked project ref `xbjqxnvemcqubjfflain`) + ProGuard keep 룰. 011_watch_app_rls.sql 운영 DB 적용 완료 (RLS narrowing 4종 + supabase_realtime publication 4 테이블 ADD). tests/sql/test_011_rls_isolation.sql + scripts/seed_watch_demo.py (D-05 fallback). 커밋 ddf2def·92bed99·4be6d2c.
  - 07-02 ✓ COMPLETE (2026-05-14): notifications/index.ts case 'watch-ack' (BRIDGE-02) + case 'watch-pair' (BRIDGE-03) 운영 배포. 8 curl smoke 모두 PASS — watch_ack 3종 (정상 200 / idempotency 404 / ownership 404) + watch_pair 5종 (정상 200 / MAC invalid 400 / spoofing 409 / unpair 200 / re-pair idempotent 200). T-7-02·03·05 mitigation. D-09 알림 전이 회귀 가드 통과 (notifications insert 0). 커밋 e2298a2·3eb872d.
  - 07-03 ⏸ : Android UI (MyApp SupabaseClient 싱글톤 + watch/ 패키지 8 파일 + HomeWorker ComposeView 카드 + SafetyAlertsActivity + DeviceManage 워치 섹션).
  - 07-04 ⏸ : 단축 PoC + E2E 시연 (autonomous: false).
Phase 8: NEW (2026-05-14 추가) — Drift X3 RTSP 실시간 카메라 (RTSP-01·02·03). ai_agent mp4 → RTSP 전환 + 실기기 검증 + 재연결 안정성.
Phase 9: NEW (2026-05-14 추가) — TBM 현장 작업자 가이드 (TBM-01·02·03). 4 신규 테이블 + Android 가이드 화면 + 미참여 알림. 기존 관리자 순회 점검과 동시 운용.
Status: Phase 3 ✓ COMPLETE + Phase 7 Wave 1·2 ✓ COMPLETE (07-01 인프라 + 07-02 Edge Function watch-ack/pair). 다음 즉시 = /gsd-execute-plan 7 03 (Android UI — MyApp SupabaseClient 싱글톤 + watch/ 패키지 + HomeWorker 카드 + SafetyAlertsActivity + DeviceManage 워치 섹션).
Last activity: 2026-05-14 — Phase 7 Plan 02 (Edge Function notifications watch-ack/pair) ✓ COMPLETE — case 2개 추가 + 운영 배포 + 8/8 curl smoke PASS + D-09 회귀 가드 통과.

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
- 2026-05-14: Phase 3 Plan 01 COMPLETE — detect_all() (D-01), fusion_helpers.py pure
  functions (D-02), FUSION_CONFIGS dict (D-03) 구현 완료. Legacy bbox_utils.py y2 clamp
  버그 수정 (`if y2 < 0: y1 = 0` → `y2 = max(0.0, ...)`). T-03-01 (ZeroDivisionError guard)
  + T-03-02 (frame_width=0 clamp) 위협 완화. test_fusion.py 12/12 PASS (Phase 2 포함 20/20
  full suite). 커밋 729a1b4·cd2528a·40a9224.
- 2026-05-14: Phase 3 Plan 02 COMPLETE + Phase 3 종결. scheduler.py fusion wiring
  (_fusion_buffer + _process_fusion_for_camera + fusion loop + D-04 disabled guard) +
  migration 009 운영 DB 적용 + 22/22 pytest PASS. Code review = 0 critical / 3 warnings
  (WR-01 temp file leak, WR-02 helmet label divergence, WR-03 y1>=y2 guard) — 후속 처리
  대상. HUMAN-UAT (DB row 확인) approved.
- **2026-05-14 (마일스톤 확장)**: 사용자 추가 요청 — 수요일 2026-05-20 까지 (1) 워치-앱
  양방향 연동, (2) Drift X3 RTSP 실시간 카메라, (3) TBM 현장 작업자 가이드. ROADMAP/
  REQUIREMENTS 갱신: Phase 6→9 (Phase 7 BRIDGE-01·02·03 수요일 마감, Phase 8
  RTSP-01·02·03, Phase 9 TBM-01·02·03 추가). REQ 19→28. 마일스톤 명 "5월 PPT 데모" →
  "5월 PPT 데모 + 수요일 추가". 다음 = `/gsd-discuss-phase 7` (수요일 마감 critical).
- 2026-05-14: Phase 3 Plan 02 COMPLETE — scheduler fusion wiring: _fusion_buffer module dict
  + _process_fusion_for_camera (7-step, D-05) + disabled guard (D-04) + fusion loop in
  run_detection_cycle (D-12). Rule 1 fix: camera dict key mismatch (`camera["id"]` →
  `camera["camera_id"]`) caught pre-implementation. Migration 009 `지게차 충돌 위험` applied
  to production Supabase DB via `supabase db push --include-all`. T-03-04/06/08 위협 완화.
  22/22 pytest PASS. FUSION-01·02 완전 충족. 커밋 769a0fc·a2a31c8·d546fb5.
- **2026-05-14 (Phase 7 Plan 02 COMPLETE)**: Edge Function notifications/index.ts 의 case 'watch-ack' (BRIDGE-02) + case 'watch-pair' (BRIDGE-03) 운영 배포 완료. (a) case 'watch-ack' = ownership SQL `device_id IN (SELECT FROM devices WHERE user_id=$user_id)` (T-7-02 mitigation) + idempotency `.is('ack_at', null)` 가드 (재호출 시 0행 + 404) + 서버측 `new Date().toISOString()` (T-7-05 clock spoofing 방어). (b) case 'watch-pair' = MAC 정규식 `/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/` 재검증 + toUpperCase 정규화 + 두 lookup 경로 (mac eq → serial fallback) 모두 user_id 충돌 시 409 (T-7-03 spoofing 차단) + unpair-after-pair 케이스 호환 3-tier 룩업. (c) 8 curl smoke 모두 PASS — watch_ack 3종 (200/404/404) + watch_pair 5종 (200/400/409/200/200). (d) Pitfall 5 회귀 가드 (`acknowledged_at` grep == 0) + D-09 알림 전이 회귀 가드 (notifications insert 5분 윈도우 0행) 모두 통과. (e) Deviations: Rule 3 × 3 (`_shared/{supabase,response,cors}.ts` stash 복원 + `.env` 작성 + Pitfall 5 코멘트 우회) + Rule 1 × 1 (re-pair after unpair → unique constraint → serial_number fallback 추가, `'already paired to another user'` 등장 횟수 1→2 강도 강화). 커밋 e2298a2·3eb872d. Wave 3 (Android UI) 진입 가능.
- **2026-05-14 (Phase 7 Plan 01 COMPLETE)**: 워치-앱 양방향 인프라 토대 lock. (a) app/build.gradle.kts
  에 supabase-kt 2.2.0 + ktor-cio 2.3.9 + desugar_jdk_libs 2.0.4 + BuildConfig (SUPABASE_URL/
  ANON_KEY 운영 ref `xbjqxnvemcqubjfflain`) 추가, 회귀 가드 2종 (3.x 거부 + okhttp engine 거부)
  통과. (b) app/proguard-rules.pro 에 io.github.jan.supabase.* + io.ktor.* + kotlinx.serialization.*
  keep 룰 (Pitfall 8 v1.1 minify 대비). (c) supabase/migrations/011_watch_app_rls.sql 운영 DB
  적용 — `supabase db push` 성공, NOTICE 4종 (정책 first-time creation), PostgREST anon SELECT
  200 OK 검증. 003 의 device_watches_select USING(true) DROP + safety_alerts/wear_state_events/
  device_watches/devices SELECT × 4 narrowing (mac_address IS NOT NULL 패턴 v1.0 PoC) +
  supabase_realtime publication 4 테이블 ADD. (d) tests/sql/test_011_rls_isolation.sql + scripts/
  seed_watch_demo.py (D-05 fallback, urllib + service_role key, REMOVED 시나리오 minute_summary
  120 + wear_state_events 3 + safety_alerts 2). Deviations 4건 모두 자동 해소: Rule 1 (SUPABASE_URL
  정정 — plan 의 stale ref) + Rule 3 × 3 (grep regex false-positive 2 + supabase migration history
  stash 복원 1). T-7-01 mitigated, T-7-04 accepted v1.0, T-7-supply mitigated. 커밋 ddf2def·92bed99·
  4be6d2c.

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

- **⚠ Phase 7 (CRITICAL — 수요일 2026-05-20 마감)** — 워치-앱 양방향 연동
  (BRIDGE-01·02·03). Wave 1·2 완료 (07-01 인프라 + 07-02 Edge Function watch-ack/pair).
  남은 단계: Wave 3 = `/gsd-execute-plan 7 03` (Android UI — MyApp SupabaseClient 싱글톤 +
  watch/ 패키지 + HomeWorker 카드 + SafetyAlertsActivity + DeviceManage 워치 섹션 + 4 unit test) →
  Wave 4 = `/gsd-execute-plan 7 04` (단축 PoC + E2E 시연, autonomous: false).
- **Phase 8** (Drift X3 RTSP 실시간 카메라, RTSP-01·02·03) — `ai_agent` mp4 →
  RTSP 전환 + 실기기 검증 + 재연결 안정성. Phase 1 cameras 매핑 활용.
- **Phase 9** (TBM 현장 작업자 가이드, TBM-01·02·03) — 4 신규 테이블 + Android
  가이드 화면 + 미참여 알림. 기존 관리자 순회 점검과 병렬 운용. 비전·워치와
  코드베이스 분리.
- **Phase 3** ✓ COMPLETE (2026-05-14) — FUSION-01·02 완전 충족.
- **Phase 5** (평가 — 2단계 정량 지표, EVAL-01·02·03) — Phase 1·2·3 완료됨. Phase 4 04-04
  의사결정 결과에 따라 병렬 진입 가능. `/gsd-discuss-phase 5` 또는 `/gsd-plan-phase 5`.
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
