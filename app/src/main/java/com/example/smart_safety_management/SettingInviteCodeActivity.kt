package com.example.smart_safety_management

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SettingInviteCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_invite_code)

        // Phase 11 / 11-02 Sub-task 3.2 — common_toolbar wiring (UX-03).
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            tb.setNavigationOnClickListener { finish() }
        }

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val etInviteCode = findViewById<EditText>(R.id.et_invite_code)
        val btnSubmit = findViewById<AppCompatButton>(R.id.btn_submit)

        btnSubmit.isEnabled = false

        backButton.setOnClickListener {
            finish()
        }

        etInviteCode.doAfterTextChanged { text ->
            val isNotEmpty = !text.isNullOrBlank()
            if (isNotEmpty) {
                btnSubmit.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.orange500))
                btnSubmit.setTextColor(ContextCompat.getColor(this, R.color.white))
                btnSubmit.isEnabled = true
            } else {
                btnSubmit.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray50_gray900))
                btnSubmit.setTextColor(ContextCompat.getColor(this, R.color.gray400_gray700))
                btnSubmit.isEnabled = false
            }
        }

        btnSubmit.setOnClickListener {
            val inputCode = etInviteCode.text.toString().trim()
            val userId = UserSession.userId ?: return@setOnClickListener

            // 서버 API 호출: 그룹 참여
            val request = JoinGroupRequest(userId, inputCode)
            RetrofitClient.instance.joinGroup(request).enqueue(object : Callback<JoinGroupResponse> {
                override fun onResponse(call: Call<JoinGroupResponse>, response: Response<JoinGroupResponse>) {
                    if (response.isSuccessful) {
                        // 성공: 세션 업데이트 (isInviteChecked, groupId)
                        UserSession.isInviteChecked = true
                        UserSession.groupId = response.body()?.groupId
                        UserSession.inviteCode = inputCode // ✅ 입력한 초대코드를 세션에 저장
                        UserSession.saveSession(this@SettingInviteCodeActivity)

                        ToastUtil.showShort(this@SettingInviteCodeActivity, "그룹에 성공적으로 참여했습니다.")
                        finish()
                    } else {
                        // 유효하지 않은 코드 등 에러 처리
                        showFailConfirmDialog()
                    }
                }

                override fun onFailure(call: Call<JoinGroupResponse>, t: Throwable) {
                    ToastUtil.showShort(this@SettingInviteCodeActivity, "네트워크 오류가 발생했습니다.")
                }
            })
        }
    }
}

private fun SettingInviteCodeActivity.showFailConfirmDialog() {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.view_confirm_action_dialog, null)
    val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_title_message)
    val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tv_message)
    val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btn_cancel)
    val btnExit = dialogView.findViewById<android.widget.Button>(R.id.btn_exit)

    tvTitle.text = "잘못된 초대코드입니다."
    tvMessage.text = "올바른 초대코드를 입력해주세요"
    btnCancel.visibility = View.GONE
    btnExit.text = "확인"

    btnExit.setOnClickListener { dialog.dismiss() }
    dialog.show()
    dialog.window?.apply {
        val params = attributes
        params.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        attributes = params
    }
}
