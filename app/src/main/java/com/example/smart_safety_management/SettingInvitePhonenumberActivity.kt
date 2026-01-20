package com.example.smart_safety_management

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox

class SettingInvitePhonenumberActivity : AppCompatActivity() {

    private var isSearchVisible = false
    private lateinit var adapter: InviteContactAdapter
    private var contactList = mutableListOf<InviteContactItem>()
    private var filteredList = mutableListOf<InviteContactItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_invite_phonenumber)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val searchIcon = findViewById<ImageView>(R.id.searchIcon)
        val searchEdit = findViewById<EditText>(R.id.searchEdit)
        val checkAll = findViewById<MaterialCheckBox>(R.id.checkAll)
        val clearText = findViewById<TextView>(R.id.clearText)
        val btnSend = findViewById<Button>(R.id.btn_send)
        val recyclerView = findViewById<RecyclerView>(R.id.contactRecycler)

        // 샘플 데이터 추가
        setupMockData()
        filteredList.addAll(contactList)

        // 리사이클러뷰 설정
        adapter = InviteContactAdapter(filteredList) { selectedCount ->
            btnSend.text = "초대문자 발송 ($selectedCount)"
            checkAll.isChecked = selectedCount == filteredList.size && filteredList.isNotEmpty()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 초기 상태: 검색바 숨김
        searchEdit.visibility = View.GONE
        btnSend.text = "초대문자 발송 (0)"

        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 검색 아이콘 클릭
        searchIcon.setOnClickListener {
            isSearchVisible = !isSearchVisible
            if (isSearchVisible) {
                searchEdit.visibility = View.VISIBLE
                searchEdit.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT)
            } else {
                searchEdit.visibility = View.GONE
                searchEdit.text.clear()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchEdit.windowToken, 0)
            }
        }

        // 검색 필터링
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 전체 선택 클릭
        checkAll.setOnClickListener {
            adapter.selectAll(checkAll.isChecked)
        }

        // 선택 해제 클릭
        clearText.setOnClickListener {
            adapter.clearSelection()
            checkAll.isChecked = false
        }

        // 초대문자 발송 버튼 클릭 시 선택된 항목 전달
        btnSend.setOnClickListener {
            val selectedContacts = contactList.filter { it.isSelected }
            if (selectedContacts.isNotEmpty()) {
                val resultIntent = Intent()
                resultIntent.putExtra("selected_contacts", ArrayList(selectedContacts))
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun setupMockData() {
        contactList.add(InviteContactItem("아이유", "010-1234-5678"))
        contactList.add(InviteContactItem("김수현", "010-2345-6789"))
        contactList.add(InviteContactItem("유재석", "010-3456-7890"))
        contactList.add(InviteContactItem("전지현", "010-4567-8901"))
        contactList.add(InviteContactItem("이민호", "010-5678-9012"))
        contactList.add(InviteContactItem("소지섭", "010-6789-0123"))
        contactList.add(InviteContactItem("박신혜", "010-7890-1234"))
        contactList.add(InviteContactItem("이종석", "010-8901-2345"))
        contactList.add(InviteContactItem("정해인", "010-9012-3456"))
        contactList.add(InviteContactItem("한지민", "010-0123-4567"))
        contactList.add(InviteContactItem("김태희", "010-1234-5678"))
        contactList.add(InviteContactItem("이병헌", "010-1234-8901"))
        contactList.add(InviteContactItem("박보검", "010-8765-4321"))
        contactList.add(InviteContactItem("김연아", "010-2345-9012"))
        contactList.add(InviteContactItem("송중기", "010-3456-0123"))
        contactList.add(InviteContactItem("김태리", "010-4567-1234"))
        contactList.add(InviteContactItem("조인성", "010-5678-2345"))
        contactList.add(InviteContactItem("신세경", "010-6789-3456"))
    }

    private fun filter(text: String) {
        val filtered = if (text.isEmpty()) {
            contactList
        } else {
            contactList.filter {
                it.name.contains(text, ignoreCase = true) || 
                it.phoneNumber.contains(text)
            }
        }
        adapter.updateData(filtered)
    }
}
