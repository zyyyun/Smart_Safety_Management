package com.example.smart_safety_management

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.smart_safety_management.ui.theme.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- 1. 데이터 모델 및 Enum ---

enum class EventStatus {
    PENDING,
    COMPLETED,
    FALSE_DETECTION
}

data class EventData(
    val id: Int,
    val accidentType: String,
    val location: String,
    val content: String,
    val occurrenceTime: String = "",
    val deviceName: String = "",
    val accuracy: String = ""
)

data class ProcessedEventData(
    val pending: List<EventData> = emptyList(),
    val completed: List<EventData> = emptyList(),
    val falseDetection: List<EventData> = emptyList(),
    val counts: Map<String, Int> = mapOf("전체" to 0, "위험" to 0, "경고" to 0, "주의" to 0)
)

// --- 2. 메인 화면 ---

@Composable
fun AIEventDetectScreen(onEventClick: (EventData) -> Unit = {}) {
    var selectedFilter by remember { mutableStateOf("전체") }
    // 2026-05-21 — Sprint B.2.3: 날짜 chip. default "오늘" 으로 시연·검단 PPT 때
    // 옛 detection_events 노이즈가 화면 상단을 가리지 않도록 함.
    var selectedDateFilter by remember { mutableStateOf("오늘") }

    // ✅ 서버 데이터 상태 관리
    var rawEvents by remember { mutableStateOf<List<DetectionEventDTO>>(emptyList()) }
    var hasUnreadNotifications by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var processedData by remember { mutableStateOf(ProcessedEventData()) }

    // ✅ [수정] 데이터 로드 로직을 함수로 분리 (재사용 및 즉시 호출용)
    val fetchData = remember {
        {
            val userId = UserSession.userId
            if (userId != null) {
                // 이벤트 목록 조회
                RetrofitClient.instance.getRecentDetectionEvents(userId).enqueue(object : Callback<GetDetectionEventsResponse> {
                    override fun onResponse(call: Call<GetDetectionEventsResponse>, response: Response<GetDetectionEventsResponse>) {
                        if (response.isSuccessful) {
                            rawEvents = response.body()?.events ?: emptyList()
                        }
                    }
                    override fun onFailure(call: Call<GetDetectionEventsResponse>, t: Throwable) {}
                })

                // 알림 상태 조회
                RetrofitClient.instance.getNotifications(userId).enqueue(object : Callback<GetNotificationsResponse> {
                    override fun onResponse(call: Call<GetNotificationsResponse>, response: Response<GetNotificationsResponse>) {
                        if (response.isSuccessful) {
                            val notifications = response.body()?.notifications ?: emptyList()
                            hasUnreadNotifications = notifications.any { !it.isRead }
                        }
                    }
                    override fun onFailure(call: Call<GetNotificationsResponse>, t: Throwable) {}
                })
            }
        }
    }

    // ✅ 서버에서 데이터 불러오기 (onResume 시점마다 갱신)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // ✅ [핵심 수정] 이미 RESUMED 상태인 경우 즉시 로드 (Navigation 복귀 시 이벤트 놓침 방지)
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            fetchData()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ✅ [최적화] 데이터 가공 로직을 백그라운드 스레드로 이동 (UI 버벅임 방지)
    // 2026-05-21 Sprint B.2: selectedDateFilter 도 의존성에 추가, 날짜 필터 적용 +
    // detectedAt DESC 정렬 (server-side ORDER BY 명시 부재 대비 클라이언트 safety net).
    LaunchedEffect(rawEvents, selectedDateFilter) {
        withContext(Dispatchers.Default) {
            val pending = mutableListOf<EventData>()
            val completed = mutableListOf<EventData>()
            val falseDetection = mutableListOf<EventData>()

            var countDanger = 0
            var countWarning = 0
            var countCaution = 0

            // SimpleDateFormat 재사용을 위한 인스턴스 생성 (반복문 밖에서 1회 생성)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            // Sprint B.2.3: KST 기준 날짜 필터 + B.2.2: detectedAt DESC 정렬
            val dateFiltered = filterEventsByDateRange(rawEvents, selectedDateFilter)
            val sorted = dateFiltered.sortedByDescending { it.detectedAt }

            sorted.forEach { dto ->
                val eventData = dto.toEventData(dateFormat)

                // 위험도 카운트 집계
                when (eventData.accidentType) {
                    "위험" -> countDanger++
                    "경고" -> countWarning++
                    "주의" -> countCaution++
                }

                // 상태별 분류
                if (dto.status.equals("PENDING", ignoreCase = true) || dto.status.equals("REQUESTED", ignoreCase = true)) {
                    pending.add(eventData)
                } else if (dto.status.equals("COMPLETED", ignoreCase = true)) {
                    completed.add(eventData)
                } else if (dto.status.equals("FALSE_POSITIVE", ignoreCase = true)) {
                    falseDetection.add(eventData)
                }
            }

            val newCounts = mapOf(
                "전체" to sorted.size,
                "위험" to countDanger,
                "경고" to countWarning,
                "주의" to countCaution
            )
            processedData = ProcessedEventData(pending, completed, falseDetection, newCounts)
        }
    }

    val filteredPendingEvents = if (selectedFilter == "전체") processedData.pending else processedData.pending.filter { it.accidentType == selectedFilter }
    val filteredCompletedEvents = if (selectedFilter == "전체") processedData.completed else processedData.completed.filter { it.accidentType == selectedFilter }
    val filteredFalseDetectionEvents = if (selectedFilter == "전체") processedData.falseDetection else processedData.falseDetection.filter { it.accidentType == selectedFilter }

    Smart_Safety_ManagementTheme {
        val topBarBackgroundColor = if (MaterialTheme.colors.isLight) MainOrange else GrayBackground
        val subTextColor = if (MaterialTheme.colors.isLight) TextGray60 else TextGray
        
        // ✅ [핵심] Scaffold 제거: 외부 Scaffold와 중복 패딩을 방지하기 위해 Column으로 레이아웃 구성
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(topBarBackgroundColor)
        ) {
            // 상단바 영역
            Column {
                MyTopAppBar(Color.Transparent, hasUnreadNotifications)
                MySecondaryTopAppBar(selectedFilter, processedData.counts, Color.Transparent) { newFilter ->
                    selectedFilter = newFilter
                }
                // 2026-05-21 Sprint B.2.3: 기간 필터 chip row
                DateFilterRow(selectedDateFilter, Color.Transparent) { newDateFilter ->
                    selectedDateFilter = newDateFilter
                }
            }

            // 콘텐츠 영역: weight(1f)를 주어 바텀바 바로 위까지 공간을 모두 사용
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = MaterialTheme.colors.onPrimary,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 상단 여백과 날짜 (기존 레이아웃 유지 위해 그룹화)
                    item {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            CurrentDateText()
                        }
                    }

                    item {
                        Text(text = "조치대기", fontWeight = FontWeight.Medium, color = subTextColor, modifier = Modifier.padding(8.dp))
                    }
                    if (filteredPendingEvents.isEmpty()) {
                        item { NoEventText() }
                    } else {
                        items(filteredPendingEvents, key = { it.id }) { event ->
                            EventItem(event, EventStatus.PENDING, onEventClick)
                        }
                    }

                    item {
                        Text(text = "조치완료", fontWeight = FontWeight.Medium, color = subTextColor, modifier = Modifier.padding(8.dp))
                    }
                    if (filteredCompletedEvents.isEmpty()) {
                        item { NoEventText() }
                    } else {
                        items(filteredCompletedEvents, key = { it.id }) { event ->
                            EventItem(event, EventStatus.COMPLETED, onEventClick)
                        }
                    }

                    item {
                        Text(text = "오탐처리", fontWeight = FontWeight.Medium, color = subTextColor, modifier = Modifier.padding(8.dp))
                    }
                    if (filteredFalseDetectionEvents.isEmpty()) {
                        item { NoEventText() }
                    } else {
                        items(filteredFalseDetectionEvents, key = { it.id }) { event ->
                            EventItem(event, EventStatus.FALSE_DETECTION, onEventClick)
                        }
                    }

                    // 리스트 끝 추가 여백
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NoEventText() {
    val historyColor = if(MaterialTheme.colors.isLight) TextGray60 else TextGray
    val textColor = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
    
    Text(
        text = buildAnnotatedString {
            append("지난 내역은 ")
            withStyle(style = SpanStyle(color = historyColor, fontWeight = FontWeight.Bold)) {
                append("이력")
            }
            append(" 탭에서 확인하세요.")
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        textAlign = TextAlign.Center,
        fontSize = 14.sp,
        color = textColor
    )
}

// --- 3. UI 컴포넌트 (기존과 동일) ---

@Composable
fun EventItem(event: EventData, status: EventStatus, onEventClick: (EventData) -> Unit = {}) {
    val isPending = status == EventStatus.PENDING
    val isLight = MaterialTheme.colors.isLight
    val alphaval = if (isLight) 0.1f else 0.36f

    val buttonColor = if (isPending) {
        when (event.accidentType) {
            "위험" -> Color(0xFFEF4444).copy(alphaval)
            "경고" -> Color(0xFFFB923C).copy(alphaval)
            "주의" -> Color(0xFFFFB114).copy(alphaval)
            else -> TextGray5
        }
    } else {
        if (isLight) TextGray5 else TextGray20
    }

    val borderColor = if (isPending) {
        when (event.accidentType) {
            "위험" -> Color(0xFFEF4444).copy(alphaval)
            "경고" -> Color(0xFFFB923C).copy(alphaval)
            "주의" -> Color(0xFFFFB114).copy(alphaval)
            else -> MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
        }
    } else {
        if (isLight) Lightgray else GrayBackground
    }

    val iconTint = if (isPending) Color.Unspecified else if (isLight) TextLight else TextGray30
    val textColor = if (isPending) (if(isLight) TextGray60 else TextGray) else (if(isLight) TextLight else TextGray30)
    val locationColor = if (isPending) (if(isLight) TextGray20 else TextGray5) else (if(isLight) TextLight else TextGray30)

    Button(
        onClick = { onEventClick(event) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(83.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = buttonColor),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconRes = when (event.accidentType) {
                "위험" -> R.drawable.danger_icon
                "경고" -> R.drawable.warning_icon
                "주의" -> R.drawable.caution_icon
                else -> 0
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconRes != 0) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = event.accidentType,
                        modifier = Modifier.padding(end = 6.dp),
                        tint = iconTint
                    )
                }

                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = event.location, color = locationColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = event.content, color = textColor, fontWeight = FontWeight.Normal, fontSize = 14.sp)
                }
            }

            Text(
                text = event.occurrenceTime,
                fontSize = 12.sp,
                fontFamily = Pretendard,
                color = textColor,
                modifier = Modifier.align(Alignment.Top).offset(y = (-3).dp)
            )
        }
    }
}

