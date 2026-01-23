package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smart_safety_management.screens.location.LocationActivity
import com.example.smart_safety_management.screens.realtime.RealTimeActivity
import com.example.smart_safety_management.ui.theme.*
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.android.material.bottomnavigation.BottomNavigationView

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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val systemUiController = rememberSystemUiController()

    SideEffect {
        when (currentRoute) {
            "detect" -> {
                systemUiController.setStatusBarColor(color = MainOrange, darkIcons = true)
            }
            else -> {
                systemUiController.setStatusBarColor(color = Color.White, darkIcons = true)
            }
        }
    }

    Scaffold(
        bottomBar = {
            // ✅ 기존 Compose BottomNavigation을 제거하고 XML 바텀바를 삽입합니다.
            // currentRoute가 "detect"일 때만 바텀바가 보이도록 설정 유지
            if (currentRoute == "detect") {
                MainHomeBottomBar()
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "detect",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("detect") {
                AIEventDetectScreen(
                    onEventClick = { event ->
                        if (event.content.contains("쓰러짐") && event.location == "C구역 2열") {
                            navController.navigate("detail")
                        }
                    }
                )
            }

            composable("detail") {
                AIEventDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onRequestAction = {
                        navController.navigate("action_detail")
                    }
                )
            }

            composable("action_detail") {
                ActionDetailScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun MainHomeBottomBar() {
    AndroidView(
        factory = { context ->
            // 1. main_home.xml 레이아웃 인플레이트
            val fullView = LayoutInflater.from(context).inflate(R.layout.main_home, null) as ViewGroup

            // 2. 바텀바 찾기
            val bottomNav = fullView.findViewById<BottomNavigationView>(R.id.bottom_nav)

            // 3. 기존 부모 뷰에서 분리 (필수)
            (bottomNav.parent as? ViewGroup)?.removeView(bottomNav)

            // 4. 초기 상태 설정
            bottomNav.selectedItemId = R.id.nav_ai

            // 5. 클릭 이벤트 설정 (기존 Activity 이동 로직 유지)
            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        context.startActivity(Intent(context, HomeActivity::class.java))
                        true
                    }
                    R.id.nav_ai -> true
                    R.id.nav_live -> {
                        context.startActivity(Intent(context, RealTimeActivity::class.java))
                        true
                    }
                    R.id.nav_history -> {
                        context.startActivity(Intent(context, HistoryActivity::class.java))
                        true
                    }
                    R.id.nav_location -> {
                        context.startActivity(Intent(context, LocationActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
            bottomNav
        }
    )
}