package com.example.smart_safety_management.watch.ble

import com.example.smart_safety_management.BuildConfig
import com.example.smart_safety_management.watch.DeviceRow
import com.example.smart_safety_management.watch.NotificationsFunctionsApi
import com.example.smart_safety_management.watch.WatchPairRequest
import com.example.smart_safety_management.watch.WatchReadingRequest
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant

class JcWearDeviceRegistrar(
    private val api: NotificationsFunctionsApi = buildNotificationsApi(),
) {
    suspend fun registerWatch(
        userId: String,
        device: JcWearDiscoveredDevice,
    ): DeviceRow {
        val normalizedAddress = device.address.uppercase()
        val resp = api.callWatchPair(
            url = notificationsUrl(),
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
            body = WatchPairRequest(
                user_id = userId,
                mac_address = normalizedAddress,
                op = "pair",
            ),
        )
        if (!resp.isSuccessful || resp.body()?.ok != true) {
            throw IllegalStateException(resp.body()?.error ?: "워치 등록 서버 요청 실패: HTTP ${resp.code()}")
        }
        val body = resp.body()
        val now = body?.last_comm_at ?: Instant.now().toString()
        return DeviceRow(
            deviceId = body?.device_id ?: 0,
            deviceType = "WATCH",
            macAddress = body?.mac_address ?: normalizedAddress,
            userId = userId,
            lastCommAt = now,
            serialNumber = "J2208A-${body?.mac_address ?: normalizedAddress}",
            updatedAt = now,
        )
    }

    suspend fun updateWatchReading(
        userId: String,
        deviceId: Int,
        reading: JcWearHealthReading,
    ) {
        if (!reading.hasServerSupportedValue || deviceId <= 0) return
        val resp = api.callWatchReading(
            url = notificationsUrl(),
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
            body = WatchReadingRequest(
                user_id = userId,
                device_id = deviceId,
                heart_rate = reading.heartRate,
                body_temp = reading.bodyTemp,
                battery_level = reading.batteryLevel,
            ),
        )
        if (!resp.isSuccessful || resp.body()?.ok != true) {
            throw IllegalStateException(resp.body()?.error ?: "워치 측정값 저장 실패: HTTP ${resp.code()}")
        }
    }
}

private fun notificationsUrl(): String =
    BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications"

private fun buildNotificationsApi(): NotificationsFunctionsApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NotificationsFunctionsApi::class.java)
