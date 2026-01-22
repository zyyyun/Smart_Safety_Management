package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import kotlin.math.roundToInt

class SettingWorkplaceLocationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // ✅ 반드시 이걸로 감싸야 LocalSafeColors가 다크/라이트로 바뀜
            Smart_Safety_ManagementTheme {
                SettingWorkplaceLocationScreen(
                    onBack = { finish() },
                    onConfirm = { finish() }
                )
            }
        }
    }
}

@Composable
fun SettingWorkplaceLocationScreen(
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val c = LocalSafeColors.current // 🌙 다크/라이트 팔레트
    LaunchedEffect(c.isDark) {
        android.util.Log.d("THEME_CHECK", "isDark=${c.isDark}")
    }
    val isPreview = LocalInspectionMode.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    var query by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isRegistered by remember { mutableStateOf(false) }

    // ✅ 지도 이동값(핀은 고정, 지도만 움직임)
    var mapOffset by remember { mutableStateOf(Offset.Zero) }

    // ✅ 하단 시트 높이(측정값)
    var sheetHeightDp by remember { mutableStateOf(252.dp) }

    val address = "인천광역시 남동구 예술로 197 (인천아시아드 주경기장)"
    val zipcode = "21983"
    val road = "송도동 162-1"

    val orange = Color(0xFFFF7A00)

    // ✅ 상태바 높이
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarH = 40.dp
    val searchTop = statusTop + topBarH + 46.dp

    Scaffold(
        containerColor = c.bg, // 🌙 전체 배경
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ✅ 지도 배경(이미지는 그대로 유지)
            if (isPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (c.isDark) Color(0xFF0B0F14) else Color(0xFFE5E7EB))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    dropdownExpanded = false
                                    focusManager.clearFocus()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    mapOffset += dragAmount
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                dropdownExpanded = false
                                focusManager.clearFocus()
                            })
                        }
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.setting_map),
                    contentDescription = "map",
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                mapOffset.x.roundToInt(),
                                mapOffset.y.roundToInt()
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    dropdownExpanded = false
                                    focusManager.clearFocus()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    mapOffset += dragAmount
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                dropdownExpanded = false
                                focusManager.clearFocus()
                            })
                        },
                    contentScale = ContentScale.Crop
                )
            }

            // ✅ 검색창 + 드롭다운
            SearchBarOverlay(
                query = query,
                onQueryChange = { newText ->
                    query = newText
                    dropdownExpanded = newText.isNotBlank()
                },
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                isPreview = isPreview,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 24.dp, end = 24.dp, top = searchTop)
            )

            // ✅ 가운데 핀(고정)
            if (isPreview) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(orange)
                )
            } else {
                Image(
                    painter = painterResource(
                        id = if (c.isDark) R.drawable.worker_orange_dark else R.drawable.worker_orange
                    ),
                    contentDescription = "pin",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 0.dp, y = (-130).dp)
                        .size(110.dp)
                )

            }

            // ✅ floating 버튼
            MapFloatButton(
                onClick = { /* TODO */ },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = sheetHeightDp + 24.dp)
            )

            // ✅ 하단 카드(높이 측정)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coords ->
                        sheetHeightDp = with(density) { coords.size.height.toDp() }
                    }
            ) {
                BottomInfoCard(
                    isRegistered = isRegistered,
                    address = address,
                    zipcode = zipcode,
                    road = road,
                    onConfirm = { isRegistered = true },
                    onDelete = { isRegistered = false },
                    onEdit = { /* TODO */ }
                )
            }

            // ✅ TopBar
            TopBarFixed(
                onBack = onBack,
                isPreview = isPreview,
                statusTop = statusTop,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun MapFloatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        Image(
            painter = painterResource(id = R.drawable.loc),
            contentDescription = "loc",
            modifier = Modifier.size(44.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TopBarFixed(
    onBack: () -> Unit,
    isPreview: Boolean,
    statusTop: Dp,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current
    val topBarH = 60.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusTop + topBarH)
            .background(c.topBar) // 🌙 다크면 어둡게
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(topBarH)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 14.dp)
                    .height(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    // ✅ 다크에선 Icons로(리소스가 라이트 전용이면 대비 문제 생김)
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_back),
                        contentDescription = "back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )

                }

                Spacer(Modifier.width(3.dp))

                Text(
                    text = "현장위치 설정",
                    fontSize = 26.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SearchBarOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isPreview: Boolean,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current
    val items = listOf("스타트업 파크 A동", "스타트업 파크 B동", "스타트업 파크 C동")

    val density = LocalDensity.current
    var fieldWidthDp by remember { mutableStateOf(0.dp) }

    val showDropdown = expanded && query.isNotBlank() && query.contains("스타트업 파크")

    Column(modifier = modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onGloballyPositioned { coords ->
                    fieldWidthDp = with(density) { coords.size.width.toDp() }
                }
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface)                 // 🌙
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ 다크/라이트 공용 아이콘
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "search",
                tint = c.sub,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { newText ->
                        onQueryChange(newText)
                        if (newText.isBlank()) onExpandedChange(false) else onExpandedChange(true)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = 21.sp,
                        lineHeight = 18.sp,
                        color = c.text,
                        fontFamily = Pretendard
                    ),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "주소를 검색하세요",
                                fontSize = 21.sp,
                                lineHeight = 18.sp,
                                color = c.sub,
                                fontFamily = Pretendard
                            )
                        }
                        inner()
                    }
                )
            }
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .width(if (fieldWidthDp > 0.dp) fieldWidthDp else Dp.Unspecified)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface)                // 🌙
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
        ) {
            items.forEach { item ->
                val isB = item.contains("B동")
                DropdownMenuItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isB) {
                                if (c.isDark) Color(0xFF5A3516) else Color(0xFFFEF1E7)
                            } else Color.Transparent
                        ),
                    text = {
                        Text(
                            text = item,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            lineHeight = 16.sp,
                            color = c.text
                        )
                    },
                    onClick = {
                        onQueryChange(item)
                        onExpandedChange(false)
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomInfoCard(
    isRegistered: Boolean,
    address: String,
    zipcode: String,
    road: String,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current
    val sheetRadius = 20.dp

    // ✅ 다크모드 시트 배경: 완전 검정
    val sheetBg = if (c.isDark) Color(0xFF000000) else c.surface

    val strongText = c.text
    val divider = c.divider

    // ✅ 피그마 기준 #CDD1D5 (우편번호/도로명 정보)
    val infoGray = Color(0xFFCDD1D5)

    val badgeBg = if (c.isDark) Color(0xFF0E3B2B) else Color(0xFFCDF7EC)

    val preInfoDown = 10.dp
    val badgeDown = 10.dp
    val postInfoDown = 8.dp
    val postAddrToZipSpace = 24.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isRegistered) 320.dp else 280.dp)
            .clip(RoundedCornerShape(topStart = sheetRadius, topEnd = sheetRadius))
            .background(sheetBg) // ✅ 변경: 다크면 완전 검정
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 30.dp)
    ) {

        if (!isRegistered) {

            Column(modifier = Modifier.padding(top = preInfoDown)) {

                Text(
                    text = address,
                    fontSize = 20.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    color = strongText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )

                Spacer(Modifier.height(30.dp))

                // ✅ 우편번호: 다크모드면 #CDD1D5
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "우편번호",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = zipcode,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ✅ 도로명: 다크모드면 #CDD1D5
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "도로명",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = road,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
            ) {
                Text(
                    text = "위치 등록",
                    fontSize = 20.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    color = if (c.isDark) Color.Black else Color.White
                )
            }



        } else {

            Row(
                modifier = Modifier
                    .padding(top = badgeDown)
                    .clip(RoundedCornerShape(999.dp))
                    .background(badgeBg)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.postend),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "등록완료",
                    fontFamily = Pretendard,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF12B76A),
                    lineHeight = 13.sp
                )
            }

            Spacer(Modifier.height(1.dp))

            Column(modifier = Modifier.padding(top = postInfoDown)) {

                Text(
                    text = address,
                    fontSize = 20.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    color = strongText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )

                Spacer(Modifier.height(postAddrToZipSpace))

                // ✅ 우편번호: 다크모드면 #CDD1D5
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "우편번호",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = zipcode,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ✅ 도로명: 다크모드면 #CDD1D5
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "도로명",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = road,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) infoGray else strongText
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(Modifier.fillMaxWidth()) {

                // 🔹 위치 삭제
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, divider),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (c.isDark) Color(0xFF000000) else c.surface
                    )
                ) {
                    Text(
                        text = "위치 삭제",
                        fontSize = 18.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) Color(0xFF8A949E) else strongText   // ✅ 변경
                    )
                }

                Spacer(Modifier.width(12.dp))

                // 🔹 위치 수정
                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (c.isDark) Color(0xFF131416) else Color(0xFFF3F4F6) // ✅ 변경
                    )
                ) {
                    Text(
                        text = "위치 수정",
                        fontSize = 18.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = if (c.isDark) Color(0xFF8A949E) else strongText   // ✅ 변경
                    )
                }
            }

        }
    }
}


@Preview(
    name = "Setting Workplace Location (Light)",
    showBackground = true,
    device = Devices.PIXEL_7
)
@Composable
fun SettingWorkplaceLocationPreview() {
    Smart_Safety_ManagementTheme(darkTheme = false) {
        SettingWorkplaceLocationScreen(
            onBack = {},
            onConfirm = {}
        )
    }
}

@Preview(
    name = "Setting Workplace Location (Dark)",
    showBackground = true,
    device = Devices.PIXEL_7
)
@Composable
fun SettingWorkplaceLocationDarkPreview() {
    Smart_Safety_ManagementTheme(darkTheme = true) {
        SettingWorkplaceLocationScreen(
            onBack = {},
            onConfirm = {}
        )
    }
}


