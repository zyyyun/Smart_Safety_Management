
package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class DailyListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 이전에 만든 컴포저블 호출
            DailyListScreen()
        }
    }
}