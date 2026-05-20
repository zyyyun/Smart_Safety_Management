---
title: "Smart Safety Management — Backlog"
created: 2026-05-20
purpose: "v1.0 마일스톤 외 / v1.1+ 진행 예정 항목의 phase scaffold 저장소"
---

# Backlog — v1.1+ Phase Scaffolds

> **사용법**: 새 phase 로 전환하려면 `/gsd-add-phase <title>` 명령으로 ROADMAP.md 에
> 정식 phase 등록 후 본 scaffold 의 SUCCESS CRITERIA 를 REQUIREMENTS.md 로 이동.

---

## FIRE-ADV — 화재 검출 기능 고도화

**Created**: 2026-05-20
**Trigger**: Phase 1 D-19 fallback (fire conf 0.10 v0.5 baseline) 의 임시조치 해제 필요.
**Estimated**: 1~2 phases (모델 + 데이터 + 운영 알람), 1~2주
**Depends on**: Phase 1·2·3 (비전 트랙) 완료. YOLO26 마이그레이션 (`iridescent-percolating-fox.md`) 와 병행 가능.
**Priority**: HIGH (5월 PPT 시연 후 즉시, 6월 검단·포천 설치 전 필요)

### 배경

Phase 1 02-CONTEXT.md D-19 fallback:
> 사용자 제공 화재 dataset (`fire_aihub_0087.mp4`, AI Hub) 의 fire 검출 conf
> 가 0.142 max → 운영 임계 0.5 미달. **임시조치**: `detector_configs.py` 의
> `fire conf_thres = 0.10` 으로 하향 + Phase 2 `frames_required = 5` 연속 룰로
> false positive 흡수 → "운영급 의미 확보".

이는 v1.0 5월 PPT 시연 임박을 위한 임시조치였음. **진정한 화재 검출 운영 품질**
은 다음 5개 sub-task 통합 후 도달.

### Sub-tasks (FIRE-ADV-01 ~ 05)

