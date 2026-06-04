# J2208A B-Mode Reactive Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make J2208A watch monitoring use one foreground-service-owned B-mode BLE read loop and make every watch UI update from live runtime state without app restart.

**Architecture:** Add pure Kotlin protocol and runtime-state units first, then refactor `JcWearBleBridge` into a serialized B-mode session helper owned by `WatchBleForegroundService`. Compose screens merge `WatchRuntimeStore.state` with Supabase snapshots so current connection, read, upload, and stale states render immediately.

**Tech Stack:** Android Kotlin, Jetpack Compose, Kotlin coroutines `StateFlow`, Android BLE GATT, Supabase PostgREST/Realtime, JUnit4.

---

## File Structure

- Create `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearBModeProtocol.kt`
  - Owns B-mode command bytes and payload parsing.
  - Keeps the `fff2` B-mode characteristic separate from the SDK `fff6`/`fff7` path.
- Modify `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearScanModels.kt`
  - Adds `ppgValue` to `JcWearHealthReading`.
  - Adds `READING` and `RETRYING` connection states.
- Create `app/src/main/java/com/example/smart_safety_management/watch/ble/WatchRuntimeState.kt`
  - Defines `WatchRuntimeState`, `WatchRuntimeSnapshot`, `WatchRuntimeStatus`, `WatchRuntimeStore`, freshness helpers, and snapshot merge helpers.
- Modify `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearBleBridge.kt`
  - Uses `fff2` B-mode init and 100 ms read loop for monitoring.
  - Serializes GATT operations so writes, reads, identify, and disconnect do not overlap.
  - Emits `JcWearHealthReading(ppgValue = value)` on every valid read.
- Modify `app/src/main/java/com/example/smart_safety_management/watch/ble/WatchBleForegroundService.kt`
  - Owns service lifetime, runtime state, upload throttling, and idempotent restart behavior.
- Modify `app/src/main/java/com/example/smart_safety_management/watch/ble/WatchBleServiceController.kt`
  - Seeds runtime state when config is saved or cleared.
- Modify `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearDeviceRegistrar.kt`
  - Accepts PPG-only readings without discarding them locally.
  - Uploads only server-supported HR/temp/battery fields until the Edge Function contract adds PPG.
- Modify `app/src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt`
  - Uses the UI bridge for scan and explicit identify only.
  - Updates runtime state immediately after registration success.
- Modify `app/src/main/java/com/example/smart_safety_management/watch/WatchCardComposable.kt`
  - Merges runtime state into the main worker watch card.
- Modify `app/src/main/java/com/example/smart_safety_management/watch/WatchMiniCardComposable.kt`
  - Merges runtime state into the admin/home compact watch card.
- Modify `app/src/main/java/com/example/smart_safety_management/watch/WatchDetailScreen.kt`
  - Shows live runtime status, PPG, freshness, and advancing relative times.
- Create `app/src/test/java/com/example/smart_safety_management/watch/ble/JcWearBModeProtocolTest.kt`
- Create `app/src/test/java/com/example/smart_safety_management/watch/ble/WatchRuntimeStateTest.kt`
- Modify existing contract tests under `app/src/test/java/com/example/smart_safety_management/watch`.

## Task 1: B-Mode Protocol And Reading Model

**Files:**
- Create: `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearBModeProtocol.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearScanModels.kt`
- Create: `app/src/test/java/com/example/smart_safety_management/watch/ble/JcWearBModeProtocolTest.kt`
- Modify: `app/src/test/java/com/example/smart_safety_management/watch/ble/JcWearScanModelsTest.kt`

- [ ] **Step 1: Write the failing B-mode protocol tests**

Create `JcWearBModeProtocolTest.kt`:

```kotlin
package com.example.smart_safety_management.watch.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JcWearBModeProtocolTest {
    @Test
    fun commandsAreFixedSixteenBytePayloadsWithoutCrc() {
        assertArrayEquals(byteArrayOf(0x2E, 0x00, 0x00) + ByteArray(13), JcWearBModeProtocol.resetCommand)
        assertArrayEquals(byteArrayOf(0x3B, 0x01, 0x01) + ByteArray(13), JcWearBModeProtocol.ppgInitCommand)
        assertArrayEquals(byteArrayOf(0x0B, 0x01, 0x01) + ByteArray(13), JcWearBModeProtocol.realtimeStartCommand)
    }

    @Test
    fun parsePpgReadsSecondAndThirdBytesAsBigEndianValue() {
        val parsed = JcWearBModeProtocol.parsePpg(byteArrayOf(0x00, 0x03, 0xA4.toByte()))

        assertEquals(932, parsed)
    }

    @Test
    fun parsePpgRejectsShortPayloads() {
        assertNull(JcWearBModeProtocol.parsePpg(byteArrayOf(0x00, 0x03)))
    }
}
```

- [ ] **Step 2: Add the reading-model failing assertion**

Append to `JcWearScanModelsTest.kt`:

```kotlin
@Test
fun ppgOnlyReadingCountsAsAValue() {
    val reading = JcWearHealthReading(ppgValue = 932)

    assertTrue(reading.hasAnyValue)
    assertEquals(932, reading.ppgValue)
}
```

