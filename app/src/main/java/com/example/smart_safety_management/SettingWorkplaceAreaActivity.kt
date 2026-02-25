package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

class SettingWorkplaceAreaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Smart_Safety_ManagementTheme {
                val userId = UserSession.userId ?: ""
                SettingWorkplaceAreaScreen(userId = userId)
            }
        }
    }
}