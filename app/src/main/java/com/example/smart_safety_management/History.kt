package com.example.smart_safety_management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

@Composable
fun HistoryScreen() {
    Smart_Safety_ManagementTheme {
        Scaffold(
            topBar = {
                HistoryTopAppBar()
            },
            backgroundColor = Color(0xFFFF7A00)
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = Color.White,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 내용 구현 예정
                }
            }
        }
    }
}

@Composable
fun HistoryTopAppBar() {
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
                IconButton(onClick = { /* 정렬 */ }) {
                    Icon(
                        painter = painterResource(id = R.drawable.dsc),
                        contentDescription = "Sort",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { /* 필터 */ }) {
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

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    HistoryScreen()
}
