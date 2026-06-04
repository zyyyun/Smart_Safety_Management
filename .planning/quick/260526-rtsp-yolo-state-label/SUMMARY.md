---
slug: rtsp-yolo-state-label
created: 2026-05-26
completed: 2026-05-26
status: complete
milestone: v1.1
---

# Quick SUMMARY: 실시간 CCTV — RTSP 카메라 YOLO 동작 state 라벨

## 무엇

실시간상황 화면(`RealTimeScreen`)의 RTSP 카메라 카드 좌상단에 YOLO scheduler 동작 여부를 동적으로 표시하는 라벨을 추가.

- **ON**: 초록 pill + 깜빡이는 흰색 dot + "YOLO" (`cameras.last_frame_at` 이 최근 90초 이내)
- **OFF**: 회색 pill + 정적 dot + "YOLO 정지" (NULL 또는 90초 초과)

mp4 데모 카메라는 라벨 표시하지 않음 (scheduler 가 1:1 매핑이라 always-on, 의미 없음).

## 왜 90초

ai_agent scheduler 한 detection cycle 이 보통 30~60초. ×1.5~2 마진을 더해 90초.
서버측 `cameras_healthcheck()` (5분 임계) 보다 빠른 client-side 판정.

## 동적 갱신 메커니즘 2-레이어

1. **server fetch polling (30초 주기)** — `RealtimeActivity.kt` 의 `LaunchedEffect` 가
   `while (true) { getCCTVList(); delay(30_000L) }` 로 변경. `last_frame_at` 값이
   주기적으로 새로고침됨.
2. **client clock tick (5초 주기)** — `YoloStateBadge` 안의 `produceState` 가 5초마다
   `System.currentTimeMillis()` 를 갱신. polling 사이에도 90초 임계 전이가 부드럽게 반영됨.

## 변경 파일

| 파일 | 변경 |
|------|------|
| `app/src/main/java/.../SignUpService.kt:153` | `CCTVItemResponse` 에 `healthState`, `lastFrameAt` SerializedName 매핑 추가 |
| `app/src/main/java/.../model/LiveCardItem.kt` | `isRtsp: Boolean`, `lastFrameAt: String?` 필드 추가 |
| `app/src/main/java/.../realtime/RealtimeActivity.kt` | `LaunchedEffect` 1회 fetch → 30초 polling loop, RTSP 판별 + lastFrameAt 매핑 |
| `app/src/main/java/.../realtime/RealTimeScreen.kt` | `YoloStateBadge` 컴포저블 신규 + `LiveListCard` / `LiveGridCard` 좌상단 통합 |

## 검증

- **Build**: `./gradlew.bat compileDebugKotlin` → **BUILD SUCCESSFUL** (40s, 새 코드에서 warning 0)
- **Manual (deferred)**: 실기기 (Drift X3 RTSP camera_id=3, rtsp://192.168.0.13/live) 에서 ai_agent ON/OFF 토글로 라벨 전이 확인.
  - 5월 PPT 데모 또는 6월 검단·포천 설치 시 사용자 검증 예정.

## 비-범위 (확정)

- mp4 데모 카메라 — 라벨 미표시
- `health_state='degraded'` 별도 단계 — 'ok'/'down' 2단계로 단순화
- 서버측 변경 — Edge Function `cameras/handleList` 가 이미 `select("*")` 사용 → 모든 컬럼 응답
- 권한/RLS 변경 — `health_state` / `last_frame_at` 는 동일 group 사용자의 cameras 행에 자동 포함

## 인프라 참조 (탐색 단계 발견)

- Phase 8 RTSP-03 의 `012_cameras_health.sql` 가 이미 다음을 깔아둠:
  - `cameras.last_frame_at` (snapshot/fall/detection capture 시마다 `update_camera_health()` 가 갱신)
  - `cameras.health_state` (pg_cron 1분 주기 `cameras_healthcheck()` 가 5분 임계 down/recovery 전이)
- 이번 quick 작업은 이 인프라를 client UI 로 노출하는 박막 layer 만 추가.

## 후속 candidate (out of scope, backlog 안 함)

- `YoloStateBadge` 를 `InternalDetailScreen` (개별 카메라 상세) 에도 노출 — 현재는 list/grid 만.
- `health_state='degraded'` 추가 단계 (예: 90~300초 사이) — 사용자 피드백 후 결정.
