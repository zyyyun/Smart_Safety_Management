package com.example.smart_safety_management

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 11 / 11-02 Sub-task 2.3 — 입구 흐름의 SignUpValidator + ErrorBanner wiring 회귀 가드.
 * 입력 필드가 있는 4 Activity (SignUp2/3/4, LogIn) 가 모두 공통 validator import.
 * SignUp1 + Splash 는 입력 없음 — 제외.
 */
class EntryFlowValidatorWiringTest {
    private val activitiesWithInput = listOf(
        "SignUp2Activity.kt",
        "SignUp3Activity.kt",
        "SignUp4Activity.kt",
        "LogInActivity.kt",
    )

    @Test fun entryActivitiesImportSignUpValidator() {
        activitiesWithInput.forEach { name ->
            val f = File("src/main/java/com/example/smart_safety_management/$name")
            assertTrue("$name not found", f.exists())
            val src = f.readText()
            assertTrue(
                "$name 가 SignUpValidator import 누락 — 공통 검증 wiring 안됨",
                src.contains("import com.example.smart_safety_management.auth.SignUpValidator")
            )
        }
    }

    @Test fun entryActivitiesImportErrorBannerMessage() {
        activitiesWithInput.forEach { name ->
            val f = File("src/main/java/com/example/smart_safety_management/$name")
            val src = f.readText()
            assertTrue(
                "$name 가 errorBannerMessage import 누락 — 공통 에러 표시 wiring 안됨",
                src.contains("errorBannerMessage")
            )
        }
    }
}
