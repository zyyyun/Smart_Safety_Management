package com.example.smart_safety_management

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LogInActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.log_in)

        val etId = findViewById<EditText>(R.id.et_id)
        val etPw = findViewById<EditText>(R.id.et_password)

        // 아이디/비밀번호 찾기 텍스트
        val findAccount = findViewById<TextView>(R.id.tv_find_account)

        // 밑줄 추가
        findAccount.paintFlags =
            findAccount.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // 클릭 이벤트
        findAccount.setOnClickListener {
            val intent = Intent(this, FindIdActivity::class.java)
            startActivity(intent)
        }

        // 뒤로가기 버튼
        val backBtn = findViewById<ImageButton>(R.id.backButton)
        backBtn.setOnClickListener {
            finish()
        }

        // 로그인 버튼
        val loginBtn = findViewById<Button>(R.id.log_in_button)
        loginBtn.setOnClickListener {
            val id = etId.text.toString()
            val pw = etPw.text.toString()

            if (id.isBlank() || pw.isBlank()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = LoginRequest(id, pw)
            RetrofitClient.instance.login(request).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body?.user != null) {
                            // 1. 세션 정보 업데이트 (이름)
                            UserSession.userId = body.user.userId
                            UserSession.userName = body.user.name
                            UserSession.userPhone = body.user.phoneNum
                            UserSession.userEmail = body.user.email

                            // 2. 역할에 따른 화면 이동 및 세션 역할 설정
                            val intent = if (body.user.userRole == "manager") {
                                UserSession.userRole = UserRole.MANAGER
                                Intent(this@LogInActivity, HomeActivity::class.java)
                            } else {
                                UserSession.userRole = UserRole.WORKER
                                Intent(this@LogInActivity, HomeWorkerActivity::class.java)
                            }

                            Toast.makeText(this@LogInActivity, "로그인 성공", Toast.LENGTH_SHORT).show()
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LogInActivity, "로그인 실패: 사용자 정보 오류", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@LogInActivity, "로그인 실패: 아이디 또는 비밀번호를 확인하세요.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Toast.makeText(this@LogInActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}