- [ ] **Step 3: Run the focused tests and verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.ble.JcWearBModeProtocolTest" --tests "com.example.smart_safety_management.watch.ble.JcWearScanModelsTest"
```

Expected: fail because `JcWearBModeProtocol` and `ppgValue` do not exist.

- [ ] **Step 4: Implement the protocol object and extend the reading model**

Create `JcWearBModeProtocol.kt`:

```kotlin
package com.example.smart_safety_management.watch.ble

import java.util.UUID

object JcWearBModeProtocol {
    val serviceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val dataUuid: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    val resetCommand: ByteArray = byteArrayOf(0x2E, 0x00, 0x00) + ByteArray(13)
    val ppgInitCommand: ByteArray = byteArrayOf(0x3B, 0x01, 0x01) + ByteArray(13)
    val realtimeStartCommand: ByteArray = byteArrayOf(0x0B, 0x01, 0x01) + ByteArray(13)

    fun parsePpg(payload: ByteArray): Int? {
        if (payload.size < 3) return null
        val high = payload[1].toInt() and 0xFF
        val low = payload[2].toInt() and 0xFF
        return (high shl 8) or low
    }
}
```

Modify `JcWearHealthReading` in `JcWearScanModels.kt`:

```kotlin
data class JcWearHealthReading(
    val heartRate: Int? = null,
    val bodyTemp: Float? = null,
    val batteryLevel: Int? = null,
    val ppgValue: Int? = null,
) {
    val hasAnyValue: Boolean
        get() = heartRate != null || bodyTemp != null || batteryLevel != null || ppgValue != null
}
```

Keep `fromSdkMap` returning `JcWearHealthReading(heartRate = heart, bodyTemp = tempValue, batteryLevel = battery)` so old SDK test coverage still passes.

- [ ] **Step 5: Run the focused tests and verify they pass**

Run the command from Step 3.

Expected: all selected tests pass.

- [ ] **Step 6: Commit Task 1**

```powershell
git add app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearBModeProtocol.kt app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearScanModels.kt app/src/test/java/com/example/smart_safety_management/watch/ble/JcWearBModeProtocolTest.kt app/src/test/java/com/example/smart_safety_management/watch/ble/JcWearScanModelsTest.kt
git commit -m "feat(watch): add J2208A B-mode protocol"
```

## Task 2: Runtime State Store And Merge Helpers

**Files:**
- Create: `app/src/main/java/com/example/smart_safety_management/watch/ble/WatchRuntimeState.kt`
- Create: `app/src/test/java/com/example/smart_safety_management/watch/ble/WatchRuntimeStateTest.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/WatchModels.kt`

- [ ] **Step 1: Write runtime-state tests**

Create `WatchRuntimeStateTest.kt`:

```kotlin
package com.example.smart_safety_management.watch.ble

import com.example.smart_safety_management.watch.DeviceRow
import com.example.smart_safety_management.watch.DeviceWatchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WatchRuntimeStateTest {
    private val device = DeviceRow(
        deviceId = 7,
        deviceType = "WATCH",
        macAddress = "21:02:02:06:01:69",
        lastCommAt = "2026-06-02T04:00:00Z",
        updatedAt = "2026-06-02T04:00:00Z",
        batteryLevel = 94,
    )

    @Test
    fun runtimeReadTimeWinsOverOlderSupabaseTimestamp() {
        val runtime = WatchRuntimeState(
            deviceId = 7,
            macAddress = "21:02:02:06:01:69",
            status = WatchRuntimeStatus.READING,
            lastReadAt = Instant.parse("2026-06-02T04:01:00Z"),
        )

        val merged = WatchRuntimeSnapshot.from(device, null, runtime, Instant.parse("2026-06-02T04:01:05Z"))

        assertEquals("5초 전", merged.lastCommunicationLabel)
        assertTrue(merged.isFresh)
        assertEquals("수신 중", merged.statusLabel)
    }

    @Test
    fun staleRuntimeDoesNotRenderAsNormalOperation() {
        val runtime = WatchRuntimeState(
            deviceId = 7,
            macAddress = "21:02:02:06:01:69",
            status = WatchRuntimeStatus.READING,
            lastReadAt = Instant.parse("2026-06-02T04:00:00Z"),
        )

        val merged = WatchRuntimeSnapshot.from(device, null, runtime, Instant.parse("2026-06-02T04:00:20Z"))

        assertFalse(merged.isFresh)
        assertEquals("데이터 대기", merged.statusLabel)
    }

    @Test
    fun ppgOnlyRuntimeKeepsHrAndTemperatureUnavailable() {
        val runtime = WatchRuntimeState(
            deviceId = 7,
            macAddress = "21:02:02:06:01:69",
            status = WatchRuntimeStatus.READING,
            lastReadAt = Instant.parse("2026-06-02T04:01:00Z"),
            latestReading = JcWearHealthReading(ppgValue = 932),
        )

        val merged = WatchRuntimeSnapshot.from(device, null, runtime, Instant.parse("2026-06-02T04:01:05Z"))

        assertEquals("932", merged.ppgDisplay)
        assertEquals("측정 대기", merged.hrDisplay)
        assertEquals("측정 대기", merged.tempDisplay)
    }

    @Test
    fun clearingRuntimeRemovesActiveDevice() {
        WatchRuntimeStore.update(WatchRuntimeState(deviceId = 7, macAddress = "21:02:02:06:01:69"))
        WatchRuntimeStore.clear(7)

        assertNull(WatchRuntimeStore.state.value.deviceId)
    }
}
```

- [ ] **Step 2: Run the runtime-state test and verify it fails**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.ble.WatchRuntimeStateTest"
```

