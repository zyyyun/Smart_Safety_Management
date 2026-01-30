package com.example.smart_safety_management

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.bumptech.glide.Glide
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log
private val dailyCheckMap = mutableMapOf<Int, MutableList<DailyCheckItem>>(
    7 to mutableListOf(
        DailyCheckItem(title="B구역 1열", desc="정리미흡으로 안전사고 발생 우려", status="미점검"),
        DailyCheckItem(title="C구역 3열", desc="정리미흡으로 안전사고 발생 우려", status="점검완료"),
    ),
    12 to mutableListOf(
        DailyCheckItem(title="A구역 1열", desc="정리미흡으로 안전사고 발생 우려", status="점검완료"),
    ),
    23 to mutableListOf(
        DailyCheckItem(title="A구역 4열", desc="정리미흡으로 안전사고 발생 우려", status="미점검"),
        DailyCheckItem(title="A구역 4열", desc="정리미흡으로 안전사고 발생 우려", status="미점검"),
        DailyCheckItem(title="D구역 2열", desc="정리미흡으로 안전사고 발생 우려", status="점검완료"),
        DailyCheckItem(title="D구역 1열", desc="정리미흡으로 안전사고 발생 우려", status="점검완료"),
        DailyCheckItem(title="D구역 2열", desc="정리미흡으로 안전사고 발생 우려", status="점검완료"),
    ),
    25 to mutableListOf(
        DailyCheckItem(title="C구역 2열", desc="정리미흡으로 안전사고 발생 우려", status="미점검"),
    ),
    26 to mutableListOf(
        DailyCheckItem(title="A구역 4열", desc="정리미흡으로 인적사고 발생 우려", status="미점검"),
    )
)




class HomeWorkerActivity : AppCompatActivity() {

    private val dailyCheckMap = mutableMapOf<Int, MutableList<DailyCheckItem>>()
    private lateinit var dailyAdapter: DailyCheckAdapter
    private lateinit var eventAdapter: WorkerEventAdapter
    private var selectedDay: Int? = null
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var isInviteDialogShowing = false

    private val detailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val day = result.data?.getIntExtra("day", -1) ?: -1
            if (day != -1) {
                updateDailyCheckList(day)
            } else {
                updateDailyCheckList(selectedDay)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscribeWorkerTopic()
        setContentView(R.layout.main_home_worker)

        initUI()
    }

