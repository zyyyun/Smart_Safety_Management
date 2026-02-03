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

    // ✅ 이제 RealTimeScreen에서는 바텀바를 그리지 않는다 (Activity가 담당)
    // ✅ 대신 content는 Activity에서 내려준 padding(modifier)에 의해 bottom inset이 확보됨

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) c.bg else Color.White)
    ) {
        Column(Modifier.fillMaxSize()) {
            // ✅ 상단바(실시간 상황 + 지도 버튼)
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
                    // ✅ 라이트/다크 자동으로 따라가게 변경
                    containerColor = if (isDark) Color(0xFF000000) else Color.White,
                    titleContentColor = c.text,
                    actionIconContentColor = c.sub
                )
            )


            // ✅ 본문
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

    // ✅ 라이트는 완전 흰색 / 다크는 앱 bg(완전 검정 톤)
    val screenBg = if (isDark) c.bg else Color.White

    // ✅ 왼쪽 다이얼로그: 카메라들의 environment_type 요소들 (중복 제거)
    val areaOptions = remember(cards) {
        listOf("공간별") + cards.map { it.place }.distinct().sorted()
    }
    var selectedArea by remember { mutableStateOf("공간별") }
    var isGrid by remember { mutableStateOf(false) }

    // ✅ 지금 열려있는 드롭다운: "area" / "camera" / null
    var openDropdownId by remember { mutableStateOf<String?>(null) }

    // ✅ 오른쪽 다이얼로그: 카메라이름, environment_type, event_type 열거
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
    val rowW = screenW - 48.dp // 좌우 padding 24dp * 2

    // ✅ 화면 전체를 Box로 감싸서 "바깥 클릭으로 닫기" 오버레이를 깔 수 있게 함
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        // ✅ 메뉴가 열려있을 때, 배경 아무 곳이나 누르면 닫힘
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

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 드롭다운 2개
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)   // ✅ 좌우 24
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                SimpleDropdown(
                    value = selectedArea,
                    options = areaOptions,
                    onSelect = { selectedArea = it },
                    expanded = (openDropdownId == "area"),
                    onExpandedChange = { open ->
                        openDropdownId = if (open) "area" else null
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
                    onSelect = { selectedCameraLabel = it },
                    expanded = (openDropdownId == "camera"),
                    onExpandedChange = { open ->
                        openDropdownId = if (open) "camera" else null
                    },
                    modifier = Modifier
                        .height(50.dp)
                        .wrapContentWidth(),
                    menuWidth = rowW,
                    menuHeight = 204.dp,
                    alignMenuRight = true
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
                    fontFamily = Pretendard,
                    color = c.sub
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { isGrid = !isGrid },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = if (isGrid) R.drawable.frame else R.drawable.vector),
                        contentDescription = null,
                        tint = c.sub,
                        modifier = Modifier.size(28.dp)
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
                            bottom = 12.dp
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
                                (selected || isPressed) && isDark  -> Color(0xFF664224)
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
                                        color = menuTextColor, // ✅ 다크모드 #CDD1D5
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

    // ✅ 다크모드 카드 배경
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
            // ✅ 상단 썸네일
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
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

            // ✅ 하단 정보 영역도 카드 배경색으로 통일
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg) // 🔥 핵심
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

    // ✅ 다크모드 카드 배경색 고정
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

            // ✅ 정보 영역도 카드색으로 통일 (그리드에서도 동일하게)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg) // 🔥 핵심
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
    // ✅ 2초 주기 깜빡임(점만)
    val transition = rememberInfiniteTransition(label = "liveDot")
    val dotAlpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            // 2초마다 한 번 깜빡: 1초 동안 fade-out, 1초 동안 fade-in
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = modifier
            .size(width = 51.dp, height = 22.dp) // ✅ 51x22
            .clip(RoundedCornerShape(999.dp))    // ✅ 타원
            .background(Color(0xFFE54F48)),      // ✅ #E54F48
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(8.dp)) // ✅ 왼쪽 8px

            // ✅ dot.svg (리소스 이름이 dot이면 R.drawable.dot)
            Image(
                painter = painterResource(id = R.drawable.dot),
                contentDescription = null,
                modifier = Modifier.alpha(dotAlpha.value) // ✅ 깜빡임
            )

            Spacer(Modifier.width(4.dp)) // ✅ dot에서 4px

            Text(
                text = "LIVE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,  // ✅ Pretendard Medium
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

    // ✅ CAM 번호 배경색 (라이트 / 다크)
    val camBg = when {
        isDark -> Color(0xFF8A949E)   // 🌙 다크모드 배경
        else   -> Color(0xFF58616A)   // 🌞 라이트모드 배경
    }.let { base ->
        if (isRisk) base.copy(alpha = 0.85f) else base
    }

// ✅ CAM 번호 텍스트 색
    val camText = when {
        isDark -> Color.Black         // 🌙 다크모드 → 검정
        else   -> Color.White         // 🌞 라이트모드 → 흰색
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

    // ✅ 다크모드 위험사고 칩 배경을 #131416 로 변경
    val bg = when {
        isDark && isRisk -> Color(0xFF131416)   // 🔥 변경됨
        isDark && !isRisk -> c.chip
        !isDark && isRisk -> Color(0xFFF3F4F6)
        else -> Color.White
    }

    val fg = when {
        isRisk && isDark -> Color(0xFF9AA1AA)
        isRisk && !isDark -> Color(0xFF6B7280)
        else -> c.chipText
    }

    val borderColor = if (!isDark) Color(0xFFE5E7EB) else c.border
    // (※ borderColor는 지금 Box에 안 쓰고 있어서 그대로 둠)

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
        Color(0xFF2A3038)   // 다크 알약 배경
    } else {
        Color(0xFFF4F5F6)   // ✅ 라이트: 요청한 카드 배경색
    }

    val border = if (isDark) {
        c.border
    } else {
        Color.Transparent
    }

    val fg = if (isDark) {
        Color(0xFF9CA3AF)
    } else {
        Color(0xFF58616A)
    }

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
