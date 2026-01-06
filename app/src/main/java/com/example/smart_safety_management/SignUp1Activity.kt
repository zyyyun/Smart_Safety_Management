package com.example.smart_safety_management

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class SignUp1Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_1)

        val backBtn = findViewById<ImageButton>(R.id.imageButton)

        backBtn.setOnClickListener {    // 뒤로가기 버튼 클릭
            finish()    // 전 화면으로 이동
        }
    }
}
