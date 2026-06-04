package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_safety_management.auth.SignUpField
import com.example.smart_safety_management.auth.SignUpFieldError
import com.example.smart_safety_management.auth.SignUpValidator
import com.example.smart_safety_management.ui.components.errorBannerMessage

class SignUp4Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_4)

        // 회원가입 처리는 이전 단계(SignUp3Activity)에서 성공적으로 완료됨

        // Phase 11 / 11-02 Sub-task 2.3 — 공통 SignUpValidator 로 이름 sanity check (UX-01).
        // welcome 화면 진입 시 UserSession.userName 이 비어있으면 generic fallback.
        val userName = UserSession.userName
        val nameError: SignUpFieldError? = SignUpValidator.validate(SignUpField.NAME, userName)
        val welcomeText = findViewById<TextView>(R.id.txt_welcome)
        welcomeText.text = if (nameError != null) {
            // 가입 흐름이 정상이면 거의 발생 X — 방어적 처리 (errorBannerMessage 참조 유지).
            errorBannerMessage(SignUpField.NAME, nameError) + " — 환영합니다!"
        } else {
            "환영합니다 ${userName}님!"
        }

        val loginButton = findViewById<Button>(R.id.next_button)
        loginButton.setOnClickListener {
            moveToLogin()
        }
    }

    private fun moveToLogin() {
        val intent = Intent(this, LogInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
