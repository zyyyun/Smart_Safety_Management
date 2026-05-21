# Phase 1: 비전 — 데모 영상 교체 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 1-vision-demo-videos
**Areas discussed:** Source, Split, Verify thr, Storage, Verify

---

## Source — 신규 데모 영상의 출처

| Option | Description | Selected |
|--------|-------------|----------|
| 레거시 detection(fire, helmet).mp4 | D:\2025_산업안전\산업안전\발표자료용 영상\detection(fire, helmet).mp4 — 39MB, 4분 19초, 960×504, 30fps, h264. fire+helmet 둘 다 포함. 즉시 사용 가능, 이미 접근·판단 끝난 자료 | ✓ |
| AI-Hub 화재/안전모 데이터셋 다운로드 | AI-Hub 3종 중 화재/안전모 관련 영상 다운로드 → 포맷 확인 → 편집. 시간 1—2일, 단 온고가 일자에 적절하지 않을 수 있음 (운영 임계 검증 안됨) | |
| 자체 촬영 30s mp4 | 작업자가 안전모 미착용 + 불꽃 등 초담 잡다면 프로덕션 데이터 가치 높지만 시간 ↑↑ | |

**User's choice:** 레거시 detection(fire, helmet).mp4
**Notes:** 시간 효율 + 즉시 가용성 + 이미 PPT 발표 자료로 사용된 검증된 영상이라는 점이 결정적. AI-Hub / 자체 촬영은 v1.1+ 데이터셋 보강 단계로 이연.

---

## Split — fire+helmet 통합 영상의 두 SOURCE 매핑 전략

| Option | Description | Selected |
|--------|-------------|----------|
| 한 영상을 두 SOURCE 에 동일 매핑 | fire camera_id=1 과 helmet camera_id=5 둘 다 같은 mp4 URL 가리키게. 검출 세션마다 fire/helmet 각각의 detector 가 해당 클래스만 필터 — 단순, ffmpeg 추가 인코딩 불필요 | ✓ |
| ffmpeg 으로 fire·helmet 구간 분할 재인코딩 | 수동 시각 확인 후 fire 구간/helmet 구간 도자르고 2 mp4 → 각각 SOURCE 에 매핑. 검증 명확하지만 사람 손이 들어감 | |
| fire 는 기존 input.mp4 그대로 두고 helmet 만 교체 | fire 의 conf 임계 0.10—0.15 문제는 운영 적합 아닌데도 헬멧만 교체 — DATA-02 식정 미충족 | |

**User's choice:** 한 영상을 두 SOURCE 에 동일 매핑
**Notes:** detector 가 알아서 target_classes 로 필터링하므로 분할 인코딩이 불필요. ffmpeg 작업 시간 절감.

---

## Verify thr — MODEL-03 (운영 임계 복원) 검증 방식

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 1 에서 detector_configs.py 영구 변경 | fire/helmet conf 0.5+ 와 helmet target_classes ['head'] 를 실제 수정·커밋·Phase 2 의 REQ MODEL-03 은 자연 흡수. ROADMAP 의 phase 분리는 조명 아닌 논리 단위 | ✓ |
| 검증용 임시 변경 후 원복 (git stash) | stash 로 임시 적용 → `--once-detect` 실행 → 로그 캐처 → 원복. Phase 2 에서 MODEL-03 을 공식적으로 커밋. 파일 수정 0건 | |
| Inference 인자 override 로 검증 (config 불변) | `--once-detect --conf-override fire=0.5 helmet=0.5` 같은 임시 플래그 추가 (수서이 수정 필요) — 시간 추가 ↑ | |

**User's choice:** Phase 1 에서 detector_configs.py 영구 변경
**Notes:** Phase 1 의 SC #2/#3 가 운영 임계에서 검출 가능을 요구하는 이상, 검증과 영구 적용을 분리할 이유가 없음. Phase 2 는 MODEL-01·02 (frames_required 룰) 에 집중하면 됨.

---

## Storage — 신규 mp4 의 Storage 객체 키

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 fire/source.mp4 · helmet/source.mp4 overwrite | 같은 경로로 업서트 (x-upsert: true 이미 동작). cameras.live_url_detail URL 패턴 변경 없음. 단순 | |
| 새 이름 (fire/source_v2.mp4 · helmet/source_v2.mp4) | 구 파일 유지 + 새 파일 올림 + cameras URL 갱신. 롤백 가능하지만 Storage 용량 ↑ | ✓ |
| 상대 경로 대신 상심택 병드 자동 경로 | 소스 1개 → 두 경로에 복사 — fire/shared.mp4 + helmet/shared.mp4 동일 내용. Storage 중복 | |

**User's choice:** 새 이름 (fire/source_v2.mp4 · helmet/source_v2.mp4)
**Notes:** 데모 직전 운영 영상 교체이므로 롤백 가능성 (cameras URL 만 원복) 보장이 더 가치 큼. Storage 50MB × 2 ≈ 100MB 추가는 무시 가능.

---

## Verify — Phase 1 의 검증 깊이

| Option | Description | Selected |
|--------|-------------|----------|
| --once-detect 1회 + detection_events SQL 조회 | ai_agent --once-detect 1회 실행 → detection_events 테이블에서 camera_id 1·5 · conf > 0.5 인 행 존재 확인 + 로그 첸크 | ✓ |
| --once-detect 3—5회 반복 + 프레임별 conf 분포 로그 | 일시 변동 면조— detection_events 적재 수·평균·최소·최대 conf 기록. Phase 5 EVAL 는 별도 100장 검증셋으로 수행 | |
| 자동 검증 스크립트 추가 (precision/recall 정량 아니어도 발생 수 계산) | scripts/verify_demo_videos.py 신규 — 이부 은 Phase 5 의 EVAL-02 와 겹칠 가능 → Phase 1 범위 넓 | |

**User's choice:** --once-detect 1회 + detection_events SQL 조회
**Notes:** SC #2/#3 의 "≥ 1프레임 검출" 조건은 단일 cycle 로 충분. 정량 평가는 Phase 5 EVAL-02 에서 별도 검증셋으로 수행 — 본 phase 가 EVAL 영역까지 확장되면 scope creep.

---

## Claude's Discretion

- conf_thres 0.5 vs 0.55 vs 0.6 미세 조정 — D-04 적용 후 detection_events.accuracy 실측치 분포 보고 결정
- ffmpeg 재인코딩 필요성 — `ffprobe` 결과상 이미 호환되지만 일부 player 호환성 우려 시 가벼운 재인코딩
- `SOURCES` dict 의 fire/helmet 항목 모두 같은 src 경로를 가리키므로 코드상 명료하게 표현 (공통 상수 추출 등) 여부

## Deferred Ideas

- AI-Hub 데이터셋 다운로드 + fine-tune → v1.1 (현장 데이터 수집) 또는 v2.0 (YOLO26)
- 자체 촬영 30s mp4 → v1.1 검단·포천 현장 설치 시 현장 영상 직접 수집
- 레거시 `LEGACY_BASE` (`D:\2025_산업안전\...`) 워크스페이스 내재화 → v1.1 cleanup phase
- fire/helmet 구간 분할 인코딩 → 시각적 구분 명확성 우려 시 v1.1
- detection_events 보존/정리 정책 → Phase 6 데모 빌드 시 검토
