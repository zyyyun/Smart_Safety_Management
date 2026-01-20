package com.example.smart_safety_management

import android.content.Context
import android.content.Intent
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

        // 내 정보 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_my_info).setOnClickListener {
            val intent = Intent(this, SettingProfileActivity::class.java)
            startActivity(intent)
        }

        // 비밀번호 수정 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_change_password).setOnClickListener {
            val intent = Intent(this, SettingChangePasswordActivity::class.java)
            startActivity(intent)
        }

        // 초대코드 입력 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_enter_invite_code).setOnClickListener {
            val intent = Intent(this, SettingInviteCodeActivity::class.java)
            startActivity(intent)
        }

        // 현장 만들기 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_create_workplace).setOnClickListener {
            val intent = Intent(this, SettingCreateWorkplaceActivity::class.java)
            startActivity(intent)
        }

        // 현장 위치 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_workplace_location_setting).setOnClickListener {
            //val intent = Intent(this, SettingWorkplaceLocationActivity::class.java)
            startActivity(intent)
        }

        // 초대하기 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_invite).setOnClickListener {
            val intent = Intent(this, SettingInviteActivity::class.java)
            startActivity(intent)
        }

        // 인원 관리 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_people_management).setOnClickListener {
            //val intent = Intent(this, SettingPeopleManagementActivity::class.java)
            startActivity(intent)
        }

        // cctv 관리 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_cctv_management).setOnClickListener {
            //val intent = Intent(this, SettingCctvManagementActivity::class.java)
            startActivity(intent)
        }

        // 장치 관리 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_machine_management).setOnClickListener {
            //val intent = Intent(this, SettingMachineManagementActivity::class.java)
            startActivity(intent)
        }

        // 초대코드 입력 성공 여부 확인 및 UI 처리
        checkInviteCodeVisibility()
    }

    private fun checkInviteCodeVisibility() {
        val prefs = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        // '성공' 했을 때만 안 뜨게 변경
        val isInviteSuccess = prefs.getBoolean("invite_code_success", false)

        if (isInviteSuccess) {
            findViewById<LinearLayout>(R.id.item_enter_invite_code).visibility = View.GONE
        } else {
            findViewById<LinearLayout>(R.id.item_enter_invite_code).visibility = View.VISIBLE
        }
    }
}
