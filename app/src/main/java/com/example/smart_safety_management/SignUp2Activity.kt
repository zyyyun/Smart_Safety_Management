package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SignUp2Activity : AppCompatActivity() {

    private lateinit var userRole: UserRole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_2)

        val nextButton = findViewById<Button>(R.id.next_button)

        nextButton.setOnClickListener {
            val intent = Intent(this, SignUp3Activity::class.java)
            startActivity(intent)
        }

        // String -> enum 복원
        userRole = UserRole.valueOf(
            intent.getStringExtra(EXTRA_USER_ROLE) ?: UserRole.WORKER.name
        )

        setupUI()
    }

    private fun setupUI() {
        when (userRole) {
            UserRole.WORKER -> setupWorkerUI()
            UserRole.MANAGER -> setupManagerUI()
        }
    }

    private fun setupWorkerUI() {
        // 근로자용 UI / 로직
    }

    private fun setupManagerUI() {
        // 관리자용 UI / 로직
    }

    // sign_up_3.xml로 이동 + enum 유지
    private fun moveToSignUp3() {
        val intent = Intent(this, SignUp3Activity::class.java)
        intent.putExtra(EXTRA_USER_ROLE, userRole.name) // enum 전달
        startActivity(intent)
    }
}
