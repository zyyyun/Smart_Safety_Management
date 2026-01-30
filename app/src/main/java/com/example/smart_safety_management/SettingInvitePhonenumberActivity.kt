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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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
        val tempContactList = mutableListOf<InviteContactItem>()
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
                val rawNumber = it.getString(numberIndex) ?: ""
                val normalizedNumber = rawNumber.replace(Regex("[^0-9]"), "")
                
                // 중복 번호 제거 로직 추가 가능
                if (normalizedNumber.isNotEmpty() && tempContactList.none { item -> item.phoneNumber.replace(Regex("[^0-9]"), "") == normalizedNumber }) {
                    val formattedNumber = formatPhoneNumber(normalizedNumber)
                    tempContactList.add(InviteContactItem(name, formattedNumber))
                }
            }
        }
        
        // 서버에 등록된 유저인지 확인 후 필터링
        checkRegisteredUsers(tempContactList)
    }

    private fun checkRegisteredUsers(tempList: MutableList<InviteContactItem>) {
        val alreadyInvited = intent.getStringArrayListExtra("already_invited") ?: arrayListOf()
        val allPhoneNumbers = tempList.map { it.phoneNumber.replace(Regex("[^0-9]"), "") }

        if (allPhoneNumbers.isEmpty()) {
            updateList(tempList)
            return
        }

        val request = CheckRegisteredContactsRequest(allPhoneNumbers)
        RetrofitClient.instance.checkRegisteredContacts(request).enqueue(object : Callback<CheckRegisteredContactsResponse> {
            override fun onResponse(call: Call<CheckRegisteredContactsResponse>, response: Response<CheckRegisteredContactsResponse>) {
                if (response.isSuccessful) {
                    val registeredNumbers = response.body()?.registeredPhoneNumbers ?: emptyList()
                    // 1. 서버에 등록된 유저 제외 AND 2. 이미 초대 리스트에 있는 유저 제외
                    val filteredContacts = tempList.filter { contact ->
                        val pureNumber = contact.phoneNumber.replace(Regex("[^0-9]"), "")
                        !registeredNumbers.contains(pureNumber) && !alreadyInvited.contains(pureNumber)
                    }
                    updateList(filteredContacts)
                } else {
                    Toast.makeText(this@SettingInvitePhonenumberActivity, "유저 확인 실패", Toast.LENGTH_SHORT).show()
                    // 실패 시에도 이미 초대된 번호는 제외하고 표시
                    val filtered = tempList.filter { !alreadyInvited.contains(it.phoneNumber.replace(Regex("[^0-9]"), "")) }
                    updateList(filtered)
                }
            }
            override fun onFailure(call: Call<CheckRegisteredContactsResponse>, t: Throwable) {
                Toast.makeText(this@SettingInvitePhonenumberActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                // 오류 시에도 이미 초대된 번호는 제외하고 표시
                val filtered = tempList.filter { !alreadyInvited.contains(it.phoneNumber.replace(Regex("[^0-9]"), "")) }
                updateList(filtered)
            }
        })
    }

    private fun updateList(list: List<InviteContactItem>) {
        contactList.clear()
        contactList.addAll(list)
        filteredList.clear()
        filteredList.addAll(list)
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

    // 전화번호 포맷팅 함수 (01012345678 -> 010-1234-5678)
    private fun formatPhoneNumber(phone: String): String {
        val number = phone.replace(Regex("[^0-9]"), "")
        return if (number.length == 11) {
            "${number.substring(0, 3)}-${number.substring(3, 7)}-${number.substring(7)}"
        } else if (number.length == 10) {
            if (number.startsWith("02")) {
                "${number.substring(0, 2)}-${number.substring(2, 6)}-${number.substring(6)}"
            } else {
                "${number.substring(0, 3)}-${number.substring(3, 6)}-${number.substring(6)}"
            }
        } else {
            number
        }
    }
}
