---
milestone: v1.0
name: "5월 PPT 데모"
status: in_progress
progress:
  phases_total: 6
  phases_done: 0
  phases_in_progress: 2
  requirements_total: 19
  requirements_validated: 0
last_activity: "2026-05-04 — Phase 4 Wave 1 완료 (04-01 마이그레이션 + 04-02 j2208a/ 31 tests pass)"
---

# Smart Safety Management — State

## Current Position

Phase 1: WAITING_USER_INPUT (사용자 mp4 2개 제공 대기 — Task 1 PASS, Task 2 rev2 placeholder)
Phase 4: Wave 1 완료 (04-01 + 04-02), Wave 2 (04-03) 진입 가능 — 단 010 마이그레이션 운영 DB 적용 선행 필요
Plan: Phase 1 = 01-01-PLAN.md / Phase 4 = 04-01·02·03·04-PLAN.md
Status: Phase 4 Wave 1 = 코드/SQL 모두 작성 + 31 tests pass + 3 commits (`8a67962`, `e3a559c`, `2e28532`). 다음 = 사용자가 `supabase db push` 또는 Claude 에 위임 → 04-03 (Wave 2) 진입.
Last activity: 2026-05-04 — Phase 4 Wave 1 완료 (04-01 마이그레이션 파일 + 04-02 j2208a/ 패키지 31 tests pass)

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

- **(해소됨, 2026-05-04 사용자 결정)** Phase 1 Task 3.3 신규 mp4 부적합 → 옵션
  **A1 (AI-Hub 화재 + 자체 촬영 helmet 30s, 두 mp4 분리)** 선택. CONTEXT D-01·D-02
  rev2, D-18 (가중치 한계 명시), D-19 (fallback = conf 0.10 + frames_required 결합)
  추가. 01-01-PLAN.md Task 2 rev2 적용 — `LEGACY_DEMO_MP4` 단일 상수 폐기, 두
  별도 상수 `AI_HUB_FIRE_MP4` + `SELF_SHOT_HELMET_MP4` 로 placeholder 추가.

### Awaiting User Input

- **(Phase 4) 010_watch_pipeline.sql 운영 DB 적용 결정**:
  - 마이그레이션 파일 작성 + 커밋 완료 (`8a67962`). 운영 DB (`xbjqxnvemcqubjfflain`)
    적용은 미수행 — auto mode 의 "shared/production system 변경은 명시적 승인" 룰 준수.
  - 적용 명령: `cd D:/2026_산업안전/Smart_Safety_Management && supabase db push`
  - 사용자 승인 후 또는 Claude 에 위임 시 적용 → 04-03 (Wave 2) 진입 가능.
  - 미적용 상태에서 04-03 진입 시 BLE 클라이언트가 raw_events insert 실패 → Wave 2
    BLOCKED. 이 단계는 04-03 의 verify (1인 24h 운용 직전) 이전에 완료되어야 함.

- **(Phase 1) 사용자가 두 mp4 파일 준비 후 경로 제공 필요:**
  1. **AI-Hub 화재 mp4** — `project_legacy_assets.md` 의 "AI-Hub URL 3종 (화재/끼임/
     공사현장)" 중 화재 항목에서 다운로드. 권장 위치 예: `D:\2025_산업안전\산업안전\AI-Hub\화재\<선택>.mp4`. 크기 ≤ 50MB, 길이 30s~3min, 화염 명확.
  2. **자체 촬영 helmet 30s mp4** — 휴대폰으로 작업자가 helmet 미착용 + 착용 구간
     모두 포함 30s 촬영. 권장 위치 예: `D:\2025_산업안전\산업안전\자체촬영\helmet_30s.mp4`.
- **두 경로 제공 후 진행 단계 (Claude 가 자동 실행)**:
  1. `01-01-PLAN.md` Task 2 의 placeholder `<USER_PROVIDES: ...>` 두 곳을 실제
     경로로 교체 + 커밋
  2. `python ai_agent/scripts/upload_reference_videos.py --only fire,helmet` 실행
     (cwd=`ai_agent/`, env 주의 — 노트 참조)
  3. `cd ai_agent && python main.py --once-detect` (env 주의)
  4. `select event_id, event_type_id, accuracy, camera_id from detection_events
     where camera_id in (1,5) and created_at > now() - interval '5 minutes'` SQL
     검증
  5. accuracy ≥ 0.5 → Phase 1 PASS. 미달 → D-19 fallback 적용 검토 (사용자 재확인)
- **Storage 현재 상태 (참고)**: `reference-videos/{fire,helmet}/source_v2.mp4` 는
  현재 부적합 mp4 가 들어있음. 새 업로드 시 자동 덮어쓰기. cameras URL 도 이미
  source_v2.mp4 가리키므로 DB 변경 없음.

### Environment Notes (Phase 1 실행 시 필수)

- `.env` 위치: **`ai_agent/.env`** (프로젝트 루트가 아님)
- Python 실행: `cd ai_agent && python main.py --once-detect` (프로젝트 루트에서
  `python -m ai_agent.main` 은 ModuleNotFoundError)
- Supabase 환경변수: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` 가 `ai_agent/.env`
  에 있어야 함

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
