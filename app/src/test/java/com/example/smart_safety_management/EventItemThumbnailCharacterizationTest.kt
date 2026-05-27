package com.example.smart_safety_management

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue 2A UI 회귀 가드 — Compose UI 단위 테스트는 Robolectric 부재 환경에서
 * 불가능하므로 source-level characterization 으로 thumbnail wiring 확인.
 *
 * cwd 는 gradle 의 `:app` module → `app/` 디렉터리.
 */
class EventItemThumbnailCharacterizationTest {

    // gradle :app:testDebugUnitTest 의 cwd 는 module root (app/).
    // Phase 11 의 CommonToolbarXmlTest 패턴과 동일.
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
        // null/blank guard 가 있어야 fallback 동작
        assertTrue(
            "imageUrl null/blank 가드 누락",
            src.contains("imageUrl") &&
                (
                    src.contains("isNullOrBlank") ||
                        src.contains("?.isNotBlank()") ||
                        src.contains("imageUrl != null")
                    )
        )
    }
}
