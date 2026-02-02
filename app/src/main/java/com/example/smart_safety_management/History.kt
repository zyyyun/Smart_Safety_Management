package com.example.smart_safety_management

import android.content.res.Configuration
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.example.smart_safety_management.ui.theme.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    var isAscending by remember { mutableStateOf(false) } // 최신순 기본
    var selectedTab by remember { mutableStateOf("AI감지") }
    
    // 검색 모드 및 쿼리 상태 관리
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 상세 필터 상태 관리
    var filterStatus by remember { mutableStateOf("전체") }
    var filterRisk by remember { mutableStateOf("전체") }
    var filterArea by remember { mutableStateOf("전체") }
    var filterWorker by remember { mutableStateOf("전체") }
    var filterEvent by remember { mutableStateOf("전체") }
    
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val coroutineScope = rememberCoroutineScope()

    // 서버 데이터 상태
    var events by remember { mutableStateOf<List<DetectionEventDTO>>(emptyList()) }
    val userId = UserSession.userId

    // 데이터 불러오기
    LaunchedEffect(Unit) {
        if (!userId.isNullOrEmpty()) {
            RetrofitClient.instance.getDetectionEvents(userId).enqueue(object : Callback<GetDetectionEventsResponse> {
                override fun onResponse(call: Call<GetDetectionEventsResponse>, response: Response<GetDetectionEventsResponse>) {
                    if (response.isSuccessful) {
                        events = response.body()?.events ?: emptyList()
                    }
                }
                override fun onFailure(call: Call<GetDetectionEventsResponse>, t: Throwable) {
                    Log.e("HistoryScreen", "Failed to fetch events", t)
                }
            })
        }
    }

    // 필터링 및 정렬 로직
    val filteredEvents = events.filter { event ->
        // 1. 탭 필터링
        val tabCondition = when (selectedTab) {
            "AI감지" -> true // 모든 데이터 표시
            "오탐이력" -> event.status.equals("FALSE_POSITIVE", ignoreCase = true)
            else -> false
        }
        // 2. 검색 필터링
        val searchCondition = if (searchQuery.isNotEmpty()) {
            (event.eventName?.contains(searchQuery, ignoreCase = true) == true) ||
            (event.installArea?.contains(searchQuery, ignoreCase = true) == true)
        } else true
        // 3. 상세 필터링
        val statusCondition = when (filterStatus) {
            "미조치" -> event.status == "DETECTED" || event.status == "REQUESTED"
            "조치완료" -> event.status == "COMPLETED" || event.status == "FALSE_POSITIVE"
            else -> true // "전체"
        }
        val riskCondition = if (filterRisk == "전체") true else mapRiskLevel(event.riskLevel) == filterRisk
        val areaCondition = if (filterArea == "전체") true else event.installArea == filterArea
        val workerCondition = if (filterWorker == "전체") true else event.workerName == filterWorker
        val eventCondition = if (filterEvent == "전체") true else event.eventName == filterEvent

        tabCondition && searchCondition && statusCondition && riskCondition && areaCondition && workerCondition && eventCondition
    }.sortedWith(if (isAscending) compareBy { it.detectedAt } else compareByDescending { it.detectedAt })

    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val topBarBackgroundColor = if (isLight) MainOrange else GrayBackground

        ModalBottomSheetLayout(
            sheetState = sheetState,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetBackgroundColor = MaterialTheme.colors.surface,
            sheetContent = {
                // 현재 로드된 이벤트에서 구역 목록 추출
                val availableAreas = remember(events) {
                    listOf("전체") + events.mapNotNull { it.installArea }.distinct().sorted()
                }
                FilterBottomSheetContent(
                    userId = userId ?: "",
                    availableAreas = availableAreas,
                    initialStatus = filterStatus,
                    initialRisk = filterRisk,
                    initialArea = filterArea,
                    initialWorker = filterWorker,
                    initialEvent = filterEvent,
                    onApply = { s, r, a, w, e ->
                        filterStatus = s; filterRisk = r; filterArea = a; filterWorker = w; filterEvent = e
                        coroutineScope.launch { sheetState.hide() }
                    }
                )
            }
        ) {
            // ✅ [핵심] Scaffold 제거: Activity의 Scaffold와 중복되지 않도록 Column으로 레이아웃 구성
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(topBarBackgroundColor)
                    .background(Color.Transparent)
            ) {
                // 1. 상단바 영역
                Column {
                    HistoryTopAppBar(
                        isAscending = isAscending,
                        onSortToggle = { isAscending = !isAscending },
                        onFilterClick = {
                            coroutineScope.launch {
                                sheetState.show()
                            }
                        },
                        onSearchIconClick = { isSearchMode = !isSearchMode },
                        backgroundColor = Color.Transparent
                    )
                    if (isSearchMode) {
                        HistorySearchBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            width = 350.dp 
                        )
                        Divider(color = if (isLight) GrayBorder else TextDark, thickness = 1.dp)
                    }
                    HistorySecondaryAppBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }

                // 2. 콘텐츠 영역: weight(1f)를 주어 바텀바 바로 위까지 공간을 모두 사용
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    color = MaterialTheme.colors.onPrimary,
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
                        filteredEvents.forEach { dto ->
                            val historyData = HistoryEventData(
                                accidentType = mapRiskLevel(dto.riskLevel),
                                location = dto.installArea ?: "위치 정보 없음",
                                content = "${dto.eventName ?: "이벤트"}가 감지되었습니다.",
                                occurrenceTime = dto.detectedAt,
                                actionByName = dto.workerName ?: "",
                                actionTime = dto.actionTime ?: ""
                            )
                            HistoryItemFrame(historyData)
                        }
                        // 리스트 끝 여백
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

