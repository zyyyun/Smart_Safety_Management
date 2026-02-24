package com.example.smart_safety_management.screens.realtime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.R
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.ui.theme.ClipartKorea
import androidx.compose.ui.platform.LocalConfiguration
import com.example.smart_safety_management.ui.theme.Pretendard
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
/* -------------------- Popup Position Provider -------------------- */
// ✅ 앵커(버튼) 바로 아래에 뜨도록 위치 계산
private class AnchorBelowPositionProvider(
    private val gap: Int,
    private val alignRightToAnchor: Boolean = false
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {

        var x = anchorBounds.left
        if (alignRightToAnchor) {
            x = anchorBounds.right - popupContentSize.width
        }

        var y = anchorBounds.bottom + gap

        // 화면 밖으로 나가면 보정
        x = x.coerceIn(0, windowSize.width - popupContentSize.width)
        y = y.coerceIn(0, windowSize.height - popupContentSize.height)

        return IntOffset(x, y)
    }
}

internal fun normalizeCamId(camId: String): String {
    val m = Regex("""CAM\s*(\d+)""").find(camId.trim()) ?: return camId.trim()
    val num = m.groupValues[1].toIntOrNull() ?: return camId.trim()
    return "CAM " + num.toString().padStart(2, '0') // CAM 1 -> CAM 01
}

internal fun sampleLiveCards(): List<LiveCardItem> = listOf(
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
            R.drawable.thumb_site,
            R.drawable.thumb_worker,
            R.drawable.thumb_workers
        ),
        isLive = true
    )
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RealTimeScreen(
    cards: List<LiveCardItem> = emptyList(),
    modifier: Modifier = Modifier,
    onCardClick: (LiveCardItem) -> Unit,
    onMapClick: () -> Unit
) {
    val c = LocalSafeColors.current
    val isDark = c.isDark

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) c.bg else Color.White)
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "실시간상황",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = ClipartKorea,
                        color = c.text,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                actions = {
                    IconButton(
                        onClick = onMapClick,
                        modifier = Modifier.padding(end = 20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.map),
                            contentDescription = "지도",
                            tint = c.sub
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color(0xFF000000) else Color.White,
                    titleContentColor = c.text,
                    actionIconContentColor = c.sub
                )
            )

            RealTimeContent(
                cards = cards,
                modifier = Modifier.fillMaxSize(),
                onCardClick = onCardClick
            )
        }
    }
}

