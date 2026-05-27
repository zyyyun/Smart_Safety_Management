---
quick_task: camera-unify-and-detection-thumbnails
created: 2026-05-27
status: in-progress
mode: tdd
files_modified:
  # Issue 1 — 전경/현장 single-camera collapse
  - app/src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt
  - app/src/test/java/com/example/smart_safety_management/screens/detail/CctvSplitVisibilityTest.kt
  # Issue 2A — AI감지 list 이미지 (client DTO + UI)
  - app/src/main/java/com/example/smart_safety_management/SignUpService.kt
  - app/src/main/java/com/example/smart_safety_management/AIEventDetect.kt
  - app/src/test/java/com/example/smart_safety_management/EventDataMappingTest.kt
  # Issue 2B — backend 검증 (필요시 수정)
  - supabase/functions/_shared/ai_events.ts (verify only — not necessarily modify)
must_haves:
  - "InternalDetail.kt: 같은 카메라(liveUrlDetail==null 또는 ==liveUrl 또는 공백) 일 때 '현장' SmartPreviewCard 섹션이 렌더되지 않음"
  - "InternalDetail.kt: liveUrlDetail 이 별도 URL 일 때는 기존처럼 두 섹션 모두 표시 (회귀 가드)"
  - "DetectionEventDTO.imageUrl 필드 추가 + @SerializedName(\"image_url\")"
  - "EventData.imageUrl 필드 추가 (nullable)"
  - "toEventData() 매핑이 imageUrl 을 propagate"
  - "EventItem Composable 의 Row 시작 위치에 thumbnail (AsyncImage 64dp) 표시, imageUrl 이 null/blank 면 기존 icon-only fallback"
  - "기존 6개 화면 unit test + ai_agent 28/28 + j2208a 43/43 회귀 0"
  - "assembleDebug BUILD SUCCESSFUL"
---

<objective>
사용자 보고 2건 동시 수정:

**Issue 1 (전경/현장 강제 split):** InternalDetail.kt 가 같은 카메라일 때도 '전경' + '현장' 두 SmartPreviewCard 를 무조건 렌더하는 문제 — 같은 카메라(URL 동일 또는 liveUrlDetail null) 면 '현장' 섹션 auto-collapse.

**Issue 2 (AI감지/알림 이미지 미표시):** DetectionEventDTO 에 image_url 필드가 없어서 client 가 서버 응답을 파싱조차 못함. EventData UI 모델도 imageUrl 부재 → 리스트에 썸네일 0건. DTO + UI 모델 + EventItem Composable 모두 통합 수정. backend 응답 실제 image_url 반환 여부도 검증.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@C:/Users/ANNA/.claude/plugins/cache/claude-plugins-official/superpowers/5.1.0/skills/test-driven-development/SKILL.md
</execution_context>

