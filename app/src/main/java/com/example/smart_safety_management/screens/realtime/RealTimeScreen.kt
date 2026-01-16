package com.example.smart_safety_management.screens.realtime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.R
import com.example.smart_safety_management.ui.theme.DarkSafeColors
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RealTimeScreen(
    modifier: Modifier = Modifier,
    onCardClick: (LiveCardItem) -> Unit
) {
    val c = LocalSafeColors.current
    val isDark = (c == DarkSafeColors)

    val areaOptions = listOf("공간별", "도로", "내부")
    var selectedArea by remember { mutableStateOf(areaOptions[0]) }

    var isGrid by remember { mutableStateOf(false) }

    val cards = remember {
        listOf(
            LiveCardItem(
                camId = "CAM 00",
                place = "도로",
                tags = listOf("충돌", "안전모 미착용"),
                thumbRes = R.drawable.thumb_site,
                location = "A구역 외부 도로",
                overviewThumb = R.drawable.thumb_road,
                siteThumb = R.drawable.thumb_site,
                captureThumbs = listOf(
                    R.drawable.thumb_worker,
                    R.drawable.thumb_workers,
                    R.drawable.thumb_worker
                ),
                isLive = true
            ),
            LiveCardItem(
                camId = "CAM 01",
                place = "내부",
                tags = listOf("화재", "통로", "운반", "협착사고"),
                thumbRes = R.drawable.thumb_workers,
                location = "A구역 1열 내부",
                overviewThumb = R.drawable.frame_a,
                siteThumb = R.drawable.frame_b,
                captureThumbs = listOf(
                    R.drawable.rectangle_a,
                    R.drawable.rectangle_b,
                    R.drawable.rectangle_c
                ),
                isLive = true
            ),
            LiveCardItem(
                camId = "CAM 02",
                place = "도로",
                tags = listOf("안전모 미착용", "통로", "운반"),
                thumbRes = R.drawable.thumb_road,
                location = "B구역 외부 도로",
                overviewThumb = R.drawable.thumb_road,
                siteThumb = R.drawable.thumb_site,
                captureThumbs = listOf(
                    R.drawable.thumb_site,
                    R.drawable.thumb_worker,
                    R.drawable.thumb_workers
                ),
                isLive = true
            ),
            LiveCardItem(
                camId = "CAM 03",
                place = "도로",
                tags = listOf("충돌", "안전모 미착용"),
                thumbRes = R.drawable.thumb_worker,
                location = "C구역 외부 도로",
                overviewThumb = R.drawable.thumb_road,
                siteThumb = R.drawable.thumb_site,
                captureThumbs = listOf(
                    R.drawable.thumb_worker,
                    R.drawable.thumb_workers,
                    R.drawable.thumb_site
                ),
                isLive = true
            )
        )
    }

    val cameraLabelByCamId = mapOf(
        "전체" to "전체",
        "CAM 00" to "00) 도로 - 충돌, 안전모 미착용",
        "CAM 01" to "01) 내부 - 화재, 통로, 운반, 협착사고",
        "CAM 02" to "02) 도로 - 중물, 안전모 미착용",
        "CAM 03" to "03) 도로 - 중물, 안전모 미착용"
    )

    val cameraOptionsWithAll = listOf("전체") + listOf(
        cameraLabelByCamId["CAM 00"]!!,
        cameraLabelByCamId["CAM 01"]!!,
        cameraLabelByCamId["CAM 02"]!!,
        cameraLabelByCamId["CAM 03"]!!
    )

    var selectedCameraLabel by remember { mutableStateOf("전체") }

    val selectedCamId = remember(selectedCameraLabel) {
        if (selectedCameraLabel == "전체") null
        else cameraLabelByCamId.entries.firstOrNull { it.value == selectedCameraLabel }?.key
    }

    val filteredCards = remember(selectedArea, selectedCamId, cards) {
        cards
            .let { list -> if (selectedArea == "공간별") list else list.filter { it.place == selectedArea } }
            .let { list -> if (selectedCamId == null) list else list.filter { it.camId == selectedCamId } }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White) // ✅ 전체 배경 흰색
    ) {
        // 드롭다운 2개
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 25.2.dp)
                .padding(top = 12.6.dp, bottom = 12.6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SimpleDropdown(
                value = selectedArea,
                options = areaOptions,
                onSelect = { selectedArea = it },
                modifier = Modifier
                    .width(108.dp)
                    .height(50.dp),
                menuWidth = 108.dp,
                menuHeight = 140.dp
            )

            SimpleDropdown(
                value = selectedCameraLabel,
                options = cameraOptionsWithAll,
                onSelect = { selectedCameraLabel = it },
                modifier = Modifier
                    .width(218.dp)
                    .height(50.dp),
                menuWidth = 309.dp,
                menuHeight = 204.dp
            )
        }

        Divider(color = c.divider, thickness = 1.dp, modifier = Modifier.fillMaxWidth())

        // 배열 + 아이콘
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 25.2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "배열",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = c.sub
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { isGrid = !isGrid }) {
                Icon(
                    painter = painterResource(id = if (isGrid) R.drawable.frame else R.drawable.vector),
                    contentDescription = null,
                    tint = c.sub,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 리스트/그리드 영역
        Box(modifier = Modifier.fillMaxSize()) {
            val sidePadding = 25.2.dp
            val itemSpacing = 16.8.dp

            if (isGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    contentPadding = PaddingValues(
                        start = sidePadding,
                        end = sidePadding,
                        bottom = 84.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredCards) { item ->
                        LiveGridCard(item = item, onClick = { onCardClick(item) })
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    contentPadding = PaddingValues(
                        start = sidePadding,
                        end = sidePadding,
                        bottom = 84.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredCards) { item ->
                        LiveListCard(item = item, onClick = { onCardClick(item) })
                    }
                }
            }
        }
    }
}

