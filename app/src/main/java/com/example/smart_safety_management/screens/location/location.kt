package com.example.smart_safety_management.screens.location

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.smart_safety_management.KakaoMapPin
import com.example.smart_safety_management.KakaoMapView
import com.example.smart_safety_management.R
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.RetrofitClient
import com.example.smart_safety_management.UserSession
import com.example.smart_safety_management.GetCCTVListResponse
import com.example.smart_safety_management.GetLocationResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.content.Context
import com.example.smart_safety_management.CamPttViewModel
import com.kakao.vectormap.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

/* -------------------- data -------------------- */

data class WorkerRow(
    val id: String,
    val role: String,
    val name: String,
    val location: String,
    val statusText: String,
    val statusColor: Color,
    @DrawableRes val camIcon: Int = R.drawable.cam
)

@Immutable
data class WorkerPin(
    val id: String,
    val area: String,
    val lat: Double,
    val lng: Double,
    val status: WorkerStatus
)

enum class WorkerStatus { NORMAL, FEVER, FALL }

@DrawableRes
private fun iconFor(status: WorkerStatus, isDark: Boolean): Int {
    return if (isDark) {
        when (status) {
            WorkerStatus.NORMAL -> R.drawable.worker_green_dark
            WorkerStatus.FEVER -> R.drawable.worker_red_dark
            WorkerStatus.FALL -> R.drawable.worker_orange_dark
        }
    } else {
        when (status) {
            WorkerStatus.NORMAL -> R.drawable.worker_green
            WorkerStatus.FEVER -> R.drawable.worker_red
            WorkerStatus.FALL -> R.drawable.worker_orange
        }
    }
}
data class MapCircle(
    val centerLat: Double,
    val centerLng: Double,
    val radius: Int = 50, // 50m 반경
    val strokeColor: Color = Color(0xFFFF0000),
    val fillColor: Color = Color(0x33FF0000)
)


