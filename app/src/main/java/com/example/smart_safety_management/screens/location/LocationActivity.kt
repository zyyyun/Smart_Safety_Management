package com.example.smart_safety_management.screens.location

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.AIEventActivity
import com.example.smart_safety_management.HistoryActivity
import com.example.smart_safety_management.HomeActivity
import com.example.smart_safety_management.R
import com.example.smart_safety_management.screens.realtime.RealTimeActivity
import com.example.smart_safety_management.ui.theme.*

class LocationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Smart_Safety_ManagementTheme {
                LocationNavigation()
            }
        }
    }
}

@Composable
private fun LocationNavigation() {
    val context = LocalContext.current
    val activity = context as? Activity

    // ✅ 테마 기준으로 다크 여부 자동 결정
    val dark = LocalSafeColors.current.isDark

    Scaffold(
        bottomBar = {
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
                        selected = route == "nav_location",
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
                                    context.startActivity(Intent(context, RealTimeActivity::class.java))
                                    activity?.finish()
                                }
                                "nav_history" -> {
                                    context.startActivity(Intent(context, HistoryActivity::class.java))
                                    activity?.finish()
                                }
                                "nav_location" -> {
                                    // 현재 화면
                                }
                            }
                        },
                        selectedContentColor = MainOrange,
                        unselectedContentColor = if (MaterialTheme.colors.isLight) GrayBorder else TextDark
                    )
                }
            }
        }
    ) { paddingValues ->
        // ✅ paddingValues를 LocationScreen에 전달
        LocationScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding()),
            bottomBarHeight = paddingValues.calculateBottomPadding(),
            isDark = dark // ✅ 여기서 자동 다크 적용
        )
    }
}
