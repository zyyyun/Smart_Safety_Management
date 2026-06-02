# J2208A Watch B-Mode Read Loop Design

Date: 2026-06-02
Status: Ready for user review

## Goal

Make the Android app receive J2208A watch data the same way the stable Python
scripts do in B mode: keep one BLE connection open and repeatedly read the
watch data characteristic at a short interval.

The immediate user-facing goals are:

- Stop repeated connect/disconnect cycles that make the watch vibrate
  unnecessarily.
- Receive watch data continuously while the app is open, on another screen, or
  running in the background.
- Keep identify vibration as an explicit user action only.
- Preserve Supabase watch registration and watch reading upload behavior.

## Reference Behavior

The reference files are `JCWear\ble_sensor.py` and `JCWear\ble_runner.py` in the
2025 JCWear reference folder.

The reference flow is:

1. Connect to the target J2208A MAC address once.
2. Use characteristic `0000fff2-0000-1000-8000-00805f9b34fb`.
3. Write the reset command `[0x2E, 0x00, 0x00] + 13 zero bytes`.
4. Wait 3 seconds.
5. Write the PPG init command `[0x3B, 0x01, 0x01] + 13 zero bytes`.
6. Wait 2 seconds.
7. Write the realtime start command `[0x0B, 0x01, 0x01] + 13 zero bytes`.
8. Loop while connected:
   - read the same `fff2` characteristic,
   - if payload length is at least 3, derive `ppgValue = data[1] << 8 | data[2]`,
   - sleep 100 ms.

This behavior is intentionally read-loop based, not SDK notification based. The
B-mode commands are fixed 16-byte payloads without CRC. They must not be mixed
with the `fff6` write / `fff7` notify command format used by the other Python
receiver script.

Android should choose write-with-response based on the characteristic properties
exposed by the device. If both write modes are available, prefer response for the
three initialization commands and keep reads serialized after each write
completes.

## Current Problem

The current Android implementation can create more than one BLE owner:

- `PairWatchSection` creates a UI `JcWearBleBridge` and can call `startScan`,
  `connect`, `identify`, and `disconnect`.
- `WatchBleForegroundService` creates another `JcWearBleBridge` and starts its
  own scan/connect loop after registration.
- Registration can happen after a UI connection, then service startup can close
  and reopen another connection.
- `JcWearBleBridge.connect()` currently stops scan, disconnects any existing
  GATT, and opens a new GATT. Repeated calls therefore reset the BLE session.
- Realtime database updates can call service start again. Even if the stored
  config is unchanged, this must not cause the active BLE session or read loop to
  restart.

This creates a high chance of connection churn. The watch appears to treat new
connections as noticeable events, causing repeated vibration. It also means the
data stream can be attached to the wrong lifetime: data may arrive only briefly
or stop after the screen/service handoff.

## Proposed Architecture

Use one long-lived BLE owner: `WatchBleForegroundService`.

The UI should not own the active watch connection. It should:

- scan if needed for registration,
- display discovered devices and registered watch state,
- send commands to the service,
- observe service/database state,
- never call the long-lived monitor `connect` path directly.

The foreground service should:

- own the active `BluetoothGatt`,
- own the read loop,
- own reconnect policy,
- expose status for UI,
- upload throttled readings to Supabase.

This gives the app one connection lifetime per registered watch instead of one
connection per screen action.

## Components

### WatchBleForegroundService

Responsibilities:

- Load the registered watch config from `WatchBleServiceController`.
- Connect to the configured MAC address.
- Initialize B mode using the Python command sequence.
- Start the `fff2` read loop.
- Publish readings to Supabase through `JcWearDeviceRegistrar`.
- Reconnect only after a real disconnect or repeated read failures.
- Keep the Android foreground notification active while monitoring.

### JcWearBleBridge

Refactor this away from SDK-notify telemetry into a lower-level session helper.

Responsibilities:

- Connect to a MAC address.
- Discover GATT services.
- Find `0000fff2-0000-1000-8000-00805f9b34fb`.
- Serialize all GATT operations through one command lane so
  `readCharacteristic`, `writeCharacteristic`, identify vibration, and
  disconnect do not overlap.
