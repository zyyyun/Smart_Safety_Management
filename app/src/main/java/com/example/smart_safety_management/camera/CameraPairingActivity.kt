package com.example.smart_safety_management.camera

// 2026-05-21 — Sprint A.2 (PC 의존성 제거 plan A.2.1 + A.2.3)
//
// Drift X3 카메라 QR 페어링 흐름:
//   1. 사용자가 카메라 이름 입력 (Drift X3는 이동식이므로 설치 구역/주소는 앱에서 받지 않음)
//   2. WiFi 정보 입력 (폰의 현재 SSID 자동 감지 + 비밀번호 수동 입력)
//   3. QR 생성: "17|<SSID>|<PASSWORD>|rtsp"
//      └ Drift X3 매뉴얼 기반 프로토콜 (코드 ref 0건, 사용자 매뉴얼 reading 의존).
//        실시연 실패 시 여기서 ─ commit message 에도 unverified 표기.
//   4. 카메라가 QR 스캔 → WiFi 접속 → 사용자가 카메라 IP 입력 후 등록
//      └ rtsp://${ip}/live URL 패턴은 Drift X3 검증된 표준 (memory: 검증 완료).
//
// Plan 의 A.2.2 (WifiNetworkSpecifier 자동 연결) 는 의도적 skip — QR fallback 이
// primary path 이며 Android 10+ 자동 연결은 UX 만 복잡하게 함.
//
// AndroidManifest 추가 권한: ACCESS_WIFI_STATE (SSID 읽기), 기존 ACCESS_FINE_LOCATION 재사용.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.smart_safety_management.RegisterCameraRequest
import com.example.smart_safety_management.RegisterCameraResponse
import com.example.smart_safety_management.RetrofitClient
import com.example.smart_safety_management.UserSession
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.InetSocketAddress
import java.net.Socket

class CameraPairingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Smart_Safety_ManagementTheme {
                Surface(color = MaterialTheme.colors.background) {
                    CameraPairingScreen(onDone = { finish() })
                }
            }
        }
    }
}

private const val MOBILE_CAMERA_AREA = "이동식"
private const val MOBILE_CAMERA_ADDRESS = "이동식 카메라"
private const val WIFI_PREFS_NAME = "camera_pairing_wifi"
private const val WIFI_PASSWORD_KEY_PREFIX = "password:"