/* -------------------- RealTimeScreen 본문 -------------------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RealTimeContent(
    cards: List<LiveCardItem>,
    modifier: Modifier = Modifier,
    onCardClick: (LiveCardItem) -> Unit
) {
    val c = LocalSafeColors.current
    val isDark = c.isDark
    val screenBg = if (isDark) c.bg else Color.White

    val areaOptions = remember(cards) {
        listOf("공간별") + cards.map { it.place }.distinct().sorted()
    }
    var selectedArea by remember { mutableStateOf("공간별") }

    var isGrid by remember { mutableStateOf(false) }
    var openDropdownId by remember { mutableStateOf<String?>(null) }

    val cameraOptions = remember(cards) {
        listOf("전체") + cards.map { card ->
            val events = if (card.tags.isNotEmpty()) card.tags.joinToString(", ") else "이벤트 없음"
            "${card.camId}) ${card.place} - $events"
        }
    }
    var selectedCameraLabel by remember { mutableStateOf("전체") }

    val selectedCamId = remember(selectedCameraLabel) {
        if (selectedCameraLabel == "전체") null
        else selectedCameraLabel.split(")").firstOrNull()?.trim()
    }

    val filteredCards = remember(selectedArea, selectedCamId, cards) {
        cards
            .let { list -> if (selectedArea == "공간별") list else list.filter { it.place == selectedArea } }
            .let { list -> if (selectedCamId == null) list else list.filter { it.camId == selectedCamId } }
    }

    val screenW = LocalConfiguration.current.screenWidthDp.dp

    val sidePadding = 25.2.dp
    val itemSpacing = 16.8.dp

    // ✅ 헤더(배열) ↔ 첫 카드 간격: 여기만 조절
    val headerBottomGap = 1.dp

    val rowW = screenW - (sidePadding * 2)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        if (openDropdownId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { openDropdownId = null }
            )
        }

        if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    end = sidePadding,
                    bottom = 12.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(2) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = headerBottomGap)
                    ) {
                        RealTimeHeader(
                            sidePadding = sidePadding,
                            rowW = rowW,
                            selectedArea = selectedArea,
                            areaOptions = areaOptions,
                            onSelectArea = { selectedArea = it },
                            selectedCameraLabel = selectedCameraLabel,
                            cameraOptions = cameraOptions,
                            onSelectCamera = { selectedCameraLabel = it },
                            openDropdownId = openDropdownId,
                            onOpenDropdownChange = { open -> openDropdownId = open },
                            isGrid = isGrid,
                            onToggleGrid = { isGrid = !isGrid }
                        )
                    }
                }

                items(filteredCards, key = { it.camId }) { item ->
                    LiveGridCard(item = item, onClick = { onCardClick(item) })
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.Top,
                contentPadding = PaddingValues(
                    start = sidePadding,
                    end = sidePadding,
                    bottom = 12.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = headerBottomGap)
                    ) {
                        RealTimeHeader(
                            sidePadding = sidePadding,
                            rowW = rowW,
                            selectedArea = selectedArea,
                            areaOptions = areaOptions,
                            onSelectArea = { selectedArea = it },
                            selectedCameraLabel = selectedCameraLabel,
                            cameraOptions = cameraOptions,
                            onSelectCamera = { selectedCameraLabel = it },
                            openDropdownId = openDropdownId,
                            onOpenDropdownChange = { open -> openDropdownId = open },
                            isGrid = isGrid,
                            onToggleGrid = { isGrid = !isGrid }
                        )
                    }
                }

                items(filteredCards, key = { it.camId }) { item ->
                    LiveListCard(item = item, onClick = { onCardClick(item) })
                }
            }
        }
    }
}
/* -------------------- Header (드롭다운 + 기기상태 + 배열) -------------------- */
@Composable
private fun RealTimeHeader(
    sidePadding: Dp,
    rowW: Dp,
    selectedArea: String,
    areaOptions: List<String>,
    onSelectArea: (String) -> Unit,
    selectedCameraLabel: String,
    cameraOptions: List<String>,
    onSelectCamera: (String) -> Unit,
    openDropdownId: String?,
    onOpenDropdownChange: (String?) -> Unit,
    isGrid: Boolean,
    onToggleGrid: () -> Unit
) {
    val c = LocalSafeColors.current

    // 드롭다운 2개
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SimpleDropdown(
            value = selectedArea,
            options = areaOptions,
            onSelect = onSelectArea,
            expanded = (openDropdownId == "area"),
            onExpandedChange = { open ->
                onOpenDropdownChange(if (open) "area" else null)
            },
            modifier = Modifier
                .width(108.dp)
                .height(50.dp),
            menuWidth = 108.dp,
            menuHeight = 150.dp,
            alignMenuRight = false
        )

        Spacer(modifier = Modifier.width(12.dp))
        Spacer(modifier = Modifier.weight(1f))

        SimpleDropdown(
            value = selectedCameraLabel,
            options = cameraOptions,
            onSelect = onSelectCamera,
            expanded = (openDropdownId == "camera"),
            onExpandedChange = { open ->
                onOpenDropdownChange(if (open) "camera" else null)
            },
            modifier = Modifier
                .height(50.dp)
                .wrapContentWidth(),
            menuWidth = rowW,
            menuHeight = 204.dp,
            alignMenuRight = true
        )
    }

    val screenW = LocalConfiguration.current.screenWidthDp.dp

    Box(modifier = Modifier.fillMaxWidth()) {
        Divider(
            color = c.divider,
            thickness = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontal(sidePadding) // ✅ 화면 끝까지
        )
    }

    // ✅ 기기 상태 대시보드
    DeviceStatusDashboard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        left = DeviceDashItem(
            iconRes = R.drawable.bell,
            title = "화재경보기",
            total = 12,
            ok = 11,
            event = 1,
            badgeText = "LIVE",
            badgeType = DashBadgeType.LIVE
        ),
        right = DeviceDashItem(
            iconRes = R.drawable.warn,
            title = "아크차단기",
            total = 15,
            ok = 11,
            event = 4,
            badgeText = "ALERT",
            badgeType = DashBadgeType.ALERT
        )
    )

    // 배열 + 아이콘
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 10.dp),   // ✅ "기기 상태" 타이틀 라인 느낌
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "배열",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Pretendard,
            color = c.sub
        )

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onToggleGrid,
            modifier = Modifier.size(36.dp) // ✅ 48 -> 36 (헤더 라인처럼)
        ) {
            Icon(
                painter = painterResource(id = if (isGrid) R.drawable.frame else R.drawable.vector),
                contentDescription = null,
                tint = c.sub,
                modifier = Modifier.size(22.dp) // ✅ 28 -> 22
            )
        }
    }
}