<cross_cutting_truths>
- **TDD Iron Law (사용자 directive 2026-05-27)**: 모든 production 변경은 RED test 가 먼저 commit → GREEN 으로 전환. 무예외.
- **JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"** 필수 (따옴표).
- **빌드 output**: D:/ssm-app-build (변경 금지).
- **ai_agent + j2208a + supabase 디렉터리 변경 최소화** — Issue 2B 검증 시 backend 변경이 필요하면 별도 supabase commit, 안 필요하면 0 lines.
- **Compose return@ 신규 코드 0건**.
- **회귀 가드**: tbm 5 test + Phase 11 신규 11 test 모두 GREEN 유지.
</cross_cutting_truths>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Pure helper shouldShowSiteSection() RED→GREEN (Issue 1)</name>
  <files>
    app/src/test/java/com/example/smart_safety_management/screens/detail/CctvSplitVisibilityTest.kt,
    app/src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt
  </files>
  <behavior>
    Pure top-level function: `fun shouldShowSiteSection(overviewUrl: String?, siteUrl: String?): Boolean` —
    - siteUrl == null → false
    - siteUrl.isBlank() → false
    - siteUrl == overviewUrl → false (같은 카메라)
    - 그 외 (다른 URL) → true
  </behavior>
  <red_test_first>
    `app/src/test/java/com/example/smart_safety_management/screens/detail/CctvSplitVisibilityTest.kt`:
    ```kotlin
    package com.example.smart_safety_management.screens.detail

    import org.junit.Assert.assertFalse
    import org.junit.Assert.assertTrue
    import org.junit.Test

    class CctvSplitVisibilityTest {
        @Test fun nullSiteUrl_hidesSiteSection() {
            assertFalse(shouldShowSiteSection("rtsp://a", null))
        }
        @Test fun blankSiteUrl_hidesSiteSection() {
            assertFalse(shouldShowSiteSection("rtsp://a", ""))
            assertFalse(shouldShowSiteSection("rtsp://a", "   "))
        }
        @Test fun sameAsOverview_hidesSiteSection() {
            assertFalse(shouldShowSiteSection("rtsp://192.168.0.13/live", "rtsp://192.168.0.13/live"))
        }
        @Test fun differentUrl_showsSiteSection() {
            assertTrue(shouldShowSiteSection("rtsp://a/live", "rtsp://b/live"))
        }
        @Test fun overviewNullSiteSet_stillShows() {
            // overview 없고 site 만 있으면 site 표시 (edge case)
            assertTrue(shouldShowSiteSection(null, "rtsp://b/live"))
        }
        @Test fun bothNull_hides() {
            assertFalse(shouldShowSiteSection(null, null))
        }
    }
    ```
    Expected RED: `unresolved reference: shouldShowSiteSection`.
  </red_test_first>
  <action>
    1단계 (RED): test 작성 → gradle → unresolved reference 확인 → commit `test(quick-camera-thumb): RED add CctvSplitVisibilityTest`.

    2단계 (GREEN): InternalDetail.kt 의 InternalDetailScreen 함수 위쪽에 top-level fun 추가:
    ```kotlin
    /**
     * 같은 카메라(전경==현장)일 때 '현장' 섹션 중복 렌더 방지.
     * - siteUrl null/blank/== overviewUrl → false (현장 섹션 숨김)
     * - 그 외 → true
     */
    internal fun shouldShowSiteSection(overviewUrl: String?, siteUrl: String?): Boolean {
        val s = siteUrl?.trim().orEmpty()
        if (s.isEmpty()) return false
        return s != overviewUrl?.trim().orEmpty()
    }
    ```
    gradle 재실행 → 7 assertions PASS → commit `feat(quick-camera-thumb): GREEN add shouldShowSiteSection helper`.
  </action>
  <verify>
    JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "*CctvSplitVisibilityTest"
  </verify>
  <acceptance_criteria>
    - grep `fun shouldShowSiteSection` in InternalDetail.kt → 1 hit
    - 7 assertions PASS
    - 2 commits: RED + GREEN
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 2: InternalDetail.kt 의 '현장' 섹션 conditional rendering (Issue 1 wiring)</name>
  <files>
    app/src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt
  </files>
  <behavior>
    Line 260-280 의 "현장" Text + SmartPreviewCard 를 if (shouldShowSiteSection(finalOverviewUrl, finalSiteUrl)) { ... } 으로 감싼다.
    Spacer(Modifier.height(24.dp)) before 현장 도 if 안으로 이동 (중복 spacer 방지).

    추가: '전경' 라벨도 if (shouldShowSiteSection(...)) "전경" else "실시간 화면" 으로 조건부 — 단일 카메라 때 '전경' 라벨이 어색해질 수 있어 더 자연스러운 라벨로.
  </behavior>
  <red_test_first>
    Task 1 의 test 가 이 task 의 회귀 가드 역할. 추가 test 안 만들고 grep + visual 가드만 사용.
  </red_test_first>
  <action>
    InternalDetail.kt line 258-280 영역을 다음으로 교체:
    ```kotlin
    Spacer(Modifier.height(16.dp))

    val showSite = shouldShowSiteSection(finalOverviewUrl, finalSiteUrl)
    val overviewLabel = if (showSite) "전경" else "실시간 화면"

    // 위쪽 overview Text/Card 의 label 도 동적으로 변경 (line 236-256)
    // → InternalDetail 안에서 label = overviewLabel 로 변경

    if (showSite) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "현장",
            ... // 기존 그대로
        )
        Spacer(Modifier.height(16.dp))
        SmartPreviewCard(
            imageRes = R.drawable.bbb,
            ... // 기존 그대로
            label = "현장"
        )
    }
    ```
    실제 적용 시 line 236-280 통째로 읽어서 정확한 indent 보존 + 두 SmartPreviewCard 의 label/text 가 overviewLabel 변수 사용하도록 wiring.

    commit `feat(quick-camera-thumb): InternalDetail 현장 섹션 conditional rendering — 같은 카메라 시 1개만 표시`.

    회귀 가드:
    - JAVA_HOME=... ./gradlew :app:compileDebugKotlin → BUILD SUCCESSFUL
    - JAVA_HOME=... ./gradlew :app:testDebugUnitTest --tests "*CctvSplitVisibilityTest" → 7 PASS
    - assembleDebug → BUILD SUCCESSFUL (XML/resource 영향 없음)
  </action>
  <acceptance_criteria>
    - grep `if (showSite)` (or `if(showSite)`) in InternalDetail.kt → 1 hit
    - grep `shouldShowSiteSection(finalOverviewUrl, finalSiteUrl)` in InternalDetail.kt → 1 hit
    - 기존 InternalDetail 의 SmartPreviewCard 호출 2번 모두 보존 (다른 URL 때 회귀 가드)
    - 1 commit (refactor, RED test 는 Task 1 의 것 재사용)
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 3: DetectionEventDTO + EventData + toEventData() 에 imageUrl 추가 (Issue 2A core)</name>
  <files>
    app/src/test/java/com/example/smart_safety_management/EventDataMappingTest.kt,
    app/src/main/java/com/example/smart_safety_management/SignUpService.kt,
    app/src/main/java/com/example/smart_safety_management/AIEventDetect.kt
  </files>
  <behavior>
    1. DetectionEventDTO 에 `@SerializedName("image_url") val imageUrl: String? = null` 필드 추가
    2. EventData 에 `val imageUrl: String? = null` 필드 추가
    3. toEventData() 매핑이 `imageUrl = this.imageUrl` propagate
  </behavior>
  <red_test_first>
    `app/src/test/java/com/example/smart_safety_management/EventDataMappingTest.kt`:
    ```kotlin
    package com.example.smart_safety_management

    import org.junit.Assert.assertEquals
    import org.junit.Assert.assertNull
    import org.junit.Test

    class EventDataMappingTest {
        @Test fun dtoWithImageUrl_propagatesToEventData() {
            val dto = DetectionEventDTO(
                eventId = 42,
                riskLevel = "DANGER",
                installArea = "장소",
                eventName = "person",
                detectedAt = "2026-05-27 10:00:00",
                deviceName = "cam01",
                accuracy = 0.92,
                status = "PENDING",
                workerName = null,
                actionTime = null,
                imageUrl = "https://example.com/capture.jpg"
            )
            val ed = dto.toEventData()
            assertEquals("https://example.com/capture.jpg", ed.imageUrl)
        }

        @Test fun dtoWithNullImageUrl_eventDataImageUrlIsNull() {
            val dto = DetectionEventDTO(
                eventId = 1,
                riskLevel = "WARNING",
                installArea = null,
                eventName = "smoke",
                detectedAt = "2026-05-27 10:00:00",
                deviceName = null,
                accuracy = null,
                status = "PENDING",
                workerName = null,
                actionTime = null,
                imageUrl = null
            )
            assertNull(dto.toEventData().imageUrl)
        }

        @Test fun dtoWithoutImageUrlArg_defaultsToNull() {
            // backward compat: 기존 호출 (image_url 없이 생성) 도 null 로 정상 작동
            val dto = DetectionEventDTO(
                eventId = 1, riskLevel = "WARNING", installArea = null,
                eventName = "smoke", detectedAt = "2026-05-27 10:00:00",
                deviceName = null, accuracy = null, status = "PENDING",
                workerName = null, actionTime = null
            )
            assertNull(dto.imageUrl)
            assertNull(dto.toEventData().imageUrl)
        }
    }
    ```
    Expected RED: `error: no value passed for parameter 'imageUrl'` 또는 `unresolved reference: imageUrl` (EventData/DetectionEventDTO 에 필드 부재).
  </red_test_first>
  <action>
    1단계 (RED): test 작성 → gradle → compile error → commit `test(quick-camera-thumb): RED add EventDataMappingTest for imageUrl propagation`.

    2단계 (GREEN):

    SignUpService.kt 의 DetectionEventDTO 정의 끝에 추가 (마지막 actionTime 필드 다음):
    ```kotlin
    data class DetectionEventDTO(
        @SerializedName("event_id") val eventId: Int,
        @SerializedName("risk_level") val riskLevel: String?,
        @SerializedName("install_area") val installArea: String?,
        @SerializedName("event_name") val eventName: String?,
        @SerializedName("detected_at") val detectedAt: String,
        @SerializedName("device_name") val deviceName: String?,
        val accuracy: Double?,
        val status: String,
        @SerializedName("worker_name") val workerName: String?,
        @SerializedName("completed_at") val actionTime: String?,
        @SerializedName("image_url") val imageUrl: String? = null   // 신규
    )
    ```

    AIEventDetect.kt 의 EventData data class 에 추가:
    ```kotlin
    data class EventData(
        val id: Int,
        val accidentType: String,
        val location: String,
        val content: String,
        val occurrenceTime: String = "",
        val deviceName: String = "",
        val accuracy: String = "",
        val imageUrl: String? = null   // 신규
    )
    ```

    AIEventDetect.kt 의 toEventData() mapping 에 추가:
    ```kotlin
    fun DetectionEventDTO.toEventData(formatter: SimpleDateFormat? = null): EventData {
        return EventData(
            id = this.eventId,
            accidentType = mapRiskLevel(this.riskLevel),
            location = this.installArea ?: "알 수 없음",
            content = "${this.eventName ?: "알 수 없는 이벤트"}이(가) 감지되었습니다.",
            occurrenceTime = calculateTimeAgo(this.detectedAt, formatter),
            deviceName = this.deviceName ?: "",
            accuracy = "${this.accuracy ?: 0}%",
            imageUrl = this.imageUrl    // 신규
        )
    }
    ```

    gradle 재실행 → 3 assertions PASS → commit `feat(quick-camera-thumb): GREEN add imageUrl to DTO/EventData/mapping`.
  </action>
  <verify>
    JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "*EventDataMappingTest"
  </verify>
  <acceptance_criteria>
    - grep `@SerializedName("image_url")` in SignUpService.kt → 1 hit
    - grep `val imageUrl: String?` in AIEventDetect.kt → 2 hits (data class + mapping)
    - 3 assertions PASS
    - 2 commits: RED + GREEN
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 4: EventItem Composable 에 thumbnail 표시 (Issue 2A UI)</name>
  <files>
    app/src/main/java/com/example/smart_safety_management/AIEventDetect.kt
  </files>
  <behavior>
    EventItem 의 inner Row (line 334-372) 시작 위치에 thumbnail AsyncImage 추가:
    - event.imageUrl 이 null/blank 이면 표시 안 함 (기존 icon-only 유지)
    - event.imageUrl 이 있으면 64dp × 64dp Box + AsyncImage + RoundedCornerShape(4.dp)
    - 기존 accidentType icon 은 thumbnail 의 상단 우측에 오버레이 (또는 thumbnail 다음 자리에 inline)

    Conservative: 기존 icon 위치 보존, thumbnail 만 icon 앞에 추가. icon 의 시각적 의미 (위험도) 가 우선.
  </behavior>
  <red_test_first>
    이 task 는 UI Composable 만 수정 → Robolectric 미사용 환경에서 unit test 불가. characterization test 로:

    `app/src/test/java/com/example/smart_safety_management/EventItemThumbnailCharacterizationTest.kt`:
    ```kotlin
    package com.example.smart_safety_management

    import org.junit.Assert.assertTrue
    import org.junit.Test
    import java.io.File

    class EventItemThumbnailCharacterizationTest {
        private val file = File("src/main/java/com/example/smart_safety_management/AIEventDetect.kt")

        @Test fun aiEventDetect_importsAsyncImage() {
            assertTrue(
                "AIEventDetect.kt 가 coil AsyncImage import 누락 — thumbnail 표시 안됨",
                file.readText().contains("import coil.compose.AsyncImage")
            )
        }

        @Test fun eventItem_referencesImageUrl() {
            val src = file.readText()
            // EventItem 안에서 event.imageUrl 호출 — thumbnail conditional rendering 의 evidence
            assertTrue(
                "EventItem 에 event.imageUrl 참조 누락 — thumbnail wiring 안됨",
                src.contains("event.imageUrl")
            )
        }

        @Test fun eventItem_handlesNullImageUrl() {
            val src = file.readText()
            // null/blank check 가 있어야 fallback 동작
            assertTrue(
                "imageUrl null/blank 가드 누락",
                src.contains("imageUrl") && (src.contains("isNullOrBlank") || src.contains("?.isNotBlank()") || src.contains("imageUrl != null"))
            )
        }
    }
    ```
    Expected RED: `assertTrue` 3개 모두 fail (coil import + event.imageUrl + null guard 모두 부재).
  </red_test_first>
  <action>
    1단계 (RED): test 작성 → gradle → 3 assertion fail → commit `test(quick-camera-thumb): RED add EventItemThumbnailCharacterizationTest`.

    2단계 (GREEN): AIEventDetect.kt 에 변경:

    a) import 블록에 추가:
    ```kotlin
    import coil.compose.AsyncImage
    import androidx.compose.ui.layout.ContentScale
    ```

    b) EventItem 의 inner Row (line 334) 의 첫 child 자리에 thumbnail 추가:
    ```kotlin
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 신규 thumbnail (event.imageUrl 이 있을 때만)
        if (!event.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = event.imageUrl,
                contentDescription = "감지 이미지",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .padding(end = 8.dp)
            )
        }

        val iconRes = when (event.accidentType) { ... }  // 기존
        // 기존 inner Row 의 icon + Column 그대로
    }
    ```

    회귀: assembleDebug + 기존 모든 unit test PASS 확인.

    commit `feat(quick-camera-thumb): GREEN EventItem 에 AsyncImage thumbnail 추가`.
  </action>
  <verify>
    JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "*EventItemThumbnailCharacterizationTest" &&
    JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
  </verify>
  <acceptance_criteria>
    - grep `import coil.compose.AsyncImage` in AIEventDetect.kt → 1 hit
    - grep `event.imageUrl` in AIEventDetect.kt → 1+ hits
    - grep `isNullOrBlank|imageUrl != null|imageUrl ?.isNotBlank` in AIEventDetect.kt → 1+ hit
    - 3 characterization assertions PASS
    - assembleDebug BUILD SUCCESSFUL
    - 2 commits: RED + GREEN
  </acceptance_criteria>
