package com.example.smart_safety_management

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentScreen by remember { mutableStateOf("write") }
            val context = LocalContext.current
            val activity = context as? Activity

            // ✅ HomeActivity가 기대하는 키들: date, location, riskFactor, safetyMeasure
            // TODO: 너 작성 화면에서 값 가져오게 바꿔야 함
            // 지금은 테스트용 기본값(임시)
            fun sendResultAndFinish(
                dateStr: String,
                location: String,
                riskFactor: String,
                safetyMeasure: String
            ) {
                val result = Intent().apply {
                    putExtra("date", dateStr)                 // "YYYY-MM-DD"
                    putExtra("location", location)
                    putExtra("riskFactor", riskFactor)
                    putExtra("safetyMeasure", safetyMeasure)
                }
                activity?.setResult(RESULT_OK, result)
                activity?.finish()
            }

            Smart_Safety_ManagementTheme {
                if (UserSession.userRole == UserRole.MANAGER) {
                    when (currentScreen) {
                        "write" -> DailyListScreen(
                            onComplete = { dateStr, location, riskFactor, safetyMeasure ->
                                sendResultAndFinish(
                                    dateStr = dateStr,
                                    location = location,
                                    riskFactor = riskFactor,
                                    safetyMeasure = safetyMeasure
                                )
                            }
                        )

                        "detail" -> DailyDetailScreen(onBackClick = { currentScreen = "write" })
                    }
                } else {
                    when (currentScreen) {
                        "write" -> DailyListWorkerScreen(
                            onComplete = { dateStr, location, riskFactor, safetyMeasure ->
                                sendResultAndFinish(
                                    dateStr = dateStr,
                                    location = location,
                                    riskFactor = riskFactor,
                                    safetyMeasure = safetyMeasure
                                )
                            }
                        )

                        "detail" -> DailyDetailWorkerScreen(
                            onBackClick = { currentScreen = "write" },
                            onReportClick = { }
                        )
                    }
                }
            }
        }
    }
}
