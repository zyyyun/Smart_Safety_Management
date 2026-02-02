package com.example.smart_safety_management.screens.location

import android.graphics.drawable.Drawable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smart_safety_management.R
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard
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

private fun ContextCompatDrawable(context: android.content.Context, @DrawableRes resId: Int): Drawable? {
    return ContextCompat.getDrawable(context, resId)
}

/* -------------------- map helpers -------------------- */

@Composable
private fun rememberOsmdroidMapView(
    center: GeoPoint,
    zoom: Double = 18.0
): MapView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        // 타일 다운로드 정책상 UA 설정 권장
        Configuration.getInstance().userAgentValue = context.packageName

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(zoom)
            controller.setCenter(center)
        }
    }

    // MapView lifecycle 연결 (메모리/타일 스레드 안정화)
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
        }
    }

    return mapView
}

private fun MapView.replaceWorkerMarkers(
    pins: List<WorkerPin>,
    selectedId: String?,
    context: Context,
    isDark: Boolean,
    onClick: (workerId: String) -> Unit
) {
    overlays.removeAll { it is Marker }

    val dimAlpha = 110  // 👈 더 연하게: 70~130 추천
    val fullAlpha = 255

    pins.forEach { p ->
        val hasSelection = selectedId != null
        val isSelected = selectedId == p.id

        val alpha = when {
            !hasSelection -> fullAlpha
            isSelected -> fullAlpha
            else -> dimAlpha
        }

        val icon = drawableWithAlpha(context, iconFor(p.status, isDark), alpha)

        val m = Marker(this).apply {
            position = GeoPoint(p.lat, p.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.icon = icon
            setOnMarkerClickListener { _, _ ->
                onClick(p.id)
                true
            }
        }
        overlays.add(m)
    }

    invalidate()
}


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
    val pillBg = if (dark) Color(0xFF131416) else c.surface
    val pillBorder = c.border

    val tableTextColor = Color(0xFF33363D)
    val iconTint = tableTextColor

    var selectedWorkerId by remember { mutableStateOf<String?>(null) }
    val areas = listOf("전체", "A구역", "B구역", "C구역", "D구역", "E구역")
    var selectedArea by remember { mutableStateOf("전체") }
    var showCamDialog by remember { mutableStateOf(false) }
    var camTargetRow by remember { mutableStateOf<WorkerRow?>(null) }

    val rows = remember {
        listOf(
            WorkerRow("w1", "근로자", "손흥민", "A구역 1열", "정상", Color(0xFF10B981)),
            WorkerRow("w2", "근로자", "이강인", "B구역 2열", "고열", Color(0xFFEF4444)),
            WorkerRow("w3", "근로자", "김민재", "C구역 3열", "정상", Color(0xFF10B981)),
            WorkerRow("w4", "근로자", "황희찬", "D구역 4열", "쓰러짐", Color(0xFFF97316)),
        )
    }

    // ✅ (중요) 핀을 x/y가 아니라 lat/lng로!
    // 아래 좌표는 예시야. 너 현장 좌표로 교체하면 됨.
    val pins = remember {
        listOf(
            WorkerPin("w1", "A구역", 37.45650, 126.70510, WorkerStatus.NORMAL),
            WorkerPin("w2", "B구역", 37.45680, 126.70610, WorkerStatus.FEVER),
            WorkerPin("w3", "C구역", 37.45610, 126.70570, WorkerStatus.NORMAL),
            WorkerPin("w4", "D구역", 37.45580, 126.70640, WorkerStatus.FALL),
        )
    }

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

    // ✅ 선택되면 마커도 그 사람만 보이게(“흐리게” 효과 대신 간단/확실하게)
    val displayPins = remember(filteredPins, selectedWorkerId) {
        if (selectedWorkerId == null) filteredPins else filteredPins.filter { it.id == selectedWorkerId }
    }

    val sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

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

    /* -------------------- UI -------------------- */

    val context = LocalContext.current

    // 지도 중심(현장 중심 좌표로 바꿔도 됨)
    val center = remember { GeoPoint(37.456, 126.705) }
    val mapView = rememberOsmdroidMapView(center = center, zoom = 18.0)

    val pinById = remember(pins) {
        pins.associateBy { it.id }
    }

// 작업자 선택 시 해당 위치로 지도 이동
    LaunchedEffect(selectedWorkerId) {
        selectedWorkerId?.let { id ->
            pinById[id]?.let { p ->
                mapView.controller.animateTo(GeoPoint(p.lat, p.lng))
                // 필요하면 줌 고정
                // mapView.controller.setZoom(18.0)
            }
        }
    }
    // ✅ pins/선택/필터가 바뀔 때마다 지도 마커 갱신
    LaunchedEffect(displayPins, dark) {
        mapView.replaceWorkerMarkers(
            pins = filteredPins,
            selectedId = selectedWorkerId,
            context = context,
            isDark = dark,
            onClick = { clickedId ->
                selectedWorkerId = if (selectedWorkerId == clickedId) null else clickedId
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {

        // ✅ 지도 (여기엔 pointerInput 붙이지 말기)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView }
        )

        // ✅ 작업자 선택된 상태면 "빈공간 탭"으로 선택 해제
        if (selectedWorkerId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f) // 지도 위
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { selectedWorkerId = null }
                        )
                    }
            )
        }

        // 상단 어두운 그라데이션
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
            color = sheetBg,
            shape = sheetShape,
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
                            onRowClick = { selectedWorkerId = row.id },
                            onCamClick = {
                                camTargetRow = it
                                showCamDialog = true
                            },
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
                onMicClick = { },
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
    val darkText = LocalSafeColors.current.isDark

    val rowAlpha = when {
        !hasSelection -> 1f
        isSelected -> 1f
        else -> 0.35f
    }

    val bodyTextColor = if (darkText) Color(0xFF9CA3AF) else textPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = rowAlpha)
            .clickable { onRowClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyCell(row.role, 0.18f, bodyTextColor)
        BodyCell(row.name, 0.22f, bodyTextColor)
        BodyCell(row.location, 0.30f, bodyTextColor)
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
    onMicClick: () -> Unit,
    isDark: Boolean
) {
    val c = LocalSafeColors.current

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

                Button(
                    onClick = onMicClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7A00)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(width = 295.dp, height = 52.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.mic),
                        contentDescription = null,
                        tint = actionColor,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(Modifier.width(4.dp))

                    Text(
                        text = "눌러서 말하기",
                        color = actionColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Pretendard
                    )
                }
            }
        }
    }
}

private fun drawableWithAlpha(
    context: Context,
    @DrawableRes resId: Int,
    alpha: Int
): BitmapDrawable? {
    val d = ContextCompat.getDrawable(context, resId) ?: return null

    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.alpha = alpha // 0~255
    }

    // drawable을 비트맵에 그리되 paint 알파 적용
    d.setBounds(0, 0, w, h)
    canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), alpha)
    d.draw(canvas)
    canvas.restore()

    return BitmapDrawable(context.resources, bmp)
}
