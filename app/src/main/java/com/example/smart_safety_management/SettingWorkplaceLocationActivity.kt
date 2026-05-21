package com.example.smart_safety_management

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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

private suspend fun geocodeToLatLon(
    context: android.content.Context,
    query: String
): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    runCatching {
        Log.d("Geocode", "Geocoding query: $query")
        val geocoder = Geocoder(context, Locale.KOREA)
        val list = geocoder.getFromLocationName(query, 1)
        if (list.isNullOrEmpty()) {
            Log.e("Geocode", "No results found for: $query")
            null
        } else {
            Log.d("Geocode", "Found: ${list[0].latitude}, ${list[0].longitude}")
            Pair(list[0].latitude, list[0].longitude)
        }
    }.onFailure {
        Log.e("Geocode", "Geocoding failed", it)
    }.getOrNull()
}

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

        val roadOnly = when {
            !a.thoroughfare.isNullOrBlank() && !a.subThoroughfare.isNullOrBlank() ->
                "${a.thoroughfare} ${a.subThoroughfare}".trim()

            !a.thoroughfare.isNullOrBlank() && !a.featureName.isNullOrBlank() ->
                "${a.thoroughfare} ${a.featureName}".trim()

            !a.thoroughfare.isNullOrBlank() ->
                a.thoroughfare!!.trim()

            else -> ""
        }

        Triple(fullAddr, postal, roadOnly)
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

    var geocodeJob by remember { mutableStateOf<Job?>(null) }

    var isServerMoving by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(true) }

    var centerLat by remember { mutableStateOf(0.0) }
    var centerLon by remember { mutableStateOf(0.0) }
    var targetLatLng by remember { mutableStateOf<com.kakao.vectormap.LatLng?>(null) }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var isRegistered by remember { mutableStateOf(false) }

    var serverLatLng by remember { mutableStateOf<com.kakao.vectormap.LatLng?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        if (isRegistered || serverLatLng != null) {
            return@rememberLauncherForActivityResult
        }

        val isFine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val isCoarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        @SuppressLint("MissingPermission")
        if (isFine || isCoarse) {
            val locationManager =
                context.getSystemService(android.content.Context.LOCATION_SERVICE)
                        as android.location.LocationManager

            try {
                val gpsLoc =
                    if (isFine && ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        locationManager.getLastKnownLocation(
                            android.location.LocationManager.GPS_PROVIDER
                        )
                    } else null

                val finalLoc = gpsLoc ?: if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    locationManager.getLastKnownLocation(
                        android.location.LocationManager.NETWORK_PROVIDER
                    )
                } else null

                if (finalLoc != null) {
                    centerLat = finalLoc.latitude
                    centerLon = finalLoc.longitude
                    targetLatLng =
                        com.kakao.vectormap.LatLng.from(finalLoc.latitude, finalLoc.longitude)

                    isServerMoving = false
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    val kakaoRetrofit = remember {
        retrofit2.Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
    }
    val placeApi = remember { kakaoRetrofit.create(PlaceApi::class.java) }
   //REST키
    val REST_API_KEY = BuildConfig.KAKAO_REST_API_KEY
    val placeVm: PlaceSearchViewModel =
        viewModel(factory = PlaceSearchVmFactory(placeApi, REST_API_KEY))


    val query by placeVm.query.collectAsState()
    val suggestions by placeVm.items.collectAsState()
    val loading by placeVm.loading.collectAsState()

    var sheetHeightDp by remember { mutableStateOf(252.dp) }

    var address by remember { mutableStateOf("") }
    var zipcode by remember { mutableStateOf("") }
    var road by remember { mutableStateOf("") }

    val orange = Color(0xFFFF7A00)

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarH = 60.dp
    val searchTop = statusTop + topBarH + 16.dp

    var kakaoMapObj by remember { mutableStateOf<com.kakao.vectormap.KakaoMap?>(null) }


    var didServerMove by remember { mutableStateOf(false) }

    fun requestAddressUpdateByLatLon(lat: Double, lon: Double) {
        geocodeJob?.cancel()
        geocodeJob = scope.launch {
            delay(300)
            val (addr, post, roadAddr) = reverseGeocodeKorea(context, lat, lon)
            if (addr.isNotBlank()) {
                address = addr
                zipcode = post
                road = roadAddr
            }
        }
    }

    LaunchedEffect(Unit) {
        val userId = UserSession.userId
        Log.d("WorkplaceLoc", "LaunchedEffect Start. userId: $userId")

        if (!userId.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    Log.d("WorkplaceLoc", "Requesting location...")
                    val response = RetrofitClient.instance.getWorkplaceLocation(userId).execute()
                    Log.d("WorkplaceLoc", "Response Code: ${response.code()}")

                    var isDbLocationFound = false

                    if (response.isSuccessful && response.body() != null) {
                        val data = response.body()!!
                        val savedAddr = data.address
                        Log.d("WorkplaceLoc", "Saved Address: $savedAddr")

                        val sLat = data.latitude
                        val sLon = data.longitude

                        if (sLat != null && sLon != null && sLat != 0.0 && sLon != 0.0) {
                            isDbLocationFound = true
                            withContext(Dispatchers.Main) {
                                address = savedAddr ?: ""
                                road = data.roadAddress ?: ""
                                isRegistered = true

                                centerLat = sLat
                                centerLon = sLon

                                geocodeJob?.cancel()

                                serverLatLng = com.kakao.vectormap.LatLng.from(sLat, sLon)

                                isServerMoving = true
                                serverLatLng = com.kakao.vectormap.LatLng.from(sLat, sLon)
                                targetLatLng = serverLatLng
                                isServerMoving = true


                            }
                        } else if (!savedAddr.isNullOrBlank()) {
                            val latLon = geocodeToLatLon(context, savedAddr)
                            if (latLon != null) {
                                isDbLocationFound = true
                                withContext(Dispatchers.Main) {
                                    address = savedAddr
                                    // SignUpService의 WorkplaceLocationResponse는 roadAddress 필드 사용
                                    road = data.roadAddress ?: ""
                                    isRegistered = true


                                    geocodeJob?.cancel()
                                    centerLat = latLon.first
                                    centerLon = latLon.second

                                    serverLatLng = com.kakao.vectormap.LatLng.from(latLon.first, latLon.second)
                                    targetLatLng = serverLatLng
                                    isServerMoving = true
                                    
                                }
                            }
                        }
                    } else {
                        Log.e("WorkplaceLoc", "Response failed or body is null")
                    }

                    if (!isDbLocationFound) {
                        withContext(Dispatchers.Main) {
                            @SuppressLint("MissingPermission")
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager

                                val gpsLoc = if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                } else null

                                val finalLoc = gpsLoc ?: if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                                } else null

                                if (finalLoc != null) {
                                    centerLat = finalLoc.latitude
                                    centerLon = finalLoc.longitude
                                    targetLatLng = com.kakao.vectormap.LatLng.from(finalLoc.latitude, finalLoc.longitude)

                                    isServerMoving = false
                                }
                            } else {
                                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                        }
                    }

                    Unit
                } catch (e: Exception) {
                    Log.e("WorkplaceLoc", "Exception", e)
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        } else {
            isLoading = false
        }
    }

    LaunchedEffect(kakaoMapObj, serverLatLng) {
        val target = serverLatLng ?: return@LaunchedEffect
        if (kakaoMapObj == null) return@LaunchedEffect // ✅ 지도가 준비될 때까지 대기
        if (didServerMove) return@LaunchedEffect
        didServerMove = true

        targetLatLng = target

    }

    LaunchedEffect(isServerMoving) {
        if (isServerMoving) {
            delay(2000) // 2초 동안은 역지오코딩 막음
            isServerMoving = false
        }
    }


    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

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
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (c.isDark) Color(0xFF0B0F14) else Color(0xFFE5E7EB))
                    )
                } else {
                    KakaoMapView(
                        lat = centerLat,
                        lon = centerLon,
                        targetLatLng = targetLatLng,
                        onMapReady = { map -> 
                            kakaoMapObj = map
                            map.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.zoomTo(18))
                        },
                        onCenterChanged = { clat, clon ->
                            centerLat = clat
                            centerLon = clon

                            if (clat == 0.0 && clon == 0.0) return@KakaoMapView
                            if (isServerMoving) return@KakaoMapView

                            geocodeJob?.cancel()
                            geocodeJob = scope.launch {
                                delay(400)
                                if (isServerMoving) return@launch
                                val (addr, post, roadAddr) = reverseGeocodeKorea(context, clat, clon)
                                if (isServerMoving) return@launch
                                if (addr.isNotBlank()) {
                                    address = addr
                                    zipcode = post
                                    road = roadAddr
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    dropdownExpanded = false
                                    focusManager.clearFocus()
                                }
                            }
                    )
                }
            }

            SearchBarOverlay(
                query = query,
                onQueryChange = { newText ->
                    placeVm.setQuery(newText)
                    dropdownExpanded = newText.isNotBlank()
                },
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                items = suggestions,
                loading = loading,
                onSelectItem = { selected ->
                    dropdownExpanded = false
                    focusManager.clearFocus()

                    val lat = selected.lat
                    val lon = selected.lon

                    if (lat != null && lon != null) {
                        targetLatLng = com.kakao.vectormap.LatLng.from(lat, lon)

                        centerLat = lat
                        centerLon = lon

                        address = selected.address ?: address
                        zipcode = selected.zipcode ?: zipcode
                        road = selected.road_address ?: road

                        requestAddressUpdateByLatLon(lat, lon)
                    } else {
                        Toast.makeText(context, "좌표 정보가 없는 항목입니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                isPreview = isPreview,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 24.dp, end = 24.dp, top = searchTop)
            )

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

            if (!isRegistered) {
                MapFloatButton(
                    onClick = {

                        requestAddressUpdateByLatLon(centerLat, centerLon)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = sheetHeightDp + 24.dp)
                )
            }

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
                        val userId = UserSession.userId
                        if (userId.isNullOrBlank()) {
                            Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val request = RegisterLocationRequest(userId, address, road, zipcode, centerLat, centerLon)
                                    val response = RetrofitClient.instance.registerWorkplaceLocation(request).execute()

                                    withContext(Dispatchers.Main) {
                                        if (response.isSuccessful) {
                                            isRegistered = true
                                            Toast.makeText(context, "위치 등록 성공", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "등록 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "서버 연결 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    },
                    onDelete = {
                        val userId = UserSession.userId
                        if (userId.isNullOrBlank()) {
                            Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    // '내 현장'은 서버에서 기본값으로 사용하는 이름입니다.
                                    val request = DeleteWorkplaceRequest(placeName = "내 현장", adminId = userId)
                                    val response = RetrofitClient.instance.resetWorkplaceLocation(request).execute()

                                    withContext(Dispatchers.Main) {
                                        if (response.isSuccessful) {
                                            isRegistered = false
                                            Toast.makeText(context, "위치 삭제 성공", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "삭제 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    onEdit = {
                        val userId = UserSession.userId
                        if (userId.isNullOrBlank()) {
                            Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val request = RegisterLocationRequest(userId, address, road, zipcode, centerLat, centerLon)
                                    val response = RetrofitClient.instance.registerWorkplaceLocation(request).execute()

                                    withContext(Dispatchers.Main) {
                                        if (response.isSuccessful) {
                                            Toast.makeText(context, "위치 수정 성공", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "수정 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "서버 연결 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                )
            }

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
    items: List<PlaceSuggestion>,
    loading: Boolean,
    onSelectItem: (PlaceSuggestion) -> Unit,
    isPreview: Boolean,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current
    val density = LocalDensity.current
    var fieldWidthDp by remember { mutableStateOf(0.dp) }

    val showDropdown =
        expanded && query.trim().length >= 2 && (loading || items.isNotEmpty())

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

        if (showDropdown) {
            Surface(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                shape = RoundedCornerShape(12.dp),
                color = c.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "검색중...",
                            fontFamily = Pretendard,
                            fontSize = 14.sp,
                            color = c.sub
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(items.size) { idx ->
                            val item = items[idx]

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectItem(item)
                                        onExpandedChange(false)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    fontFamily = Pretendard,
                                    fontSize = 16.sp,
                                    color = c.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!item.address.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.address!!,
                                        fontFamily = Pretendard,
                                        fontSize = 12.sp,
                                        color = c.sub,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (idx != items.lastIndex) {
                                Divider(color = c.divider)
                            }
                        }
                    }
                }
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