/* -------------------- Dropdown -------------------- */

@Composable
fun SimpleDropdown(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    menuWidth: Dp? = null,
    menuHeight: Dp? = null
) {
    val c = LocalSafeColors.current

    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val finalMenuWidth = menuWidth ?: with(density) { anchorWidthPx.toDp() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White) // ✅ 라이트 흰색
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .clickable { expanded = true }
            .onSizeChanged { anchorWidthPx = it.width }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = c.text
            )
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(id = R.drawable.dropdown_arrow),
                contentDescription = null,
                tint = c.sub,
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(finalMenuWidth)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White), // ✅ 라이트 흰색
            offset = DpOffset(0.dp, 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .then(if (menuHeight != null) Modifier.height(menuHeight) else Modifier)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { opt ->
                    val selected = opt == value

                    DropdownMenuItem(
                        text = {
                            Text(
                                opt,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = c.text
                            )
                        },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected && c == DarkSafeColors)
                                    Color(0xFFFF7A00).copy(alpha = 0.18f)
                                else
                                    Color.Transparent
                            )
                    )
                }
            }
        }
    }
}

/* -------------------- Bottom Bar -------------------- */

@Composable
fun RealTimeBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalSafeColors.current

    val selectedColor = Color(0xFFFF7A00)
    val unselectedColor = c.sub

    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        color = c.bottomBar,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomIconItem(
                painter = painterResource(R.drawable.home),
                label = "안전점검",
                selected = selected == 0,
                enabled = false,
                selectedColor = selectedColor,
                unselectedColor = unselectedColor
            ) { onSelect(0) }

            BottomIconItem(
                painter = painterResource(R.drawable.ai),
                label = "AI감지",
                selected = selected == 1,
                enabled = false,
                selectedColor = selectedColor,
                unselectedColor = unselectedColor
            ) { onSelect(1) }

            BottomIconItem(
                painter = painterResource(R.drawable.live),
                label = "실시간상황",
                selected = selected == 2,
                enabled = true,
                selectedColor = selectedColor,
                unselectedColor = unselectedColor
            ) { onSelect(2) }

            BottomIconItem(
                painter = painterResource(R.drawable.history),
                label = "이력",
                selected = selected == 3,
                enabled = false,
                selectedColor = selectedColor,
                unselectedColor = unselectedColor
            ) { onSelect(3) }

            BottomIconItem(
                painter = painterResource(R.drawable.location),
                label = "위치정보",
                selected = selected == 4,
                enabled = true,
                selectedColor = selectedColor,
                unselectedColor = unselectedColor
            ) { onSelect(4) }
        }
    }
}

