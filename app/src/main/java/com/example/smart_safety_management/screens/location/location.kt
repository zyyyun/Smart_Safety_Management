package com.example.safe.screens.location

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.safe.R
import com.example.safe.ui.theme.LocalSafeColors

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
    val x: Float,
    val y: Float,
    val status: WorkerStatus
)

enum class WorkerStatus { NORMAL, FEVER, FALL }

@DrawableRes
private fun iconFor(status: WorkerStatus, isDark: Boolean): Int {
    return if (isDark) {
        when (status) {
            WorkerStatus.NORMAL -> R.drawable.worker_green_dark
            WorkerStatus.FEVER  -> R.drawable.worker_red_dark
            WorkerStatus.FALL   -> R.drawable.worker_orange_dark
        }
    } else {
        when (status) {
            WorkerStatus.NORMAL -> R.drawable.worker_green
            WorkerStatus.FEVER  -> R.drawable.worker_red
            WorkerStatus.FALL   -> R.drawable.worker_orange
        }
    }
}


/* -------------------- screen -------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    bottomBarHeight: Dp = 0.dp,
    isDark: Boolean               // ✅ AppRoot 토글 값
) {
    // (참고용) 시스템 다크값이 필요하면 이름만 다르게
    val systemDark = isSystemInDarkTheme()

    // ✅ SafeTheme(LocalSafeColors)에서 공급한 팔레트 읽기
    val c = LocalSafeColors.current

    // ✅ 화면에서 쓸 다크 여부는 "파라미터 isDark"로 고정
    //    (systemDark로 덮어쓰면 토글이 무시됨)
    val dark = isDark

    // ✅ Location 전용 팔레트
    val sheetBg = if (dark) c.surface.copy(alpha = 0.92f) else c.surface
    val textPrimary = c.text
    val textSecondary = c.sub
    val dividerStrong = c.divider
    val dividerLight = c.divider.copy(alpha = 0.7f)
    val pillBg = if (dark) c.bg.copy(alpha = 0.10f) else c.surface
    val pillBorder = c.border
    val iconTint = c.text

    val chipBg = c.surface
    val chipBorder = c.border
    val chipText = c.text
    val chipSelectedText = Color(0xFFFF7A00)

    val bottomState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomState)

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

    val pins = remember {
        listOf(
            WorkerPin("w1", "A구역", 0.18f, 0.28f, WorkerStatus.NORMAL),
            WorkerPin("w2", "B구역", 0.72f, 0.24f, WorkerStatus.FEVER),
            WorkerPin("w3", "C구역", 0.50f, 0.46f, WorkerStatus.NORMAL),
            WorkerPin("w4", "D구역", 0.82f, 0.44f, WorkerStatus.FALL),
        )
    }

    fun rowArea(row: WorkerRow) = row.location.split(" ").first()

    // ✅ 구역 필터
    val filteredRows = remember(selectedArea, rows) {
        if (selectedArea == "전체") rows else rows.filter { rowArea(it) == selectedArea }
    }

    // ✅ 선택이 있으면 표는 1명만
    val displayRows = remember(filteredRows, selectedWorkerId) {
        if (selectedWorkerId == null) filteredRows
        else filteredRows.filter { it.id == selectedWorkerId }
    }

    // ✅ 지도 핀은 선택해도 “구역필터 기준 전체”는 유지
    val visibleIds = remember(filteredRows) { filteredRows.map { it.id }.toSet() }

    val filteredPins = remember(selectedArea, pins, visibleIds) {
        val byArea = if (selectedArea == "전체") pins else pins.filter { it.area == selectedArea }
        byArea.filter { it.id in visibleIds }
    }

    val basePinW = 34.dp
    val basePinH = 46.dp
    val selectedScale = 1.3f
    val dimAlpha = 0.45f

    val sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val peek = 140.dp

    BottomSheetScaffold(
        modifier = Modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        sheetPeekHeight = peek,
        sheetDragHandle = null, // ✅ 기본 드래그 핸들(지도 위 작대기) 제거
        containerColor = Color.Transparent,
        sheetContainerColor = Color.Transparent,
        sheetShape = sheetShape,
        sheetShadowElevation = 0.dp,
        sheetTonalElevation = 0.dp,
        sheetContent = {
            Surface(color = sheetBg, shape = sheetShape) {
                Column(
                    modifier = Modifier.padding(start = 12.dp)
                ) {

                    // ✅ 흰 박스(패널) 안에 작대기 넣고 싶으면 다시 추가
                    SheetHandle(handleColor = if (isDark) Color(0xFF2A3646) else Color(0xFFE5E7EB))

                    SheetSummary(
                        count = filteredRows.size,
                        hasSelection = selectedWorkerId != null,
                        onBackToAll = { selectedWorkerId = null },
                        textPrimary = textPrimary,
                        pillBg = pillBg,
                        pillBorder = pillBorder
                    )

                    TableHeader(
                        textSecondary = textSecondary,
                        divider = dividerStrong,
                        isDark = isDark,
                        sheetBg = sheetBg
                    )

                    LazyColumn(
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
        }
    ) {
        Box(Modifier.fillMaxSize()) {

            Image(
                painter = painterResource(R.drawable.map_b),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedWorkerId) {
                        detectTapGestures { selectedWorkerId = null }
                    }
            )

            // ✅ 다크모드에서 지도 위 살짝 어둡게(스샷 느낌)
            if (isDark) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = if (isDark) 0.70f else 0.55f),
                                Color.Black.copy(alpha = if (isDark) 0.45f else 0.30f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // ✅ 핀: 선택된 핀만 확대, 나머지는 dim
            BoxWithConstraints(Modifier.fillMaxSize()) {
                filteredPins.forEach { p ->
                    val isSelected = selectedWorkerId == p.id
                    val hasSelection = selectedWorkerId != null

                    val alpha = when {
                        !hasSelection -> 1f
                        isSelected -> 1f
                        else -> dimAlpha
                    }

                    val scale = if (isSelected) selectedScale else 1f

                    val offsetX = maxWidth * p.x - basePinW / 2
                    val offsetY = maxHeight * p.y - basePinH

                    Image(
                        painter = painterResource(iconFor(p.status, isDark))
                        ,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(offsetX, offsetY)
                            .size(basePinW, basePinH)
                            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                            .pointerInput(p.id, selectedWorkerId) {
                                detectTapGestures {
                                    selectedWorkerId = if (isSelected) null else p.id
                                }
                            }
                    )
                }
            }

            // ✅ 상단 칩
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(10f)
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_location),
                    contentDescription = null,
                    modifier = Modifier.height(28.dp)
                )

                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(areas) { area ->
                        FilterChip(
                            modifier = Modifier.height(34.dp),
                            selected = selectedArea == area,
                            onClick = {
                                selectedArea = area
                                selectedWorkerId = null
                            },
                            label = {
                                Text(
                                    area,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedArea == area) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = chipBg,
                                selectedContainerColor = chipBg,
                                labelColor = chipText,
                                selectedLabelColor = chipSelectedText
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selectedArea == area) Color(0xFFFF7A00) else chipBorder
                            ),
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }
            }

            // ✅ CAM 팝업
            if (showCamDialog && camTargetRow != null) {
                CamDialog(
                    title = "안전모 CAM",
                    onDismiss = { showCamDialog = false },
                    onMicClick = { },
                    isDark = isDark
                )
            }
        }
    }
}

/* -------------------- bottom sheet parts -------------------- */

