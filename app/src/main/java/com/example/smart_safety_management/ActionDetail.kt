package com.example.smart_safety_management

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

@Composable
fun ActionDetailScreen(
    onBackClick: () -> Unit = {},
    initialExpanded: Boolean = false
) {
    var actionType by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var attachedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // 스크롤 상태 관리
    val scrollState = rememberScrollState()
    
    // 드롭다운 확장 상태 관리
    var expanded by remember { mutableStateOf(initialExpanded) }
    val options = listOf("조치공유", "조치필요", "즉시조치")

    Smart_Safety_ManagementTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "조치요청서 작성",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF131416),
                            fontFamily = Pretendard,
                            modifier = Modifier.offset(x = (-20).dp),
                            fontSize = 24.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.backicon),
                                contentDescription = "Back",
                                tint = Color.Black
                            )
                        }
                    },
                    backgroundColor = Color.White,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = Color.White
            ) {
                // 스크롤바가 포함된 Column
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScrollbar(scrollState) // 커스텀 스크롤바 적용
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "이벤트 내용",
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF58616A),
                        fontFamily = Pretendard,
                        fontSize = 16.sp,
                        modifier = Modifier.offset(x = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(15.dp))

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .background(Color(0xFFFFFFFF), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFCDD1D5), shape = RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.warning_icon),
                                    contentDescription = "경고",
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .offset(x = (-5).dp, y = 5.dp),
                                    tint = Color.Unspecified
                                )
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = "C구역 2열",
                                        color = Color.Black,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        fontFamily = Pretendard
                                    )
                                    Text(
                                        text = "쓰러짐이 감지되었습니다.",
                                        color = Color(0xFF58616A),
                                        fontSize = 14.sp,
                                        fontFamily = Pretendard,
                                        modifier = Modifier.offset(y = (4).dp)
                                    )
                                }
                            }

                            Divider(
                                color = Color(0xFFF4F5F6),
                                thickness = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val detailItems = listOf(
                                "감지 이벤트" to "쓰러짐",
                                "발생 시간" to "2025-05-07 16:05:20",
                                "장치명" to "CAM03",
                                "발생 위치" to "D구역 1열"
                            )

                            detailItems.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        color = Color(0xFF33363D),
                                        fontSize = 16.sp,
                                        fontFamily = Pretendard,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = value,
                                        color = Color(0xFF131416),
                                        fontSize = 16.sp,
                                        fontFamily = Pretendard,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "조치 유형",
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF58616A),
                        fontFamily = Pretendard,
                        fontSize = 16.sp,
                        modifier = Modifier.offset(x = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(15.dp))
                    
                    // 조치 유형 드롭다운 박스
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        // 선택된 항목에 따른 아이콘 리소스 결정
                        val selectedIconRes = when (actionType) {
                            "조치공유" -> R.drawable.priority1
                            "조치필요" -> R.drawable.priority2
                            "즉시조치" -> R.drawable.priority3
                            else -> null
                        }

                        OutlinedTextField(
                            value = if (actionType.isEmpty()) "조치 유형 선택" else actionType,
                            onValueChange = { },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            textStyle = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = Pretendard,
                                color = Color(0xFF131416)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                Box(
                                    modifier = Modifier.fillMaxHeight(),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(end = 12.dp)
                                    ) {
                                        if (selectedIconRes != null) {
                                            Icon(
                                                painter = painterResource(id = selectedIconRes),
                                                contentDescription = null,
                                                tint = Color.Unspecified
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        Icon(
                                            painter = painterResource(id = R.drawable.dropbox),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                unfocusedBorderColor = Color(0xFFCDD1D5),
                                textColor = Color(0xFF131416)
                            )
                        )
                        
                        // 드롭다운 메뉴
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .width(this@BoxWithConstraints.maxWidth)
                                .background(Color.White)
                        ) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    onClick = {
                                        actionType = option
                                        expanded = false
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = option,
                                            fontFamily = Pretendard,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF131416)
                                        )
                                        val iconRes = when(option) {
                                            "조치공유" -> R.drawable.priority1
                                            "조치필요" -> R.drawable.priority2
                                            "즉시조치" -> R.drawable.priority3
                                            else -> null
                                        }
                                        if (iconRes != null) {
                                            Icon(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = null,
                                                tint = Color.Unspecified
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 전체 영역 클릭 가로채기
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expanded = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "제목",
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF58616A),
                        fontFamily = Pretendard,
                        fontSize = 16.sp,
                        modifier = Modifier.offset(x = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(15.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(60.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = TextStyle(fontSize = 16.sp, fontFamily = Pretendard, color = Color(0xFF131416)),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            unfocusedBorderColor = Color(0xFFCDD1D5)
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "내용",
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF58616A),
                        fontFamily = Pretendard,
                        fontSize = 16.sp,
                        modifier = Modifier.offset(x = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(15.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = TextStyle(fontSize = 16.sp, fontFamily = Pretendard, color = Color(0xFF131416)),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            unfocusedBorderColor = Color(0xFFCDD1D5)
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "사진",
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF58616A),
                        fontFamily = Pretendard,
                        fontSize = 16.sp,
                        modifier = Modifier.offset(x = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clickable {
                                    attachedPhotos = attachedPhotos + "photoUri_${System.currentTimeMillis()}"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                                drawRoundRect(
                                    color = Color(0xFFCDD1D5),
                                    style = Stroke(width = 1.dp.toPx(), pathEffect = pathEffect),
                                    cornerRadius = CornerRadius(8.dp.toPx())
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.camera_icon),
                                    contentDescription = "사진첨부",
                                    tint = Color(0xFFB1B8BE)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "사진첨부",
                                    color = Color(0xFFB1B8BE),
                                    fontSize = 18.sp
                                )
                            }
                        }

                        attachedPhotos.reversed().forEach { photoUri ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Photo, contentDescription = "Attached Photo: $photoUri", tint = Color.Gray)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = { /* 작성 완료 로직 */ },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                            .padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFF97316),
                            contentColor = Color(0xFFFFFFFF)
                        ),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp)


                    ) {
                        Text(
                            text = "작성완료",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            fontFamily = Pretendard
                        )
                    }
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        }
    }
}

// 커스텀 세로 스크롤바 모디파이어
fun Modifier.verticalScrollbar(
    state: androidx.compose.foundation.ScrollState,
    width: Dp = 4.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f)
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue > 0) {
        val scrollbarHeight = size.height * (size.height / (state.maxValue + size.height))
        val scrollbarOffset = state.value * (size.height / (state.maxValue + size.height))
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx(), scrollbarOffset),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2)
        )
    }
}

@Preview(showBackground = true, name = "일반 상태")
@Composable
fun ActionDetailScreenPreview() {
    ActionDetailScreen()
}

@Preview(showBackground = true, name = "드롭다운 아이템 UI 확인")
@Composable
fun ActionTypeItemsPreview() {
    val options = listOf("조치공유", "조치필요", "즉시조치")
    Smart_Safety_ManagementTheme {
        Surface(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = 4.dp,
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    DropdownMenuItem(onClick = {}) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                fontFamily = Pretendard,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF131416)
                            )
                            val iconRes = when(option) {
                                "조치공유" -> R.drawable.priority1
                                "조치필요" -> R.drawable.priority2
                                "즉시조치" -> R.drawable.priority3
                                else -> null
                            }
                            if (iconRes != null) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