Expected: fail because runtime-state types do not exist.

- [ ] **Step 3: Implement runtime state and snapshot helpers**

Create `WatchRuntimeState.kt`:

```kotlin
package com.example.smart_safety_management.watch.ble

import com.example.smart_safety_management.watch.DeviceRow
import com.example.smart_safety_management.watch.DeviceWatchSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

enum class WatchRuntimeStatus {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    READING,
    UPLOADING,
    RETRYING,
    DISCONNECTED,
    FAILED,
}

data class WatchRuntimeState(
    val deviceId: Int? = null,
    val userId: String? = null,
    val macAddress: String? = null,
    val status: WatchRuntimeStatus = WatchRuntimeStatus.IDLE,
    val lastReadAt: Instant? = null,
    val lastUploadAt: Instant? = null,
    val latestReading: JcWearHealthReading? = null,
    val lastError: String? = null,
)

data class WatchRuntimeSnapshot(
    val statusLabel: String,
    val isFresh: Boolean,
    val lastCommunicationLabel: String,
    val ppgDisplay: String,
    val hrDisplay: String,
    val tempDisplay: String,
    val batteryDisplay: String,
) {
    companion object {
        private val freshWindow = Duration.ofSeconds(10)

        fun from(
            device: DeviceRow?,
            dbSnapshot: DeviceWatchSnapshot?,
            runtime: WatchRuntimeState,
            now: Instant = Instant.now(),
        ): WatchRuntimeSnapshot {
            val runtimeTime = listOfNotNull(runtime.lastReadAt, runtime.lastUploadAt).maxOrNull()
            val dbTime = listOfNotNull(parseInstant(dbSnapshot?.updatedAt), parseInstant(device?.lastCommAt), parseInstant(device?.updatedAt)).maxOrNull()
            val newest = listOfNotNull(runtimeTime, dbTime).maxOrNull()
            val fresh = runtimeTime?.let { Duration.between(it, now) <= freshWindow } == true
            val reading = runtime.latestReading
            val status = when {
                runtime.status == WatchRuntimeStatus.RETRYING -> "재연결 중"
                runtime.status == WatchRuntimeStatus.CONNECTING -> "연결 중"
                runtime.status == WatchRuntimeStatus.CONNECTED -> "연결됨"
                runtime.status == WatchRuntimeStatus.READING && fresh -> "수신 중"
                runtime.status == WatchRuntimeStatus.UPLOADING && fresh -> "업로드 중"
                runtime.status == WatchRuntimeStatus.FAILED -> "연결 실패"
                runtime.status == WatchRuntimeStatus.DISCONNECTED -> "끊김"
                device?.macAddress.isNullOrBlank() -> "미등록"
                else -> "데이터 대기"
            }
            return WatchRuntimeSnapshot(
                statusLabel = status,
                isFresh = fresh,
                lastCommunicationLabel = relative(newest, now),
                ppgDisplay = reading?.ppgValue?.toString() ?: "측정 대기",
                hrDisplay = (reading?.heartRate ?: dbSnapshot?.heartRate)?.takeIf { it > 0 }?.let { "$it bpm" } ?: "측정 대기",
                tempDisplay = (reading?.bodyTemp ?: dbSnapshot?.bodyTemp)?.takeIf { it > 0f }?.let { String.format("%.1f°C", it) } ?: "측정 대기",
                batteryDisplay = (reading?.batteryLevel ?: dbSnapshot?.batteryLevel ?: device?.batteryLevel)?.let { "$it%" } ?: "--",
            )
        }

        private fun parseInstant(value: String?): Instant? {
            val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return runCatching { Instant.parse(raw) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }.getOrNull()
        }

        private fun relative(value: Instant?, now: Instant): String {
            value ?: return "-"
            val seconds = Duration.between(value, now).seconds.coerceAtLeast(0)
            return when {
                seconds < 60 -> "${seconds}초 전"
                seconds < 3600 -> "${seconds / 60}분 전"
                else -> "${seconds / 3600}시간 전"
            }
        }
    }
}

object WatchRuntimeStore {
    private val _state = MutableStateFlow(WatchRuntimeState())
    val state: StateFlow<WatchRuntimeState> = _state.asStateFlow()

    fun update(next: WatchRuntimeState) {
        _state.value = next
    }

    fun mutate(block: (WatchRuntimeState) -> WatchRuntimeState) {
        _state.value = block(_state.value)
    }

    fun clear(deviceId: Int? = null) {
        if (deviceId == null || _state.value.deviceId == deviceId) {
            _state.value = WatchRuntimeState()
        }
    }
}
```

- [ ] **Step 4: Run the runtime-state test and verify it passes**

Run the command from Step 2.

Expected: `WatchRuntimeStateTest` passes.

- [ ] **Step 5: Commit Task 2**

```powershell
git add app/src/main/java/com/example/smart_safety_management/watch/ble/WatchRuntimeState.kt app/src/test/java/com/example/smart_safety_management/watch/ble/WatchRuntimeStateTest.kt
git commit -m "feat(watch): add live runtime state"
```

