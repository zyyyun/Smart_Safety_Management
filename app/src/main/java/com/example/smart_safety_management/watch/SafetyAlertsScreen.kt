package com.example.smart_safety_management.watch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.BuildConfig
import com.example.smart_safety_management.UserSession
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Phase 7 / 07-03 BRIDGE-02 — 워치 알림 전용 LazyColumn.
 *
 * - Realtime safetyAlertsFlow 구독으로 실시간 갱신
 * - 미해결 (resolved_at IS NULL AND ack_at IS NULL) alert 만 acknowledge 버튼 노출
 * - 클릭 → Edge Function 'watch-ack' POST → 응답 200 + Realtime UPDATE 가 카드 색상 변경
 * - Idempotency: 두 번째 ack 시 404 → "이미 확인됨" 메시지 (예외 throw X)
 * - 하단 fine print: "1차 경고용, 의료기기 아님" (PROJECT.md key decision)
 */
@Composable
fun SafetyAlertsScreen(
    deviceId: Int,
    supabase: SupabaseClient,
    @Suppress("UNUSED_PARAMETER") highlightAlertId: Long? = null,
) {
    var alerts by remember { mutableStateOf<List<SafetyAlertRow>>(emptyList()) }
    var ackInFlight by remember { mutableStateOf<Long?>(null) }
    var ackResultMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val repo = remember { WatchRealtimeRepository(supabase) }
    val api = remember { buildNotificationsApi() }

    LaunchedEffect(deviceId) {
        repo.safetyAlertsFlow(deviceId).collectLatest { alerts = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(alerts) { a ->
                val isUnacked = a.resolvedAt == null && a.ackAt == null
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            alertTitleFor(a),
                            fontWeight = FontWeight.Bold,
                            color = if (isUnacked) Color(0xFFEF4444) else Color.Gray,
                            fontSize = 16.sp,
                        )
                        Text("발생 ${a.raisedAt}", fontSize = 12.sp, color = Color.Gray)
                        if (a.ackAt != null) {
                            Text("확인 ${a.ackAt}", fontSize = 12.sp, color = Color.Gray)
                        }
                        if (isUnacked) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val userId = UserSession.userId ?: return@Button
                                    ackInFlight = a.alertId
                                    scope.launch {
                                        try {
                                            val resp = api.callWatchAck(
                                                url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                                auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                                body = WatchAckRequest(alert_id = a.alertId, user_id = userId),
                                            )
                                            ackResultMsg = when {
                                                resp.isSuccessful && resp.body()?.ok == true -> "확인됨"
                                                resp.code() == 404 -> "이미 확인됨"
                                                else -> "오류 (${resp.code()})"
                                            }
                                        } catch (e: Exception) {
                                            ackResultMsg = "네트워크 오류"
                                        } finally {
                                            ackInFlight = null
                                        }
                                    }
                                },
                                enabled = ackInFlight != a.alertId,
                            ) {
                                Text(if (ackInFlight == a.alertId) "확인 중..." else "확인")
                            }
                        }
                    }
                }
            }
        }
        ackResultMsg?.let {
            Text(it, modifier = Modifier.padding(8.dp), color = Color.Gray, fontSize = 13.sp)
        }
        // 의료기기 면책 fine print (PROJECT.md key decision).
        Text(
            "1차 경고용, 의료기기 아님",
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            color = Color.Gray,
            fontSize = 11.sp,
        )
    }
}

internal fun alertTitleFor(a: SafetyAlertRow): String = when (a.alertType) {
    "TACHY"      -> "빈맥 의심"
    "REMOVED"    -> "워치 미착용"
    "COMMS_LOST" -> "통신 두절"
    else         -> a.alertType
}

internal fun buildNotificationsApi(): NotificationsFunctionsApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NotificationsFunctionsApi::class.java)
