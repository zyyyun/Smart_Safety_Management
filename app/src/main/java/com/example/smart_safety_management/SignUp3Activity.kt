package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class SignUp3Activity : AppCompatActivity() {

    private lateinit var userRole: UserRole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_3)

        // enum 복원
        userRole = UserRole.valueOf(
            intent.getStringExtra(EXTRA_USER_ROLE) ?: UserRole.WORKER.name
        )

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
        val intent = Intent(this, SignUp4Activity::class.java)
        intent.putExtra(EXTRA_USER_ROLE, userRole.name) // enum 유지
        startActivity(intent)
    }
}
