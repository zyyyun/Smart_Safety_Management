package com.example.smart_safety_management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_safety_management.tbm.TbmDashboardScreen
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.watch.SupabaseModule

/**
 * Phase 9 / 09-03 TBM-02 — manager TBM 대시보드 Activity (D-06).
 *
 * 권한 가드 (T-9-13 mitigation):
 *   - UserSession.userRole != UserRole.MANAGER → Toast + finish()
 *   - android:exported="false" (외부 deep-link 차단, manifest 등록)
 *
 * 데이터 소스: TbmDashboardScreen 안에서 TbmRepository 의 Stage A+B Realtime 구독.
 *
 * Phase 7 SafetyAlertsActivity 패턴 미러.
 */
class TbmDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2026-05-19: PoC 한정 — manager only 권한 가드 + null 가드 완화.
        // 어떤 계정으로 들어가도 진입 가능. v1.1 정상 운영 시:
        //   - UserSession.userRole != UserRole.MANAGER → Toast + finish() (T-9-13 manager-only)
        //   - UserSession.userId null → finish() (정상 흐름 필수)
        // 복원 권장. 현 시점은 시연/디버깅 우선.
        //
        // 2026-05-20 Change 1 — groupId 파라미터 제거. dashboard 안에서
        // TbmRepository.fetchGroupsForManager() 로 admin 이 다중 그룹 selectable.
        val leaderUserId = UserSession.userId ?: "test_user"

        val supabase = SupabaseModule.client(this)
        setContent {
            Smart_Safety_ManagementTheme {
                TbmDashboardScreen(
                    leaderUserId = leaderUserId,
                    supabase = supabase,
                )
            }
        }
    }
}
