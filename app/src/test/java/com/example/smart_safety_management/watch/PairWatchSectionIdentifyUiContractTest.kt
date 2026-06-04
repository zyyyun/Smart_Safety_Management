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
        val deviceAssignedIndex = src.indexOf("device = registered")
        val seedIndex = src.indexOf("current.seedForRegisteredWatch(userId, registered)")
        val disconnectIndex = src.indexOf("bleBridge.disconnect()", startIndex = seedIndex)
        val serviceStartIndex = src.indexOf("WatchBleServiceController.configureAndStart(context, userId, registered)")

        assertTrue(src.contains("WatchRuntimeStore.mutate { current ->"))
        assertTrue(seedIndex > deviceAssignedIndex)
        assertTrue(seedIndex < serviceStartIndex)
        assertTrue(disconnectIndex in seedIndex until serviceStartIndex)
        assertFalse(src.contains("WatchRuntimeStore.update("))
    }

    @Test
    fun initialDeviceLoadCompletesBeforeUnpairedScanPanelIsShown() {
        val src = section.readText()

        assertTrue(src.contains("var deviceLoadComplete by remember { mutableStateOf(false) }"))
        assertTrue(src.contains("deviceLoadComplete = true"))
        assertTrue(src.contains("val showUnpairedScan = deviceLoadComplete && status == WatchStatus.UNPAIRED"))
        assertTrue(src.contains("if (!deviceLoadComplete)"))
        assertTrue(src.contains("WatchPairLoadingPanel()"))
        assertTrue(src.contains("} else if (showUnpairedScan)"))
    }

    @Test
    fun topStatusBadgeUsesFreshRuntimeStateWhenAvailable() {
        val src = section.readText()

        assertTrue(src.contains("val displayedStatus ="))
        assertTrue(src.contains("runtimeSnapshot.runtimeStatus == WatchRuntimeStatus.READING"))
        assertTrue(src.contains("runtimeSnapshot.runtimeStatus == WatchRuntimeStatus.RETRYING -> WatchStatus.CONNECTING"))
        assertTrue(src.contains("runtimeSnapshot.runtimeStatus == WatchRuntimeStatus.FAILED"))
        assertTrue(src.contains("StatusBadge(displayedStatus)"))
        assertFalse(src.contains("StatusBadge(status)"))
    }

    @Test
    fun pairingUiDoesNotOwnHealthReadCollectionOrMeasurementRequests() {
        val src = section.readText()

        assertTrue(src.contains("JcWearBleBridge(context.applicationContext, startTelemetryOnConnect = false)"))
        assertFalse(src.contains("bleBridge.healthReadings.collect"))
        assertFalse(src.contains("requestCurrentMeasurements"))
    }
}
