package com.example.smart_safety_management.watch

import org.junit.Assert.assertTrue
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
        assertTrue(src.contains("WatchRuntimeSnapshot.from"))
    }

    @Test
    fun detailScreenRefreshesRuntimeSnapshotEverySecondAndRendersPpgAndCommunicationAge() {
        val src = watchDetail.readText()

        assertTrue(src.contains("WatchRuntimeStore.state.collectAsState"))
        assertTrue(src.contains("WatchRuntimeSnapshot.from"))
        assertTrue(src.contains("delay(1_000)"))
        assertTrue(src.contains("ppgDisplay"))
        assertTrue(src.contains("lastCommunicationLabel"))
    }
}
