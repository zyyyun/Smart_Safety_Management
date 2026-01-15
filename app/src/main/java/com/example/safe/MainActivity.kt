package com.example.safe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.safe.screens.AppRoot
import com.example.safe.ui.theme.SafeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ edge-to-edge 켜기 (지도 화면에서 status bar 영역까지 그릴 수 있음)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            SafeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "realtime") {
                    composable("realtime") { AppRoot() }
                }
            }
        }
    }
}
