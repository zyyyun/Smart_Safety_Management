# Phase 10: LAN RTSP 자동 수신 감지 - Context

**Gathered:** 2026-05-22
**Status:** Ready for planning / partially implemented

<domain>
## Phase Boundary

PC와 Drift X3 카메라가 같은 네트워크에 있다는 전제에서, 본부/현장 PC의
`SmartSafetyAiAgent` Windows service 가 상시 실행된다. 사용자가 별도 PowerShell
스크립트를 실행하지 않아도 service 안에서 RTSP 카메라 수신 가능 상태를 자동 감지하고,
기존 Phase 8 YOLO detection pipeline 을 즉시 구동한다.

**Out of scope**:
- 모바일 Supabase Storage frame sampler productionization.
- Tailscale / WebRTC / RTMP / Cloud GPU relay.
- Jetson 포팅 및 ARM64 성능 검증.
- 카메라 자동 discovery by subnet scan. v1 은 DB에 등록된 `cameras.live_url_detail`
  RTSP URL만 감지한다.

</domain>

<decisions>
## Implementation Decisions

### Architecture gate
- **D-01:** Phase 10은 Approach 5 모바일 frame sampler가 아니라 **same-LAN direct RTSP + PC service auto-detect** 로 전환한다.
- **D-02:** YOLO는 모바일이 아니라 PC의 기존 `ai_agent/main.py` / `scheduler.py` 경로에서 실행한다.
- **D-03:** 사용자는 `demo_rtsp_real_camera.ps1`, `rtsp_poc_pull.py`, `python main.py --once-detect` 를 수동 실행하지 않는다. 상시 실행 책임은 `scripts/ai_agent_service.ps1` 의 Windows service 가 가진다.

### Camera readiness signal
- **D-04:** 카메라 수신 가능 여부는 RTSP URL의 host:port TCP connect 로 빠르게 probe 한다.
- **D-05:** TCP probe 는 cheap signal 이고, 실제 frame 유효성은 기존 `snapshot.capture_rtsp()` 가 검증한다.
- **D-06:** v1 은 DB에 이미 등록된 `cameras.live_url_detail` 만 대상으로 한다. subnet scan, mDNS, ONVIF discovery 는 별도 phase.

### Trigger behavior
- **D-07:** `unknown/unreachable -> reachable` 전이에서만 즉시 detection cycle 을 깨운다.
- **D-08:** 기존 1분 `general_detection` scheduler job 은 유지한다. 자동 감지는 "카메라가 막 붙었을 때" 첫 cycle 을 당기는 역할이다.
- **D-09:** 자동 감지 job 과 기존 detection job 이 겹치면 `_detection_cycle_lock` 으로 중복 실행을 스킵한다.
- **D-10:** general detector 가 로드되어 있으면 `run_detection_cycle()`, fall detector 가 로드되어 있으면 `run_fall_cycle()` 도 함께 트리거한다.

### Operator model
- **D-11:** 현장 설치 운영 절차는 "PC 부팅 -> service 자동 시작 -> 카메라 WiFi 연결 -> RTSP reachable 감지 -> YOLO 자동 시작" 이다.
- **D-12:** service 상태/로그 확인은 기존 스크립트를 사용한다.
  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Status
  powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Logs
  ```

### the agent's Discretion
- Probe interval/timeout 기본값은 15초 / 1.5초로 둔다.
- 환경변수로 조정 가능하게 하고, 운영 중 더 빠른 감지가 필요하면 interval 을 낮춘다.

</decisions>

<specifics>
## Specific Ideas

- 사용자가 명시한 새 전제: "pc 모바일 디바이스가 같은 네트워크에 있다."
- 사용자가 명시한 운영 목표: "현재 pc에서 따로 스크립트를 실행하지 않더라도 카메라의 수신을 자동으로 감지하여 yolo detect를 구동."
- 기존 모바일 frame sampler PoC 문서는 historical evidence 로 남기되, Phase 10의 현 실행 방향은 아니다.

</specifics>

<canonical_refs>
## Canonical References

### Phase 10 planning
- `.planning/phases/10-lan-rtsp-autodetect/10-01-PLAN.md` — 새 LAN auto-detect plan.
- `.planning/explorations/2026-05-21_rtsp_mobile_relay_architecture.md` — 이전 architecture matrix 와 Approach 4/5 배경.
- `.planning/explorations/2026-05-21_rtsp_poc_results.md` — 모바일 frame sampler PoC 결과 scaffold. 새 방향에서는 historical evidence.

### Existing implementation
- `ai_agent/main.py` — Windows service 가 실행하는 상시 agent entrypoint.
- `ai_agent/scheduler.py` — RTSP camera expansion, detection/fall/fusion cycles, 신규 auto-detect job.
- `ai_agent/snapshot.py` — Drift X3 RTSP capture path.
- `scripts/ai_agent_service.ps1` — PC에서 별도 스크립트 없이 agent를 상시 실행시키는 NSSM service wrapper.
- `.planning/phases/08-rtsp-camera/08-CONTEXT.md` — Phase 8 direct RTSP capture/detection decisions.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `scripts/ai_agent_service.ps1`: 이미 "사용자가 PowerShell을 매번 켜지 않아도 됨" 요구를 해결하는 Windows service wrapper.
- `scheduler._expand_target_cameras()`: RTSP URL 카메라는 모든 detector/fusion/fall 대상에 자동 포함.
- `snapshot.capture_rtsp()`: Drift X3 검증 패턴 기반 direct RTSP frame capture.

### Established Patterns
- RTSP 카메라 판별은 `cameras.live_url_detail` 이 `rtsp://` 또는 `rtsps://` 로 시작하는지로 결정.
- 카메라 health source 는 capture 성공 직후 `cameras.last_frame_at` 갱신.
- 서비스 로그는 `logs/ai_agent.log` 와 `logs/ai_agent_service.stderr.log` 를 확인.

### Integration Points
- `build_scheduler()` 에 `rtsp_autodetect` interval job 추가.
- `Settings` 에 `RTSP_AUTODETECT_*` 환경변수 추가.
- 기존 `run_detection_cycle()` 과의 overlap 은 module-level lock 으로 제어.

</code_context>

<deferred>
## Deferred Ideas

- 등록된 카메라 없이 LAN subnet scan 으로 Drift X3를 자동 discovery.
- ONVIF discovery 또는 mDNS 기반 camera discovery.
- Android 앱에서 PC agent status 를 실시간 확인하는 운영 화면.
- 모바일 frame sampler v2 production path.
- Jetson/미니PC ARM64 deployment path.

</deferred>

---

*Phase: 10-lan-rtsp-autodetect*
*Context gathered: 2026-05-22*