/* -------------------- screen -------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    modifier: Modifier = Modifier,
    bottomBarHeight: Dp = 68.dp,
    isDark: Boolean,
    _onTabSelect: (Int) -> Unit = {}
) {
    val c = LocalSafeColors.current
    val dark = isDark

    val sheetBg = if (dark) Color(0xFF000000) else c.surface
    val textPrimary = c.text
    val dividerStrong = c.divider
    val dividerLight = c.divider.copy(alpha = 0.7f)
    val pillBorder = c.border

    val tableTextColor = Color(0xFF33363D)
    val iconTint = tableTextColor

    // ✅ 기존 상태들
    var selectedWorkerId by remember { mutableStateOf<String?>(null) }
    var areas by remember { mutableStateOf(listOf("전체")) }
    var selectedArea by remember { mutableStateOf("전체") }

    // ✅ CamDialog 관련 (권한 런처에서 쓰니까 먼저 선언해야 함)
    var showCamDialog by remember { mutableStateOf(false) }
    var camTargetRow by remember { mutableStateOf<WorkerRow?>(null) }

    // ✅ 권한 관련
    val context = LocalContext.current

    // ✅ 현장(등록된 workplace) 좌표로 초기 카메라 시작하기 위한 상태
    var isLoadingWorkplace by remember { mutableStateOf(true) }
    var workplaceLatLng by remember { mutableStateOf<LatLng?>(null) }

    var pendingCamRow by remember { mutableStateOf<WorkerRow?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            camTargetRow = pendingCamRow
            showCamDialog = true
            pendingCamRow = null
        } else {
            pendingCamRow = null
            // (선택) 토스트/스낵바 안내 가능
        }
    }

    // ✅ [수정] 서버 데이터로 교체하기 위해 상태 변수로 변경 (초기값 빈 리스트)
    var rows by remember { mutableStateOf<List<WorkerRow>>(emptyList()) }
    var pins by remember { mutableStateOf<List<WorkerPin>>(emptyList()) }
    // ✅ 리스트 데이터 (너가 실제 데이터로 교체)

    fun rowArea(row: WorkerRow) = row.location.split(" ").first()

    val filteredRows = remember(selectedArea, rows) {
        if (selectedArea == "전체") rows else rows.filter { rowArea(it) == selectedArea }
    }

    val displayRows = remember(filteredRows, selectedWorkerId) {
        if (selectedWorkerId == null) filteredRows
        else filteredRows.filter { it.id == selectedWorkerId }
    }

    val visibleIds = remember(filteredRows) { filteredRows.map { it.id }.toSet() }

    val filteredPins = remember(selectedArea, pins, visibleIds) {
        val byArea = if (selectedArea == "전체") pins else pins.filter { it.area == selectedArea }
        byArea.filter { it.id in visibleIds }
    }

    // ✅ 선택된 작업자 -> 카메라 이동 타겟
    val pinById = remember(pins) { pins.associateBy { it.id } }
    val targetLatLng: LatLng? = remember(selectedWorkerId, pinById) {
        selectedWorkerId
            ?.let { id -> pinById[id] }
            ?.let { p -> LatLng.from(p.lat, p.lng) }
    }

    // ✅ 카카오맵 핀(선택 시 강조 / 나머지 dim)
    val kakaoPins: List<KakaoMapPin> = remember(filteredPins, selectedWorkerId, dark) {
        val hasSelection = selectedWorkerId != null
        val dimAlpha = 110
        val fullAlpha = 255

        filteredPins.map { p ->
            val isSelected = selectedWorkerId == p.id
            val alpha = when {
                !hasSelection -> fullAlpha
                isSelected -> fullAlpha
                else -> dimAlpha
            }
            KakaoMapPin(
                id = p.id,
                lat = p.lat,
                lon = p.lng,
                iconRes = iconFor(p.status, dark),
                alpha = alpha
            )
        }
    }



    /* -------------------- bottom sheet height -------------------- */

    val density = LocalDensity.current
    fun Dp.toPxSafe(): Float = with(density) { toPx() }
    fun Float.toDpSafe(): Dp = with(density) { toDp() }

    val rowH = 48.dp
    val baseH = 160.dp
    val estimatedMax = baseH + (rowH * displayRows.size)
    val maxSheetHeight = estimatedMax.coerceIn(260.dp, 460.dp)
    val minSheetHeight: Dp = 42.dp
    val initialSheetHeight = maxSheetHeight.coerceAtMost(260.dp)

    var sheetHeightPx by remember { mutableFloatStateOf(initialSheetHeight.toPxSafe()) }

    val dragState = rememberDraggableState { deltaPx ->
        sheetHeightPx = (sheetHeightPx - deltaPx)
            .coerceIn(minSheetHeight.toPxSafe(), maxSheetHeight.toPxSafe())
    }

    val sheetHeight = sheetHeightPx.toDpSafe()
    val revealHeight = 220.dp

    LaunchedEffect(selectedArea, selectedWorkerId) {
        val targetPx = revealHeight.toPxSafe()
            .coerceIn(minSheetHeight.toPxSafe(), maxSheetHeight.toPxSafe())
        if (sheetHeightPx < targetPx) sheetHeightPx = targetPx
    }

    // ✅ [추가] 등록된 현장 위치(workplace)를 서버에서 가져와 지도 초기 중심으로 사용
    LaunchedEffect(Unit) {
        val userId = UserSession.userId

        if (userId.isNullOrBlank()) {
            isLoadingWorkplace = false
            
            return@LaunchedEffect
        }

        try {
            val res = withContext(Dispatchers.IO) {
                RetrofitClient.instance.getWorkplaceLocation(userId).execute()
            }

            if (res.isSuccessful) {
                val data = res.body()
                val lat = data?.latitude
                val lon = data?.longitude

                if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                    workplaceLatLng = LatLng.from(lat, lon)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingWorkplace = false
        }
    }

    // ✅ 서버에서 CCTV 리스트를 가져와 구역 필터 동적 생성
    LaunchedEffect(Unit) {
        val userId = UserSession.userId
        if (!userId.isNullOrEmpty()) {
            RetrofitClient.instance.getCCTVList(null, null, userId).enqueue(object : Callback<GetCCTVListResponse> {
                override fun onResponse(call: Call<GetCCTVListResponse>, response: Response<GetCCTVListResponse>) {
                    if (response.isSuccessful) {
                        val list = response.body()?.cctvList ?: emptyList()
                        // install_area 예: "A구역 1열" -> "A구역" 추출
                        val dynamicAreas = list.mapNotNull { it.location }
                            .map { it.split(" ").first() }
                            .distinct()
                            .sorted()
                        areas = listOf("전체") + dynamicAreas
                    }
                }
                override fun onFailure(call: Call<GetCCTVListResponse>, t: Throwable) {}
            })
        }
    }

    // ✅ [수정] 서버에서 작업자 위치(Location) 가져와서 리스트 및 핀 구성 (실시간 갱신)
    LaunchedEffect(Unit) {
        val userId = UserSession.userId
        if (!userId.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val response = RetrofitClient.instance.getLocation(userId).execute()
                        if (response.isSuccessful) {
                            val locations = response.body()?.locations ?: emptyList()

                            withContext(Dispatchers.Main) {
                                // 1. 리스트 데이터(rows) 구성
                                rows = locations.map { loc ->
                                    // ✅ DB status 값에 따른 상태 텍스트 및 색상 매핑
                                    val (statusText, statusColor) = when (loc.status) {
                                        "고열","위험", "추락", "FALL", "DANGER" -> "위험" to Color(0xFFFF5252) // Red
                                        "경고", "FEVER", "WARNING" -> "경고" to Color(0xFFFF9800) // Orange
                                        else -> "정상" to Color(0xFF10B981) // Green
                                    }

                                    WorkerRow(
                                        id = loc.userId,
                                        role = if (loc.role.equals("manager", true)) "관리자" else "근로자",
                                        name = loc.name,
                                        location = loc.currentZone ?: "위치 정보 없음",
                                        statusText = statusText,
                                        statusColor = statusColor
                                    )
                                }

                                // 2. 지도 핀(pins) 구성
                                pins = locations.mapNotNull { loc ->
                                    val lat = loc.latitude
                                    val lng = loc.longitude
                                    
                                    // ✅ DB status 값에 따른 핀 아이콘 상태 매핑
                                    val pinStatus = when (loc.status) {
                                        "위험", "추락", "FALL", "DANGER" -> WorkerStatus.FALL
                                        "경고", "고열", "FEVER", "WARNING" -> WorkerStatus.FEVER
                                        else -> WorkerStatus.NORMAL
                                    }

                                    if (lat != null && lng != null) {
                                        WorkerPin(
                                            id = loc.userId,
                                            area = loc.currentZone ?: "미지정",
                                            lat = lat,
                                            lng = lng,
                                            status = pinStatus
                                        )
                                    } else null
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(3000) // 3초마다 갱신
                }
            }
        }
    }

    /* -------------------- UI -------------------- */

    Box(modifier = modifier.fillMaxSize()) {

        // ✅ 카카오맵 + 핀 + 핀 클릭
        // ✅ 카카오맵 + 핀 + 핀 클릭
        if (isLoadingWorkplace) {
            // 로딩 중에는 지도 대신 빈 배경(깜빡임 방지)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE5E7EB))
            )
        } else {
            val start = workplaceLatLng ?: LatLng.from(37.456, 126.705) // 등록 없으면 fallback

            KakaoMapView(
                lat = start.latitude,
                lon = start.longitude,
                modifier = Modifier.fillMaxSize(),

                // ✅ 작업자 선택이 있으면 그쪽으로 이동, 없으면 현장 등록 위치로 시작
                targetLatLng = targetLatLng ?: workplaceLatLng,

                pins = kakaoPins,
                selectedId = selectedWorkerId,
                onPinClick = { clickedId ->
                    selectedWorkerId = if (selectedWorkerId == clickedId) null else clickedId
                }
            )
        }


        // ✅ 선택된 상태에서 지도 빈 곳 탭하면 선택 해제
        if (selectedWorkerId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { selectedWorkerId = null })
                    }
            )
        }

        // 상단 그라데이션
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 상단 타이틀 + 구역 칩
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .zIndex(10f)
        ) {
            Text(
                text = "위치정보",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = ClipartKorea,
                color = Color.White,
                modifier = Modifier.padding(start = 10.dp)
            )

            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(areas) { area ->
                    val isSelected = selectedArea == area
                    val chipWidth = if (area == "전체") 48.dp else 59.dp

                    val chipBg = when {
                        dark -> Color.Black
                        else -> if (isSelected) Color(0xFFFF7A00) else Color.White
                    }

                    val chipBorder = when {
                        dark -> Color(0xFF2A2F37)
                        isSelected -> Color(0xFFFF7A00)
                        else -> Color(0xFFE5E7EB)
                    }

                    val chipTextColor = when {
                        dark && isSelected -> Color.White
                        dark -> Color(0xFF58616A)
                        isSelected -> Color.White
                        else -> Color(0xFF6B7280)
                    }

                    Box(
                        modifier = Modifier
                            .width(chipWidth)
                            .widthIn(min = 56.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(999.dp))
                            .clickable {
                                selectedArea = area
                                selectedWorkerId = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = area,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = Pretendard,
                            color = chipTextColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 바텀시트
        Surface(
            color = if (dark) Color(0xFF000000) else sheetBg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                SheetHandle(
                    handleColor = if (dark) Color(0xFF2A3646) else Color(0xFFE5E7EB),
                    modifier = Modifier.draggable(state = dragState, orientation = Orientation.Vertical)
                )

                SheetSummary(
                    count = filteredRows.size,
                    hasSelection = selectedWorkerId != null,
                    onBackToAll = { selectedWorkerId = null },
                    textPrimary = textPrimary,
                    pillBg = if (dark) Color(0xFF131416) else c.surface,
                    pillBorder = pillBorder
                )

                TableHeader(
                    textSecondary = textPrimary,
                    divider = dividerStrong,
                    dark = dark
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + 12.dp)
                ) {
                    items(displayRows, key = { it.id }) { row ->
                        TableRowItem(
                            row = row,
                            isSelected = (row.id == selectedWorkerId),
                            hasSelection = (selectedWorkerId != null),
                            onRowClick = {
                                selectedWorkerId = if (selectedWorkerId == row.id) null else row.id
                            },
                            onCamClick = { row ->
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (granted) {
                                    camTargetRow = row
                                    showCamDialog = true
                                } else {
                                    pendingCamRow = row
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            ,
                            textPrimary = textPrimary,
                            iconTint = iconTint,
                            divider = dividerLight
                        )
                    }
                }
            }
        }

        if (showCamDialog && camTargetRow != null) {
            CamDialog(
                title = "안전모 CAM",
                onDismiss = { showCamDialog = false },
                workerId = camTargetRow!!.id,   // ✅ 추가
                isDark = dark
            )

        }
    }
}

/* -------------------- bottom sheet parts -------------------- */

@Composable
private fun SheetHandle(
    handleColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(handleColor)
        )
    }
}