@Composable
private fun RowScope.BottomIconItem(
    painter: Painter,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit
) {
    val color = if (selected) selectedColor else unselectedColor

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/* -------------------- Cards -------------------- */

@Composable
fun LiveListCard(item: LiveCardItem, onClick: () -> Unit) {
    val c = LocalSafeColors.current
    val isDark = (c == DarkSafeColors)
    val isRisk = item.hasRisk()

    // ✅ 라이트: 카드 배경 완전 흰색
    val cardBg = if (isDark) c.surface else Color.White

    // ✅ 라이트: 정보영역(회색) 제거 -> 흰색
    val infoBg = if (isDark) {
        if (isRisk) Color(0xFF1F252C) else Color.Transparent
    } else {
        Color.White
    }

    val infoText = if (isRisk) {
        if (isDark) Color(0xFF9AA1AA) else Color(0xFF6B7280)
    } else {
        c.text
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, c.border)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            ) {
                Image(
                    painter = painterResource(id = item.thumbRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.isLive) {
                    LiveBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(infoBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CamPill(item.camId, isRisk = isRisk)
                    PlaceText(item.place, color = infoText)
                }

                TagsRowSingleLine(item.tags, isRisk = isRisk)
            }
        }
    }
}

@Composable
fun LiveGridCard(item: LiveCardItem, onClick: () -> Unit) {
    val c = LocalSafeColors.current
    val isDark = (c == DarkSafeColors)
    val isRisk = item.hasRisk()

    val cardBg = if (isDark) c.surface else Color.White

    val infoBg = if (isDark) {
        if (isRisk) Color(0xFF1F252C) else Color.Transparent
    } else {
        Color.White
    }

    val infoText = if (isRisk) {
        if (isDark) Color(0xFF9AA1AA) else Color(0xFF6B7280)
    } else {
        c.text
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, c.border)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Image(
                    painter = painterResource(id = item.thumbRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.isLive) {
                    LiveBadge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(infoBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CamPill(item.camId, isRisk = isRisk)
                    PlaceText(item.place, color = infoText)
                }

                TagsRow(item.tags, isRisk = isRisk)
            }
        }
    }
}

/* -------------------- Badge / Pills -------------------- */

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFE85B5B))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "LIVE",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CamPill(text: String, isRisk: Boolean = false) {
    val c = LocalSafeColors.current
    val isDark = (c == DarkSafeColors)

    val camBg = when {
        isDark -> Color(0xFF3A4250)
        else -> Color(0xFF4B5563)
    }.let { base ->
        if (isRisk) base.copy(alpha = 0.85f) else base
    }

    val camText = if (isDark) c.chipText else Color.White

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(camBg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = camText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PlaceText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsRow(tags: List<String>, isRisk: Boolean = false) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { TagPill(it, isRisk = isRisk) }
    }
}

@Composable
fun TagPill(text: String, isRisk: Boolean = false) {
    val c = LocalSafeColors.current
    val isDark = (c == DarkSafeColors)

    // ✅ 위험요소 칩만 "연한 회색" 처리 (라이트)
    val bg = when {
        isDark && isRisk -> Color(0xFF2A3038)
        isDark && !isRisk -> c.chip
        !isDark && isRisk -> Color(0xFFF3F4F6)   // ✅ 연한 회색 (원하면 더 연하게/진하게 조정)
        else -> Color.White
    }

    // ✅ 라이트에서 위험요소면 글씨 조금 어둡게
    val fg = when {
        isRisk && isDark -> Color(0xFF9AA1AA)
        isRisk && !isDark -> Color(0xFF6B7280)
        else -> c.chipText
    }

    val borderColor = if (!isDark) Color(0xFFE5E7EB) else c.border

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TagsRowSingleLine(tags: List<String>, isRisk: Boolean = false) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 2.dp)
    ) {
        items(tags, key = { it }) { tag ->
            TagPillCompact(tag, isRisk = isRisk)
        }
    }
}

@Composable
fun TagPillCompact(text: String, isRisk: Boolean = false) {
    val c = LocalSafeColors.current
    val isDark = (c == DarkSafeColors)

    val bg = when {
        isDark && isRisk -> Color(0xFF2A3038)
        isDark && !isRisk -> c.chip
        !isDark && isRisk -> Color(0xFFF3F4F6)   // ✅ 연한 회색
        else -> Color.White
    }

    val fg = when {
        isRisk && isDark -> Color(0xFF9AA1AA)
        isRisk && !isDark -> Color(0xFF6B7280)
        else -> c.chipText
    }

    val borderColor = if (!isDark) Color(0xFFE5E7EB) else c.border

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}


/* -------------------- risk detect -------------------- */

private fun LiveCardItem.hasRisk(): Boolean {
    val riskKeywords = listOf("충돌", "안전모", "화재", "협착", "쓰러짐")
    return tags.any { tag -> riskKeywords.any { key -> tag.contains(key) } }
}

@Preview(showBackground = true)
@Composable
fun PreviewRealTimeScreen() {
    Smart_Safety_ManagementTheme {
        RealTimeScreen(onCardClick = {})
    }
}
