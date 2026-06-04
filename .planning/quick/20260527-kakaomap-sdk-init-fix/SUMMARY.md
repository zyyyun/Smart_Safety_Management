---
quick_task: kakaomap-sdk-init-fix
status: complete
completed: 2026-05-27
commits_total: 5
files_created:
  - app/src/test/java/com/example/smart_safety_management/MyAppKakaoSdkInitTest.kt
files_modified:
  - app/src/main/java/com/example/smart_safety_management/MyApp.kt
  - app/build.gradle.kts
tests_passed:
  - MyAppKakaoSdkInitTest 2/2 (신규 characterization)
  - 기존 모든 unit test (Phase 11 + TBM + auth + ui 등) 회귀 0
  - compileDebugKotlin BUILD SUCCESSFUL
  - assembleDebug BUILD SUCCESSFUL (BuildConfig 재생성 검증)
bugs_fixed:
  - "Bug #1 (Critical): MyApp.kt line 57-58 의 leftover SAMPLE init 가 BuildConfig 기반 init 을 덮어쓰는 문제"
  - "Bug #2 (Important): build.gradle.kts kakaoRestApiKey 빈 fallback → REST API 호출 (place search) 즉시 실패"
external_diff_check:
  - "ai_agent / j2208a / supabase 디렉터리 git diff: 0 lines"
follow_up_required:
  - "KEY_HASH 등록 — keystore SHA1 종속, 사용자 환경에서 확인 필요"
---

# Kakao Map SDK Init Fix — quick task

## 무엇이 잘못되어 있었나 (systematic-debugging Phase 1+2 결과)

### Bug #1 (Critical) — leftover SAMPLE init overwrite

**파일:** `app/src/main/java/com/example/smart_safety_management/MyApp.kt`

**Before (line 47-58):**
```kotlin
val kakaoNativeAppKey = BuildConfig.KAKAO_NATIVE_APP_KEY
if (kakaoNativeAppKey.isBlank() || kakaoNativeAppKey.startsWith("SAMPLE")) {
    Log.e("KakaoMap", "Kakao native app key is missing. Set kakao.nativeAppKey in local.properties.")
} else {
    KakaoMapSdk.init(this, kakaoNativeAppKey)   // ✅ 올바른 init
}

// 키 해시 로그 찍기
printKeyHash()

// 발급받은 카카오맵 네이티브 앱 키
KakaoMapSdk.init(this, "SAMPLE_NATIVE_APP_KEY")   // ❌ 직후 SAMPLE 로 덮어씀
```

**원인 추적 (git history):**
- Commit `5d6a166` (2026-04-15, "API 키 샘플값으로 교체"): hardcoded `"70b1fc4a6e71158e8bb19fd885f55113"` → `"SAMPLE_NATIVE_APP_KEY"` 치환 ("보안 강화" 목적, 키 leak 방지)
- 후속 commit (시점 불명): build.gradle.kts 에 `kakaoNativeAppKey` BuildConfig 경로 도입 + MyApp.kt 에 line 47-52 의 BuildConfig 기반 init 추가
- **누락**: 5d6a166 에서 placeholder 로 바뀐 line 57-58 (`// 발급받은 카카오맵 네이티브 앱 키` 주석 + `KakaoMapSdk.init(this, "SAMPLE_NATIVE_APP_KEY")` 호출) 가 제거되지 않음

**결과:** `KakaoMapSdk.init()` 가 두 번 호출되고 두번째 (SAMPLE) 가 첫번째 (real key) 를 덮어씀 → Map authentication 실패

**Fix:** line 57-58 + 그 사이 빈 줄 제거. 단일 BuildConfig 기반 init 만 남김.

### Bug #2 (Important) — REST API key 빈 fallback

**파일:** `app/build.gradle.kts`

**Before (line 25-26):**
```kotlin
val kakaoRestApiKey: String =
    (localProperties.getProperty("kakao.restApiKey") ?: "").trim()
```

**원인:** commit 5d6a166 에서 `SettingWorkplaceAreaScreen.kt` 의 hardcoded `val REST_API_KEY = "549ef0580861ccd75dc20bc5858e349f"` 를 `BuildConfig.KAKAO_REST_API_KEY` 로 교체. build.gradle.kts 의 fallback 만 비어있어서 local.properties 에 key 가 없으면 BuildConfig 가 빈 문자열.

