package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.watch.DeviceRow
import com.example.smart_safety_management.watch.SafetyAlertsScreen
import com.example.smart_safety_management.watch.SupabaseModule
import io.github.jan.supabase.postgrest.from

/**
 * Phase 7 / 07-03 BRIDGE-02 — 워치 알림 전용 화면 (AIEventActivity 와 별도).
 *
 * - testuser1 의 paired device_id 조회 후 SafetyAlertsScreen 호출
 * - FCM watch_alert 진입 시 alert_id extras 로 강조 (현재는 highlightAlertId 만 전달,
 *   v1.1 에서 LazyColumn 의 scrollToItem + 강조 색상 추가)
 * - alert_id extras 는 신뢰 X — DB 재조회 (Realtime safetyAlertsFlow) 가 source of truth.
 */
class SafetyAlertsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val highlightAlertId = intent.getLongExtra("alert_id", -1L).takeIf { it > 0 }
        val supabase = SupabaseModule.client(this)
        setContent {
            Smart_Safety_ManagementTheme {
                var deviceId by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    val userId = UserSession.userId ?: return@LaunchedEffect
                    deviceId = supabase.from("devices").select {
                        filter {
                            eq("user_id", userId)
                            eq("device_type", "WATCH")
                        }
                        limit(1)
                    }.decodeSingleOrNull<DeviceRow>()?.deviceId
                }
                deviceId?.let { id ->
                    SafetyAlertsScreen(
                        deviceId = id,
                        supabase = supabase,
                        highlightAlertId = highlightAlertId,
                    )
                }
            }
        }
    }
}
