package com.example.smart_safety_management

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingPeopleManagementActivity : AppCompatActivity() {
    private lateinit var adapter: PeopleAdapter
    private var allPeople = mutableListOf<PeopleItem>()
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
        allPeople = mutableListOf(
            PeopleItem(1, "지코", "010-2345-6789", "관리자"),
            PeopleItem(2, "박보검", "010-3456-7890", "근로자"),
            PeopleItem(3, "수지", "010-4567-8901", "근로자"),
            PeopleItem(4, "정해인", "010-5678-9012", "근로자"),
            PeopleItem(5, "아이유", "010-6789-0123", "관리자"),
            PeopleItem(6, "공유", "010-7890-1234", "근로자")
        )

        // 어댑터 설정 (삭제 콜백 전달)
        adapter = PeopleAdapter(allPeople) { deletedItem ->
            allPeople.remove(deletedItem)
            applyFilterAndSearch()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 커스텀 드롭다운 어댑터 설정
        val filterItems = resources.getStringArray(R.array.people_filter_array)
        val spinnerAdapter = object : ArrayAdapter<String>(
            this,
            R.layout.spinner_item,
            filterItems
        ) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = layoutInflater.inflate(R.layout.item_spinner_dropdown, parent, false)
                val tv = if (view is TextView) view else view.findViewById(android.R.id.text1)
                tv.text = getItem(position)

                // 위치에 따라 라운드가 다른 배경 적용
                val backgroundRes = when (position) {
                    0 -> R.drawable.bg_spinner_item_top
                    count - 1 -> R.drawable.bg_spinner_item_bottom
                    else -> R.drawable.bg_spinner_item_middle
                }
                view.setBackgroundResource(backgroundRes)

                return view
            }
        }
        filterSpinner.adapter = spinnerAdapter

        // 필터 스피너 리스너 설정
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