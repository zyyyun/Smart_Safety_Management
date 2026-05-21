# Phase 1: 비전 — 데모 영상 교체 - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

helmet/fire 데모 영상 2종을 운영급 임계 (helmet `target_classes=['head']` +
`conf_thres ≥ 0.5`, fire `conf_thres ≥ 0.5`) 에서 검출 가능한 새 영상으로 교체.
`reference-videos` 버킷에 신규 객체 업로드 + `cameras.live_url_detail` 갱신 +
`scripts/upload_reference_videos.py` 의 `--only fire,helmet` 부분 재업로드 검증
까지가 본 phase 의 범위. 다른 3종 (쓰러짐 · 지게차 · 사람) 영상은 무손상 보존.

**Out of scope** (다른 phase 에서 처리):
- frames_required 룰 (Phase 2 — MODEL-01/02)
- bbox 겹침 / 공간 매칭 (Phase 3 — FUSION)
- 검증 셋 100장 라벨링 + 자동 평가 스크립트 (Phase 5 — EVAL-02)
- AI-Hub 데이터셋 다운로드, fine-tune (v1.1+ Future)

</domain>

<decisions>
## Implementation Decisions

### 데이터 소스 (2026-05-04 재정의 — A1 hybrid 후 EMPIRICAL 검증 결과로 D-01·02 수정)

**~~D-01·D-02 (구버전, 2026-05-02)~~ 폐기됨 — 사유는 D-18 참조.**

- **D-01 (rev2, 2026-05-04): fire/helmet 영상 분리 — AI-Hub fire mp4 + 자체 촬영 helmet 30s mp4**
  - **fire 영상**: AI-Hub 화재 데이터셋 다운로드 mp4 (`project_legacy_assets.md` 의
    "AI-Hub URL 3종 (화재/끼임/공사현장)" 참조). 사용자가 다운로드 후 경로 제공.
    예상 위치: `D:\2025_산업안전\산업안전\AI-Hub\화재\<선택>.mp4`
    선정 기준: 크기 ≤ 50MB, 길이 30s~3min, 화염이 명확히 보이는 구간 포함
  - **helmet 영상**: 사용자 휴대폰 30s 자체 촬영 mp4. 작업자가 helmet 미착용
    상태에서 카메라 정면을 본 구간 + helmet 착용 상태 구간을 동시에 포함하여
    head 검출 가능성 ↑. 예상 위치: `D:\2025_산업안전\산업안전\자체촬영\helmet_30s.mp4`
  - **두 영상 분리** = `cameras.camera_id=1` (fire) 과 `camera_id=5` (helmet) 의
    `live_url_detail` 이 *서로 다른* Storage 객체를 가리킴
  - **선결 조건**: 사용자가 두 mp4 파일 준비 후 경로 제공해야 Task 2/3 재실행 가능

- **D-02 (rev2, 2026-05-04): fire/helmet 분리 매핑 (이전 단일 mp4 매핑 폐기)**
  - `SOURCES["fire"]["src"]` = AI-Hub fire mp4 경로
  - `SOURCES["helmet"]["src"]` = 자체 촬영 helmet 30s mp4 경로
  - 두 항목 모두 `kind="mp4"` (image_loop X)
  - `LEGACY_DEMO_MP4` 공통 상수 폐기 — 두 개의 별도 상수 (`AI_HUB_FIRE_MP4`,
    `SELF_SHOT_HELMET_MP4`) 사용
  - Storage remote_path = `f"{event_key}/source_v2.mp4"` 그대로 (D-03 unchanged)
    — 같은 키 덮어쓰기. 이전 시도의 `source_v2.mp4` 가 새 mp4 로 교체됨

### Storage 경로
- **D-03: 신규 파일은 새 객체 키로 업로드, 기존 파일은 보존 (롤백 가능)**
  - fire: `reference-videos/fire/source.mp4` (legacy `input.mp4` 기반) →
    `reference-videos/fire/source_v2.mp4` (legacy `detection(fire, helmet).mp4` 기반)
  - helmet: `reference-videos/helmet/source.mp4` (legacy hard_hat_images image_loop) →
    `reference-videos/helmet/source_v2.mp4` (동일 detection mp4 복사)
  - `x-upsert: true` 동작은 그대로지만, 새 키이므로 사실상 신규 INSERT
  - `cameras.live_url_detail` 만 신규 URL 로 교체. 문제 발생 시 URL 만 원복하면 즉시 롤백

