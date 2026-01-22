package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class DeviceManageWorkerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // DeviceManage_worker.kt에 있는 Screen 호출
            DeviceManageWorkerScreen(onBackClick = { finish() })
        }
    }
}