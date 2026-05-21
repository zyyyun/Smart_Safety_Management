package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

        // 2026-05-20 — FCM data.type='ai_event' push 탭 시 detail 직접 진입.
        // MyFirebaseMessagingService.showAiEventNotification 이 putExtra(EXTRA_EVENT_ID).
        // -1 (no extra) 면 기존 default "detect" 리스트 진입.
        val initialEventId = intent.getIntExtra(EXTRA_EVENT_ID, -1).takeIf { it > 0 }

        setContent {
            Smart_Safety_ManagementTheme {
                AIEventNavigation(initialEventId = initialEventId)
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_RISK_LEVEL = "extra_risk_level"
        const val EXTRA_CAMERA_ID = "extra_camera_id"
    }
}

@Composable
fun AIEventNavigation(initialEventId: Int? = null) {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isDarkTheme = isSystemInDarkTheme()
    val systemUiController = rememberSystemUiController()

    SideEffect {
        val statusBarColor = when (currentRoute) {
            "detect" -> if (isDarkTheme) GrayBackground else MainOrange
            else -> if (isDarkTheme) Color.Black else Color.White
        }
        systemUiController.setStatusBarColor(
            color = statusBarColor,
            darkIcons = !isDarkTheme
        )
    }

    Scaffold(
        bottomBar = {
            // ✅ 모든 화면에서 공통 바텀바 유지 (필요 시 조건부 노출)
            AIEventBottomBar()
        }
    ) { paddingValues ->
        // ✅ Scaffold의 paddingValues를 하단 여백으로 정확히 적용
        Surface(modifier = Modifier.padding(paddingValues)) {
            NavHost(
                navController = navController,
                startDestination = if (initialEventId != null && initialEventId > 0)
                    "detail/$initialEventId" else "detect",
            ) {
                composable("detect") {
                    AIEventDetectScreen(
                        onEventClick = { event ->
                            navController.navigate("detail/${event.id}")
                        }
                    )
                }
                composable("detail/{eventId}") { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId")?.toIntOrNull()
                    if (eventId != null) {
                        val currentContext = LocalContext.current
                        AIEventDetailScreen(
                            eventId = eventId,
                            onBackClick = { navController.popBackStack() },
                            onRequestAction = {
                                val intent = Intent(currentContext, ActionDetailActivity::class.java)
                                intent.putExtra("eventId", eventId)
                                currentContext.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AIEventBottomBar() {
    val isLight = MaterialTheme.colors.isLight
    // ✅ 바텀바 배경색을 테마에 맞춰 고정하여 흰 여백 방지
    val navBgColor = if (isLight) TextGray5.toArgb() else TextGray20.toArgb()

    AndroidView(
        factory = { context ->
            val fullView = LayoutInflater.from(context).inflate(R.layout.main_home, null) as ViewGroup
            val bottomNav = fullView.findViewById<BottomNavigationView>(R.id.bottom_nav)
            (bottomNav.parent as? ViewGroup)?.removeView(bottomNav)

            // ✅ [핵심] 시스템 인셋 자동 패딩 비활성화
            bottomNav.setOnApplyWindowInsetsListener { _, insets -> insets }
            bottomNav.setPadding(0, 0, 0, 0)

            bottomNav.elevation = 0f
            bottomNav.setBackgroundColor(navBgColor)
            bottomNav.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

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
        },
        modifier = Modifier.wrapContentHeight()
    )
}
