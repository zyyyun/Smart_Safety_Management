# Phase 8: Drift X3 RTSP 실시간 카메라 - Context

**Gathered:** 2026-05-15
**Status:** Ready for planning
**Deadline:** 2026-05-20 (수요일, D-5)

<domain>
## Phase Boundary

`ai_agent/scheduler.py` 가 mp4 파일 대신 RTSP 스트림을 직접 frame 추출하여 YOLO 추론 →
`detection_events` insert (기존 경로 재사용). `cameras.live_url_detail` 의 `rtsp://...`
URL 자동 인식 + mp4 fallback 양립. RTSP 끊김 시 backoff 재연결 + `cameras.last_frame_at`
헬스체크 + 5분 무수신 시 FCM 관리자 알림.

**가벼운 통합 방향**: scheduler 가 이미 RTSP-aware (`rtsp_url = camera["live_url_detail"]`),
snapshot.capture() 가 ffmpeg subprocess 라 RTSP/mp4 양쪽 자동 처리 가능 — 변경 surface
는 (1) ffmpeg RTSP 플래그 추가 + (2) snapshot.capture 내부 backoff + (3) 헬스체크
마이그레이션 + cron job + (4) mediamtx 합성 검증 스크립트. 4 detector 진입점 (line 72/
128/228/328) 무변경.

**Out of scope** (다른 phase / future):
- **Drift X3 실기기 검증 (RTSP-02)** — 사용자 환경 부재 → mediamtx 합성 RTSP 로
  RTSP-01·03 검증 + RTSP-02 실기기는 **deferred** (Phase 7 04 패턴). 6월 검단·포천
  설치 직전 LP-3 단계에서 본격 (v1.1).
- 다중 카메라 동시 RTSP 부하 테스트 → v1.x
- GPU 가속 / hardware-encoded H.264 직접 입력 → v1.x (현재 ffmpeg + CPU 추론)
- RTSP 의 RTCP/SDP 메타데이터 처리, ONVIF discovery → v1.x
- Adaptive bitrate / multi-resolution stream 선택 → v1.x

</domain>

<spec_lock>
## Locked Requirements (from ROADMAP.md)

3 requirements (RTSP-01·02·03), 4 Success Criteria. RTSP-02 의 "Drift X3 1대 실측" 만
deferred 표기, 나머지는 합성 검증으로 충족.

- **RTSP-01**: ai_agent 가 cameras.live_url_detail 의 rtsp:// URL 을 cv2/ffmpeg 으로
  frame 추출 → detector → detection_events. mp4 fallback 유지, URL scheme 분기.
- **RTSP-02**: Drift X3 ≥ 1대 실기기 + 1 detection cycle + ≤10s 지연 → **deferred** (실기기 부재).
  v1.0 합성 충족: mediamtx + sample mp4 → RTSP publish → 1 detection cycle + detection_events row.
- **RTSP-03**: 끊김 시 재연결 backoff (최대 3회) + cameras.last_frame_at 컬럼 + N분
  무수신 시 운영 알림 (FCM 또는 로그) + 헬스체크 SQL.

**SC #4 (cross-cutting)**: mp4 demo + RTSP 운영 동일 detector 코드 — 분기점이
VideoCapture 객체 생성 1줄에 한정. ✓ 본 phase 의 D-01 (ffmpeg subprocess 단일 path) 가 자동 충족.

</spec_lock>

<decisions>
## Implementation Decisions

