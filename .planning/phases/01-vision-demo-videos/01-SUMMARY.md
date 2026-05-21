# Phase 1: 비전 — 데모 영상 교체 - SUMMARY

**Status:** ✓ COMPLETE (2026-05-06)
**Outcome:** PASS — DATA-01·02·03 + MODEL-03 absorbed (partial — D-19 fallback for fire)

---

## What was built

### Files modified
- `ai_agent/detector_configs.py` — fire conf_thres 0.10 (D-19), helmet conf_thres 0.5 + target_classes ['head'] (D-04 정상). 운영급 주석 영구 적용.
- `ai_agent/scripts/upload_reference_videos.py` — `AI_HUB_FIRE_MP4` + `SELF_SHOT_HELMET_MP4` 두 별도 상수, fire/helmet 분리 mp4 매핑.
- `ai_agent/.env` — `DETECTORS_DEMO_SEEK_SEC=0.0` → `10.0` (fire frame 300 + helmet loop 도달).

### Files created
- `reference_media/fire_aihub_0087.mp4` — 사용자 제공 화재현상/불꽃/0087 (360 frames @ 30fps = 12s, 29MB, crf=18)
- `reference_media/helmet_h0_L2_09-08_001.mp4` — 자체 helmet H0 best sequence (298 frames = 9.9s, 6.3MB)
- `reference_media/helmet_h0_demo.mp4` — 위 helmet × stream_loop 2 = 30s (19MB), seek=10s 도달용
- `reference_media/_phase1_inference/REPORT.md` — 추론 측정 리포트 + 어노테이션 24장 + JSON

### Storage objects (Supabase)
- `reference-videos/fire/source_v2.mp4` (덮어쓰기, 29MB)
- `reference-videos/helmet/source_v2.mp4` (덮어쓰기, 19MB)
- 기존 `source.mp4` 보존 (롤백 가능, D-03)

### DB updates (Supabase)
- `cameras.live_url_detail` (camera_id 1, 5) → 신규 `source_v2.mp4` URL
- `detection_events` ev=22/23 (fire cam=1, conf=0.134), ev=24/25 (helmet cam=5, label='head', conf=0.687) — Task 3 검증 결과

---

## Acceptance criteria results

| Phase 1 ROADMAP SC | Result | Evidence |
|---|---|---|
| SC #1: 신규 detection_events 사이클에서 fire/helmet 모두 검출 | ✓ PASS | event_id 22-25 적재됨 |
| SC #2: `scripts/upload_reference_videos.py --only fire,helmet` 무사 통과 | ✓ PASS | "성공 2 / 실패 0" |
| SC #3: detector_configs.py 의 fire/helmet conf_thres + helmet target_classes 운영급 (D-04 절반 적용 — fire D-19 fallback) | ◐ PARTIAL | helmet 0.5+['head'] 정상, fire 0.10 fallback (가중치 한계) |
| SC #4: forklift/person 무손상 보존 | ✓ PASS | event_id 20 (forklift conf=0.73), 21 (person conf=0.89) — Wave 1 사이클 그대로 |

---

## Implementation decisions invoked

13 → 22 D-decisions invoked during execution:

- **D-01·D-02 rev2**: 단일 mp4 → fire/helmet 분리 mp4 (사용자 결정 A1)
- **D-03**: source_v2.mp4 새 키, 기존 source.mp4 보존 (적용)
- **D-04 partial**: helmet 만 0.5 + ['head'], fire 는 D-19 fallback
- **D-05**: helmet target_classes ['head'] 정상 적용
- **D-06**: --once-detect + detection_events SQL 검증
- **D-18 (학습)**: 가중치 empirical 한계 — fire/helmet 모두 측정
- **D-19 (fallback)**: fire conf 0.10 + Phase 2 frames_required 결합 (발동)
- **D-20**: fire batch scan 결과 기록 — 3 clip 모두 0.5 미달 확정
- **D-21**: DETECTORS_DEMO_SEEK_SEC = 10.0 (공통 seek)
- **D-22**: D-04 의 conf 0.5 임계는 helmet 만 적용 (fire 는 fallback)

---

## Notable deviations / discoveries

1. **사용자 데이터 측정의 중요성**: planning 단계에서 D-01 의 "단일 mp4" 가정이 시각 검사에 의존했고, YOLO 가중치의 empirical 검증이 미수행. Task 3 진입 후에야 발견 → planning gap. 후속 phase 의 데이터 적합성 검증은 plan 작성 시점에 실측 추론 의무화 권장.

2. **ffmpeg → mp4 → cv2 라운드트립 conf 손실 미미**: 초기 진단 ("인코딩 손실이 conf 깎는다") 은 틀렸음. 실측 비교 (`reference_media/_phase1_inference/REPORT.md` §1.3): fire 직접 JPG 0.136 vs mp4 0.142 (≈ 동일). 진짜 원인은 영상 자체 신호 약함 + 가중치-데이터셋 분포 불일치.

3. **fire 가중치 데이터 분포 불일치**: 사용자 제공 3 clip 모두 max 0.156 미달. 옵션 A (다른 clip 시도) 도 같은 데이터셋 내에선 해결 불가. v1.1 의 AI-Hub fine-tune 으로 해결 예정.

4. **helmet H0 시퀀스 다양성**: 24 H0 시퀀스 중 5개만 head 라벨 검출 가능. 같은 "안전모 미착용" 표시지만 각도/조명/거리에 따라 head 가시성 차이 큼. 데이터셋 큐레이션 필요성 확인.

5. **scheduler 의 seek_sec 글로벌**: per-camera 다른 seek 불가 → 두 영상이 공통 seek 시점에 검출 가능해야 함. helmet 영상 stream_loop 으로 30s 만들어 fire 의 10s seek 와 호환.

---

## Phase 1 의 Phase 2/3 영향

- **Phase 2 (MODEL — frames 연속 룰)**: D-19 의 핵심 → fire conf 0.10 + 5 연속 frame 결합이 진짜 운영급 의미. Phase 1 단독으로는 fire 약함을 frames_required 가 보강.
- **Phase 3 (FUSION)**: helmet target_classes ['head'] 정상 동작 → FUSION-02 (head + helmet 공간 매칭) 데이터 입력 OK.
- **Phase 5 (EVAL)**: fire/helmet detection_events 정확도 측정의 baseline 확보 (acc 0.13 / 0.69).

---

## Files reference

- Plan: `.planning/phases/01-vision-demo-videos/01-01-PLAN.md`
- Context: `.planning/phases/01-vision-demo-videos/01-CONTEXT.md` (D-01..D-22)
- Discussion log: `.planning/phases/01-vision-demo-videos/01-DISCUSSION-LOG.md`
- Inference report: `reference_media/_phase1_inference/REPORT.md` + 24 annotated images + JSON
- Commits: `34cb4ec` (Task 1), `022383c` (Task 2 v1), `99d6f5e` (BLOCKED state),
  `48a7659` (A1 hybrid revision), TBD (this final commit — A1 batch scan + D-19 fallback + Phase 1 close)
