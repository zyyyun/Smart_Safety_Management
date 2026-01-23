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
import com.example.smart_safety_management.screens.dialog.MapDialog
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.ui.theme.ClipartKorea
import androidx.compose.ui.platform.LocalConfiguration
import com.example.smart_safety_management.ui.theme.Pretendard


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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RealTimeScreen(
    modifier: Modifier = Modifier,
    onCardClick: (LiveCardItem) -> Unit
) {
    val c = LocalSafeColors.current
    val isDark = c.isDark

    // ✅ 지도 다이얼로그 토글 + 선택된 카드 저장
    var showMap by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<LiveCardItem?>(null) }

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
                        onClick = { showMap = true },
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
                    containerColor  =if (isDark) Color(0xFF000000) else Color.White,
                    titleContentColor = c.text,
                    actionIconContentColor = c.sub
                )
            )



            // ✅ 본문
            RealTimeContent(
                modifier = Modifier.fillMaxSize(),
                onCardClick = { item ->
                    selectedItem = item
                    onCardClick(item)
                }
            )
        }

        // ✅ 지도 다이얼로그
        if (showMap) {
            MapDialog(
                item = selectedItem,
                onDismiss = { showMap = false },
                onMoveCamera = {
                    showMap = false
                    // TODO: 카메라 이동/상세로 이동 연결
                }
            )
        }
    }
}


/* -------------------- RealTimeScreen 본문 -------------------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RealTimeContent(
    modifier: Modifier = Modifier,

    onCardClick: (LiveCardItem) -> Unit
) {
    val c = LocalSafeColors.current
    val isDark = c.isDark

    // ✅ 라이트는 완전 흰색 / 다크는 앱 bg(완전 검정 톤)
    val screenBg = if (isDark) c.bg else Color.White

    val areaOptions = listOf("도로", "내부", "공간별")
    var selectedArea by remember { mutableStateOf("공간별") }
    var isGrid by remember { mutableStateOf(false) }

    // ✅ 지금 열려있는 드롭다운: "area" / "camera" / null
    var openDropdownId by remember { mutableStateOf<String?>(null) }

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

                Spacer(modifier = Modifier.width(12.dp)) // ✅ 두 드롭다운 사이 최소 간격(원하면 유지)

                Spacer(modifier = Modifier.weight(1f))   // ✅ 오른쪽 드롭다운을 끝으로 밀기 (핵심)

                SimpleDropdown(
                    value = selectedCameraLabel,
                    options = cameraOptionsWithAll,
                    onSelect = { selectedCameraLabel = it },
                    expanded = (openDropdownId == "camera"),
                    onExpandedChange = { open ->
                        openDropdownId = if (open) "camera" else null
                    },
                    modifier = Modifier
                        .height(50.dp)
                        .wrapContentWidth(),
                    menuWidth = rowW,          // ✅ Row 전체 폭(=왼쪽 시작선~오른쪽 끝)
                    menuHeight = 204.dp,
                    alignMenuRight = true      // ✅ 오른쪽 버튼 기준으로 오른쪽 끝 맞춤
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
                Box(
                    modifier = Modifier
                        .width(finalMenuWidth)
                        .clip(RoundedCornerShape(10.dp))
                        .background(dropdownBg)
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .then(if (menuHeight != null) Modifier.height(menuHeight) else Modifier)
                            .verticalScroll(rememberScrollState())
                    ) {
                        options.forEach { opt ->
                            val selected = opt == value

                            // ✅ 선택된 항목 배경색:
                            // - 라이트: #FEF1E7
                            // - 다크: 기존 #664224 유지
                            val selectedBg = when {
                                selected && !isDark -> Color(0xFFFEF1E7)
                                selected && isDark -> Color(0xFF664224)
                                else -> Color.Transparent
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = opt,
                                        fontSize = 18.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = c.text
                                    )
                                },
                                onClick = {
                                    onSelect(opt)
                                    onExpandedChange(false)
                                },
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(selectedBg)
                            )
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

    val cardBg = if (isDark) c.surface else Color.White   // ✅ 카드 전체는 항상 흰색

    val infoBg = if (isDark) {
        if (isRisk) Color(0xFF1F252C) else Color.White
    } else {
        if (isRisk) Color(0xFFF4F5F6) else Color.White    // ✅ 위험가능성만 회색
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
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

    val cardBg = if (isDark) c.surface else Color.White

    val infoBg = if (isDark) {
        if (isRisk) Color(0xFF1F252C) else Color.White
    } else {
        if (isRisk) Color(0xFFF4F5F6) else Color.White
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
            fontWeight = FontWeight.Bold,
            fontFamily = Pretendard
        )
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
            .padding(horizontal = 12.dp, vertical = 7.dp)
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
    Text(
        text = text,
        color = color,
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
        isDark && isRisk -> Color(0xFF2A3038)
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
        RealTimeScreen(onCardClick = {})
    }
}
