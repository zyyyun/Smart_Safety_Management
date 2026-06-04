package com.example.smart_safety_management.watch.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WatchBleForegroundServiceContractTest {
    private val manifest = File("src/main/AndroidManifest.xml")
    private val service = File(
        "src/main/java/com/example/smart_safety_management/watch/ble/WatchBleForegroundService.kt",
    )
    private val controller = File(
        "src/main/java/com/example/smart_safety_management/watch/ble/WatchBleServiceController.kt",
    )
    private val pairWatchSection = File(
        "src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt",
    )
    private val homeActivity = File(
        "src/main/java/com/example/smart_safety_management/HomeActivity.kt",
    )
    private val homeWorkerActivity = File(
        "src/main/java/com/example/smart_safety_management/HomeWorkerActivity.kt",
    )

    @Test
    fun manifestDeclaresConnectedDeviceForegroundService() {
        val xml = manifest.readText()

        assertTrue(xml.contains("android:name=\".watch.ble.WatchBleForegroundService\""))
        assertTrue(xml.contains("android:foregroundServiceType=\"connectedDevice\""))
        assertTrue(xml.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"))
    }

    @Test
    fun serviceOwnsBleConnectionAndMeasurementUploadLoop() {
        assertTrue("WatchBleForegroundService.kt missing", service.exists())
        val src = service.readText()

        assertTrue(src.contains("class WatchBleForegroundService : Service()"))
        assertTrue(src.contains("JcWearBleBridge"))
        assertTrue(src.contains("JcWearDeviceRegistrar"))
        assertTrue(src.contains("registrar.updateWatchReading"))
        assertTrue(src.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE"))
        assertTrue(src.contains("START_STICKY"))
        assertTrue(src.contains("bridge.startScan()"))
        assertTrue(src.contains("bridge.connect"))
        assertTrue(src.contains("WatchRuntimeStore.mutate"))
        assertTrue(src.contains("WatchRuntimeStatus.SCANNING"))
        assertTrue(src.contains("WatchRuntimeStatus.CONNECTING"))
        assertTrue(src.contains("WatchRuntimeStatus.READING"))
        assertTrue(src.contains("WatchRuntimeStatus.RETRYING"))
        assertTrue(src.contains("lastReadAt = Instant.now()") || src.contains("val readAt = Instant.now()"))
        assertTrue(src.contains("lastUploadAt = Instant.now()") || src.contains("val uploadAt = Instant.now()"))
        assertTrue(src.contains("if (config == activeConfig && sameConfigJobsActive() && activeSessionId != null)"))
        assertTrue(src.contains("WatchReadingUploadPolicy"))
    }

    @Test
    fun serviceRestartsWhenEitherMonitoringOrUploadJobIsInactive() {
        val src = service.readText()

        assertTrue(src.contains("private fun sameConfigJobsActive()"))
        assertTrue(src.contains("monitorJob?.isActive == true"))
        assertTrue(src.contains("uploadJob?.isActive == true"))
        assertTrue(src.contains("restartMonitoring(config)"))
        assertFalse(src.contains("if (config != activeConfig) {\r\n            restartMonitoring(config)\r\n        }"))
        assertFalse(src.contains("if (config != activeConfig) {\n            restartMonitoring(config)\n        }"))
    }

    @Test
    fun serviceDoesNotSwallowCancellationOrWriteStaleRuntime() {
        val src = service.readText()

        assertTrue(src.contains("import kotlinx.coroutines.CancellationException"))
        assertTrue(src.contains("if (error is CancellationException) throw error"))
        assertTrue(src.contains("mutateRuntimeFor(config, monitoringSessionId)"))
        assertTrue(src.contains("mutateRuntimeFor(config, activeSessionId)"))
        assertTrue(src.contains("activeMonitoringSessionId != monitoringSessionId"))
        assertTrue(src.contains("current.monitoringSessionId?.let { it != monitoringSessionId } == true"))
    }

    @Test
    fun serviceStopClearsRuntimeForCapturedConfigOnly() {
        val src = service.readText()

        assertTrue(src.contains("val configToStop = activeConfig"))
        assertTrue(src.contains("val monitoringSessionIdToStop = activeMonitoringSessionId"))
        assertTrue(src.contains("WatchRuntimeStore.clearMonitoringSession"))
        assertTrue(src.contains("deviceId = configToStop.deviceId"))
        assertTrue(src.contains("monitoringSessionId = monitoringSessionIdToStop"))
        assertFalse(src.contains("WatchRuntimeStore.clear(activeConfig?.deviceId)"))
        assertFalse(src.contains("WatchRuntimeStore.clear(configToStop.deviceId)"))
    }

    @Test
    fun serviceTreatsReadingAsStableActiveConnection() {
        val src = service.readText()

        assertTrue(src.contains("JcWearConnectionState.READING"))
        assertTrue(src.contains("isActiveConnectionState"))
        assertTrue(src.contains("connectionState == JcWearConnectionState.CONNECTED ||"))
        assertTrue(src.contains("connectionState == JcWearConnectionState.READING"))
        assertTrue(src.contains("if (isActiveConnectionState(bridge.uiState.value.connectionState)) 30_000 else 5_000"))
    }

    @Test
    fun controllerPersistsServiceConfigAndStartsForegroundService() {
        assertTrue("WatchBleServiceController.kt missing", controller.exists())
        val src = controller.readText()

        assertTrue(src.contains("saveConfig"))
        assertTrue(src.contains("loadConfig"))
        assertTrue(src.contains("ContextCompat.startForegroundService"))
        assertTrue(src.contains("ACTION_START"))
        assertTrue(src.contains("ACTION_STOP"))
        assertTrue(src.contains("seedForServiceStart"))
    }

    @Test
    fun screensStartServiceButDoNotOwnHealthUploadLoop() {
        val pairSrc = pairWatchSection.readText()
        val homeSrc = homeActivity.readText()
        val workerSrc = homeWorkerActivity.readText()

        assertTrue(pairSrc.contains("WatchBleServiceController.configureAndStart"))
        assertTrue(pairSrc.contains("WatchBleServiceController.stopAndClear"))
        assertFalse(pairSrc.contains("bleBridge.healthReadings.collect"))
        assertTrue(homeSrc.contains("WatchBleServiceController.configureAndStart"))
        assertTrue(workerSrc.contains("WatchBleServiceController.configureAndStart"))
    }
}
