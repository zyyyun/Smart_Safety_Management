package com.example.smart_safety_management

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingCreateWorkplaceActivity : AppCompatActivity() {

    private lateinit var workplaceAdapter: WorkplaceAdapter
    private var workplaceList = mutableListOf<WorkplaceItem>()
    private val sharedPrefs by lazy { getSharedPreferences("workplace_prefs", MODE_PRIVATE) }
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_create_workplace)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val etWorkplaceName = findViewById<EditText>(R.id.et_workplace_name)
        val btnCreate = findViewById<Button>(R.id.btn_create)
        val rvWorkplace = findViewById<RecyclerView>(R.id.rv_workplace)

        // 저장된 리스트 불러오기
        loadWorkplaceList()

        // 어댑터 초기화
        workplaceAdapter = WorkplaceAdapter(
            workplaceList,
            onEditClick = { _ ->
                // TODO: 편집 기능 구현
            },
            onDeleteClick = { position ->
                workplaceAdapter.removeItem(position)
                saveWorkplaceList() // 삭제 후 저장
            }
        )

        // 리사이클러뷰 설정
        rvWorkplace.apply {
            layoutManager = LinearLayoutManager(this@SettingCreateWorkplaceActivity)
            adapter = workplaceAdapter
        }

        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 입력값에 따른 버튼 색상 변경
        etWorkplaceName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    btnCreate.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@SettingCreateWorkplaceActivity, R.color.gray50_gray900))
                    btnCreate.setTextColor(ContextCompat.getColor(this@SettingCreateWorkplaceActivity, R.color.gray400_gray700))
                } else {
                    btnCreate.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@SettingCreateWorkplaceActivity, R.color.orange500))
                    btnCreate.setTextColor(ContextCompat.getColor(this@SettingCreateWorkplaceActivity, R.color.white_black))
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 추가 버튼 클릭 시 리스트에 추가 및 저장
        btnCreate.setOnClickListener {
            val name = etWorkplaceName.text.toString().trim()
            if (name.isNotEmpty()) {
                val newItem = WorkplaceItem(name)
                workplaceAdapter.addItem(newItem)
                saveWorkplaceList() // 추가 후 저장
                
                etWorkplaceName.text.clear()
                rvWorkplace.scrollToPosition(workplaceList.size - 1)
            } else {
                Toast.makeText(this, "현장명을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveWorkplaceList() {
        val json = gson.toJson(workplaceList)
        sharedPrefs.edit().putString("workplace_list", json).apply()
    }

    private fun loadWorkplaceList() {
        val json = sharedPrefs.getString("workplace_list", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<WorkplaceItem>>() {}.type
            val savedList: MutableList<WorkplaceItem> = gson.fromJson(json, type)
            workplaceList.clear()
            workplaceList.addAll(savedList)
        }
    }
}
