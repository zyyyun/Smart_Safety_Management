package com.example.smart_safety_management.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Phase 7 / 07-03 BRIDGE-01 — HomeWorkerActivity 의 워치 카드 (HR/temp/wear-state/last-alert).
 *
 * - Realtime 3 채널 구독 (device_watches / wear_state_events / safety_alerts)
 * - SUBSCRIBED 가 아닐 때 5초 polling fallback (D-01)
 * - 의료기기 면책 (PROJECT.md key decision):
 *   - HR=0 또는 wear-state ∈ {WARMUP, OFF} 시 HR/temp 회색 + "—" 표기 (신호=상태신호)
 *   - WearStateLabel 의 라벨은 의료기기 측정 표현 사용 금지 (PROJECT.md key decision)
 * - 마지막 활성 alert (resolved_at IS NULL) 1건만 노출 — 알림 전이 원칙 (D-09 from Phase 4)
 */
@Composable
fun WatchCardComposable(
    deviceId: Int,
    supabase: SupabaseClient,
    onCardTap: () -> Unit,
) {
    var snapshot by remember { mutableStateOf<DeviceWatchSnapshot?>(null) }
    var device by remember { mutableStateOf<DeviceRow?>(null) }
    var lastWearState by remember { mutableStateOf<String?>(null) }
    var allAlerts by remember { mutableStateOf<List<SafetyAlertRow>>(emptyList()) }
    val realtimeStatus by supabase.realtime.status.collectAsState()
    val repo = remember { WatchRealtimeRepository(supabase) }

    LaunchedEffect(deviceId, realtimeStatus) {
        if (realtimeStatus == Realtime.Status.CONNECTED) {
            // Realtime path
            launch { repo.deviceFlow(deviceId).collectLatest { device = it } }
            launch { repo.deviceWatchFlow(deviceId).collectLatest { snapshot = it } }
            launch { repo.lastWearStateFlow(deviceId).collectLatest { lastWearState = it.toState } }
            launch {
                repo.safetyAlertsFlow(deviceId).collectLatest { list ->
                    allAlerts = list
                }
            }
        } else {
            // D-01 polling fallback (5초)
            // 2026-05-21: device_watches PK = device_id (1 row). order 불필요.
            // crash 방지 try-catch + order("updated_at") 제거.
            while (true) {
                try {
                    device = supabase.from("devices").select {
                        filter { eq("device_id", deviceId) }
                        limit(1)
                    }.decodeSingleOrNull()
                    snapshot = supabase.from("device_watches").select {
                        filter { eq("device_id", deviceId) }
                        limit(1)
                    }.decodeSingleOrNull()
                    allAlerts = supabase.from("safety_alerts").select {
                        filter { eq("device_id", deviceId) }
                        order("raised_at", Order.DESCENDING)
                        limit(20)
                    }.decodeList()
                } catch (_: Exception) {
                    // silent — polling best-effort
                }
                delay(5_000)
            }
        }
    }

    val lastActiveAlert = WatchActiveAlertSelector.select(allAlerts, lastWearState)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCardTap,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("J2208A 워치", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 신호=상태신호 원칙: HR=0 또는 wear-state WARMUP/OFF 일 때 회색 처리.
                val isWarming = lastWearState in listOf("WARMUP", "OFF")
                val hrText = snapshot?.heartRate?.takeIf { it > 0 && !isWarming }?.let { "$it bpm" } ?: "—"
                val tempText = snapshot?.bodyTemp?.takeIf { !isWarming }?.let { String.format("%.1f°C", it) } ?: "—"
                val color = if (isWarming) Color.Gray else Color.Black
                Text(hrText, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(tempText, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            WearStateLabel(lastWearState)
            Spacer(Modifier.height(6.dp))
            Text(
                device?.batteryLevel?.let { "배터리 $it%" } ?: "배터리 --",
                color = Color.Gray,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                lastActiveAlert?.let { "⚠ ${alertTitle(it)} ${it.raisedAt.takeLast(8).take(5)}" }
                    ?: "정상 운용 중",
                color = if (lastActiveAlert != null) Color(0xFFEF4444) else Color.Gray,
                fontSize = 13.sp,
            )
        }
    }
}

internal fun alertTitle(a: SafetyAlertRow): String = when (a.alertType) {
    "TACHY"      -> "빈맥 의심"
    "REMOVED"    -> "워치 미착용"
    "COMMS_LOST" -> "통신 두절"
    else         -> a.alertType
}

/**
 * 페어링 안 됨 시 표시되는 카드 — SettingDeviceManagement 진입 유도.
 */
@Composable
fun EmptyWatchPrompt(onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("J2208A 워치 등록 필요", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("탭하여 등록 화면으로 이동", color = Color.Gray, fontSize = 13.sp)
        }
    }
}