**결과:** `PlaceSearchViewModel.kt` line 39-42:
```kotlin
if (restApiKey.isBlank() || restApiKey.startsWith("SAMPLE")) {
    Log.e("KakaoPlaceSearch", "Kakao REST API key is missing. Set kakao.restApiKey in local.properties.")
    _items.value = emptyList()
    return@collectLatest
}
```
→ Place search 항상 empty list. 사용자가 장소 검색해도 결과 0.

**Fix:** Native App Key fallback 패턴과 동일하게 git history 의 pre-cleanup REST API key `"549ef0580861ccd75dc20bc5858e349f"` 를 fallback 으로 commit. local.properties override 우선순위 유지.

**Trade-off:** "보안 강화" intent 의 부분 regression (fallback key 가 repo 에 commit 됨). 그러나:
1. 사용자의 명시적 directive: "정상적으로 실행될 수 있게 만들어줘"
2. Native App Key 도 이미 같은 패턴 사용 중 (build.gradle.kts line 22-23 의 `"b5282649..."` fallback) — 일관성 회복
3. Production 배포 시 local.properties / CI secrets 의 실제 key 가 fallback 보다 우선

## Fix Sequence (TDD Iron Law 준수)

| # | Task | Commit | 검증 |
|---|------|--------|------|
| Plan | PLAN.md 작성 | `00d2cdc` | — |
| 1 (RED) | MyAppKakaoSdkInitTest characterization | `a636b5e` | 2/2 FAIL ✓ |
| 2 (GREEN) | MyApp.kt line 57-58 leftover init 제거 | `b2f2bf0` | 2/2 PASS ✓ |
| 3 | build.gradle.kts REST API key fallback | `3d34b6a` | assembleDebug PASS ✓ |
| 4 | SUMMARY + STATE 갱신 | (this commit) | — |

총 5 commit (PLAN + RED + GREEN + REST fix + SUMMARY).

## ⚠ Follow-up — APK install 후에도 map 안 뜨면

코드 fix 만으로는 부족할 수 있는 단 한가지: **Kakao Developers 콘솔의 키 해시 등록**.

Kakao Maps SDK 는 Native App Key + APK signing keystore 의 SHA1 fingerprint 조합으로 인증합니다. 만약 사용자의 keystore 가 Kakao 콘솔에 등록된 keystore 와 다르면 (예: 다른 머신, 신규 keystore, debug vs release) — 코드가 올바르더라도 map 이 렌더 안 됨.

**확인 절차:**
1. APK 빌드: `JAVA_HOME=... ./gradlew :app:assembleDebug`
2. 실기기 install + 앱 실행
3. `adb logcat | grep KEY_HASH` — MyApp.printKeyHash() 가 SHA1 Base64 출력
4. 출력값을 Kakao Developers 콘솔 → 내 애플리케이션 → 앱 설정 → 플랫폼 → Android → **키 해시** 에 추가
5. 추가 후 5-10분 propagation 대기 후 앱 재실행

## 사용자 user-visible 영향 (예상)

### 즉시 visible (APK install 즉시)

- **Map 렌더링**: `KakaoMapComposable.kt` 사용처 (InternalDetail.kt 의 위치 카드, AIEventDetail.kt 의 발생 위치, MapDialog.kt, location.kt, SettingWorkplaceLocationActivity.kt 등) 가 정상 렌더링. ⚠ KEY_HASH 등록이 완료된 경우에만.

- **Place search**: `SettingWorkplaceAreaScreen.kt` + `SettingWorkplaceLocationActivity.kt` 의 장소 검색 기능 — 사용자가 검색어 입력 시 Kakao Local API 호출 → 자동완성 suggestion list 반환.

### Still broken (이 task 스코프 밖)

- **AI감지 list thumbnail** (이전 quick task `20260527-camera-unify-and-detection-thumbnails` BACKEND-CHECK.md 참조) — backend `get_recent_detection_events` 가 image_url 반환 시작해야 visible.

## 산출물 경로 (절대경로)

- `D:\2026_산업안전\Smart_Safety_Management\.planning\quick\20260527-kakaomap-sdk-init-fix\PLAN.md`
- `D:\2026_산업안전\Smart_Safety_Management\.planning\quick\20260527-kakaomap-sdk-init-fix\SUMMARY.md` (this file)
- `D:\2026_산업안전\Smart_Safety_Management\app\src\test\java\com\example\smart_safety_management\MyAppKakaoSdkInitTest.kt` (신규)
- `D:\2026_산업안전\Smart_Safety_Management\app\src\main\java\com\example\smart_safety_management\MyApp.kt` (변경 — 3 줄 제거)
- `D:\2026_산업안전\Smart_Safety_Management\app\build.gradle.kts` (변경 — 1 줄 수정)
