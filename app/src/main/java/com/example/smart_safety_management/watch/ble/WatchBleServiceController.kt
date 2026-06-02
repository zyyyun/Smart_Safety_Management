package com.example.smart_safety_management.watch.ble

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.smart_safety_management.watch.DeviceRow

data class WatchBleServiceConfig(
    val userId: String,
    val deviceId: Int,
    val macAddress: String,
)

object WatchBleServiceController {
    const val ACTION_START = "com.example.smart_safety_management.watch.ACTION_START_WATCH_BLE"
    const val ACTION_STOP = "com.example.smart_safety_management.watch.ACTION_STOP_WATCH_BLE"

    private const val PREF_NAME = "watch_ble_service"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_MAC_ADDRESS = "mac_address"

    fun configureAndStart(context: Context, userId: String, device: DeviceRow): Boolean {
        val mac = device.macAddress?.trim()?.uppercase().orEmpty()
        if (userId.isBlank() || device.deviceId <= 0 || mac.isBlank()) return false
        val config = WatchBleServiceConfig(
            userId = userId,
            deviceId = device.deviceId,
            macAddress = mac,
        )
        saveConfig(context, config)
        WatchRuntimeStore.update(
            WatchRuntimeState(
                deviceId = config.deviceId,
                userId = config.userId,
                macAddress = config.macAddress,
                status = WatchRuntimeStatus.CONNECTING,
            ),
        )
        start(context)
        return true
    }

    fun start(context: Context): Boolean {
        val config = loadConfig(context) ?: return false
        val intent = Intent(context, WatchBleForegroundService::class.java)
            .setAction(ACTION_START)
            .putExtra(KEY_USER_ID, config.userId)
            .putExtra(KEY_DEVICE_ID, config.deviceId)
            .putExtra(KEY_MAC_ADDRESS, config.macAddress)
        ContextCompat.startForegroundService(context.applicationContext, intent)
        return true
    }

    fun stopAndClear(context: Context) {
        clearConfig(context)
        WatchRuntimeStore.clear()
        val intent = Intent(context, WatchBleForegroundService::class.java).setAction(ACTION_STOP)
        context.applicationContext.startService(intent)
    }

    fun saveConfig(context: Context, config: WatchBleServiceConfig) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, config.userId)
            .putInt(KEY_DEVICE_ID, config.deviceId)
            .putString(KEY_MAC_ADDRESS, config.macAddress.uppercase())
            .apply()
    }

    fun loadConfig(context: Context): WatchBleServiceConfig? {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val deviceId = prefs.getInt(KEY_DEVICE_ID, 0).takeIf { it > 0 } ?: return null
        val mac = prefs.getString(KEY_MAC_ADDRESS, null)?.trim()?.uppercase()
            ?.takeIf { it.isNotBlank() } ?: return null
        return WatchBleServiceConfig(userId = userId, deviceId = deviceId, macAddress = mac)
    }

    fun clearConfig(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
