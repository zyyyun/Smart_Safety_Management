package com.example.smart_safety_management

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AIEventDetectScreen() {
    Scaffold(
        // topBar에 Column을 사용하여 여러 개의 상단바를 배치합니다.
        topBar = {
            Column {
                MyTopAppBar()
                MySecondaryTopAppBar() // 여기에 두 번째 상단바를 추가합니다.
            }
        },
        bottomBar = { MyBottomNavigation() }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            color = Color.White
        ) {
            // 배경을 흰색으로 채웁니다.
        }
    }
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
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
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
fun MySecondaryTopAppBar() {
    TopAppBar(
        backgroundColor = Color(0xFFFF7A00),
        contentColor = Color.White,
        modifier = Modifier.height(56.dp),
        elevation = 0.dp // 그림자 효과 제거
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "하위 메뉴 또는 필터 영역",
                fontSize = 16.sp
            )
            // 여기에 다른 버튼이나 UI 요소를 추가할 수 있습니다.
        }
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

@Preview(showBackground = true)
@Composable
fun AIEventDetectScreenPreview() {
    AIEventDetectScreen()
}
