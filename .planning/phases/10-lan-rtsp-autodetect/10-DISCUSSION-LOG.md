# Phase 10 Discussion Log

**Date:** 2026-05-22
**Phase:** RTSP 모바일 프레임 샘플러 PoC -> LAN RTSP 자동 수신 감지

## Initial Selection

The user selected:

- Existing plan handling: `Continue + replan after`
- First gray area: `Decision gate`

## Direction Change

During the Decision gate discussion, the user changed the phase direction:

> PC와 모바일/카메라 디바이스가 같은 네트워크에 있다는 가정하에, PC에서 따로
> 스크립트를 실행하지 않아도 카메라의 수신을 자동으로 감지하여 YOLO detect를
> 구동할 수 있게 만들 것.

## Decisions Captured

### Architecture gate

- The mobile Supabase Storage frame sampler is no longer the active Phase 10 path.
- Phase 10 now assumes same-LAN direct RTSP.
- The PC runs the existing `ai_agent/main.py` as a Windows service.
- The service, not a user-run script, owns detection startup.

### Camera readiness

- Readiness is detected by TCP probing the registered RTSP URL's host/port.
- Only DB-registered cameras are in scope for v1.
- Subnet scan, ONVIF, and discovery are deferred.

### Trigger model

- On `unknown/unreachable -> reachable`, trigger the existing YOLO detection cycle immediately.
- Keep the existing 1-minute scheduled detection cycle as the steady-state safety net.
- Guard overlap with a process-level detection lock.

## Deferred

- Mobile frame sampler production hardening.
- Jetson/mini PC production path.
- LAN camera auto-discovery without registration.

