package com.example.smart_safety_management

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogInActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.log_in)

        // 아이디/비밀번호 찾기 텍스트
        val findAccount = findViewById<TextView>(R.id.tv_find_account)

        // 밑줄 추가
        findAccount.paintFlags =
            findAccount.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // 클릭 이벤트
        findAccount.setOnClickListener {
            val intent = Intent(this, FindIdActivity::class.java)
            startActivity(intent)
        }

        // 뒤로가기 버튼
        val backBtn = findViewById<ImageButton>(R.id.backButton)
        backBtn.setOnClickListener {
            finish()
        }

        // 로그인 버튼
        val loginBtn = findViewById<Button>(R.id.log_in_button)
        loginBtn.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
        }
    }
}
