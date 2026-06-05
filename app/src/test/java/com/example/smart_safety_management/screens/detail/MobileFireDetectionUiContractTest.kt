package com.example.smart_safety_management.screens.detail

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MobileFireDetectionUiContractTest {
    @Test
    fun internalDetailUsesRtspMobileDetectionPlayerForRtspUrls() {
        val text = readSource("src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt")

        assertTrue(text.contains("RtspMobileDetectionPlayer("))
        assertTrue(text.contains("MobileFireDetectionBadge("))
    }

    @Test
    fun rtspPlayerUsesTextureViewForSamplingAndMobileDetection() {
        val text = readSource("src/main/java/com/example/smart_safety_management/mobileai/RtspTexturePlayer.kt")

        assertTrue(text.contains("TextureView"))
        assertTrue(text.contains("TextureViewFrameSampler"))
        assertTrue(text.contains("MobileFireDetectionCoordinator"))
    }

    @Test
    fun rtspBadgeUsesRealKoreanLabels() {
        val text = readSource("src/main/java/com/example/smart_safety_management/mobileai/RtspTexturePlayer.kt")
        val expectedLabels = listOf(
            "모바일 감지 꺼짐",
            "모바일 감지 준비 중",
            "모바일 감지 실행 중",
            "화재 감지",
            "모바일 감지 대기",
            "감지 오류"
        )

        expectedLabels.forEach { label ->
            assertTrue("Expected badge label: $label", text.contains(label))
        }
    }

    private fun readSource(path: String): String {
        return String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
    }
}
