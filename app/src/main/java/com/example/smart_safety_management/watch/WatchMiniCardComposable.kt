package com.example.smart_safety_management.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

/**
 * 2026-05-21 — HomeActivity 상단 profile_bar 우측에 표시되는 컴팩트 워치 카드.
 *
 * WatchCardComposable 의 mini 버전:
 *   - 가로 ~100dp, 세로 ~64dp
 *   - 상태 색상 dot + HR 큰 숫자 + 상태 라벨
 *   - 탭 → WatchDetailActivity 진입 (detail 화면 = 상세 신체 정보)
 *
 * 데이터 source 는 WatchCardComposable 와 동일 (device_watches + wear_state + safety_alerts).
 * Realtime/polling fallback 도 동일 패턴.
 */
@Composable
fun WatchMiniCardComposable(
    deviceId: Int,
    supabase: SupabaseClient,
    onCardTap: () -> Unit,
) {
    var snapshot by remember(deviceId) { mutableStateOf<DeviceWatchSnapshot?>(null) }
    var lastWearState by remember(deviceId) { mutableStateOf<String?>(null) }
    var lastActiveAlert by remember(deviceId) { mutableStateOf<SafetyAlertRow?>(null) }
    val realtimeStatus by supabase.realtime.status.collectAsState()
    val repo = remember { WatchRealtimeRepository(supabase) }

    LaunchedEffect(deviceId, realtimeStatus) {
        if (realtimeStatus == Realtime.Status.CONNECTED) {
            launch { repo.deviceWatchFlow(deviceId).collectLatest { snapshot = it } }
            launch { repo.lastWearStateFlow(deviceId).collectLatest { lastWearState = it.toState } }
            launch {
                repo.safetyAlertsFlow(deviceId).collectLatest { list ->
                    lastActiveAlert = list.firstOrNull { it.resolvedAt == null }
                }
            }
        } else {
            // polling fallback 5s — device_watches PK = device_id, 1 row 만 존재 → order 불필요.
            // 2026-05-21: order("updated_at") 제거 (DB 에 컬럼 없음, BadRequestRestException crash 회피).
            // try-catch 추가 — network/schema 변경 시 main thread crash 방지.
            while (true) {
                try {
                    snapshot = supabase.from("device_watches").select {
                        filter { eq("device_id", deviceId) }
                        limit(1)
                    }.decodeSingleOrNull()
                    lastActiveAlert = supabase.from("safety_alerts").select {
                        filter { eq("device_id", deviceId) }
                        order("raised_at", Order.DESCENDING)
                        limit(20)
                    }.decodeList<SafetyAlertRow>().firstOrNull { it.resolvedAt == null }
                } catch (_: Exception) {
                    // silent — polling 은 best-effort. Realtime 가 곧 CONNECTED 되면 자동 복구.
                }
                delay(5_000)
            }
        }
    }

    val hrLevel = WatchHealthFormatter.classifyHr(snapshot?.heartRate, lastWearState)
    val (statusText, statusColor) = WatchHealthFormatter.overallStatus(
        snapshot, lastWearState, lastActiveAlert,
    )

    Card(
        modifier = Modifier
            .height(56.dp)
            .clickable { onCardTap() },
        colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 상태 dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Column(verticalArrangement = Arrangement.SpaceBetween) {
                // HR 큰 숫자
                Text(
                    WatchHealthFormatter.hrDisplay(snapshot?.heartRate, lastWearState),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                // 상태 텍스트 (정상 운용 중 / 주의 — / 위험 —)
                Text(
                    statusText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 페어링 안 됨 — 옅은 회색 카드. 탭하면 PairWatchSection 화면 (DeviceManage) 진입.
 */
@Composable
fun EmptyWatchMiniCard(onCardTap: () -> Unit) {
    Card(
        modifier = Modifier
            .height(56.dp)
            .clickable { onCardTap() },
        colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(WatchHealthFormatter.ColorIdle),
            )
            Column {
                Text(
                    "워치 미연결",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "탭하여 등록",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
