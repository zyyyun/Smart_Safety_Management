package com.example.smart_safety_management

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.bumptech.glide.Glide
import com.example.smart_safety_management.screens.location.LocationActivity
import com.example.smart_safety_management.screens.realtime.RealTimeActivity
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
// Phase 9 / 09-03 TBM-02 — ComposeView 임베드 (첫 manager 카드, Pitfall 12 Theme 래핑 강제)
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.smart_safety_management.tbm.TbmDashboardCardComposable
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import com.example.smart_safety_management.watch.SupabaseModule

class HomeActivity : AppCompatActivity() {

    private val dailyCheckMap = mutableMapOf<Int, MutableList<DailyCheckItem>>()
    private lateinit var dailyAdapter: DailyCheckAdapter
    private var selectedDay: Int? = null
    private var isInviteDialogShowing = false
    private var inviteDialog: androidx.appcompat.app.AlertDialog? = null
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0 // 1~12


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PHOTO_DEBUG", "HomeActivity onCreate called")

        setContentView(R.layout.main_home)

        initUI()

        // Phase 9 / 09-03 TBM-02 — manager TBM 대시보드 카드 (D-06)
        setupTbmDashboardCard()
    }

    /**
     * Phase 9 / 09-03 TBM-02 — main_home.xml 의 tbm_dashboard_compose ComposeView 에
     * TbmDashboardCardComposable 을 setContent. Pitfall 12 (HomeActivity 첫 ComposeView
     * Theme 래핑 강제) 적용.
     *
     * ViewCompositionStrategy.DisposeOnLifecycleDestroyed: Activity 파괴 시 Composition
     * 해제 → Realtime channel.unsubscribe() 호출 → WSS slot 해소 (Phase 7 D-01 패턴).
     *
     * 권한 가드는 TbmDashboardActivity 의 onCreate 에서 다시 검증 (manager only).
     */
    private fun setupTbmDashboardCard() {
        val composeView = findViewById<ComposeView>(R.id.tbm_dashboard_compose) ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this)
        )
        val supabase = SupabaseModule.client(this)
        val groupId = UserSession.groupId?.toIntOrNull()
        if (groupId == null) {
            // 그룹 미가입 시 카드 빈 영역 — 초대코드 입력 흐름이 별도로 진행 중.
            return
        }
        composeView.setContent {
            Smart_Safety_ManagementTheme {
                TbmDashboardCardComposable(
                    groupId = groupId,
                    supabase = supabase,
                    onClickDashboard = {
                        startActivity(Intent(this@HomeActivity, TbmDashboardActivity::class.java))
                    },
                )
            }
        }
    }

    private val addDailyLauncher =

        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            val dateStr = data.getStringExtra("date") ?: return@registerForActivityResult
            val location = data.getStringExtra("location") ?: ""
            val riskFactor = data.getStringExtra("riskFactor") ?: ""
            val safetyMeasure = data.getStringExtra("safetyMeasure") ?: ""
            val photoUris = data.getStringArrayListExtra("photoUris")?.toList() ?: emptyList()
            Log.d("PHOTO_DEBUG", "addDailyLauncher received photoCount=${photoUris.size} uris=$photoUris")



            val editMode = data.getBooleanExtra("editMode", false)
            val oldDay = data.getIntExtra("day", -1)

            val itemId = data.getStringExtra("itemId") ?: ""

            val newDay = dateStr.split("-").getOrNull(2)?.toIntOrNull() ?: return@registerForActivityResult

            if (editMode && oldDay != -1 && itemId.isNotBlank()) {
                // ✅ 수정 처리 (교체)
                val oldList = dailyCheckMap[oldDay] ?: mutableListOf()
                val idx = oldList.indexOfFirst { it.id == itemId }

                if (idx != -1) {
                    val oldItem = oldList[idx]

                    val updatedItem = oldItem.copy(
                        title = location,
                        desc = riskFactor,
                        safetyMeasure = safetyMeasure,
                        photoUris = photoUris
                    )

                    // ✅ 날짜가 그대로면 같은 리스트에서 교체
                    if (oldDay == newDay) {
                        oldList[idx] = updatedItem
                    } else {
                        // ✅ 날짜가 바뀌면: 기존에서 제거 후 새 날짜 리스트에 추가
                        oldList.removeAt(idx)
                        val newList = dailyCheckMap.getOrPut(newDay) { mutableListOf() }
                        newList.add(0, updatedItem)
                    }
                }
            } else {
                val newItem = DailyCheckItem(
                    title = location,
                    desc = riskFactor,
                    safetyMeasure = safetyMeasure,
                    photoUris = photoUris,
                    status = "미점검"
                )

                val list = dailyCheckMap.getOrPut(newDay) { mutableListOf() }
                list.add(0, newItem)
            }
            val savedCount = dailyCheckMap[newDay]?.firstOrNull { it.id == itemId }?.photoUris?.size
            Log.d("PHOTO_DEBUG", "afterSave newDay=$newDay editMode=$editMode itemId=$itemId savedPhotoCount=$savedCount")

            selectedDay = newDay
            fillCalendarReal()
            updateDailyCheckList(selectedDay)
            ToastUtil.showShort(this, if (editMode) "수정 완료!" else "작성 완료!")
        }


    // ✅ 삭제 결과 받는 Launcher (addDailyLauncher 바로 아래에 추가)
    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            val action = data.getStringExtra("action") ?: return@registerForActivityResult

            // ✅ 1) 삭제 처리
            if (action == "delete") {
                val day = data.getIntExtra("day", -1)
                val itemId = data.getStringExtra("itemId") ?: ""
                if (day == -1 || itemId.isBlank()) return@registerForActivityResult

                val list = dailyCheckMap[day] ?: return@registerForActivityResult
                list.removeAll { it.id == itemId }

                selectedDay = day
                fillCalendarReal()
                updateDailyCheckList(selectedDay)

                ToastUtil.showShort(this, "삭제 완료!")
                return@registerForActivityResult
            }

            // ✅ 2) 수정 처리 (DailyDetailActivity -> DailyListActivity 수정 완료 결과)
            if (action == "edit") {
                val dateStr = data.getStringExtra("date") ?: return@registerForActivityResult
                val location = data.getStringExtra("location") ?: ""
                val riskFactor = data.getStringExtra("riskFactor") ?: ""
                val safetyMeasure = data.getStringExtra("safetyMeasure") ?: ""
                val photoUris = data.getStringArrayListExtra("photoUris")?.toList() ?: emptyList()

                val oldDay = data.getIntExtra("day", -1)
                val itemId = data.getStringExtra("itemId") ?: ""
                if (oldDay == -1 || itemId.isBlank()) return@registerForActivityResult

                val newDay = dateStr.split("-").getOrNull(2)?.toIntOrNull()
                    ?: return@registerForActivityResult

                val oldList = dailyCheckMap[oldDay] ?: return@registerForActivityResult
                val idx = oldList.indexOfFirst { it.id == itemId }
                if (idx == -1) return@registerForActivityResult

                val oldItem = oldList[idx]
                val updatedItem = oldItem.copy(
                    title = location,
                    desc = riskFactor,
                    safetyMeasure = safetyMeasure,
                    photoUris = photoUris
                )

                if (oldDay == newDay) {
                    oldList[idx] = updatedItem
                } else {
                    oldList.removeAt(idx)
                    val newList = dailyCheckMap.getOrPut(newDay) { mutableListOf() }
                    newList.add(0, updatedItem)
                }

                selectedDay = newDay
                fillCalendarReal()
                updateDailyCheckList(selectedDay)

                ToastUtil.showShort(this, "수정 완료!")
                return@registerForActivityResult
            }

            // 그 외 action이면 무시
        }


    private fun initUI() {
        updateProfile()
        // 2026-05-19: 초대 코드 입력 dialog 자동 노출 제거 (test 브랜치 / Phase 7·9 시연 환경 한정).
        // 필요 시 SettingActivity 의 명시적 메뉴에서 수동 진입.
        // checkInviteCodeDialog()
        val cal = Calendar.getInstance()
        selectedYear = cal.get(Calendar.YEAR)
        selectedMonth = cal.get(Calendar.MONTH) + 1

        val topBar = findViewById<View>(R.id.top_bar)
        val btnAlarm = topBar.findViewById<ImageButton>(R.id.btn_alarm)
        val alarmDot = findViewById<View>(R.id.view_alarm_dot)
        val btnSetting = topBar.findViewById<ImageButton>(R.id.btn_setting)

        // 초기 상태는 GONE, onResume에서 서버 확인 후 표시
        alarmDot.visibility = View.GONE

        btnAlarm.setOnClickListener {
            startActivity(Intent(this, NoticeActivity::class.java))
        }

        btnSetting.setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
        }

        findViewById<View>(R.id.layout_monthly_action).setOnClickListener {
            startActivity(Intent(this, MonthlyListActivity::class.java))
        }

        findViewById<View>(R.id.btn_add).setOnClickListener {

            val cal = Calendar.getInstance()
            val day = selectedDay ?: cal.get(Calendar.DAY_OF_MONTH)

            val intent = Intent(this, DailyListActivity::class.java).apply {
                putExtra("year", selectedYear)
                putExtra("month", selectedMonth)
                putExtra("day", day)
            }

            addDailyLauncher.launch(intent)
        }


        val rv = findViewById<RecyclerView>(R.id.rv_daily_check)
        rv.layoutManager = LinearLayoutManager(this)
        dailyAdapter = DailyCheckAdapter(
            items = emptyList(),
            onOpenDetail = { day, item ->
                // 기존 상세 열기 로직 그대로
                val dateStr = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, day)

                val intent = Intent(this, DailyDetailActivity::class.java).apply {
                    putExtra("day", day)
                    putExtra("itemId", item.id)
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
                // ✅ 같은 그룹의 모든 근로자에게 알림 전송
                sendGroupNotification(item.title)
            }
        )

        rv.adapter = dailyAdapter


        if (selectedDay == null) {
            selectedDay = cal.get(Calendar.DAY_OF_MONTH)
        }

        dailyAdapter.initTooltip()
        fillCalendarReal()
        updateDailyCheckList(selectedDay)
        fetchDailyChecks()

        val scroll = findViewById<NestedScrollView>(R.id.home_scroll)
        scroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, scrollX, scrollY, oldX, oldY ->
                if (scrollX != oldX || scrollY != oldY) {
                    dailyAdapter.updateTooltipPosition()
                }
            }
        )

        setupBottomNavigation()
    }

    private fun sendGroupNotification(location: String) {
        val userId = UserSession.userId ?: return
        val title = "안전감시단"
        val content = "$location 점검이 필요합니다."

        val request = SendGroupNotificationRequest(userId, title, content)
        RetrofitClient.instance.sendGroupNotification(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    ToastUtil.showShort(this@HomeActivity, "근로자들에게 점검 요청 알림을 보냈어요.")
                } else {
                    ToastUtil.showShort(this@HomeActivity, "알림 전송에 실패했습니다.")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                ToastUtil.showShort(this@HomeActivity, "네트워크 오류가 발생했습니다.")
            }
        })
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_ai -> {
                    startActivity(Intent(this, AIEventActivity::class.java))
                    true
                }
                R.id.nav_live -> {
                    startActivity(Intent(this, RealTimeActivity::class.java))
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                R.id.nav_location -> {
                    startActivity(Intent(this, LocationActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateProfile()
        fetchDailyChecks()
        updateAlarmDotVisibility()
        fetchUserInfo()
    }

    private fun updateAlarmDotVisibility() {
        val userId = UserSession.userId ?: return
        val alarmDot = findViewById<View>(R.id.view_alarm_dot) ?: return

        RetrofitClient.instance.getNotifications(userId).enqueue(object : Callback<GetNotificationsResponse> {
            override fun onResponse(call: Call<GetNotificationsResponse>, response: Response<GetNotificationsResponse>) {
                if (response.isSuccessful) {
                    val notifications = response.body()?.notifications ?: emptyList()
                    // ✅ 읽지 않은 알림이 하나라도 있는지 확인
                    val hasUnread = notifications.any { !it.isRead }
                    alarmDot.visibility = if (hasUnread) View.VISIBLE else View.GONE
                }
            }
            override fun onFailure(call: Call<GetNotificationsResponse>, t: Throwable) {
                Log.e("HomeActivity", "Error checking notifications", t)
            }
        })
    }

    private fun fetchUserInfo() {
        val userId = UserSession.userId ?: return
        
        // RetrofitClient에 getUserInfo 메서드와 GetUserInfoResponse 데이터 클래스가 필요합니다.
        RetrofitClient.instance.getUserInfo(userId).enqueue(object : Callback<GetUserInfoResponse> {
            override fun onResponse(call: Call<GetUserInfoResponse>, response: Response<GetUserInfoResponse>) {
                if (response.isSuccessful) {
                    val userInfo = response.body()
                    if (userInfo != null) {
                        UserSession.isInviteChecked = userInfo.isInviteChecked
                        UserSession.groupId = userInfo.groupId?.toString()
                        UserSession.inviteCode = userInfo.inviteCode
                        UserSession.saveSession(this@HomeActivity)

                        if (UserSession.isInviteChecked && isInviteDialogShowing) {
                            inviteDialog?.dismiss()
                            isInviteDialogShowing = false
                        }

                        // 2026-05-19: 서버 응답 기반 초대 코드 dialog 자동 노출도 제거.
                        // 사용자가 명시적으로 SettingActivity 진입 시에만 입력.
                        // if (!UserSession.isInviteChecked && !isInviteDialogShowing) {
                        //     showInviteCodeDialog()
                        // }
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

    private fun fetchDailyChecks() {
        val userId = UserSession.userId ?: return
        RetrofitClient.instance.getDailyChecks(userId, selectedYear, selectedMonth).enqueue(object : retrofit2.Callback<GetDailyChecksResponse> {
            override fun onResponse(call: retrofit2.Call<GetDailyChecksResponse>, response: retrofit2.Response<GetDailyChecksResponse>) {
                if (response.isSuccessful) {
                    dailyCheckMap.clear()
                    val checks = response.body()?.checks ?: emptyList()
                    
                    checks.forEach { dto ->
                        // created_at 기준으로 날짜 파싱 (YYYY-MM-DD HH:mm:ss)
                        // createdAt이 없으면 checkDate를 사용하도록 예외 처리
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
            override fun onFailure(call: retrofit2.Call<GetDailyChecksResponse>, t: Throwable) {}
        })
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

        findViewById<TextView>(R.id.tv_progress).text = spannable
    }

    private fun hasDailyCheckItem(day: Int): Boolean {
        val list = dailyCheckMap[day] ?: return false
        return list.isNotEmpty()
    }

    private fun fillCalendarReal() {
        val grid = findViewById<GridLayout>(R.id.calendar_grid)
        val tvMonth = findViewById<TextView>(R.id.tv_month)

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

    private fun checkInviteCodeDialog() {
        // DB의 is_invite_checked 값이 false이면 팝업 표시
        if (!UserSession.isInviteChecked) showInviteCodeDialog()
    }

    private fun showInviteCodeDialog() {
        isInviteDialogShowing = true
        val dialogView = layoutInflater.inflate(R.layout.invite_code, null)
        val etInviteCode = dialogView.findViewById<EditText>(R.id.et_invite_code)
        val btnSubmit = dialogView.findViewById<View>(R.id.btn_submit)
        val tvError = dialogView.findViewById<View>(R.id.tv_error)
        val tvSkip = dialogView.findViewById<TextView>(R.id.tv_skip_invite_code)

        // 내 초대코드가 있으면 미리 입력해두기
        if (!UserSession.inviteCode.isNullOrEmpty()) {
            etInviteCode.setText(UserSession.inviteCode)
        }

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
                        UserSession.saveSession(this@HomeActivity)
                        ToastUtil.showShort(this@HomeActivity, "그룹에 참여되었습니다.")
                        inviteDialog?.dismiss()

                        // 그룹 가입 성공 후 데이터 및 UI 즉시 새로고침
                        updateProfile()
                        fetchDailyChecks()
                    } else {
                        tvError.visibility = View.VISIBLE
                        etInviteCode.setBackgroundResource(R.drawable.bg_edittext_error)
                    }
                }
                override fun onFailure(call: Call<JoinGroupResponse>, t: Throwable) {
                    ToastUtil.showShort(this@HomeActivity, "네트워크 오류가 발생했습니다.")
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
}
