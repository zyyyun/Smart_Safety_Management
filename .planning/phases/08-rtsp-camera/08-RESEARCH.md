# Phase 8: Drift X3 RTSP 실시간 카메라 — Research

**Researched:** 2026-05-15
**Domain:** RTSP frame capture (cv2.VideoCapture/CAP_FFMPEG) + Supabase pg_net/pg_cron healthcheck + mediamtx 합성 RTSP publish + FCM action-routing
**Confidence:** HIGH (verified primary sources for all 5 priority items)
**Deadline:** 2026-05-20 (수요일, D-5)

## Summary

CONTEXT.md 의 5개 결정 (D-01~D-04) 는 모두 valid 하지만, **합성 검증을 위한 환경 가정 3건이 실제와 불일치** — 이 RESEARCH 가 정정한다. 우선순위 1~5 모두 verified primary sources 로 결론 도달. 핵심 변경 권고:

1. **mediamtx 미설치** — `/c/Users/ANNA/Desktop/mediamtx/` 부재 확인됨. 다운로드 task 추가 필요 (또는 `ffmpeg -listen 1` fallback).
2. **pg_net 미활성화** — `001_extensions.sql` 에 pg_net 없음. 012 마이그레이션이 `CREATE EXTENSION pg_net` 먼저 실행해야 함.
3. **service_role key 보관** — `current_setting('app.service_role_key')` 대신 **Supabase Vault** (`vault.decrypted_secrets`) 가 표준 패턴.
4. **camera_alerts Android channel 미존재** — MyFirebaseMessagingService.kt 는 `fcm_default_channel` + `watch_alerts` 만 인식. 신규 채널 추가 vs `fcm_default_channel` 재사용 — **deadline 우선이면 후자 권장**.
5. **manager 알림 = sendPushToUsers (plural)** — `cameras.group_id` → multiple managers. Phase 4 watch-alert 의 single-recipient 패턴을 그대로 복사하면 안 됨.

**Primary recommendation:** Day 1 에 cv2 capture_rtsp() + URL 분기 + 단위 테스트 → Day 2 에 012 마이그 (pg_net + Vault + healthcheck 함수) + Edge Function camera-down/recovered → Day 3 에 mediamtx 다운로드 + scripts/start_rtsp_mock 실행 + 합성 검증. CONTEXT.md 의 D-01·02·03·04 모두 보존, **5개 정정사항만 plan tasks 에 반영**.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 (amended)**: cv2.VideoCapture(url, cv2.CAP_FFMPEG) for RTSP / 기존 ffmpeg subprocess for mp4. URL scheme 분기 (`rtsp://` vs file). drift_test.py 패턴 채택 — opencv-python 4.12 prebuilt FFMPEG=YES 호환성 검증됨. 신규 `capture_rtsp(url, tmp_path, max_attempts=3)` in `ai_agent/snapshot.py`. snapshot.capture() 진입점 wrapper 가 URL scheme 으로 분기. 4 detector 진입점 무변경. SC #4 자동 충족.
- **D-02 (amended)**: drift_test.py 의 `try_connect(max_attempts=3)` 패턴 — 시도 사이 `time.sleep(2)` 고정 3회 (총 ~12초). VideoCapture 직후 `time.sleep(2)` (RTSP handshake) 보존. cap.isOpened() + frame is not None 가드. 3회 실패 시 SnapshotError raise → scheduler 의 기존 except SnapshotError 패턴이 catch.
- **D-03**: `012_cameras_health.sql` — cameras 에 last_frame_at·health_state·last_alert_at ALTER + pg_cron 1분 healthcheck + 5분 임계 + 30분 cooldown + ok↔down 전이 알림. notifications/index.ts 에 `case 'camera-down'` + `case 'camera-recovered'` 추가. `_shared/fcm.ts` 의 sendPushToUser/sendPushToUsers 재사용. supabase_client.py 에 `update_camera_health(camera_id)` 헬퍼 추가.
- **D-04**: mediamtx + reference-videos mp4 → 합성 RTSP publish + cameras 임시 갱신 + scheduler --once-detect 검증. RTSP-02 실기기는 deferred (v1.1 6월 검단·포천).

### Claude's Discretion

- ffmpeg RTSP 플래그 정확 값 (timeout 5s default 권장)
- 재연결 backoff 시간 조정 (D-02 의 2s 고정 3회 default)
- pg_cron schedule (1분 default)
- healthcheck 함수 구현 방식 (pg_net 직접 vs Edge Function trigger) — **본 research 가 pg_net 권장 결론**
- last_frame_at 갱신 빈도 (매 capture 성공 default; cycle 단위 dedup planner 결정)
- mediamtx config 위치 (`scripts/mediamtx.yml` 기본)
- ffmpeg publish loop args (`-re -stream_loop -1`)
- 시연 카메라 row (기존 1·5 임시 변경 vs 신규 row 추가)

### Deferred Ideas (OUT OF SCOPE)

- Drift X3 실기기 검증 (RTSP-02 실측) → v1.1 6월 검단·포천
- 다중 카메라 동시 RTSP 부하 테스트 → v1.x
- GPU 가속 / hardware-encoded H.264 / RTCP/SDP / ONVIF discovery → v1.x
- Adaptive bitrate / multi-resolution stream 선택 → v1.x
- 운영 대시보드 (cameras.health_state grid + 시계열) → Phase 6 또는 v1.x
- 카메라 다중 채널 fan-out (1 RTSP → N detector) → v1.x
- mediamtx 영구 운영 (systemd / docker-compose) → v1.x
- LP-5 룰 seed DB / risk_level 매핑 → v1.1
- 운영자 SQL UPDATE 로 health_state 임계 보정 → v1.1

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| RTSP-01 | scheduler 가 cameras.live_url_detail 의 rtsp:// URL 을 cv2/ffmpeg 으로 frame 추출 → detector → detection_events. mp4 fallback 유지. | §"Standard Stack" cv2 4.12 + §"Code Examples" capture_rtsp 패턴 — drift_test.py + CAP_FFMPEG verified working. 단위 테스트 4종으로 합성 검증. |
| RTSP-02 (deferred) | Drift X3 ≥ 1대 실기기 + 1 cycle 실측 + ≤10s 지연. | mediamtx 합성 RTSP 로 부분 충족 (지연 ≈ 0 로컬). 실기기 deferred 표기 in SUMMARY (Phase 7-04 패턴 동일). |
| RTSP-03 | 끊김 시 재연결 (최대 3회 backoff) + cameras.last_frame_at + N분 무수신 운영 알림 + 헬스체크 SQL. | §"Architecture Patterns" pg_net + Vault + cameras_healthcheck() 함수 + Edge Function camera-down/recovered. 5분 임계 + 30분 cooldown + ok↔down 전이 알림 (Phase 4 D-09). |

</phase_requirements>

## CONTEXT.md 정정사항 (CRITICAL — planner MUST address)

