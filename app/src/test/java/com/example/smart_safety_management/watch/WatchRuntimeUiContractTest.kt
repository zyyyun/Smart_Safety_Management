package com.example.smart_safety_management.watch

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class WatchRuntimeUiContractTest {
    private val watchCard = File("src/main/java/com/example/smart_safety_management/watch/WatchCardComposable.kt")
    private val watchMiniCard = File("src/main/java/com/example/smart_safety_management/watch/WatchMiniCardComposable.kt")
    private val watchDetail = File("src/main/java/com/example/smart_safety_management/watch/WatchDetailScreen.kt")

    @Test
    fun homeWatchCardCollectsLiveRuntimeSnapshot() {
        val src = watchCard.readText()

        assertTrue(src.contains("WatchRuntimeStore.state.collectAsState"))
        assertTrue(src.contains("WatchRuntimeSnapshot.from"))
    }

    @Test
    fun miniWatchCardCollectsLiveRuntimeSnapshot() {
        val src = watchMiniCard.readText()

        assertTrue(src.contains("WatchRuntimeStore.state.collectAsState"))
        assertTrue(src.contains("var device by remember(deviceId)"))
        assertTrue(src.contains("repo.deviceFlow(deviceId)"))
        assertTrue(src.contains("WatchRuntimeSnapshot.from(device, snapshot, runtime)"))
        assertTrue(src.contains("runtimeStatusSnapshot"))
        assertTrue(src.contains("heartRate = runtimeSnapshot.heartRate"))
        assertTrue(src.contains("bodyTemp = runtimeSnapshot.bodyTemp"))
        assertTrue(src.contains("WatchHealthFormatter.overallStatus("))
        assertTrue(src.contains("val miniPrimaryMetric ="))
        assertTrue(src.contains("runtimeSnapshot.ppgDisplay != \"--\""))
        assertTrue(src.contains("\"PPG \${runtimeSnapshot.ppgDisplay}\""))
        assertTrue(src.contains("miniPrimaryMetric"))
        assertFalse(src.contains("WatchHealthFormatter.overallStatus(\n        snapshot, lastWearState"))
    }

    @Test
    fun detailScreenRefreshesRuntimeSnapshotEverySecondAndRendersPpgAndCommunicationAge() {
        val src = watchDetail.readText()

        assertTrue(src.contains("WatchRuntimeStore.state.collectAsState"))
        assertTrue(src.contains("WatchRuntimeSnapshot.from"))
        assertTrue(src.contains("delay(1_000)"))
        assertTrue(src.contains("ppgDisplay"))
        assertTrue(src.contains("lastCommunicationLabel"))
        assertTrue(src.contains("HrCard(runtimeSnapshot.hrDisplay, runtimeSnapshot.heartRate, wearState)"))
        assertTrue(src.contains("TempCard(runtimeSnapshot.tempDisplay, runtimeSnapshot.bodyTemp, wearState)"))
        assertTrue(src.contains("heartRate = runtimeSnapshot.heartRate"))
        assertTrue(src.contains("bodyTemp = runtimeSnapshot.bodyTemp"))
        assertTrue(src.contains("runtimeSnapshot.batteryLevel"))
        assertFalse(src.contains("Last communication"))
        assertFalse(src.contains("마지막 측정"))
    }
}
