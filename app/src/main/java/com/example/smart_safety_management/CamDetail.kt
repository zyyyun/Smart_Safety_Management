package com.example.smart_safety_management

import android.content.res.Configuration
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.ui.theme.TextGray5
import com.example.smart_safety_management.ui.theme.*

data class Caminfo(
    val name : String,
    val code : String,
    val hostname : String,
    val hostid : String,
    val hostpw : String,
    val state : Boolean,
    val lastconnect : String,
    val install : String,
)

// 카메라 상세 더미 데이터
val mockCamInfo = Caminfo(
    name = "CAM01",
    code = "TCVR2947G729DH49",
    hostname = "빌라 에코",
    hostid = "빌라 에코",
    hostpw = "********",
    state = true,
    lastconnect = "2025-03-18 10:35:59",
    install = "C구역 1열"
)

enum class EventType(val label: String) {
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


@Composable
fun CamDetailScreen(
    onBackClick: () -> Unit = {}
) {
    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val categoryColor = if (isLight) TextGray60 else TextGray
        val mainColor = if (isLight) TextGray20 else TextGray5
        val dividerColor = if (isLight) Lightgray else GrayBackground

        // 드롭박스 상태 관리
        var selectedDirection by remember { mutableStateOf("12시") }
        var selectedCycle by remember { mutableStateOf("5분") }

        Scaffold(
            backgroundColor = MaterialTheme.colors.onPrimary,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "카메라 상세",
                            fontWeight = FontWeight.Bold,
                            color = if (isLight) Color.Black else TextGray5,
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
                                tint = if (isLight) Color.Black else TextGray5
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            @Suppress("UNUSED_VARIABLE")
            val selectedEvents = remember { mutableStateListOf<EventType>() }

            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(start = 23.dp, end = 23.dp, top = 23.dp, bottom = 35.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "정보",
                    fontFamily = Pretendard,
                    fontSize = 16.sp,
                    color = categoryColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                CamInfoItem("이름", mockCamInfo.name, categoryColor, mainColor)
                CamInfoItem("장치코드", mockCamInfo.code, categoryColor, mainColor)
                CamInfoItem("호스트 이름", mockCamInfo.hostname, categoryColor, mainColor)
                CamInfoItem("호스트 ID", mockCamInfo.hostid, categoryColor, mainColor)
                CamInfoItem("호스트 PW", mockCamInfo.hostpw, categoryColor, mainColor)
                CamInfoItem("최근 연결", mockCamInfo.lastconnect, categoryColor, mainColor)
                CamInfoItem("상태", if (mockCamInfo.state) "정상" else "미수신", categoryColor, mainColor)

                Divider(
                    color = dividerColor,
                    thickness = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 24.dp)
                )
                Text(
                    text = "촬영",
                    fontFamily = Pretendard,
                    fontSize = 16.sp,
                    color = categoryColor
                )

                CamInfoItem("설치구역", mockCamInfo.install, categoryColor, mainColor)


                FilmingInfo("촬영 방향", categoryColor) {
                    CustomDropdown(
                        options = listOf("12시", "3시", "6시", "9시"),
                        selectedOption = selectedDirection,
                        onOptionSelected = { selectedDirection = it },
                        isLight = isLight
                    )
                }

                FilmingInfo("영역 촬영주기", categoryColor) {
                    CustomDropdown(
                        options = listOf("1분", "3분", "5분", "10분"),
                        selectedOption = selectedCycle,
                        onOptionSelected = { selectedCycle = it },
                        isLight = isLight
                    )

                }

            }
        }
    }
}


@Composable
fun CustomDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    isLight: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val bgColor = if (isLight) Color.White else TextGray20
    val borderColor = if (isLight) GrayBorder else TextDark
    val textColor = if (isLight) TextDark else GrayBorder

    Box {
        Row(
            modifier = Modifier
                .size(width = 106.dp, height = 40.dp)
                .background(color = bgColor, shape = RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedOption,
                fontFamily = Pretendard,
                fontSize = 16.sp,
                color = textColor
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
                .width(106.dp)
                .background(bgColor)
        ) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    onOptionSelected(option)
                    expanded = false
                }) {
                    Text(
                        text = option,
                        color = textColor,
                        fontFamily = Pretendard,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CamInfoItem(label: String, value: String, labelColor: Color, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = Pretendard,
            fontSize = 18.sp,
            color = labelColor
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontFamily = Pretendard,
            fontSize = 18.sp,
            color = valueColor,
            textAlign = TextAlign.End
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            painter = painterResource(id = R.drawable.edit),
            contentDescription = "edit",
            tint = TextMedium,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun FilmingInfo(label: String, labelColor: Color, valueContent: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = Pretendard,
            fontSize = 18.sp,
            color = labelColor
        )
        Spacer(modifier = Modifier.weight(1f))
        valueContent()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CamDetailScreenPreview() {
    CamDetailScreen()
}
