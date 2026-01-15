package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
// 아래 테마 import가 핵심입니다.
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

class DailyListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 현재 화면 상태 관리 ("write" 또는 "detail")
            var currentScreen by remember { mutableStateOf("write") }

            Smart_Safety_ManagementTheme {
                when (currentScreen) {
                    "write" -> {
                        // DailyList.kt에서 onComplete 인자를 받도록 수정해야 합니다.
                        DailyListScreen(
                            onComplete = { currentScreen = "detail" }
                        )
                    }
                    "detail" -> {
                        // DailyDetail.kt에서 onBackClick 인자를 받도록 수정해야 합니다.
                        DailyDetailScreen(
                            onBackClick = { currentScreen = "write" }
                        )
                    }
                }
            }
        }
    }
}