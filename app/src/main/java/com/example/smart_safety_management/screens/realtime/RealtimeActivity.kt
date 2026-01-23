package com.example.smart_safety_management.screens.realtime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.AIEventActivity
import com.example.smart_safety_management.HistoryActivity
import com.example.smart_safety_management.HomeActivity
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.R
import com.example.smart_safety_management.screens.detail.InternalDetailScreen
import com.example.smart_safety_management.screens.dialog.MapDialog
import com.example.smart_safety_management.screens.location.LocationActivity
import com.example.smart_safety_management.ui.theme.GrayBorder
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.MainOrange
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.ui.theme.TextDark

class RealTimeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Smart_Safety_ManagementTheme {
                RealTimeNavigation()
            }
        }
    }
}

@Composable
private fun RealTimeNavigation() {
    val context = LocalContext.current
    val activity = context as? Activity

    var selectedItem by remember { mutableStateOf<LiveCardItem?>(null) }
    var showMap by remember { mutableStateOf(false) }

    // ✅ 바텀바 색을 SafeColors 기준으로 통일 (다크/라이트 자동)
    val c = LocalSafeColors.current
    val bottomBg = c.bottomBar

    // ✅ 시스템 네비게이션 바(제스처 영역) 색도 동일하게
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.navigationBarColor = bottomBg.toArgb()
    }

    Scaffold(
        // ✅ 바닥(검정 띠 영역)까지 같은 톤으로 채우기
        backgroundColor = bottomBg,
        bottomBar = {
            BottomNavigation(
                // ✅ LocationActivity와 동일: height 강제 지정 제거
                modifier = Modifier.navigationBarsPadding(),
                backgroundColor = bottomBg,
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
                        selected = route == "nav_live",
                        onClick = {
                            when (route) {
                                "nav_home" -> {
                                    context.startActivity(Intent(context, HomeActivity::class.java))
                                    activity?.finish()
                                }
                                "nav_ai" -> {
                                    context.startActivity(Intent(context, AIEventActivity::class.java))
                                    activity?.finish()
                                }
                                "nav_live" -> {
                                    // 현재 화면
                                }
                                "nav_history" -> {
                                    context.startActivity(Intent(context, HistoryActivity::class.java))
                                    activity?.finish()
                                }
                                "nav_location" -> {
                                    context.startActivity(Intent(context, LocationActivity::class.java))
                                    activity?.finish()
                                }
                            }
                        },
                        selectedContentColor = MainOrange,
                        unselectedContentColor = if (MaterialTheme.colors.isLight) GrayBorder else TextDark,
                        alwaysShowLabel = false,
                        // ✅ LocationActivity와 동일한 크기/간격
                        icon = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = title,
                                    modifier = Modifier.size(22.dp)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->

        if (selectedItem == null) {
            RealTimeScreen(
                modifier = Modifier.padding(paddingValues),
                onCardClick = { item -> selectedItem = item }
            )
        } else {
            InternalDetailScreen(
                item = selectedItem!!,
                onBack = { selectedItem = null },
                onMapClick = { showMap = true },
                modifier = Modifier.padding(paddingValues)
            )
        }

        if (showMap) {
            MapDialog(
                item = selectedItem,
                onDismiss = { showMap = false },
                onMoveCamera = { showMap = false }
            )
        }
    }
}
