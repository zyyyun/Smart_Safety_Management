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
import com.example.smart_safety_management.screens.dialog.MapDialog
import com.example.smart_safety_management.screens.location.LocationActivity
import com.example.smart_safety_management.ui.theme.*

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

    // ✅ 상세로 넘어갈 아이템 상태
    var selectedItem by remember { mutableStateOf<LiveCardItem?>(null) }

    // ✅ 지도 다이얼로그 상태 (리스트/상세 공통)
    var showMap by remember { mutableStateOf(false) }

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

        // ✅ 화면 분기
        if (selectedItem == null) {
            RealTimeScreen(
                modifier = Modifier.padding(paddingValues),
                onCardClick = { item ->
                    selectedItem = item
                }
            )
        } else {
            InternalDetailScreen(
                item = selectedItem!!,
                onBack = { selectedItem = null },
                onMapClick = { showMap = true },           // ✅ 상세 우측 상단 버튼 연결
                modifier = Modifier.padding(paddingValues)
            )
        }

        // ✅ MapDialog를 Activity 레벨에서 공통으로 띄움 (리스트/상세 어디서든)
        if (showMap) {
            MapDialog(
                item = selectedItem,
                onDismiss = { showMap = false },
                onMoveCamera = {
                    showMap = false
                    // TODO: 여기서 원하는 동작(카메라 이동/해당 CAM 상세로 이동 등) 연결
                }
            )
        }
    }
}
