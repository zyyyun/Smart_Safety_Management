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

        // UserSession에 저장된 이름을 가져와서 환영 메시지 설정
        val userName = UserSession.userName

        val welcomeText = findViewById<TextView>(R.id.txt_welcome)
        welcomeText.text = "환영합니다 ${userName}님!"

        val loginButton = findViewById<Button>(R.id.next_button)
        loginButton.setOnClickListener {
            moveToLogin()
        }
    }

    private fun moveToLogin() {
        val intent = Intent(this, LogInActivity::class.java)
        startActivity(intent)
        finish()
    }
}