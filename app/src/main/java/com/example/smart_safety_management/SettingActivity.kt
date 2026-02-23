package com.example.smart_safety_management

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_safety_management.screens.afci.ArcBreakerManageActivity

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
            val intent = Intent(this@SettingActivity, SettingWorkplaceLocationActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.item_workplace_area_setting).setOnClickListener {
            val intent = Intent(this, SettingWorkplaceAreaActivity::class.java)
            startActivity(intent)
        }


        // 초대하기 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_invite).setOnClickListener {
            val intent = Intent(this, SettingInviteActivity::class.java)
            startActivity(intent)
        }

        // 인원 관리 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_people_management).setOnClickListener {
            val intent = Intent(this, SettingPeopleManagementActivity::class.java)
            startActivity(intent)
        }

        // cctv 관리 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_cctv_management).setOnClickListener {
            val intent = Intent(this, SettingCctvManagementActivity::class.java)
            startActivity(intent)
        }

        // 장치 관리 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.item_machine_management).setOnClickListener {
            val intent = Intent(this, SettingDeviceManagementActivity::class.java)
            startActivity(intent)
        }

        // 화재 예방 장치 관리 아이템 클릭 시 화면 이동
        findViewById<LinearLayout>(R.id.fire_prevention_device_management).setOnClickListener {
            val intent = Intent(this, FireAlarmManageActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.AFCI_management).setOnClickListener {
            val intent = Intent(this, ArcBreakerManageActivity::class.java)
            startActivity(intent)
        }

        // 초대코드 입력 여부 확인 및 UI 처리
        checkInviteCodeVisibility()
    }
    override fun onResume() {
        super.onResume()
        // 다른 화면에서 돌아왔을 때 UI를 다시 확인하여 갱신
        checkInviteCodeVisibility()
    }

    private fun checkInviteCodeVisibility() {
        // 초대코드 입력 메뉴 노출 여부: is_invite_checked가 false일 때만 노출
        val isJoined = UserSession.isInviteChecked
        
        if (isJoined) {
            findViewById<LinearLayout>(R.id.item_enter_invite_code).visibility = View.GONE
        } else {
            findViewById<LinearLayout>(R.id.item_enter_invite_code).visibility = View.VISIBLE
        }
    }
}
