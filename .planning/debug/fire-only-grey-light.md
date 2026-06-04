---
slug: fire-only-grey-light
status: superseded_by_phase_15  # 2026-05-23 사용자 결정: NSSM 폐기 + Docker 화로 architectural fix. evidence 도착 시 진단 마저 진행 가능 (Suspect 2 YOLO26 model load 별도 fix 필요한지 확인용).
resolution_phase: 15
created: 2026-05-23
updated: 2026-05-23
trigger: |
  사용자가 `.\scripts\demo_rtsp_real_camera.ps1 -FireOnly -CameraIds 1 -NumCycles 5` 실행 후
  Android 실시간 화면의 YOLO 탐지 상태 태그가 계속 회색 (= 검출 안 됨 / unknown 상태) 으로 표시됨.
  YOLO 탐지 자체가 실행이 안 되는 것으로 보임.
related_commits:
  - 9439c47 # docs(state) quick task 01 RTSP YOLO state 라벨
  - 137af42 # feat(realtime) RTSP 카메라 YOLO 동작 state 라벨 + 30s polling
  - 69880d3 # FIRE-ADV-01 YOLO26 fire 모델 교체
  - 666b123 # load_dotenv override=False + demo_rtsp Cyan filter 보강
  - 12e5e2f # demo_rtsp 단일 detector 시연 옵션 (-FireOnly 추가)
  - 7d81af7 # RTSP 실 카메라 자동 5종 detector
related_phases:
  - Phase 8 (RTSP-02, baseline conf 0.92 latency 3.16s)
  - Phase 10 (LAN RTSP auto-detect, may interfere with demo script)
  - FIRE-ADV-01 (BACKLOG, YOLO26 fire 모델 교체 in progress)
---

# Debug Session: fire-only-grey-light

## Trigger

사용자가 `.\scripts\demo_rtsp_real_camera.ps1 -FireOnly -CameraIds 1 -NumCycles 5` 실행 후
Android 실시간 화면의 YOLO 탐지 상태 태그가 계속 회색 으로 표시됨.

## Symptoms (gathered)

| Field | Value |
|-------|-------|
| Expected behavior | Fire detection cycle 실행 + capture 성공 시 last_frame_at 갱신 + 태그 회색→초록 'YOLO' |
| Actual behavior | 태그가 계속 회색 'YOLO 정지' = last_frame_at null 또는 > 90초 |
| Error messages | 미수집 (사용자가 PowerShell 출력 / Supabase log / Android logcat 모두 미공유) |
| Timeline | FIRE-ADV-01 (YOLO26 fire 모델 교체) commit 69880d3 + 137af42 (YOLO state 라벨 신규) 이후 |
| Reproduction | demo_rtsp_real_camera.ps1 -FireOnly -CameraIds 1 -NumCycles 5 |
| Where 회색불 | RealTimeScreen.kt:953-1013 YoloStateBadge — isRtsp + (nowMs - lastFrameAt) ≤ 90s 면 초록, 그 외 회색 |

## Architecture confirmed (Phase 1 evidence)

**Badge truth source:** cameras.last_frame_at column.
- Android: RetrofitClient -> /get_cctv_list -> cameras Edge Function 'list' action -> `select("*")` 로 last_frame_at 포함 반환
- 30초 polling (RealTimeActivity:80~119)
- Client-side decision: `nowMs - lastMs in 0..90_000L` (RealTimeScreen.kt:967)

**Update path:** scheduler 의 4 진입점 (run_cycle / run_fall_cycle / run_detection_cycle / run_fusion) 의
각 capture() 성공 직후 `bridge.update_camera_health(camera_id)` 호출
(scheduler.py:305, 377, 488, 632).

**update_camera_health 실패 = 회색.** 호출 안 됨 / capture 실패 / DB 업데이트 실패 모두 회색 유발.

## Suspect Areas

