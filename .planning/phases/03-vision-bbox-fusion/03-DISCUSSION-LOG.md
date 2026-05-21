# Phase 3: 비전 — bbox 겹침/공간 매칭 - Discussion Log

**Session date:** 2026-05-14
**Mode:** /gsd-discuss-phase 3 (autonomous — clarifying questions skipped per
user instruction; gray areas locked by Claude using Phase 1/2 precedents + Explore
findings)

---

## Domain Boundary

`ai_agent/scheduler.py` 에 다중 detector cross-reference fusion 룰 추가:
- FUSION-01 "지게차 충돌 위험" (DANGER) — forklift+person IoU>0.3, N=3 frame 연속
- FUSION-02 "안전모 미착용" (WARNING) — person head 영역 + helmet 객체 매칭, N=3
  frame 연속. **Phase 1 D-05 의 helmet 단독 head 알람을 fusion 으로 대체**.

---

## Carrying Forward (이전 phase 결정 그대로 적용)

- **Phase 1 D-05** (`target_classes=['head']`): Phase 3 의 D-04 로 *부분 revert*. 단독
  head 알람 경로 제거 → fusion 으로 인수. ROADMAP Phase 3 SC #2 의 명시 의도.
- **Phase 2 D-01·D-02·D-07** (`_detection_buffer` + N 연속 + buffer.clear+cooldown
  reset): 동일 패턴으로 `_fusion_buffer` 추가 (D-06). 별도 dict, 같은 cooldown 공유.
- **Phase 2 D-06** (데몬 모드 컨벤션): fusion 검증도 데몬 모드 또는 mock 으로
  buffer pre-fill. `--once-detect` 단일 cycle 검증 한계.
- **Phase 1 D-19** (fire conf 0.10 fallback): fusion 처리에 영향 없음 (fire 는 fusion
  대상 아님).

---

## Gray Areas Identified

다음 6개 gray area 가 Phase 3 핵심 결정 — 모두 Claude 가 prior phase 패턴 + 코드
구조 + ROADMAP SC + Explore 결과를 근거로 locked.

### 1. Multi-bbox API
**선택지**: (a) `detect()` API 깨뜨림 + list 반환 / (b) `detect_all()` 신규 메서드 /
(c) FUSION 전용 helper 가 ultralytics model 직접 호출

**결정 (D-01)**: (b) — `detect_all()` 신규. `detect()` backward compat 유지, Phase 1/2
호출 경로 무영향. yolov5/v8 분기 코드는 이미 multi-bbox 추출 후 best 선택만 함 → 코드
중복 최소.

**근거**: `yolo_detector.py:166-184` 의 `best` 선택 로직만 분기 — 추가 추론 호출 없음.
Breaking change 회피.

### 2. 같은 frame · 다중 detector 동시 처리
**선택지**: (a) DETECTOR_CONFIGS 에 fusion 매핑 추가 / (b) 신규 `FUSION_CONFIGS` 별
파일 / (c) scheduler 내부 hardcode

**결정 (D-03)**: (b) — `ai_agent/fusion_configs.py` 별 모듈. detector_configs.py
(단일 detector 메타) 와 의미 분리. 2 entries (`forklift_collision`, `helmet_missing`).

**근거**: 단일 detector 와 fusion 은 schema 다름 (detectors_required, rule, threshold
가 fusion 만의 키). 같은 dict 에 섞으면 가독성 ↓.

### 3. 카메라 매핑
**선택지**: (a) fusion 전용 새 camera_id 신설 / (b) 기존 detector 카메라 재사용 +
person cross-camera 추론 / (c) DB 의 cameras 행 변경 (forklift+person 같은 camera_id)

**결정 (D-09)**: (b) — `forklift_collision=[4]` (forklift cam 재사용),
`helmet_missing=[5]` (helmet cam 재사용). person detector 가 cam 4·5 에서도 `detect_all`
호출 (cross-camera).

**근거**: cameras 테이블 변경은 v0.5 시드 + Phase 1 의 `live_url_detail` 매핑에 영향.
person 추가 추론 비용 ~200ms × 2 cam × 1분 cycle = 0.5% overhead 허용. v1.1 frame
caching 가능.

### 4. helmet detector 단독 알람 경로 처리
**선택지**: (a) 단독 + fusion 병행 (둘 다 발사) / (b) 단독 → fusion 대체 / (c) 단독
유지 + fusion 은 *추가* DANGER 이벤트

**결정 (D-04)**: (b) — helmet detector 의 `target_classes` `['head']` → None, 
`frames_required` 3 → 1. 단독 알람 경로 소멸. ROADMAP Phase 3 SC #2 명시.