@Composable
fun CurrentDateText() {
    val currentDate = Date()
    val formatter = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN)
    val formattedDate = formatter.format(currentDate)
    Text(
        text = formattedDate,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
        textAlign = TextAlign.Start,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface
    )
}

@Composable
fun MyTopAppBar(backgroundColor: Color, hasUnreadNotifications: Boolean) {
    val context = LocalContext.current
    TopAppBar(
        backgroundColor = backgroundColor,
        contentColor = Color.White,
        modifier = Modifier.height(50.dp),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().offset(y = 6.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "AI 이벤트 감지",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp),
                color = Color.White,
                fontFamily = ClipartKorea
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = {
                        val intent = Intent(context, NoticeActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Icon(painter = painterResource(id = R.drawable.alarm), contentDescription = "알림", tint = Color.White)
                    }
                    if (hasUnreadNotifications) {
                        Icon(
                            painter = painterResource(id = R.drawable.dot_icon),
                            contentDescription = null,
                            tint = Color(0xFFFFCE69),
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 7.dp).size(6.dp)
                        )
                    }
                }
                IconButton(onClick = {
                    val intent = Intent(context, SettingActivity::class.java)
                    context.startActivity(intent)
                }) {
                    Icon(painter = painterResource(id = R.drawable.setting), contentDescription = "설정", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun MySecondaryTopAppBar(selectedFilter: String, counts: Map<String, Int>, backgroundColor: Color, onFilterChange: (String) -> Unit) {
    TopAppBar(
        backgroundColor = backgroundColor,
        contentColor = Color.White,
        modifier = Modifier.height(47.dp),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            FilterButton("전체", counts["전체"] ?: 0, selectedFilter == "전체") { onFilterChange("전체") }
            FilterButton("위험", counts["위험"] ?: 0, selectedFilter == "위험") { onFilterChange("위험") }
            FilterButton("경고", counts["경고"] ?: 0, selectedFilter == "경고") { onFilterChange("경고") }
            FilterButton("주의", counts["주의"] ?: 0, selectedFilter == "주의") { onFilterChange("주의") }
        }
    }
}

// ✅ 헬퍼 함수: DTO -> EventData 변환
fun DetectionEventDTO.toEventData(formatter: SimpleDateFormat? = null): EventData {
    return EventData(
        id = this.eventId,
        accidentType = mapRiskLevel(this.riskLevel),
        location = this.installArea ?: "알 수 없음",
        content = "${this.eventName ?: "알 수 없는 이벤트"}이(가) 감지되었습니다.",
        occurrenceTime = calculateTimeAgo(this.detectedAt, formatter),
        deviceName = this.deviceName ?: "",
        accuracy = "${this.accuracy ?: 0}%"
    )
}

// ✅ 헬퍼 함수: 위험도 매핑
fun mapRiskLevel(level: String?): String {
    return when (level?.lowercase()) {
        "high", "위험", "danger" -> "위험"
        "medium", "경고", "warning" -> "경고"
        "low", "주의", "caution" -> "주의"
        else -> "주의"
    }
}

// ✅ 헬퍼 함수: 시간 계산
fun calculateTimeAgo(dateString: String, formatter: SimpleDateFormat? = null): String {
    try {
        val format = formatter ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = format.parse(dateString) ?: return ""
        val diff = System.currentTimeMillis() - date.time
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "방금 전"
            minutes < 60 -> "${minutes}분 전"
            hours < 24 -> "${hours}시간 전"
            else -> "${days}일 전"
        }
    } catch (e: Exception) {
        return dateString
    }
}

@Composable
fun FilterButton(text: String, count: Int, isSelected: Boolean, onClick: () -> Unit) {
    val isLight = MaterialTheme.colors.isLight
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.Transparent,
            contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
        ),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text, 
                fontSize = 18.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if(isLight) (if (isSelected) Color.White else SubOrange) else (if (isSelected) Color.White else TextMedium)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier.size(20.dp).background(
                    color = if (isLight) (if (isSelected) Color.White else SubOrange) else (if (isSelected) MainOrange.copy(alpha = 0.36f) else TextMedium),
                    shape = CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium ,
                    color = if (isLight) MainOrange else (if (isSelected) MainOrange else TextLight)
                )
            }
        }
    }
}

