package com.example.smart_safety_management

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EventStatus {
    PENDING,
    COMPLETED,
    FALSE_DETECTION
}

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

    val filteredPendingEvents = if (selectedFilter == "전체") allPendingEvents else allPendingEvents.filter { it.accidentType == selectedFilter }
    val filteredCompletedEvents = if (selectedFilter == "전체") allCompletedEvents else allCompletedEvents.filter { it.accidentType == selectedFilter }
    val filteredFalseDetectionEvents = if (selectedFilter == "전체") allFalseDetectionEvents else allFalseDetectionEvents.filter { it.accidentType == selectedFilter }

    Smart_Safety_ManagementTheme {
        Scaffold(
            topBar = {
                Column {
                    MyTopAppBar()
                    MySecondaryTopAppBar(selectedFilter) { newFilter ->
                        selectedFilter = newFilter
                    }
                }
            },
            bottomBar = { MyBottomNavigation() },
            backgroundColor = Color(0xFFFF7A00) // Scaffold 배경을 상단바와 동일한 색으로 설정
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), // 상단바/하단바 영역을 제외한 나머지 공간을 채움
                color = Color.White,
                // 상단 모서리만 둥글게 처리하여 곡선 효과를 줍니다.
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                // 향후 콘텐츠를 담을 Column
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
                        Text("조치대기", fontWeight = FontWeight.Medium, color = Color(0xFF58616A))
                        if (filteredPendingEvents.isEmpty()) {
                            Text(
                                text = "지난 내역은 이력 탭에서 확인하세요.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        } else {
                            filteredPendingEvents.forEach { event ->
                                EventItem(event, EventStatus.PENDING)
                            }
                        }

                        Text("조치완료", fontWeight = FontWeight.Medium, color = Color(0xFF58616A))
                        if (filteredCompletedEvents.isEmpty()) {
                            Text(
                                text = "지난 내역은 이력 탭에서 확인하세요.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        } else {
                            filteredCompletedEvents.forEach { event ->
                                EventItem(event, EventStatus.COMPLETED)
                            }
                        }

                        Text("오탐처리", fontWeight = FontWeight.Medium, color = Color(0xFF58616A))
                        if (filteredFalseDetectionEvents.isEmpty()) {
                            Text(
                                text = "지난 내역은 이력 탭에서 확인하세요.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Gray
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

@Composable
fun EventItem(event: EventData, status: EventStatus) {
    val isPending = status == EventStatus.PENDING

    val buttonColor = if (isPending) {
        when (event.accidentType) {
            "위험" -> Color(0x1FEF4444)
            "경고" -> Color(0x1FFB923C)
            "주의" -> Color(0x1FFFB114)
            else -> Color.LightGray
        }
    } else {
        Color(0xFFF4F5F6) // A light gray for completed/false-positive
    }

    val borderColor = if (isPending) {
        when (event.accidentType) {
            "위험" -> Color(0x1FEF4444)
            "경고" -> Color(0x1FFB923C)
            "주의" -> Color(0x1FFFB114)
            else -> Color(0xFFE6E8EA)
        }
    } else {
        Color(0xFFE6E8EA)
    }

    val iconTint = if (isPending) Color.Unspecified else Color(0xFFB1B8BE)
    val textColor = if (isPending) Color(0xFF58616A) else Color(0xFFB1B8BE)
    val locationColor = if (isPending) Color.Black else Color.Gray

    Button(
        onClick = { /* TODO: Handle event click */ },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = buttonColor),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .offset(x = (-5).dp, y = 5.dp),
                    tint = iconTint
                )
            }
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text("${event.location}", color = locationColor, fontWeight = FontWeight.SemiBold)
                Text("${event.content}", color = textColor)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Start, // 중앙 정렬에서 왼쪽 정렬로 변경
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun MyTopAppBar() {
    TopAppBar(
        backgroundColor = Color(0xFFFF7A00),
        contentColor = Color.White,
        modifier = Modifier.height(64.dp),
        elevation = 0.dp // 그림자 효과 제거
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 좌측: 타이틀
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AI 이벤트 감지",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp),
                    color = Color(0xFFFFFFFF),
                    fontFamily = ClipartKorea
                )
            }

            // 우측: 아이콘 영역
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* 알림 버튼 클릭 */ }) {
                    Icon(painter = painterResource(id = R.drawable.alarm), contentDescription = "알림", tint = Color.White)
                }
                IconButton(onClick = { /* 설정 버튼 클릭 */ }) {
                    Icon(painter = painterResource(id = R.drawable.setting), contentDescription = "설정", tint = Color.White)
                }
            }
        }
    }
}

/**
 * 두 번째 상단바 Composable.
 * 필터나 하위 메뉴 등을 배치하는 용도로 사용할 수 있습니다.
 */
@Composable
fun MySecondaryTopAppBar(selectedFilter: String, onFilterChange: (String) -> Unit) {
    TopAppBar(
        backgroundColor = Color(0xFFFF7A00),
        contentColor = Color.White,
        modifier = Modifier.height(56.dp),
        elevation = 0.dp // 그림자 효과 제거
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            FilterButton("전체", selectedFilter == "전체") { onFilterChange("전체") }
            FilterButton("위험", selectedFilter == "위험") { onFilterChange("위험") }
            FilterButton("경고", selectedFilter == "경고") { onFilterChange("경고") }
            FilterButton("주의", selectedFilter == "주의") { onFilterChange("주의") }
        }
    }
}

@Composable
fun FilterButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.Transparent,
            contentColor = if (isSelected) Color.White else Color.Gray
        ),
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
fun MyBottomNavigation() {
    val items = listOf(
        BottomNavItem("안전점검", R.drawable.home, "nav_home"),
        BottomNavItem("AI감지", R.drawable.ai, "nav_ai"),
        BottomNavItem("실시간상황", R.drawable.live, "nav_live"),
        BottomNavItem("이력", R.drawable.history, "nav_history"),
        BottomNavItem("위치정보", R.drawable.location, "nav_location")
    )
    var selectedItem by remember { mutableStateOf("nav_ai") }

    BottomNavigation(
        backgroundColor = Color.White
    ) {
        items.forEach { item ->
            BottomNavigationItem(
                icon = { Icon(painter = painterResource(id = item.iconResId), contentDescription = item.title) },
                label = { Text(item.title, fontSize = 12.sp) },
                selected = selectedItem == item.screenRoute,
                onClick = { selectedItem = item.screenRoute },
                selectedContentColor = Color(0xFFFF7A00),
                unselectedContentColor = Color.Gray
            )
        }
    }
}

data class BottomNavItem(val title: String, @DrawableRes val iconResId: Int, val screenRoute: String)

data class EventData(
    val accidentType: String, // 사고유형
    val location: String, // 위치
    val content: String, // 내용
    val occurrenceTime: String = "", // 발생시간
    val deviceName: String = "", // 장치명
    val accuracy: String = ""
)

@Preview(showBackground = true)
@Composable
fun AIEventDetectScreenPreview() {
    AIEventDetectScreen()
}
