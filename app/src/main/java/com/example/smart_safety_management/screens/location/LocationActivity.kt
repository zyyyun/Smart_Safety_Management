package com.example.smart_safety_management.screens.location

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.smart_safety_management.R
import com.example.smart_safety_management.screens.realtime.RealTimeActivity
import com.example.smart_safety_management.ui.theme.GrayBorder
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.MainOrange
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.ui.theme.TextDark
import com.example.smart_safety_management.ui.theme.TextGray5
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height

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

    val dark = LocalSafeColors.current.isDark
    val bottomBg = if (dark) Color(0xFF131416) else TextGray5

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.navigationBarColor = bottomBg.toArgb()
    }

    Scaffold(
        backgroundColor = bottomBg,
        bottomBar = {
            BottomNavigation(
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
                        unselectedContentColor = if (MaterialTheme.colors.isLight) GrayBorder else TextDark,
                        alwaysShowLabel = false,
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
        LocationScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding()),
            bottomBarHeight = paddingValues.calculateBottomPadding(),
            isDark = dark
        )
    }
}
