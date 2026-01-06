package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

class SignUp1Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_1)

        // 뒤로가기 버튼
        val backBtn = findViewById<ImageButton>(R.id.imageButton)
        backBtn.setOnClickListener {
            finish()
        }

        // 카드형 버튼들 (ConstraintLayout)
        val btnWorker = findViewById<ConstraintLayout>(R.id.btn_worker)
        val btnManager = findViewById<ConstraintLayout>(R.id.btn_manager)

        // 근로자로 가입하기
        btnWorker.setOnClickListener {
            moveToSignUp2(UserRole.WORKER)
        }

        // 관리자로 가입하기
        btnManager.setOnClickListener {
            moveToSignUp2(UserRole.MANAGER)
        }
    }

    // sign_up_2.xml 화면으로 이동 + enum 전달
    private fun moveToSignUp2(role: UserRole) {
        val intent = Intent(this, SignUp2Activity::class.java)
        intent.putExtra(EXTRA_USER_ROLE, role.name) // enum → String
        startActivity(intent)
    }
}