### Frame 추출 방식 (RTSP-01)
- **D-01 (amended 2026-05-15 — drift_test.py 검증 적용): cv2.VideoCapture + CAP_FFMPEG (RTSP) / 기존 ffmpeg subprocess (mp4 fallback)**
  - **사용자 검증 레퍼런스**: `D:\2025_산업안전\산업안전\Dirft 카메라\DriftX3\drift_test.py`
    가 실제 동작하는 RTSP 카메라 연결 코드. 핵심:
    ```python
    cap = cv2.VideoCapture(rtsp_url, cv2.CAP_FFMPEG)
    time.sleep(2)
    ret, frame = cap.read()
    if ret and cap.isOpened() and frame is not None: ...
    ```
  - **호환성 검증 완료**: ai_agent env 의 `opencv-python 4.12.0` build 가
    `FFMPEG: YES (prebuilt binaries)` + `CAP_FFMPEG = 1900` 가용 확인 — drift_x3
    conda env (Python 3.9 + opencv-python 4.13) 와 별도 분리 불필요. **단일 ai_agent
    env (Python 3.10/3.11) 에서 그대로 동작**.
  - **신규 함수**: `ai_agent/snapshot.py` 에 `capture_rtsp(url, tmp_path, max_attempts=3)` 추가.
    ```python
    def capture_rtsp(url, tmp_path, max_attempts=3):
        for attempt in range(max_attempts):
            cap = cv2.VideoCapture(url, cv2.CAP_FFMPEG)
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)  # latest frame only
            time.sleep(2)
            ret, frame = cap.read()
            if ret and cap.isOpened() and frame is not None:
                cv2.imwrite(str(tmp_path), frame)
                cap.release()
                return tmp_path
            cap.release()
            time.sleep(BACKOFF_DELAYS[attempt])  # D-02 backoff
        raise SnapshotError(f"RTSP {url} failed after {max_attempts} attempts")
    ```
  - **URL scheme 분기**: `snapshot.capture()` 진입점 wrapper —
    `if url.startswith("rtsp://"): return capture_rtsp(url, tmp_path)
     else: return capture_ffmpeg(url, tmp_path, ffmpeg_bin=...)`
  - **mp4 fallback 유지**: 기존 ffmpeg subprocess (snapshot.capture 의 현재 구현)
    가 mp4/file:// 처리. 1줄 분기로 SC #4 자동 충족.
  - **buffer size = 1**: drift_test.py 에는 명시 X 지만 long-lived 가 아닌
    1-shot capture 라 stale frame 방지 위해 추가 — 가장 최근 frame 만 사용.
  - **GUI 코드 제거**: drift_test.py 의 `cv2.imshow` / `cv2.waitKey` /
    `cv2.destroyAllWindows` 는 headless ai_agent 운영에 불필요. 제거.
  - **scheduler 변경 X**: `from snapshot import capture` 호출 그대로. capture()
    내부에서 URL scheme 자동 분기.
  - **SC #4 충족**: 분기점이 snapshot.py 내부 1줄 (URL scheme if/else) — scheduler
    의 4 detector 코드 무변경.
  - **근거**:
    - 검증된 코드 (사용자 환경에서 실제 RTSP 카메라 연결 성공 사례)
    - opencv-python prebuilt 의 FFMPEG backend 가 시스템 ffmpeg 보다 호환성 ↑
    - drift_test.py 의 `time.sleep(2)` + `cap.read()` 검증 패턴 그대로 reuse
    - subprocess 대비 process spawn overhead 없음 (cv2 in-process)

