package com.example.smart_safety_management

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingInviteActivity : AppCompatActivity() {

    private lateinit var tabManager: TextView
    private lateinit var tabWorker: TextView
    private lateinit var underlineManager: View
    private lateinit var underlineWorker: View
    private lateinit var emptyText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: InvitedUserAdapter
    private lateinit var bannerLayout: ConstraintLayout
    private lateinit var cancelInvite: TextView

    private var managerList = mutableListOf<InviteContactItem>()
    private var workerList = mutableListOf<InviteContactItem>()
    private var isManagerTab = true
    private var isBannerClosedManually = false

    // 연락처 선택 결과 처리를 위한 launcher
    private val getInviteResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedContacts = result.data?.getSerializableExtra("selected_contacts") as? ArrayList<InviteContactItem>
            selectedContacts?.let {
                if (isManagerTab) {
                    managerList.addAll(it)
                } else {
                    workerList.addAll(it)
                }
                updateTabState(isManagerTab)
                
                // 선택된 연락처가 있을 경우 첫 번째 연락처로 메시지 앱 실행
                if (it.isNotEmpty()) {
                    sendInviteSms(it[0].phoneNumber)
                }
            }
        }
    }

    // 초대 취소 결과 처리를 위한 launcher
    private val getCancelResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val updatedManagers = result.data?.getSerializableExtra("updated_managers") as? ArrayList<InviteContactItem>
            val updatedWorkers = result.data?.getSerializableExtra("updated_workers") as? ArrayList<InviteContactItem>
            
            updatedManagers?.let { 
                managerList.clear()
                managerList.addAll(it) 
            }
            updatedWorkers?.let { 
                workerList.clear()
                workerList.addAll(it) 
            }
            updateTabState(isManagerTab)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_invite)

        // 뷰 초기화
        val backButton = findViewById<ImageButton>(R.id.backButton)
        val btnInvite = findViewById<Button>(R.id.btn_invite)
        cancelInvite = findViewById(R.id.cancelInvite)
        val closeBanner = findViewById<ImageView>(R.id.closeBanner)
        
        tabManager = findViewById(R.id.tab_manager)
        tabWorker = findViewById(R.id.tab_worker)
        underlineManager = findViewById(R.id.underline_manager)
        underlineWorker = findViewById(R.id.underline_worker)
        emptyText = findViewById(R.id.emptyText)
        recyclerView = findViewById(R.id.inviteRecycler)
        bannerLayout = findViewById(R.id.bannerLayout)

        // 리사이클러뷰 설정
        adapter = InvitedUserAdapter(mutableListOf()) { item ->
            // 다시초대 클릭 시 메시지 앱 실행
            sendInviteSms(item.phoneNumber)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 연락처로 초대문자 발송 버튼 클릭 시
        btnInvite.setOnClickListener {
            val intent = Intent(this, SettingInvitePhonenumberActivity::class.java)
            
            // 현재 탭에 이미 추가된 번호들을 추출해서 전달 (중복 방지용)
            val currentList = if (isManagerTab) managerList else workerList
            val alreadyInvitedNumbers = currentList.map { it.phoneNumber.replace(Regex("[^0-9]"), "") }
            intent.putStringArrayListExtra("already_invited", ArrayList(alreadyInvitedNumbers))
            
            getInviteResult.launch(intent)
        }
        
        // 배너 닫기
        closeBanner.setOnClickListener {
            bannerLayout.visibility = View.GONE
            isBannerClosedManually = true
        }
        
        // 초대 취소 클릭 시 이동
        cancelInvite.setOnClickListener {
            val intent = Intent(this, SettingInviteCancelActivity::class.java)
            intent.putExtra("manager_list", ArrayList(managerList))
            intent.putExtra("worker_list", ArrayList(workerList))
            intent.putExtra("is_manager_tab", isManagerTab)
            getCancelResult.launch(intent)
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
            
            emptyText.text = "아직 초대된 관리자가 없어요"
        } else {
            tabWorker.setTextColor(ContextCompat.getColor(this, R.color.orange500))
            tabWorker.typeface = ResourcesCompat.getFont(this, R.font.pretendard_bold)
            underlineWorker.setBackgroundColor(ContextCompat.getColor(this, R.color.orange500))
            underlineWorker.visibility = View.VISIBLE

            tabManager.setTextColor(ContextCompat.getColor(this, R.color.gray500_gray650))
            tabManager.typeface = ResourcesCompat.getFont(this, R.font.pretendard_medium)
            underlineManager.visibility = View.INVISIBLE
            
            emptyText.text = "아직 초대된 근로자가 없어요"
        }

        // 리스트 업데이트 및 빈 화면 처리
        if (currentList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            bannerLayout.visibility = View.GONE
            cancelInvite.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            cancelInvite.visibility = View.VISIBLE
            if (!isBannerClosedManually) {
                bannerLayout.visibility = View.VISIBLE
            }
            adapter.updateData(currentList)
        }
    }

    private fun sendInviteSms(phoneNumber: String) {
        // DB에서 가져와 UserSession에 저장된 현재 관리자의 초대코드를 사용
        val inviteCode = UserSession.inviteCode ?: ""
        val message = "[중대재해예방 플랫폼] 안전관리 시스템에 초대되었습니다.\n" +
                "초대코드: $inviteCode\n" +
                "앱을 설치하고 코드를 입력하여 가입을 완료해주세요."

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${phoneNumber.replace("-", "")}")
            putExtra("sms_body", message)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.showShort(this, "메시지 앱을 열 수 없습니다.")
        }
    }
}
