package com.example.smart_safety_management

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox

class SettingInviteCancelActivity : AppCompatActivity() {

    private lateinit var tabManager: TextView
    private lateinit var tabWorker: TextView
    private lateinit var underlineManager: View
    private lateinit var underlineWorker: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: InviteCancelAdapter
    private lateinit var checkAll: MaterialCheckBox

    private var managerList = mutableListOf<InviteContactItem>()
    private var workerList = mutableListOf<InviteContactItem>()
    private var isManagerTab = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_invite_cancel)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val clearText = findViewById<TextView>(R.id.clearText)
        checkAll = findViewById(R.id.checkAll)
        tabManager = findViewById(R.id.tab_manager)
        tabWorker = findViewById(R.id.tab_worker)
        underlineManager = findViewById(R.id.underline_manager)
        underlineWorker = findViewById(R.id.underline_worker)
        recyclerView = findViewById(R.id.cancelRecycler)

        // 초대하기 창에서 보낸 데이터 수신
        val managers = intent.getSerializableExtra("manager_list") as? ArrayList<InviteContactItem>
        val workers = intent.getSerializableExtra("worker_list") as? ArrayList<InviteContactItem>
        val initialTab = intent.getBooleanExtra("is_manager_tab", true)

        managers?.let { managerList.addAll(it) }
        workers?.let { workerList.addAll(it) }

        adapter = InviteCancelAdapter(mutableListOf(), { selectedCount ->
            val currentSize = if (isManagerTab) managerList.size else workerList.size
            checkAll.isChecked = selectedCount > 0 && selectedCount == currentSize
        }, { item ->
            // 개별 초대취소 클릭 시
            if (isManagerTab) managerList.remove(item) else workerList.remove(item)
            updateTabState(isManagerTab)
        })

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        backButton.setOnClickListener { 
            onBackPressed() 
        }

        checkAll.setOnClickListener {
            adapter.selectAll(checkAll.isChecked)
        }

        clearText.setOnClickListener {
            if (isManagerTab) {
                managerList.removeAll { it.isSelected }
            } else {
                workerList.removeAll { it.isSelected }
            }
            adapter.deleteSelected()
            checkAll.isChecked = false
            updateTabState(isManagerTab)
        }

        updateTabState(initialTab)

        tabManager.setOnClickListener { updateTabState(true) }
        tabWorker.setOnClickListener { updateTabState(false) }
    }

    override fun onBackPressed() {
        // 결과 반환
        val resultIntent = Intent()
        resultIntent.putExtra("updated_managers", ArrayList(managerList))
        resultIntent.putExtra("updated_workers", ArrayList(workerList))
        setResult(Activity.RESULT_OK, resultIntent)
        super.onBackPressed()
    }

    private fun updateTabState(isManagerSelected: Boolean) {
        isManagerTab = isManagerSelected
        val currentList = if (isManagerSelected) managerList else workerList

        if (isManagerSelected) {
            tabManager.setTextColor(ContextCompat.getColor(this, R.color.orange500))
            tabManager.typeface = ResourcesCompat.getFont(this, R.font.pretendard_bold)
            underlineManager.setBackgroundColor(ContextCompat.getColor(this, R.color.orange500))
            underlineManager.visibility = View.VISIBLE
            tabWorker.setTextColor(ContextCompat.getColor(this, R.color.gray500_gray650))
            tabWorker.typeface = ResourcesCompat.getFont(this, R.font.pretendard_medium)
            underlineWorker.visibility = View.INVISIBLE
        } else {
            tabWorker.setTextColor(ContextCompat.getColor(this, R.color.orange500))
            tabWorker.typeface = ResourcesCompat.getFont(this, R.font.pretendard_bold)
            underlineWorker.setBackgroundColor(ContextCompat.getColor(this, R.color.orange500))
            underlineWorker.visibility = View.VISIBLE
            tabManager.setTextColor(ContextCompat.getColor(this, R.color.gray500_gray650))
            tabManager.typeface = ResourcesCompat.getFont(this, R.font.pretendard_medium)
            underlineManager.visibility = View.INVISIBLE
        }

        // 선택 상태 초기화 (전달 시 복사된 선택 상태가 있을 수 있으므로)
        currentList.forEach { it.isSelected = false }
        adapter.updateData(currentList)
        checkAll.isChecked = false
    }
}