// ... 나머지 컴포저블들은 기존과 동일 (생략 없이 전체 유지) ...

@Composable
fun HistorySearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    width: Dp = Dp.Unspecified 
) {
    val focusManager = LocalFocusManager.current
    val isLight = MaterialTheme.colors.isLight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center 
    ) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth())
                .height(52.dp)
                .border(1.dp, if (isLight) GrayBorder else TextDark, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp), 
            placeholder = { 
                Text(
                    "검색하세요", 
                    color = TextLight,
                    fontSize = 18.sp,
                    fontFamily = Pretendard
                ) 
            },
            textStyle = TextStyle(
                color = MaterialTheme.colors.onSurface,
                fontSize = 14.sp,
                fontFamily = Pretendard
            ),
            singleLine = true,
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.search),
                    contentDescription = null,
                    tint = TextMedium
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
                cursorColor = MainOrange,
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
    onSearchIconClick: () -> Unit,
    backgroundColor: Color
) {
    TopAppBar(
        backgroundColor = backgroundColor,
        contentColor = Color.White,
        modifier = Modifier.height(50.dp),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    color = Color.White,
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
fun FilterBottomSheetContent(
    userId: String,
    availableAreas: List<String>,
    initialStatus: String,
    initialRisk: String,
    initialArea: String,
    initialWorker: String,
    initialEvent: String,
    onApply: (String, String, String, String, String) -> Unit
) {
    var selectedStatus by remember(initialStatus) { mutableStateOf(if (initialStatus == "전체") "미조치" else initialStatus) }
    var selectedRisk by remember(initialRisk) { mutableStateOf(if (initialRisk == "전체") "주의" else initialRisk) }
    var selectedEvent by remember(initialEvent) { mutableStateOf(initialEvent) }
    var isEventDropDownExpanded by remember { mutableStateOf(false) }
    var selectedArea by remember(initialArea) { mutableStateOf(initialArea) }
    var isAreaDropDownExpanded by remember { mutableStateOf(false) }
    var selectedActionByName by remember(initialWorker) { mutableStateOf(initialWorker) }
    var isActionByNameDropDownExpanded by remember { mutableStateOf(false) }

    val isLight = MaterialTheme.colors.isLight
    val CategoryColor = if (isLight) TextGray60 else TextGray
    val toptextColor = if (isLight) TextDark else GrayBorder
    val borderColor = if (isLight) GrayBorder else TextDark
    val textColor = if (isLight) TextGray20 else TextGray5
    val bgColor = if (isLight) Color.White else TextGray20
    val dropboxBorder = if (isLight) TextGray5 else TextGray20
    val placeholderColor = if (isLight) TextLight else TextGray30
    val alphavalue = if (isLight) 0.1f else 0.36f
    val selectAlpha = if (isLight) 0.12f else 0.36f

    val density = LocalDensity.current
    var areaBoxWidth by remember { mutableStateOf(0) }
    var actionByBoxWidth by remember { mutableStateOf(0) }
    var eventBoxWidth by remember { mutableStateOf(0) }

    // 서버 데이터 상태
    var workerOptions by remember { mutableStateOf(listOf("전체")) }
    var eventOptions by remember { mutableStateOf(listOf("전체")) }

    // 날짜 계산 (이번 달 1일 ~ 말일)
    val (startDate, endDate) = remember {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        // 이번 달 1일
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = sdf.format(cal.time)
        
        // 이번 달 말일
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = sdf.format(cal.time)
        
        start to end
    }

    LaunchedEffect(Unit) {
        // 조치자(그룹 멤버) 목록 가져오기
        RetrofitClient.instance.getGroupMembers(userId).enqueue(object : Callback<GroupMembersResponse> {
            override fun onResponse(call: Call<GroupMembersResponse>, response: Response<GroupMembersResponse>) {
                if (response.isSuccessful) {
                    workerOptions = listOf("전체") + (response.body()?.members ?: emptyList())
                }
            }
            override fun onFailure(call: Call<GroupMembersResponse>, t: Throwable) {}
        })
        // 이벤트 유형 목록 가져오기
        RetrofitClient.instance.getEventTypes().enqueue(object : Callback<EventTypesResponse> {
            override fun onResponse(call: Call<EventTypesResponse>, response: Response<EventTypesResponse>) {
                if (response.isSuccessful) {
                    eventOptions = listOf("전체") + (response.body()?.event_types ?: emptyList())
                }
            }
            override fun onFailure(call: Call<EventTypesResponse>, t: Throwable) {}
        })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(750.dp)
            .background(MaterialTheme.colors.onPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.height(4.dp).width(60.dp)
            .background(borderColor,shape = RoundedCornerShape(100.dp))
            .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.fillMaxHeight())
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = borderColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = "필터 설정", fontFamily = Pretendard, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = toptextColor, modifier = Modifier.padding(horizontal = 24.dp))
            IconButton(onClick = { 
                selectedStatus = "미조치"
                selectedRisk = "주의"
                selectedEvent = "전체"
                selectedArea = "전체"
                selectedActionByName = "전체"
            }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 55.dp)) {
                Icon(painter = painterResource(id = R.drawable.reset), contentDescription = "Reset", tint = Color.Unspecified)
            }
            Text(text = "초기화", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).clickable { 
                selectedStatus = "미조치"; selectedRisk = "주의"; selectedEvent = "전체"; selectedArea = "전체"; selectedActionByName = "전체"
            }, fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MainOrange)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "정렬기준", fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CategoryColor, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.height(40.dp).weight(1f).border(1.dp, borderColor, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = startDate, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = textColor)
                    Icon(painter = painterResource(id = R.drawable.calendar2), contentDescription = null, tint = toptextColor)
                }
            }
            Spacer(modifier = Modifier.width(15.dp))
            Icon(painter = painterResource(id = R.drawable.underbar), contentDescription = null, tint = if (isLight) Color.Unspecified else TextGray)
            Spacer(modifier = Modifier.width(15.dp))
            Box(modifier = Modifier.height(40.dp).weight(1f).border(1.dp, borderColor, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = endDate, fontFamily = Pretendard, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Icon(painter = painterResource(id = R.drawable.calendar2), contentDescription = null, tint = toptextColor)
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(text = "상태", fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CategoryColor, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val statusOptions = listOf("미조치", "조치완료")
            statusOptions.forEach { option ->
                val isSelected = selectedStatus == option
                Button(onClick = { selectedStatus = option }, modifier = Modifier.height(37.dp).weight(1f), colors = ButtonDefaults.buttonColors(backgroundColor = if (isSelected) MainOrange.copy(alphavalue) else bgColor), shape = RoundedCornerShape(8.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp), border = BorderStroke(1.dp, if (isSelected) MainOrange else borderColor)) {
                    Text(text = option, color = toptextColor, fontFamily = Pretendard, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp)); Text(text = "위험도", fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CategoryColor, modifier = Modifier.padding(horizontal = 24.dp)); Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val riskOptions = listOf("주의", "경고", "위험")
            riskOptions.forEach { option ->
                val isSelected = selectedRisk == option
                Button(onClick = { selectedRisk = option }, modifier = Modifier.height(40.dp).weight(1f), colors = ButtonDefaults.buttonColors(backgroundColor = if (isSelected) MainOrange.copy(alphavalue) else bgColor), shape = RoundedCornerShape(8.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp), border = BorderStroke(1.dp, if (isSelected) MainOrange else borderColor)) {
                    Text(text = option, color = toptextColor, fontFamily = Pretendard, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp)); Divider(color = borderColor, thickness = 1.dp); Spacer(modifier = Modifier.height(28.dp))
        
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "구역", fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CategoryColor)
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .onSizeChanged { areaBoxWidth = it.width }
                    .background(color = bgColor, shape = RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable { isAreaDropDownExpanded = true }, 
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = selectedArea, fontFamily = Pretendard, fontSize = 14.sp, color = if (selectedArea == "전체") placeholderColor else textColor)
                        Icon(painter = painterResource(id = R.drawable.dropbox), contentDescription = null, tint = if (isLight) Color.Unspecified else TextGray, modifier = Modifier.size(width = 14.dp, height = 9.dp))
                    }
                    MaterialTheme(
                        colors = MaterialTheme.colors.copy(surface = bgColor),
                        shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenu(
                            expanded = isAreaDropDownExpanded, 
                            onDismissRequest = { isAreaDropDownExpanded = false }, 
                            offset = DpOffset(x = 0.dp, y = 8.dp), 
                            modifier = Modifier.width(with(density) { areaBoxWidth.toDp() }).background(bgColor)
                        ) {
                            availableAreas.forEachIndexed { index, option ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                DropdownMenuItem(
                                    onClick = { selectedArea = option; isAreaDropDownExpanded = false },
                                    interactionSource = interactionSource,
                                    modifier = Modifier.background(if (isPressed) MainOrange.copy(alpha = selectAlpha) else bgColor)
                                ) { 
                                    Text(text = option, fontFamily = Pretendard, color = textColor) 
                                }
                                if (index < availableAreas.size - 1) Divider(color = dropboxBorder, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "조치자", fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CategoryColor)
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .onSizeChanged { actionByBoxWidth = it.width }
                    .background(bgColor, shape = RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable { isActionByNameDropDownExpanded = true }, 
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = selectedActionByName, fontFamily = Pretendard, fontSize = 14.sp, color = if (selectedActionByName == "전체") placeholderColor else textColor)
                        Icon(painter = painterResource(id = R.drawable.dropbox), contentDescription = null, tint = if (isLight) Color.Unspecified else TextGray, modifier = Modifier.size(width = 14.dp, height = 9.dp))
                    }
                    MaterialTheme(
                        colors = MaterialTheme.colors.copy(surface = bgColor),
                        shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenu(
                            expanded = isActionByNameDropDownExpanded, 
                            onDismissRequest = { isActionByNameDropDownExpanded = false }, 
                            offset = DpOffset(x = 0.dp, y = 8.dp), 
                            modifier = Modifier.width(with(density) { actionByBoxWidth.toDp() }).background(bgColor)
                        ) {
                            workerOptions.forEachIndexed { index, option ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                DropdownMenuItem(
                                    onClick = { selectedActionByName = option; isActionByNameDropDownExpanded = false },
                                    interactionSource = interactionSource,
                                    modifier = Modifier.background(if (isPressed) MainOrange.copy(alpha = selectAlpha) else bgColor)
                                ) { 
                                    Text(text = option, fontFamily = Pretendard, color = textColor) 
                                }
                                if (index < workerOptions.size - 1) Divider(color = dropboxBorder, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp)); Text(text = "이벤트유형", fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CategoryColor, modifier = Modifier.padding(horizontal = 24.dp)); Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(45.dp)
            .onSizeChanged { eventBoxWidth = it.width }
            .background(bgColor, shape = RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { isEventDropDownExpanded = true }, 
            contentAlignment = Alignment.CenterStart
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = selectedEvent, fontFamily = Pretendard, fontSize = 14.sp, color = if (selectedEvent == "전체") placeholderColor else textColor)
                Icon(painter = painterResource(id = R.drawable.dropbox), contentDescription = null, tint = if (isLight) Color.Unspecified else TextGray, modifier = Modifier.size(width = 14.dp, height = 9.dp))
            }
            MaterialTheme(
                colors = MaterialTheme.colors.copy(surface = bgColor),
                shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(8.dp))
            ) {
                DropdownMenu(
                    expanded = isEventDropDownExpanded, 
                    onDismissRequest = { isEventDropDownExpanded = false }, 
                    offset = DpOffset(x = 0.dp, y = 8.dp), 
                    modifier = Modifier.width(with(density) { eventBoxWidth.toDp() }).background(bgColor)
                ) {
                    eventOptions.forEachIndexed { index, option ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        DropdownMenuItem(
                            onClick = { selectedEvent = option; isEventDropDownExpanded = false },
                            interactionSource = interactionSource,
                            modifier = Modifier.background(if (isPressed) MainOrange.copy(alpha = selectAlpha) else bgColor)
                        ) { 
                            Text(text = option, fontFamily = Pretendard, color = textColor) 
                        }
                        if (index < eventOptions.size - 1) Divider(color = dropboxBorder, thickness = 1.dp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp)); Button(onClick = { 
            onApply(selectedStatus, selectedRisk, selectedArea, selectedActionByName, selectedEvent)
        }, modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(backgroundColor = MainOrange), shape = RoundedCornerShape(8.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
            Text(text = "적용하기", color = MaterialTheme.colors.onPrimary, fontFamily = Pretendard, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HistoryItemFrame(data: HistoryEventData) {
    val isLight = MaterialTheme.colors.isLight
    val borderColor = if (isLight) GrayBorder else TextDark
    val textColor = if (isLight) TextGray20 else TextGray5
    val subTextColor = if (isLight) TextGray60 else TextGray

    Box(modifier = Modifier.size(width = 350.dp, height = 140.dp).background(MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(12.dp)).border(1.dp, borderColor, shape = RoundedCornerShape(12.dp))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().height(80.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val iconRes = when (data.accidentType) {
                    "위험" -> R.drawable.danger_icon
                    "경고" -> R.drawable.warning_icon
                    "주의" -> R.drawable.caution_icon
                    else -> R.drawable.warning_icon
                }
                Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = Color.Unspecified)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = data.location, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textColor)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = data.content, fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = subTextColor)
                }
            }
            Divider(color = if (isLight) Lightgray else Color.White.copy(alpha = 0.05f), thickness = 1.dp, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Row(modifier = Modifier.width(140.dp).fillMaxHeight().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(id = R.drawable.avatar), contentDescription = null, tint = Color.Unspecified)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(data.actionByName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = Pretendard, color = subTextColor)
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp), verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("발생시간", fontSize = 11.sp, color = subTextColor, fontFamily = Pretendard, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp)); Text(data.occurrenceTime, fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFamily = Pretendard, color = subTextColor)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("조치시간", fontSize = 11.sp, color = subTextColor, fontFamily = Pretendard, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp)); Text(data.actionTime, fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFamily = Pretendard, color = subTextColor)
                    }
                }
            }
        }
        Divider(color = borderColor, modifier = Modifier.offset(x = 140.dp, y = 90.dp).width(1.dp).height(40.dp))
    }
}