@Composable
private fun SheetHandle(handleColor: Color) {
    Box(
        modifier = Modifier
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
                    painter = painterResource(id = R.drawable.back),
                    contentDescription = "뒤로",
                    tint = textPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "선택한 작업자 현황",
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = "작업자 현황",
                color = textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(Modifier.width(10.dp))

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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/* -------------------- table -------------------- */

@Composable
private fun TableHeader(
    textSecondary: Color,
    divider: Color,
    isDark: Boolean,
    sheetBg: Color,           // ✅ 추가
) {
    val headerBg = if (isDark) {
        Color(0xFFFF7A00).copy(alpha = 0.20f)
    } else {
        Color(0xFFFFF4EC)
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ✅ 1) 먼저 시트 배경을 깔고 (지도 비침 차단)
        // ✅ 2) 그 위에 주황(헤더Bg)을 올림
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(sheetBg)
                .background(headerBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderCell("구분", 0.18f, textSecondary)
                HeaderCell("이름", 0.22f, textSecondary)
                HeaderCell("현재위치", 0.30f, textSecondary)
                HeaderCell("상태", 0.18f, textSecondary)
                HeaderCell("캠", 0.12f, textSecondary)
            }
        }

        Divider(color = divider, thickness = 1.dp)
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
            Icon(
                painter = painterResource(row.camIcon),
                contentDescription = null,
                tint = iconTint,
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
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(w),
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun RowScope.BodyCell(text: String, w: Float, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.weight(w),
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

/* -------------------- CAM dialog -------------------- */

@Composable
private fun CamDialog(
    title: String,
    onDismiss: () -> Unit,
    onMicClick: () -> Unit,
    isDark: Boolean
) {
    // ✅ LocalSafeColors 기반으로 통일
    val c = LocalSafeColors.current

    val bg = if (isDark) c.bg else Color.White
    val text = c.text
    val closeTint = c.sub

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
                        .height(27.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
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

                Spacer(Modifier.height(8.dp))

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
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "눌러서 말하기",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
