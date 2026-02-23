package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.example.smart_safety_management.screens.firealarm.FireAlarmManageRoute

class FireAlarmManageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                FireAlarmManageRoute(
                    onBack = { finish() }
                )
            }
        }
    }
}