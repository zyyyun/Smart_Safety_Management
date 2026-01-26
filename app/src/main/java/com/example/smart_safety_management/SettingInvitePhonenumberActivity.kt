package com.example.smart_safety_management

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox

class SettingInvitePhonenumberActivity : AppCompatActivity() {

    private var isSearchVisible = false
    private lateinit var adapter: InviteContactAdapter
    private var contactList = mutableListOf<InviteContactItem>()
    private var filteredList = mutableListOf<InviteContactItem>()
    
    private val CONTACTS_PERMISSION_CODE = 101

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

        // 연락처 권한 확인 및 로드
        checkAndRequestPermission()

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

    private fun checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), CONTACTS_PERMISSION_CODE)
        } else {
            loadActualContacts()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CONTACTS_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadActualContacts()
        } else {
            Toast.makeText(this, "연락처 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadActualContacts() {
        contactList.clear()
        val resolver = contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, 
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: "이름 없음"
                val number = it.getString(numberIndex) ?: ""
                
                // 중복 번호 제거 로직 추가 가능
                if (contactList.none { item -> item.phoneNumber == number }) {
                    contactList.add(InviteContactItem(name, number))
                }
            }
        }
        
        filteredList.clear()
        filteredList.addAll(contactList)
        adapter.notifyDataSetChanged()
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
