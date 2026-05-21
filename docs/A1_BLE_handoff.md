# A.1 워치 BLE → Android Kotlin 포팅 — Handoff

**작성일**: 2026-05-21
**현재 상태**: **A.1.1 완료 (이번 세션) — A.1.2~A.1.5 는 후속 세션에서**
**Plan ref**: `C:\Users\ANNA\.claude\plans\tbm-linear-dragonfly.md` A.1

## 왜 이 문서가 있는가

A.1 (워치 BLE Python → Android Kotlin 포팅) 은 plan 에서 **1.5~2주 풀 작업**, ~2000 lines Kotlin + 신규 Edge Function + Foreground Service + UI rewrite + JUnit/Robolectric 으로 j2208a 43개 테스트 포팅. 한 세션에서 전부 진행하면 중간에 깨진 상태로 끝나 다음 세션이 더 불리. 이번 세션은 **A.1.1 인프라만 완료** + 다음 세션 진입점을 이 doc 으로 정착.

## A.1.1 — 이번 세션에서 완료된 것

✅ **AndroidManifest BLE permission**:
- `BLUETOOTH_SCAN` (Android 12+, neverForLocation flag)
- `BLUETOOTH_CONNECT` (Android 12+)
- `BLUETOOTH` + `BLUETOOTH_ADMIN` (Android 11- backwards compat, maxSdkVersion=30)
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` (Android 14+ FGS subtype)
- `<uses-feature android:name="android.hardware.bluetooth_le" required="true">`

✅ **Nordic Android BLE library dep**: `no.nordicsemi.android:ble-ktx:2.11.0`
- Plan 의 2.7.x 권고를 latest stable 2.11.0 으로 bump (2주 sprint 동안 살아남을 dep).

## A.1.2~A.1.5 — 다음 세션에서 작업할 것

### A.1.2 j2208a Python → Kotlin 포팅 (3-5일)

**대상 패키지**: `app/src/main/java/com/example/smart_safety_management/watch/j2208a/`

**6 파일 직역 (Python → Kotlin)**:

| Kotlin 파일 | Python 원본 | 책임 |
|---|---|---|
| `J2208aProtocol.kt` | `scripts/j2208a_sensor_reader.py:137-272` + `j2208a/decode.py:54-74` | UUID 상수 + 패킷 빌더 + CRC (sum & 0xFF) |
| `PacketParser.kt` | `j2208a/decode.py:131-205` | 16-byte fixed packet parse, cmd 0x01/0x09/0x13/0x22/0x27/0x28/0x41/0x4B 분기 |
| `S2Validator.kt` | `j2208a/validate.py` | HR/temp 범위 검증, drift 감지 |
| `S3Aggregator.kt` | `j2208a/aggregate.py` | median/IQR/delta 1분 집계 |
| `WearStateMachine.kt` | `j2208a/state_machine.py` | 5-state FSM, 5초 majority vote |
| `AlertDeriver.kt` | `j2208a/derive.py` | TACHY/REMOVED/COMMS_LOST alert rule |

**우선 참조 자료** (`reference_jcwear_assets.md` 메모리 참조):
1. `D:\2025_산업안전\산업안전\JCWear\J-Style 2208A Smart Health Bracelet SDK_v2_20231225\Android.zip` — **제조사 공식 Kotlin/Java 참조, 1순위**
2. `JCWear_deassemble.hwp` + `블루투스 센서 데이터 수신 과정.hwp` + `블루투스 연결 데이터 받는법.pdf` — 한글 프로토콜 문서
3. `scripts/j2208a_sensor_reader.py:137-272` — 현재 동작하는 Python reference
4. `j2208a/decode.py`, `j2208a/validate.py` 등 — 직역 source

**TEST ORACLE — 절대 변경 금지**:
- `j2208a/tests/` 의 43개 테스트 입력·출력 데이터셋. Kotlin 포팅한 클래스가 동일 입력 → 동일 출력을 내야 함.
- Robolectric + JUnit 4 로 테스트 포팅 (`app/src/test/java/.../watch/j2208a/`).

**첫 concrete 작업 (다음 세션 시작점)**:
```
J2208aProtocol.kt 생성 → CRC 함수 포팅
   원본: j2208a/decode.py:54-74
   ```python
   def calc_crc(data: bytes) -> int:
       return sum(data) & 0xFF
   ```
   Kotlin:
   ```kotlin
   fun calcCrc(data: ByteArray): Byte = (data.sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
   ```
