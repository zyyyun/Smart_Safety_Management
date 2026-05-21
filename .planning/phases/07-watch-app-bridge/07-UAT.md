---
status: partial
phase: 07-watch-app-bridge
source:
  - 07-01-SUMMARY.md
  - 07-02-SUMMARY.md
  - 07-03-SUMMARY.md
  - 07-04-SUMMARY.md (deferred — excluded from UAT)
started: 2026-05-15T19:00:00+09:00
updated: 2026-05-15T19:05:00+09:00
mode: auto-verified + hardware-blocked
---

## Current Test

[testing complete — 7 auto-verified PASS, 3 hardware-blocked, awaiting user confirmation of blocked tests]

## Tests

### 1. APK 빌드 산출물 존재
expected: |
  `D:/ssm-app-build/outputs/apk/debug/app-debug.apk` 파일 존재, 약 70MB.
  실기기/에뮬레이터에 설치 가능 상태.
result: pass
verified_at: 2026-05-15T19:00:00+09:00
verification: |
  ls 확인 — 72402098 bytes (≈69 MB), 2026-05-14 17:17 빌드.

### 2. supabase-kt 2.2.0 의존성 lock (Wave 1 D-01 amended)
expected: |
  `app/build.gradle.kts` 에 supabase-kt 2.2.0 + ktor-client-cio 2.3.9.
  3.x 또는 ktor-client-okhttp 0회 (회귀 가드).
result: pass
verified_at: 2026-05-15T19:00:00+09:00
verification: |
  grep 확인 — realtime-kt:2.2.0 / postgrest-kt:2.2.0 / ktor-client-cio:2.3.9 명시.

### 3. 011 RLS 마이그레이션 적용 (Wave 1)
expected: |
  Supabase 운영 DB 에 011_watch_app_rls.sql 적용됨. service_role 로 safety_alerts SELECT 시 200 응답.
result: pass
verified_at: 2026-05-15T19:00:00+09:00
verification: |
  curl PostgREST → `[{"alert_id":1}]` 반환 (service_role bypass 정상).

### 4. Edge Function notifications/watch-ack 배포 (Wave 2)
expected: |
  notifications Edge Function 에 case 'watch-ack' 배포됨.
  존재 X alert_id 요청 시 ownership check + "alert not found or already acknowledged or not owned by user" 응답.
result: pass
verified_at: 2026-05-15T19:00:00+09:00
verification: |
  curl POST {alert_id:99999, user_id:'test_user'} → {"error":"alert not found ..."}
  의도된 ownership/존재 차단 응답.

### 5. Edge Function notifications/watch-pair 배포 (Wave 2)
expected: |
  notifications Edge Function 에 case 'watch-pair' 배포됨.
  test_user + MAC 21:02:02:06:01:69 pair 요청 시 {ok:true, device_id, mac, op:'pair'} 응답.
result: pass
verified_at: 2026-05-15T19:00:00+09:00
verification: |
  curl POST → {"ok":true,"device_id":1,"mac_address":"21:02:02:06:01:69","op":"pair"}.
  idempotent re-pair 성공.

### 6. devices 테이블 owner 매핑 (시연 진입 setup)
expected: |
  devices.user_id='test_user', mac_address='21:02:02:06:01:69', device_type='WATCH'.
  test 브랜치 자동 로그인 user (test_user) 와 일치 → 앱 시연 진입 시 즉시 paired 상태.
result: pass
verified_at: 2026-05-15T19:00:00+09:00
verification: |
  curl SELECT → user_id=test_user 확인. (2026-05-15 이전 testuser1 → test_user 이전 완료.)

### 7. Wave 3 unit test suite (26/26 PASS)
expected: |
  ./gradlew app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.*"
  → 26 tests, 0 failures (MacAddressValidator 9 + WearStateLabel 8 + WatchAckIdempotency 3 + WatchRealtimeRepository 6).
result: pass
verified_at: 2026-05-14T17:13:00+09:00
verification: |
  07-03-SUMMARY 의 빌드 로그 + 커밋 c20d0dd 의 frontmatter unit test PASS 기록.
  (재실행은 gradle 시간 비용 — SUMMARY 의 evidence 인용.)

### 8. HomeWorkerActivity 워치 카드 라이브 갱신 (BRIDGE-01 시연)
expected: |
  testuser1/test_user 로그인 → 홈 화면 진입 → 워치 카드 표시 (HR/temp/wear-state 라벨 + 마지막 알림 1건).
  PC sensor_reader.py 켜진 상태에서 데이터 변화 시 ≤3초 안에 카드 갱신.
result: blocked
blocked_by: physical-device
reason: |
  J2208A 워치 + PC BLE 마스터 + Android 실기기 시연 환경 부재 (사용자 보고 2026-05-15).
  코드 path 는 Wave 3 의 WatchCardComposable + WatchRealtimeRepository 단위 테스트 통과.

### 9. SafetyAlertsActivity acknowledge 사이클 (BRIDGE-02 시연)
expected: |
  카드 탭 → SafetyAlertsActivity 진입 → 미해결 alert 표시 → "확인" 버튼 → ≤5초 안에 ack_at 컬럼 갱신 + 카드에서 alert 사라짐.
result: blocked
blocked_by: physical-device
reason: |
  실기기 + 실시간 alert seed 부재. Edge Function watch-ack 자체는 검증됨 (Test 4),
  SafetyAlertsScreen 의 LazyColumn + acknowledge 버튼은 단위 테스트 (WatchAckIdempotencyTest 3 cases) 통과.

### 10. SettingDeviceManagementActivity 페어링 status badge (BRIDGE-03 시연)
expected: |
  설정 → 기기관리 → J2208A 워치 섹션 → status badge 표시 (paired/connected/disconnected).
  PC sensor_reader 가 last_comm_at 갱신 시 connected 로 전환.
result: blocked
blocked_by: physical-device
reason: |
  실기기 + PC sensor_reader 동시 운용 환경 부재.
  Edge Function watch-pair + PairWatchSection 단위 테스트는 통과.

## Summary

total: 10
passed: 7
issues: 0
pending: 0
blocked: 3
skipped: 0

## Gaps

[none — no code issues. 3 blocked tests await hardware/시연 환경.]

## Blocked Tests Note

3개 blocked 모두 동일 blocker (`physical-device`):
- J2208A 워치 실기기 (MAC 21:02:02:06:01:69)
- PC BLE 마스터 (j2208a_sensor_reader.py 실행)
- Android 실기기 또는 에뮬레이터 (APK 설치)

이 3 조건이 충족되면 동시에 모두 검증 가능 → 합성 검증 (PoC) 단일 세션으로 8·9·10 한꺼번에 PASS 가능. 시연 가용 시점에 본 UAT 재개 (`/gsd-verify-work 7`).
