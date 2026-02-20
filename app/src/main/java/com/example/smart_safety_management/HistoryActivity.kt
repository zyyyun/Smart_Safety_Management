package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.example.smart_safety_management.screens.location.LocationActivity
import com.example.smart_safety_management.screens.realtime.RealTimeActivity
import com.example.smart_safety_management.ui.theme.*
import com.google.android.material.bottomnavigation.BottomNavigationView

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Smart_Safety_ManagementTheme {
                HistoryNavigationWrapper()
            }
        }
    }
}

@Composable
fun HistoryNavigationWrapper() {
    // 배경색 설정
    val totalBackgroundColor = if (MaterialTheme.colors.isLight) MainOrange else GrayBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(totalBackgroundColor)
    ) {
        HistoryScreen(bottomBar = { HistoryBottomBar() })
    }
}

@Composable
fun HistoryBottomBar() {
    val navBgColor = if (MaterialTheme.colors.isLight) TextGray5.toArgb() else TextGray20.toArgb()

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

            // 초기 상태 및 클릭 리스너 설정
            bottomNav.selectedItemId = R.id.nav_history
            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        context.startActivity(Intent(context, HomeActivity::class.java))
                        true
                    }
                    R.id.nav_ai -> {
                        context.startActivity(Intent(context, AIEventActivity::class.java))
                        true
                    }
                    R.id.nav_live -> {
                        context.startActivity(Intent(context, RealTimeActivity::class.java))
                        true
                    }
                    R.id.nav_history -> true // 현재 페이지
                    R.id.nav_location -> {
                        context.startActivity(Intent(context, LocationActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
            bottomNav
        },
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight() // Compose 측에서도 높이를 명시적으로 고정
    )
}
