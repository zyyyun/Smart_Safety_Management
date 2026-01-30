package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignUp4Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_4)

        // DB에 유저 정보 생성 요청
        registerUser()

        val welcomeText = findViewById<TextView>(R.id.txt_welcome)
        welcomeText.text = "환영합니다 ${UserSession.userName}님!"

        val loginButton = findViewById<Button>(R.id.next_button)
        loginButton.setOnClickListener {
            moveToLogin()
        }
    }

    private fun registerUser() {
        val signUpData = SignUpRequest(
            userId = UserSession.userId ?: "",
            password = UserSession.userPassword ?: "",
            userRole = UserSession.userRole.name.lowercase(),
            name = UserSession.userName,
            email = UserSession.userEmail,
            phoneNum = UserSession.userPhone,
            groupId = null
        )

        RetrofitClient.instance.signUp(signUpData).enqueue(object : Callback<SignUpResponse> {
            override fun onResponse(call: Call<SignUpResponse>, response: Response<SignUpResponse>) {
                if (response.isSuccessful) {
                    // 가입 성공 - UserSession에서 임시 비밀번호 삭제
                    UserSession.userPassword = null
                    Toast.makeText(this@SignUp4Activity, "회원가입이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SignUp4Activity, "회원가입 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    // 실패 시 처리 (예: 다시 가입 시도하거나 이전 화면으로 이동)
                }
            }

            override fun onFailure(call: Call<SignUpResponse>, t: Throwable) {
                Toast.makeText(this@SignUp4Activity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun moveToLogin() {
        val intent = Intent(this, LogInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
