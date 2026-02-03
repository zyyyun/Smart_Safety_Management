package com.example.smart_safety_management

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
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

        // 초기 상태는 GONE, onResume에서 서버 확인 후 표시
        alarmDot.visibility = View.GONE

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
                // 근로자 화면에서는 무시
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
        fillCalendarReal()
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
                                photoUris = dto.images ?: emptyList()
                            )
                            dailyCheckMap.getOrPut(day) { mutableListOf() }.add(item)
                        }
                    }
                    fillCalendarReal()
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
                    eventAdapter = WorkerEventAdapter(displayEvents) { eventData ->
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
        updateAlarmDotVisibility() // ✅ 알림 뱃지 업데이트 추가
    }

    private fun updateAlarmDotVisibility() {
        val userId = UserSession.userId ?: return
        val alarmDot = findViewById<View>(R.id.view_alarm_dot) ?: return

        RetrofitClient.instance.getNotifications(userId).enqueue(object : Callback<GetNotificationsResponse> {
            override fun onResponse(call: Call<GetNotificationsResponse>, response: Response<GetNotificationsResponse>) {
                if (response.isSuccessful) {
                    val notifications = response.body()?.notifications ?: emptyList()
                    // ✅ 읽지 않은 알림이 하나라도 있는지 확인하여 뱃지 노출 여부 결정
                    val hasUnread = notifications.any { !it.isRead }
                    alarmDot.visibility = if (hasUnread) View.VISIBLE else View.GONE
                }
            }
            override fun onFailure(call: Call<GetNotificationsResponse>, t: Throwable) {
                Log.e("HomeWorkerActivity", "Error checking notifications", t)
            }
        })
    }

    private fun updateProfile() {
        val profileBar = findViewById<View>(R.id.profile_bar)
        if (profileBar != null) {
            profileBar.findViewById<TextView>(R.id.tv_user_name)?.text = UserSession.userName
            profileBar.findViewById<TextView>(R.id.tv_user_authority)?.text = 
                if (UserSession.userRole == UserRole.MANAGER) "관리자님" else "근로자님"
            
            val ivProfileBar = profileBar.findViewById<ImageView>(R.id.iv_profile_bar)
            val cardProfile = ivProfileBar?.parent as? MaterialCardView

            if (ivProfileBar != null) {
                val params = ivProfileBar.layoutParams as ViewGroup.MarginLayoutParams
                UserSession.profileImageUri?.let { uriString ->
                    Glide.with(this)
                        .load(uriString)
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(ivProfileBar)

                    cardProfile?.apply {
                        setContentPadding(0, 0, 0, 0)
                        preventCornerOverlap = false
                        useCompatPadding = false
                    }

                    ivProfileBar.scaleType = ImageView.ScaleType.CENTER_CROP
                    ivProfileBar.setPadding(0, 0, 0, 0)
                    params.setMargins(0, 0, 0, 0)
                    ivProfileBar.layoutParams = params
                } ?: run {
                    ivProfileBar.setImageResource(R.drawable.profile)

                    cardProfile?.apply {
                        preventCornerOverlap = true
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
        // DB의 is_invited_checked 값이 false이면 팝업 표시
        if (!UserSession.isInviteChecked) {
            showInviteCodeDialog()
        }
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
            val userId = UserSession.userId ?: return@setOnClickListener

            val request = JoinGroupRequest(userId, inputCode)
            RetrofitClient.instance.joinGroup(request).enqueue(object : Callback<JoinGroupResponse> {
                override fun onResponse(call: Call<JoinGroupResponse>, response: Response<JoinGroupResponse>) {
                    if (response.isSuccessful) {
                        UserSession.isInviteChecked = true
                        UserSession.groupId = response.body()?.groupId
                        UserSession.saveSession(this@HomeWorkerActivity)
                        ToastUtil.showShort(this@HomeWorkerActivity, "그룹에 참여되었습니다.")
                        dialog.dismiss()
                    } else {
                        tvError.visibility = View.VISIBLE
                        etInviteCode.setBackgroundResource(R.drawable.bg_edittext_error)
                    }
                }
                override fun onFailure(call: Call<JoinGroupResponse>, t: Throwable) {
                    ToastUtil.showShort(this@HomeWorkerActivity, "네트워크 오류가 발생했습니다.")
                }
            })
        }

        etInviteCode.doAfterTextChanged {
            tvError.visibility = View.GONE
            etInviteCode.setBackgroundResource(R.drawable.bg_edittext)
        }

        tvSkip.setOnClickListener {
            // 건너뛰기 시 저장하지 않음 (다음에 다시 뜸)
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
            val intent = Intent(this, EmergencyContactActivity::class.java)
            startActivity(intent)
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
        if (dateStr.isNullOrEmpty()) return ""
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        try {
            val date = format.parse(dateStr) ?: return ""
            val now = java.util.Date()
            val diff = now.time - date.time
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            val months = days / 30
            val years = days / 365

            return when {
                seconds < 60 -> "방금 전"
                minutes < 60 -> "${minutes}분 전"
                hours < 24 -> "${hours}시간 전"
                days < 30 -> "${days}일 전"
                months < 12 -> "${months}달 전"
                else -> "${years}년 전"
            }
        } catch (e: Exception) {
            return dateStr
        }
    }

    private fun hasDailyCheckItem(day: Int): Boolean {
        val list = dailyCheckMap[day] ?: return false
        return list.isNotEmpty()
    }

    private fun fillCalendarReal() {
        val grid = findViewById<GridLayout>(R.id.calendar_grid) ?: return
        val tvMonth = findViewById<TextView>(R.id.tv_month) ?: return

        grid.removeAllViews()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, selectedYear)
        calendar.set(Calendar.MONTH, selectedMonth - 1)

        tvMonth.text = "${selectedYear}년 ${selectedMonth}월"
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val prevCalendar = calendar.clone() as Calendar
        prevCalendar.add(Calendar.MONTH, -1)
        val prevMaxDay = prevCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 1 until firstDayOfWeek) {
            addCalendarDay(grid, prevMaxDay - (firstDayOfWeek - 1 - i), false)
        }
        for (day in 1..daysInMonth) {
            addCalendarDay(grid, day, true)
        }
        val totalCells = (firstDayOfWeek - 1) + daysInMonth
        for (day in 1..((7 - (totalCells % 7)) % 7)) {
            addCalendarDay(grid, day, false)
        }
    }

    private fun addCalendarDay(grid: GridLayout, day: Int, isCurrentMonth: Boolean) {
        val dayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)

                setMargins(0, 0, 0, (resources.displayMetrics.density * 8).toInt())
            }
        }

        val daySize = (resources.displayMetrics.density * 36).toInt()
        val dayFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(daySize, daySize)
        }

        val tv = TextView(this).apply {
            text = day.toString()
            gravity = Gravity.CENTER
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            if (isCurrentMonth) {
                setOnClickListener {
                    selectedDay = day
                    fillCalendarReal()
                    updateDailyCheckList(selectedDay)
                }
            }
        }

        if (isCurrentMonth) {
            tv.setTextColor(ContextCompat.getColor(this, R.color.gray700_gray400))
            if (day == selectedDay) {
                tv.background = circleDrawable(ContextCompat.getColor(this, R.color.orange500))
                tv.setTextColor(ContextCompat.getColor(this, R.color.white_black))
            }
        } else {
            tv.setTextColor(ContextCompat.getColor(this, R.color.gray200_gray800))
        }

        val alarmDot = if (isCurrentMonth) {
            ImageView(this).apply {
                setImageResource(R.drawable.ellipse_alram)
                visibility = if (hasDailyCheckItem(day) && day != selectedDay) View.VISIBLE else View.INVISIBLE
                val size = (resources.displayMetrics.density * 6).toInt()
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
            }
        } else null

        dayFrame.addView(tv)
        if (alarmDot != null) dayFrame.addView(alarmDot)

        dayContainer.addView(dayFrame)
        grid.addView(dayContainer)
    }

    private fun circleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }
}

private fun subscribeWorkerTopic() {
    FirebaseMessaging.getInstance().subscribeToTopic("workers")
        .addOnSuccessListener { Log.d("FCM", "Subscribed to topic workers") }
        .addOnFailureListener { e -> Log.e("FCM", "Subscribe failed", e) }
}