1. ~~**scheduler.py 의 -FireOnly 옵션 인식 실패**~~ — Eliminated (evidence below)
2. **YOLO26 fire 모델 로드 실패** — `D:\2025_산업안전\산업안전\모델 7종\화재 탐지\yolov26_fire_best.pt` 파일 존재 + ultralytics YOLO 로 load 가능 여부. detector_configs.py framework='yolov8' + classes={'fire','smoke','other'} mapping 검증. eager load 단계에서 exception 발생 시 demo 가 0 detector 로 진행 → 0 capture → 0 health update → grey.
3. **Drift X3 RTSP 도달 실패** — `rtsp://192.168.0.13/live` 가 같은 LAN 에 있는지. 변경: 카메라 또는 PC WiFi 환경 변동 가능.
4. **cameras.health_state 'unknown' 유지** — 회색 source 는 last_frame_at 이지 health_state 아님. 우선순위 낮춤.
5. **Phase 10 SmartSafetyAiAgent service 가 demo 와 경합** — 가능성 1순위로 격상. 서비스가 Running 상태면 `.env` 의 4종 detector 로드 + 15초 마다 RTSP 자동감지 + 카메라 reachable 전이 시 자동 detection. demo 와 락 충돌 (_detection_cycle_lock) + load_dotenv 와 무관한 4 detector 자동 활성 (이전 세션의 "person 검출" 미스터리 설명).
6. **Android polling timing** — 30초 polling + 90초 임계 = grey to green 전이 최대 ~30~120초 지연. 사용자가 demo 5 cycle (~30~50초) 종료 직후 확인했다면 polling tick 못 받았을 가능성. priority 낮음 (사용자가 "계속 회색" 보고했으므로 단순 timing 아님).
7. **Server-phone 시간 차** — last_frame_at 은 UTC ISO (datetime.now(timezone.utc).isoformat() — supabase_client.py:178). 폰 시계가 server 보다 90초 이상 늦으면 (nowMs - lastMs) 가 음수 → out of 0..90_000 → grey. 가능성 매우 낮음 (다른 시각 표시 화면들도 동일 timestamp 사용 중) — 증거 도착 후에만 검토.

## Current Focus

```yaml
status: awaiting_evidence
hypothesis: |
  최우선: Suspect 5 (SmartSafetyAiAgent service 가 RUNNING 상태로 demo subprocess
  와 경합/lock 충돌) OR Suspect 2 (YOLO26 모델 eager load 실패로 demo 가 0 detector
  로 진입 — capture 0회 → last_frame_at 영원히 stale).
  Suspect 3 (RTSP unreachable) 도 같은 evidence batch 로 동시 검증 가능.
test: |
  사용자에게 evidence 5종 요청 — (1) service status (2) ai_agent.log tail
  (3) service stderr.log (4) cameras row REST 조회 (5) demo 실행 console capture.
  이 5개로 Suspect 2/3/5/6 모두 discriminate 가능.
expecting: |
  service status + ai_agent.log 의 "detectors=" 라인 + capture/eager-load error 유무
  + cameras.last_frame_at 의 실제 timestamp + demo console 의 cycle 출력.
next_action: EVIDENCE NEEDED — 위 5 commands 사용자에게 요청 후 응답 대기.
reasoning_checkpoint:
  hypothesis: "주요 후보 2개: (A) NSSM service overlap (B) YOLO26 모델 load 실패. 실제 capture 가 한 번도 안 일어났다는 직접 증거 필요."
  confirming_evidence:
    - 사용자 trigger 메시지 명시 '회색불 계속' (= last_frame_at 계속 stale)
    - update_camera_health 가 capture 성공 직후에만 호출되는 코드 경로 (scheduler.py)
    - 이전 세션의 'person 검출' 미스터리 = service overlap 으로 자연 설명됨
  falsification_test: |
    if (1) service Running + (2) ai_agent.log 의 [DETECT] 라인 존재 + (3) cameras.last_frame_at 가 최근 = service 가 제대로 도는 중, 회색은 단순 polling timing → Android Suspect 6 으로 전환
    if (1) service Stopped + (2) ai_agent.log 에 'eager load 실패' = Suspect 2 확정
    if 둘 다 정상인데 cameras.last_frame_at stale = Suspect 3 (RTSP unreachable) 또는 service 가 demo 와 다른 카메라만 처리 중
  fix_rationale: 증거 도착 후 결정. 현재는 가설.
  blind_spots:
    - SmartSafetyAiAgent service 의 실제 status (사용자 시스템 상태)
    - ai_agent.log 최근 출력
    - cameras row 의 실제 last_frame_at 값
tdd_checkpoint: null
```

## Evidence

