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

/** ✅ 지오코딩: 주소 -> (lat, lon) */
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

/** ✅ 역지오코딩: (lat, lon) -> (전체주소, 우편번호, 도로명 비슷한 값) */
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

        // ✅ 도로명만 만들기 (시/구/국가 제거)
        // 1순위: 도로명(thoroughfare) + 번지(subThoroughfare)
        // 2순위: 도로명(thoroughfare) + featureName(건물번호/번지)
        // 3순위: 도로명(thoroughfare)만
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

    // ✅ (중요) 디바운스용 Job 선언 (기존 코드에 없어서 컴파일 에러 나던 부분)
    var geocodeJob by remember { mutableStateOf<Job?>(null) }

    // ✅ [위치 이동] 서버 데이터로 이동 중일 때 역지오코딩을 막기 위한 플래그 (Launcher보다 먼저 선언되어야 함)
    var isServerMoving by remember { mutableStateOf(true) }

    // ✅ [추가] 초기 데이터 로딩 상태 (DB 조회 전 지도 렌더링 방지)
    var isLoading by remember { mutableStateOf(true) }

    // ✅ "현재 중심좌표"를 직접 읽기 어려운 경우를 대비해서 상태로 관리
    // (기본값=초기 카메라 위치)
    var centerLat by remember { mutableStateOf(0.0) }
    var centerLon by remember { mutableStateOf(0.0) }
    var targetLatLng by remember { mutableStateOf<com.kakao.vectormap.LatLng?>(null) }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var isRegistered by remember { mutableStateOf(false) }

    // ✅ 서버에서 가져온 초기 좌표
    var serverLatLng by remember { mutableStateOf<com.kakao.vectormap.LatLng?>(null) }

    // ✅ 위치 권한 요청 Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isFine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val isCoarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        @SuppressLint("MissingPermission")
        if (isFine || isCoarse) {
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            try {
                // ✅ var loc 대신 val을 사용하여 스마트 캐스트 문제 해결
                val gpsLoc = if (isFine && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                } else null

                val finalLoc = gpsLoc ?: if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                } else null

                if (finalLoc != null) {
                    centerLat = finalLoc.latitude
                    centerLon = finalLoc.longitude
                    targetLatLng = com.kakao.vectormap.LatLng.from(finalLoc.latitude, finalLoc.longitude)
                    
                    // ✅ GPS로 이동했으므로 주소 업데이트 허용
                    isServerMoving = false
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    // PlaceSearchViewModel 내부에서 사용하는 Retrofit은 Kakao API용이므로 유지하거나
    // ViewModel 내부 구현에 따라 다르겠지만, 여기서는 Activity 내의 불필요한 Retrofit 빌더만 제거합니다.
    // (참고: PlaceSearchViewModel 팩토리 생성 부분은 기존 코드 유지)
    // 다만, 아래 코드에서 placeApi, kakaoCoordApi를 생성하는 부분은 Kakao API용이므로
    // 이 부분은 서버 연결(10.0.2.2)과 무관하지만, 질문의 의도인 "서버 요청 부분" 수정을 위해
    // 로컬 서버 통신 부분 위주로 수정합니다.
    // *주의*: 위에서 retrofit 변수를 지우면 placeApi 생성에서 에러가 날 수 있으므로
    // Kakao API용 Retrofit은 남겨두거나 별도로 처리해야 합니다.
    // 하지만 질문의 핵심인 '로컬 서버 접속 문제' 해결을 위해 아래 로직을 수정합니다.

    // Kakao API용 Retrofit (기존 유지)
    val kakaoRetrofit = remember {
        retrofit2.Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
    }
    val placeApi = remember { kakaoRetrofit.create(PlaceApi::class.java) }
    val REST_API_KEY = "549ef0580861ccd75dc20bc5858e349f"
    val placeVm: PlaceSearchViewModel =
        viewModel(factory = PlaceSearchVmFactory(placeApi, REST_API_KEY))


    val query by placeVm.query.collectAsState()
    val suggestions by placeVm.items.collectAsState()
    val loading by placeVm.loading.collectAsState()

    // ✅ 하단 시트 높이(측정값)
    var sheetHeightDp by remember { mutableStateOf(252.dp) }

    // ✅ 표시되는 주소 정보
    var address by remember { mutableStateOf("") }
    var zipcode by remember { mutableStateOf("") }
    var road by remember { mutableStateOf("") }

    val orange = Color(0xFFFF7A00)

    // ✅ 상태바/탑바 높이
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarH = 60.dp
    val searchTop = statusTop + topBarH + 16.dp

    // ✅ 카카오맵 객체
    var kakaoMapObj by remember { mutableStateOf<com.kakao.vectormap.KakaoMap?>(null) }


    var didServerMove by remember { mutableStateOf(false) }

    /** ✅ 좌표 기준으로 주소 업데이트 (MapView/osmdroid 제거) */
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

    // ✅ 초기 진입 시 서버에서 위치 정보 가져오기
    LaunchedEffect(Unit) {
        val userId = UserSession.userId
        Log.d("WorkplaceLoc", "LaunchedEffect Start. userId: $userId")

        if (!userId.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    Log.d("WorkplaceLoc", "Requesting location...")
                    // ✅ RetrofitClient.instance 사용 (동기 호출 execute)
                    val response = RetrofitClient.instance.getWorkplaceLocation(userId).execute()
                    Log.d("WorkplaceLoc", "Response Code: ${response.code()}")

                    var isDbLocationFound = false

                    if (response.isSuccessful && response.body() != null) {
                        val data = response.body()!!
                        val savedAddr = data.address
                        Log.d("WorkplaceLoc", "Saved Address: $savedAddr")

                        // ✅ [수정] 서버에서 위도/경도가 있으면 우선 사용
                        val sLat = data.latitude
                        val sLon = data.longitude

                        if (sLat != null && sLon != null && sLat != 0.0 && sLon != 0.0) {
                            isDbLocationFound = true
                            withContext(Dispatchers.Main) {
                                address = savedAddr ?: ""
                                road = data.roadAddress ?: ""
                                isRegistered = true

                                // ✅ [수정] 지도 중심 좌표 상태도 함께 업데이트하여 리컴포지션 시 위치 유지
                                centerLat = sLat
                                centerLon = sLon

                                // ✅ [중요] 서버 데이터 적용 시작: 진행 중인 역지오코딩 취소 및 플래그 설정
                                geocodeJob?.cancel()

                                // ✅ 상태 변수를 즉시 업데이트하여 지도가 처음부터 해당 위치를 바라보게 함
                                // targetLatLng를 설정하면 지도가 이동하고, 그 후 onCenterChanged에서 centerLat/Lon이 자연스럽게 업데이트됨
                                serverLatLng = com.kakao.vectormap.LatLng.from(sLat, sLon)
                                
                                // ✅ [추가] 서버 이동 시작 플래그 설정
                                isServerMoving = true
                                serverLatLng = com.kakao.vectormap.LatLng.from(sLat, sLon)
                                targetLatLng = serverLatLng          // ✅ 추가: 즉시 카메라 이동 타겟 세팅
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

                                    // ✅ [중요] 서버 데이터 적용 시작
                                    geocodeJob?.cancel()

                                    // ✅ [수정] 지도 중심 좌표 상태도 함께 업데이트
                                    centerLat = latLon.first
                                    centerLon = latLon.second

                                    // ✅ 서버 주소 -> 좌표로 카메라 이동 준비
                                    serverLatLng = com.kakao.vectormap.LatLng.from(latLon.first, latLon.second)
                                    targetLatLng = serverLatLng
                                    isServerMoving = true
                                    
                                }
                            }
                        }
                    } else {
                        Log.e("WorkplaceLoc", "Response failed or body is null")
                    }

                    // ✅ DB에 위치 정보가 없으면 GPS 위치로 이동
                    if (!isDbLocationFound) {
                        withContext(Dispatchers.Main) {
                            @SuppressLint("MissingPermission")
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                                
                                // ✅ var loc 대신 val을 사용하여 스마트 캐스트 문제 해결
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
                                    
                                    // ✅ GPS 위치로 이동 시에는 역지오코딩 허용
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
                    // ✅ [수정 3] 서버 데이터 수신 완료(성공/실패) 시 로딩 해제
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

        // ✅ "이동"은 targetLatLng로
        targetLatLng = target

        // [수정] 상태 동기화 제거 (onCenterChanged에 위임)
    }

    // ✅ [추가] 서버 이동 플래그가 켜지면 2초 뒤에 자동으로 끔 (지도 이동 완료 예상 시간)
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

            // ✅ 지도
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
            } else // ✅ 지도
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

                    // ✅ [추가] 서버/DB 조회 완료 전에는 지도 렌더링 자체를 안 함 (0,0 깜빡임 제거)
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
                            onMapReady = { map -> kakaoMapObj = map },
                            onCenterChanged = { clat, clon ->
                                centerLat = clat
                                centerLon = clon

                                if (clat == 0.0 && clon == 0.0) return@KakaoMapView
                                if (isServerMoving) return@KakaoMapView  // ✅ isLoading 체크는 이제 불필요(이미 로딩 끝나야 지도 뜸)

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


            // ✅ 검색창 + 드롭다운
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
                        // ✅ 이동은 targetLatLng로만 트리거 (지도 드래그해도 다시 안 돌아감)
                        targetLatLng = com.kakao.vectormap.LatLng.from(lat, lon)

                        // ✅ 현재 중심 좌표 상태도 동기화
                        centerLat = lat
                        centerLon = lon

                        // ✅ 표시값은 일단 업데이트
                        address = selected.address ?: address
                        zipcode = selected.zipcode ?: zipcode
                        road = selected.road_address ?: road

                        // ✅ 정확한 주소로 다시 한 번 역지오코딩 (선택 직후 카드 즉시 갱신)
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




                // ✅ 가운데 핀
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
            // (기존의 mapViewHolder/osmdroid 사용 제거 → centerLat/centerLon으로 역지오코딩)
            if (!isRegistered) {
                MapFloatButton(
                    onClick = {
                        // ✅ 현재 중심좌표(상태)를 기준으로 주소 갱신
                        requestAddressUpdateByLatLon(centerLat, centerLon)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = sheetHeightDp + 24.dp)
                )
            }

            // ✅ 하단 카드
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
                                // ✅ RetrofitClient.instance 사용
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
                                // ✅ RetrofitClient.instance 사용
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
