package com.example.smart_safety_management

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import kotlinx.coroutines.launch

// --- 1. 데이터 모델 정의 ---
data class HistoryEventData(
    val accidentType: String,
    val location: String,
    val content: String,
    val occurrenceTime: String,
    val actionByName: String,
    val actionTime: String
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HistoryScreen() {
    var isAscending by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf("AI감지") }
    
    // 검색 모드 및 쿼리 상태 관리
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val coroutineScope = rememberCoroutineScope()

    // 더미 데이터 생성
    val dummyHistoryData = listOf(
        HistoryEventData("위험", "C구역 2열", "화재사고가 감지되었습니다.", "2025-05-07 16:05:20", "홍길동", "2025-05-07 16:10:00"),
        HistoryEventData("경고", "A구역 1열", "쓰러짐이 감지되었습니다.", "2025-05-07 15:30:10", "김철수", "2025-05-07 15:45:00"),
        HistoryEventData("주의", "B구역 3열", "미착용 보호구가 감지되었습니다.", "2025-05-07 14:20:05", "이영희", "2025-05-07 14:30:00"),
        HistoryEventData("경고", "D구역 2열", "접근 금지 구역 진입 감지", "2025-05-07 13:10:45", "박민수", "2025-05-07 13:25:00")
    )

    Smart_Safety_ManagementTheme {
        ModalBottomSheetLayout(
            sheetState = sheetState,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContent = {
                FilterBottomSheetContent()
            }
        ) {
            Scaffold(
                topBar = {
                    Column {
                        HistoryTopAppBar(
                            isAscending = isAscending,
                            onSortToggle = { isAscending = !isAscending },
                            onFilterClick = {
                                coroutineScope.launch {
                                    sheetState.show()
                                }
                            },
                            onSearchIconClick = { isSearchMode = !isSearchMode }
                        )
                        // 상단바 아래에 검색바 표시
                        if (isSearchMode) {
                            HistorySearchBar(
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                width = 350.dp 
                            )
                            // 검색바와 아래 선택바 사이 경계선
                            Divider(color = Color(0xFFCDD1D5), thickness = 1.dp)
                        }
                        HistorySecondaryAppBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                },
                bottomBar = { MyBottomNavigation(selectedRoute = "nav_history") },
                backgroundColor = Color(0xFFFF7A00)
            ) { paddingValues ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    color = Color.White,
                    shape = RectangleShape
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        dummyHistoryData.forEach { data ->
                            HistoryItemFrame(data)
                        }
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HistorySearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    width: Dp = Dp.Unspecified // 너비 조절 파라미터
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center // 중앙 정렬
    ) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth())
                .height(52.dp)
                .border(1.dp, Color(0xFFCDD1D5), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp), 
            placeholder = { 
                Text(
                    "검색하세요", 
                    color = Color(0xFFB1B8BE),
                    fontSize = 18.sp,
                    fontFamily = Pretendard
                ) 
            },
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 14.sp,
                fontFamily = Pretendard
            ),
            singleLine = true,
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.search),
                    contentDescription = null,
                    tint = Color(0xFF6D7882)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                cursorColor = Color(0xFFFF7A00),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )
    }
}