- timestamp: 2026-05-26
  checked: scripts/demo_rtsp_real_camera.ps1 (lines 64-129) + ai_agent/config.py (load_dotenv)
  found: |
    -FireOnly switch (line 64) 가 line 68-71 에서 $DetectorsEnabled='fire' + $EnableFall=$false
    설정. Step 0 (line 100-104) 가 .env 를 process env 에 먼저 set. Step 0.5 직전 line 116-118
    이 if($DetectorsEnabled) $env:DETECTORS_ENABLED=$DetectorsEnabled 로 'fire' override.
    Python spawn 시점에 process env DETECTORS_ENABLED='fire' 확정. config.py load_dotenv(override=False)
    가 .env 의 fire,helmet,forklift,person 으로 덮어쓰지 않음.
  implication: Suspect 1 (env override 실패) 는 코드 path 만으로 eliminated.

- timestamp: 2026-05-26
  checked: ai_agent/main.py _load_general_detectors (lines 93-121) + DETECTOR_CONFIGS['fire']
  found: |
    detector eager load 가 try/except 로 감싸여 있지만 (line 117-120), FileNotFoundError 와
    Exception 모두 log.error 만 찍고 detectors dict 에서 그 detector 만 제외함. 'fire' 단일
    detector 모드에서 fire load 실패 시 detectors={} 가 되고, line 209-211 의 'if not detectors:
    log.error("로드된 detector 가 없어 --once-detect 종료"); return 1' 로 exit 1. 즉 demo 가
    early exit → capture 0회 → update_camera_health 0회 → last_frame_at 영원히 stale.
  implication: Suspect 2 확인을 위해 ai_agent.log 의 'eager load 실패' line + 'main return 1' 확인 필요.

- timestamp: 2026-05-26
  checked: ai_agent/scheduler.py run_detection_cycle (lines 743-755) + _detection_cycle_lock
  found: |
    _detection_cycle_lock 은 module-level threading.Lock — 같은 프로세스 내 중복 실행만 막음.
    별도 프로세스 (예: NSSM service + 사용자 demo subprocess) 는 락 공유 안 함. 따라서
    "service 와 demo 가 lock 으로 충돌" 가설은 잘못. 그러나 두 프로세스가 같은 RTSP URL 을
    동시에 cv2.VideoCapture 로 열 때 카메라(Drift X3) 가 한 connection 만 허용하면 한쪽이
    READ 실패 → SnapshotError → update_camera_health 호출 안 됨. service 가 먼저 도는 중이면
    demo 의 capture 가 fail 할 수도. 반대도 가능.
  implication: Suspect 5 변형 — lock 충돌이 아니라 RTSP 동시 접속 충돌. service status 확인이 결정적.

- timestamp: 2026-05-26
  checked: app/src/.../RealTimeScreen.kt:953-1013 YoloStateBadge + RealtimeActivity.kt:80-119
  found: |
    Badge 의 색상 결정 = `lastMs != null && (nowMs - lastMs) in 0..90_000L`. 30 초 마다
    /get_cctv_list polling. cameras Edge Function 의 select("*") 가 last_frame_at 포함 반환
    (cameras/index.ts:62). LiveCardItem.isRtsp 가 false 면 badge 자체 표시 X (line 832/907).
    'YOLO 정지' (회색) 표시 = isRtsp=true AND (lastFrameAt null OR > 90초 stale).
    isRtsp 가 true 인 시점 = cameras.live_url_detail 이 rtsp:// 로 시작하는 동안 (= demo
    가 PATCH 한 직후 ~ Step 6 restore 까지의 짧은 윈도).
  implication: 사용자가 회색 보고 = demo 실행 중 (RTSP PATCH 살아있는 동안) 의 그 짧은 윈도.
    Step 6 restore 후엔 isRtsp=false 라 badge 자체 표시 X. 즉 사용자가 정확히 demo 실행
    중에 본 것. last_frame_at 이 그 동안 갱신 안 됨 = capture 실패 OR demo 가 early exit.

## Eliminated

- hypothesis: scheduler 의 -FireOnly env override 실패
  evidence: |
    demo_rtsp_real_camera.ps1 lines 100-104 이 .env 를 먼저 process env 에 set 한 뒤
    lines 116-118 이 if($DetectorsEnabled) $env:DETECTORS_ENABLED=$DetectorsEnabled 로
    'fire' override. config.py line 17 load_dotenv(override=False) 가 process env 보존.
    PowerShell + Python env propagation 정상. (이전 세션의 'person 검출' 미스터리는
    별도 분리 — NSSM service overlap 가설로 자연 설명 가능, Suspect 5 참조.)
  timestamp: 2026-05-26

## Resolution

(pending — Phase 2 evidence 도착 대기)
