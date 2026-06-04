package com.example.smart_safety_management.watch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PairWatchSectionHealthReadingsTest {
    private val pairSource = File(
        "src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt",
    )
    private val serviceSource = File(
        "src/main/java/com/example/smart_safety_management/watch/ble/WatchBleForegroundService.kt",
    )

    @Test
    fun healthReadingServerWritesAreNotCancelledByLaterBleCallbacks() {
        val pairSrc = pairSource.readText()
        val serviceSrc = serviceSource.readText()

        assertFalse(
            "BLE health readings must not use collectLatest because battery/heart/temp callbacks can cancel in-flight server writes.",
            pairSrc.contains("bleBridge.healthReadings.collect"),
        )
        assertTrue(
            "Foreground service should collect readings serially so each reading reaches the server.",
            Regex("""bridge\.healthReadings\s*\.collect\s*\{\s*reading\s*->""")
                .containsMatchIn(serviceSrc),
        )
    }
}
