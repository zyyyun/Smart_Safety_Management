package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SettingWorkerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_worker)

        // 뒤로가기
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 내 정보
        findViewById<LinearLayout>(R.id.item_my_info).setOnClickListener {
            startActivity(Intent(this, SettingProfileActivity::class.java))
        }

        // 비밀번호 수정
        findViewById<LinearLayout>(R.id.item_change_password).setOnClickListener {
            startActivity(Intent(this, SettingChangePasswordActivity::class.java))
        }

        // 초대코드 입력
        findViewById<LinearLayout>(R.id.item_enter_invite_code).setOnClickListener {
            startActivity(Intent(this, SettingInviteCodeActivity::class.java))
        }

        // 인원현황
        findViewById<LinearLayout>(R.id.item_people_management).setOnClickListener {
            startActivity(Intent(this, SettingPeopleManagementActivity::class.java))
        }

        // 기기관리
        findViewById<LinearLayout>(R.id.item_machine_management).setOnClickListener {
            startActivity(Intent(this, DeviceManageWorkerActivity::class.java))
        }

        checkInviteCodeVisibility()
    }

    override fun onResume() {
        super.onResume()
        checkInviteCodeVisibility()
    }

    private fun checkInviteCodeVisibility() {
        if (UserSession.isInviteSuccessWorker) {
            findViewById<LinearLayout>(R.id.item_enter_invite_code).visibility = View.GONE
        } else {
            findViewById<LinearLayout>(R.id.item_enter_invite_code).visibility = View.VISIBLE
        }
    }
}
