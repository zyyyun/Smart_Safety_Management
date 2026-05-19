package com.example.smart_safety_management.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.BuildConfig
import com.example.smart_safety_management.UserSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import java.time.Instant

/**
 * Phase 7 / 07-03 BRIDGE-03 — SettingDeviceManagement 의 'J2208A 워치' 섹션.
 *
 * - status 3 상태 (D-04):
 *     unpaired (mac_address NULL)               → 회색 "미등록"
 *     paired AND last_comm_at < 5분             → 초록 "연결됨"
 *     paired AND last_comm_at >= 5분 또는 NULL  → 노랑 "끊김"
 * - MAC TextField + 정규식 검증 (MacAddressValidator) + Edge Function 'watch-pair' 호출
 * - 등록 성공 → Realtime devices 채널이 status badge 갱신
 * - unpair 버튼: paired 상태에서만 노출
 */
enum class WatchStatus(val label: String, val color: Color) {
    UNPAIRED("미등록", Color.Gray),
    CONNECTED("연결됨", Color(0xFF22C55E)),
    DISCONNECTED("끊김", Color(0xFFFBBF24)),
}

internal fun computeStatus(device: DeviceRow?): WatchStatus {
    if (device == null || device.macAddress.isNullOrBlank()) return WatchStatus.UNPAIRED
    val lastComm = device.lastCommAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: return WatchStatus.DISCONNECTED
    val staleMin = Duration.between(lastComm, Instant.now()).toMinutes()
    return if (staleMin < 5) WatchStatus.CONNECTED else WatchStatus.DISCONNECTED
}

/** 페어링/언페어링 결과 모달 상태. null = 비표시. */
internal data class PairResultDialog(
    val title: String,
    val message: String,
    val isSuccess: Boolean,
)

