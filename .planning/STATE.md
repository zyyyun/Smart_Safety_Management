---
milestone: v1.0
name: "5월 PPT 데모 + 수요일 추가 (워치-앱·RTSP·TBM)"
status: in_progress
progress:
  phases_total: 9
  phases_done: 4
  phases_in_progress: 2
  requirements_total: 28
  requirements_validated: 21
phases_planned: 1
last_activity: "2026-05-18 — Phase 9 CONTEXT 작성 완료 (`--auto` 모드, 사용자 directive 'no clarifying questions') — 09-CONTEXT.md + 09-DISCUSSION-LOG.md 작성. 9 gray area 모두 autonomous 결정 (Schema/Lifecycle/Check-in method/Missed-attendance target/Missed-alert threshold/Manager dashboard/Worker guide screen/Edge Function actions/FCM payload). D-01 013_tbm_schema.sql 4 신규 테이블 (tbm_sessions UNIQUE group_id+date / tbm_templates JSONB checklist 5종 시드 / tbm_checklists session-snapshot / tbm_participants method='signature') + RLS + Realtime publication 4 ADD + pg_cron 'tbm_missed_attendance_minute'. D-03 수기 서명 (Compose Canvas → PNG → Storage 신규 'tbm-signatures' 버킷 private + signed URL 60s). D-04 미참여 대상 = v1.0 한정 그룹 worker 전원 단순 정의 (출근 시스템 부재, scope creep 회피). D-05 expected_end_at + 30분 임계 + Phase 4 D-09 알림 전이 1회 발사 (missed_alert_at). D-06·D-07 ComposeView 임베드 (HomeActivity manager 카드 + HomeWorkerActivity worker 카드, Phase 7 D-02 1:1 미러). D-08 notifications/index.ts 4 case 추가 (tbm-start/checkin/end/missed, Phase 4·7·8 action-routing 패턴 일관). D-09 fcm_default_channel 재사용 (Phase 8 Option B). 다음 즉시 = /gsd-plan-phase 9.\n\n2026-05-18 — Phase 8 ✓ COMPLETE (Wave 4 / Plan 04 ✓ COMPLETE) — mediamtx 합성 RTSP E2E + backoff + recovery 검증. Task 1 (커밋 85370c5): .gitignore bin/ 추가 (advisor #3 FIRST, mediamtx 30MB 다운로드 *전*에 차단) + bin/mediamtx/mediamtx.exe v1.18.2 다운로드 (.gitignore 차단 검증, git ls-files bin/ 빈 출력) + scripts/mediamtx.yml (5 paths runOnDemand + hls/webrtc/rtmp disable T-8-04 mitigation + 정확한 reference_media 매핑 advisor #1: fire→fire_aihub_0087.mp4, helmet→helmet_h0_demo.mp4, forklift/person→helmet_h0_L2_09-08_001.mp4 재사용, fall→fall/E02_001.mp4) + scripts/start_rtsp_mock.sh + scripts/stop_rtsp_mock.sh (taskkill /F SIGKILL Pitfall 5 회피) + scripts/restore_cameras_mp4.sql. Task 2 (validation evidence, 코드 변경 0): (a) RTSP-01 합성 충족 — mediamtx :8554 listener + cameras 1·5 PATCH → rtsp://localhost:8554/{fire,helmet} + 6 cycles --once-detect 실행 → camera 1·5 last_frame_at 갱신 6회 (RTSP capture 반복 성공: 03:13:59→03:16:55 camera 1, 03:14:09→03:17:01 camera 5) + camera_id=4 forklift detection_events 6건 적재 (event_id 38·39·40·41·42·43, conf=0.68). (b) RTSP-03 backoff 검증 — stop_rtsp_mock.sh (taskkill /F) → tasklist 0건 → --once-detect 재실행 → `[DETECT_SNAPSHOT_ERR] camera_id=1 event=fire: RTSP capture failed after 3 attempts (url=rtsp://localhost:8554/fire): cap.read ret=False isOpened=False frame_is_None=True` console log + cameras 1·5 last_frame_at 갱신 X (03:16:55/03:17:01 그대로 = SnapshotError raise + update_camera_health 미호출 검증) + 측정 ~101s (OpenCV FFMPEG default 30s timeout × 3 attempts, drift_test sleep 보다 backend timeout 우선 — plan 예측 ~12s 와 차이는 backend 동작 차이). (c) 5분 healthcheck round-trip — 3분 wait 후 cameras_healthcheck() RPC 명시 호출 HTTP 204 + cameras 1·5 health_state='unknown' 유지 (DOWN 전이 X). **Vault `service_role_key` 미시드 graceful skip 확인** (012 line 105-110: IF sr_key IS NULL THEN RAISE WARNING + RETURN, RPC 204 + cameras unchanged 조합으로 자동 detect). step 10 FCM 도착만 deferred (사용자 Dashboard 시드 후 1분 cron tick 부터 자연 동작 — 08-02 SUMMARY User Setup Required 재인용). (d) RTSP-03 recovery 검증 — start_rtsp_mock.sh 재시작 → --once-detect → camera 1 PATCH 200 at 03:25:48, camera 5 PATCH 200 at 03:26:02 (last_frame_at 갱신). (e) Cleanup — stop_rtsp_mock.sh → mediamtx.exe + ffmpeg.exe tasklist 0건 (T-8-04 mitigation 검증) + PostgREST PATCH 로 cameras 1·5 원복 (fire/source_v2.mp4 + helmet/source_v2.mp4). (f) RTSP-02 deferred 표기 — 'Drift X3 실기기 부재. mediamtx 로컬 검증으로 SC #2 1 cycle detection + ≈0초 지연 부분 충족. 실기기 ≤10s 측정만 v1.1 6월 LP-3'. (g) regression ai_agent/tests/ 28/28 PASS (5.18s). Deviations 3건 (모두 Rule 3): scheduler CLI 진입점 `python main.py --once-detect` 정정 (`python -m scheduler` 부재) + Vault sr_key 미시드로 step 10 부분 deferred + Bash redirect 순서 오류 1회 (console output 직접 검증으로 우회). 커밋 85370c5 (Task 1) + 본 SUMMARY commit (Task 2 evidence). Phase 8 종결 — RTSP-01·03 완전 충족, RTSP-02 실기기 측정 + Vault sr_key 시드 부분 deferred. 다음 = Phase 9 또는 Phase 4 04-04 의사결정.\\n\\n2026-05-18 — Phase 8 Plan 03 ✓ COMPLETE — notifications case camera-down/camera-recovered + supabase functions deploy notifications (70.21kB) + 4/4 curl smoke PASS (sent:1 testuser1 실제 push 수신 검증) + SupabaseBridge.update_camera_health(camera_id) datetime.now(timezone.utc).isoformat() (RESEARCH A6 PostgREST '\"now()\"' 함정 회피) + scheduler 4 detector wiring (_process_single_camera/_process_fall_for_camera/_process_detection_for_camera/_process_fusion_for_camera 모두 capture(...) 성공 직후 bridge.update_camera_health(camera_id) 1줄). SC #4 zero-change 검증 (capture() 호출 line diff 0). D-09 회귀 가드 통과 (notifications insert 0건, push-only). Pitfall 3 회피 (Option B fcm_default_channel 재사용, channel_id 함수 코드 0). Pitfall 8 accepted (분당 ≈20× PATCH, dedup deferred v1.1). 회귀 가드 ai_agent/tests/ 28/28 PASS (7.83s). Deviations 2건: Rule 3 plan correction (sendPushToUsers helper 이미 fcm.ts:239 export 존재 — 신규 작성 불필요, advisor 정정 적용) + Rule 3 환경 (esm.sh 522 transient 1회, 5초 후 재시도 성공). 커밋 c8c7b6d (Edge Function + 4 smoke) · 00aeedf (Python wiring). Wave 4 prerequisite = service_role_key Vault 시드 (dashboard 수동, 08-04 cron round-trip 검증 전 필수, 본 plan smoke 와 무관). RTSP-01·03 backend wiring 완전, 다음 Wave 4 = 08-04 (mediamtx 합성 E2E + RTSP-02 deferred).\\n\\n2026-05-18 — Phase 8 Plan 02 ✓ COMPLETE — 012_cameras_health.sql 운영 DB 적용 완료. pg_net 활성화 (Pitfall 1) + cameras 3 컬럼 ALTER (last_frame_at + health_state ENUM + last_alert_at) + Vault vault.create_secret 시드 best-effort + EXCEPTION OTHERS 흡수 (advisor 권고) + cameras_healthcheck() SECURITY DEFINER plpgsql + SET search_path 잠금 + 5분 임계 + 30분 cooldown + Phase 4 D-09 알림 전이 1:1 미러 + pg_cron 'cameras_healthcheck_minute' 1분 주기. [BLOCKING] supabase db push --linked --yes 성공 (NOTICE: vault.secrets seeded: edge_function_base_url). supabase migration list 에 012 등장. PostgREST 검증: cameras 5 rows 모두 health_state='unknown'+last_frame_at=NULL (Pitfall 4 검증 OK), RPC cameras_healthcheck 204 (SECURITY DEFINER void 호출 OK), anon PATCH cameras body[health_state=down] → 200 [] 빈 배열 + 후속 GET 으로 보존 확인 (T-8-02 회귀 가드), testuser1 manager+group_id=1 ↔ cameras 1·5 group_id=1 정합 (A4/A8). tests/sql/test_012_cameras_health_isolation.sql 작성 (7 assertions, Dashboard SQL Editor 실행 대기). User Setup Required: service_role_key 는 dashboard Vault 수동 시드 (T-8-01 git 노출 회피, 08-03 deploy 전 1회 필수). 커밋 0131ffa·0755f04. Deviations 2건: Rule 3 환경 (psql 미설치 + SUPABASE_DB_PASSWORD/ACCESS_TOKEN 부재 → PostgREST + Management API NOTICE 조합 우회) + Rule 2 보안 보강 (SET search_path = public, extensions, net 함수 정의에 추가). 다음 Wave 3 = 08-03 (notifications case camera-down/recovered + deploy + scheduler last_frame_at wiring).\n\n2026-05-18 — Phase 8 Plan 01 ✓ COMPLETE — snapshot.capture_rtsp (cv2.VideoCapture+CAP_FFMPEG drift_test.py 검증 패턴) + URL scheme 분기 wrapper + _capture_ffmpeg internal rename (mp4 fallback 무회귀) + OPENCV_FFMPEG_CAPTURE_OPTIONS module top-level setdefault (Pitfall 6). TDD: RED 6/6 fail → GREEN 6/6 pass → 전체 ai_agent/tests/ 28/28 pass. scheduler.py zero-change (SC #4). 커밋 c3dbf41·715c277. Deviation 1건 (Rule 1 advisor 사전 적용 — success 분기에서 output_path.exists()/stat() 가드 제거 → mocked imwrite 호환).\n\n2026-05-15 — Phase 7 Plan 04 (단축 PoC + E2E) DEFERRED (사용자 시연 환경 부재). 코드/인프라 (Wave 1·2·3) 모두 완성, 합성 검증 통과 (8 curl smoke + 26 unit + assembleDebug APK). 부수: test 브랜치 자동 로그인 hardcode `test_user` 가 DB 에 없어 페어링 차단됐던 이슈 해소 — auth.users + profiles 생성 + devices.user_id 'testuser1' → 'test_user' 이전. 다음 즉시 = Phase 8 (Drift X3 RTSP). 수요일 D-5.\n\n2026-05-14 — Phase 7 Plan 03 (Android UI — supabase-kt Realtime + ComposeView 임베드 + watch 패키지) ✓ COMPLETE. MyApp.supabase by-lazy 싱글톤 (Realtime + Postgrest + ktor-cio), watch/ 패키지 9 main 파일 (1217 lines + 4 test 합산) — Repository (3 채널 postgresChangeFlow + SafetyAlertReducer) / WatchCardComposable (HR/temp/wear-state/last-alert + polling fallback) / SafetyAlertsScreen (LazyColumn + acknowledge + 의료기기 면책) / SafetyAlertsActivity / PairWatchSection (3-상태 badge + watch-pair 호출). HomeWorkerActivity.setupWatchCard() — main_home_worker.xml ComposeView 임베드 + DisposeOnLifecycleDestroyed. MyFirebaseMessagingService data.type='watch_alert' 라우팅 → SafetyAlertsActivity. DeviceManage.kt 에 PairWatchSection 통합. 4 unit test 26 cases 모두 PASS (MacAddressValidator 9 + WearState 8 + Ack 3 + Reducer 6, 0.116s sum). compileDebugKotlin BUILD SUCCESSFUL. 회귀 가드 acknowledged_at/측정값/realtime-kt:3. 모두 0. Deviation [Rule 3 - environmental block]: Korean repo path 가 forked test JVM sun.jnu.encoding=CP949 와 충돌 → ClassNotFoundException, JEP 400 한계로 -D 플래그 fix 불가. Workaround: layout.buildDirectory.set('D:/ssm-app-build') — 같은 D: 드라이브 ASCII path. Plan correction: Realtime.Status.SUBSCRIBED → CONNECTED (실제 enum). 커밋 c20d0dd·ebcd623·d3d3baf. Wave 4 (07-04 단축 PoC + E2E 시연, autonomous: false) 진입 가능."
---

