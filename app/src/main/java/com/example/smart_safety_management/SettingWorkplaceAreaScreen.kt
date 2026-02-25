package com.example.smart_safety_management

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

private val BrandOrange = Color(0xFFF97316)

private val PretendardBold = FontFamily(
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

private val PretendardMedium = FontFamily(
    Font(R.font.pretendard_medium, FontWeight.Medium)
)

private enum class DrawMode { NONE, RECT_2TAP, POLYGON_TAP }
private enum class UiState { IDLE, DRAWING_NEW, EDITING_SELECTED }

private data class Zone(
    val id: Int,
    val name: String,
    val points: List<LatLng>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingWorkplaceAreaScreen(
    initialLat: Double = 37.5665,
    initialLon: Double = 126.9780,
    userId: String
) {
    val c = LocalSafeColors.current
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    val kakaoRetrofit = remember {
        Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val placeApi = remember { kakaoRetrofit.create(PlaceApi::class.java) }

    val REST_API_KEY = "549ef0580861ccd75dc20bc5858e349f"
    val placeVm: PlaceSearchViewModel =
        viewModel(factory = PlaceSearchVmFactory(placeApi, REST_API_KEY))

    val query by placeVm.query.collectAsState()
    val suggestions by placeVm.items.collectAsState()
    val loading by placeVm.loading.collectAsState()
    var dropdownExpanded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    val zones = remember { mutableStateListOf<Zone>() }
    var selectedZoneId by remember { mutableStateOf<Int?>(null) }
    var currentGroupId by remember { mutableStateOf<Int?>(null) }
    var pendingCameraMove by remember { mutableStateOf<LatLng?>(null) }

    var uiState by remember { mutableStateOf(UiState.IDLE) }
    var mode by remember { mutableStateOf(DrawMode.NONE) }

    var rectStart by remember { mutableStateOf<LatLng?>(null) }
    val draftPoints = remember { mutableStateListOf<LatLng>() }

    var mapTick by remember { mutableStateOf(0) }

    var statusText by remember {
        mutableStateOf("영역을 등록하려면 하단의 '영역 등록 시작'을 눌러주세요")
    }
    var inputZoneName by remember { mutableStateOf("") }

    val appRetrofit = remember {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val signUpService = remember { appRetrofit.create(SignUpService::class.java) }

    LaunchedEffect(userId) {
        signUpService.getUserInfo(userId).enqueue(object : Callback<GetUserInfoResponse> {
            override fun onResponse(call: Call<GetUserInfoResponse>, response: Response<GetUserInfoResponse>) {
                if (response.isSuccessful) {
                    currentGroupId = response.body()?.groupId
                    if (currentGroupId == null) {
                        statusText = "그룹에 속해있지 않아 영역을 관리할 수 없습니다."
                    }
                }
            }
            override fun onFailure(call: Call<GetUserInfoResponse>, t: Throwable) {
                statusText = "유저 정보 로드 실패: ${t.message}"
            }
        })
    }

    LaunchedEffect(currentGroupId) {
        val gid = currentGroupId
        if (gid != null) {
            signUpService.getGeofenceZones(gid).enqueue(object : Callback<GetGeofenceZonesResponse> {
                override fun onResponse(call: Call<GetGeofenceZonesResponse>, response: Response<GetGeofenceZonesResponse>) {
                    if (response.isSuccessful) {
                        val serverZones = response.body()?.zones ?: emptyList()
                        zones.clear()
                        zones.addAll(serverZones.map { dto ->
                            Zone(
                                id = dto.zoneId,
                                name = dto.zoneName,
                                points = dto.points.map { LatLng.from(it.latitude, it.longitude) }
                            )
                        })
                        statusText = "서버에서 ${zones.size}개의 영역을 불러왔습니다."
                    }
                }
                override fun onFailure(call: Call<GetGeofenceZonesResponse>, t: Throwable) {
                    Log.e("SettingArea", "Failed to load zones", t)
                    statusText = "영역 불러오기 실패: ${t.message}"
                }
            })
        }
    }

    LaunchedEffect(userId) {
        signUpService.getWorkplaceLocation(userId).enqueue(object : Callback<WorkplaceLocationResponse> {
            override fun onResponse(call: Call<WorkplaceLocationResponse>, response: Response<WorkplaceLocationResponse>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.latitude != null && body.longitude != null) {
                        pendingCameraMove = LatLng.from(body.latitude, body.longitude)
                    }
                }
            }
            override fun onFailure(call: Call<WorkplaceLocationResponse>, t: Throwable) {
                Log.e("SettingArea", "Failed to load workplace location", t)
            }
        })
    }

    LaunchedEffect(kakaoMap, pendingCameraMove) {
        if (kakaoMap != null && pendingCameraMove != null) {
            kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(pendingCameraMove, 17))
            pendingCameraMove = null
        }
    }

    val selectedZone: Zone? = zones.firstOrNull { it.id == selectedZoneId }

    val sheetPeek = 220.dp
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = sheetPeek,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),

        // ✅ 다크모드 적용 핵심
        containerColor = c.bg,
        sheetContainerColor = c.surface,
        sheetTonalElevation = 2.dp,
        sheetShadowElevation = 8.dp,
        sheetDragHandle = null,

        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "영역 설정",
                        fontFamily = PretendardBold,
                        color = c.text
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.backicon),
                            contentDescription = "Back",
                            tint = if (c.isDark) Color.White else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.topBar
                )
            )
        },
        sheetContent = {
            BottomSheetContentCard(
                uiState = uiState,
                selectedZone = selectedZone,
                draftCount = draftPoints.size,
                statusText = statusText,
                inputZoneName = inputZoneName,
                onZoneNameChange = { inputZoneName = it },
                onStartRegister = {
                    selectedZoneId = null
                    uiState = UiState.DRAWING_NEW
                    mode = DrawMode.RECT_2TAP
                    rectStart = null
                    draftPoints.clear()
                    statusText = "등록 모드: 지도를 눌러 영역을 만드세요 (기본: 사각형)"
                    inputZoneName = "구역 ${zones.size + 1}"
                },
                onConfirmRegister = {
                    if (draftPoints.size < 3) {
                        statusText = "영역이 너무 작아요 (최소 3점 필요)"
                        return@BottomSheetContentCard
                    }

                    val gid = currentGroupId
                    if (gid == null) {
                        statusText = "그룹 정보를 불러오지 못해 등록할 수 없습니다."
                        return@BottomSheetContentCard
                    }

                    val pointsDto = draftPoints.map { GeofencePointDTO(it.latitude, it.longitude) }
                    val req = CreateGeofenceRequest(
                        groupId = gid,
                        zoneName = inputZoneName.ifBlank { "구역 ${zones.size + 1}" },
                        points = pointsDto
                    )

                    signUpService.createGeofenceZone(req).enqueue(object : Callback<CreateGeofenceResponse> {
                        override fun onResponse(call: Call<CreateGeofenceResponse>, response: Response<CreateGeofenceResponse>) {
                            if (response.isSuccessful && response.body() != null) {
                                val newId = response.body()!!.zoneId
                                val newZone = Zone(
                                    id = newId,
                                    name = req.zoneName,
                                    points = draftPoints.toList()
                                )
                                zones.add(newZone)

                                draftPoints.clear()
                                rectStart = null
                                uiState = UiState.IDLE
                                selectedZoneId = newZone.id
                                mode = DrawMode.NONE
                                statusText = "등록 완료: ${newZone.name}"
                            } else {
                                statusText = "등록 실패: ${response.code()}"
                            }
                        }
                        override fun onFailure(call: Call<CreateGeofenceResponse>, t: Throwable) {
                            statusText = "등록 실패: ${t.message}"
                        }
                    })
                },
                onCancel = {
                    draftPoints.clear()
                    rectStart = null
                    mode = DrawMode.NONE
                    uiState = UiState.IDLE
                    statusText = "취소됨"
                },
                onEditSelected = {
                    val z = selectedZone ?: return@BottomSheetContentCard
                    uiState = UiState.EDITING_SELECTED
                    mode = DrawMode.POLYGON_TAP
                    rectStart = null
                    draftPoints.clear()
                    draftPoints.addAll(z.points)
                    statusText = "편집 모드: 점 추가 후 '편집 저장'을 누르세요"
                    inputZoneName = z.name
                },
                onSaveEdit = {
                    val z = selectedZone ?: return@BottomSheetContentCard
                    if (draftPoints.size < 3) {
                        statusText = "영역이 너무 작아요 (최소 3점 필요)"
                        return@BottomSheetContentCard
                    }

                    val gid = currentGroupId
                    if (gid == null) {
                        statusText = "그룹 정보를 불러오지 못해 수정할 수 없습니다."
                        return@BottomSheetContentCard
                    }

                    val pointsDto = draftPoints.map { GeofencePointDTO(it.latitude, it.longitude) }
                    val updateReq = UpdateGeofenceRequest(
                        zoneId = z.id,
                        zoneName = inputZoneName.ifBlank { z.name },
                        points = pointsDto
                    )

                    signUpService.updateGeofenceZone(updateReq).enqueue(object : Callback<UpdateGeofenceResponse> {
                        override fun onResponse(call: Call<UpdateGeofenceResponse>, response: Response<UpdateGeofenceResponse>) {
                            if (response.isSuccessful) {
                                zones.removeAll { it.id == z.id }
                                zones.add(z.copy(name = updateReq.zoneName, points = draftPoints.toList()))

                                draftPoints.clear()
                                rectStart = null
                                mode = DrawMode.NONE
                                uiState = UiState.IDLE
                                statusText = "편집 저장 완료"
                            } else {
                                statusText = "수정 실패: ${response.code()}"
                            }
                        }
                        override fun onFailure(call: Call<UpdateGeofenceResponse>, t: Throwable) {
                            statusText = "수정 실패: ${t.message}"
                        }
                    })
                },
                onDeleteSelected = {
                    val id = selectedZoneId ?: return@BottomSheetContentCard

                    signUpService.deleteGeofenceZone(DeleteGeofenceRequest(id)).enqueue(object : Callback<DeleteGeofenceResponse> {
                        override fun onResponse(call: Call<DeleteGeofenceResponse>, response: Response<DeleteGeofenceResponse>) {
                            if (response.isSuccessful) {
                                zones.removeAll { it.id == id }
                                selectedZoneId = null
                                draftPoints.clear()
                                rectStart = null
                                mode = DrawMode.NONE
                                uiState = UiState.IDLE
                                statusText = "삭제 완료"
                            } else {
                                statusText = "삭제 실패: ${response.code()}"
                            }
                        }
                        override fun onFailure(call: Call<DeleteGeofenceResponse>, t: Throwable) {
                            statusText = "삭제 실패: ${t.message}"
                        }
                    })
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(c.bg)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    focusManager.clearFocus()
                    dropdownExpanded = false
                }
        ) {
            KakaoMapView(
                lat = initialLat,
                lon = initialLon,
                modifier = Modifier.fillMaxSize(),
                onMapReady = { km ->
                    kakaoMap = km

                    km.setOnMapClickListener { _, latLng, _, _ ->
                        focusManager.clearFocus()
                        dropdownExpanded = false
                        when (uiState) {
                            UiState.DRAWING_NEW, UiState.EDITING_SELECTED -> {
                                when (mode) {
                                    DrawMode.RECT_2TAP -> {
                                        val start = rectStart
                                        if (start == null) {
                                            rectStart = latLng
                                            statusText = "반대쪽 꼭짓점을 한 번 더 눌러 사각형을 완성하세요"
                                        } else {
                                            draftPoints.clear()
                                            draftPoints.addAll(makeRectPolygon(start, latLng))
                                            rectStart = null
                                            statusText = "사각형 생성 완료 — 아래에서 등록/저장하세요"
                                        }
                                    }

                                    DrawMode.POLYGON_TAP -> {
                                        draftPoints.add(latLng)
                                        statusText = "점 추가됨 (${draftPoints.size}개) — 아래에서 등록/저장하세요"
                                    }

                                    else -> Unit
                                }
                            }

                            UiState.IDLE -> {
                                val hit = zones.lastOrNull { z -> isPointInPolygon(latLng, z.points) }
                                if (hit != null) {
                                    selectedZoneId = hit.id
                                    statusText = "선택됨: ${hit.name}  (하단에서 편집/삭제 가능)"
                                } else {
                                    selectedZoneId = null
                                    statusText = "영역이 선택되지 않았습니다. 등록을 시작하거나 영역을 탭하세요."
                                }
                            }
                        }
                    }
                },
                onCenterChanged = { _, _ -> mapTick++ }
            )

            ZonesOverlay(
                kakaoMap = kakaoMap,
                zones = zones.toList(),
                selectedZoneId = selectedZoneId,
                draftPoints = draftPoints.toList(),
                uiState = uiState,
                tick = mapTick,
                modifier = Modifier.fillMaxSize()
            )

            SearchBarOverlay(
                query = query,
                onQueryChange = { text ->
                    placeVm.setQuery(text)
                    dropdownExpanded = text.isNotBlank()
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
                        val ll = LatLng.from(lat, lon)
                        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(ll, 17))
                    }
                },
                isPreview = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp)
            )

            ToolBarOverlay(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                enabled = (uiState == UiState.DRAWING_NEW || uiState == UiState.EDITING_SELECTED),
                mode = mode,
                onRectMode = {
                    mode = DrawMode.RECT_2TAP
                    rectStart = null
                    draftPoints.clear()
                    statusText = "사각형 모드: 지도를 두 번 눌러 사각형을 만드세요"
                },
                onPolyMode = {
                    mode = DrawMode.POLYGON_TAP
                    rectStart = null
                    draftPoints.clear()
                    statusText = "다각형 모드: 지도를 눌러 점을 추가하세요"
                },
                onClearDraft = {
                    rectStart = null
                    draftPoints.clear()
                    statusText = "임시 영역이 초기화되었습니다"
                }
            )
        }
    }
}

