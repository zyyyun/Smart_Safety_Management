package com.example.smart_safety_management

import android.animation.ObjectAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
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

        // 2026-05-26 — 정상 로그인 흐름 + dev 계정 picker (login Edge Function 부재 우회):
        //   - 저장된 세션 (is_logged_in=true) 이 있으면 그대로 홈으로 진입
        //   - 없으면 (또는 로그아웃 후) debug 빌드에서는 dev 계정 picker 표시,
        //     release 빌드에서는 정상 Sign Up / Login 버튼 노출
        // login Edge Function 이 운영에 미배포 상태라 LogInActivity 가 호출하는 endpoint 404.
        // login 함수 작성·배포 후엔 isDebug 분기를 release 흐름으로 통일 권장.
        fadeLogo.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (UserSession.loadSession(this@SplashActivity)) {
                    moveToHome()
                } else if (BuildConfig.DEBUG) {
                    showDevAccountPicker(logo, btnSignUp, btnLogin)
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

    /**
     * Debug 빌드 한정 — login Edge Function 부재 우회용 dev 계정 picker.
     *
     * 운영의 `POST /functions/v1/login` 이 404 (Edge Function 미배포) 라 LogInActivity 가
     * 실제 로그인 불가. login 함수 작성·배포 후엔 이 dialog 를 제거하고 isDebug 분기를
     * showAuthButtons() 로 통일.
     *
     * 선택한 계정으로 UserSession 을 직접 세팅 + saveSession 호출 → 다음 진입 시 자동 로그인.
     * 다른 계정 테스트하려면 앱 내 로그아웃 → clearSession → 재진입 → 다시 picker.
     */
    private fun showDevAccountPicker(logo: View, btnSignUp: View, btnLogin: View) {
        val accounts = arrayOf(
            "testuser1 (관리자)",
            "testuser_w1 (작업자 1)",
            "testuser_w2 (작업자 2)",
            "testuser_w3 (작업자 3)",
        )
        AlertDialog.Builder(this)
            .setTitle("테스트 계정 선택 (개발 빌드 전용)")
            .setItems(accounts) { _, which ->
                when (which) {
                    0 -> applyDevSession("testuser1", "관리자", UserRole.MANAGER)
                    1 -> applyDevSession("testuser_w1", "작업자 1", UserRole.WORKER)
                    2 -> applyDevSession("testuser_w2", "작업자 2", UserRole.WORKER)
                    3 -> applyDevSession("testuser_w3", "작업자 3", UserRole.WORKER)
                }
            }
            .setOnCancelListener {
                // Picker 취소 시 — 일반 인증 버튼으로 fallback
                showAuthButtons(logo, btnSignUp, btnLogin)
            }
            .show()
    }

    private fun applyDevSession(userId: String, name: String, role: UserRole) {
        // Phase 9 seed (010_watch_pipeline.sql + scripts/seed_tbm_demo.py) 와 정합:
        //   testuser1 (manager) / testuser_w1·w2·w3 (worker) 모두 group_id=1.
        UserSession.userId = userId
        UserSession.userName = name
        UserSession.userRole = role
        UserSession.isInviteChecked = true
        UserSession.groupId = "1"
        UserSession.inviteCode = "TEST"
        UserSession.saveSession(this)
        Log.d("SplashActivity", "dev session applied: $userId / $role")
        moveToHome()
    }
}
