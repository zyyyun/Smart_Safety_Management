# JCWear Direct BLE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace manual watch MAC registration with direct JCWear/J2208A BLE scan, connect, and phone-side data collection.

**Architecture:** The Android app owns the watch connection. A thin `watch/ble` layer wraps the JCWear SDK and exposes scan/connect/data states to Compose, while the existing watch repository continues to read Supabase rows for dashboards.

**Tech Stack:** Android Kotlin, Jetpack Compose, Supabase PostgREST/Realtime, JCWear 2208A SDK jar, Android BLE permissions.

---

## File Structure

- `app/libs/2208asdk2.0.jar`: JCWear SDK jar copied from the reference SDK package.
- `app/build.gradle.kts`: Add the local SDK jar dependency.
- `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearScanModels.kt`: Pure Kotlin scan status and display models.
- `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearBleBridge.kt`: Android BLE scanner/connector boundary. Uses JCWear SDK only behind this interface.
- `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearDeviceRegistrar.kt`: Creates or updates the Supabase `devices` row for the selected watch.
- `app/src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt`: Replace manual MAC form with scan, device list, connect/register actions, and disconnect state.
- `app/src/test/java/com/example/smart_safety_management/watch/ble/JcWearScanModelsTest.kt`: Unit tests for scan status and device display behavior.

## Task 1: Scan Model Contract

**Files:**
- Create: `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearScanModels.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/watch/ble/JcWearScanModelsTest.kt`

- [ ] **Step 1: Write failing tests**

Test desired behavior:
- Empty BLE name renders as `Unknown J2208A`.
- RSSI is rendered as `-62 dBm`.
- Scan button label changes by scanning/permission state.
- A discovered device can be mapped into a stable registration identifier.

- [ ] **Step 2: Run the specific test and verify it fails**

Run:
`$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.ble.JcWearScanModelsTest"`

Expected: FAIL because the model file does not exist.

- [ ] **Step 3: Implement model functions**

Create small pure Kotlin data classes and functions only.

- [ ] **Step 4: Run the test and verify it passes**

Run the same command. Expected: PASS.

## Task 2: SDK Dependency And Bridge Boundary

**Files:**
- Create: `app/libs/2208asdk2.0.jar`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearBleBridge.kt`

- [ ] **Step 1: Copy the SDK jar into `app/libs`**

Source:
`D:\2025_산업안전\산업안전\JCWear\J-Style 2208A Smart Health Bracelet SDK_v2_20231225\2208a\2208aSdk\2208asdk2.0.jar`

- [ ] **Step 2: Add Gradle dependency**

Add `implementation(files("libs/2208asdk2.0.jar"))`.

- [ ] **Step 3: Create bridge skeleton**

Expose `scan()`, `stopScan()`, `connect()`, `disconnect()`, `connectionState`, and `discoveredDevices`.

- [ ] **Step 4: Build**

Run:
`$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug`

Expected: build succeeds or reveals the next exact SDK compatibility error.

## Task 3: Supabase Registration Boundary

**Files:**
- Create: `app/src/main/java/com/example/smart_safety_management/watch/ble/JcWearDeviceRegistrar.kt`
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/WatchModels.kt`

- [ ] **Step 1: Add registration payload model if needed**

Use the existing `DeviceRow` shape where possible.

- [ ] **Step 2: Upsert selected watch**

Upsert `devices` with `device_type = WATCH`, `user_id`, `serial_number`, `mac_address`, `battery_level`, and `last_comm_at`.

- [ ] **Step 3: Keep Edge Function pairing path out of the new UI**

The old `watch-pair` function remains for compatibility but is no longer the primary path.

## Task 4: PairWatchSection UI Replacement

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt`

- [ ] **Step 1: Remove manual MAC text field from the default registration path**

The user sees scan and discovered devices instead.

- [ ] **Step 2: Add scan permission and empty states**

States: no permission, Bluetooth off, scanning, no devices found, devices found, registering, connected.

- [ ] **Step 3: Register selected device**

Selecting a device connects, confirms readable watch info when available, and upserts the device row.

- [ ] **Step 4: Keep existing status badge semantics**

Connected is still derived from recent `last_comm_at`.

## Task 5: Verification

**Files:**
- Run only; no code changes expected.

- [ ] **Step 1: Unit tests**

Run:
`$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.smart_safety_management.watch.ble.*"`

- [ ] **Step 2: Debug build**

Run:
`$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug`

- [ ] **Step 3: Manual device test**

On an Android device:
1. Log in as worker.
2. Open device/watch settings.
3. Grant Bluetooth permission.
4. Start scan.
5. Select the J2208A watch.
6. Confirm a `devices` row appears and status updates after data collection.
