package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*

// ✅ 감지 이벤트 카테고리 Enum
enum class CCTVEventType(val label: String) {
    HELMET_NOT_WEARING("안전모 미착용"),
    AISLE_ACCIDENT("통로사고"),
    COLLISION_ACCIDENT("충돌사고"),
    SAFETY_LINE_VIOLATION("안전선 침범"),
    TRANSPORT_ACCIDENT("운반사고"),
    FIRE_ACCIDENT("화재사고"),
    PINCH_ACCIDENT("협착사고"),
    FALL_DOWN("쓰러짐");

    companion object {
        val allLabels = values().map { it.label }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CCTVManagementScreen(
    onBackClick: () -> Unit = {}
) {
    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val editColor = TextMedium
        val titleColor = if (isLight) Color.Black else TextGray5
        val dividerColor = if (isLight) Lightgray else GrayBackground

        // ✅ 상태 관리
        var isEditMode by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var selectedArea by remember { mutableStateOf("전체 구역") }
        val selectedEvents = remember { mutableStateListOf<String>() }

        Scaffold(
            backgroundColor = MaterialTheme.colors.onPrimary,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "CCTV 관리",
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                            fontFamily = Pretendard,
                            modifier = Modifier.offset(x = (-24).dp),
                            fontSize = 24.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.backicon),
                                contentDescription = "Back",
                                tint = titleColor
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { isEditMode = !isEditMode }) {
                            Text(
                                text = if (isEditMode) "완료" else "편집",
                                color = if (isEditMode) MainOrange else editColor,
                                fontFamily = Pretendard,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 36.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ✅ 편집 모드가 아닐 때만 설치구역 표시
                AnimatedVisibility(
                    visible = !isEditMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LabelText("설치구역")
                        CCTVCustomDropdown(
                            options = listOf("전체 구역", "A구역", "B구역", "C구역, D구역"),
                            selectedOption = selectedArea,
                            onOptionSelected = { selectedArea = it },
                            isLight = isLight
                        )
                    }
                }
                
                // ✅ 편집 모드일 때 나타나는 액션 바
                if (isEditMode) {
                    EditModeActionBar(isLight = isLight)
                }

                Divider(color = dividerColor, thickness = 1.dp)

                LabelText("감지 이벤트")

                // ✅ 감지 이벤트 버튼 리스트 (FlowRow를 사용하여 자동 줄바꿈)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CCTVEventType.allLabels.forEach { eventLabel ->
                        val isSelected = selectedEvents.contains(eventLabel)
                        CCTVEventSelectButton(
                            text = eventLabel,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelected) selectedEvents.remove(eventLabel)
                                else selectedEvents.add(eventLabel)
                            }
                        )
                    }
                }
                Divider(color = dividerColor, thickness = 1.dp)
                LabelText("카메라 목록")
                
                // 카메라 목록 예시
                CamFrame(
                    name = "CAM01",
                    location = "C구역 1열",
                    event = listOf("화재사고", "협착사고")
                )

            }
        }
    }
}

@Composable
fun CCTVEventSelectButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isLight = MaterialTheme.colors.isLight
    val notBgColor = if (isLight) TextGray5 else TextGray20
    val notTextColor = if (isLight) TextGray else TextGray60
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(
                color = if (isSelected) MainOrange else notBgColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = Pretendard,
            fontSize = 16.sp,
            color = if (isSelected) MaterialTheme.colors.onPrimary else notTextColor,
        )
    }
}

@Composable
fun EditModeActionBar(isLight: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(1.dp, GrayBorder, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "전체 선택",
                fontFamily = Pretendard,
                fontSize = 16.sp,
                color = if (isLight) TextGray20 else TextGray5
            )
        }

        Button(
            onClick = { /* 삭제 로직 */ },
            colors = ButtonDefaults.buttonColors(backgroundColor = StatusRed),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.elevation(0.dp, 0.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = "삭제", color = Color.White, fontFamily = Pretendard, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CCTVCustomDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    isLight: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val bgColor = if (isLight) Color.White else TextGray20
    val borderColor = if (isLight) GrayBorder else TextDark

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(color = bgColor, shape = RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedOption,
                fontFamily = Pretendard,
                fontSize = 16.sp,
                color = if (isLight) TextGray20 else TextGray5
            )
            Icon(
                painter = painterResource(id = R.drawable.dropbox),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(14.dp, 9.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(bgColor)
        ) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    onOptionSelected(option)
                    expanded = false
                }) {
                    Text(
                        text = option,
                        color = if (isLight) TextGray20 else TextGray5,
                        fontFamily = Pretendard,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LabelText(text: String) {
    val isLight = MaterialTheme.colors.isLight
    val color = if (isLight) TextGray60 else TextGray
    Text(
        text = text,
        fontFamily = Pretendard,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun CamFrame(name: String, location: String, event: List<String>) {
    val isLight = MaterialTheme.colors.isLight
    val bgColor = if (isLight) Color.White else TextGray20
    val borderColor = if (isLight) GrayBorder else TextDark
    val titleColor = if (isLight) TextDark else GrayBorder
    val subTextColor = if (isLight) TextGray60 else TextGray

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(157.dp)
            .background(color = bgColor, shape = RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = name,
                        color = titleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Pretendard
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = location,
                        color = subTextColor,
                        fontSize = 14.sp,
                        fontFamily = Pretendard
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.right),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                event.forEach { e ->
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isLight) TextGray5 else Color(0xFF374151),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = e,
                            color = subTextColor,
                            fontSize = 12.sp,
                            fontFamily = Pretendard
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CCTVManagementScreenPreview() {
    CCTVManagementScreen()
}
