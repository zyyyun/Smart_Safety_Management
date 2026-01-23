package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    Scaffold(
        bottomBar = {
            // XML 바텀바 표시
            HistoryBottomBar()
        }
    ) { paddingValues ->
        // paddingValues를 적용하여 HistoryScreen이 하단바에 가려지지 않게 합니다.
        Surface(modifier = Modifier.padding(paddingValues)) {
            HistoryScreen()
        }
    }
}

@Composable
fun HistoryBottomBar() {
    AndroidView(
        factory = { context ->
            // 1. main_home.xml 레이아웃 인플레이트
            val fullView = LayoutInflater.from(context).inflate(R.layout.main_home, null) as ViewGroup

            // 2. 바텀바 찾기
            val bottomNav = fullView.findViewById<BottomNavigationView>(R.id.bottom_nav)

            // 3. 기존 부모 뷰에서 분리 (필수)
            (bottomNav.parent as? ViewGroup)?.removeView(bottomNav)

            // 4. 초기 상태 설정 (현재 페이지가 이력 페이지임을 표시)
            bottomNav.selectedItemId = R.id.nav_history

            // 5. 클릭 이벤트 설정 (기존 Activity 이동 로직 유지)
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
        }
    )
}
