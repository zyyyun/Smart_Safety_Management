package com.example.smart_safety_management

import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import kotlinx.coroutines.Dispatchers
import android.widget.Toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapAdapter
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

class SettingWorkplaceLocationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            Smart_Safety_ManagementTheme {
                SettingWorkplaceLocationScreen(
                    onBack = { finish() },
                    onConfirm = { finish() }
                )
            }
        }
    }
}

private suspend fun geocodeToPoint(
    context: android.content.Context,
    query: String
): GeoPoint? = withContext(Dispatchers.IO) {
    runCatching {
        val geocoder = Geocoder(context, Locale.KOREA)
        val list = geocoder.getFromLocationName(query, 1)
        if (list.isNullOrEmpty()) null
        else GeoPoint(list[0].latitude, list[0].longitude)
    }.getOrNull()
}


/** ✅ 역지오코딩 (좌표 -> 주소) */
private suspend fun reverseGeocodeKorea(
    context: android.content.Context,
    lat: Double,
    lon: Double
): Triple<String, String, String> = withContext(Dispatchers.IO) {
    runCatching {
        val geocoder = Geocoder(context, Locale.KOREA)
        val list = geocoder.getFromLocation(lat, lon, 1)
        if (list.isNullOrEmpty()) return@withContext Triple("", "", "")

        val a = list[0]
        val fullAddr = a.getAddressLine(0) ?: ""
        val postal = a.postalCode ?: ""

        // 도로명/번지 느낌으로 최대한 구성
        val roadAddr = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ").trim()
            .ifBlank {
                // 대체값: featureName 등
                listOfNotNull(a.thoroughfare, a.featureName).joinToString(" ").trim()
            }

        Triple(fullAddr, postal, roadAddr)
    }.getOrElse {
        Triple("", "", "")
    }
}

