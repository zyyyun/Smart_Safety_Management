package com.example.smart_safety_management

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

class DailyDetailActivity : ComponentActivity() {

    private val editLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            // ✅ DailyListActivity에서 action 누락해도 HomeActivity가 처리할 수 있게 보정
            if (data.getStringExtra("action").isNullOrBlank()) {
                data.putExtra("action", "edit")
            }

            // ✅ HomeActivity(detailLauncher)로 결과 그대로 전달
            setResult(Activity.RESULT_OK, data)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val day = intent.getIntExtra("day", -1)
        val itemId = intent.getStringExtra("itemId") ?: ""

        val date = intent.getStringExtra("date") ?: ""
        val location = intent.getStringExtra("location") ?: ""
        val riskFactor = intent.getStringExtra("riskFactor") ?: ""
        val safetyMeasure = intent.getStringExtra("safetyMeasure") ?: ""

        // ✅ Compose 쪽이 List<String>을 쓰므로 그대로 맞춤
        val photoUris: List<String> =
            intent.getStringArrayListExtra("photoUris")?.toList() ?: emptyList()

        setContent {
            Smart_Safety_ManagementTheme {
                if (UserSession.userRole == UserRole.MANAGER) {
                    DailyDetailScreen(
                        date = date,
                        location = location,
                        riskFactor = riskFactor,
                        safetyMeasure = safetyMeasure,
                        day = day,
                        itemId = itemId,
                        photoUris = photoUris,
                        onBackClick = { finish() },
                        onEditClick = {
                            val editIntent = Intent(this, DailyListActivity::class.java).apply {
                                // ✅ 수정 화면 프리필 값들
                                putExtra("date", date)
                                putExtra("location", location)
                                putExtra("riskFactor", riskFactor)
                                putExtra("safetyMeasure", safetyMeasure)
                                putStringArrayListExtra("photoUris", ArrayList(photoUris))

                                // ✅ 수정 식별자 (HomeActivity가 이걸로 찾아서 교체함)
                                putExtra("editMode", true)
                                putExtra("day", day)
                                putExtra("itemId", itemId)
                            }
                            editLauncher.launch(editIntent)
                        }
                    )
                } else {
                    DailyDetailWorkerScreen(
                        date = date,
                        location = location,
                        riskFactor = riskFactor,
                        safetyMeasure = safetyMeasure,
                        photoUris = photoUris,
                        onBackClick = { finish() },
                        onReportClick = {
                            // 근로자 점검 보고 화면으로 이동
                            val reportIntent = Intent(this, DailyListActivity::class.java).apply {
                                putExtra("date", date) // 기본 날짜 전달 (필요시)
                                putExtra("editMode", true) // 보고 모드
                                putExtra("day", day)
                                putExtra("itemId", itemId)
                            }
                            editLauncher.launch(reportIntent)
                        }
                    )
                }
            }
        }
    }
}