| # | CONTEXT 가 가정한 것 | 실제 상태 (verified) | 영향 | 해결 |
|---|----------------------|----------------------|------|------|
| C1 | `/c/Users/ANNA/Desktop/mediamtx/bin` 존재 | **부재** (verified `ls` 실패) | Day 4 합성 검증 blocking | Day 4 task 첫 단계 = mediamtx 다운로드 (`gh release download` 또는 https://github.com/bluenviron/mediamtx/releases). 폴더는 `D:\2026_산업안전\Smart_Safety_Management\bin\mediamtx\` 권장 (프로젝트 내부, .gitignore 추가) |
| C2 | pg_net extension 가용 | **001_extensions.sql 에 미포함** (postgis + pg_cron 만). Supabase managed Postgres 는 pg_net 을 pre-installed 하지만 default-enabled 는 아님. | 012 마이그 의 net.http_post() 호출 즉시 실패 | 012_cameras_health.sql 의 첫 줄: `CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;` |
| C3 | `current_setting('app.service_role_key')` 패턴으로 service_role 주입 | postgresql.conf-level GUC 필요, 세션 간 영속 X. **표준 = Supabase Vault** (`vault.decrypted_secrets`) | healthcheck 함수가 service_role 가져오지 못하면 Edge Function 401 | Vault 시드 1회 (dashboard 또는 마이그 INSERT INTO vault.secrets) + 함수 내 `(SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name='service_role_key')` |
| C4 | `ANDROID_CHANNEL_ID="camera_alerts"` 사용 | MyFirebaseMessagingService.kt 는 `fcm_default_channel` + `watch_alerts` 만 알고 있음. `camera_alerts` 채널 + 라우팅 미정의 | Android 8+ 에서 알림 표시 안 됨 (channel not found warning) | **Option A** (정공): MyFirebaseMessagingService.kt 에 `showCameraAlertNotification()` + `camera_alerts` 채널 추가 (watch_alert 패턴 미러). Edge Function payload `data.type='camera_alert'`. **Option B** (deadline 우선, 권장): `fcm_default_channel` 재사용 + 일반 `showNotification()` 흐름 → Android 코드 변경 0. v1.1 에서 분리. |
| C5 | sendPushToUser (single) 호출 | cameras.group_id → manager 권한 사용자 **N명**일 수 있음. Phase 4 watch-alert 는 1인 worker 대상 (single OK). | 첫 manager 1인만 알림 받고 나머지 누락 | Edge Function 내부에서 `SELECT user_id FROM profiles WHERE group_id=$1 AND user_role IN ('manager','general_manager')` → `sendPushToUsers(supabase, userIds, payload)` (plural). |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| RTSP frame 추출 | ai_agent (Python process, in-process cv2) | ffmpeg subprocess (mp4 fallback only) | drift_test.py 검증 패턴 — process spawn overhead 없음. snapshot.py 단일 진입점에서 URL scheme 으로 분기. |
| Backoff 재연결 | ai_agent (capture_rtsp 함수 내부) | scheduler (기존 except SnapshotError) | 1-shot capture × max_attempts=3 + 2s sleep 단순 loop. 외부 재시도 X. |
| 헬스체크 임계 평가 | Supabase Postgres (pg_cron 1분 + cameras_healthcheck() 함수) | — | 시간 의존 (5분 임계, 30분 cooldown) → DB 가 권한 있는 곳. Python agent 가 수행하면 process down 시 알림 자체가 죽음. |
| FCM 발송 | Supabase Edge Function (notifications case 'camera-down') | _shared/fcm.ts | Phase 4·7 패턴 일관. service_role 권한 필요 (profiles.fcm_token SELECT). |
| last_frame_at 갱신 | ai_agent → PostgREST (snapshot 성공 직후) | — | capture 의 진실은 ai_agent 만 알고 있음. PostgREST PATCH 1회 idempotent. |
| 합성 RTSP publish | mediamtx + ffmpeg (별도 프로세스, scripts/start_rtsp_mock.sh) | — | 검증 단계만. 운영 시는 Drift X3 실기기 (RTSP-02 v1.1). |
| 시연 모드 전환 | DB UPDATE (cameras.live_url_detail) | — | 코드 deploy 0 — mp4 → rtsp 전환은 단순 row update. SC #4 의 "분기점 1줄" 충족. |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| opencv-python | 4.12.0.88 (verified env) | cv2.VideoCapture(url, cv2.CAP_FFMPEG) | prebuilt FFMPEG=YES (verified `getBuildInformation()`). drift_test.py 검증 코드와 1:1. CAP_FFMPEG=1900 가용. |
| ffmpeg | 4.3.1 (system, verified) | mp4 fallback frame 추출 + mp4→RTSP publish | 기존 snapshot.capture() 가 사용 중. mediamtx publish 도 동일 binary. |
| supabase-py | >=2.5.0 (requirements.txt) | cameras PATCH (last_frame_at) | 기존 SupabaseBridge 패턴 reuse. update_camera_health() 헬퍼 추가. |
| pg_cron | (001_extensions.sql 활성화됨) | 1분 주기 healthcheck 트리거 | Phase 4 010 에서 cleanup_raw_events_hourly 검증됨. |
| **pg_net** | **(미설치 — 012 마이그가 추가)** | net.http_post() 으로 Edge Function 호출 | Supabase 표준 pattern. async fire-and-forget. |
| mediamtx | latest (v1.x) — **다운로드 필요** | 합성 RTSP server | bluenviron/mediamtx, runOnDemand 패턴. |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| pytest | (기존) | 단위 테스트 | capture_rtsp 4 시나리오 + URL 분기 |
| supabase Vault | (managed) | service_role key 보관 | healthcheck 함수가 Edge Function 호출 시 |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| cv2.VideoCapture | ffmpeg subprocess (기존 snapshot.capture 의 RTSP 분기) | subprocess 는 stderr parsing + timeout + spawn overhead. cv2 in-process 가 단순 + drift_test.py 검증 우위. |
| pg_net | Edge Function self-poll (cron-style) | self-poll 은 cold-start × 60회/시간 + Edge Function 의 pg row 직접 SELECT. pg_net 이 표준 + 비용 ↓. |
| Supabase Vault | postgresql.conf GUC + setup script | GUC 는 reboot 시 reset, 마이그레이션 후행 적용 시 잊기 쉬움. Vault 가 dashboard-managed + 안전. |
| mediamtx | `ffmpeg -listen 1 -f rtsp ...` | ffmpeg listen mode 는 single path only — 5종 (fire/helmet/forklift/person/fall) 동시 publish 어려움. mediamtx 가 5 paths 한 config 으로 깔끔. |

**Installation (Day 1 single-shot):**
```bash
# Python deps — 이미 설치됨 (verified opencv-python 4.12.0.88)
# 추가 X. ai_agent 의 기존 cv2 import 그대로 활용.

# mediamtx — Day 4 검증 직전 다운로드 (Windows)
# https://github.com/bluenviron/mediamtx/releases/latest
# (예: mediamtx_v1.x.x_windows_amd64.zip → bin/mediamtx/ 압축 해제)
```

**Version verification:**
- opencv-python: `pip show opencv-python` → 4.12.0.88 (verified 2026-05-15) [VERIFIED: pip env]
- ffmpeg: `ffmpeg -version` → 4.3.1 (verified 2026-05-15) [VERIFIED: bash]
- mediamtx: 최신 release v1.x (https://github.com/bluenviron/mediamtx/releases) [CITED]

## Architecture Patterns

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────┐
│  AI AGENT PROCESS (ai_agent/scheduler.py — single Python daemon)   │
│                                                                    │
│  APScheduler 1분 cycle (run_detection_cycle / run_fall_cycle):     │
│     ├─ for camera in cameras.fetch_active():                       │
│     │     rtsp_url = camera["live_url_detail"]                     │
│     │     ┌──────── snapshot.capture(rtsp_url, tmp) ──────────┐    │
│     │     │  if url.startswith("rtsp://"):                    │    │
│     │     │     return capture_rtsp(url, tmp, max_attempts=3) │    │
│     │     │  else:                                            │    │
│     │     │     return capture_ffmpeg(url, tmp)  [기존]       │    │
│     │     └────────────────────────────────────────────────────┘    │
│     │     │                                                        │
│     │     ▼ success                                                │
│     │  bridge.update_camera_health(camera_id)  ← NEW HELPER        │
│     │     │ PATCH cameras SET last_frame_at=now() WHERE id=$1      │
│     │     │                                                        │
│     │     ▼                                                        │
│     │  YOLO detector (Phase 1·2·3 무변경)                          │
│     │     │                                                        │
│     │     ▼ is_detected + frames_required                          │
│     │  bridge.register_ai_event() → detection_events insert        │
│                                                                    │
│  capture_rtsp 내부 (D-01·02):                                      │
│     for attempt in range(3):                                       │
│       cap = cv2.VideoCapture(url, cv2.CAP_FFMPEG)                  │
│       time.sleep(2)  ← RTSP handshake 대기                         │
│       ret, frame = cap.read()                                      │
│       if ret and cap.isOpened() and frame is not None:             │
│         cv2.imwrite(tmp, frame); cap.release(); return tmp         │
│       cap.release(); time.sleep(2)  ← retry backoff                │
│     raise SnapshotError(...)                                       │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼ (cameras.last_frame_at 갱신)
┌────────────────────────────────────────────────────────────────────┐
│  SUPABASE POSTGRES                                                 │
│                                                                    │
│  pg_cron 1분 주기 → cameras_healthcheck() 함수                      │
│     │                                                              │
│     ├─ SELECT cameras WHERE last_frame_at < now() - 5min           │
│     │     AND health_state != 'down'                               │
│     │     AND (last_alert_at IS NULL OR last_alert_at < now()-30m) │
│     ├─ UPDATE cameras SET health_state='down', last_alert_at=now() │
│     └─ pg_net.http_post(                                           │
│           url := '.../functions/v1/notifications',                 │
│           headers := jsonb {'Bearer ' ||                           │
│             (SELECT decrypted_secret FROM vault.decrypted_secrets  │
│              WHERE name='service_role_key')},  ← VAULT             │
│           body := {'action':'camera-down', 'camera_id':...}        │
│         )                                                          │
│  (회복 분기: WHERE last_frame_at >= now()-5min AND health_state='down') │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  EDGE FUNCTION  notifications/index.ts                             │
│                                                                    │
│  case 'camera-down':                                               │
│    SELECT user_id FROM profiles                                    │
│      WHERE group_id = $1                                           │
│      AND user_role IN ('manager','general_manager')                │
│    sendPushToUsers(supabase, userIds, {                            │
│      title: '카메라 통신두절',                                      │
│      body: `${cam.device_name} (${cam.install_area}) 5분+ 무수신`, │
│      data: {type:'camera_alert', camera_id, severity:'WARNING'},   │
│    })                                                              │
│                                                                    │
│  case 'camera-recovered': (동일 패턴, severity:'NORMAL')            │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│  SYNTHETIC RTSP (검증 단계만 — scripts/start_rtsp_mock.sh)          │
│                                                                    │
│  bin/mediamtx/mediamtx.exe scripts/mediamtx.yml &                  │
│    paths:                                                          │
│      fire:     {source: publisher}                                 │
│      helmet:   {source: publisher}                                 │
│      forklift: {source: publisher}                                 │
│      person:   {source: publisher}                                 │
│      fall:     {source: publisher}                                 │
│                                                                    │
│  ffmpeg -re -stream_loop -1 -i fire/source_v2.mp4 \                │
│         -c copy -f rtsp rtsp://localhost:8554/fire &               │
│  (5종 동시 background)                                             │
│                                                                    │
│  UPDATE cameras SET live_url_detail='rtsp://localhost:8554/fire'   │
│    WHERE camera_id=1; (검증 후 원복)                                │
└────────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
ai_agent/
├── snapshot.py              ← MODIFY (capture_rtsp + URL scheme 분기)
├── scheduler.py             ← UNCHANGED (capture() 호출 그대로)
├── supabase_client.py       ← MODIFY (update_camera_health 헬퍼 추가)
└── tests/
    └── test_snapshot_rtsp.py ← NEW (4 시나리오 + URL 분기)

supabase/
├── migrations/
│   └── 012_cameras_health.sql ← NEW (pg_net + Vault + cameras ALTER + healthcheck 함수 + cron)
└── functions/
    └── notifications/
        └── index.ts          ← MODIFY (case 'camera-down' + 'camera-recovered' 추가)

scripts/
├── start_rtsp_mock.sh        ← NEW (mediamtx + ffmpeg 5종 publish)
├── mediamtx.yml              ← NEW (5 paths 설정)
└── stop_rtsp_mock.sh         ← NEW (정리)

bin/
└── mediamtx/                 ← NEW (Day 4 다운로드, .gitignore)

app/src/main/java/com/example/smart_safety_management/
└── MyFirebaseMessagingService.kt  ← OPTIONAL (Option A 시 + camera_alerts channel)
```

### Pattern 1: cv2 capture_rtsp (D-01)

**What:** 1-shot RTSP frame 추출 + 3회 retry. drift_test.py 검증된 timing.
**When to use:** RTSP URL only. mp4 는 기존 ffmpeg subprocess.
**Example:**
```python
# Source: drift_test.py + opencv 4.12 prebuilt FFMPEG (VERIFIED env)
import time
import cv2
from pathlib import Path

# Optional: TCP 강제 (default 도 TCP 권장 — 방화벽/패킷손실 시 안정)
# import os; os.environ.setdefault("OPENCV_FFMPEG_CAPTURE_OPTIONS", "rtsp_transport;tcp")

BACKOFF_SEC = 2  # drift_test.py 의 sleep(2) 패턴

def capture_rtsp(url: str, output_path: Path | str, *, max_attempts: int = 3) -> Path:
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    last_err: str | None = None
    for attempt in range(max_attempts):
        cap = cv2.VideoCapture(url, cv2.CAP_FFMPEG)
        try:
            time.sleep(BACKOFF_SEC)  # RTSP handshake 대기 (drift_test.py)
            ret, frame = cap.read()
            if ret and cap.isOpened() and frame is not None:
                ok = cv2.imwrite(str(output_path), frame)
                if ok and output_path.exists() and output_path.stat().st_size > 0:
                    return output_path
                last_err = "cv2.imwrite returned False or empty file"
            else:
                last_err = (
                    f"cap.read ret={ret} isOpened={cap.isOpened()} "
                    f"frame_None={frame is None}"
                )
        finally:
            cap.release()
        if attempt < max_attempts - 1:
            time.sleep(BACKOFF_SEC)  # retry 사이 wait

    raise SnapshotError(
        f"RTSP capture failed after {max_attempts} attempts (url={url}): {last_err}"
    )
```

### Pattern 2: snapshot.capture URL scheme 분기

**What:** 기존 capture() 가 wrapper 가 되고, 내부에서 URL prefix 로 분기.
**When to use:** 모든 호출 (4 detector 진입점). SC #4 충족.
**Example:**
```python
def capture(
    url: str,
    output_path: Path | str,
    *,
    ffmpeg_bin: str = "ffmpeg",
    timeout_sec: int = 30,
    seek_seconds: float | None = None,
) -> Path:
    """RTSP 또는 mp4/file 에서 frame 추출. URL scheme 으로 자동 분기."""
    if url.lower().startswith(("rtsp://", "rtsps://")):
        # D-01: cv2.VideoCapture path. seek_seconds, timeout_sec 무시
        # (RTSP 라이브 스트림은 seek 불가).
        return capture_rtsp(url, output_path)
    # 기존 ffmpeg subprocess path (mp4, file://, http(s):// 영상)
    return _capture_ffmpeg(
        url, output_path,
        ffmpeg_bin=ffmpeg_bin,
        timeout_sec=timeout_sec,
        seek_seconds=seek_seconds,
    )

def _capture_ffmpeg(...):
    # 기존 capture() 본문 그대로 (snapshot.py:24-96 의 ffmpeg subprocess 로직)
    ...
```

### Pattern 3: cameras_healthcheck() pg_cron + pg_net + Vault

**What:** 1분 주기 healthcheck — 5분 무수신 시 down 전이 + Edge Function 호출.
**When to use:** 012 마이그레이션 1회 적용 후 자동 동작.
**Example:**
```sql
-- Source: Supabase Vault docs + pg_net API + Phase 4 D-09 알림 전이
-- ASSUMED: vault.secrets 에 'service_role_key' + 'edge_function_base_url' 시드됨.

CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;  -- C2 정정

-- Vault 시드 (1회 — dashboard 또는 마이그):
-- INSERT INTO vault.secrets (name, secret) VALUES
--   ('service_role_key', '<paste from project settings>'),
--   ('edge_function_base_url', 'https://xbjqxnvemcqubjfflain.supabase.co/functions/v1');

ALTER TABLE public.cameras
    ADD COLUMN IF NOT EXISTS last_frame_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS health_state   TEXT DEFAULT 'unknown'
        CHECK (health_state IN ('ok','degraded','down','unknown')),
    ADD COLUMN IF NOT EXISTS last_alert_at  TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION public.cameras_healthcheck() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    r           RECORD;
    sr_key      TEXT;
    base_url    TEXT;
BEGIN
    SELECT decrypted_secret INTO sr_key
        FROM vault.decrypted_secrets WHERE name = 'service_role_key';
    SELECT decrypted_secret INTO base_url
        FROM vault.decrypted_secrets WHERE name = 'edge_function_base_url';

    -- (a) DOWN 전이: 5분 무수신 + 30분 cooldown 통과
    FOR r IN
        SELECT camera_id, group_id, last_frame_at
        FROM public.cameras
        WHERE last_frame_at IS NOT NULL
          AND last_frame_at < now() - INTERVAL '5 minutes'
          AND health_state IS DISTINCT FROM 'down'
          AND (last_alert_at IS NULL OR last_alert_at < now() - INTERVAL '30 minutes')
    LOOP
        UPDATE public.cameras
            SET health_state = 'down', last_alert_at = now()
            WHERE camera_id = r.camera_id;

        PERFORM net.http_post(
            url := base_url || '/notifications',
            headers := jsonb_build_object(
                'Authorization', 'Bearer ' || sr_key,
                'Content-Type',  'application/json'
            ),
            body := jsonb_build_object(
                'action',        'camera-down',
                'camera_id',     r.camera_id,
                'group_id',      r.group_id,
                'last_frame_at', r.last_frame_at
            )
        );
    END LOOP;

    -- (b) RECOVERY 전이: down 상태에서 frame 다시 수신
    FOR r IN
        SELECT camera_id, group_id, last_frame_at
        FROM public.cameras
        WHERE health_state = 'down'
          AND last_frame_at >= now() - INTERVAL '5 minutes'
    LOOP
        UPDATE public.cameras
            SET health_state = 'ok', last_alert_at = now()
            WHERE camera_id = r.camera_id;

        PERFORM net.http_post(
            url := base_url || '/notifications',
            headers := jsonb_build_object(
                'Authorization', 'Bearer ' || sr_key,
                'Content-Type',  'application/json'
            ),
            body := jsonb_build_object(
                'action',    'camera-recovered',
                'camera_id', r.camera_id,
                'group_id',  r.group_id
            )
        );
    END LOOP;
END;
$$;

-- pg_cron 1분 주기 등록 (재실행 안전 — Phase 4 010 의 unschedule 패턴)
DO $$ BEGIN
    PERFORM cron.unschedule('cameras_healthcheck_minute');
EXCEPTION WHEN OTHERS THEN NULL; END $$;
SELECT cron.schedule(
    'cameras_healthcheck_minute',
    '* * * * *',
    $$SELECT public.cameras_healthcheck();$$
);
```

### Pattern 4: Edge Function camera-down (multi-recipient FCM)

**What:** notifications/index.ts 에 추가. group_id → manager N명 → sendPushToUsers.
**When to use:** pg_cron 이 호출. payload 검증 + manager 권한 SELECT + plural FCM.
**Example:**
```typescript
// Source: Phase 4 watch-alert + Phase 7 watch-pair 패턴 + sendPushToUsers (plural)
case "camera-down": {
    const { camera_id, group_id, last_frame_at } = body;
    if (!camera_id || !group_id) {
        return err("camera_id, group_id are required");
    }

    // 카메라 메타 (알림 본문용 — install_area 등)
    const { data: cam, error: camErr } = await supabase
        .from("cameras")
        .select("camera_id, device_name, install_area")
        .eq("camera_id", camera_id)
        .maybeSingle();
    if (camErr) return err(camErr.message, 500);

    // C5 정정: manager 권한 사용자 N명 → sendPushToUsers (plural)
    const { data: managers, error: mgrErr } = await supabase
        .from("profiles")
        .select("user_id")
        .eq("group_id", group_id)
        .in("user_role", ["manager", "general_manager"]);
    if (mgrErr) return err(mgrErr.message, 500);

    const userIds = (managers ?? []).map((m) => m.user_id);
    if (userIds.length === 0) {
        return ok({ ok: true, sent: 0, reason: "no managers in group" });
    }

    const camName = cam?.device_name ?? `camera-${camera_id}`;
    const camArea = cam?.install_area ?? "";
    const r = await sendPushToUsers(supabase, userIds, {
        title: "카메라 통신두절",
        body:  `${camName} (${camArea}) 5분 이상 frame 무수신`,
        data: {
            // C4 정정: Option B (deadline 우선) — fcm_default_channel 흐름,
            // type='camera_alert' 만 보내고 Android 측은 일반 showNotification 사용.
            // Option A (분리 채널) 채택 시 type='watch_alert' 와 별개로
            // showCameraAlertNotification() 추가하고 channel_id="camera_alerts" 반환.
            type: "camera_alert",
            camera_id: String(camera_id),
            severity:  "WARNING",
            last_frame_at: String(last_frame_at ?? ""),
        },
    });
    return ok({ ok: true, sent: r.sent, failed: r.failed, skipped: r.skipped });
}

case "camera-recovered": {
    // 동일 패턴 — title:"카메라 회복", severity:"NORMAL", body:"frame 수신 재개"
    // (코드는 camera-down 의 사본; 텍스트만 변경)
}
```

### Pattern 5: mediamtx + ffmpeg synthetic publish (D-04)

**What:** 5종 reference mp4 → 5종 RTSP path 동시 publish.
**When to use:** 검증 단계만 (Day 4). 운영 시는 Drift X3 실기기 (RTSP-02 v1.1).
**Example mediamtx.yml:**
```yaml
# Source: https://mediamtx.org/docs/usage/publish + GitHub bluenviron/mediamtx
# scripts/mediamtx.yml — 5 paths 모두 publisher 모드 (외부 ffmpeg 가 publish)
logLevel: info
rtspAddress: :8554

# runOnDemand 패턴: 클라이언트가 connect 할 때만 ffmpeg 시작 + auto-restart
paths:
  fire:
    runOnDemand: ffmpeg -re -stream_loop -1 -i reference_media/fire/source_v2.mp4 -c copy -f rtsp rtsp://localhost:$RTSP_PORT/$MTX_PATH
    runOnDemandRestart: yes
  helmet:
    runOnDemand: ffmpeg -re -stream_loop -1 -i reference_media/helmet/source_v2.mp4 -c copy -f rtsp rtsp://localhost:$RTSP_PORT/$MTX_PATH
    runOnDemandRestart: yes
  forklift:
    runOnDemand: ffmpeg -re -stream_loop -1 -i reference_media/forklift/source.mp4 -c copy -f rtsp rtsp://localhost:$RTSP_PORT/$MTX_PATH
    runOnDemandRestart: yes
  person:
    runOnDemand: ffmpeg -re -stream_loop -1 -i reference_media/person/source.mp4 -c copy -f rtsp rtsp://localhost:$RTSP_PORT/$MTX_PATH
    runOnDemandRestart: yes
  fall:
    runOnDemand: ffmpeg -re -stream_loop -1 -i reference_media/fall/source.mp4 -c copy -f rtsp rtsp://localhost:$RTSP_PORT/$MTX_PATH
    runOnDemandRestart: yes
```

**Example start_rtsp_mock.sh (Windows + Git Bash):**
```bash
#!/usr/bin/env bash
# scripts/start_rtsp_mock.sh — D-04 합성 검증 launcher
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MEDIAMTX_BIN="$ROOT/bin/mediamtx/mediamtx.exe"
CONFIG="$ROOT/scripts/mediamtx.yml"

if [[ ! -x "$MEDIAMTX_BIN" ]]; then
    echo "[FATAL] mediamtx binary not found at $MEDIAMTX_BIN"
    echo "        Download: https://github.com/bluenviron/mediamtx/releases/latest"
    echo "        Extract to: $ROOT/bin/mediamtx/"
    exit 1
fi

cd "$ROOT"
echo "[INFO] mediamtx server starting on :8554 (paths: fire, helmet, forklift, person, fall)"
exec "$MEDIAMTX_BIN" "$CONFIG"
```

**SQL — 시연 카메라 row 임시 변경:**
```sql
-- 검증 직전: 5 cameras 의 live_url_detail 을 mediamtx 로
UPDATE public.cameras SET live_url_detail = 'rtsp://localhost:8554/fire'     WHERE camera_id = 1;
UPDATE public.cameras SET live_url_detail = 'rtsp://localhost:8554/helmet'   WHERE camera_id = 5;
-- (forklift / person / fall 도 동일 패턴 — 실제 매핑은 cameras 시드 확인 후)

-- 검증 후 원복: scripts/restore_cameras_mp4.sql 또는 commit 직전 manual UPDATE
```

### Anti-Patterns to Avoid

- **CAP_PROP_BUFFERSIZE=1 의존**: FFMPEG backend 가 silently 무시함 ([opencv #23430](https://github.com/opencv/opencv/issues/23430)). 1-shot per cycle (fresh VideoCapture + 2s sleep + single read) 패턴이라 어차피 stale frame 위험 없음 — 의존 X. 코멘트로만 남기거나 제거.
- **단일 cap 객체 long-lived 재사용**: D-02 의 max_attempts=3 매번 fresh `cv2.VideoCapture(url, ...)` 가 정답. cap 재사용은 stale buffer + reconnect 로직 추가 필요.
- **Phase 4 watch-alert single-recipient 패턴 그대로 복사**: camera-down 은 manager N명. 반드시 sendPushToUsers (plural) + group_id 기반 SELECT.
- **service_role key 를 SQL 함수 본문에 hardcode**: 마이그 git 노출 + 회전 불가능. Vault 사용.
- **scheduler 에서 backoff 추가**: capture_rtsp() 내부에서만. 이중 retry = 알림 지연 폭증.
- **ffmpeg listen mode 로 5종 동시 publish 시도**: ffmpeg `-listen 1 -f rtsp` 는 single path. 5종은 mediamtx.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| RTSP frame 추출 | 자체 RTP socket parser | `cv2.VideoCapture(url, cv2.CAP_FFMPEG)` | drift_test.py 검증 + prebuilt FFMPEG. 1줄. |
| HTTP 호출 from SQL | 자체 PL/Python 또는 trigger + Edge Function poller | `net.http_post()` (pg_net) | Supabase 표준, async fire-and-forget, request_id tracking. |
| service_role key 보관 | 환경변수 (마이그에서 접근 X) / postgresql.conf GUC | Supabase Vault (`vault.decrypted_secrets`) | dashboard managed, 암호화 저장, 마이그 idempotent. |
| FCM HTTP v1 호출 | RS256 self-sign 직접 작성 | `_shared/fcm.ts` 의 `sendPushToUser(s)` | Phase 4·7 검증 패턴, UNREGISTERED 토큰 정리 포함. |
| Multi-recipient FCM batching | for loop 으로 sendPushToUser × N | `sendPushToUsers(supabase, userIds, payload)` | _shared/fcm.ts 가 이미 Promise.allSettled batched. |
| RTSP server (5 paths) | 자체 Go/Python RTSP server | mediamtx (bluenviron) | Production-grade, 1 yml config, runOnDemand 패턴. |
| pg_cron schedule | systemd timer + psql script | `cron.schedule(...)` | Postgres 내부 — agent 죽어도 실행. Phase 4 010 검증됨. |

**Key insight:** RTSP·pg_net·Vault·FCM 모두 검증된 라이브러리/extension 가 있다. 본 phase 의 신규 작성은 (a) capture_rtsp() 30 라인, (b) cameras_healthcheck() 60 라인, (c) Edge Function 2 case 80 라인, (d) mediamtx config 30 라인. **총 ~200 라인 신규 + 수정 ~50 라인**. CONTEXT.md 의 "가벼운 통합" 정신 충족.

## Runtime State Inventory

> Phase 8 은 신규 컬럼 + 신규 cron job + 신규 Vault secret + 신규 Edge Function action 추가. Rename/refactor 아님. 따라서 일부 카테고리만 적용.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | cameras 5행 (camera_id 1·2·3·5·기타) — last_frame_at NULL 로 ALTER 시 healthcheck 가 즉시 down 알림 보내지 않음 (NULL 가드 `last_frame_at IS NOT NULL` 필요) | healthcheck 함수 WHERE 절에 `last_frame_at IS NOT NULL` 가드. agent 첫 capture 까지는 unknown 상태 유지. |
| Live service config | mediamtx 서버 (Day 4 다운로드 후 검증 단계만) — git 미관리, scripts/start_rtsp_mock.sh 가 manual launcher | `bin/mediamtx/` .gitignore 추가. SUMMARY 에 launcher 사용법 기재. |
| OS-registered state | Windows: 없음 (mediamtx 는 console process, systemd 서비스 등록 X) | 검증 후 Ctrl-C 로 정리. v1.x 영구 운영 시 systemd service 검토 (deferred). |
| Secrets/env vars | **신규 Supabase Vault 시드**: `service_role_key` + `edge_function_base_url` 2개. 기존 secrets 와 무관. | 012 마이그가 `INSERT INTO vault.secrets ... ON CONFLICT DO NOTHING` 또는 dashboard 1회 시드. SUMMARY 에 시드 명령 기재. |
| Build artifacts | None — Python 단일 패키지, 컴파일 산출물 없음. | None. |

**중요:** 12 마이그 적용 전 Vault 시드 필수. Vault 시드 없이 마이그 적용 시 cron 함수가 매분 NULL 반환 + Edge Function 호출 X (silent fail). SUMMARY checklist 에 명시.

## Common Pitfalls

### Pitfall 1: pg_net 미설치로 healthcheck silent fail

**What goes wrong:** 012 마이그가 net.http_post() 호출하는데 pg_net extension 없음 → `function net.http_post(...) does not exist` 에러로 cron 함수 1분마다 실패.
**Why it happens:** Supabase managed Postgres 는 pg_net 을 pre-installed 하지만 default-enabled 는 아님. 001_extensions.sql 에 명시 X.
**How to avoid:** 012 마이그 첫 줄: `CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;`
**Warning signs:** Supabase Studio → Logs → cron job error "function net.http_post(...) does not exist"

### Pitfall 2: Vault 시드 누락으로 sr_key NULL

**What goes wrong:** healthcheck 함수가 `vault.decrypted_secrets` 에서 SELECT 하는데 row 없음 → `sr_key=NULL` → Authorization 헤더 `Bearer null` → Edge Function 401.
**Why it happens:** 마이그가 함수 정의만 하고 vault 시드 분리. Day 2 적용 후 Day 3 검증 시 발견.
**How to avoid:** 012 마이그 마지막에 `INSERT INTO vault.secrets (name, secret) VALUES (...) ON CONFLICT (name) DO NOTHING` (Vault API 확인 필요) 또는 dashboard 1회 시드 + SUMMARY checklist 명시.
**Warning signs:** healthcheck 함수 호출 후 net._http_response 에 status_code=401.

### Pitfall 3: Android camera_alerts channel 미존재로 알림 표시 안 됨

**What goes wrong:** Edge Function 이 `data.channel_id="camera_alerts"` 보내는데 MyFirebaseMessagingService 가 그 채널 ID 모름 → Android 8+ 에서 알림 silent.
**Why it happens:** CONTEXT.md D-03 의 ANDROID_CHANNEL_ID 가정 vs 실제 Android code 갭.
**How to avoid:** **Option B (권장)**: Edge Function payload 의 channel_id 명시 X (default `fcm_default_channel` 사용) + data.type='camera_alert'. Android 측 변경 0. v1.1 에서 분리 채널 추가. **Option A (정공)**: MyFirebaseMessagingService.kt 에 `showCameraAlertNotification()` + `camera_alerts` 채널 + `messaging.data.type=='camera_alert'` 분기 추가 (watch_alert 패턴 미러).
**Warning signs:** FCM 발송 200 OK 인데 Android 알림 트레이에 표시 안 됨.

### Pitfall 4: cameras_healthcheck() 가 NULL last_frame_at 즉시 down 처리

**What goes wrong:** 012 ALTER 직후 모든 cameras.last_frame_at = NULL → WHERE `last_frame_at < now() - INTERVAL '5 minutes'` 에서 NULL 비교 = NULL → row 안 잡힘 (의도) **하지만** 다른 SQL 실수로 `COALESCE(last_frame_at, '1970-01-01')` 같은 시도 시 즉시 down 폭발.
**Why it happens:** SQL NULL 비교 의미 혼동. unknown 상태 유지 의도.
**How to avoid:** WHERE 절에 명시적 `last_frame_at IS NOT NULL AND last_frame_at < now() - INTERVAL '5 minutes'`. agent 첫 capture 후에만 healthcheck 활성화.
**Warning signs:** 012 적용 직후 5종 cameras 가 모두 down 알림 발사.

### Pitfall 5: ffmpeg subprocess 가 mediamtx graceful shutdown 시 즉시 EOF

**What goes wrong:** D-04 backoff 검증 시 mediamtx 를 Ctrl-C 로 끄면 RTSP TEARDOWN signal 이 ffmpeg 에 도달 → ffmpeg 즉시 EOF → 본 phase 의 capture_rtsp 가 ret=False 로 즉시 fail (재시도 X 의도와 맞음). 하지만 mediamtx 만 죽이고 ffmpeg publish process 는 살리면 stale RTSP 응답 → 검증 의미 없음.
**Why it happens:** SIGKILL 와 SIGTERM 의 차이. RTSP TEARDOWN 메시지 vs hard kill.
**How to avoid:** backoff 검증 시 mediamtx 를 `kill -9` (SIGKILL) 또는 ffmpeg publish 만 kill (mediamtx 살림). scripts/stop_rtsp_mock.sh 에 두 옵션 명시.
**Warning signs:** kill 후 capture_rtsp 가 backoff 3회 모두 시도하지 않고 첫 시도에서 즉시 ret=False.

### Pitfall 6: RTSP UDP default → 패킷 손실 시 끊김 잦음

**What goes wrong:** cv2 의 FFMPEG backend 는 일반적으로 TCP default 지만 환경/버전에 따라 UDP 일 수 있음. 방화벽/네트워크 상황에 따라 packet loss → ret=False 빈발.
**Why it happens:** OpenCV FFMPEG backend rtsp_transport 기본값이 ambiguous ([opencv #21558](https://github.com/opencv/opencv/issues/21558)).
**How to avoid:** capture_rtsp() 호출 전에 `os.environ.setdefault("OPENCV_FFMPEG_CAPTURE_OPTIONS", "rtsp_transport;tcp")` 한 번. 또는 ai_agent 진입점 (config.py 또는 scheduler.py) 에서 import 직전 설정.
**Warning signs:** drift_test.py 는 동작하는데 본 phase capture_rtsp 만 ret=False 다발.

### Pitfall 7: pg_cron 함수 SECURITY DEFINER 누락 시 권한 오류

**What goes wrong:** cameras_healthcheck() 함수가 vault.decrypted_secrets 와 cameras UPDATE 권한 필요. SECURITY DEFINER 없으면 cron 실행 user 권한으로 동작.
**Why it happens:** PL/pgSQL default 는 SECURITY INVOKER.
**How to avoid:** 함수 정의에 `LANGUAGE plpgsql SECURITY DEFINER`. 위 Pattern 3 코드 반영됨.
**Warning signs:** cron logs "permission denied for table cameras" 또는 "permission denied for view decrypted_secrets".

### Pitfall 8: scheduler 의 4 detector 가 같은 cycle 에 같은 cameras row 4번 UPDATE

**What goes wrong:** Phase 1·2·3 의 4 detector (fire/helmet/forklift/person + fall + fusion) 가 동일 camera_id 에 동시 capture 호출 → update_camera_health() 4번 호출 → cameras row PostgREST PATCH 4×.
**Why it happens:** scheduler 의 capture 호출은 detector 별 독립.
**How to avoid:** **권장**: process-local cache (`_last_camera_health_update: dict[int, float]`) 으로 cycle 당 1회만 PATCH (예: 30초 윈도우). **간단**: 그냥 4번 PATCH 허용 — PostgREST idempotent, Supabase 부하 미미 (1분 cycle × 4 = 분당 20회 PATCH 정도). planner 결정.
**Warning signs:** 부하 의미 없음. logs 에 update_camera_health 4× per cycle 보일 뿐.

## Code Examples

### Example A: ai_agent/snapshot.py (D-01 + URL 분기)

이미 위 Pattern 1·2 에 전체 코드 제시. 핵심:
- 기존 `capture()` → `_capture_ffmpeg()` rename + URL scheme 분기 wrapper 신설
- 신규 `capture_rtsp()` 30 라인
- `SnapshotError` 그대로 (scheduler 가 catch)

### Example B: ai_agent/supabase_client.py (D-03 헬퍼 추가)

```python
# Source: 기존 SupabaseBridge 패턴 + Phase 8 D-03

def update_camera_health(self, camera_id: int) -> None:
    """capture 성공 직후 cameras.last_frame_at = now() 갱신.

    PostgREST PATCH 1회. 실패 시 warn 로깅만 (capture 자체는 성공한 상태).
    """
    try:
        self._client.table("cameras") \
            .update({"last_frame_at": "now()"}) \
            .eq("camera_id", camera_id) \
            .execute()
    except Exception as e:
        log.warning("update_camera_health failed camera_id=%s: %s", camera_id, e)
```
**주의**: PostgREST 의 `now()` 문자열은 server-side evaluation 안 됨 → Python `datetime.now(timezone.utc).isoformat()` 사용 필요. 또는 service_role 로 raw SQL 호출. planner 가 검증.

### Example C: ai_agent/tests/test_snapshot_rtsp.py (단위 테스트 4 시나리오)

```python
# Source: 기존 test_scheduler_buffer.py 의 mock 패턴 + drift_test.py 시나리오

import sys
import time
from pathlib import Path
from unittest.mock import MagicMock, patch
import pytest

AGENT_DIR = Path(__file__).resolve().parents[1]
if str(AGENT_DIR) not in sys.path:
    sys.path.insert(0, str(AGENT_DIR))

import snapshot  # noqa


@pytest.fixture
def fake_frame():
    """numpy ndarray 흉내 — cv2.imwrite 가 받기만 하면 OK."""
    arr = MagicMock(name="bgr_ndarray")
    arr.shape = (720, 1280, 3)
    return arr


def _make_cap(read_outcomes: list, is_opened: bool = True):
    """cv2.VideoCapture mock factory.

    read_outcomes: list of (ret, frame) tuples — 호출 순서대로 반환.
    """
    cap = MagicMock(name="VideoCapture")
    cap.isOpened.return_value = is_opened
    cap.read.side_effect = read_outcomes
    return cap


def test_rtsp_success_first_attempt(monkeypatch, fake_frame, tmp_path):
    """첫 시도 ret=True + frame OK → return 즉시. sleep × 1회 (handshake)."""
    cap = _make_cap([(True, fake_frame)])
    monkeypatch.setattr(snapshot.cv2, "VideoCapture", lambda *a, **kw: cap)
    monkeypatch.setattr(snapshot.cv2, "imwrite", lambda p, f: True)
    monkeypatch.setattr(snapshot, "time", MagicMock(sleep=MagicMock()))

    out = snapshot.capture_rtsp("rtsp://x/live", tmp_path / "out.jpg", max_attempts=3)
    # 첫 시도 성공이면 sleep 1회 (handshake). retry sleep 없음.
    assert snapshot.time.sleep.call_count == 1
    assert cap.read.call_count == 1


def test_rtsp_retry_then_success(monkeypatch, fake_frame, tmp_path):
    """[fail, fail, success] → 3번째 시도에서 return. sleep handshake×3 + retry×2."""
    caps = [
        _make_cap([(False, None)]),
        _make_cap([(False, None)]),
        _make_cap([(True, fake_frame)]),
    ]
    cap_iter = iter(caps)
    monkeypatch.setattr(snapshot.cv2, "VideoCapture", lambda *a, **kw: next(cap_iter))
    monkeypatch.setattr(snapshot.cv2, "imwrite", lambda p, f: True)
    monkeypatch.setattr(snapshot, "time", MagicMock(sleep=MagicMock()))

    snapshot.capture_rtsp("rtsp://x/live", tmp_path / "out.jpg", max_attempts=3)
    # handshake 3회 + retry 2회 = 5회 sleep
    assert snapshot.time.sleep.call_count == 5


def test_rtsp_three_failures_raises(monkeypatch, tmp_path):
    """3회 모두 ret=False → SnapshotError raise."""
    caps = [_make_cap([(False, None)]) for _ in range(3)]
    cap_iter = iter(caps)
    monkeypatch.setattr(snapshot.cv2, "VideoCapture", lambda *a, **kw: next(cap_iter))
    monkeypatch.setattr(snapshot, "time", MagicMock(sleep=MagicMock()))

    with pytest.raises(snapshot.SnapshotError, match="failed after 3 attempts"):
        snapshot.capture_rtsp("rtsp://x/live", tmp_path / "out.jpg", max_attempts=3)


def test_rtsp_isOpened_false_treated_as_fail(monkeypatch, fake_frame, tmp_path):
    """ret=True 지만 cap.isOpened()=False → 실패 취급 (drift_test.py 가드)."""
    cap = _make_cap([(True, fake_frame)], is_opened=False)
    monkeypatch.setattr(snapshot.cv2, "VideoCapture", lambda *a, **kw: cap)
    monkeypatch.setattr(snapshot, "time", MagicMock(sleep=MagicMock()))

    with pytest.raises(snapshot.SnapshotError):
        snapshot.capture_rtsp("rtsp://x/live", tmp_path / "out.jpg", max_attempts=1)


def test_capture_dispatch_rtsp(monkeypatch, tmp_path):
    """capture() wrapper 가 rtsp:// → capture_rtsp() 호출."""
    rtsp_called = MagicMock(return_value=tmp_path / "out.jpg")
    ffmpeg_called = MagicMock()
    monkeypatch.setattr(snapshot, "capture_rtsp", rtsp_called)
    monkeypatch.setattr(snapshot, "_capture_ffmpeg", ffmpeg_called)

    snapshot.capture("rtsp://cam/live", tmp_path / "out.jpg")
    assert rtsp_called.called
    assert not ffmpeg_called.called


def test_capture_dispatch_mp4(monkeypatch, tmp_path):
    """capture() wrapper 가 file/mp4 → 기존 ffmpeg subprocess 호출."""
    rtsp_called = MagicMock()
    ffmpeg_called = MagicMock(return_value=tmp_path / "out.jpg")
    monkeypatch.setattr(snapshot, "capture_rtsp", rtsp_called)
    monkeypatch.setattr(snapshot, "_capture_ffmpeg", ffmpeg_called)

    snapshot.capture("https://storage.example/fire/source_v2.mp4", tmp_path / "out.jpg")
    assert ffmpeg_called.called
    assert not rtsp_called.called
```

### Example D: Edge Function camera-down/recovered curl smoke

```bash
# Day 3 검증 — Edge Function 배포 후
SUPABASE_URL="https://xbjqxnvemcqubjfflain.supabase.co"
ANON_KEY="<anon key from .env>"

# (1) 정상 200 — manager 가 group_id=1 에 있다고 가정
curl -i -X POST "$SUPABASE_URL/functions/v1/notifications" \
  -H "apikey: $ANON_KEY" \
  -H "Authorization: Bearer $ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"action":"camera-down","camera_id":1,"group_id":1,"last_frame_at":"2026-05-15T12:00:00Z"}'

# (2) payload 누락 400
curl -i -X POST "$SUPABASE_URL/functions/v1/notifications" \
  -H "apikey: $ANON_KEY" \
  -H "Authorization: Bearer $ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"action":"camera-down"}'  # camera_id, group_id 누락

# (3) manager 없는 group_id → 200 + sent:0
curl -i -X POST "$SUPABASE_URL/functions/v1/notifications" \
  -H "apikey: $ANON_KEY" \
  -H "Authorization: Bearer $ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"action":"camera-down","camera_id":1,"group_id":99999,"last_frame_at":"2026-05-15T12:00:00Z"}'
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ffmpeg subprocess for RTSP (snapshot.py 현 구현) | cv2.VideoCapture(url, CAP_FFMPEG) (D-01) | 2026-05-15 | in-process 호출 + drift_test 검증 timing 일관성 |
| pg_cron static service_role in SQL | pg_cron + Vault.decrypted_secrets | Supabase Vault GA (2024+) | 회전 가능, 마이그 git 노출 X |
| Single-recipient FCM loop | sendPushToUsers (Promise.allSettled batched) | Phase 4 04-03 (2026-05-07) | 타이밍 ↓, error handling 일관 |
| pgsodium (deprecated) | Vault | Supabase 2024 권고 | 단순화 |

**Deprecated/outdated:**
- `current_setting('app.service_role_key')` 패턴 — postgresql.conf GUC 의존, 세션 영속 X. Vault 사용.
- `pgsodium` — Supabase Vault 로 이전 (Vault 가 pgsodium 위에 추상화).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | reference_media/forklift, person, fall mp4 가 존재 | Pattern 5 mediamtx.yml | mediamtx publish 실패 → 5종 중 일부만 검증. planner Day 4 진입 전 `ls reference_media/*/` 확인 필수. Phase 1·2·3 검증된 source_v2.mp4 는 fire/helmet 만 확인됨 (DATA-01·02). forklift/person/fall 의 reference media 파일명/존재는 [ASSUMED]. |
| A2 | `vault.secrets` INSERT 가 마이그레이션 SQL 에서 가능 (RLS 또는 권한 문제 X) | Pitfall 2 | 권한 문제 시 dashboard 1회 시드 fallback. SUMMARY checklist 명시 필수. [ASSUMED — Supabase docs 가 dashboard 시드를 권장하므로 SQL 시드는 검증 안 됨] |
| A3 | net.http_post 이 fire-and-forget 으로 즉시 return — healthcheck 함수 1분 timeout 위험 X | Pattern 3 | 표준 동작. async by design ([VERIFIED: pg_net docs](https://supabase.com/docs/guides/database/extensions/pg_net)). |
| A4 | 기존 cameras 5행 (1·2·3·5·기타) — group_id 가 모두 채워져 있고 manager 권한 사용자가 group_id 별로 1+ 명 존재 | Edge Function camera-down | manager 0명이면 sent=0 (silent). 데모 시 알림 안 옴. planner 가 testuser1 의 user_role + group_id 확인 필수. |
| A5 | mediamtx Windows binary 가 Git Bash 에서 실행 가능 (`.exe`) | scripts/start_rtsp_mock.sh | macOS/Linux 사용자에는 `.exe` 분기 필요 — 본 프로젝트는 Windows 단일 환경이라 OK. [VERIFIED: env Windows 11] |
| A6 | PostgREST 의 update with `"now()"` string vs Python datetime | Example B (update_camera_health) | PostgREST 가 `"now()"` 를 string literal 로 PATCH 하면 cast 실패 → planner 가 `datetime.now(timezone.utc).isoformat()` 으로 보정. |
| A7 | Android Option B (fcm_default_channel 재사용) 으로 알림 표시 OK | Pitfall 3 / C4 정정 | 이미 verified 패턴 (기존 일반 알림이 동일 채널 사용). 시각적 구분 약함 — v1.1 분리. |
| A8 | testuser1 (manager) 의 group_id 와 cameras 1·5 의 group_id 일치 | A4 와 동일 | 시드 확인 후 결정. planner Day 2 task 마지막에 `SELECT user_role, group_id FROM profiles WHERE user_id='testuser1'; SELECT camera_id, group_id FROM cameras WHERE camera_id IN (1,5);` 검증. |

**8개 [ASSUMED] 모두 planner 가 Day 진입 전 검증할 수 있는 사항.** 본 research 는 표준 패턴을 제시 — 시드/권한 정합은 plan task 의 첫 step.

## Open Questions

1. **Android 알림 채널 분리 정책 (Option A vs B — Pitfall 3)**
   - What we know: MyFirebaseMessagingService 에 watch_alerts 채널 분리 선례. Phase 4 D-11 + Phase 7 D-03 가 fcm.ts 의 ANDROID_CHANNEL_ID 단일 상수 사용.
   - What's unclear: 5월 PPT 시연에서 카메라 알림과 일반 알림을 시각적으로 구분해야 하는가?
   - Recommendation: **Option B (fcm_default_channel 재사용)** — Android 코드 변경 0, deadline D-5 우선. v1.1 에서 분리.

2. **cameras_healthcheck() 함수의 SECURITY DEFINER owner**
   - What we know: SECURITY DEFINER + cron 실행 user 가 supabase_admin 또는 postgres role.
   - What's unclear: Vault decrypted_secrets SELECT 권한이 어느 role 에 있는지.
   - Recommendation: planner Day 2 에 마이그 적용 후 `SELECT cameras_healthcheck()` 수동 호출 → 에러 메시지 확인. supabase_admin 또는 service_role owner 변경 필요할 수 있음.

3. **scheduler 의 update_camera_health() 호출 주기 (Pitfall 8)**
   - What we know: 4 detector × 1분 cycle = 카메라당 4번 PATCH/min.
   - What's unclear: Supabase Free Tier 의 PostgREST rate limit 영향 정도.
   - Recommendation: **간단 (4× PATCH 허용)** default. 부하 측정 후 v1.1 에 cycle-단위 dedup. 체감 지연 ↑ 시 process-local cache (`_last_health_update[camera_id] > now()-30s` skip) 추가.

4. **mediamtx 다운로드 + .gitignore 추가 시점**
   - What we know: `bin/mediamtx/` 약 30MB binary.
   - What's unclear: CI/CD 환경에 영향 (현재 CI 없음 verified).
   - Recommendation: Day 4 진입 직전 다운로드 + `.gitignore` 에 `bin/` 추가. 마이그레이션·코드 커밋과 분리.

5. **reference_media/{forklift,person,fall}/ 존재 여부 (A1)**
   - What we know: fire + helmet 은 source_v2.mp4 검증됨 (DATA-01·02).
   - What's unclear: forklift/person/fall 의 reference media 파일 존재 여부.
   - Recommendation: planner Day 4 첫 task = `ls reference_media/forklift reference_media/person reference_media/fall` 확인. 없으면 fire/helmet 2 path 만 검증 + 나머지 3종은 동일 파일 재사용 (mediamtx path 만 다르게).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Python 3.10/11 (ai_agent) | requirements.txt 명시 | ⚠ system 3.13 | 3.13.5 | ai_agent 자체 venv 가 있을 가능성 — `where python` 확인 필요 (ai_agent 디렉터리 내 venv 확인) |
| opencv-python 4.12+ | cv2.CAP_FFMPEG | ✓ | 4.12.0.88 (FFMPEG=YES prebuilt) | — |
| ffmpeg | mp4 fallback + mediamtx publish | ✓ | 4.3.1 | — |
| pg_cron | healthcheck cron | ✓ (001_extensions.sql 적용됨) | — | — |
| **pg_net** | net.http_post | **✗** (Supabase pre-installed but not enabled) | — | 012 마이그가 `CREATE EXTENSION IF NOT EXISTS pg_net` |
| Supabase Vault | vault.decrypted_secrets | ✓ (managed Postgres 기본) | — | postgresql.conf GUC 또는 함수 내 hardcode (둘 다 비추) |
| **mediamtx** | 합성 RTSP server | **✗** (CONTEXT 가정 경로 부재) | — | (a) Day 4 직전 다운로드 https://github.com/bluenviron/mediamtx/releases (b) ffmpeg `-listen 1` single path fallback |
| supabase-py | SupabaseBridge | ✓ | >=2.5.0 (requirements.txt) | — |
| pytest | 단위 테스트 | ✓ (기존 test_scheduler_buffer 검증됨) | — | — |

**Missing dependencies with no fallback:**
- 없음 (모두 download/install 가능)

**Missing dependencies with fallback:**
- mediamtx — 다운로드 (Day 4 task)
- pg_net — 012 마이그 첫 줄 CREATE EXTENSION (Day 2 task)

**Action items:**
1. Day 1 시작 전: `ls D:\2026_산업안전\Smart_Safety_Management\ai_agent\.venv\` 또는 `ai_agent\Scripts\python.exe` 확인 → ai_agent 가 사용하는 정확한 Python 버전 verify
2. Day 4 시작 전: mediamtx 다운로드 + bin/mediamtx/ 압축 해제 + scripts/start_rtsp_mock.sh 실행 확인

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | pytest >= 7.x (verified by 기존 test_scheduler_buffer.py + test_fusion.py PASS) |
| Config file | none — `tests/__init__.py` + `conftest.py` 패턴 (sys.path 조정 in-file) |
| Quick run command | `cd ai_agent && python -m pytest tests/test_snapshot_rtsp.py -x` |
| Full suite command | `cd ai_agent && python -m pytest tests/ -x -v` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| RTSP-01 | URL scheme 분기 (rtsp:// → capture_rtsp / mp4 → ffmpeg) | unit | `pytest tests/test_snapshot_rtsp.py::test_capture_dispatch_rtsp -x` | ❌ Wave 0 |
| RTSP-01 | URL scheme 분기 fallback | unit | `pytest tests/test_snapshot_rtsp.py::test_capture_dispatch_mp4 -x` | ❌ Wave 0 |
| RTSP-01 | scheduler --once-detect → detection_events 적재 (mediamtx 합성) | integration (E2E manual) | `python -m ai_agent.scheduler --once-detect` + SQL `SELECT count(*) FROM detection_events WHERE created_at > now()-INTERVAL '5min'` | n/a (Day 4) |
| RTSP-02 | Drift X3 실기기 ≤10s 지연 | manual (실기기) | (deferred) | (deferred) |
| RTSP-03 | capture_rtsp 첫 시도 성공 | unit | `pytest tests/test_snapshot_rtsp.py::test_rtsp_success_first_attempt -x` | ❌ Wave 0 |
| RTSP-03 | capture_rtsp 2회 fail + 3회 success | unit | `pytest tests/test_snapshot_rtsp.py::test_rtsp_retry_then_success -x` | ❌ Wave 0 |
| RTSP-03 | capture_rtsp 3회 모두 fail → SnapshotError | unit | `pytest tests/test_snapshot_rtsp.py::test_rtsp_three_failures_raises -x` | ❌ Wave 0 |
| RTSP-03 | cap.isOpened()=False → fail 처리 | unit | `pytest tests/test_snapshot_rtsp.py::test_rtsp_isOpened_false_treated_as_fail -x` | ❌ Wave 0 |
| RTSP-03 | Edge Function camera-down 정상 200 | smoke | `bash scripts/curl_camera_down_smoke.sh` (Day 3 신규) | ❌ Wave 0 |
| RTSP-03 | Edge Function camera-down payload 누락 400 | smoke | (위 스크립트 case 2) | ❌ Wave 0 |
| RTSP-03 | Edge Function camera-recovered 정상 200 | smoke | `bash scripts/curl_camera_recovered_smoke.sh` | ❌ Wave 0 |
| RTSP-03 | pg_cron healthcheck E2E (mediamtx kill → 5분 → cameras.health_state=down) | manual integration | (Day 4 — sleep 5min + SQL select) | n/a |

### Sampling Rate

- **Per task commit:** `cd ai_agent && python -m pytest tests/test_snapshot_rtsp.py -x` (unit, ~2초)
- **Per wave merge:** `cd ai_agent && python -m pytest tests/ -x -v` (full suite ~10초) + Edge Function curl smoke
- **Phase gate (수요일):** Full suite green + 합성 mediamtx E2E (RTSP-01 + 헬스체크 round-trip 5분) + scheduler --once-detect 1회

### Wave 0 Gaps

- [ ] `ai_agent/tests/test_snapshot_rtsp.py` — RTSP-01·03 6 unit cases
- [ ] `scripts/curl_camera_down_smoke.sh` — Edge Function 3 smoke (정상 200 / payload 누락 400 / no-manager group 200+sent:0)
- [ ] `scripts/curl_camera_recovered_smoke.sh` — Edge Function 1 smoke (정상 200)
- [ ] `scripts/mediamtx.yml` — 5 paths runOnDemand 패턴
- [ ] `scripts/start_rtsp_mock.sh` + `scripts/stop_rtsp_mock.sh` — Day 4 검증 launcher
- [ ] `bin/mediamtx/mediamtx.exe` — Day 4 다운로드 (.gitignore)

*(이미 존재: pytest 7+, ai_agent/tests/__init__.py, MagicMock 패턴 — test_scheduler_buffer.py 참고)*

## Sources

### Primary (HIGH confidence)
- [drift_test.py](D:\2025_산업안전\산업안전\Dirft 카메라\DriftX3\drift_test.py) — 사용자 검증된 RTSP 패턴 원천
- [snapshot.py:24-96](D:\2026_산업안전\Smart_Safety_Management\ai_agent\snapshot.py) — 기존 ffmpeg subprocess 패턴 (mp4 fallback 보존 대상)
- [scheduler.py:36,72,128,228,328](D:\2026_산업안전\Smart_Safety_Management\ai_agent\scheduler.py) — capture() 호출 4곳 (변경 X 검증)
- [notifications/index.ts](D:\2026_산업안전\Smart_Safety_Management\supabase\functions\notifications\index.ts) — case 'watch-alert/ack/pair' 패턴 (camera-down 미러 base)
- [_shared/fcm.ts](D:\2026_산업안전\Smart_Safety_Management\supabase\functions\_shared\fcm.ts) — sendPushToUsers (plural, Promise.allSettled batched)
- [010_watch_pipeline.sql](D:\2026_산업안전\Smart_Safety_Management\supabase\migrations\010_watch_pipeline.sql) — pg_cron unschedule + schedule 패턴 (012 mirror)
- [test_scheduler_buffer.py](D:\2026_산업안전\Smart_Safety_Management\ai_agent\tests\test_scheduler_buffer.py) — pytest mock 패턴 (test_snapshot_rtsp 의 base)
- [MyFirebaseMessagingService.kt:40-124](D:\2026_산업안전\Smart_Safety_Management\app\src\main\java\com\example\smart_safety_management\MyFirebaseMessagingService.kt) — channel 정의 (camera_alerts 부재 verified)
- [pg_net: Async Networking | Supabase Docs](https://supabase.com/docs/guides/database/extensions/pg_net) — pg_net pre-installed but not enabled by default
- [Publish a stream | MediaMTX](https://mediamtx.org/docs/usage/publish) — runOnDemand + ffmpeg `-re -stream_loop -1` 공식 패턴
- [GitHub bluenviron/mediamtx](https://github.com/bluenviron/mediamtx) — release binaries

### Secondary (MEDIUM confidence)
- [Recommended Pattern for Cron Job -> Edge Function Auth](https://www.answeroverflow.com/m/1426300945578590301) — Vault 패턴 (community 권장)
- [Secure API Calls from DB Functions with Supabase pg_net and Vault](https://tomaspozo.com/articles/secure-api-calls-supabase-pg-net-vault) — vault.decrypted_secrets 사용 예시
- [OpenCV forum: rtsp_transport tcp](https://forum.opencv.org/t/why-is-it-still-tcp-when-i-using-opencv-python-rtsp-capture-option-udp/18475) — OPENCV_FFMPEG_CAPTURE_OPTIONS 문법
- [opencv-python on PyPI](https://pypi.org/project/opencv-python/) — cp37-abi3 wheel = Python 3.13 호환

### Tertiary (LOW confidence)
- [opencv issues #23430 (CAP_PROP_BUFFERSIZE silently ignored)](https://github.com/opencv/opencv/issues/23430) — 일반론, 본 use case (1-shot per cycle) 에는 무관

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — opencv-python + ffmpeg + pg_cron 모두 env verified, mediamtx 다운로드 명확
- Architecture: HIGH — Phase 4·7 패턴 일관 + Vault/pg_net 표준 패턴 verified
- Pitfalls: HIGH — 5건 모두 verified primary source 또는 env 검증
- Code examples: HIGH — drift_test.py + 기존 snapshot.py + 기존 notifications/index.ts 의 sub-patterns 확장
- Validation Architecture: HIGH — 기존 pytest 인프라 + Edge Function curl smoke 패턴 (Phase 7 02 검증됨)

**Research date:** 2026-05-15
**Valid until:** 2026-05-22 (1주 — 마감일 D-5 + 7일 buffer)

---

## RESEARCH COMPLETE — Critical Pitfalls Summary

### CONTEXT.md 5 정정사항 (planner 즉시 반영)
1. **mediamtx 미설치** → Day 4 다운로드 task (`bin/mediamtx/`, .gitignore)
2. **pg_net 미활성** → 012 마이그 첫 줄 `CREATE EXTENSION pg_net WITH SCHEMA extensions`
3. **service_role key 보관** → Supabase Vault 시드 + `vault.decrypted_secrets` 패턴 (NOT `app.service_role_key` GUC)
4. **camera_alerts Android channel 부재** → Option B (fcm_default_channel 재사용, 코드변경 0) 권장
5. **manager 알림 다수** → `sendPushToUsers (plural)` + `WHERE user_role IN ('manager','general_manager') AND group_id=$1`

### Wave 0 Gaps
- ai_agent/tests/test_snapshot_rtsp.py (6 unit cases)
- scripts/{mediamtx.yml, start_rtsp_mock.sh, stop_rtsp_mock.sh, curl_camera_down_smoke.sh, curl_camera_recovered_smoke.sh}
- bin/mediamtx/ + .gitignore

### Day 1~5 권장 순서
- **Day 1**: capture_rtsp + URL 분기 (snapshot.py) + pytest 6 cases — D-01·02
- **Day 2**: 012_cameras_health.sql (pg_net + Vault + ALTER + healthcheck 함수 + cron) + Vault 시드 1회 — D-03 a
- **Day 3**: notifications/index.ts case camera-down/recovered + curl smoke + supabase_client.update_camera_health — D-03 b
- **Day 4**: mediamtx 다운로드 + scripts/{mediamtx.yml, start_rtsp_mock.sh} + 합성 publish 5종 + scheduler --once-detect E2E + cameras row 임시 변경 + 5분 sleep healthcheck round-trip — D-04
- **Day 5 (수요일)**: cameras row 원복 + SUMMARY 작성 + buffer (Phase 9 진입 또는 deferred 정리)

### 운영 모드 전환 (시연용 → 6월 검단·포천 v1.1)
```sql
-- v1.1: code redeploy 0, 단순 row update 만으로 RTSP 전환
UPDATE cameras SET live_url_detail = 'rtsp://drift-x3-real-ip/live' WHERE camera_id = 1;
```
SC #4 자동 충족.

---
*Phase: 08-rtsp-camera*
*Research completed: 2026-05-15*
