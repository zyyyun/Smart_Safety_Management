package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SignUp4Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_4)

        // 회원가입 처리는 이전 단계(SignUp3Activity)에서 성공적으로 완료됨

        val welcomeText = findViewById<TextView>(R.id.txt_welcome)
        welcomeText.text = "환영합니다 ${UserSession.userName}님!"

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
