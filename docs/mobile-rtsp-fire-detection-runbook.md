# Mobile RTSP Fire Detection Runbook

This runbook covers the Android on-device fire detection PoC for Drift RTSP streams. It does not replace the PC `ai_agent` path.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\ANNA\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

## Model Asset

The app expects these Android assets:

- `app/src/main/assets/mobile_fire_labels.txt`
- `app/src/main/assets/mobile_fire.tflite`
- `app/src/main/assets/mobile_fire_model_contract.json`

Generate the TFLite model from the existing fire weights:

```powershell
python scripts/export_mobile_fire_tflite.py
```

If the `.tflite` asset is missing, the mobile detection path must show an error state instead of crashing. The PC `ai_agent` path remains the fallback.

## Supabase Deploy

Deploy the mobile event endpoint. Also deploy or update the existing `auth`
function so the `login` action returns `access_token` or `auth_token`.

```powershell
supabase functions deploy mobile-ai-event
```

The endpoint requires a real authenticated user bearer token. The app stores
`access_token` or `auth_token` from the login response and sends it only for the
mobile fire upload call. Requests that only carry the public Supabase anon key
must be rejected before any JPEG decode, Storage upload, or event creation.

The function creates the same AI event contract used by the existing AI Event UI and stores detected frames under:

```text
camera-captures/detection/{cameraId}/fire_{cameraId}_{timestamp}_*.jpg
```

## Manual PoC

1. Install the debug APK on the Android test phone.
2. Log in as a manager account that has access to the target camera group.
   The login response must include `access_token` or `auth_token`.
3. Open `AI감지` once to confirm the current event list loads.
4. Open `실시간상황`.
5. Tap the Drift RTSP camera.
6. Confirm the live panel shows `모바일 감지 준비 중`, then `모바일 감지 실행 중`.
7. Present an approved fire reference target in front of the Drift camera.
8. Wait up to 10 seconds.
9. Confirm the badge changes to `화재 감지`.
10. Confirm a new Storage object exists under `camera-captures/detection/{cameraId}/fire_...jpg`.
11. Confirm `AI감지` shows a new fire event using the uploaded image.
12. Keep the RTSP detail screen open for 3-5 minutes and confirm repeated duplicate events are not created during cooldown.

## Rollback

If the Android PoC misbehaves during a demo, use the PC `ai_agent` path as the stable fallback. For an APK rollback, use a build before `RtspMobileDetectionPlayer` was wired into the RTSP detail screen.
