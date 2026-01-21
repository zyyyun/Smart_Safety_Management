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

        val backBtn = findViewById<ImageButton>(R.id.imageButton)
        backBtn.setOnClickListener {
            finish()
        }

        val btnWorker = findViewById<ConstraintLayout>(R.id.btn_worker)
        val btnManager = findViewById<ConstraintLayout>(R.id.btn_manager)

        btnWorker.setOnClickListener {
            UserSession.userRole = UserRole.WORKER
            moveToNext()
        }

        btnManager.setOnClickListener {
            UserSession.userRole = UserRole.MANAGER
            moveToNext()
        }
    }

    private fun moveToNext() {
        startActivity(Intent(this, SignUp2Activity::class.java))
    }
}