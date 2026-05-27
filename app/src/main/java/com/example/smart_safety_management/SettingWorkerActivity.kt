package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SettingWorkerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_worker)

        // Phase 11 / 11-02 Sub-task 3.2 — common_toolbar wiring (UX-03).
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            tb.setNavigationOnClickListener { finish() }
        }

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
        fetchUserInfo()
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

    private fun fetchUserInfo() {
        val userId = UserSession.userId ?: return
        RetrofitClient.instance.getUserInfo(userId).enqueue(object : Callback<GetUserInfoResponse> {
            override fun onResponse(call: Call<GetUserInfoResponse>, response: Response<GetUserInfoResponse>) {
                if (response.isSuccessful) {
                    val userInfo = response.body()
                    if (userInfo != null) {
                        UserSession.isInviteChecked = userInfo.isInviteChecked
                        UserSession.groupId = userInfo.groupId?.toString()
                        UserSession.inviteCode = userInfo.inviteCode
                        UserSession.saveSession(this@SettingWorkerActivity)

                        checkInviteCodeVisibility()
                    }
                }
            }
            override fun onFailure(call: Call<GetUserInfoResponse>, t: Throwable) {}
        })
    }
}