```

### A.1.3 BLE Foreground Service (2-3일)

**대상**: `app/src/main/java/com/example/smart_safety_management/watch/WatchBleService.kt`

- Nordic BLE library 의 `BleManager` 상속 → `requiredService` UUID 0xFFF0, write 0xFFF6, notify 0xFFF7
- `Manager.connect().retry(3, 2_000).useAutoConnect(true)` (Python bleak backoff 미러)
- `ForegroundService` + `NotificationCompat` "워치 연결 중" 표시 (Android 12+ FGS 요건)
- ViewModel 이 packet flow → S2 → S3 → S4 인라인 호출

### A.1.4 Supabase Edge Function — `watch-raw-write` (1-2일)

**왜 필요한가**: APK 안에 service_role key 평문 넣으면 anyone APK 디컴파일 → DB 마음대로 INSERT 가능. Edge Function proxy 가 anon JWT + claim 의 user_id 로 ownership 강제.

**대상**: `supabase/functions/notifications/index.ts` (기존 watch case 들과 같은 라우터) 또는 신규 `supabase/functions/watch/index.ts`.

**4 action 추가**:
- `raw_event_batch` — 1-5초 buffer 한 raw_events 배열 INSERT
- `minute_summary_batch` — 1분 집계 INSERT
- `wear_state_event` — 상태 전이 INSERT
- `safety_alert` — 기존 `watch-alert` case 로 위임 (FCM 까지 일관)

**Android 측**: `app/.../watch/WatchSupabaseWriter.kt` 신규 — Retrofit 으로 4 action 호출.

### A.1.5 페어링 UI 자동화 (1-2일)

**대상**: `app/.../watch/PairWatchSection.kt` rewrite.

- 현재: MAC 수동 입력
- 변경: `BluetoothLeScanner.startScan()` 12초 → J-Style 또는 service UUID 매칭 device list
- 1개 발견 시 자동 선택, 여러 개면 dialog
- 선택 후 자동으로 `WatchBleService` 시작 + 기존 `watch-pair` Edge Function 으로 UPSERT

## 알려진 위험 (plan 에서 식별)

1. **JCWear 앱과 BLE adapter 충돌** — OS 수준 회피 불가. 사용자 가이드 "JCWear 종료" 명시 + 앱 진입 시 active app 감지 후 경고.
2. **service_role key 노출** — A.1.4 Edge Function proxy 로 완전 우회.
3. **Callback thread ANR** — Nordic BLE library 가 coroutine 자동 marshal → 직접 `BluetoothGattCallback` 사용 시만 위험.

## 다음 세션 시작 명령어 (suggested)

```
A.1.2 부터 이어서 진행. JCWear/Android.zip 압축 해제부터 시작 →
J2208aProtocol.kt + PacketParser.kt 2개 파일 먼저 완성 + Robolectric 테스트.
```

## 검증 체크리스트 (A.1 전체 완료 시점)

- 폰 BLE 페어링 화면 → 자동 scan → testuser1 워치 자동 연결
- PC bleak 종료 상태에서도 폰만으로 raw_events Supabase 적재
- `SELECT * FROM raw_events WHERE device_id=X ORDER BY ts DESC LIMIT 5` 최신 1초 row
- 폰 background 진입 → Foreground Service 알림 유지 + BLE 끊김 없음
- `j2208a/tests/` 43개 → Kotlin Robolectric 포팅 후 동일 PASS