### 재연결 backoff (RTSP-03)
- **D-02 (amended 2026-05-15 — drift_test.py 패턴 적용): capture_rtsp 내부 + drift_test 의 3회 + 2초 sleep 패턴**
  - **drift_test.py 검증 패턴**: `try_connect(max_attempts=3)` 함수가 각 시도
    사이에 `time.sleep(2)` 으로 단순 고정 wait. RTSP 카메라 연결의 실제 동작
    검증된 timing.
  - **변경**: 원안의 1s→3s→9s exponential 대신 **drift_test.py 의 2s 고정 3회**
    적용 (총 6초 + 시도당 2초 wait = ~12초). exponential 대비 단순.
    `BACKOFF_DELAYS = [2, 2, 2]` (또는 그냥 `for ...: sleep(2); ...`).
  - **연결 시도 내부 추가 wait**: drift_test.py 가 `cv2.VideoCapture()` 직후
    `time.sleep(2)` 후 read 시도 — RTSP handshake 완료 대기. 이 패턴도 보존.
  - **위치**: `capture_rtsp()` 내부 (D-01). mp4 fallback (기존 ffmpeg subprocess)
    은 backoff 없음 (파일 read 는 transient failure 적음).
  - **포기 시**: `SnapshotError` raise → scheduler 의 기존 except 패턴 (line 84
    `[SNAPSHOT_ERR]` 로그) 자동 catch → 1분 cycle skip + 다음 cycle 자동 재시도.
    스케줄러는 변경 없음.
  - **알람 방지**: 3회 모두 실패 시점에 cameras.last_frame_at 갱신 X → 헬스체크
    (D-03) 가 5분 임계 도달 시점에 FCM 발사. 단일 blip 자동 흡수.
  - **테스트**: `ai_agent/tests/test_snapshot_rtsp.py` 신규 — mocked
    cv2.VideoCapture (3회 fail → SnapshotError / 2회 fail + 3회 success → 정상
    frame 반환 / instant success → wait 1회 + return / cap.read() ret=False →
    재시도). drift_test.py 의 cap.isOpened() + frame is not None 가드 검증.
  - **근거**: 검증된 코드 우선 (드리프트 X3 카메라 실제 검증된 timing). ROADMAP
    SC #3 의 "최대 3회" 충족. 단순 고정 sleep 이 디버깅 + tracing 쉬움.

### 헬스체크 + 알림 (RTSP-03)
- **D-03: cameras.last_frame_at + pg_cron 1분 주기 healthcheck + FCM 관리자 알림 + 5분 임계**
  - **마이그레이션**: `supabase/migrations/012_cameras_health.sql` 신규
    ```sql
    -- 1. cameras 컬럼 추가
    ALTER TABLE cameras ADD COLUMN IF NOT EXISTS last_frame_at TIMESTAMPTZ;
    ALTER TABLE cameras ADD COLUMN IF NOT EXISTS health_state TEXT
      DEFAULT 'unknown' CHECK (health_state IN ('ok','degraded','down','unknown'));
    ALTER TABLE cameras ADD COLUMN IF NOT EXISTS last_alert_at TIMESTAMPTZ;

    -- 2. pg_cron 1분 주기 healthcheck job
    SELECT cron.schedule(
      'cameras_healthcheck',
      '* * * * *',  -- 매 분
      $$ SELECT public.cameras_healthcheck(); $$
    );

    -- 3. healthcheck 함수 (FCM Edge Function 호출)
    CREATE OR REPLACE FUNCTION public.cameras_healthcheck() RETURNS void
    LANGUAGE plpgsql AS $$
    DECLARE r RECORD;
    BEGIN
      FOR r IN
        SELECT camera_id, group_id, last_frame_at, health_state, last_alert_at
        FROM cameras
        WHERE last_frame_at < now() - interval '5 minutes'
          AND health_state != 'down'
          AND (last_alert_at IS NULL OR last_alert_at < now() - interval '30 minutes')
      LOOP
        -- 상태 전이 (D-09 알림 전이 원칙)
        UPDATE cameras SET health_state='down', last_alert_at=now()
        WHERE camera_id = r.camera_id;
        -- FCM call (HTTP via pg_net 또는 Edge Function)
        PERFORM net.http_post(
          url := 'https://xbjqxnvemcqubjfflain.supabase.co/functions/v1/notifications',
          headers := jsonb_build_object('Authorization','Bearer '||current_setting('app.service_role_key'),
                                         'Content-Type','application/json'),
          body := jsonb_build_object('action','camera-down','camera_id',r.camera_id,
                                       'group_id',r.group_id,'last_frame_at',r.last_frame_at)
        );
      END LOOP;

      -- 회복 전이 (down → ok, 종료 알림)
      FOR r IN
        SELECT camera_id, group_id FROM cameras
        WHERE last_frame_at >= now() - interval '5 minutes' AND health_state = 'down'
      LOOP
        UPDATE cameras SET health_state='ok', last_alert_at=now() WHERE camera_id = r.camera_id;
        PERFORM net.http_post(...);  -- recovery FCM (Phase 4 D-09 종료 알림 패턴)
      END LOOP;
    END;
    $$;
    ```
  - **알림 채널**: FCM only — `_shared/fcm.ts` 의 `sendPushToUser` 재사용. Phase 4 D-11 +
    Phase 7 D-03 의 일관성. 대상 = manager 권한 사용자 (cameras.group_id → profiles
    where user_role='manager' AND group_id=...).
  - **알림 전이 원칙 (Phase 4 D-09)**: 같은 카메라 ok→down 전이 시점에만 1회 발사.
    down 지속 중 반복 X. down→ok 회복 시 종료 알림 1회. last_alert_at 으로 30분
    cooldown (재발 시 재발사).
  - **신규 Edge Function action**: `notifications/index.ts` 에 `case 'camera-down'`
    + `case 'camera-recovered'` 추가 (Phase 4·7 패턴 동일). payload 검증 +
    sendPushToUser 호출 + ANDROID_CHANNEL_ID="camera_alerts".
  - **last_frame_at 갱신**: snapshot.capture 성공 시점에 scheduler 가
    `UPDATE cameras SET last_frame_at = now() WHERE camera_id = $1` (PostgREST PATCH
    또는 supabase_client.bridge.update_camera_health()). 4 detector 진입점 모두 동일
    호출 — supabase_client.py 에 헬퍼 추가.

