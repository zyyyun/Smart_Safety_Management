package com.example.smart_safety_management

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_safety_management.tbm.OpsCatalogScreen
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.watch.SupabaseModule

class SettingOpsCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (UserSession.userRole != UserRole.MANAGER) {
            Toast.makeText(this, "관리자만 접근 가능합니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val userId = UserSession.userId ?: run {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val supabase = SupabaseModule.client(this)
        setContent {
            Smart_Safety_ManagementTheme {
                OpsCatalogScreen(userId = userId, supabase = supabase)
            }
        }
    }
}
