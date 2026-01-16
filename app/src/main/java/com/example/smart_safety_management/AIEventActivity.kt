package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

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

    NavHost(navController = navController, startDestination = "detect") {
        // 1. AI 이벤트 감지 목록 화면
        composable("detect") {
            AIEventDetectScreen(
                onEventClick = { event ->
                    // "C구역 2열 / 쓰러짐" 이벤트 클릭 시 상세 화면으로 이동
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
                    // [조치요청] 버튼 클릭 시 조치 요청서 작성 화면으로 이동
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
