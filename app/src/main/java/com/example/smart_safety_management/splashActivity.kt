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

        // 처음 상태
        logo.alpha = 0f
        logo.translationY = 0f
        btnSignUp.alpha = 0f
        btnLogin.alpha = 0f

        // 로고 페이드 인
        val fadeLogo = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f)
        fadeLogo.duration = 2000
        fadeLogo.start()

        // 정상 로그인 흐름 (2026-05-26 복구 — 이전 testuser1 manager 자동 로그인 우회 해제):
        //   - 저장된 세션 (is_logged_in=true) 이 있으면 그대로 홈으로 진입
        //   - 없으면 (또는 로그아웃 후) Sign Up / Login 버튼 노출 → 사용자가 직접 진입
        // 작업자 계정 테스트를 위해 manager 자동 로그인 차단. 이전 우회 코드는 git history 참조
        // (커밋 ce58e08 이전 SplashActivity 의 fadeLogo onAnimationEnd 블록).
        fadeLogo.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (UserSession.loadSession(this@SplashActivity)) {
                    moveToHome()
                } else {
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
