package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_safety_management.tbm.TbmWorkerScreen
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.watch.SupabaseModule

/**
 * Phase 9 / 09-03 TBM-02 — worker TBM 가이드 Activity (D-07).
 *
 * 진입 경로:
 *   1. HomeWorkerActivity TBM 카드 클릭
 *   2. FCM tbm_alert (action_in_app=tbm-started 또는 tbm-missed) 클릭
 *
 * FCM extras 의 session_id 는 신뢰 X — DB 재조회 (Phase 7 D-02 anti-pattern 회피).
 * sessionHintFromFcm 만 hint 로 받고 TbmWorkerScreen 내부에서 todaySessionFlow 로 재조회.
 *
 * Phase 7 SafetyAlertsActivity 패턴 미러.
 */
class TbmWorkerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2026-05-19: PoC 한정 fallback — userId/groupId 미설정 시 test_user / group_id=1 (Plan 09-01 seed) 사용.
        // 어떤 계정으로 들어가도 진입 가능. v1.1 정상 운영 시 ?: finish() 로 복원.
        val userId = UserSession.userId ?: "test_user"
        val groupId = UserSession.groupId?.toIntOrNull() ?: 1

        val sessionHintFromFcm = intent.getLongExtra(EXTRA_SESSION_ID, -1L).takeIf { it > 0 }

        val supabase = SupabaseModule.client(this)
        setContent {
            Smart_Safety_ManagementTheme {
                TbmWorkerScreen(
                    groupId = groupId,
                    userId = userId,
                    supabase = supabase,
                    sessionHintFromFcm = sessionHintFromFcm,
                )
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_tbm_session_id"
    }
}
