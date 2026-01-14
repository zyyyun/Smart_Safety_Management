package com.example.smart_safety_management

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
    
    // 검색 모드 및 쿼리 상태 관리
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
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
                            },
                            onSearchIconClick = { isSearchMode = !isSearchMode }
                        )
                        // 상단바 아래에 검색바 표시
                        if (isSearchMode) {
                            HistorySearchBar(
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                width = 350.dp // 여기서 너비를 조절할 수 있습니다.
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
                        repeat(10) {
                            HistoryItemFrame()
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(800.dp)
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "필터 설정",
                fontFamily = Pretendard,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF131416)
            )
        }
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
            Box(modifier = Modifier.fillMaxWidth().height(80.dp))
            Divider(color = Color(0xFFE6E8EA), thickness = 1.dp, modifier = Modifier.fillMaxWidth())
            Box(modifier = Modifier.fillMaxWidth().height(60.dp))
        }
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
