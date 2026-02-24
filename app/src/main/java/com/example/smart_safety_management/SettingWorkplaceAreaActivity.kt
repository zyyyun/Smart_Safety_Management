package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class SettingWorkplaceAreaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val userId = UserSession.userId ?: ""
            SettingWorkplaceAreaScreen(userId = userId)
        }
    }
}