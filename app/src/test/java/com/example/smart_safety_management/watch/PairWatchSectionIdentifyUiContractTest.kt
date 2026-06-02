package com.example.smart_safety_management.watch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PairWatchSectionIdentifyUiContractTest {
    private val section = File("src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt")

    @Test
    fun scanRowsExposeManualIdentifyButtonBeforeConnectButton() {
        val src = section.readText()

        assertTrue(src.contains("Icons.Default.Notifications"))
        assertTrue(src.contains("onIdentify = { bleBridge.identify(it) }"))
        assertTrue(src.contains("onIdentify: () -> Unit"))
        assertTrue(src.contains("IconButton(onClick = onIdentify"))
        assertTrue(src.indexOf("IconButton(onClick = onIdentify") < src.indexOf("OutlinedButton("))
    }
}
