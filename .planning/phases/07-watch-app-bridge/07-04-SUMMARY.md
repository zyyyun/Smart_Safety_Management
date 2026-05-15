---
phase: 07-watch-app-bridge
plan: 04
subsystem: validation (단축 PoC + E2E 시연)
tags: [phase-7, watch, e2e, poc, demo, deferred]
status: deferred
deferred_at: 2026-05-15
deferred_reason: "사용자가 현재 워치 착용 + PC BLE + 실기기 시연 검증 불가 상황. autonomous: false 플랜으로 자동 실행 불가능. Phase 7 의 코드/인프라 (Wave 1·2·3) 는 모두 완성되어 있으며 합성 검증 (curl smoke 8/8 PASS, unit 26/26 PASS, compileDebugKotlin PASS, assembleDebug APK 빌드 성공) 통과. 실측 E2E 만 사용자 가용 시점으로 이연."
requires:
  - 07-01-SUMMARY (인프라 — supabase-kt 2.2.0 + 011 RLS + publication ALTER)
  - 07-02-SUMMARY (Edge Function — watch-ack + watch-pair 배포 완료)
  - 07-03-SUMMARY (Android UI — 22 파일, APK 빌드 성공)
provides:
  - (deferred — 시연 evidence 추후 첨부)
affects:
  - BRIDGE-01·02·03 의 코드 레벨 충족은 완료, 실측 SC 만 미검증
  - 워치 owner 이전 완료 (testuser1 → test_user) — 시연 진입 가능 상태
deferred_acceptance:
  - "PC sensor_reader.py 30분 안정 운용 + REMOVED 5분 시나리오 1회"
  - "HomeWorker 카드 라이브 갱신 캡처 (HR/temp/wear-state 1회 변화)"
  - "SafetyAlertsActivity REMOVED alert + acknowledge 클릭 캡처 (≤5초 사이클)"
  - "SettingDeviceManagement 페어링 status badge 'paired'/'connected' 캡처"
---

# Phase 7 / Plan 04: 단축 PoC + E2E 시연 - SUMMARY (DEFERRED)

**Status:** ⏸ DEFERRED (2026-05-15)
**Outcome:** 코드 + 인프라 완성, 실측 E2E 만 사용자 가용 시점으로 이연

---

## 이연 사유

본 plan 의 acceptance criteria 는 `autonomous: false` 로 사용자 액션 (워치 24시간 착용 또는 단축 2시간 착용 + PC BLE 마스터 + 실기기 캡처) 을 요구함. 2026-05-15 시점에 사용자가 현장 환경 부재로 PoC 진행 불가 → 본 plan 만 이연하고 후속 phase (8 RTSP, 9 TBM) 진행.

## 합성 검증 (이미 완료된 부분)

Wave 1·2·3 단계에서 자동 검증된 항목:

| 검증 항목 | 결과 | 출처 |
|---|---|---|
| supabase-kt 2.2.0 + ktor-cio 2.3.9 의존성 lock | PASS | 07-01 Task 1 grep guards |
| 011_watch_app_rls.sql 운영 DB 적용 | PASS | `supabase db push` + `supabase migration list` 011/011/011 |
| ALTER PUBLICATION supabase_realtime 4 테이블 추가 | PASS | 07-01 Task 2 PostgREST anon SELECT 200 검증 |
| notifications/index.ts case 'watch-ack' 배포 | PASS | 07-02 Task 1 deploy + curl smoke 3/3 (200/404/404) |
| notifications/index.ts case 'watch-pair' 배포 | PASS | 07-02 Task 2 deploy + curl smoke 5/5 (200/400/409/200/200) |
| MAC validation regex (9 cases) | PASS | MacAddressValidatorTest |
| Wear-state 라벨 색상 매핑 (8 cases) | PASS | WearStateLabelTest |
| watch-ack idempotency (3 cases) | PASS | WatchAckIdempotencyTest |
| Realtime SafetyAlertReducer (6 cases) | PASS | WatchRealtimeRepositoryTest |
| compileDebugKotlin | PASS | 3 cycles (Task 1·2·3) |
| testDebugUnitTest watch.* | PASS | 26 tests, 0 failures, 0.116s |
| assembleDebug APK 빌드 | PASS | 70 MB at `D:/ssm-app-build/outputs/apk/debug/app-debug.apk` |
| 워치 owner 매핑 (test 브랜치 자동 로그인 호환) | PASS | 2026-05-15 — devices.user_id testuser1 → test_user 이전 완료 |

## 이연된 acceptance (사용자 PoC 시 검증 필요)

위 frontmatter `deferred_acceptance` 4개 항목.

## 시연 진입 시 동선

1. PC: `python scripts/j2208a_sensor_reader.py` 실행 (BLE 마스터)
2. 워치 착용 (testuser1 시드 MAC `21:02:02:06:01:69`)
3. 폰 (test_user 자동 로그인): HomeWorker 카드 라이브 갱신 확인
4. 5분 탈착 → REMOVED CAUTION FCM 푸시 + 카드 표시 확인
5. SafetyAlertsActivity → "확인" 버튼 → ack_at 갱신 확인
6. 페어링 status badge `paired`/`connected` 캡처
7. 본 SUMMARY 의 frontmatter status 를 `complete` 로 변경 + evidence 경로 첨부

## BRIDGE-01·02·03 의 코드 vs 실측 분리

| REQ | 코드 레벨 (완료) | 실측 (이연) |
|---|---|---|
| BRIDGE-01 | Realtime SDK 3채널 구독 + ComposeView 카드 렌더링 + lifecycle binding | ≤3초 지연 라이브 갱신 캡처 |
| BRIDGE-02 | Edge Function watch-ack + ownership SQL + idempotency + Realtime ack 갱신 path | ≤5초 사이클 사용자 클릭 → DB UPDATE → 카드 반영 |
| BRIDGE-03 | watch-pair Edge Function + MAC regex + spoofing 차단 + status badge UI | 실기기 페어링 사이클 + status connected/disconnected 전이 캡처 |

## Notes

- `test_user` profile 생성됨 (auth.users + profiles.user_id='test_user') — Phase 7 시연 진입을 위한 setup. 시연 종료 후 운영 정리 시 `DELETE FROM profiles WHERE user_id='test_user'` + auth admin delete 가능.
- `stash@{0}` 사용자 pre-phase7 WIP (KakaoMap 키 + 기타) 보존 중. 모든 phase 완료 후 pop + conflict 해소 예정.
- 마이그레이션 001~006 SQL 파일은 Wave 1 stash 복원 후 untracked 잔존 — 운영 DB 와 일치 (Phase 4 이전 상태). 본 phase 변경 없음.
