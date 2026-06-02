package com.example.smart_safety_management.watch

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.BuildConfig
import com.example.smart_safety_management.UserSession
import com.example.smart_safety_management.watch.ble.JcWearBleBridge
import com.example.smart_safety_management.watch.ble.JcWearConnectionState
import com.example.smart_safety_management.watch.ble.JcWearDeviceRegistrar
import com.example.smart_safety_management.watch.ble.JcWearDiscoveredDevice
import com.example.smart_safety_management.watch.ble.WatchBleServiceController
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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

enum class WatchStatus(val label: String, val color: Color) {
    UNPAIRED("미등록", Color.Gray),
    CONNECTED("연결됨", Color(0xFF16A34A)),
    DISCONNECTED("끊김", Color(0xFFF59E0B)),
}

internal fun computeStatus(device: DeviceRow?, now: Instant = Instant.now()): WatchStatus {
    if (device == null || device.macAddress.isNullOrBlank()) return WatchStatus.UNPAIRED
    val lastComm = listOfNotNull(
        parseWatchInstant(device.lastCommAt),
        parseWatchInstant(device.updatedAt),
    ).maxOrNull()
        ?: return WatchStatus.DISCONNECTED
    val age = Duration.between(lastComm, now)
    return if (age <= Duration.ofMinutes(5)) WatchStatus.CONNECTED else WatchStatus.DISCONNECTED
}

private fun parseWatchInstant(value: String?): Instant? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }.getOrNull()
}

internal data class PairResultDialog(
    val title: String,
    val message: String,
    val isSuccess: Boolean,
)