@Composable
fun HistoryTopAppBar(
    isAscending: Boolean, 
    onSortToggle: () -> Unit,
    onFilterClick: () -> Unit,
    onSearchIconClick: () -> Unit
) {
    TopAppBar(
        backgroundColor = Color(0xFFFF7A00),
        contentColor = Color.White,
        modifier = Modifier.height(50.dp),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 6.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "이력",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp),
                    color = Color(0xFFFFFFFF),
                    fontFamily = ClipartKorea
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSortToggle) {
                    Icon(
                        painter = painterResource(id = if (isAscending) R.drawable.asc else R.drawable.dsc),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                IconButton(onClick = onFilterClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.filter),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                IconButton(onClick = onSearchIconClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.search),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun FilterBottomSheetContent() {
    var selectedStatus by remember { mutableStateOf("조치완료") }
    var selectedRisk by remember { mutableStateOf("주의") }
    
    var selectedEvent by remember { mutableStateOf("전체") }
    var isEventDropDownExpanded by remember { mutableStateOf(false) }
    
    var selectedArea by remember { mutableStateOf("전체") }
    var isAreaDropDownExpanded by remember { mutableStateOf(false) }

    var selectedActionByName by remember { mutableStateOf("전체") }
    var isActionByNameDropDownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(750.dp)
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        // 필터 설정 바 영역
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color(0xFFCDD1D5),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "필터 설정",
                fontFamily = Pretendard,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF33363D),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            IconButton(
                onClick = { 
                    selectedStatus = "조치완료"
                    selectedRisk = "주의"
                    selectedEvent = "전체"
                    selectedArea = "전체"
                    selectedActionByName = "전체"
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 55.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reset),
                    contentDescription = "Reset",
                    tint = Color.Unspecified
                )
            }
            Text(
                text = "초기화",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .clickable {
                        selectedStatus = "조치완료"
                        selectedRisk = "주의"
                        selectedEvent = "전체"
                        selectedArea = "전체"
                        selectedActionByName = "전체"
                    },
                fontFamily = Pretendard,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF97316)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 정렬기준 섹션
        Text(
            text = "정렬기준",
            fontFamily = Pretendard,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF58616A),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // 날짜 선택 박스 행
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 시작 날짜 박스
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 40.dp)
                    .border(1.dp, Color(0xFFCDD1D5), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "2026-05-07",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF131416)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.calendar2),
                        contentDescription = null,
                        tint = Color.Unspecified
                    )
                }
            }

            // underbar 아이콘
            Icon(
                painter = painterResource(id = R.drawable.underbar),
                contentDescription = null
            )

            // 종료 날짜 박스
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 40.dp)
                    .border(1.dp, Color(0xFFCDD1D5), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "2026-05-16",
                        fontFamily = Pretendard,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF33363D)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.calendar2),
                        contentDescription = null,
                        tint = Color.Unspecified
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 상태 섹션
        Text(
            text = "상태",
            fontFamily = Pretendard,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF58616A),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statusOptions = listOf("조치완료", "오탐처리")
            statusOptions.forEach { option ->
                val isSelected = selectedStatus == option
                Button(
                    onClick = { selectedStatus = option },
                    modifier = Modifier.size(width = 168.dp, height = 37.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isSelected) Color(0x1FFB923C) else Color(0xFFFFFFFF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFF97316) else Color(0xFFCDD1D5))
                ) {
                    Text(
                        text = option,
                        color = Color(0xFF131416),
                        fontFamily = Pretendard,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 위험도 섹션
        Text(
            text = "위험도",
            fontFamily = Pretendard,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF58616A),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val riskOptions = listOf("주의", "경고", "위험")
            riskOptions.forEach { option ->
                val isSelected = selectedRisk == option
                Button(
                    onClick = { selectedRisk = option },
                    modifier = Modifier.size(width = 110.dp, height = 40.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isSelected) Color(0x1FFB923C) else Color(0xFFFFFFFF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFF97316) else Color(0xFFCDD1D5))
                ) {
                    Text(
                        text = option,
                        color = Color(0xFF131416),
                        fontFamily = Pretendard,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Divider(color = Color(0xFFCDD1D5), thickness = 1.dp)

        Spacer(modifier = Modifier.height(28.dp))

        // 구역 및 조치자 섹션 (가로 배치 및 텍스트 왼쪽 끝 맞춤)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 구역 섹션
            Column {
                Text(
                    text = "구역",
                    fontFamily = Pretendard,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF58616A)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 45.dp)
                        .background(Color.White, shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFCDD1D5), RoundedCornerShape(8.dp))
                        .clickable { isAreaDropDownExpanded = true },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedArea,
                            fontFamily = Pretendard,
                            fontSize = 14.sp,
                            color = if (selectedArea == "전체") Color(0xFFB1B8BE) else Color(0xFF131416)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.dropbox),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(width = 14.dp, height = 9.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = isAreaDropDownExpanded,
                        onDismissRequest = { isAreaDropDownExpanded = false },
                        offset = DpOffset(x = 0.dp, y = 8.dp)
                    ) {
                        val areaOptions = listOf("A구역", "B구역", "C구역", "D구역")
                        areaOptions.forEach { option ->
                            DropdownMenuItem(onClick = {
                                selectedArea = option
                                isAreaDropDownExpanded = false
                            }) {
                                Text(text = option, fontFamily = Pretendard)
                            }
                        }
                    }
                }
            }

            // 조치자 섹션
            Column {
                Text(
                    text = "조치자",
                    fontFamily = Pretendard,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF58616A)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 45.dp)
                        .background(Color.White, shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFCDD1D5), RoundedCornerShape(8.dp))
                        .clickable { isActionByNameDropDownExpanded = true },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedActionByName,
                            fontFamily = Pretendard,
                            fontSize = 14.sp,
                            color = if (selectedActionByName == "전체") Color(0xFFB1B8BE) else Color(0xFF131416)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.dropbox),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(width = 14.dp, height = 9.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = isActionByNameDropDownExpanded,
                        onDismissRequest = { isActionByNameDropDownExpanded = false },
                        offset = DpOffset(x = 0.dp, y = 8.dp)
                    ) {
                        // 조치자 리스트 (요청에 따라 비워둠)
                        val actionOptions = listOf<String>()
                        actionOptions.forEach { option ->
                            DropdownMenuItem(onClick = {
                                selectedActionByName = option
                                isActionByNameDropDownExpanded = false
                            }) {
                                Text(text = option, fontFamily = Pretendard)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 이벤트유형 섹션
        Text(
            text = "이벤트유형",
            fontFamily = Pretendard,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF58616A),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // 이벤트유형 드롭박스 버튼
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth() // 너비 확장
                .height(45.dp)
                .background(Color.White, shape = RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFCDD1D5), RoundedCornerShape(8.dp))
                .clickable { isEventDropDownExpanded = true },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedEvent,
                    fontFamily = Pretendard,
                    fontSize = 14.sp,
                    color = if (selectedEvent == "전체") Color(0xFFB1B8BE) else Color(0xFF131416)
                )
                Icon(
                    painter = painterResource(id = R.drawable.dropbox), // 아이콘 변경
                    contentDescription = null,
                    tint = Color.Unspecified, // dropbox.xml의 strokeColor 등을 살리기 위해 Unspecified 권장
                    modifier = Modifier.size(width = 14.dp, height = 9.dp) // dropbox.xml의 크기에 맞춤
                )
            }

            DropdownMenu(
                expanded = isEventDropDownExpanded,
                onDismissRequest = { isEventDropDownExpanded = false },
                offset = DpOffset(x = 0.dp, y = 8.dp), // 아래로 펼쳐지도록 유도하는 오프셋 추가
                modifier = Modifier.fillMaxWidth(0.9f) // 드롭다운 너비도 어느 정도 확보
            ) {
                val eventOptions = listOf("안전모 미착용", "통로사고", "충돌사고", "운반사고", "화재사고", "협착사고", "쓰러짐")
                eventOptions.forEach { option ->
                    DropdownMenuItem(onClick = {
                        selectedEvent = option
                        isEventDropDownExpanded = false
                    }) {
                        Text(text = option, fontFamily = Pretendard)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 적용하기 버튼
        Button(
            onClick = { /* 적용 로직 */ },
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFFF97316)
            ),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
        ) {
            Text(
                text = "적용하기",
                color = Color.White,
                fontFamily = Pretendard,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HistoryItemFrame(data: HistoryEventData) {
    Box(
        modifier = Modifier
            .size(width = 350.dp, height = 140.dp)
            .background(Color.White, shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFCDD1D5), shape = RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 상단 영역 (80dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconRes = when (data.accidentType) {
                    "위험" -> R.drawable.danger_icon
                    "경고" -> R.drawable.warning_icon
                    "주의" -> R.drawable.caution_icon
                    else -> R.drawable.warning_icon
                }
                
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.location,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color(0xFF131416)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = data.content,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = Color(0xFF58616A)
                    )
                }
            }

            // 가로 경계선 (0xFFE6E8EA)
            Divider(
                color = Color(0xFFE6E8EA),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // 하단 영역 (60dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                // 조치자 영역
                Row(
                    modifier = Modifier
                        .width(140.dp)
                        .fillMaxHeight()
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.avatar),
                        contentDescription = null,
                        tint = Color.Unspecified,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(data.actionByName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = Pretendard, color = Color(0xFF58616A))
                }
                
                // 조치시간 영역
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("발생시간", fontSize = 11.sp, color = Color(0xFF58616A), fontFamily = Pretendard, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(data.occurrenceTime, fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFamily = Pretendard, color = Color(0xFF58616A))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("조치시간", fontSize = 11.sp, color = Color(0xFF58616A), fontFamily = Pretendard, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(data.actionTime, fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFamily = Pretendard, color = Color(0xFF58616A))
                    }
                }
            }
        }

        // 세로 경계선
        Divider(
            color = Color(0xFFCDD1D5),
            modifier = Modifier
                .offset(x = 140.dp, y = 90.dp)
                .width(1.dp)
                .height(40.dp)
        )
    }
}

@Composable
fun HistorySecondaryAppBar(selectedTab: String, onTabSelected: (String) -> Unit) {
    val tabs = listOf("AI감지", "오탐이력")
    TabRow(
        selectedTabIndex = tabs.indexOf(selectedTab),
        backgroundColor = Color(0xFFFFFFFF),
        contentColor = Color.White,
        modifier = Modifier.height(60.dp),
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(selectedTab)]), color = Color(0XFFF97316), height = 2.dp)
        },
        divider = {}
    ) {
        tabs.forEach { title ->
            Tab(
                selected = selectedTab == title,
                onClick = { onTabSelected(title) },
                text = {
                    Text(text = title, fontSize = 18.sp, fontFamily = Pretendard, color = if (selectedTab == title) Color(0xFFF97316) else Color(0xFFB1B8BE), fontWeight = if (selectedTab == title) FontWeight.Bold else FontWeight.Medium)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    HistoryScreen()
}

@Preview(showBackground = true, name = "필터 다이얼로그 프리뷰")
@Composable
fun FilterBottomSheetPreview() {
    Smart_Safety_ManagementTheme {
        FilterBottomSheetContent()
    }
}
