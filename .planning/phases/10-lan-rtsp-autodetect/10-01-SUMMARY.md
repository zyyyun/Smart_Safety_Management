---
phase: 10
plan: 01
title: "LAN RTSP 자동 수신 감지 + YOLO service trigger"
status: complete
completed: 2026-05-22
branch: feature_rtps_test
---

# Phase 10 Plan 01 Summary — LAN RTSP 자동 수신 감지

## Outcome

PC와 Drift X3 카메라가 같은 LAN 에 있다는 전제에서, 사용자가 본부/현장 PC에서
`demo_rtsp_real_camera.ps1` 또는 별도 pull 스크립트를 실행하지 않아도
`SmartSafetyAiAgent` Windows service 가 등록된 RTSP URL 의 수신 가능 상태를
주기적으로 감지하고 기존 YOLO detection cycle 을 자동 구동하도록 구현했다.

이전 Phase 10 초안인 모바일 Supabase Storage frame sampler PoC 는 historical
evidence 로 보존하고, active path 는 PC service 중심의 LAN RTSP auto-detect 로
전환했다.

## Code Changes

- `ai_agent/config.py`
  - `RTSP_AUTODETECT_ENABLED`
  - `RTSP_AUTODETECT_INTERVAL_SEC`
  - `RTSP_AUTODETECT_TIMEOUT_SEC`
  - boolean env parsing helper 추가
- `ai_agent/scheduler.py`
  - RTSP URL host/port TCP probe helper 추가
  - `run_rtsp_autodetect_cycle()` 추가
  - `unknown/unreachable -> reachable` 전이에서 기존 `run_detection_cycle()` 즉시 호출
  - fall detector 가 로드된 경우 `run_fall_cycle()` 도 함께 호출
  - 기존 1분 detection job 과 auto-detect trigger 의 중복 실행을 막는 lock 추가
  - APScheduler interval job `rtsp_autodetect` 등록
- `ai_agent/main.py`
  - startup log 에 auto-detect enabled/interval 값을 노출
- `ai_agent/.env.example`
  - RTSP auto-detect env 기본값 문서화
- `ai_agent/tests/test_scheduler_rtsp_autodetect.py`
  - probe host/port parsing
  - reachable transition 1회 trigger
  - disabled 상태에서 DB fetch/detection skip 검증

## Verification

Automated verification passed:

```powershell
python -m py_compile ai_agent\config.py ai_agent\scheduler.py ai_agent\main.py
C:\Users\ANNA\miniconda3\python.exe -m pytest ai_agent\tests -q
```

Result:

- `ai_agent` full test suite: `31 passed in 6.60s`
- Targeted auto-detect tests: `3 passed`
- Related scheduler/RTSP regression tests: `17 passed`
- `git diff --check`: pass, CRLF warnings only

Service status check:

- `SmartSafetyAiAgent` is installed with `StartType: Automatic`
- Current status during execution: `Stopped`
- Real auto-detect operation requires starting/restarting the service from an elevated PowerShell.

## Manual Verification Scenario

1. Android app camera registration must have a camera row whose `live_url_detail` is the Drift X3 RTSP URL, for example:
   `rtsp://192.168.0.13/live`
2. PC and camera must be on the same LAN.
3. Start or restart the agent service:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Start
   ```
4. Turn on the Drift X3 or reconnect it to WiFi.
5. Check `logs/ai_agent.log` for:
   - `rtsp_autodetect: camera_id=... reachable`
   - detection cycle log lines or no-detect log lines
6. Confirm at least one of:
   - `detection_events` receives a YOLO event within about 1 minute
   - logs show the detection cycle ran and produced no detection

## Deviations

- Direction changed from mobile Storage frame sampler to same-LAN PC service auto-detect after user clarification.
- `gsd-sdk` was not available in this local environment, so GSD state files were updated directly.
- Live camera/service verification remains manual because the service was installed but stopped at execution time.
