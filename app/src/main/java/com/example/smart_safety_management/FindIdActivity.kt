package com.example.smart_safety_management

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FindIdActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.find_id)

        // 비밀번호 찾기 텍스트
        val findPassword = findViewById<TextView>(R.id.tv_find_password)

        // 밑줄 추가
        findPassword.paintFlags =
            findPassword.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // 비밀번호 찾기 이동
        findPassword.setOnClickListener {
            val intent = Intent(this, FindPasswordActivity::class.java)
            startActivity(intent)
        }

        // 뒤로가기 버튼
        val backBtn = findViewById<ImageButton>(R.id.backButton)
        backBtn.setOnClickListener {
            finish()
        }

        // 다음 버튼
        val nextButton = findViewById<Button>(R.id.next_button)
        nextButton.setOnClickListener {
            val intent = Intent(this, FoundIdActivity::class.java)
            startActivity(intent)
        }
    }
}