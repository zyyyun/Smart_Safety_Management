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
import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import android.location.LocationManager
import android.content.Context
// Phase 7 / 07-03 BRIDGE-01 — ComposeView + Realtime
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.smart_safety_management.ui.SsmColors
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.watch.DeviceRow
import com.example.smart_safety_management.watch.EmptyWatchPrompt
import com.example.smart_safety_management.watch.SupabaseModule
import com.example.smart_safety_management.watch.WatchCardComposable
import com.example.smart_safety_management.watch.WatchDetailActivity
import com.example.smart_safety_management.watch.ble.WatchBleServiceController
// Phase 9 / 09-03 TBM-02 — TBM 카드 (worker)
import com.example.smart_safety_management.tbm.TbmWorkerCardComposable
import io.github.jan.supabase.postgrest.from


class HomeWorkerActivity : AppCompatActivity() {

    private val dailyCheckMap = mutableMapOf<Int, MutableList<DailyCheckItem>>()
    private lateinit var dailyAdapter: DailyCheckAdapter
    private lateinit var eventAdapter: WorkerEventAdapter
    private var selectedDay: Int? = null
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var isInviteDialogShowing = false
    private var inviteDialog: androidx.appcompat.app.AlertDialog? = null

    private val detailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // ✅ [수정] DB 변경 사항을 반영하기 위해 서버에서 데이터를 다시 가져옴
            fetchDailyChecks()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isLocGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (isLocGranted) {
            startLocationService()
        } else {
            Toast.makeText(this, "원활한 기능 사용을 위해 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscribeWorkerTopic()
        setContentView(R.layout.main_home_worker)

        initUI()
        checkLocationPermission()

        // Phase 7 / 07-03 BRIDGE-01 — J2208A 워치 카드 (ComposeView 임베드)
        setupWatchCard()

        // Phase 9 / 09-03 TBM-02 — TBM 카드 (D-07, watch_card_compose 아래)
        setupTbmCard()

        // Phase 7 / 07-03 BRIDGE-02 — FCM watch_alert intent 라우팅
        // intent extras 의 alert_type='watch_alert' 시 SafetyAlertsActivity 진입.
        // (alert_id 는 신뢰 X — DB 재조회 가 source of truth)
        if (intent?.getStringExtra("alert_type") == "watch_alert") {
            val alertId = intent.getLongExtra("alert_id", -1L)
            startActivity(Intent(this, SafetyAlertsActivity::class.java).apply {
                if (alertId > 0) putExtra("alert_id", alertId)
            })
        }
    }

    /**
     * Phase 7 / 07-03 BRIDGE-01 — main_home_worker.xml 의 watch_card_compose ComposeView
     * 에 WatchCardComposable 을 setContent. supabase Realtime 채널의 lifecycle 은
     * Compose collectLatest 의 cancellation finally 블록에서 unsubscribe (D-01).
     *
     * ViewCompositionStrategy.DisposeOnLifecycleDestroyed: Activity 파괴 시 Composition
     * 해제 → Realtime channel.unsubscribe() 호출 → WSS slot 해소.
     */
    private fun setupWatchCard() {
        val composeView = findViewById<ComposeView>(R.id.watch_card_compose) ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this)
        )
        val supabase = SupabaseModule.client(this)
        composeView.setContent {
            Smart_Safety_ManagementTheme {
                var pairedDeviceId by remember { mutableStateOf<Int?>(null) }
                var loaded by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    val userId = UserSession.userId ?: run { loaded = true; return@LaunchedEffect }
                    val row = supabase.from("devices").select {
                        filter {
                            eq("user_id", userId)
                            eq("device_type", "WATCH")
                        }
                        limit(1)
                    }.decodeSingleOrNull<DeviceRow>()
                    pairedDeviceId = row?.deviceId
                    row?.let { WatchBleServiceController.configureAndStart(this@HomeWorkerActivity, userId, it) }
                    loaded = true
                }
                if (loaded) {
                    val devId = pairedDeviceId
                    if (devId != null) {
                        WatchCardComposable(
                            deviceId = devId,
                            supabase = supabase,
                            onCardTap = {
                                startActivity(
                                    Intent(this@HomeWorkerActivity, WatchDetailActivity::class.java)
                                        .putExtra(WatchDetailActivity.EXTRA_DEVICE_ID, devId)
                                )
                            },
                        )
                    } else {
                        EmptyWatchPrompt(onClick = {
                            startActivity(Intent(this@HomeWorkerActivity, SettingDeviceManagementActivity::class.java))
                        })
                    }
                }
            }
        }
    }

    /**
     * Phase 9 / 09-03 TBM-02 — main_home_worker.xml 의 tbm_card_compose ComposeView 에
     * TbmWorkerCardComposable 을 setContent. Phase 7 setupWatchCard 1:1 미러.
     */
    private fun setupTbmCard() {
        val composeView = findViewById<ComposeView>(R.id.tbm_card_compose) ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this)
        )
        val supabase = SupabaseModule.client(this)
        // 2026-05-19: PoC 한정 fallback — userId/groupId 미설정 시 test_user / group_id=1 (Plan 09-01 seed) 사용.
        // 어떤 계정으로 들어가도 카드 표시. v1.1 정상 운영 시 ?: return 으로 복원.
        val userId = UserSession.userId ?: "test_user"
        val groupId = UserSession.groupId?.toIntOrNull() ?: 1
        composeView.setContent {
            Smart_Safety_ManagementTheme {
                TbmWorkerCardComposable(
                    groupId = groupId,
                    userId = userId,
                    supabase = supabase,
                    onClickGuide = { sessionId ->
                        startActivity(Intent(this@HomeWorkerActivity, TbmWorkerActivity::class.java).apply {
                            putExtra(TbmWorkerActivity.EXTRA_SESSION_ID, sessionId)
                        })
                    },
                )
            }
        }
    }

    private fun initUI() {
        updateProfile()
        updateWorkerDay()
        // 2026-05-19: 초대 코드 입력 dialog 자동 노출 제거 (test 브랜치 / Phase 7·9 시연 환경 한정).
        // 필요 시 SettingActivity 의 명시적 메뉴에서 수동 진입.
        // checkInviteCodeDialog()
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
                val intent = Intent(this, DailyListActivity::class.java).apply {
                    putExtra("screen", "detail")
                    putExtra("day", day)
                    putExtra("itemId", item.id)
                    val dateStr = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, day)
                    putExtra("date", dateStr)
                    putExtra("location", item.title)
                    putExtra("riskFactor", item.desc)
                    putExtra("safetyMeasure", item.safetyMeasure)
                    putExtra("status", item.status)
                    putStringArrayListExtra("photoUris", ArrayList(item.photoUris))
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
                        val day = try {
                            dailyChecklistDisplayDate(dto.checkDate, dto.createdAt).dayOfMonth
                        } catch (e: Exception) {
                            null
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
                            content = "${dto.eventName ?: "알 수 없는 이벤트"}이(가) 감지되었습니다.",
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
        setupWatchCard()
        fetchWorkerEvents()
        updateAlarmDotVisibility() // ✅ 알림 뱃지 업데이트 추가
        fetchUserInfo()
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

    private fun fetchUserInfo() {
        val userId = UserSession.userId ?: return

        RetrofitClient.instance.getUserInfo(userId).enqueue(object : Callback<GetUserInfoResponse> {
            override fun onResponse(call: Call<GetUserInfoResponse>, response: Response<GetUserInfoResponse>) {
                if (response.isSuccessful) {
                    val userInfo = response.body()
                    if (userInfo != null) {
                        UserSession.isInviteChecked = userInfo.isInviteChecked
                        UserSession.groupId = userInfo.groupId?.toString()
                        UserSession.inviteCode = userInfo.inviteCode
                        UserSession.saveSession(this@HomeWorkerActivity)

                        if (UserSession.isInviteChecked && isInviteDialogShowing) {
                            inviteDialog?.dismiss()
                            isInviteDialogShowing = false
                        }
                        updateProfile()
                    }
                }
            }
            override fun onFailure(call: Call<GetUserInfoResponse>, t: Throwable) {}
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
        // DB의 is_invite_checked 값이 false이면 팝업 표시
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

        inviteDialog = androidx.appcompat.app.AlertDialog.Builder(this)
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
                        UserSession.inviteCode = inputCode // ✅ 입력한 초대코드를 세션에 저장
                        UserSession.saveSession(this@HomeWorkerActivity)
                        ToastUtil.showShort(this@HomeWorkerActivity, "그룹에 참여되었습니다.")
                        inviteDialog?.dismiss()

                        // 그룹 가입 성공 후 데이터 및 UI 즉시 새로고침
                        updateProfile()
                        fetchDailyChecks()
                        fetchWorkerEvents()
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
            inviteDialog?.dismiss()
        }

        inviteDialog?.show()
        inviteDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val params = inviteDialog?.window?.attributes
        params?.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        inviteDialog?.window?.attributes = params
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
            "high", "위험", "danger" -> "위험"
            "medium", "경고", "warning" -> "경고"
            "low", "주의", "caution" -> "주의"
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

    private fun checkLocationPermission() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Android 13(Tiramisu) 이상은 알림 권한 필요
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            locationPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            startLocationService()
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

private fun subscribeWorkerTopic() {
    FirebaseMessaging.getInstance().subscribeToTopic("workers")
        .addOnSuccessListener { Log.d("FCM", "Subscribed to topic workers") }
        .addOnFailureListener { e -> Log.e("FCM", "Subscribe failed", e) }
}
