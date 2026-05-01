---
milestone: v1.0
name: "5월 PPT 데모"
status: planning
progress:
  phases_total: 6
  phases_done: 0
  requirements_total: 19
  requirements_validated: 0
last_activity: "2026-05-02 — Phase 4 context gathered (CONTEXT.md, 13 decisions)"
---

# Smart Safety Management — State

## Current Position

Phase: 1 (planned, ready to execute) + Phase 4 (context gathered, awaiting plan-phase) — 병렬 트랙
Plan: 01-01-PLAN.md (Phase 1, 3 tasks); Phase 4 plan TBD
Status: Phase 1 = 실행 진입 가능 / Phase 4 = /gsd-plan-phase 4 진입 가능
Last activity: 2026-05-02 — Phase 4 context gathered (CONTEXT.md, 13 decisions)

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

### Blockers

- (없음)

### Pending Todos

- Phase 1 execute: `/gsd-execute-phase 1` 진입 가능 (3 task autonomous, ROADMAP SC #1-#4 매핑됨)
- Phase 4 plan: `/gsd-plan-phase 4` 진입 가능 (CONTEXT.md 13 decisions 캡처됨)
- 비전 Phase 2·3, 평가 Phase 5, 데모 Phase 6 — 의존성 풀린 시점에 plan 작성

## Notes

- `gsd-sdk` CLI 미설치 → `git add` + `git commit` 직접 사용. 이후 `gsd-sdk` 설치
  시 `state.milestone-switch` / `commit` / `phases.clear` 핸들러로 전환 가능.
- GSD agents (`gsd-roadmapper`, `gsd-project-researcher`,
  `gsd-research-synthesizer`) 는 `~/.claude/agents/` 에 설치 확인됨.
- Dependency graph: Phase 1 → 2 → 3 (비전 chain), Phase 4 (워치) 병렬, Phase 5
  ← (1·2·3·4) 모두, Phase 6 ← Phase 5 (시연 흐름은 Phase 3·4 산출물 활용).
