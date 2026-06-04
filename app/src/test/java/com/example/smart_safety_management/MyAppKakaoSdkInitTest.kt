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
            "MyApp.kt 의 KakaoMapSdk.init( 호출 수 = $initCallCount, 기대 = 1 (이중 init 시 후자가 전자 덮어씀)",
            1,
            initCallCount
        )
    }
}
