package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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

enum class HourType(val hour: Int) {
    H00(0), H01(1), H02(2), H03(3), H04(4), H05(5),
    H06(6), H07(7), H08(8), H09(9), H10(10), H11(11),
    H12(12), H13(13), H14(14), H15(15), H16(16), H17(17),
    H18(18), H19(19), H20(20), H21(21), H22(22), H23(23);

    val label: String get() = "%02d시".format(hour)

    companion object {
        val allLabels: List<String> = values().map { it.label }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CamDetailScreen(
    cameraId: String,
    onBackClick: () -> Unit = {}
) {
    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val categoryColor = if (isLight) TextGray60 else TextGray
        val mainColor = if (isLight) TextGray20 else TextGray5
        val dividerColor = if (isLight) Lightgray else GrayBackground
        val textColor = if (isLight) TextDark else GrayBorder

        // 상태 관리
        var camInfo by remember { mutableStateOf<CCTVDetailResponse?>(null) }
        var selectedDirection by remember { mutableStateOf("12시") }
        var selectedCycle by remember { mutableStateOf("5분") }
        val selectedEvents = remember { mutableStateListOf<String>() }
        val selectedHours = remember { mutableStateListOf<String>() }

        // 서버에서 데이터 불러오기
        LaunchedEffect(cameraId) {
            RetrofitClient.instance.getCCTVDetail(cameraId).enqueue(object : Callback<CCTVDetailResponse> {
                override fun onResponse(call: Call<CCTVDetailResponse>, response: Response<CCTVDetailResponse>) {
                    if (response.isSuccessful) {
                        val data = response.body()
                        if (data != null) {
                            camInfo = data
                            
                            // UI 상태 업데이트
                            selectedDirection = data.direction ?: "12시"
                            selectedCycle = if (data.shootingInterval != null) "${data.shootingInterval}분" else "5분"
                            
                            selectedEvents.clear()
                            selectedEvents.addAll(data.events)

                            // 가동 시간 파싱 (000000... 문자열 -> 시간 리스트)
                            selectedHours.clear()
                            data.operatingHours?.let { hoursStr ->
                                if (hoursStr.length == 24) {
                                    hoursStr.forEachIndexed { index, char ->
                                        if (char == '1') {
                                            selectedHours.add(HourType.values().getOrNull(index)?.label ?: "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                override fun onFailure(call: Call<CCTVDetailResponse>, t: Throwable) {
                    // 에러 처리
                }
            })
        }

        if (camInfo == null) {
            // 로딩 중 표시 (필요 시 구현)
        }

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
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 35.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "정보",
                    fontFamily = Pretendard,
                    fontSize = 16.sp,
                    color = categoryColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                CamInfoItem("이름", camInfo?.deviceName ?: "-", categoryColor, mainColor)
                CamInfoItem("장치코드", camInfo?.deviceCode ?: "-", categoryColor, mainColor)
                CamInfoItem("호스트 이름", camInfo?.hostCode ?: "-", categoryColor, mainColor)
                CamInfoItem("호스트 ID", camInfo?.hostId ?: "-", categoryColor, mainColor)
                CamInfoItem("호스트 PW", camInfo?.hostPassword?.map { '*' }?.joinToString("") ?: "-", categoryColor, mainColor)
                CamInfoItem("최근 연결", camInfo?.lastCommDate?.replace("T", " ")?.substringBefore(".") ?: "-", categoryColor, mainColor)
                
                // ✅ 상태값에 따른 텍스트 및 색상 적용, 편집 아이콘 제거 및 여백 삭제
                val isStatusNormal = camInfo?.status == "정상" || camInfo?.status == "Active" // DB 값에 따라 조정 필요
                CamInfoItem(
                    label = "상태",
                    value = camInfo?.status ?: "미수신",
                    labelColor = categoryColor,
                    valueColor = if (isStatusNormal) StatusGreenDark else StatusRed,
                    showEditIcon = false
                )

                // ✅ 첫 번째 경계선: 화면 끝까지 닿도록 수정
                Divider(
                    color = dividerColor,
                    thickness = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val paddingPx = 24.dp.roundToPx()
                            val expandedWidth = constraints.maxWidth + (paddingPx * 2)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = expandedWidth,
                                    maxWidth = expandedWidth
                                )
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.place(-paddingPx, 0)
                            }
                        }
                )
                Text(
                    text = "촬영",
                    fontFamily = Pretendard,
                    fontSize = 16.sp,
                    color = categoryColor
                )

                CamInfoItem("설치구역", camInfo?.installArea ?: "-", categoryColor, mainColor)


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

                FilmingInfo(
                    label = "감지 이벤트",
                    labelColor = categoryColor,
                    showLabelIcon = false,
                    isVertical = true // ✅ 아이콘이 없어도 아래로 배치되도록 설정
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EventType.allLabels.forEach { eventLabel ->
                            val isSelected = selectedEvents.contains(eventLabel)
                            EventSelectButton(
                                text = eventLabel,
                                isSelected = isSelected,
                                onClick = null
                            )
                        }
                    }
                }

                FilmingInfo(
                    label = "가동 시간", 
                    labelColor = categoryColor, 
                    showLabelIcon = false, 
                    isVertical = true // ✅ 아이콘이 없어도 아래로 배치되도록 설정
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HourType.allLabels.forEach { hourLabel ->
                            val isSelected = selectedHours.contains(hourLabel)
                            EventSelectButton(
                                text = hourLabel,
                                isSelected = isSelected,
                                onClick = null
                            )
                        }
                    }
                }
                
                // ✅ 두 번째 경계선: 화면 끝까지 닿도록 수정
                Divider(
                    color = dividerColor,
                    thickness = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val paddingPx = 24.dp.roundToPx()
                            val expandedWidth = constraints.maxWidth + (paddingPx * 2)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = expandedWidth,
                                    maxWidth = expandedWidth
                                )
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.place(-paddingPx, 0)
                            }
                        }
                )
                Text(
                    text = "설치위치",
                    fontFamily = Pretendard,
                    fontSize = 16.sp,
                    color = categoryColor,
                )

                Text(
                    text = "설치 위치 지도",
                    fontFamily = Pretendard,
                    fontSize = 18.sp,
                    color = textColor,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.camdetail_map),
                        contentDescription = "Installation Map",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                    Image(
                        painter = painterResource(id = if (isLight) R.drawable.worker_orange else R.drawable.worker_orange_dark),
                        contentDescription = "Worker Icon",
                        modifier = Modifier.scale(1.67f)
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

        // ✅ 펼쳐진 리스트의 모서리 곡률과 너비를 드롭박스와 동일하게 설정
        MaterialTheme(shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(8.dp))) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(106.dp) // 드롭박스 버튼 너비와 동일하게 설정
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
}

@Composable
fun CamInfoItem(
    label: String, 
    value: String, 
    labelColor: Color, 
    valueColor: Color,
    showEditIcon: Boolean = true // ✅ 아이콘 표시 여부 추가
) {
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
        
        // ✅ showEditIcon이 true일 때만 아이콘과 여백을 표시
        if (showEditIcon) {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                painter = painterResource(id = R.drawable.edit),
                contentDescription = "edit",
                tint = TextMedium,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun FilmingInfo(
    label: String,
    labelColor: Color,
    showLabelIcon: Boolean = false,
    isVertical: Boolean = false, // ✅ 레이아웃 방향 제어를 위한 파라미터 추가
    valueContent: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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

            if (showLabelIcon) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    painter = painterResource(id = R.drawable.edit),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = labelColor
                )
            }

            // ✅ 세로 모드가 아닐 때만 Row 내부에 내용을 배치 (가로 배치)
            if (!isVertical) {
                Spacer(modifier = Modifier.weight(1f))
                valueContent()
            }
        }

        // ✅ 세로 모드이거나 아이콘이 표시될 때 내용을 아래에 배치 (세로 배치)
        if (isVertical || showLabelIcon) {
            Spacer(modifier = Modifier.height(12.dp))
            valueContent()
        }
    }
}

@Composable
fun EventSelectButton(
    text: String,
    isSelected: Boolean,
    onClick: (() -> Unit)? = null
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
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
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

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CamDetailScreenPreview() {
    CamDetailScreen(cameraId = "1")
}
