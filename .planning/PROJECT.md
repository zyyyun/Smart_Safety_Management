# Smart Safety Management — Project Context

> **Project Code**: SSM
> **Bootstrapped from existing context** on 2026-04-29 (sources: `docs/PROJECT_SPEC.md`, `docs/J2208A_안전관리_시스템_PLAN.md`, memory files, prior plan `iridescent-percolating-fox.md`, 25+ commits).
> **Last updated**: 2026-06-04 (**v1.1 milestone shipped as scope-reduced close** — Phase 11 complete, Phase 12/13 partial by code evidence, Docker/UAT deferred. See `.planning/milestones/v1.1-MILESTONE-AUDIT.md`.)

---

## What This Is

산업 현장의 위험 상황을 실시간으로 감지하고 관리자·작업자에게 즉시 통보하여
사고를 예방·대응하는 통합 안전관리 플랫폼. 검단·포천 도금/금속가공 SME 2개소
파일럿 대상 (5월 PPT 데모 → 6월 설치 → 9월 1차 정량 평가).

## Core Value

- **실시간**: 감지 → 알림 ≤ 10초 (FCM HTTP v1)
- **자동화**: CCTV 1분 슬로텍 추론 + 10분 주기 스냅샷 보관
- **이력화**: 모든 이벤트 → DB 영구 기록 + Storage 캡처 첨부
- **현장 작업 지원**: 작업자에게 직접 조치 요청 + 일일점검 일지

## Domain Threats (3 카테고리, `docs/PROJECT_SPEC.md §1.2`)

| 카테고리 | 감지 대상 | 본 프로젝트의 대응 |
|---|---|---|
| **화재** | 화재 발생, 화재감지기 알람, 아크차단기 이상 | YOLO 화재 검출 + (예정) 화재감지기 BLE + 아크차단기 모니터링 |
| **인명 위험** | 작업자 쓰러짐, 안전모 미착용, 지게차 충돌 위험, 혼잡도 | YOLO + YOLOv7-pose + (Phase 4) J2208A BLE 워치 fusion |
| **화학물질** | (후속) 가스·먼지·온도 등 환경센서 | (LP-5/LP-6 단계 예정, v2.x) |

## Stack (Validated)

- **Android Kotlin/Compose** — manager · worker · general_manager 역할별 UI, Retrofit 인터셉터
- **Supabase** — Postgres (22 tables, RLS) + Auth + Storage (5 buckets) + 13 Edge Functions (Deno) + pg_cron
- **Python `ai_agent`** — FFmpeg + ultralytics + torch.hub yolov5 + yolov7_fork; 1분 슬로텍 detection
- **Firebase FCM** (`smart-safety-2026`) — 메시징 채널 전용. RS256 JWT self-sign
- **카카오맵** — Native App Key, REST API Key, 작업장 위치 표시
- **BLE PoC: J-Style 2208A** — 펌웨어 0.6.3.9, MAC `21:02:02:06:01:69`,
  BLE 4.0 / Service `0xFFF0`, Write `0xFFF6`, Notify `0xFFF7`. 16-byte 고정 패킷
  (CRC = sum & 0xFF). `scripts/j2208a_sensor_reader.py` 가 파이프라인 S1 (Decode)
  까지 동작 확인.

## Validated Capabilities

- **5종 AI 비전 detector E2E** (`09f2764`) — 쓰러짐(YOLOv7-pose) · 화재 · 안전모 ·
  지게차 · 사람. `--once-detect` / `--once-fall` 모드로 5종 모두 detection_events
  생성 + FCM 푸시까지 동작.
- **FCM 푸시 실전송 E2E** (`8e601eb`) — `_shared/fcm.ts` 헬퍼, RS256 JWT self-sign,
  5종 훅 지점 (`detection`, `location`, `devices`, `notifications`). 신 Firebase
  프로젝트 `smart-safety-2026` 이전 검증.
- **이미지 업로드 E2E** (`7458a9e`) — 4 버킷 (profile-images / action-images /
  check-images / camera-captures) 13 항목 체크리스트 통과.
- **CCTV 스냅샷 자동화** (`ad1e82d` 외 6 커밋) — 외부 Python agent → Storage
  업로드 + Edge Function insert.
