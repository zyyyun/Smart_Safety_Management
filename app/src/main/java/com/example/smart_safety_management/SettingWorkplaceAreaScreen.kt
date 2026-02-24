package com.example.smart_safety_management

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import java.lang.reflect.Proxy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalFocusManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// -----------------------------
// Theme constants
// -----------------------------
private val BrandOrange = Color(0xFFF97316)

private val PretendardBold = FontFamily(
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

private val PretendardMedium = FontFamily(
    Font(R.font.pretendard_medium, FontWeight.Medium)
)

// -----------------------------
// Models / States
// -----------------------------
private enum class DrawMode { NONE, RECT_2TAP, POLYGON_TAP }
private enum class UiState { IDLE, DRAWING_NEW, EDITING_SELECTED }

private data class Zone(
    val id: String,
    val name: String,
    val points: List<LatLng>
)

// -----------------------------
// Screen
// -----------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingWorkplaceAreaScreen(
    initialLat: Double = 37.5665,
    initialLon: Double = 126.9780,
) {
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    // -----------------------------
    // ✅ Search VM 연결 (실제 검색 동작)
    // -----------------------------
    val kakaoRetrofit = remember {
        Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val placeApi = remember { kakaoRetrofit.create(PlaceApi::class.java) }

    // TODO: 너가 쓰던 REST 키 그대로 넣어 (현장위치설정 화면에서 쓰던 값)
    val REST_API_KEY = "549ef0580861ccd75dc20bc5858e349f"

    val placeVm: PlaceSearchViewModel =
        viewModel(factory = PlaceSearchVmFactory(placeApi, REST_API_KEY))

    val query by placeVm.query.collectAsState()
    val suggestions by placeVm.items.collectAsState()
    val loading by placeVm.loading.collectAsState()
    var dropdownExpanded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    // -----------------------------
    // 기존 상태들
    // -----------------------------
    val zones = remember { mutableStateListOf<Zone>() }
    var selectedZoneId by remember { mutableStateOf<String?>(null) }

    var uiState by remember { mutableStateOf(UiState.IDLE) }
    var mode by remember { mutableStateOf(DrawMode.NONE) }

    var rectStart by remember { mutableStateOf<LatLng?>(null) }
    val draftPoints = remember { mutableStateListOf<LatLng>() }

    var mapTick by remember { mutableStateOf(0) }

    var statusText by remember {
        mutableStateOf("영역을 등록하려면 하단의 '영역 등록 시작'을 눌러주세요")
    }

    val selectedZone: Zone? = zones.firstOrNull { it.id == selectedZoneId }

    // ✅ 바텀시트: 처음 높이(피크) 줄이기
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
        sheetContainerColor = Color.White,
        sheetTonalElevation = 2.dp,
        sheetShadowElevation = 8.dp,
        sheetDragHandle = null, // ✅ 짝대기(드래그핸들) 위에 하나 더 생기는거 방지
        topBar = {
            TopAppBar(
                title = { Text(text = "영역 설정", fontFamily = PretendardBold) },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.backicon),
                            contentDescription = "Back",
                            tint = Color.Black,   // 원하면 BrandOrange로 바꿔도 됨
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        },
        sheetContent = {
            BottomSheetContentCard(
                uiState = uiState,
                selectedZone = selectedZone,
                draftCount = draftPoints.size,
                statusText = statusText,
                onStartRegister = {
                    selectedZoneId = null
                    uiState = UiState.DRAWING_NEW
                    mode = DrawMode.RECT_2TAP
                    rectStart = null
                    draftPoints.clear()
                    statusText = "등록 모드: 지도를 눌러 영역을 만드세요 (기본: 사각형)"
                },
                onConfirmRegister = {
                    if (draftPoints.size < 3) {
                        statusText = "영역이 너무 작아요 (최소 3점 필요)"
                        return@BottomSheetContentCard
                    }
                    val newZone = Zone(
                        id = System.currentTimeMillis().toString(),
                        name = "구역 ${zones.size + 1}",
                        points = draftPoints.toList()
                    )
                    zones.add(newZone)

                    draftPoints.clear()
                    rectStart = null
                    uiState = UiState.IDLE
                    selectedZoneId = newZone.id
                    mode = DrawMode.NONE
                    statusText = "등록 완료: ${newZone.name}"
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
                },
                onSaveEdit = {
                    val z = selectedZone ?: return@BottomSheetContentCard
                    if (draftPoints.size < 3) {
                        statusText = "영역이 너무 작아요 (최소 3점 필요)"
                        return@BottomSheetContentCard
                    }
                    val idx = zones.indexOfFirst { it.id == z.id }
                    if (idx >= 0) zones[idx] = z.copy(points = draftPoints.toList())

                    draftPoints.clear()
                    rectStart = null
                    mode = DrawMode.NONE
                    uiState = UiState.IDLE
                    statusText = "편집 저장 완료"
                },
                onDeleteSelected = {
                    val id = selectedZoneId ?: return@BottomSheetContentCard
                    zones.removeAll { it.id == id }

                    selectedZoneId = null
                    draftPoints.clear()
                    rectStart = null
                    mode = DrawMode.NONE
                    uiState = UiState.IDLE
                    statusText = "삭제 완료"
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // ✅ 빈곳 터치하면 키보드 내리고 드롭다운 닫기
                    focusManager.clearFocus()
                    dropdownExpanded = false
                }
        ) {
            // Map
            KakaoMapView(
                lat = initialLat,
                lon = initialLon,
                modifier = Modifier.fillMaxSize(),
                onMapReady = { km ->
                    kakaoMap = km


                    attachMapTapListenerSafely(km) { latLng ->
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

            // ✅ 상단 검색바 (좌우 여백 + 실제 검색 연결)
            SearchBarOverlay(
                query = query,
                onQueryChange = { text ->
                    placeVm.setQuery(text)                 // ✅ 실제 검색
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
                        // ✅ 지도 이동
                        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(ll))
                        // (원하면 확대도)
                        // kakaoMap?.moveCamera(CameraUpdateFactory.zoomTo(18))
                    }
                },
                isPreview = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp) // ✅ 양옆 안 닿게
            )

            // Overlay
            ZonesOverlay(
                kakaoMap = kakaoMap,
                zones = zones.toList(),
                selectedZoneId = selectedZoneId,
                draftPoints = draftPoints.toList(),
                uiState = uiState,
                tick = mapTick,
                modifier = Modifier.fillMaxSize()
            )

            // 툴바
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
// Search / ToolBar
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
            color = Color.White,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp
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
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(20.dp)
                )


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
                            color = Color(0xFF9CA3AF)
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = PretendardMedium,
                        fontSize = 16.sp,
                        color = Color.Black
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
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
                color = Color.White,
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
                            fontFamily = PretendardMedium,
                            fontSize = 14.sp,
                            color = Color(0xFF6B7280)
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
                                    color = Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!item.address.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.address!!,
                                        fontFamily = PretendardMedium,
                                        fontSize = 12.sp,
                                        color = Color(0xFF6B7280),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (idx != items.lastIndex) {
                                Divider(color = Color(0xFFE5E7EB))
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onRectMode, enabled = enabled) {
                Icon(Icons.Default.Place, contentDescription = "사각형")
            }
            IconButton(onClick = onPolyMode, enabled = enabled) {
                Icon(Icons.Default.Edit, contentDescription = "다각형")
            }
            IconButton(onClick = onClearDraft, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "임시영역 초기화")
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = if (!enabled) "LOCK" else when (mode) {
                    DrawMode.RECT_2TAP -> "RECT"
                    DrawMode.POLYGON_TAP -> "POLY"
                    else -> "READY"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PretendardMedium
            )
        }
    }
}