@Composable
private fun SheetSummary(
    count: Int,
    hasSelection: Boolean,
    onBackToAll: () -> Unit,
    textPrimary: Color,
    pillBg: Color,
    pillBorder: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasSelection) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onBackToAll() }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.arrow_back_dark),
                    contentDescription = "뒤로가기",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(14.dp)
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    text = "선택한 작업자 현황",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Pretendard,
                    maxLines = 1
                )
            }
        } else {
            Row(
                modifier = Modifier.offset(x = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "작업자 현황",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Pretendard,
                    maxLines = 1
                )

                Spacer(Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(pillBg)
                        .border(1.dp, pillBorder, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "총 작업자 ${count}명",
                        color = Color(0xFFFF7A00),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = Pretendard,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TableHeader(
    textSecondary: Color,
    divider: Color,
    dark: Boolean
) {
    val headerBg = if (dark) {
        Color(0xFFFB923C).copy(alpha = 0.36f)
    } else {
        Color(0xFFFFF4EC)
    }

    val headerText = if (dark) Color.White else textSecondary

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderCell("구분", 0.18f, headerText)
                HeaderCell("이름", 0.22f, headerText)
                HeaderCell("현재위치", 0.30f, headerText)
                HeaderCell("상태", 0.18f, headerText)
                HeaderCell("캠", 0.12f, headerText)
            }
        }

        Divider(
            color = if (dark) Color(0xFF1F2937) else divider,
            thickness = 1.dp
        )
    }
}

@Composable
private fun TableRowItem(
    row: WorkerRow,
    isSelected: Boolean,
    hasSelection: Boolean,
    onRowClick: () -> Unit,
    onCamClick: (WorkerRow) -> Unit,
    textPrimary: Color,
    iconTint: Color,
    divider: Color
) {
    val rowAlpha = when {
        !hasSelection -> 1f
        isSelected -> 1f
        else -> 0.35f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = rowAlpha)
            .clickable { onRowClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyCell(row.role, 0.18f, textPrimary)
        BodyCell(row.name, 0.22f, textPrimary)
        BodyCell(row.location, 0.30f, textPrimary)
        BodyCell(row.statusText, 0.18f, row.statusColor)

        Box(
            modifier = Modifier.weight(0.12f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.camimg),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onCamClick(row) }
            )
        }
    }

    Divider(color = divider, thickness = 1.dp)
}

