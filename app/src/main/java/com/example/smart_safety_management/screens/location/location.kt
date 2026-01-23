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
import com.example.smart_safety_management.R
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import androidx.compose.ui.zIndex
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.ClipartKorea


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

/* -------------------- screen -------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    modifier: Modifier = Modifier,
    bottomBarHeight: Dp = 68.dp,
    isDark: Boolean,
    _onTabSelect: (Int) -> Unit = {} // 미사용 경고 제거
) {
    val c = LocalSafeColors.current
    val dark = isDark // ✅ 외부에서 넘겨준 다크 여부를 기준으로 통일

    // ✅ 다크일 때 시트 "완전 검정"
    val sheetBg = if (dark) Color(0xFF000000) else c.surface

    val textPrimary = c.text
    val textSecondary = c.sub
    val dividerStrong = c.divider
    val dividerLight = c.divider.copy(alpha = 0.7f)
    val pillBg = if (dark) {
        Color(0xFF131416)
    } else {
        c.surface
    }

    val pillBorder = c.border
    val iconTint = c.text

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

    val basePinW = 42.dp
    val basePinH = 58.dp
    val selectedScale = 1.3f
    val dimAlpha = 0.45f

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

    Box(modifier = modifier.fillMaxSize()) {

        Image(
            painter = painterResource(R.drawable.map_b),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedWorkerId) { detectTapGestures { selectedWorkerId = null } }
        )

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
                    painter = painterResource(iconFor(p.status, dark)),
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

            // ✅ 다크모드: 칩 배경은 항상 검정 / 선택은 "텍스트만 흰색"
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(areas) { area ->
                    val isSelected = selectedArea == area
                    val chipWidth = if (area == "전체") 48.dp else 59.dp

                    val chipBg = when {
                        dark -> Color.Black // ✅ 다크는 항상 검정
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
                    pillBg = pillBg,
                    pillBorder = pillBorder
                )

                TableHeader(
                    textSecondary = textSecondary,
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
                    painter = painterResource(id = R.drawable.arrow_back_dark), // ✅ 변경
                    contentDescription = "뒤로가기",
                    tint = Color.Unspecified, // ✅ 원본 아이콘 색 그대로 사용
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


@Composable
private fun TableHeader(
    textSecondary: Color,
    divider: Color,
    dark: Boolean
) {
    // ✅ 요청: #FB923C · 36%
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
            .padding(horizontal = 16.dp, vertical = 8.dp), // ✅ 10dp -> 8dp
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
    Box(
        modifier = Modifier
            .weight(w)
            .heightIn(min = 24.dp), // ✅ 헤더 텍스트 높이 안정화 (원하면 28.dp)
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

    // ✅ 카드(다이얼로그) 배경: 피그마 #1E2124 (다크모드일 때)
    val bg = if (isDark) Color(0xFF1E2124) else Color.White

    val text = c.text
    val closeTint = c.sub

    // ✅ 다크모드일 때 버튼 텍스트/아이콘을 검정으로
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
                        .height(27.dp),
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

