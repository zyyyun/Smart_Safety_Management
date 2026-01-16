package com.example.smart_safety_management

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SettingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting)

        // ===============================
        // 뒤로가기
        // ===============================
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 초대코드 입력 성공 여부 확인 및 UI 처리
        checkInviteCodeVisibility()
    }

    private fun checkInviteCodeVisibility() {
        val prefs = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        // '성공' 했을 때만 안 뜨게 변경
        val isInviteSuccess = prefs.getBoolean("invite_code_success", false)

        if (isInviteSuccess) {
            findViewById<LinearLayout>(R.id.enter_invite_code).visibility = View.GONE
        } else {
            findViewById<LinearLayout>(R.id.enter_invite_code).visibility = View.VISIBLE
        }
    }
}