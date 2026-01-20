package com.example.smart_safety_management.screens.realtime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.AIEventActivity
import com.example.smart_safety_management.HistoryActivity
import com.example.smart_safety_management.HomeActivity
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.R
import com.example.smart_safety_management.screens.detail.InternalDetailScreen
import com.example.smart_safety_management.screens.location.LocationActivity
import com.example.smart_safety_management.ui.theme.*

import com.example.smart_safety_management.screens.dialog.MapDialog // (지금은 안 쓰면 지워도 됨)

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

    // ✅ 상세로 넘어갈 아이템 상태 (추가)
    var selectedItem by remember { mutableStateOf<LiveCardItem?>(null) }

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
                        unselectedContentColor = if (MaterialTheme.colors.isLight) GrayBorder else TextDark
                    )
                }
            }
        }
    ) { paddingValues ->

        // ✅ 여기서 화면 분기 (추가)
        if (selectedItem == null) {
            RealTimeScreen(
                modifier = Modifier.padding(paddingValues),
                onCardClick = { item ->
                    selectedItem = item   // ✅ 카드 누르면 상세로 전환
                }
            )
        } else {
            InternalDetailScreen(
                item = selectedItem!!,
                onBack = { selectedItem = null }, // ✅ 뒤로가기 누르면 다시 리스트
                onMapClick = { /* 필요하면 여기에서 지도 연결 */ },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
