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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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
                
                // 선택된 모든 연락처로 메시지 앱 실행
                if (it.isNotEmpty()) {
                    sendInviteSms(it)
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
            // 다시초대 클릭 시 해당 한 명에게 발송
            sendInviteSms(listOf(item))
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

        updateTabState(isManagerSelected = true)

        tabManager.setOnClickListener {
            updateTabState(isManagerSelected = true)
        }

        tabWorker.setOnClickListener {
            updateTabState(isManagerSelected = false)
        }

        // 초기 데이터 로드 (서버에서 PENDING 상태 목록 가져오기)
        loadPendingInvites()
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

    private fun loadPendingInvites() {
        val userId = UserSession.userId ?: return

        RetrofitClient.instance.getPendingInvites(userId).enqueue(object : Callback<GetPendingInvitesResponse> {
            override fun onResponse(call: Call<GetPendingInvitesResponse>, response: Response<GetPendingInvitesResponse>) {
                if (response.isSuccessful) {
                    val invites = response.body()?.pendingInvites ?: emptyList()
                    
                    managerList.clear()
                    workerList.clear()

                    for (invite in invites) {
                        val formattedPhone = formatPhoneNumber(invite.phoneNumber)
                        val contactItem = InviteContactItem(invite.name, formattedPhone)
                        
                        // 역할에 따라 리스트 분류 (역할이 없으면 기본적으로 근로자로 분류하거나, 이름 없음인 경우 근로자로 처리)
                        if (invite.userRole == "manager") {
                            managerList.add(contactItem)
                        } else {
                            // userRole이 "worker"이거나 null(미가입자)인 경우 근로자 탭에 표시
                            workerList.add(contactItem)
                        }
                    }
                    // 현재 탭 상태에 맞춰 UI 갱신
                    updateTabState(isManagerTab)
                }
            }

            override fun onFailure(call: Call<GetPendingInvitesResponse>, t: Throwable) {
                ToastUtil.showShort(this@SettingInviteActivity, "초대 목록을 불러오는데 실패했습니다.")
            }
        })
    }

    private fun formatPhoneNumber(phone: String): String {
        if (phone.length == 11) {
            return "${phone.substring(0, 3)}-${phone.substring(3, 7)}-${phone.substring(7)}"
        }
        return phone
    }

    private fun sendInviteSms(contacts: List<InviteContactItem>) {
        // DB에서 가져와 UserSession에 저장된 현재 관리자의 초대코드를 사용
        val inviteCode = UserSession.inviteCode
        if (inviteCode.isNullOrEmpty()) {
            ToastUtil.showShort(this, "초대코드를 불러올 수 없습니다. 다시 로그인해주세요.")
            return
        }

        val senderId = UserSession.userId
        if (senderId == null) {
            ToastUtil.showShort(this, "로그인 정보가 없습니다.")
            return
        }

        // 선택된 모든 전화번호를 ';'로 연결
        val inviteContacts = contacts.map {
            InviteContactDTO(it.phoneNumber.replace(Regex("[^0-9]"), ""), it.name)
        }
        val phoneNumbersStr = inviteContacts.joinToString(";") { it.phoneNumber }

        // 서버에 초대 멤버 등록 요청
        val request = InviteMembersRequest(senderId, inviteContacts)
        RetrofitClient.instance.inviteMembers(request).enqueue(object : Callback<InviteMembersResponse> {
            override fun onResponse(call: Call<InviteMembersResponse>, response: Response<InviteMembersResponse>) {
                if (response.isSuccessful) {
                    // DB 등록 성공 시 목록 새로고침
                    loadPendingInvites()

                    // DB 등록 성공 시 문자 발송 앱 실행
                    val message = "[중대재해예방 플랫폼] 안전관리 시스템에 초대되었습니다.\n" +
                            "초대코드: $inviteCode\n" +
                            "앱을 설치하고 코드를 입력하여 가입을 완료해주세요."

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phoneNumbersStr")
                        putExtra("sms_body", message)
                    }

                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        ToastUtil.showShort(this@SettingInviteActivity, "메시지 앱을 열 수 없습니다.")
                    }
                } else {
                    ToastUtil.showShort(this@SettingInviteActivity, "초대 등록 실패: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<InviteMembersResponse>, t: Throwable) {
                ToastUtil.showShort(this@SettingInviteActivity, "네트워크 오류가 발생했습니다.")
            }
        })
    }
}
