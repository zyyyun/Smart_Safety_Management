package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

class SettingCctvManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 상세 화면 표시 여부 상태
            var showDetail by remember { mutableStateOf(false) }

            // 시스템 백버튼 처리
            BackHandler(enabled = showDetail) {
                showDetail = false
            }

            if (showDetail) {
                // 임시로 어떤 카메라를 클릭해도 동일한 상세 화면 표시
                CamDetailScreen(
                    onBackClick = {
                        showDetail = false
                    }
                )
            } else {
                CCTVManagementScreen(
                    onBackClick = {
                        finish()
                    },
                    onCameraClick = {
                        showDetail = true
                    }
                )
            }
        }
    }
}
