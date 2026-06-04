---
slug: rtsp-yolo-state-label
created: 2026-05-26
status: complete
milestone: v1.1
---

# Quick: 실시간 CCTV — RTSP 카메라 YOLO 동작 state 라벨

## 요청

실시간 CCTV 화면에서 **RTSP 카메라**가 **YOLO 가 동작 중인지** 동적으로 확인하는 state 라벨을 추가.

## 현재 상태 (탐색 결과)

**인프라 (이미 깔려있음)**:
- `cameras.last_frame_at` (TIMESTAMPTZ) — `ai_agent/scheduler.py` 가 capture 성공 시마다 `update_camera_health()` 로 갱신
- `cameras.health_state` ('ok'/'degraded'/'down'/'unknown') — pg_cron 1분 주기 `cameras_healthcheck()` 가 5분 무수신 → 'down'
- Edge Function `cameras/handleList` 가 `select("*")` 사용 → 위 두 컬럼이 **이미 응답에 포함됨**

**누락**:
- Kotlin `CCTVItemResponse` 모델 (SignUpService.kt:153) 에 두 필드 매핑이 없어 Gson 이 무시
- `LiveCardItem` (model/LiveCardItem.kt) 에 RTSP 여부 / YOLO state 필드 없음
- `RealTimeScreen.kt` 의 `LiveListCard` / `LiveGridCard` 에 state 라벨 UI 없음
- 동적 갱신 없음 (현재 `LaunchedEffect(Unit)` 1회만 fetch)

## 설계 결정

1. **상태 모델 (2단계, 단순화)**
   - `YOLO ON` (green dot + "YOLO" 라벨): `last_frame_at` 이 최근 90초 이내
   - `YOLO OFF` (gray dot + "YOLO" 라벨): NULL 이거나 90초 초과
   - 90초 = scheduler detection interval (보통 30~60초) × 1.5~2 마진
   - server-side `health_state='down'` (5분 임계) 보다 빠른 client-side 판정

2. **RTSP 카메라만 라벨 표시**
   - 판별: `liveUrlDetail?.startsWith("rtsp://", ignoreCase=true) == true`
   - mp4 데모 카메라는 라벨 미표시 (의미 없음 — scheduler 가 mp4 file path 와 1:1 매핑이라 항상 동작)

3. **동적 갱신**
   - `LaunchedEffect` 안에서 fetch + `delay(30_000L)` polling loop
   - 30초 = scheduler 한 cycle 길이와 비슷

4. **UI 배치**
   - `LiveListCard` / `LiveGridCard` 이미지 좌상단 (LIVE 배지는 우상단, 충돌 회피)
   - 작은 pill: green dot + "YOLO" / gray dot + "YOLO 정지"

## 변경 파일

1. **app/src/main/java/com/example/smart_safety_management/SignUpService.kt** (line 153-165)
   - `CCTVItemResponse` 에 `@SerializedName("health_state") val healthState: String?` + `@SerializedName("last_frame_at") val lastFrameAt: String?` 추가

2. **app/src/main/java/com/example/smart_safety_management/model/LiveCardItem.kt**
   - `val isRtsp: Boolean = false` 추가
   - `val lastFrameAt: String? = null` 추가 (ISO 8601 string, parse 는 Composable 안에서)

3. **app/src/main/java/com/example/smart_safety_management/screens/realtime/RealtimeActivity.kt** (line 79-110)
   - `LiveCardItem` 매핑 시 `isRtsp = dto.liveUrlDetail?.startsWith("rtsp://", ignoreCase=true) == true` + `lastFrameAt = dto.lastFrameAt`
   - polling: `while (true) { fetch(); delay(30_000L) }` (coroutine 안전 종료)

4. **app/src/main/java/com/example/smart_safety_management/screens/realtime/RealTimeScreen.kt**
   - 신규 Composable `YoloStateBadge(lastFrameAt: String?, modifier: Modifier)` 추가
   - `LiveListCard` (line 793) / `LiveGridCard` (line 860) 의 이미지 Box 안에 `if (item.isRtsp) YoloStateBadge(item.lastFrameAt, Modifier.align(Alignment.TopStart))` 추가
   - `currentTimeMillis()` 를 1초 단위로 trigger 하는 `produceState` 로 dynamic 갱신

## 검증

- Build: `gradlew assembleDebug` (warning OK)
- Manual: app 실행 → 실시간상황 화면 → RTSP 카메라 (camera_id=3, Drift X3, rtsp://192.168.0.13/live) 카드에 "YOLO" 라벨 보이는지 확인
- ai_agent 동작 중이면 green, 끄면 90초 후 gray 로 전이

## 비-범위 (out of scope)

- mp4 카메라 라벨 — 의미 없으므로 안 함
- `health_state` 의 'degraded' 별도 표시 — 'ok'/'down' 2단계로 충분
- 서버측 변경 — Edge Function 은 이미 모든 컬럼 응답
- 권한·RLS 변경 — `health_state` / `last_frame_at` 는 동일 group 사용자가 읽는 cameras 행에 자동 포함

## Atomic commit 단위

1. `feat(realtime): expose health_state + last_frame_at in CCTV list response model`
2. `feat(realtime): add YoloStateBadge for RTSP cameras with 30s polling`
