package com.example.smart_safety_management.camera

// 2026-05-21 — Sprint A.2 (PC 의존성 제거 plan A.2.1 + A.2.3)
//
// Drift X3 카메라 QR 페어링 흐름:
//   1. 사용자가 카메라 등록 정보 입력 (install_area, installation_address)
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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

@Composable
fun CameraPairingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current

    // Step 1: 카메라 등록 정보
    var installArea by remember { mutableStateOf("") }
    var installationAddress by remember { mutableStateOf("") }
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

    // Step 3: QR
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Step 4: Camera register
    var cameraIp by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }

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
        StepCard(stepNumber = 1, title = "카메라 등록 정보") {
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("장치 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = installArea,
                onValueChange = { installArea = it },
                label = { Text("설치 구역 (예: A동 입구)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = installationAddress,
                onValueChange = { installationAddress = it },
                label = { Text("설치 주소") },
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
                        // Drift X3 QR 프로토콜 (사용자 매뉴얼 reading, 코드 ref 0)
                        val qrText = "17|$ssid|$wifiPassword|rtsp"
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
        StepCard(stepNumber = 4, title = "카메라 IP 입력 + 등록") {
            OutlinedTextField(
                value = cameraIp,
                onValueChange = { cameraIp = it },
                label = { Text("카메라 IP (예: 192.168.0.13)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (cameraIp.isNotBlank()) {
                Text(
                    text = "RTSP URL: rtsp://$cameraIp/live",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Button(
                onClick = {
                    val groupId = UserSession.groupId?.toIntOrNull()
                    when {
                        groupId == null -> Toast.makeText(ctx, "그룹 정보 없음 — 다시 로그인", Toast.LENGTH_SHORT).show()
                        installArea.isBlank() || installationAddress.isBlank() ->
                            Toast.makeText(ctx, "Step 1 입력 누락", Toast.LENGTH_SHORT).show()
                        cameraIp.isBlank() -> Toast.makeText(ctx, "카메라 IP 입력", Toast.LENGTH_SHORT).show()
                        else -> {
                            isRegistering = true
                            registerCamera(
                                ctx = ctx,
                                deviceName = deviceName,
                                installArea = installArea,
                                installationAddress = installationAddress,
                                cameraIp = cameraIp,
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

/**
 * cameras 테이블 INSERT — Edge Function cameras/register 호출.
 * RTSP URL 패턴: rtsp://${ip}/live (Drift X3 표준, memory: 검증 완료 in Phase 8 RTSP-02).
 */
private fun registerCamera(
    ctx: Context,
    deviceName: String,
    installArea: String,
    installationAddress: String,
    cameraIp: String,
    groupId: Int,
    onComplete: () -> Unit,
    onError: () -> Unit,
) {
    val liveUrl = "rtsp://$cameraIp/live"
    val request = RegisterCameraRequest(
        deviceName = deviceName,
        deviceCode = "DRIFTX3-$cameraIp", // best-effort 식별자
        installArea = installArea,
        groupId = groupId,
        liveUrl = liveUrl,
        liveUrlDetail = liveUrl,
        installationAddress = installationAddress,
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
