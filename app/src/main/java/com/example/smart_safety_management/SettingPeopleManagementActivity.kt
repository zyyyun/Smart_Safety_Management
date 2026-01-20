package com.example.smart_safety_management

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingPeopleManagementActivity : AppCompatActivity() {
    private lateinit var adapter: PeopleAdapter
    private lateinit var allPeople: List<PeopleItem>
    private var currentFilter = "전체"
    private var currentSearch = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_people_management)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val filterSpinner = findViewById<Spinner>(R.id.filterSpinner)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)

        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 임시 데이터 생성
        allPeople = listOf(
            PeopleItem(1, "지코", "010-2345-6789", "관리자"),
            PeopleItem(2, "박보검", "010-3456-7890", "근로자"),
            PeopleItem(3, "수지", "010-4567-8901", "근로자"),
            PeopleItem(4, "정해인", "010-5678-9012", "근로자"),
            PeopleItem(5, "아이유", "010-6789-0123", "관리자"),
            PeopleItem(6, "공유", "010-7890-1234", "근로자")
        )

        // 어댑터 설정
        adapter = PeopleAdapter(allPeople)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 커스텀 폰트가 적용된 스피너 어댑터 설정
        val spinnerAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.people_filter_array,
            R.layout.spinner_item // 여기서 폰트가 적용된 커스텀 레이아웃 사용
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = spinnerAdapter

        // 필터 스피너 설정
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = parent?.getItemAtPosition(position).toString()
                applyFilterAndSearch()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 검색창 설정
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearch = s.toString()
                applyFilterAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyFilterAndSearch() {
        val filteredList = allPeople.filter { item ->
            val matchesFilter = currentFilter == "전체" || item.role == currentFilter
            val matchesSearch = item.name.contains(currentSearch, ignoreCase = true) ||
                    item.phone.contains(currentSearch)
            
            matchesFilter && matchesSearch
        }
        adapter.updateList(filteredList)
    }
}