#### FIRE-ADV-01 — 화재 모델 교체 / 재학습
- **목표**: 운영 임계 `conf 0.5+` 정상 도달. D-19 fallback (conf 0.10) 해제.
- **후보 path**:
  1. **AI Hub 화재 dataset 자체 학습** — `D:\2025_산업안전\산업안전\` 의 사용자
     fire dataset 사용. YOLOv5/v8 transfer learning. 학습 환경 RTX 3060 Ti 8GB.
  2. **공개 fire detection 모델 평가** — Hugging Face fire-smoke YOLO 모델
     (예: `keremberke/yolov8m-fire-detection`, `yolov8-fire-and-smoke`) 검증.
  3. **YOLO26 fire 통합** — `iridescent-percolating-fox.md` 의 YOLO26
     전면 마이그레이션 (Ultralytics 2026-01-14) 와 결합. end-to-end NMS 제거 +
     CPU 43% 향상 + pose 통합 + fire detection 통합.
- **Acceptance**:
  - `fire_aihub_0087.mp4` (Phase 1 reference) 에서 `conf >= 0.5` 단일 frame 검출
  - `detector_configs.py:fire.conf_thres` = 0.5 복원 (D-19 fallback 해제)
  - `frames_required` 재평가 (현재 5 → 3 또는 1 가능?)
  - mp4 데모 4개 + 실 카메라 모두 회귀 0

#### FIRE-ADV-02 — Smoke 분리 라벨
- **목표**: 불꽃 (flame) vs 연기 (smoke) 별도 클래스 → 알람 severity 차등.
- **단계**:
  1. dataset 라벨 재정의: `flame` / `smoke` / `flame+smoke`
  2. 모델 학습 또는 2-class fire detector 도입
  3. `event_types` 테이블 / `risk_level` 매핑:
     - smoke only → **CAUTION** (발화 초기 가능)
     - flame only → **WARNING** (소형 화재)
     - flame + smoke → **DANGER** (본격 화재 + FCM 즉시 + 119)
- **Acceptance**:
  - 3 시나리오 (smoke / flame / both) 의 detection_events 별 risk_level 정확
  - 같은 frame 에 smoke + flame 동시 검출 시 fusion → DANGER 자동 분류

#### FIRE-ADV-03 — 다중 frame fusion (시간축)
- **목표**: 단일 frame conf 의존도 낮춤. 시간축 N frame 평균 + bbox IoU 안정성 검사.
- **알고리즘**:
  - `frames_required = 5` 가 단순 "5 연속 True" — 각 frame 의 bbox 좌표는 무시
  - 신규: 5 frame 의 bbox IoU > 0.5 + conf 평균 > 0.4 → 알람 (안정적 객체만)
  - 같은 위치 진정한 화재 vs 단순 빨간 옷/조명 random flicker 구분
- **Phase 3 fusion_helpers.py 패턴 재사용 가능**

#### FIRE-ADV-04 — 환경 sensor cross-check (LP-6 연동)
- **목표**: 비전 fire 검출 + 환경 sensor (온도/CO/연기) 양방향 검증.
- **선행**: LP-6 화재감지기 BLE + 아크차단기 모니터링 활성화
- **로직**:
  - 비전 fire WARNING + sensor 정상 → CAUTION 강등 (false positive 가능성)
  - 비전 fire 미검출 + sensor 화재경보 → 알람 (사각지대 화재)
  - 비전 fire + sensor 화재경보 → DANGER (확정)

#### FIRE-ADV-05 — 운영 알람 강화
- **목표**: 화재 검출 → 즉시 자동 대응 chain
- **chain**:
  1. detection_events DANGER risk_level
  2. FCM 즉시 (cooldown 5분 → 화재는 1분으로 짧음)
  3. 작업자 대피 음성 (PTT — Next-5 트랙 연계)
  4. 119 신고 prompt (manager 화면 + 자동 다이얼 옵션)
  5. 인접 카메라 자동 RTSP 모드 전환 (현장 다중 시야 확보)
  6. SMS/카카오톡 알림톡 (Next-7 트랙 연계)
- **Acceptance**: 화재 시뮬 → 60초 내 FCM + 음성 + SMS 모두 도착

### 새 phase 전환 시 추천 분할

**옵션 A — 1 phase 통합** (5월 PPT 후 즉시, 1~2주):
- Phase 10: 화재 검출 운영급 도달 (FIRE-ADV-01·02·03 통합)
- FIRE-ADV-04·05 는 v1.x 별도

**옵션 B — 2 phase 분할** (안정성 우선):
- Phase 10: FIRE-ADV-01 (모델 교체) — 1주
- Phase 11: FIRE-ADV-02·03 (smoke + fusion) — 1주
- FIRE-ADV-04·05 v1.x

**옵션 C — YOLO26 마이그레이션 흡수** (6월~):
- 단독 phase 없이 YOLO26 통합 시점에 fire 가중치도 교체
- 5종 detector 단일화 + fire 임계 정상화

### 참조

- `ai_agent/detector_configs.py:24-46` — fire detector 설정 + D-19 fallback 주석
- `.planning/phases/01-vision-demo-videos/01-CONTEXT.md` — D-19 fallback 결정 history
- `.planning/phases/02-vision-frames-required/02-CONTEXT.md` — frames_required=5 결정
- `D:\2025_산업안전\산업안전\` — 사용자 fire dataset 위치
- `C:\Users\ANNA\.claude\plans\iridescent-percolating-fox.md` — YOLO26 마이그레이션 비전

### 의사결정 대기

- [ ] 옵션 A/B/C 선택
- [ ] dataset 학습 환경 결정 (로컬 RTX 3060 Ti vs 클라우드)
- [ ] YOLO26 마이그레이션 일정 확정 (6월~ 명시되어 있으나 정확한 시점 미정)
- [ ] fire smoke 라벨링 작업 vs 공개 모델 평가 — 시간 비용 trade-off

---

## (향후 항목 추가 위치)

새 backlog 항목은 본 BACKLOG.md 에 `## XXX-YYY — 제목` 섹션으로 추가.
정식 phase 로 전환되면 ROADMAP.md 에 등록 + 본 파일에서 "→ Phase N 으로 이동 (날짜)"
표기 후 보존 (history 추적용).