@Composable
fun SettingWorkplaceLocationScreen(
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val c = LocalSafeColors.current
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isRegistered by remember { mutableStateOf(false) }

    // ✅ 하단 시트 높이(측정값)
    var sheetHeightDp by remember { mutableStateOf(252.dp) }

    // ✅ 아래 주소(지도 중심 좌표에 따라 바뀌게!)
    var address by remember { mutableStateOf("인천광역시 남동구 예술로 197 (인천아시아드 주경기장)") }
    var zipcode by remember { mutableStateOf("21983") }
    var road by remember { mutableStateOf("송도동 162-1") }

    val orange = Color(0xFFFF7A00)

    // ✅ 상태바/탑바 높이 (서로 다른 값 쓰면 어긋나서 통일!)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarH = 60.dp
    val searchTop = statusTop + topBarH + 16.dp

    // ✅ MapView를 remember로 유지 + 디바운스 Job
    val mapViewHolder = remember { mutableStateOf<MapView?>(null) }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }

    // ✅ 서버에서 가져온 초기 좌표 저장용
    var serverLocationPoint by remember { mutableStateOf<GeoPoint?>(null) }

    // ✅ 초기 진입 시 서버에서 위치 정보 가져오기
    LaunchedEffect(Unit) {
        val userId = UserSession.userId
        if (!userId.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val retrofit = Retrofit.Builder()
                        .baseUrl("http://10.0.2.2:3000/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()

                    val api = retrofit.create(LocationApi::class.java)
                    val response = api.getLocation(userId)

                    if (response.isSuccessful && response.body() != null) {
                        val data = response.body()!!
                        val savedAddr = data.address
                        if (!savedAddr.isNullOrBlank()) {
                            // 주소를 좌표로 변환
                            val point = geocodeToPoint(context, savedAddr)
                            if (point != null) {
                                withContext(Dispatchers.Main) {
                                    address = savedAddr
                                    road = data.road_address ?: ""
                                    isRegistered = true
                                    serverLocationPoint = point // 지도 이동 트리거
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // ✅ 지도 중심좌표 -> 주소 업데이트 함수
    fun requestAddressUpdate(context: android.content.Context, mapView: MapView) {
        geocodeJob?.cancel()
        geocodeJob = scope.launch {
            delay(300) // ✅ 끌다 멈췄을 때만(디바운스)
            val center = mapView.mapCenter as? GeoPoint ?: return@launch
            val (addr, post, roadAddr) = reverseGeocodeKorea(context, center.latitude, center.longitude)
            if (addr.isNotBlank()) {
                address = addr
                zipcode = post
                road = roadAddr
            }
        }
    }

    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ✅ 지도 배경 (Preview는 더미, 실제는 OSMDroid)
            if (isPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (c.isDark) Color(0xFF0B0F14) else Color(0xFFE5E7EB))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                dropdownExpanded = false
                                focusManager.clearFocus()
                            })
                        }
                )
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                dropdownExpanded = false
                                focusManager.clearFocus()
                            })
                        },
                    factory = { context ->
                        Configuration.getInstance().load(
                            context,
                            PreferenceManager.getDefaultSharedPreferences(context)
                        )
                        Configuration.getInstance().userAgentValue = context.packageName

                        MapView(context).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)

                            controller.setZoom(18.0)
                            controller.setCenter(GeoPoint(37.4563, 126.7052)) // 기본값

                            mapViewHolder.value = this

                            addMapListener(object : MapAdapter() {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    requestAddressUpdate(context, this@apply)
                                    return true
                                }

                                override fun onZoom(event: ZoomEvent?): Boolean {
                                    requestAddressUpdate(context, this@apply)
                                    return true
                                }
                            })

                            requestAddressUpdate(context, this)
                        }
                    },
                    update = { mapView ->
                        mapViewHolder.value = mapView
                    }
                )

                DisposableEffect(Unit) {
                    onDispose {
                        geocodeJob?.cancel()
                        mapViewHolder.value?.onDetach()
                        mapViewHolder.value = null
                    }
                }
            }

            // ✅ 서버에서 가져온 위치가 있고, 맵이 준비되면 해당 위치로 이동
            LaunchedEffect(mapViewHolder.value, serverLocationPoint) {
                val map = mapViewHolder.value
                val point = serverLocationPoint
                if (map != null && point != null) {
                    map.controller.setZoom(18.0)
                    map.controller.animateTo(point)
                }
            }

            // ✅ 검색창 + 드롭다운 (⭐ 여기 수정!)
            SearchBarOverlay(
                query = query,
                onQueryChange = { newText ->
                    query = newText
                    dropdownExpanded = newText.isNotBlank()
                },
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },

                // ✅ 추가: 드롭다운에서 선택하면 지도 이동
                onSelectItem = { selected ->
                    query = selected
                    dropdownExpanded = false
                    focusManager.clearFocus()

                    val mv = mapViewHolder.value ?: return@SearchBarOverlay
                    scope.launch {
                        val p = geocodeToPoint(mv.context, selected) // ✅ 주소 -> 좌표
                        if (p != null) {
                            mv.controller.setZoom(18.0)
                            mv.controller.animateTo(p) // ✅ 그 위치로 이동
                            requestAddressUpdate(mv.context, mv) // ✅ 아래 주소도 즉시 갱신
                        }
                    }
                },

                isPreview = isPreview,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 24.dp, end = 24.dp, top = searchTop)
            )

            // ✅ 가운데 핀(고정)
            if (isPreview) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(orange)
                )
            } else {
                val pinSize = 110.dp
                Image(
                    painter = painterResource(
                        id = if (c.isDark) R.drawable.worker_orange_dark else R.drawable.worker_orange
                    ),
                    contentDescription = "pin",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = -(pinSize * 0.5f))
                        .size(pinSize)
                )
            }

            // ✅ floating 버튼 (등록 전만 노출)
            if (!isRegistered) {
                MapFloatButton(
                    onClick = {
                        val mv = mapViewHolder.value ?: return@MapFloatButton
                        requestAddressUpdate(mv.context, mv)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = sheetHeightDp + 24.dp)
                )
            }

            // ✅ 하단 카드(높이 측정)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coords ->
                        sheetHeightDp = with(density) { coords.size.height.toDp() }
                    }
            ) {
                BottomInfoCard(
                    isRegistered = isRegistered,
                    address = address,
                    zipcode = zipcode,
                    road = road,
                    onConfirm = {
                        // 1. user_id 가져오기 (UserSession 사용)
                        val userId = UserSession.userId

                        if (userId.isNullOrBlank()) {
                            Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            // 2. API 호출
                            scope.launch {
                                try {
                                    val retrofit = Retrofit.Builder()
                                        .baseUrl("http://10.0.2.2:3000/") // 로컬 서버 주소 (에뮬레이터 기준)
                                        .addConverterFactory(GsonConverterFactory.create())
                                        .build()

                                    val api = retrofit.create(LocationApi::class.java)
                                    val request = RegisterLocationRequest(userId, address, road, zipcode)
                                    val response = api.registerLocation(request)

                                    if (response.isSuccessful) {
                                        isRegistered = true
                                        Toast.makeText(context, "위치 등록 성공", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "등록 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "서버 연결 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    onDelete = { isRegistered = false },
                    onEdit = {
                        // 1. user_id 가져오기 (UserSession 사용)
                        val userId = UserSession.userId

                        if (userId.isNullOrBlank()) {
                            Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            // 2. API 호출 (수정)
                            scope.launch {
                                try {
                                    val retrofit = Retrofit.Builder()
                                        .baseUrl("http://10.0.2.2:3000/") // 로컬 서버 주소
                                        .addConverterFactory(GsonConverterFactory.create())
                                        .build()

                                    val api = retrofit.create(LocationApi::class.java)
                                    val request = RegisterLocationRequest(userId, address, road, zipcode)
                                    val response = api.registerLocation(request)

                                    if (response.isSuccessful) {
                                        Toast.makeText(context, "위치 수정 성공", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "수정 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "서버 연결 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                )
            }

            // ✅ TopBar
            TopBarFixed(
                onBack = onBack,
                statusTop = statusTop,
                topBarH = topBarH,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}


@Composable
private fun MapFloatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current
    val darkBg = Color(0xFF1D2D47)

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(if (c.isDark) darkBg else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (!c.isDark) {
            Image(
                painter = painterResource(id = R.drawable.background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Image(
            painter = painterResource(id = R.drawable.loc),
            contentDescription = "loc",
            modifier = Modifier.size(44.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TopBarFixed(
    onBack: () -> Unit,
    statusTop: Dp,
    topBarH: Dp,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current

    val topBarBg = if (c.isDark) c.topBar else Color.White

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusTop + topBarH)
            .background(topBarBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(topBarH)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 14.dp)
                    .height(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_back),
                        contentDescription = "back",
                        tint = if (c.isDark) Color.White else Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(3.dp))

                Text(
                    text = "현장위치 설정",
                    fontSize = 24.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SearchBarOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectItem: (String) -> Unit,
    isPreview: Boolean,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current
    val items = listOf("스타트업 파크 A동", "스타트업 파크 B동", "스타트업 파크 C동")

    val density = LocalDensity.current
    var fieldWidthDp by remember { mutableStateOf(0.dp) }

    val showDropdown = expanded && query.isNotBlank() && query.contains("스타트업 파크")

    Column(modifier = modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onGloballyPositioned { coords ->
                    fieldWidthDp = with(density) { coords.size.width.toDp() }
                }
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.search),
                contentDescription = "search",
                tint = c.sub,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { newText ->
                        onQueryChange(newText)
                        if (newText.isBlank()) onExpandedChange(false) else onExpandedChange(true)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = 21.sp,
                        lineHeight = 18.sp,
                        color = c.text,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium
                    ),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "주소를 검색하세요",
                                fontSize = 21.sp,
                                lineHeight = 18.sp,
                                color = c.sub,
                                fontFamily = Pretendard,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        inner()
                    }
                )
            }
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .width(if (fieldWidthDp > 0.dp) fieldWidthDp else Dp.Unspecified)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
        ) {
            items.forEach { item ->
                val isB = item.contains("B동")
                DropdownMenuItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isB) {
                                if (c.isDark) Color(0xFF5A3516) else Color(0xFFFEF1E7)
                            } else Color.Transparent
                        ),
                    text = {
                        Text(
                            text = item,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            lineHeight = 16.sp,
                            color = c.text
                        )
                    },
                    onClick = {
                        onSelectItem(item)      // ✅ 선택 처리(지도 이동)
                        onExpandedChange(false) // 드롭다운 닫기
                    }
                    ,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomInfoCard(
    isRegistered: Boolean,
    address: String,
    zipcode: String,
    road: String,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current
    val sheetRadius = 20.dp
    val sheetBg = if (c.isDark) Color(0xFF000000) else c.surface
    val strongText = c.text
    val divider = c.divider
    val infoGray = Color(0xFFCDD1D5)
    val badgeBg = if (c.isDark) Color(0xFF0E3B2B) else Color(0xFFCDF7EC)

    val buttonH = 54.dp
    val bottomGap = 24.dp
    val bottomReserve = buttonH + bottomGap + 36.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isRegistered) 340.dp else 300.dp)
            .clip(RoundedCornerShape(topStart = sheetRadius, topEnd = sheetRadius))
            .background(sheetBg)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = bottomReserve)
        ) {
            if (!isRegistered) {
                Text(
                    text = address,
                    fontSize = 20.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    color = strongText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 28.sp
                )

                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "우편번호",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = zipcode,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "도로명",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = road,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(badgeBg)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.postend_check),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "등록완료",
                        fontFamily = Pretendard,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF12B76A),
                        lineHeight = 13.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = address,
                    fontSize = 20.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    color = strongText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )

                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "우편번호",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = zipcode,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "도로명",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = road,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText
                    )
                }
            }
        }

        if (!isRegistered) {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = bottomGap)
                    .fillMaxWidth()
                    .height(buttonH),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
            ) {
                Text(
                    text = "위치 등록",
                    fontSize = 20.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    color = if (c.isDark) Color.Black else Color.White
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = bottomGap)
                    .fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(buttonH),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, divider),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (c.isDark) Color(0xFF000000) else c.surface
                    )
                ) {
                    Text(
                        text = "위치 삭제",
                        fontSize = 18.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold,
                        color = if (c.isDark) Color(0xFF8A949E) else Color(0xFF58616A)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(buttonH),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (c.isDark) Color(0xFF131416) else Color(0xFFF3F4F6)
                    )
                ) {
                    Text(
                        text = "위치 수정",
                        fontSize = 18.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold,
                        color = if (c.isDark) Color(0xFF8A949E) else Color(0xFF58616A)
                    )
                }
            }
        }
    }
}

