package com.example.smart_safety_management.tbm

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class TbmDashboardComposeStabilityTest {
    @Test
    fun dashboardDoesNotReturnEarlyFromComposableContent() {
        val source = File(
            "src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt",
        ).readText()

        assertFalse(source.contains("return@Column"))
        assertFalse(Regex("""Text\("없음"[\s\S]{0,120}\n\s*return""").containsMatchIn(source))
    }

    @Test
    fun workerScreenDoesNotReturnEarlyFromComposableContent() {
        val source = File(
            "src/main/java/com/example/smart_safety_management/tbm/TbmWorkerScreen.kt",
        ).readText()

        assertFalse(source.contains("return@Column"))
    }
}