@Composable
fun PairWatchSection(supabase: SupabaseClient) {
    var macInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf(false) }
    var resultDialog by remember { mutableStateOf<PairResultDialog?>(null) }
    var device by remember { mutableStateOf<DeviceRow?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { buildPairApi() }

    // 초기 fetch + Realtime 구독 (devices 의 본 user_id row UPDATE)
    // 2026-05-19 fix: 전체 try-catch 추가 — SupabaseClient by-lazy cold-start 또는
    // PostgREST/Realtime 첫 호출 예외가 LaunchedEffect 밖으로 propagation 되면 Compose
    // runtime → Activity crash → 사용자 "첫 진입 시 튕김" 증상. 예외 무시 + 로그만 남김
    // (다음 composition 또는 사용자 액션에서 재시도 가능).
    LaunchedEffect(Unit) {
        try {
            val userId = UserSession.userId ?: return@LaunchedEffect
            device = runCatching {
                supabase.from("devices").select {
                    filter {
                        eq("user_id", userId)
                        eq("device_type", "WATCH")
                    }
                    limit(1)
                }.decodeSingleOrNull<DeviceRow>()
            }.getOrElse { e ->
                android.util.Log.w("PairWatchSection", "Initial devices fetch failed: ${e.message}", e)
                null
            }

            val ch = supabase.channel("devices_user:$userId")
            val flow = ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "devices"
                filter("user_id", FilterOperator.EQ, userId)
            }
            try {
                ch.subscribe()
            } catch (e: Exception) {
                android.util.Log.w("PairWatchSection", "Realtime subscribe failed: ${e.message}", e)
                return@LaunchedEffect
            }
            try {
                flow.collectLatest { action ->
                    runCatching { device = action.decodeRecord<DeviceRow>() }
                        .onFailure { android.util.Log.w("PairWatchSection", "decodeRecord failed", it) }
                }
            } finally {
                runCatching { ch.unsubscribe() }
            }
        } catch (e: Exception) {
            android.util.Log.e("PairWatchSection", "LaunchedEffect fatal: ${e.message}", e)
        }
    }

    val status = computeStatus(device)

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("J2208A 워치", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            StatusBadge(status)
        }
        Spacer(Modifier.height(8.dp))
        if (status == WatchStatus.UNPAIRED) {
            OutlinedTextField(
                value = macInput,
                onValueChange = { macInput = it; isError = false },
                label = { Text("MAC 주소 (예: 21:02:02:06:01:69)") },
                isError = isError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val normalized = MacAddressValidator.normalize(macInput)
                    if (!MacAddressValidator.isValid(normalized)) {
                        isError = true
                        resultDialog = PairResultDialog(
                            title = "MAC 형식 오류",
                            message = "MAC 주소 형식이 올바르지 않습니다.\n예: 21:02:02:06:01:69 (콜론 5개)",
                            isSuccess = false,
                        )
                        return@Button
                    }
                    val userId = UserSession.userId
                    if (userId.isNullOrBlank()) {
                        resultDialog = PairResultDialog(
                            title = "로그인 필요",
                            message = "사용자 세션이 만료되었습니다. 앱을 재시작해주세요.",
                            isSuccess = false,
                        )
                        return@Button
                    }
                    pairing = true
                    scope.launch {
                        try {
                            val resp = api.callWatchPair(
                                url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                body = WatchPairRequest(
                                    user_id = userId,
                                    mac_address = normalized,
                                    op = "pair",
                                ),
                            )
                            resultDialog = when {
                                resp.isSuccessful && resp.body()?.ok == true -> PairResultDialog(
                                    title = "등록 완료",
                                    message = "워치가 정상적으로 등록되었습니다.\nMAC: $normalized",
                                    isSuccess = true,
                                )
                                resp.code() == 409 -> PairResultDialog(
                                    title = "등록 실패",
                                    message = "이미 다른 사용자에게 등록된 워치입니다.\n관리자에게 문의하세요.",
                                    isSuccess = false,
                                )
                                resp.code() == 400 -> PairResultDialog(
                                    title = "등록 실패",
                                    message = "MAC 주소 형식이 올바르지 않습니다 (서버 검증 실패).",
                                    isSuccess = false,
                                )
                                else -> PairResultDialog(
                                    title = "등록 실패",
                                    message = "서버 오류가 발생했습니다 (HTTP ${resp.code()}).\n잠시 후 다시 시도해주세요.",
                                    isSuccess = false,
                                )
                            }
                        } catch (e: Exception) {
                            resultDialog = PairResultDialog(
                                title = "네트워크 오류",
                                message = "서버와 통신할 수 없습니다.\n인터넷 연결을 확인해주세요.\n\n상세: ${e.message ?: e.javaClass.simpleName}",
                                isSuccess = false,
                            )
                        } finally {
                            pairing = false
                        }
                    }
                },
                enabled = !pairing && macInput.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(if (pairing) "등록 중..." else "등록")
            }
        } else {
            Text("MAC: ${device?.macAddress ?: "-"}", fontSize = 13.sp, color = Color.Gray)
            Button(
                onClick = {
                    val userId = UserSession.userId
                    if (userId.isNullOrBlank()) {
                        resultDialog = PairResultDialog(
                            title = "로그인 필요",
                            message = "사용자 세션이 만료되었습니다. 앱을 재시작해주세요.",
                            isSuccess = false,
                        )
                        return@Button
                    }
                    pairing = true
                    scope.launch {
                        try {
                            val resp = api.callWatchPair(
                                url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                body = WatchPairRequest(
                                    user_id = userId,
                                    op = "unpair",
                                ),
                            )
                            resultDialog = if (resp.isSuccessful) {
                                PairResultDialog(
                                    title = "해제 완료",
                                    message = "워치 페어링이 해제되었습니다.",
                                    isSuccess = true,
                                )
                            } else {
                                PairResultDialog(
                                    title = "해제 실패",
                                    message = "서버 오류 (HTTP ${resp.code()})",
                                    isSuccess = false,
                                )
                            }
                        } catch (e: Exception) {
                            resultDialog = PairResultDialog(
                                title = "네트워크 오류",
                                message = "서버와 통신할 수 없습니다.\n상세: ${e.message ?: e.javaClass.simpleName}",
                                isSuccess = false,
                            )
                        } finally {
                            pairing = false
                        }
                    }
                },
                enabled = !pairing,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(if (pairing) "처리 중..." else "해제")
            }
        }
    }

    // 결과 모달 (성공/실패 모두 표시)
    resultDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { resultDialog = null },
            title = {
                Text(
                    dialog.title,
                    color = if (dialog.isSuccess) Color(0xFF22C55E) else Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = { resultDialog = null }) {
                    Text("확인")
                }
            },
        )
    }
}

@Composable
private fun StatusBadge(status: WatchStatus) {
    Box(
        modifier = Modifier
            .background(status.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            status.label,
            color = status.color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun buildPairApi(): NotificationsFunctionsApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NotificationsFunctionsApi::class.java)
