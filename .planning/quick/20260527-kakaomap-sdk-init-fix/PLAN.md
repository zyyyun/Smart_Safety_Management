---
quick_task: kakaomap-sdk-init-fix
created: 2026-05-27
status: in-progress
mode: tdd
files_modified:
  - app/src/main/java/com/example/smart_safety_management/MyApp.kt
  - app/build.gradle.kts
  - app/src/test/java/com/example/smart_safety_management/MyAppKakaoSdkInitTest.kt
must_haves:
  - "MyApp.kt 에 'SAMPLE_NATIVE_APP_KEY' literal 부재"
  - "MyApp.kt 의 KakaoMapSdk.init( 호출이 정확히 1회"
  - "build.gradle.kts kakaoRestApiKey fallback 이 비어있지 않음 (REST API key 가 BuildConfig 에 주입됨)"
  - "compileDebugKotlin + assembleDebug BUILD SUCCESSFUL"
  - "기존 모든 unit test 회귀 0"
  - "Phase 11 TDD Iron Law: failing test commit → fix commit 순서 준수"
---

<objective>
Kakao Map API 가 작동하지 않는 root cause 2건 fix:

**Bug #1 (critical):** MyApp.kt line 56 의 leftover `KakaoMapSdk.init(this, "SAMPLE_NATIVE_APP_KEY")` 가 line 47-52 의 올바른 init 을 직후 덮어씀. commit 5d6a166 ("API 키 샘플값으로 교체") 의 cleanup 미완.

**Bug #2 (important):** build.gradle.kts 의 `kakaoRestApiKey` fallback 이 `""` 빈값 → BuildConfig.KAKAO_REST_API_KEY 가 비어서 PlaceSearchViewModel 의 place search REST 호출이 즉시 empty list 반환.

사용자 명령: "현재 있는 카카오맵 api 정보를 통해 카카오맵 api가 정상적으로 실행될 수 있게 만들어줘" — git history (pre-5d6a166 commit) 의 hardcoded REST API key `549ef0580861ccd75dc20bc5858e349f` 가 "현재 있는 정보". Native App Key 는 build.gradle.kts 의 현재 fallback `b5282649bc815793990d92669375ea72` (cleanup 후 변경된 key — older `70b1...` 와 다름, post-cleanup 의도된 신규 key) 사용.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@C:/Users/ANNA/.claude/plugins/cache/claude-plugins-official/superpowers/5.1.0/skills/test-driven-development/SKILL.md
@C:/Users/ANNA/.claude/plugins/cache/claude-plugins-official/superpowers/5.1.0/skills/systematic-debugging/SKILL.md
</execution_context>

<diagnosis_evidence>
- Pre-cleanup MyApp.kt (5d6a166^): 단일 `KakaoMapSdk.init(this, "70b1fc4a6e71158e8bb19fd885f55113")` 호출만 존재 (정상 작동했던 상태)
- Commit 5d6a166 (2026-04-15) "API 키 샘플값으로 교체": `"70b1..."` → `"SAMPLE_NATIVE_APP_KEY"` 치환 (보안 강화 목적)
- 후속 commit (확실하지 않은 시점): build.gradle.kts 에 kakaoNativeAppKey BuildConfig 경로 + 신규 fallback `"b5282..."` 도입, MyApp.kt 에 line 47-52 의 BuildConfig 기반 init 추가
- **누락**: line 55-56 (`// 발급받은 카카오맵 네이티브 앱 키` 주석 + `KakaoMapSdk.init(this, "SAMPLE_NATIVE_APP_KEY")` 호출) 가 제거되지 않음 → 두번째 init 이 첫번째를 덮어씀

