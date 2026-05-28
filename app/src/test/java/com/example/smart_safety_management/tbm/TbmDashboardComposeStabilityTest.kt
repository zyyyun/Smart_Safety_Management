package com.example.smart_safety_management.tbm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun tbmScreensRefreshCurrentPageAfterActions() {
        val dashboard = File(
            "src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt",
        ).readText()
        val worker = File(
            "src/main/java/com/example/smart_safety_management/tbm/TbmWorkerScreen.kt",
        ).readText()

        assertTrue(dashboard.contains("sessionRefreshNonce++"))
        assertTrue(dashboard.contains("detailRefreshNonce++"))
        assertTrue(dashboard.contains("onSessionChanged = { sessionRefreshNonce++ }"))
        assertTrue(worker.contains("sessionRefreshNonce++"))
        assertTrue(worker.contains("detailRefreshNonce++"))
    }

    @Test
    fun workerCheckinSuccessNavigatesBackToWorkerHome() {
        val workerScreen = File(
            "src/main/java/com/example/smart_safety_management/tbm/TbmWorkerScreen.kt",
        ).readText()
        val workerActivity = File(
            "src/main/java/com/example/smart_safety_management/TbmWorkerActivity.kt",
        ).readText()

        assertTrue(workerScreen.contains("onCheckinSubmitted: () -> Unit"))
        assertTrue(workerScreen.contains("onCheckinSubmitted()"))
        assertTrue(workerActivity.contains("HomeWorkerActivity::class.java"))
        assertTrue(workerActivity.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP"))
        assertTrue(workerActivity.contains("Intent.FLAG_ACTIVITY_SINGLE_TOP"))
    }
}
