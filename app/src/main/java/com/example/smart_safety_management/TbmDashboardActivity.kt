package com.example.smart_safety_management

import android.os.Bundle
import android.widget.Toast
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

        // 권한 가드 — manager only (T-9-13)
        if (UserSession.userRole != UserRole.MANAGER) {
            Toast.makeText(this, "관리자만 접근 가능합니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val leaderUserId = UserSession.userId ?: run {
            Toast.makeText(this, "사용자 정보를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val groupId = UserSession.groupId?.toIntOrNull() ?: run {
            Toast.makeText(this, "그룹 정보가 없습니다. 초대코드를 먼저 입력해주세요", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val supabase = SupabaseModule.client(this)
        setContent {
            Smart_Safety_ManagementTheme {
                TbmDashboardScreen(
                    leaderUserId = leaderUserId,
                    groupId = groupId,
                    supabase = supabase,
                )
            }
        }
    }
}