@Composable
fun PairWatchSection(supabase: SupabaseClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bleBridge = remember { JcWearBleBridge(context.applicationContext) }
    val scanState by bleBridge.uiState.collectAsState()
    val registrar = remember { JcWearDeviceRegistrar() }
    val unpairApi = remember { buildPairApi() }

    var registering by remember { mutableStateOf(false) }
    var unpairing by remember { mutableStateOf(false) }
    var resultDialog by remember { mutableStateOf<PairResultDialog?>(null) }
    var device by remember { mutableStateOf<DeviceRow?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        bleBridge.refreshEnvironment()
        if (grants.values.all { it }) {
            bleBridge.startScan()
        } else {
            resultDialog = PairResultDialog(
                title = "권한 필요",
                message = "워치를 찾으려면 블루투스 권한을 허용해야 합니다.",
                isSuccess = false,
            )
        }
    }

    LaunchedEffect(Unit) {
        bleBridge.refreshEnvironment()
        val userId = UserSession.userId ?: return@LaunchedEffect
        device = runCatching {
            supabase.from("devices").select {
                filter {
                    eq("user_id", userId)
                    eq("device_type", "WATCH")
                }
                limit(1)
            }.decodeSingleOrNull<DeviceRow>()
        }.getOrNull()
        val fetchedDevice = device
        if (fetchedDevice?.macAddress?.isNotBlank() == true && fetchedDevice.lastCommAt.isNullOrBlank()) {
            runCatching {
                val resp = unpairApi.callWatchPair(
                    url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                    body = WatchPairRequest(
                        user_id = userId,
                        mac_address = fetchedDevice.macAddress,
                        op = "pair",
                    ),
                )
                val body = resp.body()
                if (resp.isSuccessful && body?.ok == true) {
                    val repairedAt = body.last_comm_at ?: Instant.now().toString()
                    device = fetchedDevice.copy(lastCommAt = repairedAt, updatedAt = repairedAt)
                }
            }
        }
        device?.let { WatchBleServiceController.configureAndStart(context, userId, it) }

        val ch = supabase.channel("devices_user:$userId")
        val flow = ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "devices"
            filter("user_id", FilterOperator.EQ, userId)
        }
        runCatching { ch.subscribe() }.onFailure { return@LaunchedEffect }
        try {
            flow.collectLatest { action ->
                runCatching { action.decodeRecord<DeviceRow>() }
                    .onSuccess {
                        if (it.deviceType == "WATCH") {
                            device = it
                            WatchBleServiceController.configureAndStart(context, userId, it)
                        }
                    }
            }
        } finally {
            runCatching { ch.unsubscribe() }
        }
    }

    DisposableEffect(bleBridge) {
        onDispose { bleBridge.close() }
    }

    val status = computeStatus(device)
    val selectedDevice = scanState.discoveredDevices.firstOrNull {
        it.address == scanState.selectedAddress
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("J2208A 스마트워치", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            StatusBadge(status)
        }
        Spacer(Modifier.height(8.dp))

        if (status == WatchStatus.UNPAIRED) {
            WatchScanPanel(
                devices = scanState.discoveredDevices,
                selectedAddress = scanState.selectedAddress,
                connectionState = scanState.connectionState,
                errorMessage = scanState.errorMessage,
                scanActionLabel = scanState.scanActionLabel,
                scanning = scanState.scanning,
                bluetoothEnabled = scanState.bluetoothEnabled,
                onScanAction = {
                    when {
                        !scanState.permissionGranted -> permissionLauncher.launch(watchBlePermissions())
                        scanState.scanning -> bleBridge.stopScan()
                        else -> bleBridge.startScan()
                    }
                },
                onIdentify = { bleBridge.identify(it) },
                onConnect = { bleBridge.connect(it) },
            )

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val userId = UserSession.userId
                    if (userId.isNullOrBlank() || selectedDevice == null) {
                        resultDialog = PairResultDialog(
                            title = "등록 불가",
                            message = "로그인 상태와 선택된 워치를 확인해주세요.",
                            isSuccess = false,
                        )
                        return@Button
                    }
                    registering = true
                    scope.launch {
                        try {
                            val registered = registrar.registerWatch(userId, selectedDevice)
                            device = registered
                            WatchBleServiceController.configureAndStart(context, userId, registered)
                            resultDialog = PairResultDialog(
                                title = "등록 완료",
                                message = "${selectedDevice.displayName} 워치를 이 계정에 연결했습니다.",
                                isSuccess = true,
                            )
                        } catch (e: Exception) {
                            resultDialog = PairResultDialog(
                                title = "등록 실패",
                                message = e.message ?: e.javaClass.simpleName,
                                isSuccess = false,
                            )
                        } finally {
                            registering = false
                        }
                    }
                },
                enabled = scanState.canRegister && !registering,
            ) {
                Text(if (registering) "등록 중..." else "선택한 워치 등록")
            }
        } else {
            RegisteredWatchPanel(
                device = device,
                unpairing = unpairing,
                onUnpair = {
                    val userId = UserSession.userId
                    if (userId.isNullOrBlank()) {
                        resultDialog = PairResultDialog(
                            title = "로그인 필요",
                            message = "사용자 세션을 확인할 수 없습니다.",
                            isSuccess = false,
                        )
                        return@RegisteredWatchPanel
                    }
                    unpairing = true
                    scope.launch {
                        try {
                            val resp = unpairApi.callWatchPair(
                                url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/notifications",
                                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                                body = WatchPairRequest(
                                    user_id = userId,
                                    op = "unpair",
                                ),
                            )
                            if (resp.isSuccessful) {
                                device = null
                                WatchBleServiceController.stopAndClear(context)
                                bleBridge.disconnect()
                                resultDialog = PairResultDialog(
                                    title = "해제 완료",
                                    message = "워치 연결 등록을 해제했습니다.",
                                    isSuccess = true,
                                )
                            } else {
                                resultDialog = PairResultDialog(
                                    title = "해제 실패",
                                    message = "서버 오류가 발생했습니다. HTTP ${resp.code()}",
                                    isSuccess = false,
                                )
                            }
                        } catch (e: Exception) {
                            resultDialog = PairResultDialog(
                                title = "해제 실패",
                                message = e.message ?: e.javaClass.simpleName,
                                isSuccess = false,
                            )
                        } finally {
                            unpairing = false
                        }
                    }
                },
            )
        }
    }

    resultDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { resultDialog = null },
            title = {
                Text(
                    dialog.title,
                    color = if (dialog.isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626),
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
private fun WatchScanPanel(
    devices: List<JcWearDiscoveredDevice>,
    selectedAddress: String?,
    connectionState: JcWearConnectionState,
    errorMessage: String?,
    scanActionLabel: String,
    scanning: Boolean,
    bluetoothEnabled: Boolean,
    onScanAction: () -> Unit,
    onIdentify: (JcWearDiscoveredDevice) -> Unit,
    onConnect: (JcWearDiscoveredDevice) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "스마트폰 블루투스로 주변 J2208A 워치를 찾아 연결합니다.",
                color = Color(0xFF4B5563),
                fontSize = 13.sp,
            )
            Button(onClick = onScanAction) {
                Text(scanActionLabel)
            }
            if (!bluetoothEnabled) {
                Text("블루투스가 꺼져 있습니다.", color = Color(0xFFDC2626), fontSize = 13.sp)
            }
            errorMessage?.let {
                Text(it, color = Color(0xFFDC2626), fontSize = 13.sp)
            }
            if (devices.isEmpty()) {
                Text(
                    if (scanning) "주변 워치를 찾는 중입니다." else "스캔을 시작하면 발견된 워치가 여기에 표시됩니다.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                )
            } else {
                devices.forEach { discovered ->
                    DiscoveredWatchRow(
                        device = discovered,
                        selected = discovered.address == selectedAddress,
                        connectionState = connectionState,
                        onIdentify = { onIdentify(discovered) },
                        onConnect = { onConnect(discovered) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredWatchRow(
    device: JcWearDiscoveredDevice,
    selected: Boolean,
    connectionState: JcWearConnectionState,
    onIdentify: () -> Unit,
    onConnect: () -> Unit,
) {
    val borderColor = if (selected) Color(0xFF2563EB) else Color(0xFFE5E7EB)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(device.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${device.address} · ${device.rssiLabel}", color = Color.Gray, fontSize = 12.sp)
        }
        IconButton(onClick = onIdentify, enabled = connectionState != JcWearConnectionState.CONNECTING) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "${device.displayName} 식별 진동",
                tint = Color(0xFF6D4C9F),
            )
        }
        Spacer(Modifier.width(4.dp))
        OutlinedButton(
            onClick = onConnect,
            enabled = connectionState != JcWearConnectionState.CONNECTING,
        ) {
            val label = when {
                selected && isConnectedWatchState(connectionState) -> "연결됨"
                selected && connectionState == JcWearConnectionState.CONNECTED -> "연결됨"
                selected && connectionState == JcWearConnectionState.CONNECTING -> "연결 중"
                else -> "연결"
            }
            Text(label)
        }
    }
}

private fun isConnectedWatchState(connectionState: JcWearConnectionState): Boolean =
    connectionState == JcWearConnectionState.CONNECTED ||
        connectionState == JcWearConnectionState.READING

@Composable
private fun RegisteredWatchPanel(
    device: DeviceRow?,
    unpairing: Boolean,
    onUnpair: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("등록된 워치", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("식별값: ${device?.serialNumber ?: device?.macAddress ?: "-"}", color = Color(0xFF4B5563), fontSize = 13.sp)
            Text("마지막 통신: ${device?.lastCommAt ?: "-"}", color = Color.Gray, fontSize = 12.sp)
            OutlinedButton(onClick = onUnpair, enabled = !unpairing) {
                Text(if (unpairing) "해제 중..." else "등록 해제")
            }
        }
    }
}

@Composable
private fun StatusBadge(status: WatchStatus) {
    Box(
        modifier = Modifier
            .background(status.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            status.label,
            color = status.color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun watchBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun buildPairApi(): NotificationsFunctionsApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NotificationsFunctionsApi::class.java)