## Task 3: B-Mode Bridge Read Loop Contract

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearBleBridge.kt`
- Modify: `app/src/test/java/com/example/smart_safety_management/watch/ble/JcWearBleBridgeVibrationContractTest.kt`

- [ ] **Step 1: Replace the old vibration contract with B-mode source-contract assertions**

Modify `JcWearBleBridgeVibrationContractTest.kt` so it asserts the bridge uses the B-mode characteristic and does not start SDK realtime measurement as the primary monitor:

```kotlin
@Test
fun bridgeUsesBModeReadLoopInsteadOfSdkNotifyTelemetryForMonitoring() {
    val src = bridge.readText()

    assertTrue(src.contains("JcWearBModeProtocol.dataUuid"))
    assertTrue(src.contains("JcWearBModeProtocol.resetCommand"))
    assertTrue(src.contains("JcWearBModeProtocol.ppgInitCommand"))
    assertTrue(src.contains("JcWearBModeProtocol.realtimeStartCommand"))
    assertTrue(src.contains("JcWearBModeProtocol.parsePpg"))
    assertFalse(src.contains("BleSDK.RealTimeStep(true, true)"))
    assertFalse(src.contains("BleSDK.GetDeviceBatteryLevel()"))
}

@Test
fun bridgeKeepsManualIdentifyVibrationSeparateFromMonitorLoop() {
    val src = bridge.readText()

    assertTrue(src.contains("fun identify(device: JcWearDiscoveredDevice)"))
    assertTrue(src.contains("private const val IDENTIFY_VIBRATION_TIMES = 2"))
    assertTrue(src.contains("BleSDK.MotorVibrationWithTimes(IDENTIFY_VIBRATION_TIMES)"))
}
```

- [ ] **Step 2: Run the bridge contract and verify it fails**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.ble.JcWearBleBridgeVibrationContractTest"
```

Expected: fail because the bridge still uses `fff6`/`fff7` SDK telemetry commands.

- [ ] **Step 3: Refactor bridge state enums and constants**

In `JcWearScanModels.kt`, extend `JcWearConnectionState`:

```kotlin
enum class JcWearConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    READING,
    RETRYING,
    FAILED,
}
```

In `JcWearBleBridge.kt`, replace the monitoring data characteristic with:

```kotlin
private fun dataCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? =
    gatt.getService(JcWearBModeProtocol.serviceUuid)?.getCharacteristic(JcWearBModeProtocol.dataUuid)
```

Keep the SDK `BleSDK.MotorVibrationWithTimes` call only inside `vibrateForIdentification`.

- [ ] **Step 4: Add serialized B-mode init and read-loop methods**

In `JcWearBleBridge.kt`, add methods with these exact responsibilities:

```kotlin
private fun startBModeReadLoop(gatt: BluetoothGatt) {
    if (telemetryLoopActive) return
    telemetryLoopActive = true
    _uiState.value = _uiState.value.copy(connectionState = JcWearConnectionState.READING, errorMessage = null)
    handler.post {
        writeBModeCommand(gatt, JcWearBModeProtocol.resetCommand)
        handler.postDelayed({
            writeBModeCommand(gatt, JcWearBModeProtocol.ppgInitCommand)
            handler.postDelayed({
                writeBModeCommand(gatt, JcWearBModeProtocol.realtimeStartCommand)
                scheduleBModeRead(gatt)
            }, PPG_INIT_DELAY_MS)
        }, RESET_DELAY_MS)
    }
}

private fun scheduleBModeRead(gatt: BluetoothGatt) {
    if (!telemetryLoopActive) return
    val characteristic = dataCharacteristic(gatt) ?: return
    runCatching { gatt.readCharacteristic(characteristic) }
        .onFailure {
            _uiState.value = _uiState.value.copy(connectionState = JcWearConnectionState.FAILED, errorMessage = it.message)
        }
}

private fun writeBModeCommand(gatt: BluetoothGatt, command: ByteArray) {
    val characteristic = dataCharacteristic(gatt) ?: return
    characteristic.value = command
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(characteristic, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    } else {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        @Suppress("DEPRECATION")
        gatt.writeCharacteristic(characteristic)
    }
}
```

Add constants:

```kotlin
private const val RESET_DELAY_MS = 3_000L
private const val PPG_INIT_DELAY_MS = 2_000L
private const val B_MODE_READ_INTERVAL_MS = 100L
```

- [ ] **Step 5: Parse B-mode reads in GATT callbacks**

Add both legacy and Android 13+ read callbacks:

```kotlin
@Suppress("DEPRECATION")
override fun onCharacteristicRead(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    status: Int,
) {
    handleBModeRead(gatt, characteristic.value, status)
}

override fun onCharacteristicRead(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    status: Int,
) {
    handleBModeRead(gatt, value, status)
}
```

Add the handler:

```kotlin
private fun handleBModeRead(gatt: BluetoothGatt, value: ByteArray?, status: Int) {
    if (!telemetryLoopActive) return
    if (status == BluetoothGatt.GATT_SUCCESS && value != null) {
        JcWearBModeProtocol.parsePpg(value)?.let { ppg ->
            _healthReadings.tryEmit(JcWearHealthReading(ppgValue = ppg))
            _uiState.value = _uiState.value.copy(connectionState = JcWearConnectionState.READING, errorMessage = null)
        }
        handler.postDelayed({ scheduleBModeRead(gatt) }, B_MODE_READ_INTERVAL_MS)
    } else {
        _uiState.value = _uiState.value.copy(connectionState = JcWearConnectionState.FAILED, errorMessage = "B-mode read failed: $status")
    }
}
```

