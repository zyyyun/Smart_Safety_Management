package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

class DailyListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 현재 화면 상태 관리
            var currentScreen by remember { mutableStateOf("write") }
            // Intent로부터 수정 모드 여부 확인
            var isEditMode by remember { mutableStateOf(intent.getBooleanExtra("editMode", false)) }
            
            // 상세 화면에 전달할 데이터 상태
            var detailDate by remember { mutableStateOf(intent.getStringExtra("date") ?: "") }
            var detailLocation by remember { mutableStateOf(intent.getStringExtra("location") ?: "") }
            var detailRiskFactor by remember { mutableStateOf(intent.getStringExtra("riskFactor") ?: "") }
            var detailSafetyMeasure by remember { mutableStateOf(intent.getStringExtra("safetyMeasure") ?: "") }
            var detailPhotoUris by remember { mutableStateOf(intent.getStringArrayListExtra("photoUris")?.toList() ?: emptyList()) }
            var detailItemId by remember { mutableStateOf(intent.getStringExtra("itemId") ?: "") }
            var detailDay by remember { mutableStateOf(intent.getIntExtra("day", 0)) }

            Smart_Safety_ManagementTheme {
                // 1. 역할 확인 (UserSession 사용)
                if (UserSession.userRole == UserRole.MANAGER) {
                    // 관리자일 경우 기존 로직 유지
                    when (currentScreen) {
                        "write" -> DailyListScreen(
                            defaultDate = if (isEditMode) detailDate else (intent.getStringExtra("date") ?: run {
                                val y = intent.getIntExtra("year", -1)
                                val m = intent.getIntExtra("month", -1)
                                val d = intent.getIntExtra("day", -1)
                                if (y != -1) "$y-$m-$d" else null
                            }),
                            defaultLocation = if (isEditMode) detailLocation else (intent.getStringExtra("location") ?: ""),
                            defaultRiskFactor = if (isEditMode) detailRiskFactor else (intent.getStringExtra("riskFactor") ?: ""),
                            defaultSafetyMeasure = if (isEditMode) detailSafetyMeasure else (intent.getStringExtra("safetyMeasure") ?: ""),
                            defaultAttachedPhotos = if (isEditMode) detailPhotoUris else (intent.getStringArrayListExtra("photoUris")?.toList() ?: emptyList()),
                            checkId = if (isEditMode) detailItemId else null,
                            onComplete = { date, location, risk, measure, photos, id, day ->
                            detailDate = date
                            detailLocation = location
                            detailRiskFactor = risk
                            detailSafetyMeasure = measure
                            detailPhotoUris = photos
                            detailItemId = id
                            detailDay = day
                            currentScreen = "detail"
                            isEditMode = false
                            
                            // 수정/작성 완료 결과를 HomeActivity에 전달하기 위해 Result 설정
                            val resultIntent = android.content.Intent().apply {
                                putExtra("action", "edit") // 수정이든 작성이든 최신 데이터를 전달
                                putExtra("date", date)
                                putExtra("location", location)
                                putExtra("riskFactor", risk)
                                putExtra("safetyMeasure", measure)
                                putStringArrayListExtra("photoUris", java.util.ArrayList(photos))
                                putExtra("itemId", id)
                                putExtra("day", day)
                            }
                            setResult(RESULT_OK, resultIntent)
                        })
                        "detail" -> DailyDetailScreen(
                            date = detailDate,
                            location = detailLocation,
                            riskFactor = detailRiskFactor,
                            safetyMeasure = detailSafetyMeasure,
                            day = detailDay,
                            itemId = detailItemId,
                            photoUris = detailPhotoUris,
                            onBackClick = { finish() }, // 상세 화면에서 뒤로가면 액티비티 종료 (또는 write로 이동)
                            onEditClick = { 
                                isEditMode = true
                                currentScreen = "write" 
                            }
                        )
                    }
                } else {
                    // 2. 근로자일 경우 근로자용 화면 표시
                    when (currentScreen) {
                        "write" -> {
                            DailyListWorkerScreen(
                                checkId = detailItemId.ifBlank { intent.getStringExtra("itemId") },
                                onComplete = {
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            )
                        }
                        "detail" -> {
                            DailyDetailScreen(
                                date = detailDate,
                                location = detailLocation,
                                riskFactor = detailRiskFactor,
                                safetyMeasure = detailSafetyMeasure,
                                day = detailDay,
                                itemId = detailItemId,
                                photoUris = detailPhotoUris,
                                onBackClick = { currentScreen = "write" },
                                onEditClick = { }
                            )
                        }
                    }
                }
            }
        }
    }
}