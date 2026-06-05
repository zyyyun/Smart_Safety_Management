package com.example.smart_safety_management.screens.detail

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MobileFireDetectionUiContractTest {
    @Test
    fun internalDetailUsesRtspMobileDetectionPlayerForRtspUrls() {
        val source = readSource("src/main/java/com/example/smart_safety_management/screens/detail/InternalDetail.kt")
        val code = source.withoutComments()

        assertTrue(
            "RTSP and RTSPS URL branches should be detected in code",
            code.contains("trimmedUrl.startsWith(\"rtsp://\", ignoreCase = true)") &&
                code.contains("trimmedUrl.startsWith(\"rtsps://\", ignoreCase = true)")
        )
        assertTrue(
            "RTSP/RSTPS branch should route through mobile detection",
            code.contains("RtspMobileDetectionPlayer(")
        )
        assertTrue(
            "Non-RTSP video branch should keep the VideoPlayer fallback",
            code.contains("else {\n                VideoPlayer(url = trimmedUrl, modifier = Modifier.fillMaxSize())")
        )
    }

    @Test
    fun rtspPlayerUsesTextureViewForSamplingAndMobileDetection() {
        val text = readSource("src/main/java/com/example/smart_safety_management/mobileai/RtspTexturePlayer.kt")

        assertTrue(text.contains("TextureView"))
        assertTrue(text.contains("TextureViewFrameSampler"))
        assertTrue(text.contains("MobileFireDetectionCoordinator"))
    }

    @Test
    fun rtspCoordinatorResetsWhenUrlChanges() {
        val code = readSource("src/main/java/com/example/smart_safety_management/mobileai/RtspTexturePlayer.kt")
            .withoutComments()

        assertTrue(
            "Coordinator remember keys should include textureView, cameraId, and url",
            Regex("""val\s+coordinator\s*=\s*remember\s*\(\s*textureView\s*,\s*cameraId\s*,\s*url\s*\)""")
                .containsMatchIn(code)
        )
        assertTrue(
            "Coordinator disposal should remain keyed by coordinator",
            code.contains("DisposableEffect(coordinator)")
        )
        assertTrue(
            "Coordinator start should restart when coordinator changes",
            code.contains("LaunchedEffect(coordinator)")
        )
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

    private fun String.withoutComments(): String {
        val result = StringBuilder(length)
        var index = 0
        var inString = false

        while (index < length) {
            val current = this[index]
            val next = getOrNull(index + 1)

            when {
                !inString && current == '/' && next == '/' -> {
                    index = indexOf('\n', startIndex = index).takeIf { it >= 0 } ?: length
                }
                !inString && current == '/' && next == '*' -> {
                    index = indexOf("*/", startIndex = index + 2).takeIf { it >= 0 }?.plus(2) ?: length
                }
                current == '"' && (index == 0 || this[index - 1] != '\\') -> {
                    inString = !inString
                    result.append(current)
                    index++
                }
                else -> {
                    result.append(current)
                    index++
                }
            }
        }

        return result.toString()
    }
}