- [ ] **Step 6: Start B-mode after service discovery**

In `onServicesDiscovered`, replace `enableNotifications(gatt)` and `startTelemetryLoop()` with:

```kotlin
_uiState.value = _uiState.value.copy(
    connectionState = JcWearConnectionState.CONNECTED,
    errorMessage = null,
)
startBModeReadLoop(gatt)
```

Keep the pending identify path after `startBModeReadLoop(gatt)`.

- [ ] **Step 7: Run bridge and protocol tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.ble.*"
```

Expected: all `watch.ble` tests pass.

- [ ] **Step 8: Commit Task 3**

```powershell
git add app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearBleBridge.kt app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearScanModels.kt app/src/test/java/com/example/smart_safety_management/watch/ble/JcWearBleBridgeVibrationContractTest.kt
git commit -m "feat(watch): use B-mode BLE read loop"
```

## Task 4: Foreground Service Runtime Ownership And Upload Throttling

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/ble/WatchBleForegroundService.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/ble/WatchBleServiceController.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearDeviceRegistrar.kt`
- Modify: `app/src/test/java/com/example/smart_safety_management/watch/ble/WatchBleForegroundServiceContractTest.kt`
- Create: `app/src/test/java/com/example/smart_safety_management/watch/ble/WatchReadingUploadPolicyTest.kt`

- [ ] **Step 1: Add source-contract tests for runtime state and idempotent monitoring**

Append to `WatchBleForegroundServiceContractTest.kt`:

```kotlin
@Test
fun servicePublishesRuntimeStateForEveryConnectionPhase() {
    val src = service.readText()

    assertTrue(src.contains("WatchRuntimeStore.mutate"))
    assertTrue(src.contains("WatchRuntimeStatus.SCANNING"))
    assertTrue(src.contains("WatchRuntimeStatus.CONNECTING"))
    assertTrue(src.contains("WatchRuntimeStatus.READING"))
    assertTrue(src.contains("lastReadAt = Instant.now()"))
    assertTrue(src.contains("lastUploadAt = Instant.now()"))
}

@Test
fun repeatedStartsWithSameConfigDoNotRestartGattSession() {
    val src = service.readText()

    assertTrue(src.contains("if (config == activeConfig && monitorJob?.isActive == true)"))
    assertTrue(src.contains("return"))
}
```

- [ ] **Step 2: Add upload policy tests**

Create `WatchReadingUploadPolicyTest.kt`:

```kotlin
package com.example.smart_safety_management.watch.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WatchReadingUploadPolicyTest {
    @Test
    fun firstPpgReadingUpdatesRuntimeButDoesNotRequireServerUpload() {
        val policy = WatchReadingUploadPolicy()

        assertFalse(policy.shouldUpload(JcWearHealthReading(ppgValue = 932), Instant.parse("2026-06-02T04:00:00Z")))
    }

    @Test
    fun healthReadingWithServerSupportedFieldsUploadsAtMostOncePerSecond() {
        val policy = WatchReadingUploadPolicy()

        assertTrue(policy.shouldUpload(JcWearHealthReading(heartRate = 82), Instant.parse("2026-06-02T04:00:00Z")))
        assertFalse(policy.shouldUpload(JcWearHealthReading(heartRate = 83), Instant.parse("2026-06-02T04:00:00.500Z")))
        assertTrue(policy.shouldUpload(JcWearHealthReading(heartRate = 84), Instant.parse("2026-06-02T04:00:01.000Z")))
    }
}
```

- [ ] **Step 3: Run service and upload tests and verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.ble.WatchBleForegroundServiceContractTest" --tests "com.example.smart_safety_management.watch.ble.WatchReadingUploadPolicyTest"
```

Expected: fail because runtime updates, idempotent start guard, and upload policy are absent.

- [ ] **Step 4: Implement upload policy**

Create `WatchReadingUploadPolicy` in `JcWearDeviceRegistrar.kt` or a new file beside it:

```kotlin
class WatchReadingUploadPolicy {
    private var lastUploadAt: Instant? = null

