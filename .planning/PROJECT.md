# Smart Safety Management — Project Context

> **Project Code**: SSM
> **Bootstrapped from existing context** on 2026-04-29 (sources: `docs/PROJECT_SPEC.md`, `docs/J2208A_안전관리_시스템_PLAN.md`, memory files, prior plan `iridescent-percolating-fox.md`, 25+ commits).
> **Last updated**: 2026-04-29

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

## Active Requirements

(v1.0 시작 시점, 첫 마일스톤 — `.planning/REQUIREMENTS.md` 참조)

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

## Current Milestone: v1.0 5월 PPT 데모

**Goal:** 5월 중순 PPT 데모에서 (1) 5종 AI 비전 감지가 운영급 임계 (conf 0.5+) 와
룰 충실도 (frames 연속·bbox 겹침) 로 동작 + (2) J2208A 워치 1인 데이터 파이프라인
이 S2~S4 (Validate→Aggregate→Derive) 를 거쳐 wear-state 분류·위험 알림·대시보드
까지 완결 — 두 트랙이 단일 PPT 흐름으로 통합 시연.

**Target features (비전 트랙):**
- 의미 일관 데모 영상 교체 (helmet/fire — head 검출/conf 0.5+ 가능 영상으로)
- 룰 충실도 단계 1 (frames_required) 및 단계 2 (bbox 겹침/공간 매칭)
- 임시조치 복원 — fire/helmet conf 0.5+, helmet target_classes ['head']

**Target features (워치 트랙 — J2208A 플랜 Phase 1):**
- BLE notification 파이프라인 S2 Validate (quality flag GOOD/WARMUP/NOISY/INVALID)
- S3 Aggregate (5초/30초/1분 집계, GOOD 비율 < 50% 결측 표기)
- Wear-state state machine (OFF/WARMUP/TRANSIENT/WORN/ABNORMAL) + 5초 sliding
  window 다수결
- S4 Derive 최소 위험 판정 (탈착 OFF 5분 / 통신두절 / 빈맥 1차 임계) + FCM 알림
- Supabase 적재 (`raw_events` 7일 TTL · `wear_state_events` · `minute_summary` ·
  `safety_alerts` · `devices` · `workers`)

**Target features (공통):**
- 2단계 정량 지표 정의 + 검증셋 자동 평가 스크립트 (비전 + 워치 raw 수신율 포함)
- 5종 비전 + 워치 카드/추이/알림 통합 시연 흐름 + Android(또는 웹) 캡처 + PPT 슬라이드

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
