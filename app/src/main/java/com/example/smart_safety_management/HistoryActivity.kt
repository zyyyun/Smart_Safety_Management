package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
            // ✅ 개선된 바텀바 호출
            HistoryBottomBar()
        }
    ) { paddingValues ->
        // ✅ Scaffold의 paddingValues를 적용하여 콘텐츠 영역을 정확히 확보
        Surface(
            modifier = Modifier.padding(paddingValues),
            color = MaterialTheme.colors.onPrimary
        ) {
            HistoryScreen()
        }
    }
}

@Composable
fun HistoryBottomBar() {
    val isLight = MaterialTheme.colors.isLight
    // ✅ 테마에 맞는 배경색을 정수로 변환하여 준비 (흰 여백 방지)
    val navBgColor = if (isLight) TextGray5.toArgb() else TextGray20.toArgb()

    AndroidView(
        factory = { context ->
            // 1. 레이아웃 인플레이트
            val fullView = LayoutInflater.from(context).inflate(R.layout.main_home, null) as ViewGroup

            // 2. 바텀바 객체만 정확히 참조
            val bottomNav = fullView.findViewById<BottomNavigationView>(R.id.bottom_nav)

            // 3. 기존 부모 뷰에서 완전히 분리
            (bottomNav.parent as? ViewGroup)?.removeView(bottomNav)

            // 4. ✅ [핵심 수정] 시스템 인셋(하단바 영역) 자동 패딩 비활성화
            // 이 설정이 없으면 바텀바 아래나 주변에 흰 공간이 생길 수 있습니다.
            bottomNav.setOnApplyWindowInsetsListener { _, insets -> insets }
            bottomNav.setPadding(0, 0, 0, 0)

            // 5. 레이아웃 파라미터 및 스타일 초기화
            bottomNav.elevation = 0f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                bottomNav.outlineProvider = null
            }
            bottomNav.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 6. 테마 배경색 강제 적용
            bottomNav.setBackgroundColor(navBgColor)

            // 7. 초기 상태 및 클릭 리스너 설정
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
        modifier = Modifier.wrapContentHeight()
    )
}
