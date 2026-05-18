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

        // Phase 7 / 07-03 BRIDGE-02 — data payload 'type=watch_alert' 분기.
        // FCM payload (Phase 4 D-11): { type:"watch_alert", alert_type, severity, alert_id }
        val data = remoteMessage.data
        if (data["type"] == "watch_alert") {
            showWatchAlertNotification(
                title = remoteMessage.notification?.title ?: "워치 알림",
                body = remoteMessage.notification?.body ?: data["alert_type"] ?: "이벤트 발생",
                alertId = data["alert_id"]?.toLongOrNull() ?: -1L,
                alertType = data["alert_type"] ?: "",
            )
            return
        }

        // Phase 9 / 09-03 TBM-02 — data payload 'type=tbm_alert' 분기 (D-09).
        // FCM payload: { type:"tbm_alert", action_in_app, session_id, work_type }
        // - action_in_app == "tbm-started" → worker = TbmWorkerActivity, manager = TbmDashboardActivity
        // - action_in_app == "tbm-missed"  → worker = TbmWorkerActivity (직접 진입)
        // - action_in_app == "tbm-ended"   → 표시만 (notification tray)
        // channel_id = fcm_default_channel (Option B, Android 코드 변경 0 원칙)
        if (data["type"] == "tbm_alert") {
            showTbmAlertNotification(
                title = remoteMessage.notification?.title ?: "TBM 알림",
                body = remoteMessage.notification?.body ?: data["action_in_app"] ?: "TBM 세션 알림",
                sessionId = data["session_id"]?.toLongOrNull() ?: -1L,
                actionInApp = data["action_in_app"] ?: "",
            )
            return
        }

        // 일반 알림은 기존 흐름 유지 (data['type'] 없음 + notification 만 있는 경우)
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

    /**
     * Phase 7 / 07-03 BRIDGE-02 — 워치 알림 전용 채널 (watch_alerts) +
     * pendingIntent → SafetyAlertsActivity (alert_id extras).
     *
     * extras 의 alert_id 는 신뢰 X — SafetyAlertsScreen 진입 시 DB 재조회 (T-7-07 mitigate).
     */
    private fun showWatchAlertNotification(title: String, body: String, alertId: Long, alertType: String) {
        val intent = Intent(this, SafetyAlertsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (alertId > 0) putExtra("alert_id", alertId)
            putExtra("alert_type", "watch_alert")
            putExtra("watch_alert_type", alertType)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            alertId.toInt().takeIf { it > 0 } ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "watch_alerts"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "워치 안전 알림",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        // alert_id 를 notification id 로 사용 → 같은 alert 재push 시 갱신만 (D-09 알림 전이)
        notificationManager.notify(alertId.toInt().takeIf { it > 0 } ?: 1, notificationBuilder.build())
    }

    /**
     * Phase 9 / 09-03 TBM-02 — TBM 알림 push 처리 (D-09).
     *
     * channel_id = fcm_default_channel (Option B, Phase 8 일관) — 신규 채널 생성 X,
     * Android 코드 변경 0 원칙. v1.1 에서 'tbm_alerts' 채널 분리 검토.
     *
     * pendingIntent → UserRole 분기 (D-09):
     *   - MANAGER → TbmDashboardActivity (extras 부재 — Activity 가 자체 fetch)
     *   - WORKER  → TbmWorkerActivity (extras EXTRA_SESSION_ID 전달)
     *
     * extras 의 session_id 는 신뢰 X (T-9-12 mitigation) — Activity 진입 시 DB 재조회로
     * 권한/상태 확인. session_id 는 hint 로만 사용 (Phase 7 D-02 anti-pattern 회피).
     */
    private fun showTbmAlertNotification(
        title: String,
        body: String,
        sessionId: Long,
        @Suppress("UNUSED_PARAMETER") actionInApp: String,
    ) {
        // UserRole 분기 — manager = Dashboard, worker = WorkerActivity
        val targetActivity = if (UserSession.userRole == UserRole.MANAGER) {
            TbmDashboardActivity::class.java
        } else {
            TbmWorkerActivity::class.java
        }

        val intent = Intent(this, targetActivity).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (sessionId > 0 && targetActivity == TbmWorkerActivity::class.java) {
                putExtra(TbmWorkerActivity.EXTRA_SESSION_ID, sessionId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            sessionId.toInt().takeIf { it > 0 } ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Option B (Phase 8 일관, Pitfall 3 회피): fcm_default_channel 재사용.
        val channelId = "fcm_default_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "기본 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        // session_id 를 notification id 로 사용 → 같은 세션 재push 시 갱신만 (D-09 알림 전이)
        notificationManager.notify(
            sessionId.toInt().takeIf { it > 0 } ?: 2,
            notificationBuilder.build(),
        )
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