package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class DailyDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 현재 역할이 관리자이면 DailyDetailScreen, 근로자이면 DailyDetailWorkerScreen 표시
            if (UserSession.userRole == UserRole.MANAGER) {
                DailyDetailScreen(onBackClick = { finish() })
            } else {
                DailyDetailWorkerScreen(
                    onBackClick = { finish() },
                    onReportClick = {
                        val intent = Intent(this@DailyDetailActivity, DailyListActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}