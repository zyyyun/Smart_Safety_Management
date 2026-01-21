package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class SignUp3Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_3)

        // 뒤로가기 버튼
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 완료 버튼
        findViewById<Button>(R.id.finish_button).setOnClickListener {
            moveToSignUp4()
        }
    }

    private fun moveToSignUp4() {
        val id = findViewById<EditText>(R.id.et_id).text.toString()

        val intent = Intent(this, SignUp4Activity::class.java)
        intent.putExtra(EXTRA_USER_ID, id) // 아이디만 전달 (역할은 UserSession 전역 변수 사용)
        startActivity(intent)
    }

}