### 합성 검증 전략 (RTSP-01·03 통합 검증)
- **D-04: mediamtx + reference-videos mp4 → 합성 RTSP publish + cameras 등록 + scheduler cycle**
  - **mediamtx 활용**: 사용자 PC 에 이미 설치됨 (`/c/Users/ANNA/Desktop/mediamtx/bin`).
  - **신규 스크립트**: `scripts/start_rtsp_mock.sh` (또는 .ps1)
    - mediamtx config (yml) 작성 — `paths: { fire: { source: publisher } }` 등 5종
    - 각 reference-videos mp4 파일을 ffmpeg 으로 publish:
      ```
      ffmpeg -re -stream_loop -1 -i fire/source_v2.mp4 -c copy -f rtsp rtsp://localhost:8554/fire
      ```
    - 5종 (fire/helmet/forklift/person/fall) 동시 백그라운드 publish (또는 1종씩
      순환 — 자원 부담 따라).
  - **cameras row 갱신** (검증 단계만 임시):
    ```sql
    UPDATE cameras SET live_url_detail='rtsp://localhost:8554/fire' WHERE camera_id=1;
    UPDATE cameras SET live_url_detail='rtsp://localhost:8554/helmet' WHERE camera_id=5;
    -- ... 등 5종 매핑
    ```
    검증 후 원래 mp4 URL 로 복원 (또는 별도 시연 카메라 row 추가).
  - **검증 시나리오**:
    - **RTSP-01 검증**: `python -m ai_agent.scheduler --once-detect` → 5종
      detection_events row 1+ 적재 확인 (fire conf 0.10 D-19 fallback 적용 / helmet
      conf 0.5+ ['head'] / forklift / person / fall 모두 동일 임계).
    - **RTSP-03 backoff 검증**: mediamtx publish 강제 kill → snapshot.capture
      1s→3s→9s 재시도 로그 확인 + SnapshotError raise → scheduler skip 확인 +
      cameras.last_frame_at 갱신 멈춤.
    - **RTSP-03 헬스체크 검증**: mediamtx 끈 채로 5분+ 대기 → pg_cron 이 5분
      임계 도달 → cameras.health_state=down 전이 + FCM 알림 trigger (사용자
      단말 또는 Edge Function log) 확인.
    - **RTSP-03 회복 검증**: mediamtx 재시작 → scheduler 다음 cycle (1분 후) 에
      capture 성공 → cameras.last_frame_at 갱신 → pg_cron 이 down→ok 전이 +
      회복 FCM 발사.
  - **deferred** (RTSP-02 실기기): Drift X3 실기기 부재 → 본 phase 는 합성 검증으로
    SC #2 의 "≤10s 지연" 측정만 부분 충족 (mediamtx 로컬이라 지연 ≈ 0). 실기기 시연
    은 v1.1 6월 설치 시점 또는 phase deferred SUMMARY 에 기재.

