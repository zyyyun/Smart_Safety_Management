package com.example.smart_safety_management

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.ui.theme.*
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
fun AIEventDetectScreen() {
    var selectedFilter by remember { mutableStateOf("전체") }

    val allPendingEvents = listOf(
        EventData(accidentType = "위험", location = "C구역 2열", content = "화재사고가 감지되었습니다."),
        EventData(accidentType = "경고", location = "C구역 2열", content = "쓰러짐이 감지되었습니다."),
        EventData(accidentType = "주의", location = "C구역 2열", content = "이동경로 미정돈이 감지되었습니다.")
    )
    val allCompletedEvents = listOf(
        EventData(accidentType = "주의", location = "C구역 2열", content = "안전고리 미착용이 감지되었습니다."),
        EventData(accidentType = "경고", location = "C구역 2열", content = "안전고리 미착용이 감지되었습니다.")
    )
    val allFalseDetectionEvents = listOf(
        EventData(accidentType = "경고", location = "C구역 2열", content = "안전고리 미착용이 감지되었습니다.")
    )

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
        // 테마 내부로 이동하여 테마 변경을 감지합니다.
        val topBarBackgroundColor = if (MaterialTheme.colors.isLight) MainOrange else GrayBackground
        val subTextColor = if (MaterialTheme.colors.isLight) TextGray60 else TextGray
        Scaffold(
            topBar = {
                Surface(
                    color = topBarBackgroundColor,
                    elevation = 0.dp
                ) {
                    Column {
                        MyTopAppBar(Color.Transparent)
                        MySecondaryTopAppBar(selectedFilter, counts, Color.Transparent) { newFilter ->
                            selectedFilter = newFilter
                        }
                    }
                }
            },
            bottomBar = { MyBottomNavigation("nav_ai") },
            backgroundColor = topBarBackgroundColor // 상단 라운드 코너 배경을 일치시킵니다.
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colors.onPrimary,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    CurrentDateText()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "조치대기",
                            fontWeight = FontWeight.Medium,
                            color = subTextColor,
                            modifier = Modifier.padding(8.dp)
                        )
                        if (filteredPendingEvents.isEmpty()) {
                            Text(
                                text = "지난 내역은 이력 탭에서 확인하세요.",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            filteredPendingEvents.forEach { event ->
                                EventItem(event, EventStatus.PENDING)
                            }
                        }

                        Text(
                            text = "조치완료",
                            fontWeight = FontWeight.Medium,
                            color = subTextColor,
                            modifier = Modifier.padding(8.dp)
                        )
                        if (filteredCompletedEvents.isEmpty()) {
                            Text(
                                text = "지난 내역은 이력 탭에서 확인하세요.",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            filteredCompletedEvents.forEach { event ->
                                EventItem(event, EventStatus.COMPLETED)
                            }
                        }

                        Text(
                            text = "오탐처리",
                            fontWeight = FontWeight.Medium,
                            color = subTextColor,
                            modifier = Modifier.padding(8.dp)
                        )
                        if (filteredFalseDetectionEvents.isEmpty()) {
                            Text(
                                text = "지난 내역은 이력 탭에서 확인하세요.",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            filteredFalseDetectionEvents.forEach { event ->
                                EventItem(event, EventStatus.FALSE_DETECTION)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 3. UI 컴포넌트 ---

@Composable
fun EventItem(event: EventData, status: EventStatus) {
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
        if (isLight ) TextGray5 else TextGray20
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
    val textColor = if (isPending)
            // 조치 이전 라이트 / 다크 텍스트
        if(isLight) TextGray60 else TextGray
            // 조치 완료 라이트 / 다크 텍스트
    else if(isLight) TextLight else TextGray30

    val locationColor = if (isPending)
        if(isLight) TextGray20 else TextGray5
        else if(isLight) TextLight else TextGray30

    Button(
        onClick = { /* Handle event click */ },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = buttonColor),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconRes = when (event.accidentType) {
                "위험" -> R.drawable.danger_icon
                "경고" -> R.drawable.warning_icon
                "주의" -> R.drawable.caution_icon
                else -> 0
            }

            if (iconRes != 0) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = event.accidentType,
                    modifier = Modifier.padding(end = 8.dp).offset(x = (-5).dp, y = 5.dp),
                    tint = iconTint
                )
            }
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = event.location, 
                    color = locationColor, 
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = event.content, 
                    color = textColor,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
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
fun MyTopAppBar(backgroundColor: Color) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AI 이벤트 감지",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp),
                    color = Color.White,
                    fontFamily = ClipartKorea
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { }) {
                        Icon(painter = painterResource(id = R.drawable.alarm), contentDescription = "알림", tint = Color.White)
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.dot_icon),
                        contentDescription = null,
                        tint = Color(0xFFFFCE69),
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 7.dp).size(6.dp)
                    )
                }
                IconButton(onClick = { }) {
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
                color =
                    if(isLight)
                        if (isSelected) Color.White else SubOrange
                    else if (isSelected) Color.White else TextMedium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier.size(20.dp).background(color =
                    if (isLight) if (isSelected) Color.White else SubOrange
                    else if (isSelected) (MainOrange).copy(alpha = 0.36f) else TextMedium
                    , shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium ,
                    color =
                        if (isLight) MainOrange
                        else if (isSelected) MainOrange else TextLight
                )
            }
        }
    }
}

@Composable
fun MyBottomNavigation(selectedRoute: String = "nav_ai")
    {
    val isLight= MaterialTheme.colors.isLight
    val items = listOf(
        BottomNavItem("안전점검", R.drawable.home, "nav_home"),
        BottomNavItem("AI감지", R.drawable.ai, "nav_ai"),
        BottomNavItem("실시간상황", R.drawable.live, "nav_live"),
        BottomNavItem("이력", R.drawable.history, "nav_history"),
        BottomNavItem("위치정보", R.drawable.location, "nav_location")
    )
    BottomNavigation(
        backgroundColor = if(isLight) TextGray5 else TextGray20 ,
        elevation = 10.dp
    ) {
        items.forEach { item ->
            BottomNavigationItem(
                icon = { Icon(painter = painterResource(id = item.iconResId), contentDescription = item.title) },
                label = { Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                selected = selectedRoute == item.screenRoute,
                onClick = { /* 네비게이션 로직 */ },
                selectedContentColor = MaterialTheme.colors.primary,
                unselectedContentColor = if (isLight) GrayBorder else TextDark
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark",heightDp = 1000)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light",heightDp = 1000)
@Composable
fun AIEventDetectScreenPreview() {
    AIEventDetectScreen()
}
