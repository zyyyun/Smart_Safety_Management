package com.example.smart_safety_management

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MobileFireApiContractTest {
    @Test
    fun retrofitRoutesMobileFireEventToEdgeFunction() {
        val text = sourceText("src/main/java/com/example/smart_safety_management/RetrofitClient.kt")

        assertTrue(text.contains("\"/create_mobile_fire_event\" to Route(\"mobile-ai-event\", \"create_mobile_fire_event\")"))
    }

    @Test
    fun signUpServiceDefinesMobileFireEventApi() {
        val text = sourceText("src/main/java/com/example/smart_safety_management/SignUpService.kt")

        assertTrue(text.contains("data class CreateMobileFireEventRequest"))
        assertTrue(text.contains("fun createMobileFireEvent"))
    }

    private fun sourceText(path: String): String {
        return String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
    }
}
