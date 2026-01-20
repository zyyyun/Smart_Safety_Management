package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

class SettingInviteActivity : AppCompatActivity() {

    private lateinit var tabManager: TextView
    private lateinit var tabWorker: TextView
    private lateinit var underlineManager: View
    private lateinit var underlineWorker: View
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_invite)

        // 뷰 초기화
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val btnInvite = findViewById<Button>(R.id.btn_invite)
        tabManager = findViewById(R.id.tab_manager)
        tabWorker = findViewById(R.id.tab_worker)
        underlineManager = findViewById(R.id.underline_manager)
        underlineWorker = findViewById(R.id.underline_worker)
        emptyText = findViewById(R.id.emptyText)

        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 연락처로 초대문자 발송 버튼 클릭 시
        btnInvite.setOnClickListener {
            val intent = Intent(this, SettingInvitePhonenumberActivity::class.java)
            startActivity(intent)
        }

        // 초기 상태: 관리자 선택
        updateTabState(isManagerSelected = true)

        // 관리자 탭 클릭
        tabManager.setOnClickListener {
            updateTabState(isManagerSelected = true)
        }

        // 근로자 탭 클릭
        tabWorker.setOnClickListener {
            updateTabState(isManagerSelected = false)
        }
    }

    private fun updateTabState(isManagerSelected: Boolean) {
        if (isManagerSelected) {
            // 관리자 선택 시
            tabManager.setTextColor(ContextCompat.getColor(this, R.color.orange500))
            tabManager.typeface = ResourcesCompat.getFont(this, R.font.pretendard_bold)
            underlineManager.setBackgroundColor(ContextCompat.getColor(this, R.color.orange500))
            underlineManager.visibility = View.VISIBLE

            // 근로자 해제 시
            tabWorker.setTextColor(ContextCompat.getColor(this, R.color.gray500_gray650))
            tabWorker.typeface = ResourcesCompat.getFont(this, R.font.pretendard_medium)
            underlineWorker.setBackgroundColor(ContextCompat.getColor(this, R.color.gray500_gray650))
            
            emptyText.text = "아직 초대된 관리자가 없어요"
        } else {
            // 근로자 선택 시
            tabWorker.setTextColor(ContextCompat.getColor(this, R.color.orange500))
            tabWorker.typeface = ResourcesCompat.getFont(this, R.font.pretendard_bold)
            underlineWorker.setBackgroundColor(ContextCompat.getColor(this, R.color.orange500))

            // 관리자 해제 시
            tabManager.setTextColor(ContextCompat.getColor(this, R.color.gray500_gray650))
            tabManager.typeface = ResourcesCompat.getFont(this, R.font.pretendard_medium)
            underlineManager.setBackgroundColor(ContextCompat.getColor(this, R.color.gray500_gray650))
            
            emptyText.text = "아직 초대된 근로자가 없어요"
        }
    }
}