**근거**: "단일 detector 의 head/no_helmet 클래스 출력에 의존하지 않음" 이 ROADMAP
의 정확한 표현. Phase 3 가 helmet 알람 책임 인수. Phase 1 SUMMARY 의 ev=24/25 baseline
은 Phase 3 적용 후 더 이상 생성 안 됨 — 의도된 동작.

### 5. frames_required + FUSION 결합
**선택지**: (a) `_detection_buffer` 재사용 (fusion event_key 추가) / (b) 별도 
`_fusion_buffer` / (c) FUSION 즉시 알람 (N=1)

**결정 (D-06)**: (b) — 별도 `_fusion_buffer` dict, 같은 패턴. cooldown 은 공유.

**근거**: detector event_key 와 fusion event_key 가 의미 다름 (`fire` vs
`forklift_collision`) — 충돌 회피. 같은 패턴 적용으로 코드 일관성 유지.

### 6. 검증 방식 + 데모 영상
**선택지**: (a) 검단·포천 실제 영상 신규 수집 + 통합 검증 / (b) 합성 입력 unit test
우선 + 데모 시점 1회 통합 / (c) 단위 + 통합 둘 다 mock 만

**결정 (D-10)**: (b) — pytest unit test (8+ 케이스) + 데모 시점 데몬 모드 1회 통합 (3
cycle 후 알람 발사 확인).

**근거**: v1.0 5월 PPT 일정 빠듯. 검단·포천 영상 수집은 6월 (v1.1). Phase 2 D-03 의
검증 컨벤션 (mock pytest) 연장 = 일관성. 데모 통합은 데몬 모드 (Phase 2 D-06 컨벤션).

---

## Discussion Decisions Summary

| Decision | Topic | Lock |
|----------|-------|------|
| D-01 | `detect_all()` 신규 메서드 | locked |
| D-02 | `fusion_helpers.py` 신규 모듈 (`iou_xyxy`, `hardhat_is_on`, 2 fusion 함수) | locked |
| D-03 | `FUSION_CONFIGS` dict in `fusion_configs.py` (2 entries) | locked |
| D-04 | helmet detector 단독 알람 제거 — `target_classes=None`, `frames_required=1` | locked |
| D-05 | `_process_fusion_for_camera` in `scheduler.py` | locked |
| D-06 | `_fusion_buffer` 신규 module dict, Phase 2 패턴 | locked |
| D-07 | FUSION-01 IoU>0.3, frames_required=3 | locked |
| D-08 | FUSION-02 head 영역 top 25% × ±width/6, helmet center point-in-area, frames_required=3 | locked |
| D-09 | forklift_collision=cam4, helmet_missing=cam5, person detector cross-camera | locked |
| D-10 | pytest unit test 8+ 케이스 + 데모 시점 데몬 모드 1회 통합 | locked |
| D-11 | 마이그레이션 009 — 신규 event_type "지게차 충돌 위험" 시드 | locked |
| D-12 | Phase 2 단독 detector frames_required 룰과 fusion 룰 *직교* — forklift 의 이중 알람 (단독 WARNING + fusion DANGER) 둘 다 발사 | locked |

---

## Deferred (Phase 3 범위 밖)

- 검단·포천 현장 영상 (v1.1)
- 사람별 개별 helmet missing 알람 (v1.1)
- IoU 임계 0.3 현장 보정 (v1.1 EVAL 후)
- person pose 기반 head 영역 (v1.1, pose detector 통합)
- fire ↔ person fusion (v1.1)
- person detector frame caching (v1.1)
- fusion event storage 영구 보존 (v1.x)
- 다중 fusion rule chaining (v2.0 YOLO26 후)
- 차등 frames_required (v1.1 EVAL 후)
- fusion event 시각화 (v1.1 데모 도구)

자세한 내용은 03-CONTEXT.md `<deferred>` 섹션 참조.

---

## Claude's Discretion (planner 가 미세조정 가능)

- person/forklift `target_classes` 변경 없음 (현재 그대로)
- fusion accuracy 컬럼 의미 = max conf (IoU) 또는 unmatched person conf (hardhat_missing)
- helmet v1.1 fine-tune 결합 후 point-in-area 매칭 안정성 ↑ 기대 (현재 false positive
  민감)
- log prefix `[FUSION]` / `[no_fusion]` / `[fusion_skip_cooldown]` / `[no_fusion_yet]`

---

*Phase: 03-vision-bbox-fusion*
*Discussion gathered: 2026-05-14*