REST API key 동일 패턴: SettingWorkplaceAreaScreen.kt 의 hardcoded `"549ef0580861ccd75dc20bc5858e349f"` → BuildConfig.KAKAO_REST_API_KEY 치환, build.gradle.kts fallback 비어있음.
</diagnosis_evidence>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Characterization test RED → MyApp.kt SDK init invariant</name>
  <files>
    app/src/test/java/com/example/smart_safety_management/MyAppKakaoSdkInitTest.kt
  </files>
  <behavior>
    JVM JUnit4 characterization test 2 assertions:
    1. MyApp.kt source 에 `"SAMPLE_NATIVE_APP_KEY"` literal 부재 (cleanup commit 의 의도)
    2. MyApp.kt 의 `KakaoMapSdk.init(` 호출 회수 == 1 (덮어쓰기 방지)

    이 test 가 source file invariant 를 verify — 향후 refactor 도 회귀 가드.
  </behavior>
  <red_test_first>
    Test file path: `app/src/test/java/com/example/smart_safety_management/MyAppKakaoSdkInitTest.kt`

    Failing assertions (현재 source 가 위 invariant 위반 — 둘 다 fail):
    ```kotlin
    package com.example.smart_safety_management

    import org.junit.Assert.assertEquals
    import org.junit.Assert.assertFalse
    import org.junit.Test
    import java.io.File

    /**
     * Phase 11 후속 / quick-kakaomap-sdk-init-fix —
     * MyApp.kt 가 KakaoMapSdk.init() 을 정확히 1회만 호출하고,
     * SAMPLE 키로 overwrite 하지 않는지 source-level 회귀 가드.
     *
     * commit 5d6a166 의 cleanup 이 line 56 의 leftover init 을 제거하지 않은 bug
     * 가 재발하지 않도록 보장.
     */
    class MyAppKakaoSdkInitTest {
        private val file = File("src/main/java/com/example/smart_safety_management/MyApp.kt")

        @Test fun myApp_doesNotContainSampleNativeAppKeyLiteral() {
            assertFalse(
                "MyApp.kt 에 'SAMPLE_NATIVE_APP_KEY' literal 발견 — leftover init (5d6a166 cleanup 미완) 가 실제 key 를 덮어씀",
                file.readText().contains("\"SAMPLE_NATIVE_APP_KEY\"")
            )
        }

        @Test fun myApp_callsKakaoMapSdkInitExactlyOnce() {
            val initCallCount = file.readText().split("KakaoMapSdk.init(").size - 1
            assertEquals(
                "MyApp.kt 의 KakaoMapSdk.init( 호출 수 = ${initCallCount}, 기대 = 1 (이중 init 시 후자가 전자 덮어씀)",
                1,
                initCallCount
            )
        }
    }
    ```

    Expected RED: 2 assertion 모두 fail (현재 source 가 invariant 위반).
  </red_test_first>
  <action>
    1단계 (RED): test 작성 → `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "*MyAppKakaoSdkInitTest"` → 2 assertion 모두 FAIL 확인 → commit `test(quick-kakaomap-fix): RED add MyAppKakaoSdkInitTest characterization`.

    2단계 는 Task 2 의 책임 (별도 atomic commit).
  </action>
  <verify>
    JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "*MyAppKakaoSdkInitTest" || echo "expected: FAIL"
  </verify>
  <acceptance_criteria>
    - File exists: `app/src/test/java/com/example/smart_safety_management/MyAppKakaoSdkInitTest.kt`
    - gradle 명령 exit code 비-0 (RED 확인)
    - test result XML 에 "MyApp.kt 에 'SAMPLE_NATIVE_APP_KEY' literal 발견" 및 "KakaoMapSdk.init( 호출 수 = 2" message 포함
    - 1 commit (RED)
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 2: MyApp.kt 의 leftover SAMPLE init 제거 (Bug #1 GREEN)</name>
  <files>
    app/src/main/java/com/example/smart_safety_management/MyApp.kt
  </files>
  <behavior>
    Line 55-56 의 다음 2 줄 + 빈 줄 (line 54) 제거:
    ```
            // 발급받은 카카오맵 네이티브 앱 키
            KakaoMapSdk.init(this, "SAMPLE_NATIVE_APP_KEY")
    ```

    Edit tool 사용 — 정확히 위 2 줄만 (주변 line `printKeyHash()` 호출 보존, line 47-52 의 if/else BuildConfig init 보존).

    제거 후 MyApp.kt 의 onCreate() 흐름:
    1. super.onCreate()
    2. BuildConfig.KAKAO_NATIVE_APP_KEY 로딩
    3. blank/SAMPLE check → KakaoMapSdk.init(this, kakaoNativeAppKey) (단 1회)
    4. printKeyHash()
    5. Supabase warm-up
  </behavior>
  <action>
    Edit MyApp.kt: 두 줄 (`// 발급받은 카카오맵 네이티브 앱 키` + `KakaoMapSdk.init(this, "SAMPLE_NATIVE_APP_KEY")`) 제거. 그 위의 빈 줄도 함께 정리해서 printKeyHash() 다음에 곧바로 Supabase warm-up 가도록.

    그 후 gradle 명령으로 Task 1 의 test 재실행 → 2 assertion 모두 PASS 확인.

    추가 회귀 가드:
    - JAVA_HOME=... ./gradlew :app:compileDebugKotlin → BUILD SUCCESSFUL (MyApp.kt 의 다른 부분 회귀 없음)

    commit `fix(quick-kakaomap-fix): GREEN remove leftover SAMPLE KakaoMapSdk.init overwrite (Bug #1)`
  </action>
  <verify>
    JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "*MyAppKakaoSdkInitTest" && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin
  </verify>
  <acceptance_criteria>
    - grep `"SAMPLE_NATIVE_APP_KEY"` in MyApp.kt → 0 hits
    - grep `KakaoMapSdk.init(` in MyApp.kt → 1 hit
    - MyAppKakaoSdkInitTest 2/2 PASS
    - compileDebugKotlin BUILD SUCCESSFUL
    - 1 commit (GREEN)
  </acceptance_criteria>
</task>

<task type="auto" tdd="false">
  <name>Task 3: build.gradle.kts 의 REST API key fallback 추가 (Bug #2)</name>
  <files>
    app/build.gradle.kts
  </files>
  <behavior>
    build.gradle.kts line 25-26 의 빈 fallback 을 git history pre-5d6a166 의 작동했던 REST API key 로 교체:

    Before:
    ```kotlin
    val kakaoRestApiKey: String =
        (localProperties.getProperty("kakao.restApiKey") ?: "").trim()
    ```

    After:
    ```kotlin
    val kakaoRestApiKey: String =
        (localProperties.getProperty("kakao.restApiKey") ?: "549ef0580861ccd75dc20bc5858e349f").trim()
    ```

    이 패턴이 Native App Key 의 처리 (line 21-22) 와 일관 — local.properties 우선, fallback 으로 working key 사용. "보안 강화" 일부 regression (fallback key 가 git 에 노출) 이지만 사용자의 "정상 실행" 우선 명령 + Native App Key 도 이미 같은 패턴 사용 중인 일관성 + git history (5d6a166^) 이미 hardcoded 였던 점에서 receivable trade-off.
  </behavior>
  <red_test_first>
    Test 안 만듦 — build.gradle.kts 자체에 unit test 만들기 어렵고, BuildConfig.KAKAO_REST_API_KEY 의 값은 build-time 상수라 runtime test 도 environmental.

    대안 verification: Task 2 의 test 가 이미 SDK init invariant 보장. REST key 는 build 가 성공하면 BuildConfig 에 주입됨 (compile 가드).
  </red_test_first>
  <action>
    Edit build.gradle.kts line 25-26 — `?: ""` → `?: "549ef0580861ccd75dc20bc5858e349f"`.

    gradle clean + assembleDebug 로 BuildConfig 재생성 확인:
    - JAVA_HOME=... ./gradlew :app:assembleDebug → BUILD SUCCESSFUL

    commit `fix(quick-kakaomap-fix): add REST API key fallback in build.gradle.kts (Bug #2)`
  </action>
  <verify>
    JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
  </verify>
  <acceptance_criteria>
    - grep `549ef0580861ccd75dc20bc5858e349f` in build.gradle.kts → 1 hit
    - assembleDebug BUILD SUCCESSFUL
    - 1 commit
  </acceptance_criteria>
</task>

<task type="auto" tdd="false">
  <name>Task 4: 종결 — SUMMARY 작성 + STATE.md Quick Tasks 표 row 추가</name>
  <files>
    .planning/quick/20260527-kakaomap-sdk-init-fix/SUMMARY.md,
    .planning/STATE.md
  </files>
  <action>
    1. 최종 회귀 게이트:
       - JAVA_HOME=... ./gradlew :app:testDebugUnitTest → all PASS (신규 2 + Phase 11 + tbm 등 전부)
       - JAVA_HOME=... ./gradlew :app:assembleDebug → BUILD SUCCESSFUL
       - git diff ai_agent/ j2208a/ → 0 lines

    2. SUMMARY.md (frontmatter status: complete) — **KEY_HASH follow-up 안내 필수**:
       - 코드 fix 후에도 map 이 안 뜨면 Kakao Developers 콘솔에 키 해시 등록 필요
       - logcat 에서 `KEY_HASH` tag 값 추출 → Kakao 콘솔 → 앱 설정 → 플랫폼 → Android → 키 해시
       - 이는 keystore 의 SHA1 종속 — 다른 머신/keystore 에서는 다른 해시 필요

    3. STATE.md Quick Tasks 표에 row 추가:
       ```
       | 20260527-kakaomap-sdk-init-fix | 2026-05-27 | complete | KakaoMapSdk leftover SAMPLE init 제거 + REST API key fallback (사용자 보고) |
       ```

    4. 1 commit: `docs(quick-kakaomap-fix): SUMMARY + STATE 갱신 — Bug #1+#2 완료 + KEY_HASH follow-up 명시`
  </action>
  <acceptance_criteria>
    - SUMMARY.md exists with status: complete
    - SUMMARY 본문에 KEY_HASH follow-up 단계 포함
    - STATE.md row +1
    - 모든 회귀 gate PASS
    - 1 commit
  </acceptance_criteria>
</task>

</tasks>

<success_criteria>
1. 4 task 모두 완료
2. 신규 1 test 파일 RED (Task 1) → GREEN (Task 2)
3. MyApp.kt 의 KakaoMapSdk.init( 호출 == 1
4. SAMPLE_NATIVE_APP_KEY literal 부재
5. build.gradle.kts kakaoRestApiKey fallback 비어있지 않음
6. compileDebugKotlin + assembleDebug BUILD SUCCESSFUL
7. 기존 모든 unit test 회귀 0 (Phase 11 + TBM + watch + auth 등)
8. ai_agent / j2208a / supabase 디렉터리 git diff 0
9. SUMMARY 에 KEY_HASH 등록 follow-up 단계 명시 (환경 의존, code fix 만으로 해결 안 될 수 있음)
</success_criteria>