// ─── 2026-05-21 Sprint B.2.3: 날짜 필터 ───────────────────────────────────
//
// `detected_at` 는 서버에서 "yyyy-MM-dd HH:mm:ss" KST 형식 문자열로 옴
// (calculateTimeAgo 가 bare SimpleDateFormat 로 파싱 성공하는 게 증거).
// 날짜 부분 (앞 10자) 만 잘라 KST today 와 lexicographic 비교 → 정확.
//
// "오늘"   = 오늘 KST 00:00 ~ 23:59
// "어제"   = 어제 KST 00:00 ~ 23:59
// "최근 7일" = 오늘 포함 직전 7일
// "전체"   = 필터 없음
fun filterEventsByDateRange(events: List<DetectionEventDTO>, filter: String): List<DetectionEventDTO> {
    if (filter == "전체" || events.isEmpty()) return events

    val tz = TimeZone.getTimeZone("Asia/Seoul")
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = tz }
    val cal = Calendar.getInstance(tz)

    return when (filter) {
        "오늘" -> {
            val today = dateFmt.format(cal.time)
            events.filter { it.detectedAt.take(10) == today }
        }
        "어제" -> {
            cal.add(Calendar.DAY_OF_MONTH, -1)
            val yesterday = dateFmt.format(cal.time)
            events.filter { it.detectedAt.take(10) == yesterday }
        }
        "최근 7일" -> {
            cal.add(Calendar.DAY_OF_MONTH, -6) // 오늘 포함 7일
            val cutoff = dateFmt.format(cal.time)
            events.filter { it.detectedAt.take(10) >= cutoff }
        }
        else -> events
    }
}