- **Supabase 풀 마이그레이션** — 13 Edge Functions, 22 tables, 5 buckets, RLS 기본.
- **카카오맵 렌더** — Native App Key, 플랫폼 등록, 회원가입/로그인/지도 동작 확인.
- **test 브랜치 로그인 우회** — `SplashActivity` 직접 홈 진입 (`f5283ec`).
- **J2208A BLE 프로토콜 분석 + 1인 raw 수신 PoC** — S1 Decode 통과
  (`scripts/j2208a_sensor_reader.py`), raw notification 5–6 Hz 안정 수신.
- **v1.1 앱 안정화 묶음** — UI 공통 토큰/Setting scaffold, TBM 다중 OPS·작업자 제출 흐름·즉시 갱신, 일일점검 선택 날짜 저장, AI 이벤트 실제 capture URL 반환, Drift X3 visible PowerShell YOLO watch, J2208A B-mode runtime state 안정화가 코드로 반영됨. Formal GSD verification은 v1.1 scope-reduced close에서 deferred.

## Active Requirements

No active requirements file is open. Start the next milestone with `$gsd-new-milestone`.

## Validated Requirements (이전 마일스톤 v0.1~v0.5)

`.planning/MILESTONES.md` 참조 — 본 GSD 부트스트랩 시점에 이미 출하된 작업.

## Out of Scope (전반)

- **인프라 트랙 — sys.path 분리** (fall + general detector 동시 활성) → v1.1 또는
  YOLO26 마이그레이션이 흡수
- **LP-3 RTSP 실카메라** (Drift X3 실기기 부재) → v1.1 / 6월 설치 직전
- **YOLO26 전면 마이그레이션** (5종 비전 단일화) → v2.0 / 6월~
- **워치 트랙 Phase 2~4** (J2208A 플랜) — 다중 작업자, BLE 게이트웨이, 옥외 LTE
  중계 → v1.1 (사무실 3~5명) / v1.2 (게이트웨이) / v2.x (옥외)
- **환경센서·평면도 자동화·화재 3단계 매뉴얼** → LP-6 이후 / v2.x
- **Next-5/6/7/8/9** (PTT 음성·JWT 활성화·SMS Solapi·릴리즈 빌드·main PR) → v0.9
  또는 v1.1 별도 마일스톤
- **Phase 5 (평가 — 2단계 정량 지표)** → **v1.1 이월** (2026-05-22 결정). v1.0 종결
  시 not started. 비전+워치 정량 지표 정의 + 자동 측정 스크립트는 6월 검단·포천
  설치 + 9월 1차 정량 평가 사이클에 정착.
- **Phase 6 (데모 빌드 — 통합 시연·캡처·PPT)** → **v1.1 이월** (2026-05-22 결정).
  5월 PPT 데모 마감 (2026-05-15 계획) 이미 경과 + 백엔드/인프라 검증과 분리하여
  별 milestone 으로 정착이 효율적. v1.1 에서 6월 현장 설치 결과·9월 평가와 함께
  통합 데모 자료 재구성.
- **Phase 4 04-04 (24h 워치 실측)** → v1.1 (사용자 24h 워치 착용 의사결정 대기).
- **Phase 7-04 (단축 PoC + E2E 시연)** → v1.1 또는 6월 현장 설치 직전 (autonomous:
  false, 시연 환경 가용 시점).
- **Phase 9-04 (1일 사이클 manual 시연)** → v1.1 또는 6월 현장 설치 직전.

## Key Decisions

- **2026-04-28**: YOLO26 마이그레이션 시점 = 5월 PPT 후 (6월~). 검단·포천 설치 직전
  LP-3 와 함께. 본 v1.0 은 현 detector 그대로 시연.
- **2026-04-28**: 우선 트랙 = 모델 (룰 충실도) + 데이터 (영상 교체). 후순위 = 인프라
  (sys.path 분리), 통합 (Android UI 고도화).
- **임시조치** — fire/helmet conf 0.10, helmet target_classes None — 영상 교체 후
  0.5+ 및 ['head'] 복원 예정 (v1.0 REQ MODEL-03).
- **2026-04-29**: J2208A BLE 워치 트랙 v1.0 포함 결정 — `j2208a_sensor_reader.py`
  PoC 동작 → Phase 1 (1~2주 산출물 = S2~S4 + Supabase + 기본 대시보드) 이 5월 PPT
  데모에서 비전 5종과 함께 시연 가능. wear-state 임계값 (T_off/T_warm/N₁/N₂) 은
  잠정값으로 진행, J2208A 플랜 §8 추가 실험은 v1.1.
