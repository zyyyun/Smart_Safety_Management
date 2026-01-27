package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChangePasswordActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.change_password)

        // 뒤로가기 버튼
        val backBtn = findViewById<ImageButton>(R.id.backButton)
        backBtn.setOnClickListener {
            finish()
        }

        val etNewPw = findViewById<TextInputEditText>(R.id.et_new_password)
        val etReNewPw = findViewById<TextInputEditText>(R.id.et_re_new_password)
        val tvNotice = findViewById<TextView>(R.id.tv_notice)

        // 변경완료 버튼
        val nextButton = findViewById<Button>(R.id.finish_button)
        nextButton.setOnClickListener {
            val newPw = etNewPw.text.toString()
            val reNewPw = etReNewPw.text.toString()

            // 1. 유효성 검사
            if (newPw.length < 8) {
                tvNotice.text = "비밀번호는 8자 이상이어야 합니다."
                tvNotice.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            if (newPw != reNewPw) {
                tvNotice.text = "비밀번호가 일치하지 않습니다."
                tvNotice.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            tvNotice.visibility = android.view.View.INVISIBLE

            // 2. 서버로 변경 요청
            // 주의: 비밀번호 찾기 흐름이라면 Intent로 user_id를 받아와야 합니다.
            // 테스트를 위해 UserSession.userId를 사용하거나, Intent에서 가져오는 로직을 사용하세요.
            val userId = intent.getStringExtra("user_id") ?: UserSession.userId

            if (userId == null) {
                Toast.makeText(this, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = ChangePasswordRequest(userId, newPw)
            RetrofitClient.instance.changePassword(request).enqueue(object : Callback<ChangePasswordResponse> {
                override fun onResponse(call: Call<ChangePasswordResponse>, response: Response<ChangePasswordResponse>) {
                    if (response.isSuccessful) {
                        val intent = Intent(this@ChangePasswordActivity, PasswordChangedActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@ChangePasswordActivity, "비밀번호 변경 실패", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<ChangePasswordResponse>, t: Throwable) {
                    Toast.makeText(this@ChangePasswordActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}