/* -------------------- Dropdown -------------------- */
@Composable
fun SimpleDropdown(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuWidth: Dp? = null,
    menuHeight: Dp? = null,
    alignMenuRight: Boolean = false
) {
    val c = LocalSafeColors.current
    val isDark = c.isDark
    val density = LocalDensity.current

    var anchorWidthPx by remember { mutableStateOf(0) }

    val dropdownBg = if (isDark) c.surface else Color.White
    val borderColor = c.border
    val gapPx = with(density) { 6.dp.roundToPx() }
    val finalMenuWidth = menuWidth ?: with(density) { anchorWidthPx.toDp() }

    // ✅ 다크모드 전용 색
    val menuTextColor = if (isDark) Color(0xFFCDD1D5) else c.text
    val menuDividerColor = if (isDark) Color(0xFF8A949E) else Color(0xFFF4F5F6)

    Box(modifier = modifier) {
        // 버튼(앵커)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .onSizeChanged { anchorWidthPx = it.width }
                .clip(RoundedCornerShape(10.dp))
                .background(dropdownBg)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .clickable { onExpandedChange(!expanded) }
                .padding(start = 14.dp, end = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    fontSize = 18.sp,
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
        }

        if (expanded) {
            Popup(
                popupPositionProvider = AnchorBelowPositionProvider(
                    gap = gapPx,
                    alignRightToAnchor = alignMenuRight
                ),
                properties = PopupProperties(focusable = false)
            ) {
                val menuShape = RoundedCornerShape(10.dp)

                Box(
                    modifier = Modifier
                        .width(finalMenuWidth)
                        .clip(menuShape)
                        .background(dropdownBg)
                        .border(1.dp, borderColor, menuShape)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .wrapContentHeight()
                            .heightIn(max = menuHeight ?: Dp.Unspecified)
                            .clip(menuShape)
                    ) {
                        itemsIndexed(options) { index, opt ->
                            val selected = opt == value

                            val interactionSource = remember(opt) { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            val highlightBg = when {
                                (selected || isPressed) && !isDark -> Color(0xFFFEF1E7)
                                (selected || isPressed) && isDark -> Color(0xFF664224)
                                else -> Color.Transparent
                            }

                            val itemShape = when (index) {
                                options.lastIndex -> RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                                else -> RoundedCornerShape(0.dp)
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = opt,
                                        fontSize = 18.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = menuTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    onSelect(opt)
                                    onExpandedChange(false)
                                },
                                interactionSource = interactionSource,
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(highlightBg, itemShape)
                            )

                            if (!isDark && index != options.lastIndex) {
                                Divider(
                                    color = menuDividerColor,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* -------------------- Bottom Bar -------------------- */
@Composable
fun RealTimeBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current

    val selectedColor = Color(0xFFFF7A00)
    val unselectedColor = c.sub

    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        color = c.bottomBar,
        modifier = modifier
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
    val isDark = c.isDark
    val isRisk = item.hasRisk()

    val cardBg = if (isDark) Color(0xFF1E2124) else Color.White

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
                    .height(120.dp)
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = item.thumbRes),
                    error = painterResource(id = item.thumbRes),
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
                    .background(cardBg)
                    .padding(horizontal = 12.dp, vertical = 18.dp),
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

@Composable
fun LiveGridCard(item: LiveCardItem, onClick: () -> Unit) {
    val c = LocalSafeColors.current
    val isDark = c.isDark
    val isRisk = item.hasRisk()

    val cardBg = if (isDark) Color(0xFF1E2124) else Color.White

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
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = item.thumbRes),
                    error = painterResource(id = item.thumbRes),
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
                    .background(cardBg)
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
    val transition = rememberInfiniteTransition(label = "liveDot")
    val dotAlpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = modifier
            .size(width = 51.dp, height = 22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFE54F48)),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(8.dp))
            Image(
                painter = painterResource(id = R.drawable.dot),
                contentDescription = null,
                modifier = Modifier.alpha(dotAlpha.value)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "LIVE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Pretendard,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CamPill(
    text: String,
    isRisk: Boolean = false
) {
    val c = LocalSafeColors.current
    val isDark = c.isDark

    val camBg = when {
        isDark -> Color(0xFF8A949E)
        else -> Color(0xFF58616A)
    }.let { base ->
        if (isRisk) base.copy(alpha = 0.85f) else base
    }

    val camText = when {
        isDark -> Color.Black
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(camBg)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = camText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Pretendard
        )
    }
}

@Composable
fun PlaceText(text: String, color: Color) {
    val c = LocalSafeColors.current
    val placeColor = if (c.isDark) Color(0xFFCDD1D5) else color

    Text(
        text = text,
        color = placeColor,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = Pretendard
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
    val isDark = c.isDark

    val bg = when {
        isDark && isRisk -> Color(0xFF131416)
        isDark && !isRisk -> c.chip
        !isDark && isRisk -> Color(0xFFF3F4F6)
        else -> Color.White
    }

    val fg = when {
        isRisk && isDark -> Color(0xFF9AA1AA)
        isRisk && !isDark -> Color(0xFF6B7280)
        else -> c.chipText
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Pretendard
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
    val isDark = c.isDark

    val bg = if (isDark) {
        Color(0xFF2A3038)
    } else {
        Color(0xFFF4F5F6)
    }

    val border = if (isDark) c.border else Color.Transparent

    val fg = if (isDark) Color(0xFF9CA3AF) else Color(0xFF58616A)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1
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
        RealTimeScreen(cards = sampleLiveCards(), onCardClick = {}, onMapClick = {})
    }
}

/* -------------------- Device Status Dashboard -------------------- */
enum class DashBadgeType { LIVE, ALERT }

data class DeviceDashItem(
    val iconRes: Int,
    val title: String,
    val total: Int,
    val ok: Int,
    val event: Int,
    val badgeText: String,
    val badgeType: DashBadgeType
)

@Composable
fun DeviceStatusDashboard(
    left: DeviceDashItem,
    right: DeviceDashItem,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current

    Column(modifier = modifier) {

        Text(
            text = "기기 상태",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Pretendard,
            color = c.sub
        )

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DeviceStatusCard(item = left, modifier = Modifier.weight(1f))
            DeviceStatusCard(item = right, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeviceStatusCard(
    item: DeviceDashItem,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current
    val isDark = c.isDark

    val cardBg = if (isDark) Color(0xFF1E2124) else Color.White
    val border = if (isDark) c.border else Color(0xFFE5E7EB)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.weight(1f))

                DashBadge(
                    text = item.badgeText,
                    type = item.badgeType,
                    modifier = Modifier.wrapContentWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${item.title} | ${item.total}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Pretendard,
                color = if (isDark) Color(0xFFCDD1D5) else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "정상 ${item.ok}",
                    fontSize = 12.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color(0xFF9AA1AA) else Color(0xFF6B7280)
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    text = "이벤트 ${item.event}",
                    fontSize = 12.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.badgeType == DashBadgeType.ALERT) Color(0xFFE54F48) else Color(0xFFFF7A00)
                )
            }
        }
    }
}

@Composable
private fun DashBadge(
    text: String,
    type: DashBadgeType,
    modifier: Modifier = Modifier
) {
    val bg = when (type) {
        DashBadgeType.LIVE -> Color(0xFFFFE8D6)
        DashBadgeType.ALERT -> Color(0xFFFFE1E0)
    }
    val fg = when (type) {
        DashBadgeType.LIVE -> Color(0xFFFF7A00)
        DashBadgeType.ALERT -> Color(0xFFE54F48)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}
private fun Modifier.fullBleedHorizontal(sidePadding: Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val padPx = sidePadding.roundToPx()

        // ✅ 소수 dp 반올림 오차 보정용 여유(1~2px)
        val fudge = 2
        val extra = padPx * 2 + fudge

        val loose = constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = constraints.maxWidth + extra
        )

        val placeable = measurable.measure(loose)

        // ✅ 실제 레이아웃 폭도 강제로 extra만큼 늘림
        layout(constraints.maxWidth + extra, placeable.height) {
            // ✅ 왼쪽으로 더 당겨서 오른쪽도 꽉 차게
            placeable.placeRelative(-(padPx + fudge / 2), 0)
        }
    }
)
@Composable
private fun FullBleedDivider(
    sidePadding: Dp,
    color: Color,
    thickness: Dp = 1.dp
) {
    val density = LocalDensity.current
    val padPx = with(density) { sidePadding.toPx() }
    val strokePx = with(density) { thickness.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(thickness)
            .graphicsLayer { clip = false } // ✅ 중요: 넘쳐 그리기 허용
    ) {
        drawLine(
            color = color,
            start = Offset(-padPx, strokePx / 2f),
            end = Offset(size.width + padPx, strokePx / 2f),
            strokeWidth = strokePx
        )
    }
}

private fun Modifier.bleedHorizontal(sidePadding: Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val pad = sidePadding.roundToPx()

        // ✅ 자식은 더 넓게 측정(좌우 패딩만큼 추가)
        val widened = constraints.copy(
            maxWidth = constraints.maxWidth + pad * 2
        )

        val placeable = measurable.measure(widened)

        // ✅ 부모가 기대하는 폭은 그대로 유지 (중요)
        layout(constraints.maxWidth, placeable.height) {
            // ✅ 왼쪽으로 pad 만큼 당겨서 양쪽으로 삐져나오게
            placeable.placeRelative(-pad, 0)
        }
    }
)