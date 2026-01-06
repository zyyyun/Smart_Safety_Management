package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class TitleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.title)

        // 1. 회원가입 버튼 가져오기
        val signUpButton = findViewById<Button>(R.id.button)

        // 2. 클릭 이벤트
        signUpButton.setOnClickListener {
            val intent = Intent(this, SignUp1Activity::class.java)
            startActivity(intent)
        }

        // 로그인
        val logInButton = findViewById<Button>(R.id.button2)
        logInButton.setOnClickListener {
            val intent = Intent(this, LogInActivity::class.java)
            startActivity(intent)
        }
    }
}
