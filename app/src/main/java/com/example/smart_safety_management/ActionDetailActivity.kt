package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class ActionDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // ActionDetail.kt에 정의된 ActionDetailScreen 호출
            ActionDetailScreen(onBackClick = { finish() })
        }
    }
}