package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

class ActionDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getIntExtra("eventId", -1)
        setContent {
            Smart_Safety_ManagementTheme {
                if (eventId != -1) {
                    ActionDetailScreen(eventId = eventId, onBackClick = { finish() })
                } else {
                    // 예외 처리: ID가 없으면 종료
                    finish()
                }
            }
        }
    }
}
