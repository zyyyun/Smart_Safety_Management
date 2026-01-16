package com.example.smart_safety_management

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class SettingProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_my_profile)

        // 뒤로가기 버튼 클릭 시 현재 액티비티 종료
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }
    }
}