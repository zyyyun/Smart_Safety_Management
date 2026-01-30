package com.example.smart_safety_management

import android.animation.ObjectAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View

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
                    // 로그인 되어 있으면 바로 홈화면으로 이동 (딜레이 후)
                    logo.postDelayed({
                        val intent = if (UserSession.userRole == UserRole.MANAGER) {
                            Intent(this@SplashActivity, HomeActivity::class.java)
                        } else {
                            Intent(this@SplashActivity, HomeWorkerActivity::class.java)
                        }
                        startActivity(intent)
                        finish()
                    }, 1000)
                } else {
                    // 로그인 안 되어 있으면 버튼 등장
                    logo.postDelayed({
                        // 로고 위로 이동
                        val moveLogoUp = ObjectAnimator.ofFloat(
                            logo,
                            "translationY",
                            0f,
                            -150f   // 위로 이동
                        )
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
        })
        
        // 회원가입 버튼 이동
        btnSignUp.setOnClickListener {
            val intent = Intent(this, SignUp1Activity::class.java)
            startActivity(intent)
        }

        // 로그인 버튼 이동
        btnLogin.setOnClickListener {
            val intent = Intent(this, LogInActivity::class.java)
            startActivity(intent)
        }
    }
}