// -----------------------------
// Search / ToolBar / Sheet (Darkable)
// -----------------------------
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

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onGloballyPositioned { coords ->
                    fieldWidthDp = with(density) { coords.size.width.toDp() }
                },
            shape = RoundedCornerShape(14.dp),
            color = c.surface,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, c.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.search),
                    contentDescription = "search",
                    tint = c.sub,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(8.dp))

                TextField(
                    value = query,
                    onValueChange = { newText ->
                        onQueryChange(newText)
                        onExpandedChange(newText.isNotBlank())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "주소를 검색하세요",
                            fontFamily = PretendardMedium,
                            color = c.sub
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = PretendardMedium,
                        fontSize = 16.sp,
                        color = c.text
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = BrandOrange
                    )
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
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, c.border)
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
                            fontFamily = PretendardMedium,
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
                                    fontFamily = PretendardMedium,
                                    fontSize = 16.sp,
                                    color = c.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!item.address.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.address!!,
                                        fontFamily = PretendardMedium,
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
private fun ToolBarOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    mode: DrawMode,
    onRectMode: () -> Unit,
    onPolyMode: () -> Unit,
    onClearDraft: () -> Unit,
) {
    val c = LocalSafeColors.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        color = c.surface,
        border = BorderStroke(1.dp, c.border)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onRectMode, enabled = enabled) {
                Icon(Icons.Default.Place, contentDescription = "사각형", tint = c.text)
            }
            IconButton(onClick = onPolyMode, enabled = enabled) {
                Icon(Icons.Default.Edit, contentDescription = "다각형", tint = c.text)
            }
            IconButton(onClick = onClearDraft, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "임시영역 초기화", tint = c.text)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = if (!enabled) "LOCK" else when (mode) {
                    DrawMode.RECT_2TAP -> "RECT"
                    DrawMode.POLYGON_TAP -> "POLY"
                    else -> "READY"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PretendardMedium,
                color = c.sub
            )
        }
    }
}

