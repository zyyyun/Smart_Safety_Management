package com.example.smart_safety_management.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

/**
 * 2026-05-21 — 워치 신체 정보 상세 화면.
 *
 * HomeActivity 의 WatchMiniCardComposable 탭 → 진입.
 * 데이터 정제는 WatchHealthFormatter 가 담당.
 *
 * Sections (위에서 아래로):
 *   1. 종합 상태 헤더 (정상/주의/위험 + 색상)
 *   2. 심박 카드 (현재 값 + 정상 범위 + 막대 게이지)
 *   3. 체온 카드 (현재 값 + 정상 범위 + 막대 게이지)
 *   4. 착용 상태 (라벨 + 마지막 전이 시각)
 *   5. 배터리 + 마지막 통신
 *   6. 최근 24시간 위험 알림 list (resolved 포함)
 */
class WatchDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceId = intent.getIntExtra(EXTRA_DEVICE_ID, -1)
        val supabase = SupabaseModule.client(this)
        setContent {
            Smart_Safety_ManagementTheme {
                WatchDetailScreen(
                    deviceId = deviceId,
                    supabase = supabase,
                    onBack = { finish() },
                )
            }
        }
    }
}
