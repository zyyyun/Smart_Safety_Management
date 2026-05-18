---
phase: 08-rtsp-camera
plan: 03
subsystem: backend-edge + ai-agent
tags: [phase-8, rtsp, edge-function, supabase, deno, fcm, send-push-to-users, scheduler-wiring, T-8-03, RTSP-01, RTSP-03]
requires:
  - 08-01 (snapshot.capture_rtsp + URL scheme 분기)
  - 08-02 (012_cameras_health.sql + pg_cron + Vault edge_function_base_url + cameras_healthcheck 함수)
provides:
  - notifications/index.ts case "camera-down" + "camera-recovered" (Edge Function action-routing)
  - SupabaseBridge.update_camera_health(camera_id) — cameras.last_frame_at PATCH 헬퍼
  - scheduler 4 detector 진입점 capture-성공-직후 health wiring
  - tests/smoke/camera_down.sh (3 case) + tests/smoke/camera_recovered.sh (1 case)
affects:
  - pg_cron cameras_healthcheck() 의 net.http_post 호출이 200 응답 받음 (round-trip 완성)
  - testuser1 (manager, group_id=1) 가 cameras 1·5 down/recovered 시 실제 FCM push 수신
tech-stack:
  added: []
  patterns:
    - Edge Function action-routing 추가 (Phase 4·7 watch-* 패턴 일관)
    - sendPushToUsers (plural) — 기존 _shared/fcm.ts export 재사용 (RESEARCH 정정 #5)
    - Option B Android channel: channel_id 명시 X, fcm_default_channel 재사용 (Pitfall 3 회피)
    - PostgREST PATCH with ISO8601 (datetime.now(timezone.utc).isoformat()) — RESEARCH A6
key-files:
  created:
    - tests/smoke/camera_down.sh
    - tests/smoke/camera_recovered.sh
  modified:
    - supabase/functions/notifications/index.ts (case "camera-down" + "camera-recovered" 추가, +102 lines)
    - ai_agent/supabase_client.py (datetime import + update_camera_health 헬퍼, +24 lines)
    - ai_agent/scheduler.py (4 detector 진입점 wiring, +8 lines)
decisions:
  - "Option B (deadline 우선): channel_id 명시 X, fcm_default_channel 재사용 — Android 코드 변경 0 (RESEARCH 정정 #4 / Pitfall 3). v1.1 에서 'camera_alerts' 채널 분리 예정."
  - "sendPushToUsers (plural) 사용 — 정정 #5 (Phase 4 single-recipient 패턴 복사 거부). fcm.ts 의 export 가 이미 Promise.allSettled batching + {sent, failed, skipped} 반환 → 추가 helper 작성 불필요."
  - "D-09 알림 전이 원칙: Edge Function 본문은 push-only (notifications insert 0건). 상태 전이/30분 cooldown/last_alert_at 책임은 pg_cron cameras_healthcheck() 가 소유 (08-02)."
  - "scheduler 4× PATCH per cycle 허용 (Pitfall 8): dedup 미구현. 분당 20회 PATCH 정도, Supabase 부하 미미. process-local cycle dedup 은 v1.1 (deferred)."
  - "capture(...) 호출 line zero-change (SC #4): 4 detector 모두 capture() 직후 별도 라인에 bridge.update_camera_health(camera_id) 추가, 시그니처/인자 변경 0."
metrics:
  duration: "≈ 35분 (Task 1 Edge Function + smoke 20분, Task 2 wiring + pytest 15분)"
  completed: "2026-05-18"
---

# Phase 8 Plan 03: notifications case camera-down/recovered + scheduler health wiring — Summary

Edge Function 의 camera-down/recovered 라우팅과 ai_agent 의 last_frame_at PATCH wiring 을 합쳐, plan 08-02 가 만든 pg_cron healthcheck 인프라가 호출할 endpoint 를 활성화하고 scheduler 가 capture 성공 시점에 cameras.last_frame_at 의 진실 source 를 채우도록 함 — RTSP-01·03 backend 완성.

## What changed

### Task 1 — notifications/index.ts case 2종 추가 + deploy + 4 smoke (커밋 `c8c7b6d`)

- **case "camera-down"** (line 339-388): payload `{camera_id, group_id, last_frame_at}` 검증 → cameras 메타 조회 → profiles WHERE group_id AND user_role IN ('manager','general_manager') → sendPushToUsers (plural) → `ok({ok:true, sent, failed, skipped})`.
- **case "camera-recovered"** (line 390-426): camera-down 사본, title="카메라 회복" / severity="NORMAL" / body="frame 수신 재개". last_frame_at 은 회복 알림에 불필요 (방금 frame 수신 = recover trigger).
- **sendPushToUsers 재사용**: `_shared/fcm.ts` 의 export (line 239) 가 이미 `Promise.allSettled` batching + `{sent, failed, skipped}` 반환 → 신규 helper 작성 X (advisor 가 plan 의 critical_constraints A 항목 정정 — 기존 구현이 plan 의 기대 shape 와 1:1 일치).
- **Option B (Pitfall 3 회피)**: payload data 에 channel_id 명시 X. Android MyFirebaseMessagingService 가 기존 fcm_default_channel 로 표시. type="camera_alert" 로 클라이언트 분기 키만 제공 — v1.1 에서 camera_alerts 채널 분리 예정.
- **D-09 알림 전이 회귀 가드**: Edge Function 본문에 notifications.insert() 부재. push-only. 상태 전이는 cron 함수 책임.
- **Deploy**: `supabase functions deploy notifications` → 70.21kB 성공 (1차 esm.sh 522 transient, 5초 후 재시도 성공).
- **smoke 4/4 PASS**:
  | # | scenario | expected | actual |
  |---|----------|----------|--------|
  | 1 | camera-down 정상 (camera_id=1, group_id=1) | HTTP 200 + sent 키 | 200, `{"ok":true,"sent":1,"failed":0,"skipped":0}` — testuser1 실제 push 수신 |
  | 2 | camera-down payload 누락 | HTTP 400 + required msg | 400, `{"error":"camera_id and group_id are required"}` |
  | 3 | camera-down no-manager (group_id=99999) | HTTP 200 + sent:0 | 200, `{"ok":true,"sent":0,"failed":0,"skipped":0,"reason":"no managers in group"}` |
  | 4 | camera-recovered 정상 (camera_id=1, group_id=1) | HTTP 200 + sent 키 | 200, `{"ok":true,"sent":1,"failed":0,"skipped":0}` — 실제 push 수신 |

### Task 2 — supabase_client.update_camera_health + scheduler 4 detector wiring (커밋 `00aeedf`)

- **`SupabaseBridge.update_camera_health(camera_id)`** (supabase_client.py line 156-176): datetime import 추가 + `datetime.now(timezone.utc).isoformat()` → PostgREST `table("cameras").update({"last_frame_at": iso}).eq("camera_id", ...).execute()`. 실패 시 `log.warning` 만 (capture 자체 성공 시 detection/upload 흐름 차단 X). PostgREST `"now()"` 문자열 hardcode 회피 (RESEARCH A6).
- **scheduler.py 4 detector 진입점 wiring** (각 capture(...) 성공 직후 1줄):
  - `_process_single_camera` (line 80) — 10분 periodic snapshot
  - `_process_fall_for_camera` (line 147) — 1분 fall detector
  - `_process_detection_for_camera` (line 248) — 1분 general detector (fire/helmet/forklift/person)
  - `_process_fusion_for_camera` (line 351) — 1분 fusion rule (forklift_person/helmet_missing)
- **SC #4 zero-change**: capture(...) 호출 line 변경 0건. `git diff ai_agent/scheduler.py | grep '^[-+]\s\+capture(' | grep -v update_camera_health` 빈 출력.
- **regression**: ai_agent/tests/ 전체 28/28 PASS (test_fusion 14 + test_scheduler_buffer 8 + test_snapshot_rtsp 6). 7.83s. 영향 0.

## Verification

```text
=== Edge Function gates ===
case "camera-down"              == 1
case "camera-recovered"         == 1
sendPushToUsers (functional)    == 4  (2 new cases × 1 call each, + 2 existing in send_group)
notifications.insert (D-09)     == 3  (unchanged baseline — send_group + send_individual + watch-alert)
channel_id (functional)         == 0  (Option B — Pitfall 3 회피)
camera_alerts (functional)      == 0  (Option B — Pitfall 3 회피)

=== Python helper gates ===
def update_camera_health        == 1
'"now()"' string hardcode       == 0  (functional; docstring reference 1 — anti-pattern 설명용)
datetime.now(..timezone.utc..)  == 2  (import + usage)

=== Scheduler wiring gates ===
bridge.update_camera_health()   == 4  (4 detector 진입점)
capture(...) call sites         == 4  (unchanged — SC #4)

=== Smoke ===
camera_down: 3/3 PASS
camera_recovered: 1/1 PASS

=== Regression ===
ai_agent/tests/ 28/28 PASS (7.83s)
```

## Deviations from Plan

### Auto-fixed

**1. [Rule 3 - Plan correction] sendPushToUsers helper 작성 불필요**

- **Found during:** Task 1 첫 read (notifications/index.ts line 3 import)
- **Issue:** Plan 의 critical_constraints A 항목이 `_shared/fcm.ts` 에 `sendPushToUsers(supabase, userIds, payload)` helper 를 추가하라고 지시 — 그러나 advisor + 코드 확인 결과 fcm.ts line 239-286 에 이미 동일 이름·동일 시그니처·동일 반환 shape (`{sent, failed, skipped}`) 의 export 가 존재. notifications/index.ts line 3 도 이미 import 중. 신규 helper 작성 시 중복 정의 + 코드 회귀 유발.
- **Fix:** 기존 `sendPushToUsers` 그대로 사용. plan 의 wrapper-loop 패턴 코드는 폐기. 결과적으로 task 1 의 deliverable 은 case 2종 추가 + deploy + smoke 4종 (helper 없음).
- **Files modified:** none extra (구현 시점에서 절감)
- **Commit:** `c8c7b6d`

**2. [Rule 3 - Environmental] supabase deploy 1차 시도 esm.sh 522 transient**

- **Found during:** Task 1 step 3 (deploy)
- **Issue:** `supabase functions deploy notifications` → "Import 'https://esm.sh/@supabase/supabase-js@2' failed: 522 <unknown status code>". esm.sh 의 일시적 Cloudflare 오류.
- **Fix:** 5초 sleep 후 동일 명령 재시도 → 70.21kB 성공.
- **Files modified:** none
- **Commit:** none (재시도만)

### None for Rule 4 (architectural)

Plan executed as written for both Tasks (with the helper correction noted above).

## Wave 4 prerequisites

Plan 08-04 진입 전 사용자 액션 필요:

1. **Supabase Vault `service_role_key` 시드** (이전 plan 08-02 에서 안내됨).
   - 위치: Supabase Dashboard → Project Settings → Vault → Secrets → New secret
   - name: `service_role_key`, secret: project Settings → API 의 service_role JWT
   - 목적: pg_cron `cameras_healthcheck()` 가 Authorization header 에 사용 — 없으면 net.http_post 가 `Bearer null` → Edge Function 401.
2. **확인 명령** (시드 후): Dashboard SQL Editor 에서 `SELECT decrypted_secret IS NOT NULL FROM vault.decrypted_secrets WHERE name='service_role_key';` → `t` 확인.

본 plan 08-03 의 직접 검증 (4 smoke) 은 사용자가 anon key 로 직접 curl 호출하므로 Vault 시드와 무관 — 08-04 의 cron round-trip (5분 healthcheck → 자동 발사) 에서만 필요.

## Threat surface scan

본 plan 이 추가하는 표면은 plan frontmatter의 threat_model T-8-03 (anonymous endpoint elevation) 에 이미 등록되어 있으며 accept disposition (Phase 4·7 watch-* 일관 패턴). 추가 threat flag 0건.

## Self-Check

- [x] `supabase/functions/notifications/index.ts` — case "camera-down" / "camera-recovered" 둘 다 정확히 1회 추가 (git log 검증).
- [x] `tests/smoke/camera_down.sh` — 새 파일, 3/3 PASS log 확인 (`/tmp/p8t3a.log`).
- [x] `tests/smoke/camera_recovered.sh` — 새 파일, 1/1 PASS log 확인 (`/tmp/p8t3b.log`).
- [x] `ai_agent/supabase_client.py` — `def update_camera_health` 정확히 1회 정의.
- [x] `ai_agent/scheduler.py` — `bridge.update_camera_health` 호출 4회 (4 detector 진입점).
- [x] 28/28 ai_agent pytest PASS (zero regression).
- [x] 커밋 `c8c7b6d` (Task 1) + `00aeedf` (Task 2) 확인 — `git log --oneline -5`.

## Self-Check: PASSED
