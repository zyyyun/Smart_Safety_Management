package com.example.smart_safety_management.screens.afci

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

class ArcBreakerManageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Smart_Safety_ManagementTheme {
                ArcBreakerManageRoute(
                    onBack = { finish() }
                )
            }
        }
    }
}