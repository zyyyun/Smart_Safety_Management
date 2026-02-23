package com.example.smart_safety_management

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // 새로운 토큰이 생성될 때 호출됨 (앱 최초 실행 시 등)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token: $token")

        // TODO: 이 토큰을 서버로 전송하여 DB에 저장해야 합니다.
        // UserSession.userId가 있다면 바로 전송하고, 없다면 로그인 시점에 전송하도록 구현해야 합니다.
        sendRegistrationToServer(token)
    }

    // 앱이 포그라운드(켜져있는) 상태에서 알림을 받았을 때 호출됨
    // (백그라운드 상태에서는 시스템이 자동으로 알림을 띄워주므로 이 함수가 호출되지 않음)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // 알림 내용이 있다면 직접 알림을 생성해서 보여줌
        remoteMessage.notification?.let {
            showNotification(it.title, it.body)
        }
    }

    private fun showNotification(title: String?, body: String?) {
        val intent = Intent(this, MainActivity::class.java) // 알림 클릭 시 이동할 액티비티
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "fcm_default_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 알림 아이콘 (res/drawable에 있어야 함)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 안드로이드 8.0(Oreo) 이상을 위한 채널 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "기본 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    private fun sendRegistrationToServer(token: String) {
        val userId = UserSession.userId
        if (userId != null) {
            val request = UpdateFcmTokenRequest(userId, token)
            RetrofitClient.instance.updateFcmToken(request).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "FCM 토큰 서버 전송 성공")
                    } else {
                        Log.e(TAG, "FCM 토큰 서버 전송 실패: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e(TAG, "FCM 토큰 서버 전송 에러", t)
                }
            })
        }
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}