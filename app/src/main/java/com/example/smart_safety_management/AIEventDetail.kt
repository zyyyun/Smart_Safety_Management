package com.example.smart_safety_management

import android.content.res.Configuration
import android.location.Geocoder
import android.graphics.Bitmap
import android.widget.Toast
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.smart_safety_management.ui.theme.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.gson.annotations.SerializedName
import java.util.Locale
import com.kakao.vectormap.LatLng

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AIEventDetailScreen(
    eventId: Int,
    onBackClick: () -> Unit = {},
    onRequestAction: () -> Unit = {}, // 조치 요청 콜백 추가
) {
    var eventDetail by remember { mutableStateOf<DetectionEventDetailResponse?>(null) }
    val isActionCompleted = eventDetail?.status?.equals("COMPLETED", ignoreCase = true) == true ||
            eventDetail?.status?.equals("FALSE_POSITIVE", ignoreCase = true) == true ||
            eventDetail?.status?.equals("REQUESTED", ignoreCase = true) == true

    var showFalseDetectionDialog by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0.5f) }
    var playing by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    // 지도 좌표 상태
    var mapLat by remember { mutableStateOf<Double?>(null) }
    var mapLon by remember { mutableStateOf<Double?>(null) }
    var isGeocodingError by remember { mutableStateOf(false) }


    Smart_Safety_ManagementTheme {

        val isLight = MaterialTheme.colors.isLight
        val CategoryColor = if (isLight) TextGray60 else TextGray
        val locationColor = if (isLight) TextGray20 else TextGray5
        val labelColor = if (isLight) TextDark else GrayBorder
        val valueColor = locationColor
        val buttonBackground = if (isLight) Lightgray else GrayBackground
        val borderColor = if (isLight) GrayBorder else TextDark

        // 주소를 좌표로 변환 (Geocoding)
        LaunchedEffect(eventDetail) {
            val address = eventDetail?.installationAddress
            if (!address.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(context, Locale.KOREA)
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocationName(address, 1)
                        if (!addresses.isNullOrEmpty()) {
                            mapLat = addresses[0].latitude
                            mapLon = addresses[0].longitude
                            isGeocodingError = false
                        } else {
                            isGeocodingError = true
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        isGeocodingError = true
                    }
                }
            } else if (eventDetail != null) {
                isGeocodingError = true
            }
        }

        // ✅ [수정] 화면이 다시 보일 때(ON_RESUME) 데이터 갱신 (조치요청 후 복귀 시 상태 업데이트)
        DisposableEffect(lifecycleOwner, eventId) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    RetrofitClient.instance.getDetectionEventDetail(eventId).enqueue(object : Callback<DetectionEventDetailResponse> {
                        override fun onResponse(call: Call<DetectionEventDetailResponse>, response: Response<DetectionEventDetailResponse>) {
                            if (response.isSuccessful) {
                                eventDetail = response.body()
                            }
                        }
                        override fun onFailure(call: Call<DetectionEventDetailResponse>, t: Throwable) {
                            // 에러 처리
                        }
                    })
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        if (eventDetail == null) {
            // 로딩 중 표시 (필요 시 구현)
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "AI 이벤트 감지",
                            fontWeight = FontWeight.Bold,
                            color = if (isLight) Color.Black else TextGray5,
                            fontFamily = Pretendard,
                            modifier = Modifier.offset(x = (-20).dp),
                            fontSize = 24.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.backicon),
                                contentDescription = "Back",
                                tint = if (isLight) Color.Black else TextGray5
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colors.onPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "이벤트 내용",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(
                                    color = MaterialTheme.colors.onPrimary,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp, 
                                    color = borderColor,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = mapRiskToIcon(eventDetail?.riskLevel)),
                                        contentDescription = "경고",
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .offset(x = (-5).dp, y = 5.dp),
                                        tint = Color.Unspecified
                                    )
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = eventDetail?.installArea ?: "위치 정보 없음",
                                            color = locationColor,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard
                                        )
                                        Text(
                                            text = "${eventDetail?.eventName ?: "이벤트"}가 감지되었습니다.",
                                            color = CategoryColor,
                                            fontSize = 14.sp,
                                            fontFamily = Pretendard,
                                            modifier = Modifier.offset(y = (4).dp)
                                        )
                                    }
                                }
                                
                                Divider(
                                    color = if (isLight) TextGray5 else Color.White.copy(alpha = 0.05f),
                                    thickness = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val detailItems = listOf(
                                    "감지 이벤트" to (eventDetail?.eventName ?: "-"),
                                    "발생 시간" to (eventDetail?.detectedAt ?: "-"),
                                    "장치명" to (eventDetail?.deviceName ?: "-"),
                                    "발생위치" to (eventDetail?.installArea ?: "-"),
                                    "정확도" to "${eventDetail?.accuracy?.toInt() ?: 0}%"
                                )

                                // ✅ 위험도에 따른 텍스트 색상 결정
                                val riskColor = when (eventDetail?.riskLevel?.lowercase()) {
                                    "high", "위험", "danger" -> Color(0xFFEF4444)
                                    "medium", "경고", "warning" -> Color(0xFFF97316)
                                    "low", "주의", "caution" -> Color(0xFFFFB114)
                                    else -> valueColor
                                }

                                detailItems.forEach { (label, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = label,
                                            color = labelColor,
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard,
                                            fontWeight = FontWeight.Medium
                                        )

                                        // ✅ "감지 이벤트" 라벨일 경우에만 위험도 색상 적용
                                        val displayColor = if (label == "감지 이벤트") riskColor else valueColor

                                        Text(
                                            text = value,
                                            color = displayColor,
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                if (!isActionCompleted) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showFalseDetectionDialog = true },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = buttonBackground,
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                                        ) {
                                            Text(
                                                text = "오탐처리",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = Pretendard,
                                                color = locationColor,
                                                letterSpacing = 0.sp
                                            )
                                        }

                                        Button(
                                            onClick = { onRequestAction() }, // 조치 요청 콜백 실행
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MainOrange,
                                                contentColor = Color.White           
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                                        ) {
                                            Text(
                                                text = "조치요청",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = Pretendard,
                                                color = MaterialTheme.colors.onPrimary,
                                                letterSpacing = 0.sp
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = MainOrange,
                                            contentColor = Color.White,
                                            disabledBackgroundColor = MainOrange,
                                            disabledContentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                                        enabled = false
                                    ) {
                                        Text(
                                            text = "조치요청 처리되었습니다",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = Pretendard,
                                            color = MaterialTheme.colors.onPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "이벤트 캡처",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // ✅ [수정] 클라이언트 캡처 로직 제거 -> 서버 URL 사용
                        GlideImage(
                            model = eventDetail?.captureImageUrl,
                            contentDescription = "이벤트 캡처",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth
                        ) { it.error(R.drawable.event).placeholder(R.drawable.event) }

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "발생위치",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (mapLat != null && mapLon != null) {
                                val iconRes = if (isLight) R.drawable.worker_orange else R.drawable.worker_orange_dark

                                KakaoMapView(
                                    lat = mapLat!!,
                                    lon = mapLon!!,
                                    modifier = Modifier.fillMaxSize(),
                                    targetLatLng = LatLng.from(mapLat!!, mapLon!!),
                                    pins = listOf(
                                        KakaoMapPin(
                                            id = "event",
                                            lat = mapLat!!,
                                            lon = mapLon!!,
                                            iconRes = iconRes
                                        )
                                    ),
                                    selectedId = "event",
                                    centerOnSelectedPin = true

                                )
                            }

                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "실시간 화면",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(

                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            val liveUrl = eventDetail?.liveUrl
                            if (!liveUrl.isNullOrBlank()) {
                                val exoPlayer = remember {
                                    ExoPlayer.Builder(context).build().apply {
                                        playWhenReady = true
                                    }
                                }

                                DisposableEffect(Unit) {
                                    onDispose { exoPlayer.release() }
                                }

                                LaunchedEffect(liveUrl) {
                                    val mediaItem = MediaItem.fromUri(liveUrl)
                                    exoPlayer.setMediaItem(mediaItem)
                                    exoPlayer.prepare()
                                }

                                LaunchedEffect(playing) {
                                    if (playing) exoPlayer.play() else exoPlayer.pause()
                                }

                                AndroidView(
                                    factory = { ctx ->
                                        TextureView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                                    exoPlayer.setVideoTextureView(this@apply)
                                                }
                                                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
                                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                GlideImage(
                                    model = eventDetail?.captureImageUrl,
                                    contentDescription = "실시간 화면",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.FillWidth
                                ) { it.error(R.drawable.event).placeholder(R.drawable.event) }
                            }

                            // 3. 상단 LIVE 인디케이터 배치
                            LiveIndicator(
                                isLive = true,
                                modifier = Modifier
                                    .align(Alignment.TopEnd) // 왼쪽 상단에 배치
                                    .padding(12.dp)            // 이미지 안쪽 여백
                            )

                            // 4. 하단 재생바 및 컨트롤러 배치
                            LivePlaybackController(
                                sliderPosition = pos,
                                onSliderValueChange = { pos = it },
                                timeText = "05:20",
                                isPlaying = playing,
                                onPlayPauseClick = { playing = !playing },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter) // 하단 중앙에 배치
                                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)) // 이미지 모서리에 맞춰 하단 깎기
                            )
                        }

                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }

        if (showFalseDetectionDialog) {
            FalseDetectionDialog(
                onDismiss = { showFalseDetectionDialog = false },
                onConfirm = { 
                    val userId = UserSession.userId
                    if (!userId.isNullOrEmpty()) {
                        val request = HandleFalsePositiveRequest(eventId, userId)
                        RetrofitClient.instance.handleFalsePositive(request).enqueue(object : Callback<Void> {
                            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                                if (response.isSuccessful) {
                                    ToastUtil.showShort(context, "오탐 처리되었습니다.")
                                    showFalseDetectionDialog = false
                                    onBackClick()
                                } else {
                                    ToastUtil.showShort(context, "처리 실패: ${response.code()}")
                                }
                            }
                            override fun onFailure(call: Call<Void>, t: Throwable) {
                                ToastUtil.showShort(context, "네트워크 오류: ${t.message}")
                            }
                        })
                    } else {
                        ToastUtil.showShort(context, "사용자 정보를 찾을 수 없습니다.")
                    }
                }
            )
        }
    }
}

