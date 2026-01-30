package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChangePasswordActivity : AppCompatActivity() {
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

        // 변경완료 버튼
        val finishButton = findViewById<Button>(R.id.finish_button)
        finishButton.setOnClickListener {
            val newPw = etNewPw.text.toString().trim()
            val reNewPw = etReNewPw.text.toString().trim()

            // 1. 유효성 검사
            if (newPw.isEmpty() || reNewPw.isEmpty()) {
                Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pwError = getPasswordErrorMessage(newPw)
            if (pwError != null) {
                Toast.makeText(this, pwError, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPw != reNewPw) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. 서버로 변경 요청
            // FindPasswordActivity에서 전달받은 userId를 사용합니다.
            val userId = intent.getStringExtra("userId")

            if (userId == null) {
                Toast.makeText(this, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = ChangePasswordRequest(userId, newPw)
            RetrofitClient.instance.changePassword(request).enqueue(object : Callback<ChangePasswordResponse> {
                override fun onResponse(call: Call<ChangePasswordResponse>, response: Response<ChangePasswordResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ChangePasswordActivity, "비밀번호가 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show()
                        // 로그인 화면으로 이동하거나 가입 완료 화면 등으로 이동
                        val intent = Intent(this@ChangePasswordActivity, LogInActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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

    private fun getPasswordErrorMessage(pw: String): String? {
        return when {
            pw.length < 8 -> "비밀번호는 8자 이상 입력해주세요."
            !pw.any { it.isLetter() } -> "비밀번호에 영문을 포함해주세요."
            !pw.any { it.isDigit() } -> "비밀번호에 숫자를 포함해주세요."
            else -> null
        }
    }
}