# Smart Safety Management — State

## Current Position

Phase 1: ✓ COMPLETE (2026-05-06, A1 batch scan + D-19 fallback, 커밋 `559b90a`)
Phase 2: ✓ COMPLETE (2026-05-07, frames_required + pytest 8/8 PASS, 커밋 `954bb19`)
Phase 3: ✓ COMPLETE (2026-05-14) — Plan 01 ✓ (detect_all + fusion_helpers + fusion_configs + 12/12 tests, 커밋 729a1b4·cd2528a·40a9224) + Plan 02 ✓ (scheduler fusion wiring + D-04 disabled + migration 009 DB push + 22/22 tests, 커밋 769a0fc·a2a31c8·d546fb5). FUSION-01·02 완료.
Phase 4: Wave 1·2 완료 (04-01·02·03), Wave 3 (04-04) = 사용자 24h 워치 착용 결정 대기
  - 04-01 ✓: 010_watch_pipeline.sql 운영 DB 적용 완료 (UTC immutability fix)
  - 04-02 ✓: j2208a/ 패키지 (8 모듈, 843 lines) + pytest 39 pass (31 + 04-03 의 8 integration)
  - 04-03 ✓: BLE wiring + watch-alert Edge Function 배포 + .env 보호 + curl smoke 200
  - 04-04 ⏸ : 24h 실측 — non-autonomous, 사용자 결정 대기 (5월 시연 전 진행 vs v1.1 이연)
