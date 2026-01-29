package com.example.smart_safety_management

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.location.Geocoder
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.smart_safety_management.ui.theme.*
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration as OsmConfiguration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun ActionDetailWorkerScreen(
    eventId: Int,
    onBackClick: () -> Unit = {},
    initialExpanded: Boolean = false // 바텀 시트 초기 확장 여부
) {
    var eventDetail by remember { mutableStateOf<DetectionEventDetailResponse?>(null) }

    // 라이브 컴포넌트와 사용하기위한 상태 변수
    var pos by remember { mutableStateOf(0.5f) }
    var playing by remember { mutableStateOf(true) }

    val context = LocalContext.current
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoTextureView by remember { mutableStateOf<TextureView?>(null) }
    var mapCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var isGeocodingError by remember { mutableStateOf(false) }

    // 입력 필드 상태
    var actionType by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    
    // 작성 완료 다이얼로그 상태
    var showActionCompletedDialog by remember { mutableStateOf(false) }
    
    // 스크롤 상태 관리
    val scrollState = rememberScrollState()
    val sheetScrollState = rememberScrollState() // 바텀 시트 전용 스크롤 상태
    
    // 드롭다운 확장 상태 관리
    var dropdownExpanded by remember { mutableStateOf(false) }
    val options = listOf("조치공유", "조치필요", "즉시조치")

    // 바텀 시트 상태 관리
    val sheetState = rememberModalBottomSheetState(
        initialValue = if (initialExpanded) ModalBottomSheetValue.Expanded else ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val coroutineScope = rememberCoroutineScope()

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
                        mapCenter = GeoPoint(addresses[0].latitude, addresses[0].longitude)
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

    // 서버에서 데이터 불러오기
    LaunchedEffect(eventId) {
        RetrofitClient.instance.getDetectionEventDetail(eventId).enqueue(object : Callback<DetectionEventDetailResponse> {
            override fun onResponse(call: Call<DetectionEventDetailResponse>, response: Response<DetectionEventDetailResponse>) {
                if (response.isSuccessful) {
                    eventDetail = response.body()
                    eventDetail?.let {
                        actionType = it.requestType ?: ""
                        title = it.requestTitle ?: ""
                        content = it.requestDetails ?: ""
                    }
                }
            }
            override fun onFailure(call: Call<DetectionEventDetailResponse>, t: Throwable) {
                Log.e("ActionDetailWorker", "Network error: ${t.message}")
            }
        })
    }

    Smart_Safety_ManagementTheme {
        val theme = MaterialTheme.colors.onPrimary
        val isLight = MaterialTheme.colors.isLight
        val CategoryColor = if (isLight) TextGray60 else TextGray
        val textColor = if (isLight) TextGray20 else TextGray5
        val labelColor = if (isLight) TextDark else GrayBorder
        val borderColor = if (isLight) GrayBorder else TextDark
        val inputTextColor = if (isLight) TextGray20 else TextGray5
        val photoColor = if (isLight) TextLight else TextGray30
        val btnBackColor = if (isLight) Color.White else TextGray20
        val detailBtnColor = if (isLight) Lightgray else GrayBackground

        // 1. 이벤트 아이콘 설정 (위험, 경고, 주의에 따라 변경)
        val eventIconRes = when (eventDetail?.riskLevel?.lowercase()) {
            "high", "위험", "danger" -> R.drawable.danger_icon
            "medium", "경고", "warning" -> R.drawable.warning_icon
            "low", "주의", "caution" -> R.drawable.caution_icon
            else -> R.drawable.warning_icon
        }

        // 2. 아이콘 종류에 따른 "감지 이벤트" 밸류 텍스트 색상 설정
        val eventValueColor = when (eventIconRes) {
            R.drawable.danger_icon -> Color(0xFFEF4444)  // 위험 아이콘일 때 색상
            R.drawable.warning_icon -> Color(0xFFF97316) // 경고 아이콘일 때 색상
            R.drawable.caution_icon -> Color(0xFFFFB114) // 주의 아이콘일 때 색상
            else -> textColor
        }

        ModalBottomSheetLayout(
            sheetState = sheetState,
            sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            sheetBackgroundColor = MaterialTheme.colors.onPrimary,
            sheetContent = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(712.dp)
                        .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.Start, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(sheetScrollState) 
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "상세 보기",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = Pretendard,
                                color = textColor
                            )
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    sheetState.hide()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF6D7882)
                                )
                            }
                        }

                        LabelText("이벤트 캡처")
                        if (capturedBitmap != null) {
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = "이벤트 캡처",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        } else {
                            GlideImage(
                                model = eventDetail?.captureImageUrl,
                                contentDescription = "이벤트 캡처",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth
                            ) { it.error(R.drawable.workeraction).placeholder(R.drawable.workeraction) }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LabelText("발생 위치")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (mapCenter != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        OsmConfiguration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
                                        MapView(ctx).apply {
                                            setTileSource(TileSourceFactory.MAPNIK)
                                            setMultiTouchControls(true)
                                            controller.setZoom(17.0)
                                        }
                                    },
                                    update = { mapView ->
                                        mapView.controller.setCenter(mapCenter)
                                        mapView.overlays.clear()
                                        val marker = Marker(mapView)
                                        marker.position = mapCenter
                                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        val iconRes = if (isLight) R.drawable.worker_orange else R.drawable.worker_orange_dark
                                        marker.icon = ContextCompat.getDrawable(context, iconRes)
                                        mapView.overlays.add(marker)
                                        mapView.invalidate()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(if (isLight) Color.LightGray else Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isGeocodingError) "위치를 찾을 수 없습니다." else "위치 정보를 불러오는 중...",
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LabelText("실시간 화면")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            val liveUrl = eventDetail?.liveUrl
                            if (!liveUrl.isNullOrBlank()) {
                                val exoPlayer = remember {
                                    ExoPlayer.Builder(context).build().apply {
                                        playWhenReady = true
                                        addListener(object : Player.Listener {
                                            override fun onRenderedFirstFrame() {
                                                videoTextureView?.let { view ->
                                                    val bitmap = view.getBitmap()
                                                    if (bitmap != null) {
                                                        capturedBitmap = bitmap
                                                    }
                                                }
                                            }
                                        })
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
                                                    videoTextureView = this@apply
                                                }
                                                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
                                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                                    if (capturedBitmap == null) {
                                                        val bitmap = this@apply.getBitmap()
                                                        if (bitmap != null) {
                                                            capturedBitmap = bitmap
                                                        }
                                                    }
                                                }
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
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "조치 결과 등록",
                                fontWeight = FontWeight.Bold,
                                color = textColor,
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
                                    tint = textColor
                                )
                            }
                        },
                        backgroundColor = theme,
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
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScrollbar(scrollState)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "이벤트 내용",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(15.dp))

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(12.dp))
                                .border(1.dp, borderColor, shape = RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = eventIconRes), // 변수 적용
                                        contentDescription = "이벤트 아이콘",
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .offset(x = (-5).dp, y = 5.dp),
                                        tint = Color.Unspecified
                                    )
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = eventDetail?.installArea ?: "-",
                                            color = textColor,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard
                                        )
                                        Text(
                                            text = "${eventDetail?.eventName ?: "이벤트"}가 감지되었습니다.",
                                            color = CategoryColor,
                                            fontSize = 14.sp,
                                            fontFamily = Pretendard,
                                            fontWeight = FontWeight.Normal,
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
                                    "발생위치" to (eventDetail?.installArea ?: "-")
                                )

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
                                        
                                        // "감지 이벤트" 라벨일 경우에만 전용 색상 적용
                                        val displayColor = if (label == "감지 이벤트") eventValueColor else textColor

                                        Text(
                                            text = value,
                                            color = displayColor,
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            sheetState.show()
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp,bottom = 16.dp,start = 16.dp,end = 16.dp)
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        ,
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = ButtonDefaults.elevation(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = detailBtnColor
                                    )
                                ) {
                                    Text(
                                        text = "상세보기",
                                        fontFamily = Pretendard,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = inputTextColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "조치 유형",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            OutlinedTextField(
                                value = if (actionType.isEmpty()) "조치 유형 선택" else actionType,
                                onValueChange = { },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().height(59.dp),
                                textStyle = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = Pretendard,
                                    color = inputTextColor
                                ),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    Box(
                                        modifier = Modifier.fillMaxHeight(),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(end = 12.dp)
                                        ) {
                                            // ✅ 선택된 아이콘 제거 완료
                                            Icon(
                                                painter = painterResource(id = R.drawable.dropbox),
                                                contentDescription = null,
                                                tint = Color.Unspecified
                                            )
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    unfocusedBorderColor = borderColor,
                                    focusedBorderColor = MainOrange,
                                    textColor = inputTextColor,
                                    backgroundColor = btnBackColor
                                )
                            )
                            
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier
                                    .width(this@BoxWithConstraints.maxWidth)
                                    .background(btnBackColor)
                            ) {
                                options.forEach { option ->
                                    DropdownMenuItem(
                                        onClick = {
                                            actionType = option
                                            dropdownExpanded = false
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = option,
                                                fontFamily = Pretendard,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = inputTextColor
                                            )
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { dropdownExpanded = true }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "제목",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .height(59.dp),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = TextStyle(fontSize = 18.sp, fontFamily = Pretendard, color = inputTextColor),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                unfocusedBorderColor = borderColor,
                                focusedBorderColor = MainOrange,
                                backgroundColor = btnBackColor
                            )
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "내용",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = TextStyle(fontSize = 18.sp, fontFamily = Pretendard, color = inputTextColor),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                unfocusedBorderColor = borderColor,
                                focusedBorderColor = MainOrange,
                                backgroundColor = btnBackColor
                            )
                        )

                        if (!eventDetail?.actionImages.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "첨부 사진",
                                fontWeight = FontWeight.Medium,
                                color = CategoryColor,
                                fontFamily = Pretendard,
                                fontSize = 16.sp,
                                modifier = Modifier.offset(x = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                eventDetail?.actionImages?.forEach { imageUrl ->
                                    if (imageUrl != null) {
                                        val fullUrl = if (imageUrl.startsWith("http")) imageUrl else "${RetrofitClient.BASE_URL}${imageUrl.removePrefix("/")}"
                                        GlideImage(
                                            model = fullUrl,
                                            contentDescription = "Attached Image",
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Button(
                            onClick = { 
                                showActionCompletedDialog = true 
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                                .padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MainOrange,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Text(
                                text = "조치완료",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                fontFamily = Pretendard,
                                color = MaterialTheme.colors.onPrimary,
                                letterSpacing = (-0.3).sp
                            )
                        }
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }

        if (showActionCompletedDialog) {
            ActionCompletedDialog(
                onDismiss = { showActionCompletedDialog = false },
                onConfirm = {
                    val userId = UserSession.userId
                    if (!userId.isNullOrEmpty()) {
                        val request = CompleteActionRequest(eventId = eventId, workerId = userId)
                        RetrofitClient.instance.completeAction(request).enqueue(object : Callback<Void> {
                            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                                if (response.isSuccessful) {
                                    Toast.makeText(context, "조치 완료 처리되었습니다.", Toast.LENGTH_SHORT).show()
                                    showActionCompletedDialog = false
                                    onBackClick() // 성공 시 뒤로가기
                                } else {
                                    Toast.makeText(context, "오류가 발생했습니다: ${response.code()}", Toast.LENGTH_SHORT).show()
                                    showActionCompletedDialog = false
                                }
                            }

                            override fun onFailure(call: Call<Void>, t: Throwable) {
                                Toast.makeText(context, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                                showActionCompletedDialog = false
                            }
                        })
                    } else {
                        Toast.makeText(context, "사용자 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                        showActionCompletedDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun ActionCompletedDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
                        painter = painterResource(id = R.drawable.check),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        textAlign = TextAlign.Center,
                        text = "조치완료",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                        color = MaterialTheme.colors.onSurface
                    )
                    Text(
                        textAlign = TextAlign.Center,
                        text = "조치요청 완료처리 하시겠습니까?",
                        fontFamily = Pretendard,
                        color = textColor,
                        fontSize = 16.sp
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

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode", heightDp = 1000)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode", heightDp = 1000)
@Composable
fun ActionDetailWorkerScreenPreview() {
    ActionDetailWorkerScreen(eventId = 1)
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode - Dialog", heightDp = 1000)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode - Dialog", heightDp = 1000)
@Composable
fun ActionDetailWorkerDialogPreview() {
    ActionDetailWorkerScreen(eventId = 1, initialExpanded = true)
}
