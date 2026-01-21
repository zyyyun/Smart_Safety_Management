package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AppCompatActivity

class SignUp2Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_2)

        // 뒤로가기 버튼
        findViewById<ImageButton>(R.id.imageButton).setOnClickListener {
            finish()
        }

        // 다음 버튼
        findViewById<Button>(R.id.next_button).setOnClickListener {
            saveNameAndMoveToNext()
        }
    }

    private fun saveNameAndMoveToNext() {
        var name = findViewById<TextInputEditText>(R.id.et_name).text.toString().trim()
        
        // 이름이 빈칸일 경우 기본값 설정
        if (name.isEmpty()) {
            name = if (UserSession.userRole == UserRole.WORKER) {
                "이강인"
            } else {
                "안정우"
            }
        }
        
        // 이름 저장 (UserSession 전역 변수 사용)
        UserSession.userName = name

        startActivity(Intent(this, SignUp3Activity::class.java))
    }
}