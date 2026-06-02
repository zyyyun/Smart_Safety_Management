package com.example.smart_safety_management.watch.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smart_safety_management.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchBleForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var bridge: JcWearBleBridge
    private val registrar = JcWearDeviceRegistrar()
    private var activeConfig: WatchBleServiceConfig? = null
    private var monitorJob: Job? = null
    private var uploadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        bridge = JcWearBleBridge(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == WatchBleServiceController.ACTION_STOP) {
            stopWatchMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }

        val config = WatchBleServiceController.loadConfig(this) ?: return START_NOT_STICKY
        startAsForeground(config)
        if (config != activeConfig) {
            restartMonitoring(config)
        }
        return START_STICKY
    }

    private fun restartMonitoring(config: WatchBleServiceConfig) {
        activeConfig = config
        monitorJob?.cancel()
        uploadJob?.cancel()
        bridge.close()
        bridge.refreshEnvironment()

        uploadJob = serviceScope.launch {
            bridge.healthReadings.collect { reading ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        registrar.updateWatchReading(config.userId, config.deviceId, reading)
                    }
                }.onFailure { error ->
                    Log.w(TAG, "watch reading upload failed", error)
                }
            }
        }

        monitorJob = serviceScope.launch {
            while (isActive) {
                bridge.refreshEnvironment()
                val state = bridge.uiState.value
                val target = state.discoveredDevices.firstOrNull {
                    it.address.equals(config.macAddress, ignoreCase = true)
                }
                when {
                    target != null &&
                        state.connectionState != JcWearConnectionState.CONNECTED &&
                        state.connectionState != JcWearConnectionState.CONNECTING -> {
                        bridge.connect(target)
                    }
                    state.connectionState == JcWearConnectionState.DISCONNECTED ||
                        state.connectionState == JcWearConnectionState.FAILED -> {
                        if (!state.scanning) bridge.startScan()
                    }
                    state.connectionState == JcWearConnectionState.SCANNING && target != null -> {
                        bridge.connect(target)
                    }
                }
                delay(if (bridge.uiState.value.connectionState == JcWearConnectionState.CONNECTED) 30_000 else 5_000)
            }
        }
    }

    private fun stopWatchMonitoring() {
        monitorJob?.cancel()
        uploadJob?.cancel()
        monitorJob = null
        uploadJob = null
        activeConfig = null
        bridge.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startAsForeground(config: WatchBleServiceConfig) {
        val notification = buildNotification(config)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(config: WatchBleServiceConfig): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "J2208A 워치 모니터링",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("J2208A 워치 모니터링 중")
            .setContentText("${config.macAddress} 워치 연결과 측정값 전송을 유지합니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopWatchMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WatchBleForegroundService"
        private const val CHANNEL_ID = "watch_ble_monitor"
        private const val NOTIFICATION_ID = 2208
    }
}