### 운영급 임계 영구 적용 (MODEL-03 흡수)
- **D-04: Phase 1 에서 `detector_configs.py` 영구 변경 — Phase 2 의 MODEL-03 사실상 흡수**
  - `DETECTOR_CONFIGS["fire"]["conf_thres"]` 0.10 → **0.5** (또는 그 이상으로 검출 결과
    보고 미세조정)
  - `DETECTOR_CONFIGS["helmet"]["conf_thres"]` 0.10 → **0.5**
  - `DETECTOR_CONFIGS["helmet"]["target_classes"]` `None` → **`['head']`**
  - `forklift` / `person` conf 는 변경 없음 (이미 운영급 0.25 / 0.30)
  - 근거: Phase 1 의 SC #2/#3 가 "신규 영상이 운영급 임계에서 검출 가능" 을 요구 →
    검증과 영구 적용을 분리할 이유가 없음. ROADMAP 의 phase 분리는 조명이 아닌 논리
    단위. Phase 2 의 MODEL-03 은 자연스럽게 "이미 적용됨" 으로 마감.
- **D-05: detector_configs.py 의 임시조치 주석 (`# 데모용 하향`, `# helmet 위주라
  검출`) 도 함께 제거 또는 운영급 주석으로 갱신**

### 검증 방식
- **D-06: 검증 = `--once-detect` 1회 + `detection_events` SQL 조회**
  - `python -m ai_agent.main --once-detect` 1회 실행 후
    `select event_id, event_type_id, accuracy, camera_id from detection_events
    order by created_at desc limit 10;` 로 fire/helmet 행 + accuracy ≥ 0.5 검증
  - 5번 반복하거나 자동 스크립트는 추가하지 않음 — Phase 5 EVAL-02 에서 100장
    검증 셋으로 별도 평가
  - SC #2/#3 의 "≥ 1프레임 검출" 조건은 단일 cycle 로 충분

### 학습 사항 (2026-05-04 추가)

### 실측 결과 (2026-05-04 batch scan + Task 3 PASS)

- **D-20: fire 가중치 한계 — D-19 fallback 발동, helmet 은 정상 통과**
  - **fire batch scan** (사용자 제공 3 clip × 72 frames each, conf=0.05):
    - 불꽃/0087: max 0.136, ≥0.5: 0건
    - 연기/0096: max 0.093, ≥0.5: 0건
    - 정상/0077: max 0.156, ≥0.5: 0건
  - **결론**: 보유 fire_best.pt 가중치는 사용자 제공 데이터셋과 학습 분포 불일치.
    옵션 A (다른 clip 측정) 시도했으나 모든 clip 0.5 미달. **D-19 fallback 발동**.
    `conf_thres = 0.10` (v0.5 baseline). v1.1 의 fine-tune 으로 진정한 0.5+ 도달.
  - **helmet batch scan** (H0 24 시퀀스 × 10 frames):
    - best = `H0/L2_D2023-08-31-09-08_001` (head 9/10 sample, max conf 0.770)
    - 다른 시퀀스들은 helmet 라벨만 검출, head 0건 — H0 표시지만 head 가시성 부재
  - **helmet 새 영상 = `helmet_h0_demo.mp4`** (L2_D2023-08-31-09-08_001 × stream_loop 2 = 30s,
    seek=10s 시 head conf 0.697). D-04 정상 통과, D-05 ['head'] 정상 통과.

- **D-21: scheduler `DETECTORS_DEMO_SEEK_SEC` = 10.0 으로 변경**
  - fire 0087 frame 300 (10s) 에서 max conf 0.142, helmet demo (30s loop) seek=10s 시
    head conf 0.697. 두 detector 공통 seek = 10s.
  - `ai_agent/.env` 의 `DETECTORS_DEMO_SEEK_SEC=0.0` → `10.0`.

- **D-22: D-04 의 conf 0.5 임계는 helmet 만 적용, fire 는 D-19 fallback**
  - detector_configs.py:
    - `fire.conf_thres` = 0.10 (D-19 fallback, v0.5 baseline 그대로)
    - `helmet.conf_thres` = 0.5 (D-04 정상)
    - `helmet.target_classes` = ['head'] (D-05 정상)
  - **Task 3 검증 PASS** (커밋 hash TBD): event_id 22-25 모두 적재.
    fire cam_id=1 conf 0.134, helmet cam_id=5 label='head' conf 0.687.