@Composable
private fun RowScope.HeaderCell(text: String, w: Float, color: Color) {
    Box(
        modifier = Modifier
            .weight(w)
            .heightIn(min = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Pretendard,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RowScope.BodyCell(text: String, w: Float, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = Pretendard,
        modifier = Modifier.weight(w),
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun CamDialog(
    title: String,
    onDismiss: () -> Unit,
    workerId: String,
    isDark: Boolean
) {
    val c = LocalSafeColors.current
    val context = LocalContext.current

    val viewModel: CamPttViewModel = viewModel(key = "ptt_$workerId")
    val managerId = UserSession.userId ?: ""

    val bg = if (isDark) Color(0xFF1E2124) else Color.White
    val text = c.text
    val closeTint = c.sub
    val actionColor = if (isDark) Color.Black else Color.White

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bg,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .width(327.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(27.dp)
                        .padding(start = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = text,
                        modifier = Modifier
                            .widthIn(min = 93.dp)
                            .wrapContentWidth()
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = closeTint
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Image(
                    painter = painterResource(id = R.drawable.cam_sample),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 295.dp, height = 313.09.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) c.surface else Color(0xFFF3F4F6))
                )

                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .size(width = 295.dp, height = 52.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    viewModel.startRecording(context)
                                    tryAwaitRelease()
                                    if (viewModel.isRecording) {
                                        viewModel.stopAndUpload(managerId, workerId)
                                    }
                                }
                            )
                        }

                ) {
                    Surface(
                        color = if (viewModel.isRecording) Color.Red else Color(0xFFFF7A00),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.mic),
                                contentDescription = null,
                                tint = actionColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (viewModel.isRecording) "녹음 중..." else "눌러서 말하기",
                                color = actionColor,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = Pretendard
                            )

                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = viewModel.statusText,
                    color = c.sub,
                    fontSize = 13.sp,
                    fontFamily = Pretendard
                )
            }
        }
    }
}