private fun mapRiskToIcon(riskLevel: String?): Int {
    return when (riskLevel?.lowercase()) {
        "high", "위험", "danger" -> R.drawable.danger_icon
        "medium", "경고", "warning" -> R.drawable.warning_icon
        "low", "주의", "caution" -> R.drawable.caution_icon
        else -> R.drawable.caution_icon
    }
}

@Composable
fun FalseDetectionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val isLight = MaterialTheme.colors.isLight
        val backgroundColor = if (isLight) Color.White else GrayBackground
        val buttonBackground = if (isLight) TextGray5 else TextGray20
        val textColor = if (isLight) TextGray60 else TextGray
        
        Surface(
            modifier = Modifier.size(width = 350.dp, height = 250.dp),
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.false_alarm),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        textAlign = TextAlign.Center,
                        text = "해당 감지 이벤트를 \n오탐처리 하시겠습니까?",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                        color = MaterialTheme.colors.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.size(width = 144.dp, height = 52.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = buttonBackground,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Text(
                            text = "취소",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Pretendard,
                            color = textColor
                        )
                    }

                    Button(
                        onClick = { onConfirm() },
                        modifier = Modifier.size(width = 144.dp, height = 52.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MainOrange,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Text(
                            text = "확인",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Pretendard,
                            color = MaterialTheme.colors.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun AIEventDetailScreenPreview() {
    AIEventDetailScreen(eventId = 1)
}