@Preview(
    name = "Setting Workplace Location (Light)",
    showBackground = true,
    device = Devices.PIXEL_7
)
@Composable
fun SettingWorkplaceLocationPreview() {
    Smart_Safety_ManagementTheme(darkTheme = false) {
        SettingWorkplaceLocationScreen(onBack = {}, onConfirm = {})
    }
}

// ✅ API 통신을 위한 데이터 클래스 및 인터페이스 정의
data class RegisterLocationRequest(
    val user_id: String,
    val address: String,
    val road_address: String,
    val zipcode: String
)

data class RegisterLocationResponse(
    val message: String
)

data class GetLocationResponse(
    val address: String?,
    val road_address: String?
)

interface LocationApi {
    @POST("/register_workplace_location")
    suspend fun registerLocation(@Body request: RegisterLocationRequest): retrofit2.Response<RegisterLocationResponse>

    @GET("/get_workplace_location")
    suspend fun getLocation(@Query("user_id") userId: String): retrofit2.Response<GetLocationResponse>
}

@Preview(
    name = "Setting Workplace Location (Dark)",
    showBackground = true,
    device = Devices.PIXEL_7
)
@Composable
fun SettingWorkplaceLocationDarkPreview() {
    Smart_Safety_ManagementTheme(darkTheme = true) {
        SettingWorkplaceLocationScreen(onBack = {}, onConfirm = {})
    }
}
