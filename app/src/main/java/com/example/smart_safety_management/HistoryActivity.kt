package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*

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
    val context = LocalContext.current

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
                        selected = route == "nav_history", // 현재 이력 화면이므로 nav_history 선택
                        onClick = {
                            when (route) {
                                "nav_home" -> {
                                    val intent = Intent(context, HomeActivity::class.java)
                                    context.startActivity(intent)
                                }
                                "nav_ai" -> {
                                    val intent = Intent(context, AIEventActivity::class.java)
                                    context.startActivity(intent)
                                }
                                "nav_live" -> Toast.makeText(context, "실시간 상황 화면으로 이동", Toast.LENGTH_SHORT).show()
                                "nav_history" -> { /* 현재 화면 */ }
                                "nav_location" -> Toast.makeText(context, "위치 정보 화면으로 이동", Toast.LENGTH_SHORT).show()
                            }
                        },
                        selectedContentColor = MainOrange,
                        unselectedContentColor = if (MaterialTheme.colors.isLight) GrayBorder else TextDark
                    )
                }
            }
        }
    ) { paddingValues ->
        // paddingValues를 적용하여 HistoryScreen이 하단바에 가려지지 않게 합니다.
        Surface(modifier = Modifier.padding(paddingValues)) {
            HistoryScreen()
        }
    }
}