@Composable
fun HistorySecondaryAppBar(selectedTab: String, onTabSelected: (String) -> Unit) {
    val tabs = listOf("AI감지", "오탐이력")
    val isLight = MaterialTheme.colors.isLight
    val categoryBackgroundColor = MaterialTheme.colors.onPrimary
    val textColor = if(isLight) TextLight else TextGray30
    val borderColor = if (isLight) GrayBorder else TextDark
    
    TabRow(
        selectedTabIndex = tabs.indexOf(selectedTab),
        backgroundColor = categoryBackgroundColor,
        contentColor = Color.White,
        modifier = Modifier.height(60.dp),
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(selectedTab)]),
                color = MainOrange,
                height = 2.dp
            )
        },
        divider = {
            Divider(color = borderColor, thickness = 1.dp)
        }
    ) {
        tabs.forEach { title ->
            Tab(
                selected = selectedTab == title,
                onClick = { onTabSelected(title) },
                text = {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontFamily = Pretendard,
                        color = if (selectedTab == title) MainOrange else textColor,
                        fontWeight = if (selectedTab == title) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun HistoryScreenPreview() {
    HistoryScreen()
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Filter BottomSheet - Light")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Filter BottomSheet - Dark")
@Composable
fun FilterBottomSheetPreview() {
    Smart_Safety_ManagementTheme {
        FilterBottomSheetContent(
            userId = "test",
            availableAreas = listOf("전체", "A구역", "B구역"),
            initialStatus = "전체",
            initialRisk = "전체",
            initialArea = "전체",
            initialWorker = "전체",
            initialEvent = "전체",
            onApply = { _, _, _, _, _ -> }
        )
    }
}