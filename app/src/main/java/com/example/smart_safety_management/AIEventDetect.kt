package com.example.smart_safety_management

import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.ui.theme.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

data class BottomNavItem(
    val title: String, 
    @DrawableRes val iconResId: Int, 
    val screenRoute: String
)

// --- 2. 메인 화면 ---

@Composable
fun AIEventDetectScreen(onEventClick: (EventData) -> Unit = {}) {
    var selectedFilter by remember { mutableStateOf("전체") }

    // ✅ 서버 데이터 상태 관리
    var rawEvents by remember { mutableStateOf<List<DetectionEventDTO>>(emptyList()) }
    var hasUnreadNotifications by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // ✅ 서버에서 데이터 불러오기 (onResume 시점마다 갱신)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val userId = UserSession.userId
                if (userId != null) {
                    // 이벤트 목록 조회
                    RetrofitClient.instance.getDetectionEvents(userId).enqueue(object : Callback<GetDetectionEventsResponse> {
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
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ✅ DTO -> EventData 변환 및 분류
    val allPendingEvents = rawEvents
        .filter { it.status.equals("PENDING", ignoreCase = true) || it.status.equals("REQUESTED", ignoreCase = true) }
        .map { it.toEventData() }

    val allCompletedEvents = rawEvents
        .filter { it.status.equals("COMPLETED", ignoreCase = true) }
        .map { it.toEventData() }

    val allFalseDetectionEvents = rawEvents
        .filter { it.status.equals("FALSE_POSITIVE", ignoreCase = true) }
        .map { it.toEventData() }

    val allEvents = allPendingEvents + allCompletedEvents + allFalseDetectionEvents
    val counts = mapOf(
        "전체" to allEvents.size,
        "위험" to allEvents.count { it.accidentType == "위험" },
        "경고" to allEvents.count { it.accidentType == "경고" },
        "주의" to allEvents.count { it.accidentType == "주의" }
    )

    val filteredPendingEvents = if (selectedFilter == "전체") allPendingEvents else allPendingEvents.filter { it.accidentType == selectedFilter }
    val filteredCompletedEvents = if (selectedFilter == "전체") allCompletedEvents else allCompletedEvents.filter { it.accidentType == selectedFilter }
    val filteredFalseDetectionEvents = if (selectedFilter == "전체") allFalseDetectionEvents else allFalseDetectionEvents.filter { it.accidentType == selectedFilter }

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
                MySecondaryTopAppBar(selectedFilter, counts, Color.Transparent) { newFilter ->
                    selectedFilter = newFilter
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CurrentDateText()
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "조치대기", fontWeight = FontWeight.Medium, color = subTextColor, modifier = Modifier.padding(8.dp))
                        if (filteredPendingEvents.isEmpty()) {
                            NoEventText()
                        } else {
                            filteredPendingEvents.forEach { event ->
                                EventItem(event, EventStatus.PENDING, onEventClick)
                            }
                        }

                        Text(text = "조치완료", fontWeight = FontWeight.Medium, color = subTextColor, modifier = Modifier.padding(8.dp))
                        if (filteredCompletedEvents.isEmpty()) {
                            NoEventText()
                        } else {
                            filteredCompletedEvents.forEach { event ->
                                EventItem(event, EventStatus.COMPLETED, onEventClick)
                            }
                        }

                        Text(text = "오탐처리", fontWeight = FontWeight.Medium, color = subTextColor, modifier = Modifier.padding(8.dp))
                        if (filteredFalseDetectionEvents.isEmpty()) {
                            NoEventText()
                        } else {
                            filteredFalseDetectionEvents.forEach { event ->
                                EventItem(event, EventStatus.FALSE_DETECTION, onEventClick)
                            }
                        }
                        
                        // 리스트 끝 추가 여백
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
fun DetectionEventDTO.toEventData(): EventData {
    return EventData(
        id = this.eventId,
        accidentType = mapRiskLevel(this.riskLevel),
        location = this.installArea ?: "알 수 없음",
        content = "${this.eventName ?: "알 수 없는 이벤트"}가 감지되었습니다.",
        occurrenceTime = calculateTimeAgo(this.detectedAt),
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
fun calculateTimeAgo(dateString: String): String {
    try {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = format.parse(dateString) ?: return ""
        val diff = Date().time - date.time
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

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark", heightDp = 1000)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light", heightDp = 1000)
@Composable
fun AIEventDetectScreenPreview() {
    AIEventDetectScreen()
}
