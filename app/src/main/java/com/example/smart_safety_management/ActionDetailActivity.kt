package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class ActionDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // ✅ [수정] ActionDetail.kt에 실제 정의된 함수 이름으로 변경
            ActionDetailScreen(onBackClick = { finish() })
        }
    }
}