Phase 5·6: not started (의존성 풀린 시점에 plan)
Phase 7: ⚠ IN PROGRESS (2026-05-14) — 워치-앱 양방향 연동 (BRIDGE-01·02·03). 수요일 2026-05-20 마감.
  - 07-01 ✓ COMPLETE (2026-05-14): supabase-kt 2.2.0 + ktor-cio 2.3.9 + desugar 2.0.4 의존성 lock + BuildConfig (실제 linked project ref `xbjqxnvemcqubjfflain`) + ProGuard keep 룰. 011_watch_app_rls.sql 운영 DB 적용 완료 (RLS narrowing 4종 + supabase_realtime publication 4 테이블 ADD). tests/sql/test_011_rls_isolation.sql + scripts/seed_watch_demo.py (D-05 fallback). 커밋 ddf2def·92bed99·4be6d2c.
  - 07-02 ✓ COMPLETE (2026-05-14): notifications/index.ts case 'watch-ack' (BRIDGE-02) + case 'watch-pair' (BRIDGE-03) 운영 배포. 8 curl smoke 모두 PASS — watch_ack 3종 (정상 200 / idempotency 404 / ownership 404) + watch_pair 5종 (정상 200 / MAC invalid 400 / spoofing 409 / unpair 200 / re-pair idempotent 200). T-7-02·03·05 mitigation. D-09 알림 전이 회귀 가드 통과 (notifications insert 0). 커밋 e2298a2·3eb872d.
  - 07-03 ✓ COMPLETE (2026-05-14): Android UI 본체. MyApp.supabase by-lazy 싱글톤 + watch/ 패키지 9 main + 4 test 파일. HomeWorker ComposeView 임베드 + setupWatchCard + DisposeOnLifecycleDestroyed. WatchCardComposable (CONNECTED → SDK push, 그 외 5초 polling fallback) + SafetyAlertsScreen (acknowledge + 404 idempotent + '의료기기 아님' fine print) + SafetyAlertsActivity 신규 + PairWatchSection (UNPAIRED/CONNECTED/DISCONNECTED 3-색상 badge). MyFirebaseMessagingService data.type='watch_alert' 라우팅 → SafetyAlertsActivity (alert_id extras 신뢰 X — DB 재조회). DeviceManage.kt 통합. 26/26 unit test PASS. T-7-04 accepted, T-7-07/08 mitigated. 커밋 c20d0dd·ebcd623·d3d3baf.
  - 07-04 ⏸ DEFERRED (2026-05-15): 단축 PoC + E2E 시연. autonomous: false — 사용자 환경 부재로 시연 진입 불가, 코드/인프라/curl smoke/unit test 모두 합성 검증 통과. 시연 가용 시점에 재개 (07-04-SUMMARY.md frontmatter status: deferred).
  - **부수 처리 (2026-05-15)**: test 브랜치 자동 로그인이 hardcode 한 `test_user` 가 DB 에 없어 페어링 차단됐던 이슈 해소 — auth.users + profiles row 생성 (id `b0155c48-...`, user_role=worker→manager 보정 미적용 OK) + devices.user_id 'testuser1' → 'test_user' 이전. 시연 진입 시 즉시 paired status 표시 가능.
