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
            var selectedCameraId by remember { mutableStateOf<String?>(null) }

            // 시스템 백버튼 처리
            BackHandler(enabled = selectedCameraId != null) {
                selectedCameraId = null
            }

            if (selectedCameraId != null) {
                CamDetailScreen(
                    cameraId = selectedCameraId!!,
                    onBackClick = {
                        selectedCameraId = null
                    }
                )
            } else {
                CCTVManagementScreen(
                    onBackClick = {
                        finish()
                    },
                    onCameraClick = { cameraData ->
                        // 클릭된 카메라의 ID 저장
                        selectedCameraId = cameraData.id
                    }
                )
            }
        }
    }
}
