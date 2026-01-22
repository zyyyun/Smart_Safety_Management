package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.example.smart_safety_management.ui.theme.Pretendard
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.Pretendard



class SettingWorkplaceLocationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
    val isPreview = LocalInspectionMode.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    var query by remember { mutableStateOf("") }


    var dropdownExpanded by remember { mutableStateOf(false) }
    var isRegistered by remember { mutableStateOf(false) }
    var mapOffset by remember { mutableStateOf(Offset.Zero) }
    var sheetHeightDp by remember { mutableStateOf(252.dp) }

    val address = "인천광역시 남동구 예술로 197 (인천아시아드 주경기장)"
    val zipcode = "21983"
    val road = "송도동 162-1"

    val orange = Color(0xFFFF7A00)
    val grayText = Color(0xFF6B7280)
    val border = Color(0xFFE5E7EB)

    // 상태바 높이
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarH = 40.dp
    val searchTop = statusTop + topBarH + 46.dp

    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ✅ 지도 배경: 드래그로 이동 + 탭하면 드롭다운 닫기/포커스 해제
            if (isPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE5E7EB))
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
                border = border,
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
                    painter = painterResource(id = R.drawable.worker_orange),
                    contentDescription = "pin",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 0.dp, y = (-130).dp)
                        .size(110.dp)
                )
            }

            // ✅ background.svg + loc.svg 한쌍 버튼 (지도랑 상관없이 고정)
            MapFloatButton(
                onClick = { /* TODO */ },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 24.dp,
                        bottom = sheetHeightDp + 24.dp
                    )
            ) // ✅★★★★ 여기 닫는 괄호가 꼭 있어야 함

            // ✅ 하단 카드(높이 측정해서 sheetHeightDp 업데이트)
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
                    orange = orange,
                    grayText = grayText,
                    onConfirm = { isRegistered = true },
                    onDelete = { isRegistered = false },
                    onEdit = { /* 보여주기용 */ }
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
            modifier = Modifier.size(44.dp), // ✅ loc 아이콘 크기 조절 여기
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
    val topBarH = 60.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusTop + topBarH)
            .background(Color.White)
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
                    if (isPreview) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "back",
                            tint = Color(0xFF111827),
                            modifier = Modifier.size(width = 12.dp, height = 20.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.back_b),
                            contentDescription = "back",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(width = 12.dp, height = 20.dp)
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Text(
                    text = "현장위치 설정",
                    fontSize = 26.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
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
    border: Color,
    isPreview: Boolean,
    modifier: Modifier = Modifier
) {
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
                .background(Color.White)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPreview) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "search",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.search),
                    contentDescription = "search",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(26.dp)
                )
            }

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
                        color = Color(0xFF111827),
                        fontFamily = Pretendard
                    ),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "주소를 검색하세요",
                                fontSize = 21.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFF9CA3AF),
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
                .background(Color.White)
                .border(1.dp, border, RoundedCornerShape(12.dp))
        ) {
            items.forEach { item ->
                val isB = item.contains("B동")
                DropdownMenuItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isB) Color(0xFFFEF1E7) else Color.Transparent),
                    text = {
                        Text(
                            text = item,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF111827)
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
    orange: Color,
    grayText: Color,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetRadius = 20.dp
    val strongText = Color(0xFF111827)
    val divider = Color(0xFFE5E7EB)
    val badgeBg = Color(0xFFCDF7EC) // 피그마 #CDF7EC

    // ✅ 등록 전: 정보만 더 아래로(버튼은 그대로)
    val preInfoDown = 10.dp   // ← (기존보다 살짝 더) 8~12dp 사이로 조절 추천

    // ✅ 등록 후: 뱃지+정보만 아래로(버튼은 그대로)
    val badgeDown = 10.dp      // 뱃지만 아래로
    val postInfoDown = 8.dp   // 주소/우편번호/도로명 블록 아래로

    // ✅ 등록 후: 주소 ↔ 우편번호 간격 더 띄우기
    val postAddrToZipSpace = 24.dp  // ← 기존 16dp였다면 24dp 추천(20~28dp 조절)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isRegistered) 320.dp else 280.dp)
            .clip(RoundedCornerShape(topStart = sheetRadius, topEnd = sheetRadius))
            .background(Color.White)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 30.dp)
    ) {

        if (!isRegistered) {
            /* =================================================
               ✅ 등록 전 화면: 정보만 아래로 내리고 버튼은 그대로
               ================================================= */

            // ✅ 정보(주소/우편번호/도로명)만 아래로
            Column(modifier = Modifier.padding(top = preInfoDown)) {

                // 주소
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

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "우편번호",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = grayText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = zipcode,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        color = strongText,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "도로명",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        color = grayText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = road,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        color = strongText,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 등록 버튼
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
            ) {
                Text(
                    "위치 등록",
                    fontSize = 20.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
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

            // ✅ 뱃지-주소 간격 (조금 줄이기)
            Spacer(Modifier.height(1.dp))

            // ✅ 정보 블록(주소+우편번호+도로명)만 아래로
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

                // ✅ 주소 ↔ 우편번호 간격 더 띄우기(여기만 바꾸면 됨)
                Spacer(Modifier.height(postAddrToZipSpace))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "우편번호",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Black,
                        color = grayText,
                        style = TextStyle.Default,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = zipcode,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        color = strongText,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "도로명",
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = grayText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = road,
                        fontSize = 20.sp,
                        fontFamily = Pretendard,
                        color = strongText,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 등록 후 버튼(삭제/수정) — 위치 그대로
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, divider),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                ) {
                    Text(
                        "위치 삭제",
                        fontSize = 18.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = strongText
                    )
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6))
                ) {
                    Text(
                        "위치 수정",
                        fontSize = 18.sp,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        color = strongText
                    )
                }
            }
        }
    }
}





    @Preview(
    name = "Setting Workplace Location",
    showBackground = true,
    device = Devices.PIXEL_7
)
@Composable
fun SettingWorkplaceLocationPreview() {
    MaterialTheme {
        SettingWorkplaceLocationScreen(
            onBack = {},
            onConfirm = {}
        )
    }
}
