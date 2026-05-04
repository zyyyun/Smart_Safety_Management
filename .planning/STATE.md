---
milestone: v1.0
name: "5월 PPT 데모"
status: blocked
progress:
  phases_total: 6
  phases_done: 0
  requirements_total: 19
  requirements_validated: 0
last_activity: "2026-05-04 — Phase 1 EXECUTION BLOCKED at Task 3.3 (신규 mp4 + 기존 YOLO 가중치 부적합)"
---

# Smart Safety Management — State

## Current Position

Phase: 1 BLOCKED at Task 3.3 (Task 1·2 완료, 커밋 `34cb4ec`+`022383c`); Phase 4 planned
Plan: Phase 1 = 01-01-PLAN.md (Task 1·2 PASS, Task 3 empirical FAIL); Phase 4 = 04-01·02·03·04-PLAN.md
Status: Phase 1 사용자 결정 대기 (영상 교체 vs D-04 완화 vs YOLO 가중치 교체). Phase 4 진입 가능 (병렬).
Last activity: 2026-05-04 — Phase 1 EXECUTION BLOCKED at Task 3.3 (신규 mp4 + 기존 YOLO 가중치 부적합)

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

### Blockers

- **2026-05-04: Phase 1 Task 3.3 — 신규 mp4 가 기존 YOLO 가중치와 부적합.**
  - 측정 (cv2.VideoCapture 20프레임 균등 샘플, conf_thres=0.01, target_classes=None):
    - fire 최대 conf = 0.039 (t=177s), 평균 ≈ 0.015
    - helmet 최대 conf = 0.013, label='helmet' 만 (label='head' 미검출)
  - D-04 임계 (≥ 0.5) 와 empirical 격차 = 약 12배 (fire) / 38배 (helmet)
  - v0.5 임시조치 conf 0.10 으로 후퇴해도 실패 (max 0.039)
  - 기존 영상 (`모델 7종/화재 탐지/input.mp4`) 은 fire conf 0.10~0.14 검출 가능 →
    YOLO 가중치/파이프라인은 정상 동작. 문제는 신규 mp4 의 의미적 부적합.
  - CONTEXT D-01 가정 ("fire/helmet 두 이벤트가 한 영상에 모두 포함") 은 시각 검사
    기반이었고, YOLO 가중치 실측은 미수행 — planning gap.
  - **사용자 결정 필요:**
    - **A** 다른 영상 교체 (Task 2 LEGACY_DEMO_MP4 만 변경 후 Task 3 재실행)
    - **B** D-04 완화 (CONTEXT 수정 + Task 1 부분 revert)
    - **C** YOLO 가중치 교체 (AI-Hub fine-tune — 현재 v1.1 deferred)
  - 부분 롤백 SQL: `UPDATE cameras SET live_url=$old_url, live_url_detail=$old_url WHERE camera_id IN (1,5)` (기존 source.mp4 URL 로 환원, Storage 객체는 D-03 보존됨)

### Pending Todos

- Phase 1 execute: `/gsd-execute-phase 1` 진입 가능 (3 task autonomous)
- Phase 4 execute: `/gsd-execute-phase 4` 진입 가능 (Wave 1·2 autonomous, Wave 3 = 24h 실측)
- 비전 chain Phase 2·3 (frames 룰 + bbox fusion) — Phase 1 실행 후 진입
- 평가 Phase 5, 데모 Phase 6 — 의존성 풀린 시점에 plan
- 비전 Phase 2·3, 평가 Phase 5, 데모 Phase 6 — 의존성 풀린 시점에 plan 작성

## Notes

- `gsd-sdk` CLI 미설치 → `git add` + `git commit` 직접 사용. 이후 `gsd-sdk` 설치
  시 `state.milestone-switch` / `commit` / `phases.clear` 핸들러로 전환 가능.
- GSD agents (`gsd-roadmapper`, `gsd-project-researcher`,
  `gsd-research-synthesizer`) 는 `~/.claude/agents/` 에 설치 확인됨.
- Dependency graph: Phase 1 → 2 → 3 (비전 chain), Phase 4 (워치) 병렬, Phase 5
  ← (1·2·3·4) 모두, Phase 6 ← Phase 5 (시연 흐름은 Phase 3·4 산출물 활용).
