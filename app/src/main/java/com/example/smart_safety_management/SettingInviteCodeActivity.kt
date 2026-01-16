package com.example.smart_safety_management

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged

class SettingInviteCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_invite_code)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val etInviteCode = findViewById<EditText>(R.id.et_invite_code)
        val btnSubmit = findViewById<AppCompatButton>(R.id.btn_submit)

        // 초기 상태: 버튼 비활성화
        btnSubmit.isEnabled = false

        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 입력창 텍스트 변경 감지
        etInviteCode.doAfterTextChanged { text ->
            val isNotEmpty = !text.isNullOrBlank()

            if (isNotEmpty) {
                btnSubmit.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.orange500)
                )
                btnSubmit.setTextColor(ContextCompat.getColor(this, R.color.white))
                btnSubmit.isEnabled = true
            } else {
                btnSubmit.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.gray50_gray900)
                )
                btnSubmit.setTextColor(ContextCompat.getColor(this, R.color.gray400_gray700))
                btnSubmit.isEnabled = false
            }
        }

        // 입력하기 버튼 클릭 시
        btnSubmit.setOnClickListener {
            val inputCode = etInviteCode.text.toString().trim()
            
            if (inputCode == "1234") {
                // 초대코드 일치 시 저장
                saveInviteSuccess()
                Toast.makeText(this, "초대코드가 확인되었습니다.", Toast.LENGTH_SHORT).show()
                finish() // 이전 화면으로 돌아감
            } else {
                // 일치하지 않을 때 (필요시 에러 처리 추가)
                Toast.makeText(this, "잘못된 초대코드입니다.", Toast.LENGTH_SHORT).show()
                etInviteCode.setBackgroundResource(R.drawable.bg_edittext_error)
            }
        }
    }

    private fun saveInviteSuccess() {
        val prefs = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE).edit()
        prefs.putBoolean("invite_code_done", true)
        prefs.putBoolean("invite_code_success", true)
        prefs.apply()
    }
}