@Composable
private fun BottomSheetContentCard(
    uiState: UiState,
    selectedZone: Zone?,
    draftCount: Int,
    statusText: String,
    inputZoneName: String,
    onZoneNameChange: (String) -> Unit,
    onStartRegister: () -> Unit,
    onConfirmRegister: () -> Unit,
    onCancel: () -> Unit,
    onEditSelected: () -> Unit,
    onSaveEdit: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    val c = LocalSafeColors.current
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 170.dp)
            .verticalScroll(scroll)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp, bottom = 16.dp)
                .width(46.dp)
                .height(6.dp)
                .background(if (c.isDark) Color(0xFF3A3F45) else Color(0xFFBFC5CC), RoundedCornerShape(999.dp))
        )

        val displayTitle = when (uiState) {
            UiState.DRAWING_NEW, UiState.EDITING_SELECTED -> inputZoneName.ifBlank { "이름을 입력하세요" }
            else -> selectedZone?.name ?: "영역"
        }

        Text(
            text = displayTitle,
            fontSize = 20.sp,
            fontFamily = PretendardBold,
            fontWeight = FontWeight.Bold,
            color = c.text,
            maxLines = 1
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = when (uiState) {
                UiState.DRAWING_NEW -> "등록 중 : 점 ${draftCount}개"
                UiState.EDITING_SELECTED -> "편집 중 : 점 ${draftCount}개"
                UiState.IDLE -> if (selectedZone == null) "버튼을 눌러 등록을 시작하세요"
                else "선택된 영역입니다. 편집/삭제를 선택하세요"
            },
            fontSize = 16.sp,
            fontFamily = PretendardMedium,
            color = c.text
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = statusText,
            fontSize = 14.sp,
            fontFamily = PretendardMedium,
            color = c.sub,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(16.dp))

        if (uiState == UiState.DRAWING_NEW || uiState == UiState.EDITING_SELECTED) {
            OutlinedTextField(
                value = inputZoneName,
                onValueChange = onZoneNameChange,
                label = { Text("영역 이름", fontFamily = PretendardMedium, color = c.sub) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontFamily = PretendardMedium, color = c.text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandOrange,
                    cursorColor = BrandOrange,
                    focusedLabelColor = BrandOrange,
                    unfocusedBorderColor = c.border,
                    unfocusedLabelColor = c.sub
                )
            )
            Spacer(Modifier.height(16.dp))
        }

        when (uiState) {
            UiState.DRAWING_NEW -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BrandOrange),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandOrange)
                    ) {
                        Text("취소", fontFamily = PretendardMedium, fontSize = 18.sp)
                    }

                    Button(
                        onClick = onConfirmRegister,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                    ) {
                        Text("영역 등록", fontFamily = PretendardMedium, fontSize = 18.sp, color = Color.White)
                    }
                }
            }

            UiState.EDITING_SELECTED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BrandOrange),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandOrange)
                    ) {
                        Text("취소", fontFamily = PretendardMedium, fontSize = 18.sp)
                    }

                    Button(
                        onClick = onSaveEdit,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                    ) {
                        Text("편집 저장", fontFamily = PretendardMedium, fontSize = 18.sp, color = Color.White)
                    }
                }
            }

            UiState.IDLE -> {
                if (selectedZone == null) {
                    Button(
                        onClick = onStartRegister,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                    ) {
                        Text("영역 등록 시작", fontFamily = PretendardMedium, fontSize = 20.sp, color = Color.White)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onDeleteSelected,
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, c.border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (c.isDark) Color(0xFF8A949E) else Color(0xFF58616A),
                                containerColor = Color.Transparent
                            )
                        ) {
                            Text("삭제", fontFamily = PretendardMedium, fontSize = 18.sp)
                        }

                        Button(
                            onClick = onEditSelected,
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (c.isDark) Color(0xFF131416) else Color(0xFFF3F4F6)
                            )
                        ) {
                            Text(
                                "영역 편집",
                                fontFamily = PretendardMedium,
                                fontSize = 18.sp,
                                color = if (c.isDark) Color(0xFF8A949E) else Color(0xFF58616A)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ZonesOverlay(
    kakaoMap: KakaoMap?,
    zones: List<Zone>,
    selectedZoneId: Int?,
    draftPoints: List<LatLng>,
    uiState: UiState,
    tick: Int,
    modifier: Modifier = Modifier,
) {
    val c = LocalSafeColors.current
    val projection = remember(kakaoMap) { kakaoMap?.let { KakaoProjection(it) } }

    val conversionOk = remember(projection, zones, draftPoints) {
        val anyPts = (zones.firstOrNull()?.points ?: emptyList()) + draftPoints
        if (anyPts.isEmpty()) return@remember true
        projection != null && anyPts.take(1).all { projection.toScreen(it) != null }
    }

    if (!conversionOk) {
        Surface(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(0.92f),
            color = Color(0xB3FF1744),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "⚠️ 좌표→화면 변환 실패로 Canvas 오버레이가 보이지 않을 수 있어요.",
                color = Color.White,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = PretendardMedium
            )
        }
    }

    // ✅ 다크에서 너무 튀지 않게 톤만 살짝 조절
    val zoneFill = if (c.isDark) Color(0x1A22C55E) else Color(0x2216A34A)
    val zoneStroke = if (c.isDark) Color(0xFF22C55E) else Color(0xFF16A34A)
    val zoneFillSelected = if (c.isDark) Color(0x2622C55E) else Color(0x3322C55E)
    val zoneStrokeSelected = Color(0xFF22C55E)

    val draftFill = if (c.isDark) Color(0x1A60A5FA) else Color(0x332563EB)
    val draftStroke = if (c.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val draftPointOuter = if (c.isDark) c.bg else Color.White

    Canvas(modifier = modifier) {
        val proj = projection ?: return@Canvas

        for (z in zones) {
            val isSelected = z.id == selectedZoneId
            val pts = z.points
            if (pts.size < 3) continue

            val screenPts = pts.mapNotNull { ll -> proj.toScreen(ll) }
            if (screenPts.size < 3) continue

            val path = Path().apply {
                moveTo(screenPts[0].x, screenPts[0].y)
                for (i in 1 until screenPts.size) lineTo(screenPts[i].x, screenPts[i].y)
                close()
            }

            drawPath(path = path, color = if (isSelected) zoneFillSelected else zoneFill, style = Fill)
            drawPath(
                path = path,
                color = if (isSelected) zoneStrokeSelected else zoneStroke,
                style = Stroke(width = if (isSelected) 5f else 3f, cap = StrokeCap.Round)
            )
        }

        if ((uiState == UiState.DRAWING_NEW || uiState == UiState.EDITING_SELECTED) && draftPoints.size >= 2) {
            val screenPts = draftPoints.mapNotNull { ll -> proj.toScreen(ll) }
            if (screenPts.size >= 2) {
                val path = Path().apply {
                    moveTo(screenPts[0].x, screenPts[0].y)
                    for (i in 1 until screenPts.size) lineTo(screenPts[i].x, screenPts[i].y)
                    if (screenPts.size >= 3) close()
                }

                if (screenPts.size >= 3) drawPath(path = path, color = draftFill, style = Fill)
                drawPath(
                    path = path,
                    color = draftStroke,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )

                screenPts.forEach { p ->
                    drawCircle(color = draftPointOuter, radius = 10f, center = p)
                    drawCircle(color = draftStroke, radius = 6f, center = p)
                }
            }
        }
    }
}

private fun makeRectPolygon(a: LatLng, b: LatLng): List<LatLng> {
    val minLat = minOf(a.latitude, b.latitude)
    val maxLat = maxOf(a.latitude, b.latitude)
    val minLon = minOf(a.longitude, b.longitude)
    val maxLon = maxOf(a.longitude, b.longitude)

    return listOf(
        LatLng.from(maxLat, minLon),
        LatLng.from(maxLat, maxLon),
        LatLng.from(minLat, maxLon),
        LatLng.from(minLat, minLon),
    )
}

private fun isPointInPolygon(p: LatLng, polygon: List<LatLng>): Boolean {
    if (polygon.size < 3) return false
    val x = p.longitude
    val y = p.latitude

    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].longitude
        val yi = polygon[i].latitude
        val xj = polygon[j].longitude
        val yj = polygon[j].latitude

        val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi) + 1e-12) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

