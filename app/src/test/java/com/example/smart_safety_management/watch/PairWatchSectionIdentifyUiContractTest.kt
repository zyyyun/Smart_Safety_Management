package com.example.smart_safety_management.watch

import org.junit.Assert.assertFalse
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

    @Test
    fun selectedReadingWatchRowUsesConnectedLabel() {
        val src = section.readText()

        assertTrue(src.contains("selected && isConnectedWatchState(connectionState)"))
        assertTrue(src.contains("connectionState == JcWearConnectionState.READING"))
    }

    @Test
    fun registrationSeedsConnectingRuntimeBeforeStartingService() {
        val src = section.readText()

        assertTrue(src.contains("WatchRuntimeStore.update("))
        assertTrue(src.contains("WatchRuntimeState("))
        assertTrue(src.contains("deviceId = registered.deviceId"))
        assertTrue(src.contains("userId = userId"))
        assertTrue(src.contains("macAddress = registered.macAddress"))
        assertTrue(src.contains("status = WatchRuntimeStatus.CONNECTING"))
        assertTrue(
            src.indexOf("WatchRuntimeStore.update(") <
                src.indexOf("WatchBleServiceController.configureAndStart(context, userId, registered)"),
        )
    }

    @Test
    fun pairingUiDoesNotOwnHealthReadCollectionOrMeasurementRequests() {
        val src = section.readText()

        assertFalse(src.contains("bleBridge.healthReadings.collect"))
        assertFalse(src.contains("requestCurrentMeasurements"))
    }
}