- Execute B-mode init commands.
- Run the 100 ms read loop.
- Parse B-mode PPG payloads.
- Provide explicit identify vibration command only when requested.

It should not start independent scanning loops once service monitoring is
configured.

### PairWatchSection

Responsibilities:

- Scan and display nearby J2208A candidates for first registration.
- On registration, save the watch and start the service.
- After registration, show service/database status instead of opening its own
  monitoring connection.
- The bell/identify button should request a one-shot identify command through
  the service. It must not trigger a fresh monitor connection if the service is
  already connected.
- Repeated `configureAndStart` calls for the same user, device, and MAC should be
  idempotent from the BLE session's perspective.

### JcWearDeviceRegistrar

Responsibilities stay mostly the same:

- Pair/unpair through Supabase Edge Function.
- Upload watch readings.

Upload policy changes:

- Do not upload every 100 ms read.
- Upload at most once per second, or immediately when a meaningful value changes
  after being absent.

## Data Model

The B-mode read loop produces `JcWearHealthReading` with at least:

- `ppgValue: Int?`
- `heartRate: Int?` remains optional unless a reliable B-mode heart-rate parser
  is proven.
- `bodyTemp: Float?` remains optional unless B-mode payloads expose temperature.
- `batteryLevel: Int?` should come from a slower battery query or existing device
  snapshot, not from every B-mode read unless the payload includes it.

For the first implementation, the app should store and surface B-mode PPG as a
live sensor value while keeping existing heart rate, temperature, and battery
fields nullable when not available from B mode.

## Reconnect Policy

The service should treat connection ownership conservatively:

- Do not disconnect/reconnect on screen entry.
- Do not disconnect/reconnect after successful registration unless the service
  has not started yet.
- Do not reconnect just because Supabase realtime emits a device update.
- Reconnect only when Android reports `STATE_DISCONNECTED`, GATT error status, or
  read failures exceed a small threshold.
- Keep at most one in-flight connect attempt and one active read loop.
- Use backoff such as 3 seconds, then 5 seconds, then 10 seconds.

## Vibration Policy

Automatic monitoring must not send vibration commands.

Vibration is allowed only when:

- the user taps the identify button,
- the service has an active connection or deliberately opens a short identify
  session because there is no monitor session,
- the command is rate-limited so repeated taps cannot create repeated connection
  churn.

## Testing Strategy

Add unit tests for:

- B-mode command sequence bytes: reset, init, realtime start.
- B-mode command sequence does not append CRC and does not use `fff6`/`fff7`.
- B-mode parser: payload length guard and `data[1] << 8 | data[2]`.
- Service ownership contract: registered watch starts service monitoring without
  UI `connect`; the UI must not call the long-lived monitor connect path.
- Identify contract: identify command is user-triggered only.
- Upload throttling: 100 ms reads do not produce 10 Supabase uploads per second.
- Reconnect contract: service does not reconnect when already connected and only
  reconnects after real disconnect/read-failure threshold.
- Idempotent start contract: repeated service starts with the same config do not
  restart the GATT session or read loop.
- Stop contract: unpair/stop clears config, stops the read loop, closes GATT, and
  removes the foreground notification.

Manual verification:

- Pair one watch.
- Keep app on the watch screen for at least 2 minutes.
- Move to another app screen for at least 2 minutes.
- Background the app for at least 2 minutes.
- Confirm readings continue updating approximately once per second in Supabase.
- Confirm the watch does not vibrate except when identify is tapped.

## Non-Goals

- Do not support multiple simultaneous watches in this pass.
- Do not replace the Supabase schema in this pass.
- Do not infer heart rate or body temperature from B-mode PPG unless the payload
  contract is verified.
- Do not keep the old SDK notify telemetry path as the primary monitoring mode.

## Implementation Decision

The implementation should default to B mode. SDK notify mode may remain only as a
fallback or debug-only path if it does not create a second active connection.
