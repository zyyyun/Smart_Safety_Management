package com.example.smart_safety_management

import android.animation.ObjectAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.logoGroup)
        val btnSignUp = findViewById<View>(R.id.btnSignUp)
        val btnLogin = findViewById<View>(R.id.btnLogin)

        // 자동 로그인 체크
        val isLoggedIn = UserSession.loadSession(this)

        // 처음 상태
        logo.alpha = 0f
        logo.translationY = 0f
        btnSignUp.alpha = 0f
        btnLogin.alpha = 0f

        // 로고 페이드 인
        val fadeLogo = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f)
        fadeLogo.duration = 2000
        fadeLogo.start()

        // 로고 끝난 뒤 동작
        fadeLogo.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (isLoggedIn) {
                    // 서버에 계정 존재 여부 확인 (계정이 삭제되었거나 문제가 생겼을 경우 대비)
                    val userId = UserSession.userId ?: ""
                    RetrofitClient.instance.getUsers(userId).enqueue(object : Callback<GetUsersResponse> {
                        override fun onResponse(call: Call<GetUsersResponse>, response: Response<GetUsersResponse>) {
                            if (response.isSuccessful) {
                                // 계정 존재 확인됨 -> 홈 화면으로
                                // 서버에서 최신 유저 정보를 가져와 세션 업데이트 (is_invited_checked 동기화)
                                val users = response.body()?.users
                                val currentUser = users?.find { it.userId == userId }
                                if (currentUser != null) {
                                    UserSession.isInviteChecked = currentUser.isInvitedChecked
                                    UserSession.saveSession(this@SplashActivity)
                                }
                                moveToHome()
                            } else if (response.code() == 404) {
                                // 계정을 찾을 수 없음 (서버에서 삭제됨 등) -> 세션 지우고 타이틀로
                                UserSession.clearSession(this@SplashActivity)
                                showAuthButtons(logo, btnSignUp, btnLogin)
                            } else {
                                // 기타 서버 오류 상황 -> 일단 홈으로 보내거나 네트워크 체크 유도 (여기선 홈으로)
                                moveToHome()
                            }
                        }

                        override fun onFailure(call: Call<GetUsersResponse>, t: Throwable) {
                            // 네트워크 오류 시에도 일단 저장된 세션으로 진입 시도
                            moveToHome()
                        }
                    })
                } else {
                    // 로그인 안 되어 있으면 버튼 등장
                    showAuthButtons(logo, btnSignUp, btnLogin)
                }
            }
        })
        
        btnSignUp.setOnClickListener {
            val intent = Intent(this, SignUp1Activity::class.java)
            startActivity(intent)
        }

        btnLogin.setOnClickListener {
            val intent = Intent(this, LogInActivity::class.java)
            startActivity(intent)
        }
    }

    private fun moveToHome() {
        val intent = if (UserSession.userRole == UserRole.MANAGER) {
            Intent(this@SplashActivity, HomeActivity::class.java)
        } else {
            Intent(this@SplashActivity, HomeWorkerActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    private fun showAuthButtons(logo: View, btnSignUp: View, btnLogin: View) {
        logo.postDelayed({
            val moveLogoUp = ObjectAnimator.ofFloat(logo, "translationY", 0f, -150f)
            moveLogoUp.duration = 700
            moveLogoUp.start()

            val fadeBtn1 = ObjectAnimator.ofFloat(btnSignUp, "alpha", 0f, 1f)
            fadeBtn1.duration = 700
            fadeBtn1.start()

            val fadeBtn2 = ObjectAnimator.ofFloat(btnLogin, "alpha", 0f, 1f)
            fadeBtn2.duration = 700
            fadeBtn2.start()
        }, 1000)
    }
}