    private fun initUI() {
        updateProfile()
        updateWorkerDay()
        checkInviteCodeDialog()
        emergencyContact()

        val topBar = findViewById<View>(R.id.top_bar)
        val btnAlarm = topBar.findViewById<ImageButton>(R.id.btn_alarm)
        val alarmDot = findViewById<View>(R.id.view_alarm_dot)
        val btnSetting = topBar.findViewById<ImageButton>(R.id.btn_setting)

        alarmDot.visibility = if (true) View.VISIBLE else View.GONE

        // 알림 버튼 -> 알림 화면
        btnAlarm.setOnClickListener {
            startActivity(Intent(this, NoticeActivity::class.java))
        }

        // 설정 버튼 -> 근로자용 설정 화면
        btnSetting.setOnClickListener {
            startActivity(Intent(this, SettingWorkerActivity::class.java))
        }

        // 일일안전점검 리스트 헤더 처리 (추가 버튼 숨김)
        findViewById<View>(R.id.list_header)?.findViewById<View>(R.id.btn_add)?.visibility = View.GONE

        // 일일안전점검 리스트 초기화
        val rvDaily = findViewById<RecyclerView>(R.id.rv_daily_check)
        rvDaily.layoutManager = LinearLayoutManager(this)
        dailyAdapter = DailyCheckAdapter(
            items = emptyList(),
            onOpenDetail = { day, item ->
                val intent = Intent(this, DailyDetailActivity::class.java).apply {
                    putExtra("day", day)
                    putExtra("itemId", item.id)
                }
                detailLauncher.launch(intent)
            },
            onRequestNotify = { day, item ->
                // ✅ HomeWorkerActivity(근로자 화면)에서는 보통 호출될 일이 없지만,
                // 혹시라도 들어오면 그냥 상세 열기로 보내거나 무시해도 됨.
                // (무시)
                // 또는: onOpenDetail(day, item) 같은 동작
            }
        )
        rvDaily.adapter = dailyAdapter


        // 조치요청 리스트 초기화
        val rvEvent = findViewById<RecyclerView>(R.id.rv_worker_event)
        rvEvent.layoutManager = LinearLayoutManager(this)
        eventAdapter = WorkerEventAdapter(emptyList())
        rvEvent.adapter = eventAdapter

        selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        dailyAdapter.initTooltip()

        val scroll = findViewById<NestedScrollView>(R.id.home_scroll)
        scroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, scrollX, scrollY, oldX, oldY ->
                if (scrollX != oldX || scrollY != oldY) {
                    dailyAdapter.updateTooltipPosition()
                }
            }
        )

        val cal = Calendar.getInstance()
        selectedYear = cal.get(Calendar.YEAR)
        selectedMonth = cal.get(Calendar.MONTH) + 1

        // 초기 리스트 갱신 (오늘 날짜 기준 빈 리스트라도 표시)
        updateDailyCheckList(selectedDay)

        fetchWorkerEvents()
        fetchDailyChecks()
    }

    private fun fetchDailyChecks() {
        val userId = UserSession.userId ?: return
        RetrofitClient.instance.getDailyChecks(userId, selectedYear, selectedMonth).enqueue(object : Callback<GetDailyChecksResponse> {
            override fun onResponse(call: Call<GetDailyChecksResponse>, response: Response<GetDailyChecksResponse>) {
                if (response.isSuccessful) {
                    dailyCheckMap.clear()
                    val checks = response.body()?.checks ?: emptyList()
                    checks.forEach { dto ->
                        val targetDate = dto.createdAt ?: dto.checkDate
                        val day = try {
                            targetDate.substring(8, 10).toInt()
                        } catch (e: Exception) {
                            targetDate.split("-").getOrNull(2)?.take(2)?.toIntOrNull()
                        }

                        if (day != null) {
                            val item = DailyCheckItem(
                                id = dto.checkId.toString(),
                                title = dto.location,
                                desc = dto.hazard ?: "",
                                safetyMeasure = dto.countermeasure ?: "",
                                status = dto.status,
                                photoUris = emptyList()
                            )
                            dailyCheckMap.getOrPut(day) { mutableListOf() }.add(item)
                        }
                    }
                    updateDailyCheckList(selectedDay)
                }
            }
            override fun onFailure(call: Call<GetDailyChecksResponse>, t: Throwable) {}
        })
    }

    private fun fetchWorkerEvents() {
        val userId = UserSession.userId ?: return
        RetrofitClient.instance.getDetectionEvents(userId).enqueue(object : Callback<GetDetectionEventsResponse> {
            override fun onResponse(call: Call<GetDetectionEventsResponse>, response: Response<GetDetectionEventsResponse>) {
                if (response.isSuccessful) {
                    val rawEvents = response.body()?.events ?: emptyList()

                    val filteredEvents = rawEvents.filter {
                        it.status.equals("REQUESTED", ignoreCase = true) ||
                                it.status.equals("COMPLETED", ignoreCase = true) ||
                                it.status.equals("FALSE_POSITIVE", ignoreCase = true)
                    }.sortedBy {
                        if (it.status.equals("REQUESTED", ignoreCase = true)) 0 else 1
                    }

                    val displayEvents = filteredEvents.map { dto ->
                        val eventData = EventData(
                            id = dto.eventId,
                            accidentType = mapRiskLevel(dto.riskLevel),
                            location = dto.installArea ?: "알 수 없음",
                            content = "${dto.eventName ?: "알 수 없는 이벤트"}가 감지되었습니다.",
                            occurrenceTime = calculateTimeAgo(dto.detectedAt),
                            deviceName = dto.deviceName ?: "",
                            accuracy = "${dto.accuracy ?: 0}%"
                        )
                        val statusEnum = when {
                            dto.status.equals("REQUESTED", ignoreCase = true) -> EventStatus.PENDING
                            dto.status.equals("COMPLETED", ignoreCase = true) -> EventStatus.COMPLETED
                            dto.status.equals("FALSE_POSITIVE", ignoreCase = true) -> EventStatus.FALSE_DETECTION
                            else -> EventStatus.COMPLETED
                        }
                        Pair(eventData, statusEnum)
                    }

                    val rvEvent = findViewById<RecyclerView>(R.id.rv_worker_event)
                    // WorkerEventAdapter에 클릭 리스너를 전달하도록 수정 (Adapter 수정 필요)
                    eventAdapter = WorkerEventAdapter(displayEvents) { eventData ->
                        // 조치 대기(PENDING) 상태인 경우에만 상세 페이지로 이동
                        val status = displayEvents.find { it.first.id == eventData.id }?.second
                        if (status == EventStatus.PENDING) {
                            val intent = Intent(this@HomeWorkerActivity, ActionDetailWorkerActivity::class.java)
                            intent.putExtra("eventId", eventData.id)
                            startActivity(intent)
                        }
                    }
                    rvEvent.adapter = eventAdapter
                }
            }
            override fun onFailure(call: Call<GetDetectionEventsResponse>, t: Throwable) {
            }
        })
    }

    private fun updateWorkerDay() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val tvWorkerDay = findViewById<TextView>(R.id.tv_worker_day)
        tvWorkerDay?.text = "${year}년 ${month}월 ${day}일"
    }

    override fun onResume() {
        super.onResume()
        updateProfile()
        fetchWorkerEvents()
    }

    private fun updateProfile() {
        val profileBar = findViewById<View>(R.id.profile_bar)
        if (profileBar != null) {
            // 이름 및 역할 업데이트
            profileBar.findViewById<TextView>(R.id.tv_user_name)?.text = UserSession.userName
            
            // 역할
            profileBar.findViewById<TextView>(R.id.tv_user_authority)?.text = 
                if (UserSession.userRole == UserRole.MANAGER) "관리자님" else "근로자님"
            
            // 프로필 사진 동기화
            val ivProfileBar = profileBar.findViewById<ImageView>(R.id.iv_profile_bar)
            val cardProfile = ivProfileBar?.parent as? MaterialCardView

            if (ivProfileBar != null) {
                val params = ivProfileBar.layoutParams as ViewGroup.MarginLayoutParams
                UserSession.profileImageUri?.let { uriString ->
                    // 사진이 있을 때: 부모 CardView 제약까지 풀어서 원에 꽉 차도록 설정
                    Glide.with(this)
                        .load(uriString)
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(ivProfileBar)

                    cardProfile?.apply {
                        setContentPadding(0, 0, 0, 0)
                        preventCornerOverlap = false // 모서리 겹침 방지 여백 제거
                        useCompatPadding = false     // 호환성 패딩 제거
                    }

                    ivProfileBar.scaleType = ImageView.ScaleType.CENTER_CROP
                    ivProfileBar.setPadding(0, 0, 0, 0)
                    params.setMargins(0, 0, 0, 0)
                    ivProfileBar.layoutParams = params
                } ?: run {
                    // 기본 이미지일 때: XML 디자인(마진 5, 10, 5)을 그대로 유지
                    ivProfileBar.setImageResource(R.drawable.profile)

                    cardProfile?.apply {
                        preventCornerOverlap = true // 기본값 복구
                    }

                    ivProfileBar.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    ivProfileBar.setPadding(0, 0, 0, 0)

                    val density = resources.displayMetrics.density
                    params.setMargins(
                        (5 * density).toInt(),
                        (10 * density).toInt(),
                        (5 * density).toInt(),
                        0
                    )
                    ivProfileBar.layoutParams = params
                }
            }
        }
    }

    private fun updateDailyCheckList(day: Int?) {
        val list = dailyCheckMap[day] ?: emptyList()
        dailyAdapter.updateList(day, list)


        val totalCount = list.size
        val uncheckedCount = list.count { it.status == "미점검" }
        
        val progressText = "$uncheckedCount/$totalCount"
        val spannable = SpannableString(progressText)

        val orangeColor = ContextCompat.getColor(this, R.color.orange500)
        val grayColor = ContextCompat.getColor(this, R.color.gray600)
        
        val separatorIndex = progressText.indexOf("/")
        
        if (separatorIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(orangeColor), 0, separatorIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(grayColor), separatorIndex, progressText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        findViewById<TextView>(R.id.tv_progress)?.text = spannable
        adjustRecyclerMinHeight()
    }

    private fun adjustRecyclerMinHeight() {
        val rv = findViewById<RecyclerView>(R.id.rv_daily_check)
        val spacer = findViewById<View>(R.id.rv_spacer)
        val scroll = findViewById<NestedScrollView>(R.id.home_scroll)

        rv?.post {
            val rvBottom = rv.bottom
            val scrollBottom = scroll.bottom
            val gap = scrollBottom - rvBottom
            spacer?.layoutParams?.height = if (gap > 0) gap else 0
            spacer?.requestLayout()
        }
    }

    private fun checkInviteCodeDialog() {
        if (!UserSession.isInviteDoneWorker) showInviteCodeDialog()
    }

    private fun showInviteCodeDialog() {
        isInviteDialogShowing = true
        val dialogView = layoutInflater.inflate(R.layout.invite_code, null)
        val etInviteCode = dialogView.findViewById<EditText>(R.id.et_invite_code)
        val btnSubmit = dialogView.findViewById<View>(R.id.btn_submit)
        val tvError = dialogView.findViewById<View>(R.id.tv_error)
        val tvSkip = dialogView.findViewById<TextView>(R.id.tv_skip_invite_code)

        tvSkip.paintFlags = tvSkip.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnSubmit.setOnClickListener {
            val inputCode = etInviteCode.text.toString().trim()
            if (inputCode == "1234") {
                UserSession.isInviteDoneWorker = true
                UserSession.isInviteSuccessWorker = true
                dialog.dismiss()
            } else {
                tvError.visibility = View.VISIBLE
                etInviteCode.setBackgroundResource(R.drawable.bg_edittext_error)
            }
        }

        etInviteCode.doAfterTextChanged {
            tvError.visibility = View.GONE
            etInviteCode.setBackgroundResource(R.drawable.bg_edittext)
        }

        tvSkip.setOnClickListener {
            UserSession.isInviteDoneWorker = true
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val params = dialog.window?.attributes
        params?.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.attributes = params
    }
    private fun emergencyContact() {
        val emergencyArea = findViewById<View>(R.id.layout_emergency_root)
        emergencyArea.isClickable = true
        emergencyArea.setOnClickListener {
             // ...
        }
    }

    private fun mapRiskLevel(level: String?): String {
        return when (level?.lowercase()) {
            "high", "위험" -> "위험"
            "medium", "경고" -> "경고"
            "low", "주의" -> "주의"
            else -> "기타"
        }
    }

    private fun calculateTimeAgo(dateStr: String?): String {
        return "방금 전" // 실제 로직 생략
    }
}

private fun subscribeWorkerTopic() {
    FirebaseMessaging.getInstance().subscribeToTopic("workers")
        .addOnSuccessListener { Log.d("FCM", "Subscribed to topic workers") }
        .addOnFailureListener { e -> Log.e("FCM", "Subscribe failed", e) }
}