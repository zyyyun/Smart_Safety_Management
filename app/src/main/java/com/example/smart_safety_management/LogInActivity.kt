package com.example.smart_safety_management

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_safety_management.auth.SignUpField
import com.example.smart_safety_management.auth.SignUpFieldError
import com.example.smart_safety_management.auth.SignUpValidator
import com.example.smart_safety_management.ui.components.errorBannerMessage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LogInActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.log_in)

        val etId = findViewById<EditText>(R.id.et_id)
        val etPw = findViewById<EditText>(R.id.et_password)

        val findAccount = findViewById<TextView>(R.id.tv_find_account)
        findAccount.paintFlags = findAccount.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        findAccount.setOnClickListener {
            val intent = Intent(this, FindIdActivity::class.java)
            startActivity(intent)
        }

        val backBtn = findViewById<ImageButton>(R.id.backButton)
        backBtn.setOnClickListener {
            finish()
        }

        val loginBtn = findViewById<Button>(R.id.log_in_button)
        loginBtn.setOnClickListener {
            val id = etId.text.toString()
            val pw = etPw.text.toString()

            // Phase 11 / 11-02 Sub-task 2.3 — 공통 SignUpValidator + errorBannerMessage wiring (UX-01).
            // 로그인 ID 필드는 NAME 분기로 EMPTY 검증 (서버에서 형식 verify).
            val idError: SignUpFieldError? = SignUpValidator.validate(SignUpField.NAME, id)
            if (idError != null) {
                ToastUtil.showShort(this, errorBannerMessage(SignUpField.NAME, idError).replace("이름", "아이디"))
                return@setOnClickListener
            }
            val pwError: SignUpFieldError? = SignUpValidator.validate(SignUpField.PASSWORD, pw)
            if (pwError != null) {
                ToastUtil.showShort(this, errorBannerMessage(SignUpField.PASSWORD, pwError))
                return@setOnClickListener
            }

            val request = LoginRequest(id, pw)
            RetrofitClient.instance.login(request).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body?.user != null) {
                            // 1. 세션 정보 업데이트
                            UserSession.userId = body.user.userId
                            UserSession.userName = body.user.name
                            UserSession.userPhone = body.user.phoneNum
                            UserSession.userEmail = body.user.email
                            UserSession.profileImageUri = body.user.profileImageUri
                            UserSession.groupId = body.user.groupId
                            UserSession.inviteCode = body.user.inviteCode // 초대코드 저장 추가
                            UserSession.isInviteChecked = body.user.isInviteChecked // DB 값으로 동기화
                            UserSession.authToken = body.accessToken ?: body.authToken ?: body.token

                            // 2. 역할 설정
                            if (body.user.userRole == "manager" || body.user.userRole == "general_manager") {
                                UserSession.userRole = UserRole.MANAGER
                            } else {
                                UserSession.userRole = UserRole.WORKER
                            }

                            // 3. 세션 저장
                            UserSession.saveSession(this@LogInActivity)

                            // ✅ [추가] 로그인 성공 시 FCM 토큰 가져와서 서버로 전송
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val token = task.result
                                    val userId = UserSession.userId
                                    if (userId != null && token != null) {
                                        val tokenRequest = UpdateFcmTokenRequest(userId, token)
                                        RetrofitClient.instance.updateFcmToken(tokenRequest).enqueue(object : Callback<Void> {
                                            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                                                android.util.Log.d("FCM", "FCM 토큰 서버 전송 성공")
                                            }
                                            override fun onFailure(call: Call<Void>, t: Throwable) {
                                                android.util.Log.e("FCM", "FCM 토큰 서버 전송 실패", t)
                                            }
                                        })
                                    }
                                }
                            }

                            // 4. 화면 이동
                            val intent = if (UserSession.userRole == UserRole.MANAGER) {
                                Intent(this@LogInActivity, HomeActivity::class.java)
                            } else {
                                Intent(this@LogInActivity, HomeWorkerActivity::class.java)
                            }

                            ToastUtil.showShort(this@LogInActivity, "로그인 성공")
                            startActivity(intent)
                            finishAffinity() 
                        } else {
                            ToastUtil.showShort(this@LogInActivity, "로그인 실패: 사용자 정보 오류")
                        }
                    } else {
                        ToastUtil.showShort(this@LogInActivity, "로그인 실패: 아이디 또는 비밀번호를 확인하세요.")
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    ToastUtil.showShort(this@LogInActivity, "네트워크 오류: ${t.message}")
                }
            })
        }
    }
}