- **D-18 (구) — 보유 YOLO 가중치 (`fire_best.pt`, `hard_hat_best.pt`) 의 empirical 한계**
  - cv2.VideoCapture 20프레임 균등 샘플 측정 (conf_thres=0.01, target_classes=None):
    - 신규 mp4 (`발표자료용 영상/detection(fire, helmet).mp4`): fire max=0.039,
      helmet max=0.013 (label='helmet' 만, label='head' 0건)
    - 기존 mp4 (`모델 7종/화재 탐지/input.mp4`): fire conf 0.10~0.14 (v0.5 검증됨)
  - **결론**: 현 가중치로 conf 0.5+ 도달 불가. D-04 의 "운영급 0.5+" 는 가중치
    한계 미검증 상태에서 lock 됨 (planning gap).
  - **A1 (이번 결정) 의 의미**: AI-Hub 화재 데이터셋이 학습 데이터셋과 더 유사할
    가능성 → fire conf 0.5+ 시도 가능. helmet 자체 촬영은 명확한 head 노출로
    label='head' 검출 시도. 둘 다 실패 시 → **fallback 경로 필요**.
  - **Fallback 결정 (D-19)**: A1 으로도 0.5+ 미달성 시 자동으로 conf 0.10 +
    Phase 2 의 frames_required (fire 5, helmet 3) 결합 = 운영급 의미. CONTEXT
    D-04 부분 revert + Phase 1 의 실질적 성공 기준을 "Phase 1+2 combined" 로
    재정의. 이 경우 Phase 1 은 PARTIAL-PASS 상태로 진행, Phase 2 완료 시점에
    full-pass 표시.
  - **v1.1 액션 (별개)**: AI-Hub + 자체 라벨링 데이터셋으로 fine-tune (옵션 C
    원래 계획). v1.0 5월 PPT 데모 일정 보호 위해 fine-tune 은 v1.1 유지.

### Claude's Discretion
- conf_thres 0.5 vs 0.55 vs 0.6 미세 조정은 D-04 적용 후 detection_events.accuracy
  실측치 보고 결정 — 실측치 분포 보고 false positive 줄이는 방향으로
- ffmpeg 재인코딩이 정말 필요한지 검증 (`ffprobe` 결과상 이미 호환되지만, 일부 player
  호환성 우려 시 가벼운 재인코딩) — 우선 원본 그대로 시도, 실패 시 재인코딩
- A1 의 두 mp4 별도 상수명 (`AI_HUB_FIRE_MP4`, `SELF_SHOT_HELMET_MP4`) 또는 단순
  inline 경로 — 가독성 우선 별도 상수 권장
- D-19 fallback 자동 적용 vs 사용자 재확인 — A1 시도 후 empirical 결과 사용자
  확인 후 decide

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 1 직접 입력
- `.planning/REQUIREMENTS.md` §1 데이터 트랙 (DATA-01·02·03) — 이 phase 의 요구사항
- `.planning/ROADMAP.md` Phase 1 섹션 — Goal · Success Criteria · Depends on
- `C:\Users\ANNA\.claude\plans\iridescent-percolating-fox.md` §A "데이터 트랙 — 의미
  일관 영상 교체 (W1, ~3일)" — 본 phase 의 원천 컨텍스트

### Phase 2/3 와의 경계
- `.planning/REQUIREMENTS.md` §2 모델 트랙 단계 1 (MODEL-01·02·03) — Phase 2 범위
  이지만 본 phase 의 D-04 가 MODEL-03 을 흡수하므로 영향 받음

### 코드/스키마 출처
- `ai_agent/scripts/upload_reference_videos.py` — `SOURCES` dict, `process_one`,
  `update_camera_url`, `--only` 인자
- `ai_agent/detector_configs.py` — `DETECTOR_CONFIGS` (fire/helmet conf, target_classes)
- `migrations/00*.sql` — `cameras` 테이블 스키마 (camera_id 1·5 의 type 컬럼 확인용)

### 환경 / Storage
- `D:\2025_산업안전\산업안전\발표자료용 영상\detection(fire, helmet).mp4` — 신규 영상
  원본 (위 D-01)
