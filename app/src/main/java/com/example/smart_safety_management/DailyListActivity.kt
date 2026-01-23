package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

class DailyListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 현재 화면 상태 관리
            var currentScreen by remember { mutableStateOf("write") }

            Smart_Safety_ManagementTheme {
                // 1. 역할 확인 (UserSession 사용)
                if (UserSession.userRole == UserRole.MANAGER) {
                    // 관리자일 경우 기존 로직 유지
                    when (currentScreen) {
                        "write" -> DailyListScreen(onComplete = { currentScreen = "detail" })
                        "detail" -> DailyDetailScreen(onBackClick = { currentScreen = "write" })
                    }
                } else {
                    // 2. 근로자일 경우 근로자용 화면 표시
                    when (currentScreen) {
                        "write" -> {
                            DailyListWorkerScreen(
                                onComplete = {
                                    // 작성 완료 후 상세 화면으로 이동하거나 종료
                                    currentScreen = "detail"
                                }
                            )
                        }
                        "detail" -> {
                            // 근로자용 상세 화면 호출
                            DailyDetailWorkerScreen(
                                onBackClick = { currentScreen = "write" },
                                onReportClick = { /* 이미 작성 중이므로 필요한 로직 추가 */ }
                            )
                        }
                    }
                }
            }
        }
    }
}