package com.example.smart_safety_management

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SettingInviteCancelActivity : AppCompatActivity() {

    private lateinit var tabManager: TextView
    private lateinit var tabWorker: TextView
    private lateinit var underlineManager: View
    private lateinit var underlineWorker: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var checkAll: MaterialCheckBox
    private lateinit var clearText: TextView
    private lateinit var adapter: InviteCancelAdapter

    private var managerList = ArrayList<InviteContactItem>()
    private var workerList = ArrayList<InviteContactItem>()
    private var isManagerTab = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_invite_cancel)

        // Phase 11 / 11-02 Sub-task 3.2 — common_toolbar wiring (UX-03).
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            tb.setNavigationOnClickListener { finish() }
        }

        // Intent로 전달받은 데이터 초기화
        @Suppress("DEPRECATION")
        managerList = intent.getSerializableExtra("manager_list") as? ArrayList<InviteContactItem> ?: ArrayList()
        @Suppress("DEPRECATION")
        workerList = intent.getSerializableExtra("worker_list") as? ArrayList<InviteContactItem> ?: ArrayList()
        isManagerTab = intent.getBooleanExtra("is_manager_tab", true)

        // 뷰 바인딩
        val backButton = findViewById<ImageButton>(R.id.backButton)
        tabManager = findViewById(R.id.tab_manager)
        tabWorker = findViewById(R.id.tab_worker)
        underlineManager = findViewById(R.id.underline_manager)
        underlineWorker = findViewById(R.id.underline_worker)
        recyclerView = findViewById(R.id.cancelRecycler)
        checkAll = findViewById(R.id.checkAll)
        clearText = findViewById(R.id.clearText)

        // 리사이클러뷰 설정
        adapter = InviteCancelAdapter(
            onSelectionChanged = { updateSelectAllState() },
            onCancelClicked = { item -> cancelInvites(listOf(item)) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 이벤트 리스너 설정
        backButton.setOnClickListener {
            finishWithResult()
        }

        // 시스템 뒤로가기 버튼 처리
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithResult()
            }
        })

        tabManager.setOnClickListener {
            updateTabState(true)
        }

        tabWorker.setOnClickListener {
            updateTabState(false)
        }

        checkAll.setOnClickListener {
            adapter.setAllSelected(checkAll.isChecked)
        }

        clearText.setOnClickListener {
            cancelSelectedInvites()
        }

        // 초기 탭 상태 설정
        updateTabState(isManagerTab)
    }

    private fun updateTabState(isManager: Boolean) {
        isManagerTab = isManager
        val currentList = if (isManager) managerList else workerList

        if (isManager) {
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

        adapter.submitList(currentList)
        updateSelectAllState()
    }

    private fun updateSelectAllState() {
        val isAllSelected = adapter.isAllSelected()
        val isEmpty = adapter.itemCount == 0
        checkAll.isChecked = !isEmpty && isAllSelected
    }

    private fun cancelSelectedInvites() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            ToastUtil.showShort(this, "선택된 사용자가 없습니다.")
            return
        }
        cancelInvites(selectedItems)
    }

    private fun cancelInvites(items: List<InviteContactItem>) {
        val senderId = UserSession.userId
        if (senderId == null) {
            ToastUtil.showShort(this, "로그인 정보가 없습니다.")
            return
        }

        val phoneNumbers = items.map { it.phoneNumber }

        // 서버 API 호출 (Bulk Delete)
        // CancelInviteRequest 데이터 클래스에 phone_numbers 필드가 추가되어야 합니다.
        val request = CancelInviteRequest(senderId, null, phoneNumbers)

        RetrofitClient.instance.cancelInvite(request).enqueue(object : Callback<CancelInviteResponse> {
            override fun onResponse(call: Call<CancelInviteResponse>, response: Response<CancelInviteResponse>) {
                if (response.isSuccessful) {
                    if (isManagerTab) {
                        managerList.removeAll(items.toSet())
                    } else {
                        workerList.removeAll(items.toSet())
                    }
                    
                    adapter.submitList(if (isManagerTab) managerList else workerList)
                    adapter.clearSelection()
                    updateSelectAllState()
                    
                    val message = response.body()?.message ?: "초대가 취소되었습니다."
                    ToastUtil.showShort(this@SettingInviteCancelActivity, message)
                } else {
                    ToastUtil.showShort(this@SettingInviteCancelActivity, "초대 취소에 실패했습니다.")
                }
            }

            override fun onFailure(call: Call<CancelInviteResponse>, t: Throwable) {
                ToastUtil.showShort(this@SettingInviteCancelActivity, "네트워크 오류가 발생했습니다.")
            }
        })
    }

    private fun finishWithResult() {
        val intent = Intent()
        intent.putExtra("updated_managers", managerList)
        intent.putExtra("updated_workers", workerList)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    inner class InviteCancelAdapter(
        private val onSelectionChanged: () -> Unit,
        private val onCancelClicked: (InviteContactItem) -> Unit
    ) : RecyclerView.Adapter<InviteCancelAdapter.ViewHolder>() {
        private var items = ArrayList<InviteContactItem>()
        private val selectedItems = HashSet<InviteContactItem>()

        fun submitList(newItems: List<InviteContactItem>) {
            items = ArrayList(newItems)
            selectedItems.clear()
            notifyDataSetChanged()
        }

        fun setAllSelected(selected: Boolean) {
            if (selected) {
                selectedItems.addAll(items)
            } else {
                selectedItems.clear()
            }
            notifyDataSetChanged()
            onSelectionChanged()
        }

        fun isAllSelected(): Boolean {
            return items.isNotEmpty() && selectedItems.size == items.size
        }

        fun getSelectedItems(): List<InviteContactItem> {
            return ArrayList(selectedItems)
        }

        fun clearSelection() {
            selectedItems.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_invite_cancel, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val checkBox: MaterialCheckBox = itemView.findViewById(R.id.checkBox)
            private val name: TextView = itemView.findViewById(R.id.nameText)
            private val phone: TextView = itemView.findViewById(R.id.phoneText)
            private val btnCancelOne: TextView = itemView.findViewById(R.id.btnCancelOne)

            fun bind(item: InviteContactItem) {
                name.text = item.name
                phone.text = item.phoneNumber
                
                checkBox.setOnCheckedChangeListener(null)
                checkBox.isChecked = selectedItems.contains(item)

                val clickListener = View.OnClickListener {
                    if (selectedItems.contains(item)) {
                        selectedItems.remove(item)
                    } else {
                        selectedItems.add(item)
                    }
                    notifyItemChanged(adapterPosition)
                    onSelectionChanged()
                }

                itemView.setOnClickListener(clickListener)
                checkBox.setOnClickListener(clickListener)

                btnCancelOne.setOnClickListener {
                    onCancelClicked(item)
                }
            }
        }
    }
}