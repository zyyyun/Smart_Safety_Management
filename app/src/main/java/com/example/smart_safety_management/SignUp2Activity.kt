package com.example.smart_safety_management

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AppCompatActivity

class SignUp2Activity : AppCompatActivity() {

    private lateinit var userRole: UserRole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_2)

        // 뒤로가기 버튼
        val backBtn = findViewById<ImageButton>(R.id.imageButton)
        backBtn.setOnClickListener {
            finish()
        }

        // 다음 버튼
        val nextButton = findViewById<Button>(R.id.next_button)
        nextButton.setOnClickListener {
            saveNameAndMoveToNext()
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

    private fun saveNameAndMoveToNext() {
        val name = findViewById<TextInputEditText>(R.id.et_name).text.toString().trim()
        
        // 이름 저장
        val sharedPref = getSharedPreferences(PREF_USER_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(KEY_USER_NAME, name)
            apply()
        }

        val intent = Intent(this, SignUp3Activity::class.java)
        intent.putExtra(EXTRA_USER_ROLE, userRole.name) // enum 전달
        startActivity(intent)
    }
}
