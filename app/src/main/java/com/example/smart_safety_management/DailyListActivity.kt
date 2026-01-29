package com.example.smart_safety_management

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import java.util.Calendar

class DailyListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 캘린더에서 넘어온 날짜 (HomeActivity → DailyListActivity)
        val y = intent.getIntExtra("year", -1)
        val m = intent.getIntExtra("month", -1)
        val d = intent.getIntExtra("day", -1)

        // ✅ 상세에서 수정하기로 넘어온 값 (DailyDetailActivity → DailyListActivity)
        val editMode = intent.getBooleanExtra("editMode", false)
        val editDate = intent.getStringExtra("date") ?: ""
        val editLocation = intent.getStringExtra("location") ?: ""
        val editRiskFactor = intent.getStringExtra("riskFactor") ?: ""
        val editSafetyMeasure = intent.getStringExtra("safetyMeasure") ?: ""
        val editPhotoUris = intent.getStringArrayListExtra("photoUris") ?: arrayListOf()

        // ✅ 수정 대상 식별자
        val itemId = intent.getStringExtra("itemId") ?: ""

        // ✅ 기본 날짜(없으면 오늘)
        val fallbackToday = run {
            val cal = Calendar.getInstance()
            String.format(
                "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        // ✅ 우선순위: 수정모드면 editDate, 아니면 캘린더 y/m/d, 아니면 오늘
        val defaultDateStrRaw = when {
            editMode && editDate.isNotBlank() -> editDate
            (y != -1 && m != -1 && d != -1) -> String.format("%04d-%02d-%02d", y, m, d)
            else -> fallbackToday
        }

        // ✅ "수정모드에서 넘어온 day"는 oldDay로 쓰면 안 됨 (HomeActivity는 "day"를 읽음)
        val oldDayForEdit = if (editMode) intent.getIntExtra("day", -1) else -1

        setContent {
            val context = LocalContext.current
            val activity = context as? Activity

            fun sendResultAndFinish(
                dateStr: String,
                location: String,
                riskFactor: String,
                safetyMeasure: String,
                photoUris: List<String>
            ) {
                val result = Intent().apply {
                    // ✅ HomeActivity(detailLauncher) 가 edit를 처리하려면 action 필요
                    if (editMode) putExtra("action", "edit")

                    putExtra("date", dateStr)
                    putExtra("location", location)
                    putExtra("riskFactor", riskFactor)
                    putExtra("safetyMeasure", safetyMeasure)
                    putStringArrayListExtra("photoUris", ArrayList(photoUris))

                    // ✅ HomeActivity 쪽 로직과 키를 "완전히 동일"하게 맞춤
                    // - editMode == true 일 때 HomeActivity는 "day"와 "itemId"를 사용함
                    putExtra("editMode", editMode)
                    if (editMode) {
                        putExtra("day", oldDayForEdit)   // 🔥 기존 oldDay -> day 로 변경 (핵심)
                        putExtra("itemId", itemId)
                    }
                }

                activity?.setResult(Activity.RESULT_OK, result)
                activity?.finish()
            }

            Smart_Safety_ManagementTheme {
                if (UserSession.userRole == UserRole.MANAGER) {
                    DailyListScreen(
                        defaultDate = defaultDateStrRaw,
                        initialDate = if (editMode) editDate else "", // ✅ DailyListScreen이 initialDate 지원하니까 넣어줌
                        initialLocation = if (editMode) editLocation else "",
                        initialRiskFactor = if (editMode) editRiskFactor else "",
                        initialSafetyMeasure = if (editMode) editSafetyMeasure else "",
                        initialPhotoUris = if (editMode) editPhotoUris else emptyList(),
                        onComplete = { dateStr, location, riskFactor, safetyMeasure, photoUris ->
                            sendResultAndFinish(dateStr, location, riskFactor, safetyMeasure, photoUris)
                        }
                    )
                } else {
                    DailyListWorkerScreen(
                        defaultDate = defaultDateStrRaw,
                        initialDate = if (editMode) editDate else "", // ✅ 워커 화면도 initialDate가 있으면 동일하게
                        initialLocation = if (editMode) editLocation else "",
                        initialRiskFactor = if (editMode) editRiskFactor else "",
                        initialSafetyMeasure = if (editMode) editSafetyMeasure else "",
                        initialPhotoUris = if (editMode) editPhotoUris else emptyList(),
                        onComplete = { dateStr, location, riskFactor, safetyMeasure, photoUris ->
                            sendResultAndFinish(dateStr, location, riskFactor, safetyMeasure, photoUris)
                        }
                    )
                }
            }
        }
    }
}