    fun shouldUpload(reading: JcWearHealthReading, now: Instant = Instant.now()): Boolean {
        val hasServerSupportedField = reading.heartRate != null || reading.bodyTemp != null || reading.batteryLevel != null
        if (!hasServerSupportedField) return false
        val previous = lastUploadAt
        if (previous != null && Duration.between(previous, now) < Duration.ofSeconds(1)) return false
        lastUploadAt = now
        return true
    }
}
```

Add imports:

```kotlin
import java.time.Duration
import java.time.Instant
```

- [ ] **Step 5: Seed runtime state from the controller**

In `WatchBleServiceController.configureAndStart`, after `saveConfig(context, config)`:

```kotlin
WatchRuntimeStore.update(
    WatchRuntimeState(
        deviceId = config.deviceId,
        userId = config.userId,
        macAddress = config.macAddress,
        status = WatchRuntimeStatus.CONNECTING,
    ),
)
```

In `stopAndClear`, before starting the stop intent:

```kotlin
WatchRuntimeStore.clear()
```

- [ ] **Step 6: Make service starts idempotent**

In `WatchBleForegroundService.onStartCommand`, replace the `config != activeConfig` branch with:

```kotlin
if (config == activeConfig && monitorJob?.isActive == true) {
    WatchRuntimeStore.mutate {
        it.copy(
            deviceId = config.deviceId,
            userId = config.userId,
            macAddress = config.macAddress,
        )
    }
    return START_STICKY
}
restartMonitoring(config)
return START_STICKY
```

- [ ] **Step 7: Publish runtime state from service monitor and uploads**

In `restartMonitoring`, seed runtime state:

```kotlin
WatchRuntimeStore.update(
    WatchRuntimeState(
        deviceId = config.deviceId,
        userId = config.userId,
        macAddress = config.macAddress,
        status = WatchRuntimeStatus.SCANNING,
    ),
)
```

In `uploadJob`, update runtime before upload and after upload:

```kotlin
val uploadPolicy = WatchReadingUploadPolicy()
uploadJob = serviceScope.launch {
    bridge.healthReadings.collect { reading ->
        val readAt = Instant.now()
        WatchRuntimeStore.mutate {
            it.copy(
                deviceId = config.deviceId,
                userId = config.userId,
                macAddress = config.macAddress,
                status = WatchRuntimeStatus.READING,
                lastReadAt = readAt,
                latestReading = reading,
                lastError = null,
            )
        }
        if (uploadPolicy.shouldUpload(reading, readAt)) {
            runCatching {
                withContext(Dispatchers.IO) {
                    registrar.updateWatchReading(config.userId, config.deviceId, reading)
                }
            }.onSuccess {
                WatchRuntimeStore.mutate { it.copy(status = WatchRuntimeStatus.UPLOADING, lastUploadAt = Instant.now()) }
            }.onFailure { error ->
                WatchRuntimeStore.mutate { it.copy(lastError = error.message) }
                Log.w(TAG, "watch reading upload failed", error)
            }
        }
    }
}
```

Add `import java.time.Instant`.

- [ ] **Step 8: Publish runtime state from monitor loop decisions**

Inside the monitor loop branches:

```kotlin
WatchRuntimeStore.mutate { it.copy(status = WatchRuntimeStatus.CONNECTING, lastError = null) }
bridge.connect(target)
```

Before starting scan:

```kotlin
WatchRuntimeStore.mutate { it.copy(status = WatchRuntimeStatus.SCANNING) }
bridge.startScan()
```

When `bridge.uiState.value.connectionState == JcWearConnectionState.FAILED`:

```kotlin
WatchRuntimeStore.mutate {
    it.copy(status = WatchRuntimeStatus.RETRYING, lastError = bridge.uiState.value.errorMessage)
}
```

- [ ] **Step 9: Clear runtime state on stop**

In `stopWatchMonitoring()`:

```kotlin
activeConfig?.deviceId?.let { WatchRuntimeStore.clear(it) } ?: WatchRuntimeStore.clear()
```

- [ ] **Step 10: Run service and upload tests**

Run the command from Step 3.

Expected: both tests pass.

- [ ] **Step 11: Commit Task 4**

```powershell
git add app/src/main/java/com/example/smart_safety_management/watch/ble/WatchBleForegroundService.kt app/src/main/java/com/example/smart_safety_management/watch/ble/WatchBleServiceController.kt app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearDeviceRegistrar.kt app/src/test/java/com/example/smart_safety_management/watch/ble/WatchBleForegroundServiceContractTest.kt app/src/test/java/com/example/smart_safety_management/watch/ble/WatchReadingUploadPolicyTest.kt
git commit -m "feat(watch): publish service runtime state"
```

## Task 5: Pairing Screen Immediate Runtime Feedback

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt`
- Modify: `app/src/test/java/com/example/smart_safety_management/watch/PairWatchSectionIdentifyUiContractTest.kt`

- [ ] **Step 1: Add pairing-screen contract assertions**

Append to `PairWatchSectionIdentifyUiContractTest.kt`:

```kotlin
@Test
fun registrationUpdatesRuntimeBeforeWaitingForRealtimeEcho() {
    val src = section.readText()

    assertTrue(src.contains("WatchRuntimeStore.update"))
    assertTrue(src.contains("WatchRuntimeStatus.CONNECTING"))
    assertTrue(src.indexOf("WatchRuntimeStore.update") < src.indexOf("WatchBleServiceController.configureAndStart"))
}

@Test
fun pairingUiDoesNotCollectHealthReadingsOrOwnMonitorLoop() {
    val src = section.readText()

    assertFalse(src.contains("bleBridge.healthReadings.collect"))
    assertFalse(src.contains("requestCurrentMeasurements"))
}
```

Add `import org.junit.Assert.assertFalse`.