</task>

<task type="auto" tdd="false">
  <name>Task 5: Backend 검증 (Issue 2B) — get_recent_detection_events 가 image_url 반환?</name>
  <files>
    .planning/quick/20260527-camera-unify-and-detection-thumbnails/BACKEND-CHECK.md
  </files>
  <behavior>
    Investigation task — 코드 변경 0건이 default. 만약 backend 에서 image_url 안 반환하면 별도 follow-up issue 로 기록.
  </behavior>
  <action>
    1. supabase 의 get_recent_detection_events 함수 본문 (SQL 또는 Edge Function) 검색
    2. SELECT 절에 image_url 포함 여부 확인
    3. 없으면 → BACKEND-CHECK.md 에 "follow-up: SELECT 에 image_url 추가 필요, 마이그레이션 N번 또는 Edge Function 수정" 기록
    4. 있으면 → "OK, client 만 수정으로 충분" 기록
    5. 1 commit: `docs(quick-camera-thumb): backend check log — image_url 반환 여부`
  </action>
  <acceptance_criteria>
    - BACKEND-CHECK.md exists with verdict
    - 1 commit
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 6: 종결 — SUMMARY.md + STATE.md Quick Tasks 표 갱신 + 회귀 가드</name>
  <files>
    .planning/quick/20260527-camera-unify-and-detection-thumbnails/SUMMARY.md,
    .planning/STATE.md
  </files>
  <action>
    1. 합성 회귀 가드:
       - `JAVA_HOME=... ./gradlew :app:testDebugUnitTest` → all PASS
       - `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
       - `cd ai_agent && python -m pytest tests/` → 28/28 PASS
       - `cd j2208a && python -m pytest tests/` → 43/43 PASS
       - `git diff ai_agent/ j2208a/` → empty (supabase 는 Task 5 결과에 따라)

    2. SUMMARY.md 작성 (frontmatter status: complete)
    3. STATE.md 의 "Quick Tasks Completed" 표에 1 row 추가
    4. 1 commit: `docs(quick-camera-thumb): SUMMARY + STATE 갱신 — Issue 1+2 완료`
  </action>
  <acceptance_criteria>
    - SUMMARY.md exists with status: complete
    - STATE.md 의 Quick Tasks 표 row +1
    - 모든 회귀 게이트 PASS
    - 1 commit
  </acceptance_criteria>
</task>

</tasks>

<success_criteria>
1. 6 task 모두 GREEN
2. 신규 3 test 파일 RED 후 GREEN
3. JVM unit test 전체 PASS, assembleDebug BUILD SUCCESSFUL
4. ai_agent + j2208a 회귀 0
5. InternalDetail.kt: 같은 카메라 시 1 SmartPreviewCard 만 (회귀: 다른 URL 시 2개 유지)
6. AIEventDetect.kt: imageUrl 있는 이벤트는 thumbnail 표시 (회귀: imageUrl null/blank 시 기존 동작)
7. Backend 검증 결과 BACKEND-CHECK.md 기록 (follow-up issue 또는 OK 표기)
8. SUMMARY + STATE 갱신
</success_criteria>