- Supabase `reference-videos` 버킷 — 50 MB 제한 · public · video/* mime

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ai_agent/scripts/upload_reference_videos.py`**: 거의 그대로 사용. 변경
  사항은 (1) `SOURCES["fire"]["src"]` 와 `SOURCES["helmet"]` 항목을 새 mp4
  경로로 갱신, (2) `helmet` 의 `kind` 를 `image_loop` → `mp4` 로 전환 (영상이라
  loop 불필요), (3) Storage remote_path 가 `f"{event_key}/source.mp4"` 였던 것
  을 `f"{event_key}/source_v2.mp4"` 로 변경 (D-03).
- **`ensure_bucket`** (line 117): 50 MB 제한 + video mime 화이트리스트 그대로
  적합. `detection(fire, helmet).mp4` 39 MB 로 통과.
- **`update_camera_url`** (line 151): `live_url` 과 `live_url_detail` 둘 다
  업데이트하는 패턴 그대로.
- **`fire_best.pt` / `hard_hat_best.pt`** (`D:\2025_산업안전\산업안전\모델 7종\`):
  YOLOv5 detector 그대로 — 모델 변경 없음. conf 만 조정.

### Established Patterns
- **Storage 경로 컨벤션**: `reference-videos/{event_key}/{filename}.mp4` —
  D-03 의 새 객체 키도 같은 컨벤션 따름 (`fire/source_v2.mp4`, `helmet/source_v2.mp4`).
- **`--only fire,helmet` 분할 업로드**: `upload_reference_videos.py:236-240` 의
  `args.only` 처리 — 이미 정상 동작. 다른 3종 (쓰러짐 · 지게차 · 사람) 은
  `targets` 리스트에서 제외되어 무손상.
- **`cameras.live_url_detail` 와 `live_url` 동시 갱신**: `update_camera_url:152`
  — 두 컬럼 동시. ai_agent 가 `live_url_detail` 우선 사용, fallback 으로 `live_url`.
- **`detector_configs.py` 의 임시조치 주석 컨벤션**: `# 레거시 fire 영상의 fire
  검출 conf 가 0.10~0.15 수준이라 데모용 하향. 운영 시 올려서 ...` (line 30-31)
  — D-05 에서 이런 주석 제거/갱신.

### Integration Points
- `ai_agent/main.py` 의 `--once-detect` 플래그가 `scheduler.run_detection_cycle`
  을 1회 호출 → 5종 모두 처리 → `detection_events` insert. Phase 1 의 검증
  (D-06) 은 이 단일 명령으로 수행.
- `cameras` 테이블의 `camera_id=1` (fire), `camera_id=5` (helmet) 행이 미리
  존재해야 `update_camera_url` 이 1행 갱신. v0.5 부터 시드되어 있음.
- `event_types` 테이블에 '화재', '안전모 미착용' 가 시드되어 있음 (`migrations/008_event_types_extension.sql`) — `create_ai_event` Edge Function 이
  자동 매핑.

</code_context>

<specifics>
## Specific Ideas

- **"의미 일관성"**: helmet 영상이 head 가 잡혀야 의미 있음 — `image_loop` 의 정적
  이미지가 아니라 사람이 움직이는 동영상이어야 함. `detection(fire, helmet).mp4` 는
  실제 발표용 영상이라 작업자가 등장 → 조건 충족 (D-01 의 핵심 동기).
- **fire 영상의 conf 0.10 임시조치 원인**: 기존 `input.mp4` 의 fire 검출 conf 가
  0.10~0.15 수준에 머무름 → 새 영상 (`detection(fire, helmet).mp4`) 에서 fire 가
  conf 0.5+ 로 잡혀야 D-04 의 영구 적용이 정당화됨. 검증 단계에서 이 부분 명시 확인.
- **레거시 폴더 의존성 유지**: `LEGACY_BASE = Path(r"D:\2025_산업안전\...")` 의
  하드코딩은 v1.0 범위 밖 (v1.1 또는 cleanup 시 워크스페이스로 복사 검토). 본
  phase 에서는 그대로 둠.

</specifics>

<deferred>
## Deferred Ideas

- **AI-Hub 데이터셋 다운로드 + fine-tune** — 본 phase 의 영상 교체로 충분한 검출
  성능 확보 시 v1.1 (검단·포천 현장 데이터 수집 단계) 또는 v2.0 (YOLO26 마이그레이션)
  으로 미룸. AI-Hub 3종 (화재/끼임/공사현장) URL 은 메모리 `project_legacy_assets.md`
  에 기록.
- **자체 촬영 30s mp4** — 검단·포천 현장 설치 (6월~) 시 현장 작업자 영상 직접 수집.
  v1.1 데이터셋 보강.
- **레거시 `LEGACY_BASE` 경로 워크스페이스 내재화** — `D:\2025_산업안전\...` 의존성
  제거 (현 PC 외에서 빌드 불가). v1.1 또는 별도 cleanup phase.
- **fire/helmet 구간 분할 인코딩** — 한 영상 동일 매핑 (D-02) 으로 충분하지만,
  detection_events 의 시각적 구분 명확성 우려 시 v1.1 에서 ffmpeg 으로 분할.
- **Phase 5 EVAL-02 와의 연계** — 본 phase 의 단일 cycle 검증 (D-06) 이 통과한 후
  Phase 5 에서 100장 검증 셋으로 정량 평가. Phase 1 의 결과는 Phase 5 의 입력 영상.
- **detection_events 보존/정리 정책** — Phase 1 검증으로 새 행이 누적되는데, 데모
  진입 전 일괄 정리 (delete) vs 누적 유지 결정은 v1.0 데모 빌드 (Phase 6) 에서
  검토.

</deferred>

---

*Phase: 1-vision-demo-videos*
*Context gathered: 2026-05-02*