- [ ] **Step 2: Run the pairing contract and verify it fails**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.PairWatchSectionIdentifyUiContractTest"
```

Expected: fail because `WatchRuntimeStore.update` is not used in the screen.

- [ ] **Step 3: Observe runtime state in the pairing section**

In `PairWatchSection`, import runtime types:

```kotlin
import com.example.smart_safety_management.watch.ble.WatchRuntimeSnapshot
import com.example.smart_safety_management.watch.ble.WatchRuntimeState
import com.example.smart_safety_management.watch.ble.WatchRuntimeStatus
import com.example.smart_safety_management.watch.ble.WatchRuntimeStore
```

Collect runtime state:

```kotlin
val runtime by WatchRuntimeStore.state.collectAsState()
val runtimeSnapshot = WatchRuntimeSnapshot.from(device, null, runtime)
```

- [ ] **Step 4: Update runtime immediately after register success**

In the registration success block, immediately after `device = registered` and before `WatchBleServiceController.configureAndStart`:

```kotlin
WatchRuntimeStore.update(
    WatchRuntimeState(
        deviceId = registered.deviceId,
        userId = userId,
        macAddress = registered.macAddress,
        status = WatchRuntimeStatus.CONNECTING,
    ),
)
```

- [ ] **Step 5: Render runtime status in the registered panel**

Change `RegisteredWatchPanel` signature:

```kotlin
private fun RegisteredWatchPanel(
    device: DeviceRow?,
    runtimeSnapshot: WatchRuntimeSnapshot,
    unpairing: Boolean,
    onUnpair: () -> Unit,
)
```

Inside the panel, replace the static last communication line with:

```kotlin
Text("상태: ${runtimeSnapshot.statusLabel}", color = Color(0xFF4B5563), fontSize = 13.sp)
Text("마지막 통신: ${runtimeSnapshot.lastCommunicationLabel}", color = Color.Gray, fontSize = 12.sp)
```

Pass `runtimeSnapshot = runtimeSnapshot` at the call site.

- [ ] **Step 6: Run the pairing contract test**

Run the command from Step 2.

Expected: pass.

- [ ] **Step 7: Commit Task 5**

```powershell
git add app/src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt app/src/test/java/com/example/smart_safety_management/watch/PairWatchSectionIdentifyUiContractTest.kt
git commit -m "feat(watch): update pairing UI from runtime state"
```

## Task 6: Home And Detail Screens Render Live Runtime State

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/WatchCardComposable.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/WatchMiniCardComposable.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/WatchDetailScreen.kt`
- Create: `app/src/test/java/com/example/smart_safety_management/watch/WatchRuntimeUiContractTest.kt`

- [ ] **Step 1: Add source-contract tests for UI runtime observation**

Create `WatchRuntimeUiContractTest.kt`:

```kotlin
package com.example.smart_safety_management.watch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WatchRuntimeUiContractTest {
    private val card = File("src/main/java/com/example/smart_safety_management/watch/WatchCardComposable.kt")
    private val mini = File("src/main/java/com/example/smart_safety_management/watch/WatchMiniCardComposable.kt")
    private val detail = File("src/main/java/com/example/smart_safety_management/watch/WatchDetailScreen.kt")

    @Test
    fun homeCardsObserveWatchRuntimeStore() {
        val cardSrc = card.readText()
        val miniSrc = mini.readText()

        assertTrue(cardSrc.contains("WatchRuntimeStore.state.collectAsState"))
        assertTrue(cardSrc.contains("WatchRuntimeSnapshot.from"))
        assertTrue(miniSrc.contains("WatchRuntimeStore.state.collectAsState"))
        assertTrue(miniSrc.contains("WatchRuntimeSnapshot.from"))
    }

    @Test
    fun detailScreenUsesRuntimeSnapshotAndTickerForRelativeTimes() {
        val src = detail.readText()

        assertTrue(src.contains("WatchRuntimeStore.state.collectAsState"))
        assertTrue(src.contains("WatchRuntimeSnapshot.from"))
        assertTrue(src.contains("delay(1_000)"))
        assertTrue(src.contains("ppgDisplay"))
        assertTrue(src.contains("lastCommunicationLabel"))
    }
}
```

