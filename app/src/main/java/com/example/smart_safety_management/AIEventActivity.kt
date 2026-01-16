package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smart_safety_management.ui.theme.*

class AIEventActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Smart_Safety_ManagementTheme {
                AIEventNavigation()
            }
        }
    }
}

@Composable
fun AIEventNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // 현재 내비게이션 경로 확인
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute == "detect") {
                BottomNavigation(
                    backgroundColor = if (MaterialTheme.colors.isLight) TextGray5 else TextGray20,
                    elevation = 10.dp
                ) {
                    val items = listOf(
                        Triple("안전점검", R.drawable.home, "nav_home"),
                        Triple("AI감지", R.drawable.ai, "nav_ai"),
                        Triple("실시간상황", R.drawable.live, "nav_live"),
                        Triple("이력", R.drawable.history, "nav_history"),
                        Triple("위치정보", R.drawable.location, "nav_location")
                    )

                    items.forEach { (title, iconRes, route) ->
                        BottomNavigationItem(
                            icon = { Icon(painter = painterResource(id = iconRes), contentDescription = title) },
                            label = { Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            selected = route == "nav_ai", // 현재 AI 활동 중이므로 nav_ai 선택
                            onClick = {
                                when (route) {
                                    "nav_home" -> {
                                        // 홈 화면(HomeActivity)으로 이동
                                        val intent = Intent(context, HomeActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                    "nav_ai" -> {
                                        // 현재 화면이므로 동작 없음
                                    }
                                    "nav_live" -> Toast.makeText(context, "실시간 상황 화면으로 이동", Toast.LENGTH_SHORT).show()
                                    "nav_history" -> Toast.makeText(context, "이력 화면으로 이동", Toast.LENGTH_SHORT).show()
                                    "nav_location" -> Toast.makeText(context, "위치 정보 화면으로 이동", Toast.LENGTH_SHORT).show()
                                }
                            },
                            selectedContentColor = MainOrange,
                            unselectedContentColor = if (MaterialTheme.colors.isLight) GrayBorder else TextDark
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "detect",
            modifier = Modifier.padding(paddingValues)
        ) {
            // 1. AI 이벤트 감지 목록 화면
            composable("detect") {
                AIEventDetectScreen(
                    onEventClick = { event ->
                        if (event.content.contains("쓰러짐") && event.location == "C구역 2열") {
                            navController.navigate("detail")
                        }
                    }
                )
            }

            // 2. AI 이벤트 상세 화면
            composable("detail") {
                AIEventDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onRequestAction = {
                        navController.navigate("action_detail")
                    }
                )
            }

            // 3. 조치요청서 작성 화면
            composable("action_detail") {
                ActionDetailScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
