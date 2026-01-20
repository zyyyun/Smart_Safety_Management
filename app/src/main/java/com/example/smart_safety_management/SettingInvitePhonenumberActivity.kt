package com.example.smart_safety_management

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SettingInvitePhonenumberActivity : AppCompatActivity() {

    private var isSearchVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_invite_phonenumber)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val searchIcon = findViewById<ImageView>(R.id.searchIcon)
        val searchEdit = findViewById<EditText>(R.id.searchEdit)

        // 초기 상태: 검색바 숨김
        searchEdit.visibility = View.GONE

        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 검색 아이콘 클릭
        searchIcon.setOnClickListener {
            isSearchVisible = !isSearchVisible

            if (isSearchVisible) {
                // 검색바 보이기
                searchEdit.visibility = View.VISIBLE
                searchEdit.requestFocus()

                // 키보드 올리기
                val imm =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT)

            } else {
                // 검색바 숨기기
                searchEdit.visibility = View.GONE
                searchEdit.text.clear()

                // 키보드 내리기
                val imm =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchEdit.windowToken, 0)
            }
        }
    }
}