Phase 8: ✓ COMPLETE (2026-05-18) — Drift X3 RTSP 실시간 카메라 (RTSP-01·02·03, RTSP-02 실기기 측정 deferred → v1.1 LP-3).
  - 08-01 ✓ COMPLETE (2026-05-18): snapshot.capture_rtsp + URL scheme 분기 wrapper + 6 pytest cases. drift_test.py 검증 패턴 (cv2.VideoCapture(CAP_FFMPEG) + 2초 sleep + 3-가드 + 3회 retry) 이식. _capture_ffmpeg internal rename 으로 mp4 fallback 무회귀. OPENCV_FFMPEG_CAPTURE_OPTIONS=rtsp_transport;tcp module top-level setdefault (Pitfall 6 회피). scheduler.py zero-change (SC #4 충족). 6/6 pytest pass + 전체 28/28 pass. 커밋 c3dbf41 (RED) · 715c277 (GREEN). Deviation 1건 (Rule 1 — advisor 조언으로 success 분기에서 output_path.exists()/stat() 가드 제거 → mocked cv2.imwrite 호환). RTSP-01·03 부분 충족, full close 는 08-04 mediamtx E2E 후.
  - 08-03 ✓ COMPLETE (2026-05-18): notifications/index.ts case camera-down + camera-recovered 운영 deploy (70.21kB) + supabase_client.update_camera_health 헬퍼 + scheduler 4 detector wiring. (a) Edge Function 2 case — manager SELECT (group_id + user_role IN ('manager','general_manager')) → 기존 sendPushToUsers (plural) 재사용 (RESEARCH 정정 #5, fcm.ts:239 export 가 이미 Promise.allSettled batching + {sent, failed, skipped} shape — plan critical_constraints A 의 wrapper helper 추가 지시 advisor 정정으로 폐기). (b) Option B (Pitfall 3 회피): channel_id 명시 X, fcm_default_channel 재사용 (Android 코드 변경 0, v1.1 camera_alerts 분리 deferred). data.type='camera_alert' 만 분기 키. (c) D-09 회귀 가드: Edge Function 본문에 notifications.insert() 부재 — push-only, 상태 전이/30분 cooldown/last_alert_at 책임은 plan 08-02 의 pg_cron cameras_healthcheck() 가 소유. (d) 4 curl smoke 모두 PASS: camera-down 정상 (HTTP 200, sent:1 — testuser1 실제 push 수신 — manager group_id=1 정합), camera-down 누락 (HTTP 400), camera-down no-manager group_id=99999 (HTTP 200, sent:0, reason 'no managers in group'), camera-recovered 정상 (HTTP 200, sent:1 실제 push 수신). (e) supabase_client.py: datetime import 추가 + update_camera_health(camera_id) 헬퍼 (datetime.now(timezone.utc).isoformat() → cameras.update({'last_frame_at': iso}).eq(...).execute(), 실패 시 log.warning 만 — capture 자체 성공 시 흐름 차단 X, RESEARCH A6 PostgREST '\"now()\"' 함정 회피). (f) scheduler.py 4 진입점 wiring: _process_single_camera (10분 periodic), _process_fall_for_camera (1분 fall), _process_detection_for_camera (1분 general), _process_fusion_for_camera (1분 fusion) 모두 capture(...) 성공 직후 1줄 추가. SC #4 zero-change: capture() 호출 line 변경 0 (`git diff` 검증). Pitfall 8 accepted: 분당 ≈20× PATCH 허용, dedup deferred v1.1. (g) 회귀: ai_agent/tests/ 28/28 PASS (test_fusion 14 + test_scheduler_buffer 8 + test_snapshot_rtsp 6, 7.83s). (h) Wave 4 prerequisite: Dashboard Vault `service_role_key` 시드 필요 (08-04 cron round-trip 검증용, 본 plan smoke 와 무관). 커밋 c8c7b6d · 00aeedf. Deviations 2건 (Rule 3 plan correction sendPushToUsers helper 폐기 + Rule 3 환경 esm.sh 522 transient).
  - 08-02 ✓ COMPLETE (2026-05-18): 012_cameras_health.sql 운영 DB 적용 완료. (a) pg_net 활성화 (RESEARCH 정정 #2 / Pitfall 1). (b) cameras 3 컬럼 ALTER ADD — last_frame_at TIMESTAMPTZ + health_state TEXT DEFAULT 'unknown' CHECK ENUM + last_alert_at TIMESTAMPTZ. (c) Vault SQL 시드 best-effort — `vault.create_secret('edge_function_base_url', URL, desc)` + EXCEPTION OTHERS 흡수 (advisor 권고), 실제 push 에서 NOTICE 'vault.secrets seeded: edge_function_base_url' 출력. service_role_key 는 의도적으로 SQL 시드 X (T-8-01 git 노출 회피, dashboard 수동 절차 SUMMARY 기재). (d) `cameras_healthcheck()` SECURITY DEFINER plpgsql + SET search_path = public, extensions, net (Pitfall 7 보강) + vault.decrypted_secrets SELECT (sr_key+base_url) + NULL 가드 (RAISE WARNING+RETURN graceful skip) + DOWN 전이 (last_frame_at NOT NULL AND <now()-5min AND health IS DISTINCT FROM 'down' AND last_alert_at NULL OR <now()-30min) → UPDATE+net.http_post 'camera-down' + RECOVERY 전이 (health='down' AND last_frame_at>=now()-5min) → UPDATE+net.http_post 'camera-recovered'. Phase 4 D-09 알림 전이 원칙 1:1 미러. (e) pg_cron 'cameras_healthcheck_minute' 1분 주기 (* * * * *) — 010 unschedule+schedule idempotent 패턴. [BLOCKING] supabase db push --linked --yes 성공 + supabase migration list 에 012 등장. 검증: PostgREST GET cameras 5 rows 모두 health_state='unknown'+last_frame_at=NULL (Pitfall 4 NULL 가드 검증 — 즉시 down 폭발 0), RPC POST /rpc/cameras_healthcheck → 204 (SECURITY DEFINER void OK, 에러 0), 후속 GET 으로 5 rows 상태 보존 확인. A4/A8: testuser1 manager+group_id=1 ↔ cameras 1·5 group_id=1 정합 (plan 08-03 smoke sent>=1 보장). T-8-02 회귀 가드: anon PATCH cameras body[health_state=down] → 200 with [] 빈 배열 + 후속 GET 으로 health_state='unknown' 보존 (003 cameras_update_manager 정책 회귀 가드 PASS). tests/sql/test_012_cameras_health_isolation.sql 작성 (93 lines, 7 assertions: ALTER 3 cols + RLS ENABLED + 003 정책 살아있음 + anon UPDATE 차단 DO block + Pitfall 4 spurious_down_count=0 + cron.job 등록 + pg_proc.prosecdef=t). Dashboard SQL Editor 실행 대기 (psql 미설치). User Setup Required: service_role_key dashboard Vault 시드 (08-03 deploy 전 1회 필수). 커밋 0131ffa · 0755f04. Deviations 2건: Rule 3 환경 (psql 미설치 + SUPABASE_DB_PASSWORD/ACCESS_TOKEN 부재 → PostgREST + Management API NOTICE 조합 우회) + Rule 2 보안 보강 (SET search_path 함수-level 추가).
  - 08-04 ✓ COMPLETE (2026-05-18): mediamtx 합성 E2E + RTSP-02 + Vault sr_key step 10 deferred 명시. 본 SUMMARY 참조.
Phase 8: ✓ COMPLETE (2026-05-18) — RTSP-01·03 완전 충족, RTSP-02 실기기 측정 deferred (v1.1 LP-3), step 10 (5분 healthcheck round-trip FCM) Vault sr_key 시드 시 자연 동작 deferred.
Phase 9: ⚠ IN PROGRESS (2026-05-18) — TBM 현장 작업자 가이드 (TBM-01·02·03). CONTEXT 작성 완료 (`--auto` 모드).
  - 09-CONTEXT.md (D-01~D-09): 013_tbm_schema.sql 4 테이블 + pg_cron + Realtime publication / 수기 서명 (Compose Canvas + tbm-signatures Storage 버킷 private) / 미참여 대상 = 그룹 worker 전원 v1.0 단순 정의 / expected_end_at + 30분 임계 + missed_alert_at 1회 dedup / HomeActivity manager 카드 + HomeWorkerActivity worker 카드 (Phase 7 D-02 ComposeView 1:1 미러) / notifications 4 case (tbm-start/checkin/end/missed) / fcm_default_channel 재사용 (Phase 8 Option B).
  - 09-DISCUSSION-LOG.md: 9 gray area autonomous 결정 audit trail. Phase 4·7·8 패턴 일관성 우선.
  - 다음 = /gsd-plan-phase 9 (Plan 생성). 마감일 없음, ROADMAP estimate 3일. 시연 흐름 (3·4·7·8 + 9) 통합 가능.
Status: Phase 8 ✓ COMPLETE (4/4 plans). 4 plans 모두 commit·SUMMARY·검증 완료 — capture_rtsp + drift_test 패턴 + 012 cameras_health 운영 DB + notifications case camera-down/recovered 운영 deploy + 4/4 smoke PASS + scheduler 4 detector wiring + mediamtx 합성 E2E (cameras 6 cycles RTSP capture + camera_id=4 forklift detection_events 6건 + backoff SnapshotError ~101s + recovery 1 cycle 회복 + T-8-04 mitigation 0건). 다음 = Phase 9 (TBM, 병렬 가능) 또는 Phase 4 04-04 (24h 실측 의사결정) 또는 사용자 Vault sr_key Dashboard 시드 (step 10 FCM round-trip 활성). 수요일 (2026-05-20) D-2.
Last activity: 2026-05-18 — Phase 8 Plan 04 COMPLETE. supabase/functions/notifications/index.ts case camera-down (line 339-388) + case camera-recovered (line 390-426) + supabase functions deploy notifications (70.21kB 성공, 1차 esm.sh 522 transient 5초 후 재시도). 4/4 smoke PASS — sent:1 (testuser1 실제 push 수신, manager group_id=1 정합) / HTTP 400 (payload 누락) / HTTP 200 sent:0 (no-manager) / sent:1 (recovered 실제 push 수신). ai_agent/supabase_client.py update_camera_health 헬퍼 + scheduler.py 4 detector wiring (capture() zero-change SC #4). 회귀 28/28 PASS. 커밋 c8c7b6d · 00aeedf. Wave 4 prerequisite = Dashboard Vault `service_role_key` 시드 (cron round-trip 검증용, 본 plan smoke 와 무관). 다음 Wave 4 = 08-04.

## Accumulated Context

### Decisions

- 2026-04-28: YOLO26 마이그레이션 시점 = 5월 PPT 후 (6월~). v1.0 은 현 detector 그대로.
- 2026-04-28: 우선 트랙 = 모델 + 데이터. 후순위 = 인프라·통합.
- 2026-04-29: J2208A BLE 워치 v1.0 포함 결정. wear-state 임계값 잠정값 진행, 추가
  실험은 v1.1.
- 2026-04-29: GSD `.planning/` 부트스트랩 — `docs/PROJECT_SPEC.md` + 메모리 +
  `iridescent-percolating-fox.md` + `J2208A_안전관리_시스템_PLAN.md` 합성. PROJECT.md
  가 단일 source of truth, `docs/` 는 v0 보존본.
- 2026-04-29: Roadmap 6 phase 결정 — Phase 1·2·3 비전 chain (데이터 → frames → fusion),
  Phase 4 워치 병렬 (코드베이스 분리), Phase 5·6 통합 평가/데모. 19/19 REQs 매핑.
- 2026-05-02: Phase 1 컨텍스트 — 영상 소스 = 레거시 `발표자료용 영상/detection(fire,
  helmet).mp4` (39MB), fire/helmet 동일 mp4 매핑, Storage 새 키 (`source_v2.mp4`),
  detector_configs.py 영구 변경 (MODEL-03 흡수), 검증은 `--once-detect` 1회 + SQL.
- 2026-05-02: Phase 4 컨텍스트 — 기존 `devices` 확장 (mac_address·firmware·last_comm)
  + 신규 4 테이블 (raw_events·wear_state_events·minute_summary·safety_alerts);
  파이프라인 = `scripts/j2208a_sensor_reader.py` 인라인 (모듈 분리 `j2208a/`);
  wear-state 임계 = Python 상수 (v1.1 외부화); TTL = pg_cron + UNIQUE constraint
  dedup; 알림 = FCM only (`_shared/fcm.ts` 재사용); 24h 검증 = 실측 착용.
- 2026-05-02: Phase 4 plan — 4 plan / 3 wave. Wave 1 = 04-01 (마이그레이션) ∥ 04-02
  (j2208a/ 패키지 + 단위 테스트). Wave 2 = 04-03 (BLE wiring + watch-alert
  Edge Function + heartbeat). Wave 3 = 04-04 (24h 실측, non-autonomous).
  Iteration 2/3 PASSED — COMMS_LOST cold-start false-positive fix + XML escape +
  Test D-6 wording 보정 (커밋 `a54d71d` + `6998b3d`).
- 2026-05-04: Phase 1 Task 1·2 실행 완료 (커밋 `34cb4ec` detector_configs 운영급 임계,
  `022383c` upload_reference_videos SOURCES + remote_path). Storage 업로드도 성공
  (fire/source_v2.mp4 + helmet/source_v2.mp4) + cameras.live_url_detail (camera_id 1, 5)
  도 신규 URL 가리킴. **그러나 Task 3.3 검증 단계에서 empirical FAIL** — 신규 영상
  `발표자료용 영상/detection(fire, helmet).mp4` 가 기존 YOLO 가중치 (`fire_best.pt`,
  `hard_hat_best.pt`) 와 부적합.
- 2026-05-04 ~ 05-07: Phase 4 Wave 2 (04-03) 완료. 4 sub-commits — runtime.py (`7e8cac1`,
  367 lines + 8 integration tests 299 lines) + sensor_reader 재작성 (`8be85da`, 521 lines,
  BLE wiring + heartbeat + 백오프) + notifications watch-alert action 배포 (`1936ee6`,
  187 lines) + .env 보호 (`1e9e51a`, 40 lines). Iteration 중 COMMS_LOST cold-start
  false-positive fix + XML escape + Test D-6 wording 보정 (j2208a/derive.py 의
  pre-receipt grace 추가 — last_raw_ts=None 시 미발사).
- 2026-05-06: Phase 1 ✓ COMPLETE (커밋 `559b90a`) — A1 batch scan 종결 + D-19 fallback
  (fire conf 0.10) + helmet 정상 (conf 0.5+ target_classes ['head']). DATA-01·02·03 +
  MODEL-03 (partial — fire D-19 fallback) 충족.
- 2026-05-07: Phase 2 ✓ COMPLETE (커밋 `72e469b`/`5b0de16`/`954bb19`) — frames_required
  룰 + pytest 8/8 PASS. MODEL-01·02·03 모두 [x].
- 2026-05-12: Phase 4 SUMMARY backfill — git log + 코드 검증 결과 (39 pytest pass) 기반
  으로 04-01·02·03-SUMMARY.md 3건 작성. SDK 가 Wave 1·2 완료 인식하도록 정정. 04-04
  (24h 실측) 만 미실행 상태로 남김. STATE/ROADMAP Plan 체크박스 갱신.
- 2026-05-14: Phase 3 (FUSION) CONTEXT.md autonomous discuss 완료. 12개 결정 잠금 —
  `detect_all()` 신규 (D-01), fusion_helpers.py (D-02), FUSION_CONFIGS dict (D-03),
  helmet 단독 알람 fusion 대체 (D-04, Phase 1 D-05 부분 revert), `_process_fusion_for_camera`
  + `_fusion_buffer` Phase 2 패턴 (D-05·D-06), IoU>0.3 N=3 (D-07), hardhat_is_on point-in-area
  head 영역 top 25%×±width/6 N=3 (D-08), person detector cross-camera (D-09), pytest unit
  + 데몬 1회 통합 검증 (D-10), 마이그레이션 009 신규 event_type (D-11), 단독+fusion
  직교 (D-12). 검증 영상 신규 수집은 v1.1 검단·포천 이연 — v1.0 은 합성 입력 + mock.
- 2026-05-14: Phase 3 Plan 01 COMPLETE — detect_all() (D-01), fusion_helpers.py pure
  functions (D-02), FUSION_CONFIGS dict (D-03) 구현 완료. Legacy bbox_utils.py y2 clamp
  버그 수정 (`if y2 < 0: y1 = 0` → `y2 = max(0.0, ...)`). T-03-01 (ZeroDivisionError guard)
  + T-03-02 (frame_width=0 clamp) 위협 완화. test_fusion.py 12/12 PASS (Phase 2 포함 20/20
  full suite). 커밋 729a1b4·cd2528a·40a9224.
- 2026-05-14: Phase 3 Plan 02 COMPLETE + Phase 3 종결. scheduler.py fusion wiring
  (_fusion_buffer + _process_fusion_for_camera + fusion loop + D-04 disabled guard) +
  migration 009 운영 DB 적용 + 22/22 pytest PASS. Code review = 0 critical / 3 warnings
  (WR-01 temp file leak, WR-02 helmet label divergence, WR-03 y1>=y2 guard) — 후속 처리
  대상. HUMAN-UAT (DB row 확인) approved.
- **2026-05-14 (마일스톤 확장)**: 사용자 추가 요청 — 수요일 2026-05-20 까지 (1) 워치-앱
  양방향 연동, (2) Drift X3 RTSP 실시간 카메라, (3) TBM 현장 작업자 가이드. ROADMAP/
  REQUIREMENTS 갱신: Phase 6→9 (Phase 7 BRIDGE-01·02·03 수요일 마감, Phase 8
  RTSP-01·02·03, Phase 9 TBM-01·02·03 추가). REQ 19→28. 마일스톤 명 "5월 PPT 데모" →
  "5월 PPT 데모 + 수요일 추가". 다음 = `/gsd-discuss-phase 7` (수요일 마감 critical).
- 2026-05-14: Phase 3 Plan 02 COMPLETE — scheduler fusion wiring: _fusion_buffer module dict
  + _process_fusion_for_camera (7-step, D-05) + disabled guard (D-04) + fusion loop in
  run_detection_cycle (D-12). Rule 1 fix: camera dict key mismatch (`camera["id"]` →
  `camera["camera_id"]`) caught pre-implementation. Migration 009 `지게차 충돌 위험` applied
  to production Supabase DB via `supabase db push --include-all`. T-03-04/06/08 위협 완화.
  22/22 pytest PASS. FUSION-01·02 완전 충족. 커밋 769a0fc·a2a31c8·d546fb5.
- **2026-05-14 (Phase 7 Plan 03 COMPLETE)**: Android 워치 UI 본체 — supabase-kt 2.2.0 Realtime + Compose 임베드. (a) MyApp.supabase by-lazy 싱글톤 (Realtime + Postgrest install + ktor-cio engine, anon key only). (b) watch/ 패키지 9 main 파일 (1217 lines + 4 test 합산) — SupabaseModule / WatchModels (8 @Serializable data class, ack_at 컬럼 사용 + acknowledged_at == 0) / MacAddressValidator (정규식 `^([0-9A-F]{2}:){5}[0-9A-F]{2}$`) / WearStateLabel (5 상태 + '측정값' 단어 0건 — 의료기기 면책) / WatchRealtimeRepository (3 채널 postgresChangeFlow + SafetyAlertReducer pure object) / WatchAckRetrofitApi / WatchCardComposable (HR/temp/wear-state/last-alert + Realtime.Status.CONNECTED 분기 + 5초 polling fallback + 신호=상태신호 회색 처리) / SafetyAlertsScreen (LazyColumn + acknowledge + 404 idempotent + '1차 경고용, 의료기기 아님' fine print) / PairWatchSection (3-색상 status badge + watch-pair 호출 + Realtime devices 채널 구독). (c) HomeWorkerActivity.setupWatchCard() — main_home_worker.xml 의 ComposeView (@+id/watch_card_compose, profile_bar 와 섹션 1 사이) 임베드 + ViewCompositionStrategy.DisposeOnLifecycleDestroyed + paired device_id 조회 → WatchCardComposable / EmptyWatchPrompt 분기. FCM intent routing — alert_type='watch_alert' 시 SafetyAlertsActivity 직행. (d) SafetyAlertsActivity (신규 ComponentActivity, AIEventActivity 와 별도) — paired device_id 조회 → SafetyAlertsScreen. AndroidManifest 등록. (e) MyFirebaseMessagingService 확장 — data.type='watch_alert' 분기 → showWatchAlertNotification (watch_alerts 채널 IMPORTANCE_HIGH + pendingIntent → SafetyAlertsActivity). notification id = alert_id (D-09 알림 전이 — 같은 alert 재push 시 갱신만). (f) DeviceManage.kt — DeviceManageScreen 끝 (배터리 현황 아래) 에 PairWatchSection 통합 + LocalContext import. (g) 4 unit test 26 cases ALL PASS — MacAddressValidator 9 + WearState 8 + Ack 3 + Reducer 6, 0.116s sum. compileDebugKotlin BUILD SUCCESSFUL. (h) 회귀 가드 — `acknowledged_at` count = 0 in watch/, `측정값` count = 0 in watch/, `realtime-kt:3.` count = 0 in build.gradle.kts. (i) Threats — T-7-04 accepted v1.0 (RLS USING(true), 모든 query 가 user_id 본인 device_id 만), T-7-07 mitigated (FCM extras 신뢰 X — DB 재조회), T-7-08 accepted (anon key 노출 = Supabase 설계). (j) Deviations — Rule 3 환경 블록 (Korean repo path D:\\2026_산업안전 가 forked test JVM sun.jnu.encoding=CP949 와 충돌, JEP 400 한계로 -D 플래그 fix 불가, layout.buildDirectory.set('D:/ssm-app-build') 같은 D: 드라이브 ASCII path 워크어라운드, 4 단계 시도 후 5번째 PASS), Plan correction (Realtime.Status.SUBSCRIBED → CONNECTED 실제 enum 정정), grep gate compliance ('측정값' 단어 코멘트 제거). 커밋 c20d0dd·ebcd623·d3d3baf. Wave 4 (07-04 단축 PoC + E2E 시연, autonomous: false) 진입 가능.
- **2026-05-14 (Phase 7 Plan 02 COMPLETE)**: Edge Function notifications/index.ts 의 case 'watch-ack' (BRIDGE-02) + case 'watch-pair' (BRIDGE-03) 운영 배포 완료. (a) case 'watch-ack' = ownership SQL `device_id IN (SELECT FROM devices WHERE user_id=$user_id)` (T-7-02 mitigation) + idempotency `.is('ack_at', null)` 가드 (재호출 시 0행 + 404) + 서버측 `new Date().toISOString()` (T-7-05 clock spoofing 방어). (b) case 'watch-pair' = MAC 정규식 `/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/` 재검증 + toUpperCase 정규화 + 두 lookup 경로 (mac eq → serial fallback) 모두 user_id 충돌 시 409 (T-7-03 spoofing 차단) + unpair-after-pair 케이스 호환 3-tier 룩업. (c) 8 curl smoke 모두 PASS — watch_ack 3종 (200/404/404) + watch_pair 5종 (200/400/409/200/200). (d) Pitfall 5 회귀 가드 (`acknowledged_at` grep == 0) + D-09 알림 전이 회귀 가드 (notifications insert 5분 윈도우 0행) 모두 통과. (e) Deviations: Rule 3 × 3 (`_shared/{supabase,response,cors}.ts` stash 복원 + `.env` 작성 + Pitfall 5 코멘트 우회) + Rule 1 × 1 (re-pair after unpair → unique constraint → serial_number fallback 추가, `'already paired to another user'` 등장 횟수 1→2 강도 강화). 커밋 e2298a2·3eb872d. Wave 3 (Android UI) 진입 가능.
- **2026-05-18 (Phase 8 Plan 04 ✓ COMPLETE — Phase 8 종결)**: mediamtx 합성 RTSP E2E + backoff + recovery 검증. (a) Task 1 setup (커밋 85370c5): .gitignore `bin/` (advisor #3 FIRST step) + mediamtx v1.18.2 다운로드 (.gitignore 차단 검증) + scripts/mediamtx.yml (5 paths runOnDemand + hls/webrtc/rtmp disable T-8-04 mitigation + 정확한 reference_media 매핑) + scripts/start_rtsp_mock.sh + scripts/stop_rtsp_mock.sh (taskkill /F SIGKILL Pitfall 5 회피) + scripts/restore_cameras_mp4.sql. (b) Task 2 validation evidence (코드 변경 0): RTSP-01 합성 충족 — mediamtx :8554 + cameras 1·5 PATCH → rtsp://localhost:8554/{fire,helmet} + 6 cycles `python main.py --once-detect` → camera 1·5 last_frame_at 6회 갱신 + camera_id=4 forklift detection_events 6건 (event_id 38~43, conf=0.68); RTSP-03 backoff 검증 — stop_rtsp_mock.sh → SnapshotError after 3 attempts log + cameras 1·5 last_frame_at 갱신 멈춤 + 측정 ~101s (OpenCV FFMPEG default 30s × 3, drift_test sleep 보다 backend timeout 우선); 5분 healthcheck — RPC 204 + cameras unchanged 조합으로 Vault sr_key 미시드 graceful skip 자동 detect (012 line 105-110 `IF sr_key IS NULL THEN RAISE WARNING + RETURN`), step 10 FCM 도착만 deferred; RTSP-03 recovery — mediamtx 재시작 → 1 cycle 내 cameras 1·5 last_frame_at 갱신 (03:25:48 / 03:26:02); cleanup — stop_rtsp_mock.sh + tasklist 0건 (T-8-04 mitigation 검증) + PostgREST PATCH cameras 1·5 원복; RTSP-02 deferred 표기 (실기기 ≤10s 측정 → v1.1 6월 LP-3); regression 28/28 PASS. Deviations 3건 모두 Rule 3 (scheduler CLI `python main.py --once-detect` 정정 + Vault sr_key 미시드 step 10 deferred + bash redirect 순서 console 직접 검증으로 우회). 커밋 85370c5 + 본 SUMMARY commit. Phase 8 종결 — RTSP-01·03 완전, RTSP-02 실기기 측정 + Vault sr_key 시드 부분 deferred. 다음 = Phase 9 또는 Phase 4 04-04 의사결정.
- **2026-05-18 (Phase 8 Plan 01 COMPLETE)**: Drift X3 RTSP — snapshot.py 에 cv2 기반 RTSP capture 이식. (a) `capture_rtsp(url, output_path, max_attempts=3)` 신설 — drift_test.py 검증된 sequence: `cv2.VideoCapture(url, cv2.CAP_FFMPEG)` + `cap.set(CAP_PROP_BUFFERSIZE, 1)` + `time.sleep(2)` (handshake) + `cap.read()` + 3-가드 (`ret ∧ cap.isOpened() ∧ frame is not None`) + 시도 사이 `time.sleep(2)` (D-02 amended 2s 고정 3회) + 3회 실패 시 `SnapshotError(f"failed after 3 attempts ...")`. (b) 기존 `capture()` 본문 (ffmpeg subprocess) 을 `_capture_ffmpeg()` internal helper 로 rename — analyzeduration/probesize/-frames:v 1/-q:v 2/-update 1 그대로, mp4 fallback 회귀 0. (c) 신규 `capture()` 는 URL scheme 분기 wrapper — `if url.lower().startswith(('rtsp://','rtsps://')): return capture_rtsp(...) else: return _capture_ffmpeg(...)` 한 줄 분기. 시그니처 보존 (ffmpeg_bin·timeout_sec·seek_seconds 키워드 RTSP 분기에선 무시). (d) 모듈 top-level `os.environ.setdefault("OPENCV_FFMPEG_CAPTURE_OPTIONS", "rtsp_transport;tcp")` — Pitfall 6 (cv2 FFMPEG backend rtsp_transport 기본값 ambiguity) 차단. (e) 6 pytest cases (`ai_agent/tests/test_snapshot_rtsp.py`, 149 라인) — RTSP-01·03 단위 검증: `test_rtsp_success_first_attempt` (sleep 1회), `test_rtsp_retry_then_success` (sleep 5회 = handshake×3+retry×2), `test_rtsp_three_failures_raises`, `test_rtsp_isOpened_false_treated_as_fail`, `test_capture_dispatch_rtsp` (rtsp:// 분기), `test_capture_dispatch_mp4` (mp4 분기). test_scheduler_buffer.py 의 sys.path insert 컨벤션 미러. (f) TDD: RED 6/6 fail (모듈 부재 AttributeError) → GREEN 6/6 pass → 전체 ai_agent/tests/ 28/28 pass (5.72s). (g) scheduler.py `git diff --stat` 빈 출력 (SC #4 충족 — 4 detector 진입점 line 78/139/235/335 무변경). (h) Deviation 1건 (Rule 1 — advisor 조언 사전 적용): plan 의 `if ok and output_path.exists() and output_path.stat().st_size > 0` 가드가 mocked `cv2.imwrite=lambda p,f:True` 와 호환 X → success 분기를 `if ok:` 만으로 단순화. cv2.imwrite 는 in-process 라 반환값 신뢰 가능 (subprocess 와 달리 거짓양성 없음). (i) RTSP-01·03 부분 충족 — full close 는 08-04 mediamtx E2E 후 (REQUIREMENTS.md 체크박스 현 plan 에선 무변경). 커밋 c3dbf41 (test RED) · 715c277 (feat GREEN). 다음 Wave 2 = 08-02 (012_cameras_health.sql + pg_cron healthcheck).
- **2026-05-14 (Phase 7 Plan 01 COMPLETE)**: 워치-앱 양방향 인프라 토대 lock. (a) app/build.gradle.kts
  에 supabase-kt 2.2.0 + ktor-cio 2.3.9 + desugar_jdk_libs 2.0.4 + BuildConfig (SUPABASE_URL/
  ANON_KEY 운영 ref `xbjqxnvemcqubjfflain`) 추가, 회귀 가드 2종 (3.x 거부 + okhttp engine 거부)
  통과. (b) app/proguard-rules.pro 에 io.github.jan.supabase.* + io.ktor.* + kotlinx.serialization.*
  keep 룰 (Pitfall 8 v1.1 minify 대비). (c) supabase/migrations/011_watch_app_rls.sql 운영 DB
  적용 — `supabase db push` 성공, NOTICE 4종 (정책 first-time creation), PostgREST anon SELECT
  200 OK 검증. 003 의 device_watches_select USING(true) DROP + safety_alerts/wear_state_events/
  device_watches/devices SELECT × 4 narrowing (mac_address IS NOT NULL 패턴 v1.0 PoC) +
  supabase_realtime publication 4 테이블 ADD. (d) tests/sql/test_011_rls_isolation.sql + scripts/
  seed_watch_demo.py (D-05 fallback, urllib + service_role key, REMOVED 시나리오 minute_summary
  120 + wear_state_events 3 + safety_alerts 2). Deviations 4건 모두 자동 해소: Rule 1 (SUPABASE_URL
  정정 — plan 의 stale ref) + Rule 3 × 3 (grep regex false-positive 2 + supabase migration history
  stash 복원 1). T-7-01 mitigated, T-7-04 accepted v1.0, T-7-supply mitigated. 커밋 ddf2def·92bed99·
  4be6d2c.

### Blockers

- **(해소됨, 2026-05-06)** Phase 1 mp4 부적합 → A1 hybrid (AI-Hub fire + 자체촬영
  helmet) 적용 + D-19 fallback 으로 종결. Phase 1 ✓ COMPLETE.
- **(해소됨, 2026-05-04)** Phase 4 010 마이그레이션 운영 DB 적용 완료 (UTC cast fix).
- **현재 활성 blocker 없음.** 5월 PPT 데모 의제 = Phase 3 (bbox fusion) + Phase 5
  (eval 지표) + Phase 6 (시연·캡처·슬라이드). Phase 4 04-04 (24h 실측) 는 별도
  의사결정 대기.

### Awaiting User Input

- **04-04 (24h 실측) 의사결정** — non-autonomous. 다음 3 옵션 중 택일 필요:
  1. **(A) 5월 시연 전 실측** — 사용자가 J2208A 워치 24시간 연속 착용 + PC BLE
     상시 연결. 데모 슬라이드에 "24h 실측 데이터" 인용 가능 (강함). 단 5월 PPT
     일정 대비 시간 빠듯.
  2. **(B) v1.1 (6월 검단·포천 설치 직전) 으로 이연** — 5월 시연은 \"코드/단위
     테스트/Edge Function 동작\" 으로만 시연 (Wave 1·2 산출물). 6월 현장 설치 시
     실제 작업자 24h 운용으로 검증. 가장 자연스러운 일정.
  3. **(C) 짧은 PoC (2~4시간 자기 착용) 만 5월 시연용** — 1440 분 행 대신
     120~240 분 행 + REMOVED 5분 탈착 시나리오 1회 + FCM 푸시 도착 캡처. 04-04
     를 \"부분 PASS + 24h 는 v1.1\" 로 close.
- **추천:** (B) v1.1 이연 — 5월 PPT 의제 = 비전 5종 + 워치 로직 완성도 (코드 +
  39 pytest pass + Edge Function 배포). 24h 실측은 검단·포천 LP-3 RTSP 카메라
  설치와 함께 6월 진행.

### Pending Todos

- **⚠ Phase 7 (CRITICAL — 수요일 2026-05-20 마감)** — 워치-앱 양방향 연동
  (BRIDGE-01·02·03). Wave 1·2·3 완료 (07-01 인프라 + 07-02 Edge Function + 07-03 Android UI).
  남은 단계: Wave 4 = `/gsd-execute-plan 7 04` (단축 PoC + E2E 시연, autonomous: false —
  사용자 워치 착용 + 실기기 시연 + 영상/캡처).
- **Phase 8 ✓ COMPLETE 2026-05-18** (Drift X3 RTSP 실시간 카메라, RTSP-01·02·03, RTSP-02 실기기 측정 deferred → v1.1 LP-3, step 10 FCM round-trip Vault sr_key 시드 시 자연 동작) — `ai_agent` mp4 →
  RTSP 전환 + 합성 검증 + 재연결 안정성 완성. Phase 1 cameras 매핑 활용.
  - 08-01 ✓ (snapshot.capture_rtsp + URL scheme 분기, c3dbf41·715c277)
  - 08-02 ✓ (012_cameras_health.sql 운영 DB + cron + Vault edge_function_base_url, 0131ffa·0755f04)
  - 08-03 ✓ (notifications case camera-down/recovered + scheduler 4 detector wiring, c8c7b6d·00aeedf)
  - 08-04 ✓ (mediamtx v1.18.2 합성 E2E + backoff + recovery + T-8-04 mitigation 검증, 85370c5)
- **Phase 9** (TBM 현장 작업자 가이드, TBM-01·02·03) — 4 신규 테이블 + Android
  가이드 화면 + 미참여 알림. 기존 관리자 순회 점검과 병렬 운용. 비전·워치와
  코드베이스 분리.
- **Phase 3** ✓ COMPLETE (2026-05-14) — FUSION-01·02 완전 충족.
- **Phase 5** (평가 — 2단계 정량 지표, EVAL-01·02·03) — Phase 1·2·3 완료됨. Phase 4 04-04
  의사결정 결과에 따라 병렬 진입 가능. `/gsd-discuss-phase 5` 또는 `/gsd-plan-phase 5`.
- **Phase 6** (데모 빌드 — 통합 시연·캡처·PPT) — Phase 5 의 정량 지표 슬라이드
  완료 후 진입.
- **Phase 4 04-04** — 위 의사결정 결과에 따라 (A 즉시 실행 / B v1.1 이연 / C
  단축 PoC).

## Notes

- `gsd-sdk` CLI 미설치 → `git add` + `git commit` 직접 사용. 이후 `gsd-sdk` 설치
  시 `state.milestone-switch` / `commit` / `phases.clear` 핸들러로 전환 가능.
- GSD agents (`gsd-roadmapper`, `gsd-project-researcher`,
  `gsd-research-synthesizer`) 는 `~/.claude/agents/` 에 설치 확인됨.
- Dependency graph: Phase 1 → 2 → 3 (비전 chain), Phase 4 (워치) 병렬, Phase 5
  ← (1·2·3·4) 모두, Phase 6 ← Phase 5 (시연 흐름은 Phase 3·4 산출물 활용).