// -----------------------------
// ✅ BottomSheet content (스크롤 가능 + PretendardMedium)
// -----------------------------
@Composable
private fun BottomSheetContentCard(
    uiState: UiState,
    selectedZone: Zone?,
    draftCount: Int,
    statusText: String,
    onStartRegister: () -> Unit,
    onConfirmRegister: () -> Unit,
    onCancel: () -> Unit,
    onEditSelected: () -> Unit,
    onSaveEdit: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 170.dp)
            .verticalScroll(scroll)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp)
    ) {
        // drag handle (한 개만)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp, bottom = 16.dp)
                .width(46.dp)
                .height(6.dp)
                .background(Color(0xFFBFC5CC), RoundedCornerShape(999.dp))
        )

        Text(
            text = selectedZone?.name ?: "영역",
            fontSize = 20.sp,
            fontFamily = PretendardBold,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827),
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
            color = Color(0xFF374151)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = statusText,
            fontSize = 14.sp,
            fontFamily = PretendardMedium,
            color = Color(0xFF6B7280),
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(16.dp))

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
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF58616A))
                        ) {
                            Text("삭제", fontFamily = PretendardMedium, fontSize = 18.sp)
                        }

                        Button(
                            onClick = onEditSelected,
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6))
                        ) {
                            Text("영역 편집", fontFamily = PretendardMedium, fontSize = 18.sp, color = Color(0xFF58616A))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// -----------------------------
// Overlay Rendering (Canvas)
// -----------------------------
@Composable
private fun ZonesOverlay(
    kakaoMap: KakaoMap?,
    zones: List<Zone>,
    selectedZoneId: String?,
    draftPoints: List<LatLng>,
    uiState: UiState,
    tick: Int,
    modifier: Modifier = Modifier,
) {
    val conversionOk = remember(kakaoMap, tick, zones, draftPoints, selectedZoneId, uiState) {
        val km = kakaoMap ?: return@remember true
        val anyPts = (zones.firstOrNull()?.points ?: emptyList()) + draftPoints
        if (anyPts.isEmpty()) return@remember true
        anyPts.take(1).all { latLngToScreenOffsetSafely(km, it) != null }
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

    Canvas(modifier = modifier) {
        val km = kakaoMap ?: return@Canvas

        zones.forEach { z ->
            val isSelected = z.id == selectedZoneId
            val pts = z.points
            if (pts.size < 3) return@forEach

            val screenPts = pts.mapNotNull { ll ->
                latLngToScreenOffsetSafely(km, ll)?.let { (x, y) -> Offset(x, y) }
            }
            if (screenPts.size < 3) return@forEach

            val path = Path().apply {
                moveTo(screenPts[0].x, screenPts[0].y)
                for (i in 1 until screenPts.size) lineTo(screenPts[i].x, screenPts[i].y)
                close()
            }

            val fill = if (isSelected) Color(0x3322C55E) else Color(0x2216A34A)
            val stroke = if (isSelected) Color(0xFF22C55E) else Color(0xFF16A34A)

            drawPath(path = path, color = fill, style = Fill)
            drawPath(
                path = path,
                color = stroke,
                style = Stroke(width = if (isSelected) 5f else 3f, cap = StrokeCap.Round)
            )
        }

        if ((uiState == UiState.DRAWING_NEW || uiState == UiState.EDITING_SELECTED) && draftPoints.size >= 2) {
            val screenPts = draftPoints.mapNotNull { ll ->
                latLngToScreenOffsetSafely(km, ll)?.let { (x, y) -> Offset(x, y) }
            }
            if (screenPts.size >= 2) {
                val path = Path().apply {
                    moveTo(screenPts[0].x, screenPts[0].y)
                    for (i in 1 until screenPts.size) lineTo(screenPts[i].x, screenPts[i].y)
                    if (screenPts.size >= 3) close()
                }

                if (screenPts.size >= 3) drawPath(path = path, color = Color(0x332563EB), style = Fill)
                drawPath(
                    path = path,
                    color = Color(0xFF2563EB),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )

                screenPts.forEach { p ->
                    drawCircle(color = Color.White, radius = 10f, center = p)
                    drawCircle(color = Color(0xFF3B82F6), radius = 6f, center = p)
                }
            }
        }
    }
}

// -----------------------------
// Map Tap Listener (reflection)
// -----------------------------
private fun attachMapTapListenerSafely(
    km: KakaoMap,
    onTap: (LatLng) -> Unit
) {
    val method = km.javaClass.methods.firstOrNull { m ->
        val n = m.name.lowercase()
        (n.contains("setonmap") && n.contains("click")) ||
                (n.contains("setonmap") && n.contains("tap")) ||
                (n.contains("seton") && n.contains("map") && n.contains("click")) ||
                (n.contains("seton") && n.contains("map") && n.contains("tap"))
    } ?: run {
        Log.e("ZONE", "❌ No tap/click listener setter found on KakaoMap")
        return
    }

    val paramType = method.parameterTypes.firstOrNull() ?: return
    if (!paramType.isInterface) return

    val proxy = Proxy.newProxyInstance(
        paramType.classLoader,
        arrayOf(paramType)
    ) { _, _, args ->
        val ll = args?.firstOrNull { it is LatLng } as? LatLng
        if (ll != null) {
            onTap(ll)
            true
        } else false
    }

    runCatching { method.invoke(km, proxy) }
        .onFailure { Log.e("ZONE", "❌ attach tap listener failed: ${method.name}", it) }
}

// -----------------------------
// Geometry helpers
// -----------------------------
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

// -----------------------------
// LatLng -> Screen offset (reflection, best-effort)
// -----------------------------
private fun latLngToScreenOffsetSafely(
    km: KakaoMap,
    latLng: LatLng
): Pair<Float, Float>? {
    runCatching {
        val direct = km.javaClass.methods.firstOrNull { m ->
            val n = m.name.lowercase()
            m.parameterTypes.size == 1 &&
                    m.parameterTypes[0].name.contains("LatLng") &&
                    (n.contains("toscreen") ||
                            (n.contains("latlng") && n.contains("point")) ||
                            (n.contains("screen") && n.contains("point")))
        }
        if (direct != null) {
            val pt = direct.invoke(km, latLng) ?: return@runCatching
            extractXY(pt)?.let { return it }
        }
    }

    val proj = runCatching {
        km.javaClass.methods.firstOrNull { m ->
            val n = m.name.lowercase()
            n.contains("projection") || (m.returnType?.name?.lowercase()?.contains("projection") == true)
        }?.invoke(km)
    }.getOrNull()

    if (proj != null) {
        val toScreen = proj.javaClass.methods.firstOrNull { m ->
            val n = m.name.lowercase()
            m.parameterTypes.size == 1 &&
                    m.parameterTypes[0].name.contains("LatLng") &&
                    (n.contains("toscreen") || (n.contains("latlng") && n.contains("point")) || n.contains("topoint"))
        }

        if (toScreen != null) {
            val pt = runCatching { toScreen.invoke(proj, latLng) }.getOrNull()
            if (pt != null) extractXY(pt)?.let { return it }
        }
    }

    return null
}

private fun extractXY(pt: Any): Pair<Float, Float>? {
    val x = runCatching { pt.javaClass.getField("x").get(pt).toString().toFloat() }.getOrNull()
        ?: runCatching { pt.javaClass.getMethod("getX").invoke(pt).toString().toFloat() }.getOrNull()
        ?: return null

    val y = runCatching { pt.javaClass.getField("y").get(pt).toString().toFloat() }.getOrNull()
        ?: runCatching { pt.javaClass.getMethod("getY").invoke(pt).toString().toFloat() }.getOrNull()
        ?: return null

    return x to y
}