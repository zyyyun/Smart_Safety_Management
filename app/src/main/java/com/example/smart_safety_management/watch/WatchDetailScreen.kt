package com.example.smart_safety_management.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun WatchDetailScreen(
    deviceId: Int,
    supabase: SupabaseClient,
    onBack: () -> Unit,
) {
    if (deviceId <= 0) {
        InvalidDeviceState(onBack)
        return
    }

    var snapshot by remember { mutableStateOf<DeviceWatchSnapshot?>(null) }
    var lastWearStateRow by remember { mutableStateOf<WearStateEventRow?>(null) }
    var allAlerts by remember { mutableStateOf<List<SafetyAlertRow>>(emptyList()) }
    var device by remember { mutableStateOf<DeviceRow?>(null) }
    val realtimeStatus by supabase.realtime.status.collectAsState()
    val repo = remember { WatchRealtimeRepository(supabase) }

    // device 정보 (mac, last_comm_at, battery) 단발성 fetch
    LaunchedEffect(deviceId) {
        device = try {
            supabase.from("devices").select {
                filter { eq("device_id", deviceId) }
                limit(1)
            }.decodeSingleOrNull<DeviceRow>()
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(deviceId, realtimeStatus) {
        if (realtimeStatus == Realtime.Status.CONNECTED) {
            launch { repo.deviceFlow(deviceId).collectLatest { device = it } }
            launch { repo.deviceWatchFlow(deviceId).collectLatest { snapshot = it } }
            launch { repo.lastWearStateFlow(deviceId).collectLatest { lastWearStateRow = it } }
            launch {
                repo.safetyAlertsFlow(deviceId).collectLatest { allAlerts = it }
            }
        } else {
            // polling fallback — device_watches PK = device_id (1 row). order 불필요.
            // 2026-05-21: crash 방지 try-catch + order("updated_at") 제거 (DB 컬럼 없음).
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

    val wearState = lastWearStateRow?.toState
    val activeAlert = WatchActiveAlertSelector.select(allAlerts, wearState)
    val (overallText, overallColor) = WatchHealthFormatter.overallStatus(
        snapshot, wearState, activeAlert,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFF3F4F6)),
    ) {
        // ── Top bar ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
            }
            Text("내 신체 정보", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "device_id $deviceId",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1) 종합 상태 헤더
            OverallStatusCard(overallText, overallColor, device, snapshot)

            // 2) 심박 카드
            HrCard(snapshot?.heartRate, wearState)

            // 3) 체온 카드
            TempCard(snapshot?.bodyTemp, wearState)

            // 4) 착용 상태 카드
            WearStateCard(lastWearStateRow)

            // 5) 배터리 + 마지막 통신
            DeviceMetaCard(snapshot?.batteryLevel ?: device?.batteryLevel, device?.lastCommAt)

            // 6) 최근 알림 list
            AlertHistoryCard(allAlerts.take(10))

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Sub-cards
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun OverallStatusCard(
    text: String,
    color: Color,
    device: DeviceRow?,
    snapshot: DeviceWatchSnapshot?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("종합 상태", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val mac = device?.macAddress ?: "—"
            val updated = snapshot?.updatedAt ?: device?.updatedAt
            Text(
                "J2208A · MAC $mac · 마지막 측정 ${WatchHealthFormatter.relativeTime(updated)}",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HrCard(hr: Int?, wearState: String?) {
    val level = WatchHealthFormatter.classifyHr(hr, wearState)
    val color = WatchHealthFormatter.levelToColor(level)
    val label = WatchHealthFormatter.hrLabel(level)
    MetricCard(
        title = "심박 (HR)",
        valueText = WatchHealthFormatter.hrDisplay(hr, wearState),
        levelColor = color,
        statusLabel = label,
        rangeText = "정상 범위 60–100 bpm",
        valueRatio = hr?.takeIf { it > 0 }?.let { (it.coerceIn(40, 160) - 40) / 120f },
        valueColor = color,
    )
}

@Composable
private fun TempCard(temp: Float?, wearState: String?) {
    val level = WatchHealthFormatter.classifyTemp(temp, wearState)
    val color = WatchHealthFormatter.levelToColor(level)
    val label = WatchHealthFormatter.tempLabel(level)
    MetricCard(
        title = "체온",
        valueText = WatchHealthFormatter.tempDisplay(temp, wearState),
        levelColor = color,
        statusLabel = label,
        rangeText = "정상 범위 36.0–37.5°C",
        valueRatio = temp?.takeIf { it > 0f }?.let { ((it.coerceIn(34f, 40f)) - 34f) / 6f },
        valueColor = color,
    )
}

@Composable
private fun MetricCard(
    title: String,
    valueText: String,
    levelColor: Color,
    statusLabel: String,
    rangeText: String,
    valueRatio: Float?,
    valueColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape).background(levelColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(valueText, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(Modifier.height(4.dp))
            Text(statusLabel, color = levelColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(rangeText, color = Color.Gray, fontSize = 11.sp)
            if (valueRatio != null) {
                Spacer(Modifier.height(8.dp))
                GaugeBar(ratio = valueRatio.coerceIn(0f, 1f), color = levelColor)
            }
        }
    }
}

@Composable
private fun GaugeBar(ratio: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFFE5E7EB)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

@Composable
private fun WearStateCard(row: WearStateEventRow?) {
    val state = row?.toState
    val label = WatchHealthFormatter.wearStateLabel(state)
    val color = WatchHealthFormatter.wearStateColor(state)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text("착용 상태", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = color)
            if (row != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "전이 ${WatchHealthFormatter.wearStateLabel(row.fromState)} → ${WatchHealthFormatter.wearStateLabel(row.toState)} (${WatchHealthFormatter.relativeTime(row.ts)})",
                    color = Color.Gray, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun DeviceMetaCard(batteryLevel: Int?, lastCommAt: String?) {
    val battLevel = WatchHealthFormatter.batteryLevel(batteryLevel)
    val battColor = WatchHealthFormatter.levelToColor(battLevel)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("배터리", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        WatchHealthFormatter.batteryDisplay(batteryLevel),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = battColor,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("마지막 통신", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        WatchHealthFormatter.relativeTime(lastCommAt),
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertHistoryCard(alerts: List<SafetyAlertRow>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("최근 위험 알림", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            if (alerts.isEmpty()) {
                Text("최근 발생한 알림이 없습니다.", color = Color.Gray, fontSize = 13.sp)
            } else {
                alerts.forEachIndexed { idx, a ->
                    AlertRow(a)
                    if (idx < alerts.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(a: SafetyAlertRow) {
    val sevColor = WatchHealthFormatter.severityColor(a.severity)
    val resolved = a.resolvedAt != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (resolved) Color(0xFFD1D5DB) else sevColor))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${WatchHealthFormatter.severityKorean(a.severity)} — ${WatchHealthFormatter.alertTypeKorean(a.alertType)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (resolved) Color.Gray else Color.Black,
            )
            Text(
                "${WatchHealthFormatter.relativeTime(a.raisedAt)}${if (resolved) " · 해제됨" else ""}",
                fontSize = 11.sp, color = Color.Gray,
            )
        }
    }
}

@Composable
private fun InvalidDeviceState(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("워치 정보를 불러올 수 없습니다.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text("device_id 가 전달되지 않았거나 페어링이 해제되었습니다.", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.TextButton(onClick = onBack) {
            Text("뒤로 가기")
        }
    }
}