- **신호 해석 원칙** (J2208A 플랜 §3 핵심 도입): HR/temp raw 를 측정치가 아니라
  *상태 신호* 로 처리. wear-state state machine
  (OFF/WARMUP/TRANSIENT/WORN/ABNORMAL) 적용 후 위험 알림은 `WORN` 60초 이상 지속
  시에만 활성화 (오탐 방지).
- **알림 전이 원칙** (J2208A 플랜 §6): 같은 등급 지속 중 반복 알림 X — 정상↔주의↔
  경보 상태 *전이* 시점에만 발송. 본 원칙은 워치/비전 모두에 일관 적용.
- **단일 source of truth**: `.planning/PROJECT.md` 가 신규 SoT. `docs/PROJECT_SPEC.md`
  는 v0 보존본 (read-only). 이후 사실관계 갱신은 PROJECT.md 가 우선.
- **2026-05-22**: Phase 10 신설 후 방향 전환 — 초기에는 `feature_rtps_test` 브랜치의
  모바일 frame sampler PoC (R1 ExoPlayer+ImageReader 실패 → R3 LibVLC takeSnapshot compile
  실패 → R3a LibVLC+TextureView 동작 확인) 를 흡수했으나, 새 전제는 **PC와 카메라가
  같은 LAN**. 따라서 active path 는 모바일 relay 가 아니라 **PC service 가 등록된 RTSP
  URL 수신 가능 상태를 자동 probe 하고 기존 YOLO detection cycle 을 즉시 구동**하는 운영
  자동화로 전환. 모바일 frame sampler 는 historical evidence 로 보존.
- **2026-05-22**: Phase 10 완료 — `SmartSafetyAiAgent` service path 에 RTSP auto-detect
  interval job 을 추가했다. 등록된 RTSP URL 이 `unknown/unreachable → reachable` 로 전이되면
  기존 YOLO detection/fall cycle 을 자동 구동하며, 중복 실행 방지 lock 과 unit tests 를 포함한다.
- **2026-05-23**: Phase 15 신설 — Phase 10 의 NSSM 서비스 (`SmartSafetyAiAgent`) **deprecation**.
  debug session `fire-only-grey-light` 진행 중 NSSM 의 운영·검증 어려움이 architectural 문제로
  확인됨 (Suspect 5 — RTSP 동시 접속 경합 + admin PowerShell 요구 + service status 불투명).
  Docker 컨테이너화로 architectural fix. ai_agent only scope (j2208a·Supabase 는 그대로).
  코드 (`scheduler.py` 의 RTSP autodetect job 등) 보존, deployment 방식만 NSSM → Docker.
- **2026-06-04**: v1.1 scope-reduced close 결정 — 사용자가 남은 갭을 인정하고 현재 상태로 마일스톤 종료를 선택. Docker 요구사항은 실제 운영 방향인 visible PowerShell scheduled task와 충돌하므로 다음 마일스톤에서 유지/교체를 정식 결정해야 함.

## Current State: after v1.1 scope-reduced close

v1.1 is closed. The product is more stable than at v1.0, but the milestone is intentionally archived with known gaps rather than treated as fully verified.

**What changed in practice:**
- App visual consistency improved through shared UI tokens/components.
- TBM UX and flows were redesigned and repeatedly stabilized after device feedback.
- Daily checklist date handling and AI event capture source mapping were fixed.
- RTSP YOLO startup moved toward a visible PowerShell/task-scheduler operation model.
- Watch integration moved toward JcWear-style B-mode runtime reading with reactive UI state.

**Known gaps carried forward:**
- Formal v1.1 phase verification artifacts are missing.
- KOSHA TBM PDF/export is not verified.
- Docker scope is unresolved and likely needs replacement with the visible task-scheduler approach.
- 검단/포천 pre-install UAT and 3-role walkthrough are not complete.

**Next milestone candidates:**
- Field-readiness UAT: TBM, watch, RTSP, daily checklist, AI event capture.
- Watch runtime hardening and multi-watch identification.
- ai_agent operation model decision: Docker vs visible PowerShell scheduled task.
- KOSHA TBM PDF/export.

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state