@Composable
fun DateFilterRow(selected: String, backgroundColor: Color, onChange: (String) -> Unit) {
    TopAppBar(
        backgroundColor = backgroundColor,
        contentColor = Color.White,
        modifier = Modifier.height(40.dp),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "기간",
                color = if (MaterialTheme.colors.isLight) Color.White.copy(alpha = 0.85f) else TextLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 10.dp)
            )
            DateFilterChip("오늘", selected == "오늘") { onChange("오늘") }
            Spacer(Modifier.width(6.dp))
            DateFilterChip("어제", selected == "어제") { onChange("어제") }
            Spacer(Modifier.width(6.dp))
            DateFilterChip("최근 7일", selected == "최근 7일") { onChange("최근 7일") }
            Spacer(Modifier.width(6.dp))
            DateFilterChip("전체", selected == "전체") { onChange("전체") }
        }
    }
}

@Composable
fun DateFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val isLight = MaterialTheme.colors.isLight
    val bgColor = when {
        selected && isLight -> Color.White
        selected && !isLight -> MainOrange.copy(alpha = 0.36f)
        else -> Color.White.copy(alpha = 0.15f)
    }
    val textColor = when {
        selected && isLight -> MainOrange
        selected && !isLight -> Color.White
        else -> Color.White.copy(alpha = 0.85f)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier
            .clickable { onClick() }
            .height(26.dp),
        elevation = 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark", heightDp = 1000)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light", heightDp = 1000)
@Composable
fun AIEventDetectScreenPreview() {
    AIEventDetectScreen()
}