@Composable
fun CameraPairingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Step 1: 카메라 이름
    var deviceName by remember { mutableStateOf("Drift X3") }

    // Step 2: WiFi
    var ssid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) {
            ssid = readCurrentSsid(ctx) ?: ssid
        } else {
            Toast.makeText(ctx, "위치 권한 거부 — SSID 수동 입력해 주세요", Toast.LENGTH_LONG).show()
        }
    }
    // 진입 시 1회 자동 감지 시도
    LaunchedEffect(Unit) {
        if (hasLocationPermission && ssid.isEmpty()) {
            ssid = readCurrentSsid(ctx) ?: ""
        }
    }
    LaunchedEffect(ssid) {
        loadSavedWifiPassword(ctx, ssid)?.let { savedPassword ->
            if (wifiPassword != savedPassword) {
                wifiPassword = savedPassword
            }
        }
    }

    // Step 3: QR
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Step 4: Camera register
    var cameraIp by remember { mutableStateOf("") }
    var rtspPath by remember { mutableStateOf("live") }
    var isRegistering by remember { mutableStateOf(false) }
    var isScanningRtsp by remember { mutableStateOf(false) }
    var rtspScanMessage by remember { mutableStateOf<String?>(null) }
    var rtspCandidates by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Drift X3 카메라 페어링",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "폰의 현재 WiFi 정보를 QR 로 만들어 카메라에 보여줍니다. " +
                "카메라가 같은 WiFi 에 접속되면 IP 를 입력하고 등록하세요.",
            fontSize = 13.sp,
            color = Color.Gray
        )

        // ── Step 1 ──────────────────────────────────────────────
        StepCard(stepNumber = 1, title = "카메라 이름") {
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("장치 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Step 2 ──────────────────────────────────────────────
        StepCard(stepNumber = 2, title = "WiFi 정보") {
            if (!hasLocationPermission) {
                Button(
                    onClick = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("위치 권한 허용 + SSID 자동 감지") }
            }
            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("WiFi SSID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = wifiPassword,
                onValueChange = { wifiPassword = it },
                label = { Text("WiFi 비밀번호") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "숨김" else "표시", fontSize = 12.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Step 3 ──────────────────────────────────────────────
        StepCard(stepNumber = 3, title = "QR 생성 + 카메라 스캔") {
            Button(
                onClick = {
                    if (ssid.isBlank() || wifiPassword.isBlank()) {
                        Toast.makeText(ctx, "SSID 와 비밀번호 모두 입력", Toast.LENGTH_SHORT).show()
                    } else {
                        val qrSsid = ssid.trim()
                        saveWifiPassword(ctx, qrSsid, wifiPassword)
                        // Drift X3 QR 프로토콜 (사용자 매뉴얼 reading, 코드 ref 0)
                        val qrText = "17|$qrSsid|$wifiPassword|rtsp"
                        qrBitmap = generateQrBitmap(qrText, sizePx = 720)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("QR 생성") }

            qrBitmap?.let { bmp ->
                Spacer(Modifier.height(8.dp))
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "WiFi QR for Drift X3",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(8.dp)
                )
                Text(
                    text = "카메라가 QR 을 스캔하고 WiFi 에 연결될 때까지 대기하세요.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // ── Step 4 ──────────────────────────────────────────────
        StepCard(stepNumber = 4, title = "카메라 RTSP 입력 + 등록") {
            Button(
                onClick = {
                    isScanningRtsp = true
                    rtspScanMessage = "같은 WiFi 대역에서 RTSP 포트(554)를 찾는 중..."
                    rtspCandidates = emptyList()
                    coroutineScope.launch {
                        val candidates = scanRtspHostsOnCurrentWifi(ctx)
                        rtspCandidates = candidates
                        isScanningRtsp = false
                        if (candidates.isNotEmpty()) {
                            cameraIp = candidates.first()
                            rtspScanMessage = if (candidates.size == 1) {
                                "RTSP 후보 1개 감지: ${candidates.first()}"
                            } else {
                                "RTSP 후보 ${candidates.size}개 감지. 첫 번째 IP를 자동 입력했습니다."
                            }
                        } else {
                            rtspScanMessage = "RTSP 후보를 찾지 못했습니다. 카메라 WiFi 연결 또는 같은 네트워크 여부를 확인하세요."
                        }
                    }
                },
                enabled = !isScanningRtsp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isScanningRtsp) "RTSP 자동 감지 중..." else "RTSP IP 자동 감지")
            }
            rtspScanMessage?.let { message ->
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (rtspCandidates.size > 1) {
                Text(
                    text = "후보: ${rtspCandidates.joinToString()}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            OutlinedTextField(
                value = cameraIp,
                onValueChange = { cameraIp = it },
                label = { Text("카메라 IP 또는 호스트 (예: 192.168.0.13)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = rtspPath,
                onValueChange = { rtspPath = it },
                label = { Text("RTSP 경로 (예: live)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            val previewRtspUrl = remember(cameraIp, rtspPath) {
                buildRtspUrl(cameraIp, rtspPath)
            }
            if (previewRtspUrl != null) {
                Text(
                    text = "RTSP URL: $previewRtspUrl",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Button(
                onClick = {
                    val groupId = UserSession.groupId?.toIntOrNull()
                    val rtspUrl = buildRtspUrl(cameraIp, rtspPath)
                    when {
                        groupId == null -> Toast.makeText(ctx, "그룹 정보 없음 — 다시 로그인", Toast.LENGTH_SHORT).show()
                        deviceName.isBlank() -> Toast.makeText(ctx, "장치 이름 입력", Toast.LENGTH_SHORT).show()
                        cameraIp.isBlank() -> Toast.makeText(ctx, "카메라 IP 또는 호스트 입력", Toast.LENGTH_SHORT).show()
                        rtspUrl == null -> Toast.makeText(ctx, "RTSP 주소 형식을 확인해 주세요", Toast.LENGTH_SHORT).show()
                        else -> {
                            isRegistering = true
                            registerCamera(
                                ctx = ctx,
                                deviceName = deviceName,
                                rtspUrl = rtspUrl,
                                groupId = groupId,
                                onComplete = {
                                    isRegistering = false
                                    onDone()
                                },
                                onError = { isRegistering = false }
                            )
                        }
                    }
                },
                enabled = !isRegistering,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRegistering) "등록 중..." else "카메라 등록")
            }
        }
    }
}

@Composable
private fun StepCard(stepNumber: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stepNumber.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            content()
        }
    }
}

/**
 * 폰 현재 WiFi SSID 자동 감지.
 *
 * Android 8+ 는 quoted 형식 ("MyWiFi"), 10+ 는 ACCESS_FINE_LOCATION 권한 필수.
 * 권한 거부 또는 못 가져오면 null 반환 → 사용자 수동 입력으로 fallback.
 */
private fun readCurrentSsid(ctx: Context): String? {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) return null
    return try {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        @Suppress("DEPRECATION")
        val raw = wifi.connectionInfo?.ssid ?: return null
        val unquoted = raw.trim('"')
        if (unquoted.isBlank() || unquoted.equals("<unknown ssid>", ignoreCase = true)) null
        else unquoted
    } catch (e: SecurityException) {
        null
    }
}

private fun loadSavedWifiPassword(ctx: Context, ssid: String): String? {
    val normalizedSsid = ssid.trim()
    if (normalizedSsid.isBlank()) return null
    return ctx.getSharedPreferences(WIFI_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(WIFI_PASSWORD_KEY_PREFIX + normalizedSsid, null)
        ?.takeIf { it.isNotEmpty() }
}

private fun saveWifiPassword(ctx: Context, ssid: String, password: String) {
    val normalizedSsid = ssid.trim()
    if (normalizedSsid.isBlank() || password.isBlank()) return
    ctx.getSharedPreferences(WIFI_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(WIFI_PASSWORD_KEY_PREFIX + normalizedSsid, password)
        .apply()
}

/**
 * QR 비트맵 생성 (ZXing core only — android-embedded 의존 없이 BitMatrix → Bitmap 직접 변환).
 */
private fun generateQrBitmap(content: String, sizePx: Int = 720): Bitmap {
    val matrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, width, 0, 0, width, height)
    return bmp
}

private fun buildRtspUrl(hostOrUrl: String, pathInput: String): String? {
    val raw = hostOrUrl.trim()
    if (raw.isBlank() || raw.any { it.isWhitespace() }) return null

    val scheme = when {
        raw.startsWith("rtsps://", ignoreCase = true) -> "rtsps"
        else -> "rtsp"
    }
    val withoutScheme = raw.replaceFirst(Regex("^rtsps?://", RegexOption.IGNORE_CASE), "")
    val host = withoutScheme.substringBefore("/").trim()
    if (host.isBlank() || host.any { it.isWhitespace() }) return null

    val pathFromUrl = withoutScheme.substringAfter("/", missingDelimiterValue = "")
    val path = (pathFromUrl.ifBlank { pathInput })
        .trim()
        .trim('/')
        .ifBlank { "live" }
    if (path.any { it.isWhitespace() }) return null

    return "$scheme://$host/$path"
}

private suspend fun scanRtspHostsOnCurrentWifi(ctx: Context): List<String> = withContext(Dispatchers.IO) {
    val prefix = currentWifiIpv4Prefix(ctx) ?: return@withContext emptyList()
    coroutineScope {
        val limit = Semaphore(32)
        (1..254).map { hostSuffix ->
            async {
                val host = "$prefix.$hostSuffix"
                limit.withPermit {
                    if (isTcpPortOpen(host, 554, timeoutMs = 180)) host else null
                }
            }
        }.awaitAll().filterNotNull()
    }
}

private fun currentWifiIpv4Prefix(ctx: Context): String? {
    return try {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        @Suppress("DEPRECATION")
        val ipAddress = wifi.connectionInfo?.ipAddress ?: return null
        if (ipAddress == 0) return null

        val first = ipAddress and 0xff
        val second = ipAddress shr 8 and 0xff
        val third = ipAddress shr 16 and 0xff
        if (first == 0 || first == 127) return null

        "$first.$second.$third"
    } catch (_: SecurityException) {
        null
    }
}

private fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
        }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * cameras 테이블 INSERT — Edge Function cameras/register 호출.
 * RTSP URL 패턴: rtsp://${ip}/live (Drift X3 표준, memory: 검증 완료 in Phase 8 RTSP-02).
 */
private fun registerCamera(
    ctx: Context,
    deviceName: String,
    rtspUrl: String,
    groupId: Int,
    onComplete: () -> Unit,
    onError: () -> Unit,
) {
    val deviceCode = "DRIFTX3-${Integer.toHexString(rtspUrl.hashCode())}"
    val request = RegisterCameraRequest(
        deviceName = deviceName,
        deviceCode = deviceCode,
        installArea = MOBILE_CAMERA_AREA,
        groupId = groupId,
        liveUrl = rtspUrl,
        liveUrlDetail = rtspUrl,
        installationAddress = MOBILE_CAMERA_ADDRESS,
    )
    RetrofitClient.instance.registerCamera(request).enqueue(object : Callback<RegisterCameraResponse> {
        override fun onResponse(
            call: Call<RegisterCameraResponse>,
            response: Response<RegisterCameraResponse>,
        ) {
            if (response.isSuccessful && response.body() != null) {
                Toast.makeText(
                    ctx,
                    "카메라 등록 완료 (id=${response.body()!!.cameraId})",
                    Toast.LENGTH_LONG
                ).show()
                onComplete()
            } else {
                Toast.makeText(ctx, "등록 실패: ${response.code()}", Toast.LENGTH_LONG).show()
                onError()
            }
        }

        override fun onFailure(call: Call<RegisterCameraResponse>, t: Throwable) {
            Toast.makeText(ctx, "등록 실패: ${t.message}", Toast.LENGTH_LONG).show()
            onError()
        }
    })
}
