package com.example.smart_safety_management

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FoundIdActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.found_id)

        val txtId = findViewById<TextView>(R.id.txt_id)
        val userId = intent.getStringExtra("USER_ID") ?: ""

        if (userId.isNotEmpty()) {
            txtId.text = maskId(userId)
        }

        // 로그인 하러 가기 텍스트
        val goToLogin = findViewById<TextView>(R.id.txt_go_to_login)

        // 밑줄 추가
        goToLogin.paintFlags =
            goToLogin.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // 로그인 이동
        goToLogin.setOnClickListener {
            val intent = Intent(this, LogInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        // 뒤로가기 버튼
        val backBtn = findViewById<ImageButton>(R.id.backButton)
        backBtn.setOnClickListener {
            finish()
        }

        // 비밀번호 찾기 버튼
        val nextButton = findViewById<Button>(R.id.find_password_button)
        nextButton.setOnClickListener {
            val intent = Intent(this, FindPasswordActivity::class.java)
            startActivity(intent)
        }
    }

    private fun maskId(userId: String): String {
        return if (userId.length > 2) {
            userId.substring(0, 2) + "*".repeat(userId.length - 2)
        } else {
            userId
        }
    }
}
