package com.example.smart_safety_management.screens.afci

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class ArcBreakerManageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ArcBreakerManageRoute(
                    onBack = { finish() }
                )
            }
        }
    }
}