### Claude's Discretion
- **ffmpeg RTSP 플래그 정확 값** — `-timeout 5000000` (5초) 가 default 권장, planner
  가 mediamtx 검증 시 조정 가능 (10초까지 허용).
- **재연결 backoff 시간 조정** — 1s→3s→9s 가 default. mediamtx 검증 시 너무 길면
  500ms→1.5s→4.5s 로 단축 가능 (planner 결정).
- **pg_cron schedule** — 1분 주기 default. cameras 수가 많아져 부하 우려 시 5분
  주기로 완화 가능 (v1.x).
- **healthcheck 함수 구현** — pg_net 직접 호출 vs trigger 가 Edge Function 호출
  vs Edge Function 자체가 cron으로 폴링 — 기본 pg_net (cron 직접) 권장. pg_net
  미설치 시 Edge Function fallback.
- **last_frame_at 갱신 빈도** — 매 capture 성공 시 default. detector 별로 1분
  cycle 에 4번 update (4 detector 동시 호출) 발생 가능 → 동일 camera_id 라면
  scheduler 가 cycle 1회당 1번만 호출하도록 dedup 가능 (planner 결정).
- **mediamtx config 위치** — `scripts/mediamtx.yml` 기본, config 단순 (paths 만).
- **ffmpeg publish loop** — `-stream_loop -1` 무한 반복. 또는 `-re` (real-time
  rate) 만 — planner 가 결정.
- **시연 카메라 row** — 기존 camera_id 1·5 등 임시 변경 vs 신규 camera_id (예:
  rtsp_test_1) 추가. 후자가 깔끔. planner 결정.

</decisions>

<specifics>
## Specific Ideas

- **"가벼운 통합" 정신** — Phase 7 와 동일. 본 phase 는 RTSP 전환 + 헬스체크 추가가
  목표지 ai_agent 대규모 리팩터 X. snapshot.py 한 파일 + 마이그레이션 1개 + Edge
  Function action 1~2개 + 합성 스크립트 1개가 한계. 4 detector 진입점 무변경.
- **알림 전이 원칙 일관 적용** (Phase 4 D-09): 카메라 ok↔down 전이 시점에만 1회.
  동일 상태 지속 중 반복 X. last_alert_at + 30분 cooldown 으로 재발 시 재발사.
- **신호 = 상태 신호 원칙** (PROJECT.md Key Decision): last_frame_at 자체는 단순
  타임스탬프지만, health_state ENUM 으로 추상화 (ok/degraded/down/unknown) — 운영
  대시보드에서 raw timestamp 가 아닌 상태 라벨로 표시.
