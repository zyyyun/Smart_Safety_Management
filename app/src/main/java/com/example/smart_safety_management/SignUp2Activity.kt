package com.example.smart_safety_management

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SignUp2Activity : AppCompatActivity() {

    private lateinit var userRole: UserRole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_2)

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
}
