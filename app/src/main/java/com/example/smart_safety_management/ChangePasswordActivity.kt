package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.change_password)

        // 뒤로가기 버튼
        val backBtn = findViewById<ImageButton>(R.id.backButton)
        backBtn.setOnClickListener {
            finish()
        }

        // 변경완료 버튼
        val nextButton = findViewById<Button>(R.id.finish_button)
        nextButton.setOnClickListener {
            val intent = Intent(this, PasswordChangedActivity::class.java)
            startActivity(intent)
        }
    }
}