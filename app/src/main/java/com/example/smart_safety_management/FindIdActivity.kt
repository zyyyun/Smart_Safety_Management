package com.example.smart_safety_management

import android.graphics.Paint
import android.os.Bundle
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
        }
}