private class KakaoProjection(private val km: KakaoMap) {
    private var toScreenMethod: java.lang.reflect.Method? = null
    private var xField: java.lang.reflect.Field? = null
    private var yField: java.lang.reflect.Field? = null
    private var xMethod: java.lang.reflect.Method? = null
    private var yMethod: java.lang.reflect.Method? = null
    private var initialized = false

    fun toScreen(latLng: LatLng): Offset? {
        if (!initialized) initialize()

        val method = toScreenMethod ?: return null
        return try {
            val pt = method.invoke(km, latLng) ?: return null

            val x = xField?.get(pt)?.toString()?.toFloatOrNull()
                ?: xMethod?.invoke(pt)?.toString()?.toFloatOrNull()
            val y = yField?.get(pt)?.toString()?.toFloatOrNull()
                ?: yMethod?.invoke(pt)?.toString()?.toFloatOrNull()

            if (x != null && y != null) Offset(x, y) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun initialize() {
        try {
            toScreenMethod = km.javaClass.methods.firstOrNull { m ->
                val n = m.name.lowercase()
                m.parameterTypes.size == 1 &&
                        m.parameterTypes[0].name.contains("LatLng") &&
                        (n.contains("toscreen") || n.contains("project") || (n.contains("latlng") && n.contains("point")))
            }
            val returnType = toScreenMethod?.returnType ?: return
            xField = runCatching { returnType.getField("x") }.getOrNull()
            yField = runCatching { returnType.getField("y") }.getOrNull()
            xMethod = runCatching { returnType.getMethod("getX") }.getOrNull()
            yMethod = runCatching { returnType.getMethod("getY") }.getOrNull()
        } catch (_: Exception) { }
        initialized = true
    }
}