- **mp4 demo + RTSP 운영 양립 (SC #4)** — 분기점이 snapshot.py 내부의 ffmpeg args 1곳
  → 5월 PPT 데모는 mp4 그대로, 6월 검단·포천 설치 시 cameras.live_url_detail 만
  rtsp:// URL 로 갱신하면 자동 RTSP 모드. 코드 deploy X.
- **수요일 마감 우선순위** (D-5 = 5일):
  - **Day 1**: snapshot.capture RTSP 플래그 + backoff + 단위 테스트 (D-01·02)
  - **Day 2**: 012 마이그레이션 + healthcheck 함수 + Edge Function 'camera-down'/'camera-recovered' (D-03)
  - **Day 3**: scheduler last_frame_at 갱신 + supabase_client 헬퍼
  - **Day 4**: scripts/start_rtsp_mock.sh + mediamtx config + 합성 검증 (D-04)
  - **Day 5 (수요일)**: 통합 검증 + buffer + Phase 9 진행 또는 deferred 정리

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 8 직접 입력
- `.planning/REQUIREMENTS.md` §8 Drift X3 RTSP (RTSP-01·02·03) — 본 phase 의 요구
- `.planning/ROADMAP.md` Phase 8 섹션 (line 248-263) — Goal · 4 Success Criteria · Depends on
- `.planning/phases/01-vision-demo-videos/01-CONTEXT.md` — Phase 1 의 D-19 fallback (fire conf 0.10), MODEL-03 임계, cameras.live_url_detail 사용 패턴
- `.planning/phases/02-vision-frames-required/02-CONTEXT.md` — frames_required 룰 (RTSP frame 도 동일)
- `.planning/phases/03-vision-bbox-fusion/03-CONTEXT.md` — fusion 적용 (RTSP frame 도 동일 fusion path)
- `.planning/phases/04-watch-j2208a-pipeline/04-CONTEXT.md` — D-09 알림 전이 원칙 + D-11 FCM only + D-12 Edge Function action-routing 패턴
- `.planning/phases/07-watch-app-bridge/07-CONTEXT.md` — D-04b notifications/index.ts case 패턴 + D-04b RLS USING true v1.0

### 기존 자산 / 재사용
- `ai_agent/scheduler.py` — 4 detector 진입점 (line 72/128/228/328) 의 `rtsp_url =
  camera["live_url_detail"]` + `capture(rtsp_url, ...)` 패턴. 본 phase 는 변경 X.
- `ai_agent/snapshot.py` — `capture(url, tmp_path, ffmpeg_bin)` + `SnapshotError` 정의.
  본 phase D-01·02 의 핵심 변경 surface.
- `ai_agent/supabase_client.py` — SupabaseBridge 클래스. 본 phase D-03 의
  `update_camera_health(camera_id, last_frame_at)` 헬퍼 추가.
- `supabase/functions/notifications/index.ts` — action-routing switch (Phase 4·7
  watch-alert/watch-ack/watch-pair 패턴). 본 phase D-03 가 case 'camera-down' +
  'camera-recovered' 추가.
- `supabase/functions/_shared/fcm.ts` — sendPushToUser (Phase 4 D-11 + Phase 7 D-03 동일).
- `supabase/migrations/002_tables.sql` (line 57-77) — cameras 테이블 정의.
  본 phase D-03 가 ALTER 로 last_frame_at·health_state·last_alert_at 추가.
- `supabase/migrations/001_extensions.sql` — pg_cron 활성화 확인 (Phase 4 010 에서
  검증됨, 본 phase 도 cron.schedule 사용).

### 외부 도구
- **mediamtx** (`/c/Users/ANNA/Desktop/mediamtx/bin`) — RTSP 미디어 서버. 본 phase
  D-04 합성 검증의 핵심. config 파일 + ffmpeg publish 명령으로 mp4 → RTSP 합성.
- **ffmpeg** (`settings.ffmpeg_bin` 경로 — Phase 1 부터 사용) — mp4 fallback frame
  추출 (snapshot.capture_ffmpeg) + mp4 → RTSP publish (mediamtx 시연용).
- **opencv-python 4.12+** (FFMPEG=YES prebuilt) — RTSP frame 추출 (snapshot.capture_rtsp,
  D-01). cv2.VideoCapture(url, cv2.CAP_FFMPEG). drift_test.py 검증 패턴.

### 사용자 검증 레퍼런스 (D-01·02 의 핵심 출처)
- **`D:\2025_산업안전\산업안전\Dirft 카메라\DriftX3\drift_test.py`** — 사용자가 실제
  Drift X3 카메라 (`rtsp://192.168.0.13/live`) 와 검증한 단순 RTSP 연결 코드. 핵심
  패턴: `cv2.VideoCapture(url, cv2.CAP_FFMPEG)` + 2초 sleep + cap.isOpened() +
  frame is not None + 3회 retry + 시도 사이 2초 wait. 본 phase 의 D-01·02 가 이
  코드의 검증된 timing/sequence 를 그대로 채택 (GUI 코드 cv2.imshow 는 headless
  운영 위해 제거). conda env `drift_x3` (Python 3.9 + opencv-python 4.13) 에서 동작
  검증되었으나, ai_agent env (Python 3.10/11 + opencv-python 4.12 prebuilt
  FFMPEG=YES) 호환성 확인 완료 — 별도 env 분리 불필요.

### testuser1 시드 + 기존 cameras
- testuser1 (`profiles.user_id`) — manager 권한 (DB 확인됨), camera_id 1·5 (fire·helmet)
  의 group_id=1 owner. 본 phase 의 manager FCM 알림 대상.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ai_agent/scheduler.py:36`** `from snapshot import SnapshotError, capture` — 본
  phase D-01·02 가 동일 함수에 ffmpeg 플래그 + backoff 추가. 호출자 변경 X.
- **`ai_agent/scheduler.py:72,128,228,328`** 4 detector 진입점의 `capture(rtsp_url,
  tmp_path, ffmpeg_bin=settings.ffmpeg_bin)` 패턴 — 본 phase 무변경. snapshot.py
  내부에서 RTSP/mp4 분기.
- **`ai_agent/snapshot.py:24`** `def capture(url, tmp_path, ffmpeg_bin)` — 본 phase
  D-01·02 의 핵심 변경 위치. ffmpeg subprocess args 리스트 구성 + retry loop 추가.
- **`supabase/migrations/002_tables.sql:57-77`** cameras 테이블 — `live_url_detail
  TEXT` 이미 존재 (mp4 또는 RTSP URL 양쪽 가능). 본 phase 는 ALTER 로
  last_frame_at·health_state·last_alert_at 추가만.
- **`supabase/migrations/001_extensions.sql`** pg_cron + pg_net 가용 가정. Phase 4
  010 에서 cron.schedule 검증됨 (j2208a TTL cleanup). 본 phase 동일 패턴.
- **`supabase/functions/notifications/index.ts`** action-routing switch — Phase 4 03 +
  Phase 7 02 에서 watch-alert/watch-ack/watch-pair 추가됨. 본 phase 는 동일 위치에
  case 'camera-down' + 'camera-recovered' 추가 (deploy 1회).
- **`supabase/functions/_shared/fcm.ts`** sendPushToUser — manager 권한 사용자에게
  push. ANDROID_CHANNEL_ID 분리 ("camera_alerts") 로 트레이 구분.

### Established Patterns
- **마이그레이션 번호 컨벤션** — 001~011 까지 사용 (Phase 4 010, Phase 7 011).
  본 phase = `012_cameras_health.sql`.
- **action-routing 추가** — `case 'foo': handleFoo(req); break;` (default err 분기 위).
  Phase 4·7 동일 패턴, 본 phase D-03.
- **SnapshotError 처리** — scheduler 가 이미 except 처리 중 (line 84·86 의
  `except SnapshotError as e: return f"[SNAPSHOT_ERR] ..."` 패턴) → 본 phase 가
  추가 작업 X. backoff 3회 모두 실패 시 자연스럽게 cycle skip.
- **Edge Function deploy gate** — `supabase functions deploy notifications` 1회 +
  curl smoke (Phase 4·7 동일).

### Integration Points
- **scheduler → snapshot.capture (RTSP/mp4)** — 분기점이 snapshot.py 내부 ffmpeg
  args 1곳. 호출자 변경 X. SC #4 자동 충족.
- **scheduler → supabase_client.update_camera_health** — 매 capture 성공 후 1회
  PostgREST PATCH (cameras.last_frame_at = now()). 신규 헬퍼.
- **pg_cron → notifications Edge Function** — pg_net.http_post 으로 직접 호출.
  Edge Function 이 sendPushToUser 발사. Phase 4 D-12 의 BLE 클라이언트 → Edge
  Function 패턴과 일관.
- **mediamtx + ffmpeg publish (시연 용)** — 별도 프로세스 (백그라운드). cameras
  row 만 rtsp:// URL 로 변경하면 scheduler 가 자동 인식.

### 잠재 함정
- **ffmpeg RTSP timeout 단위** — `-timeout` 은 microseconds (5000000 = 5초). 잘못
  쓰면 5ms 또는 5000초가 될 수 있음. planner 가 ffmpeg docs 재확인.
- **pg_net 미설치 가능성** — `001_extensions.sql` 에 pg_net 명시 안 됐을 수도.
  cameras_healthcheck() 가 pg_net 의존이라면 12 마이그레이션이 `CREATE EXTENSION
  IF NOT EXISTS pg_net WITH SCHEMA extensions;` 먼저 추가.
- **service_role key 노출** — pg_cron 함수 내부에서 service_role JWT 가 SQL 함수
  에 박혀버림. `current_setting('app.service_role_key')` 패턴으로 외부 설정 권장
  (Supabase secrets 또는 cron 함수 인자).
- **RTSP UDP vs TCP** — `-rtsp_transport tcp` 명시 안 하면 ffmpeg 기본 UDP →
  방화벽/패킷 손실 시 끊김 잦음. TCP 강제 권장 (저지연 요구 없음).
- **mediamtx kill 시점 타이밍** — backoff 검증 시 mediamtx 가 graceful shutdown
  하면 RTSP teardown signal 보내서 즉시 EOF → ffmpeg 가 retry X. SIGKILL 또는
  ffmpeg publish 만 kill (mediamtx 살려둠) 권장.
- **fire conf 0.10 D-19 fallback** (Phase 1) — RTSP frame 도 동일 약한 가중치
  검출. RTSP 의 noise/압축 아티팩트로 false positive 증가 가능 — 단 frames_required=5
  (Phase 2) 가 흡수.
- **last_frame_at 동시 UPDATE race** — 4 detector 가 동시에 cycle 돌면 같은 row
  4번 UPDATE. PostgREST 는 idempotent 라 OK 지만 부하 → cycle 단위 dedup 권장.

</code_context>

<deferred>
## Deferred Ideas

- **Drift X3 실기기 검증 (RTSP-02 실측)** — 6월 검단·포천 설치 직전 LP-3 단계 (v1.1).
  본 phase 는 mediamtx 합성 검증으로 SC #1·#3 + SC #2 부분 충족, SC #2 의 "≤10s 실측"
  은 deferred 표기.
- **다중 카메라 동시 RTSP 부하 테스트** — 5종 모두 동시 RTSP, GPU 사용률, ffmpeg
  process 수 등 → v1.x
- **GPU 가속 / CUDA 인코딩 / hardware-encoded H.264 직접 입력** → v1.x
- **RTSP 의 RTCP/SDP 메타데이터 처리, ONVIF discovery** → v1.x
- **Adaptive bitrate / multi-resolution stream 선택** → v1.x
- **LP-3 RTSP 실카메라 6월 검단·포천 설치 직전** — v1.1 별도 마일스톤
- **운영 대시보드 (cameras.health_state grid + 시계열)** → Phase 6 DEMO 또는 v1.x
- **카메라 다중 채널 fan-out (1 RTSP → N detector)** — 현재는 detector 별 capture
  4번 호출 (read 4번). 향후 1번 capture → N detector 분배 → 부하 감소. v1.x
- **mediamtx 영구 운영** — 현재는 검증용 임시 실행. v1.x 운영 시 systemd
  service 또는 docker-compose.
- **LP-5 룰 seed DB / risk_level 매핑** — v1.1
- **운영자 SQL UPDATE 로 health_state 임계 보정** — v1.1

</deferred>

---

*Phase: 08-rtsp-camera*
*Context gathered: 2026-05-15*