- [ ] **Step 2: Run the UI contract test and verify it fails**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.WatchRuntimeUiContractTest"
```

Expected: fail because UI files do not observe `WatchRuntimeStore`.

- [ ] **Step 3: Merge runtime snapshot in `WatchCardComposable`**

Add imports:

```kotlin
import com.example.smart_safety_management.watch.ble.WatchRuntimeSnapshot
import com.example.smart_safety_management.watch.ble.WatchRuntimeStore
```

Inside `WatchCardComposable`, after `repo`:

```kotlin
val runtime by WatchRuntimeStore.state.collectAsState()
val runtimeSnapshot = WatchRuntimeSnapshot.from(device, snapshot, runtime)
```

Replace the HR/temp row with runtime-aware values:

```kotlin
Text(runtimeSnapshot.hrDisplay, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
Text(runtimeSnapshot.tempDisplay, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
```

Add PPG and status text below `WearStateLabel(lastWearState)`:

```kotlin
Text(
    "PPG ${runtimeSnapshot.ppgDisplay} · ${runtimeSnapshot.statusLabel}",
    color = if (runtimeSnapshot.isFresh) Color(0xFF16A34A) else Color.Gray,
    fontSize = 13.sp,
)
```

- [ ] **Step 4: Merge runtime snapshot in `WatchMiniCardComposable`**

Add the same imports and collect runtime:

```kotlin
val runtime by WatchRuntimeStore.state.collectAsState()
val runtimeSnapshot = WatchRuntimeSnapshot.from(null, snapshot, runtime)
```

Replace the status line with:

```kotlin
Text(
    runtimeSnapshot.statusLabel,
    color = Color.White,
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    maxLines = 1,
)
```

Use `runtimeSnapshot.hrDisplay` in the top numeric line.

- [ ] **Step 5: Add a ticker and runtime snapshot to `WatchDetailScreen`**

Add imports:

```kotlin
import com.example.smart_safety_management.watch.ble.WatchRuntimeSnapshot
import com.example.smart_safety_management.watch.ble.WatchRuntimeStore
import java.time.Instant
```

Inside `WatchDetailScreen`:

```kotlin
val runtime by WatchRuntimeStore.state.collectAsState()
var now by remember { mutableStateOf(Instant.now()) }

LaunchedEffect(Unit) {
    while (true) {
        now = Instant.now()
        delay(1_000)
    }
}

val runtimeSnapshot = WatchRuntimeSnapshot.from(device, snapshot, runtime, now)
```

- [ ] **Step 6: Render runtime detail fields**

Change `OverallStatusCard` to accept `runtimeSnapshot`:

```kotlin
private fun OverallStatusCard(
    runtimeSnapshot: WatchRuntimeSnapshot,
    color: Color,
    device: DeviceRow?,
)
```

Inside the card:

```kotlin
Text(runtimeSnapshot.statusLabel, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
Text(
    "J2208A · MAC ${device?.macAddress ?: "-"} · 마지막 통신 ${runtimeSnapshot.lastCommunicationLabel}",
    color = Color.White.copy(alpha = 0.9f),
    fontSize = 12.sp,
)
```

Add a PPG metric card before HR:

```kotlin
MetricCard(
    title = "PPG",
    valueText = runtimeSnapshot.ppgDisplay,
    levelColor = if (runtimeSnapshot.isFresh) Color(0xFF16A34A) else Color.Gray,
    statusLabel = runtimeSnapshot.statusLabel,
    rangeText = "B-mode fff2 실시간 읽기",
    valueRatio = null,
    valueColor = if (runtimeSnapshot.isFresh) Color(0xFF16A34A) else Color.Gray,
)
```

Change `DeviceMetaCard` call:

```kotlin
DeviceMetaCard(
    batteryText = runtimeSnapshot.batteryDisplay,
    lastCommunicationLabel = runtimeSnapshot.lastCommunicationLabel,
)
```

Update `DeviceMetaCard` signature and text usage:

```kotlin
private fun DeviceMetaCard(batteryText: String, lastCommunicationLabel: String)
```

- [ ] **Step 7: Run the UI contract test**

Run the command from Step 2.

Expected: pass.

- [ ] **Step 8: Commit Task 6**

```powershell
git add app/src/main/java/com/example/smart_safety_management/watch/WatchCardComposable.kt app/src/main/java/com/example/smart_safety_management/watch/WatchMiniCardComposable.kt app/src/main/java/com/example/smart_safety_management/watch/WatchDetailScreen.kt app/src/test/java/com/example/smart_safety_management/watch/WatchRuntimeUiContractTest.kt
git commit -m "feat(watch): render live runtime state"
```

## Task 7: Full Verification And Manual Device Check

**Files:**
- No source files expected unless verification exposes a defect.

- [ ] **Step 1: Run all watch unit tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.*"
```

Expected: all watch tests pass.

- [ ] **Step 2: Run debug lint**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:lintDebug
```

Expected: no new lint errors in watch files.

- [ ] **Step 3: Build debug APK**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; $env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'; .\gradlew.bat :app:assembleDebug
```

Expected: build succeeds.

- [ ] **Step 4: Manual check on a phone with one J2208A watch**

Use this exact path:

1. Log in with the worker account.
2. Open device management.
3. Tap scan.
4. Tap the bell icon beside a candidate watch and confirm only that watch vibrates.
5. Tap connect.
6. Tap selected watch registration.
7. Confirm the registered panel changes to connecting or connected immediately.
8. Return to the main screen without closing the app.
9. Confirm the watch card shows connected, receiving, or data waiting immediately.
10. Open watch detail.
11. Confirm PPG appears when B-mode reads arrive.
12. Confirm last communication advances every second while the detail screen is visible.
13. Keep the app on another screen for two minutes.
14. Confirm the watch does not vibrate again unless the bell icon is tapped.
15. Confirm Supabase `devices.last_comm_at` updates only when server-supported readings are uploaded.

- [ ] **Step 5: Commit any verification fix**

If a defect is found and fixed during verification:

```powershell
git add app/src/main/java/com/example/smart_safety_management/watch app/src/test/java/com/example/smart_safety_management/watch
git commit -m "fix(watch): stabilize live runtime verification"
```

If no source change is needed, do not create a commit.

## Self-Review Notes

- Spec coverage:
  - One BLE owner: Task 3 and Task 4.
  - B-mode `fff2` command/read loop: Task 1 and Task 3.
  - Runtime state and dynamic UI: Task 2, Task 5, Task 6.
  - No repeated vibration: Task 3 and Task 7 manual check.
  - Upload throttling: Task 4.
  - HR/temp truthfulness when B-mode only emits PPG: Task 2 and Task 6.
- Type consistency:
  - `WatchRuntimeStore.state`, `WatchRuntimeSnapshot.from`, `WatchRuntimeStatus`, and `JcWearHealthReading.ppgValue` are introduced before use.
  - UI tasks consume the snapshot fields defined in Task 2.
- Scope:
  - This plan handles one registered J2208A watch, matching the spec non-goal for multiple simultaneous watches.
