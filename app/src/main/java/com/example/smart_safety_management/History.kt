package com.example.smart_safety_management

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HistoryScreen() {
    var isAscending by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf("AI감지") }
    
    // BottomSheet 상태 관리: skipHalfExpanded = true 를 추가하여 한 번에 전체가 펼쳐지도록 설정
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val coroutineScope = rememberCoroutineScope()

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
                            }
                        )
                        HistorySecondaryAppBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                },
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
                        verticalArrangement = Arrangement.spacedBy(20.dp) // 박스들 간의 상하 여백을 12dp에서 20dp로 늘림
                    ) {
                        repeat(5) {
                            HistoryItemFrame()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterBottomSheetContent() {
    // 필터 BottomSheet 내용
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(800.dp)
            .background(Color.White)
    ) {
        Text(
            text = "필터 설정",
            modifier = Modifier.align(Alignment.Center),
            fontFamily = Pretendard,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF131416)
        )
    }
}

@Composable
fun HistoryItemFrame() {
    Box(
        modifier = Modifier
            .size(width = 350.dp, height = 140.dp)
            .background(Color.White, shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFCDD1D5), shape = RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 상단 영역 (80dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
            }

            // 가로 경계선 (0xFFE6E8EA)
            Divider(
                color = Color(0xFFE6E8EA),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // 하단 영역 (60dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
            }
        }

        // 세로 경계선 추가
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
fun HistoryTopAppBar(
    isAscending: Boolean, 
    onSortToggle: () -> Unit,
    onFilterClick: () -> Unit
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
                        contentDescription = "Sort",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onFilterClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.filter),
                        contentDescription = "Filter",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { /* 검색 */ }) {
                    Icon(
                        painter = painterResource(id = R.drawable.search),
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
            }
        }
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
            TabRowDefaults.Indicator(
                Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(selectedTab)]),
                color = Color(0XFFF97316),
                height = 2.dp
            )
        },
        divider = {}
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
                        color = if ( selectedTab == title ) Color(0xFFF97316) else Color(0xFFB1B8BE),
                        fontWeight = if (selectedTab == title) FontWeight.Bold else FontWeight.Medium
                    )
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
