package com.example.smart_safety_management

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LocationService : Service(), LocationListener {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var locationManager: LocationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 서비스가 시작되면 Foreground로 전환하고 위치 추적 시작
        startForegroundService()
        startLocationTracking()
        return START_STICKY // 시스템에 의해 종료되어도 다시 시작
    }

    private fun startForegroundService() {
        val channelId = "location_channel"
        val channelName = "Location Tracking"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("위치 정보 전송 중")
            .setContentText("근로자 안전을 위해 위치 정보를 주기적으로 전송합니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Android 14 이상에서는 foregroundServiceType 명시 필요
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } catch (e: Exception) {
                startForeground(1, notification)
            }
        } else {
            startForeground(1, notification)
        }
    }

    private fun startLocationTracking() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                // [수정] 실시간 위치 업데이트 요청 (최소 시간 5초, 최소 거리 0m)
                // 에뮬레이터에서 위치 변경 시 즉시 반응하도록 설정
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 0f, this)
                locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, this)
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Error starting location updates", e)
        }
    }

    // [추가] 위치가 변경될 때마다 호출되는 콜백
    override fun onLocationChanged(location: Location) {
        serviceScope.launch {
            val userId = UserSession.userId
            if (userId != null) {
                val request = UpdateWorkerLocationRequest(userId, location.latitude, location.longitude, null)
                RetrofitClient.instance.updateWorkerLocation(request).enqueue(object : Callback<UpdateWorkerLocationResponse> {
                    override fun onResponse(call: Call<UpdateWorkerLocationResponse>, response: Response<UpdateWorkerLocationResponse>) {}
                    override fun onFailure(call: Call<UpdateWorkerLocationResponse>, t: Throwable) {
                        Log.e("LocationService", "Location update failed", t)
                    }
                })
            }
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        super.onDestroy()
        // 서비스 종료 시 위치 업데이트 중단
        locationManager?.removeUpdates(this)
        serviceJob.cancel() // 서비스 종료 시 코루틴 취소
    }
}