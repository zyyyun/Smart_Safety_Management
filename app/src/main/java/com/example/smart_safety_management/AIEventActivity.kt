package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
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

    // ✅ 다크 모드 상태 확인
    val isDarkTheme = isSystemInDarkTheme()
    val systemUiController = rememberSystemUiController()

    // ✅ 경로와 다크 모드 여부에 따른 상태바 색상 제어
    SideEffect {
        val statusBarColor = when (currentRoute) {
            "detect" -> {
                if (isDarkTheme) {
                    // TODO: 다크 모드일 때 detect 화면 상태바 색상 (예: GrayBackground)
                    GrayBackground 
                } else {
                    MainOrange
                }
            }
            else -> {
                if (isDarkTheme) {
                    // TODO: 다크 모드일 때 기타 화면 상태바 색상
                    Color.Black
                } else {
                    Color.White
                }
            }
        }

        systemUiController.setStatusBarColor(
            color = statusBarColor,
            // 다크 모드일 때는 밝은 아이콘(darkIcons = false), 라이트 모드일 때는 어두운 아이콘(darkIcons = true)
            darkIcons = !isDarkTheme
        )
    }

    Scaffold(
        bottomBar = {
            if (currentRoute == "detect") {
                AIEventBottomBar()
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
fun AIEventBottomBar() {
    AndroidView(
        factory = { context ->
            val fullView = LayoutInflater.from(context).inflate(R.layout.main_home, null) as ViewGroup
            val bottomNav = fullView.findViewById<BottomNavigationView>(R.id.bottom_nav)
            (bottomNav.parent as? ViewGroup)?.removeView(bottomNav)

            bottomNav.selectedItemId = R.id.nav_ai

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
