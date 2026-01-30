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

private val dailyCheckMap = mutableMapOf<Int, MutableList<DailyCheckItem>>(
    7 to mutableListOf(
        DailyCheckItem(
            title = "B구역 1열",
            desc = "정리미흡으로 안전사고 발생 우려",
            status = "미점검"
        ),
        DailyCheckItem(
            title = "C구역 3열",
            desc = "정리미흡으로 안전사고 발생 우려",
            status = "점검완료"
        )
    ),
    12 to mutableListOf(
        DailyCheckItem(
            title = "A구역 1열",
            desc = "정리미흡으로 안전사고 발생 우려",
            status = "점검완료"
        )
    ),
    22 to mutableListOf(
        DailyCheckItem(
            title = "A구역 4열",
            desc = "정리미흡으로 안전사고 발생 우려",
            status = "미점검"
        ),
        DailyCheckItem(
            title = "A구역 4열",
            desc = "정리미흡으로 안전사고 발생 우려",
            status = "미점검"
        ),
        DailyCheckItem(
            title = "D구역 2열",
            desc = "정리미흡으로 안전사고 발생 우려",
            status = "점검완료"
        ),
        DailyCheckItem(
            title = "D구역 1열",
            desc = "정리미흡으로 안전사고 발생 우려",
            status = "점검완료"
        ),
        DailyCheckItem(
            title = "D구역 2열",
            desc = "정리미흡으로 안전사고 발생 우려",
            status = "점검완료"
        )
    ),
    25 to mutableListOf(
        DailyCheckItem(
            title = "C구역 2열",
            desc = "정리미흡으로 안전사고 발생 우려",
            status = "미점검"
        )
    ),
    26 to mutableListOf(
        DailyCheckItem(
            title = "A구역 4열",
            desc = "정리미흡으로 인적사고 발생 우려",
            status = "미점검"
        )
    )
)

class HomeActivity : AppCompatActivity() {

    private lateinit var dailyAdapter: DailyCheckAdapter
    private var selectedDay: Int? = null
    private var isInviteDialogShowing = false
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0 // 1~12


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PHOTO_DEBUG", "HomeActivity onCreate called")
        setContentView(R.layout.main_home)

        initUI()
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
            Toast.makeText(this, if (editMode) "수정 완료!" else "작성 완료!", Toast.LENGTH_SHORT).show()
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

                Toast.makeText(this, "삭제 완료!", Toast.LENGTH_SHORT).show()
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

                Toast.makeText(this, "수정 완료!", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            // 그 외 action이면 무시
        }


    private fun initUI() {
        updateProfile()
        checkInviteCodeDialog()
        val cal = Calendar.getInstance()
        selectedYear = cal.get(Calendar.YEAR)
        selectedMonth = cal.get(Calendar.MONTH) + 1

        val topBar = findViewById<View>(R.id.top_bar)
        val btnAlarm = topBar.findViewById<ImageButton>(R.id.btn_alarm)
        val alarmDot = findViewById<View>(R.id.view_alarm_dot)
        val btnSetting = topBar.findViewById<ImageButton>(R.id.btn_setting)

        alarmDot.visibility = if (true) View.VISIBLE else View.GONE

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
        dailyAdapter = DailyCheckAdapter(emptyList()) { day, item ->
            Log.d("PHOTO_DEBUG", "detail open day=$day id=${item.id} photoCount=${item.photoUris.size} uris=${item.photoUris}")

            val dateStr = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, day)

            val intent = Intent(this, DailyDetailActivity::class.java).apply {
                putExtra("day", day)
                putExtra("itemId", item.id)

                // ✅ 디테일 화면에 보여줄 실제 데이터
                putExtra("date", dateStr)
                putExtra("location", item.title)      // title을 위치로 쓰고 있었지?
                putExtra("riskFactor", item.desc)     // desc를 위험요인으로
                putExtra("safetyMeasure", item.safetyMeasure)
                putExtra("status", item.status)

                putStringArrayListExtra("photoUris", ArrayList(item.photoUris))

            }
            detailLauncher.launch(intent)
        }

        rv.adapter = dailyAdapter


        if (selectedDay == null) {
            selectedDay = findClosestUncheckedDay()
        }

        dailyAdapter.initTooltip()

        val scroll = findViewById<NestedScrollView>(R.id.home_scroll)
        scroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, scrollX, scrollY, oldX, oldY ->
                if (scrollX != oldX || scrollY != oldY) {
                    dailyAdapter.updateTooltipPosition()
                }
            }
        )

        fillCalendarReal()
        updateDailyCheckList(selectedDay)
        setupBottomNavigation()
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
    }

    private fun updateProfile() {
        val profileBar = findViewById<View>(R.id.profile_bar)
        if (profileBar != null) {
            // 이름 업데이트
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
                    // 1. 사용자 사진 로드 및 꽉 채우기 설정
                    Glide.with(this)
                        .load(uriString)
                        .centerCrop()
                        .error(R.drawable.profile) // 로드 실패 시 기본 이미지 표시
                        .into(ivProfileBar)
                    ivProfileBar.setPadding(0, 0, 0, 0)

                    // 2. 부모 카드뷰의 모든 제약 제거 (가장 중요)
                    cardProfile?.apply {
                        setContentPadding(0, 0, 0, 0)
                        preventCornerOverlap = false // 모서리 보호 패딩 제거
                        useCompatPadding = false     // 호환성 패딩 제거
                    }

                    // 3. ImageView 마진 제거
                    params.setMargins(0, 0, 0, 0)
                    ivProfileBar.layoutParams = params
                } ?: run {
                    // 기본 이미지일 때: 사용자님이 설정하신 마진(5, 10, 5)을 정확히 유지
                    // 설정된 사진이 없으면 기본값 유지
                    ivProfileBar.setImageResource(R.drawable.profile)
                    ivProfileBar.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    ivProfileBar.setPadding(0, 0, 0, 0)

                    // 카드뷰 기본값 복구 (패딩 등)
                    cardProfile?.apply {
                        preventCornerOverlap = true
                    }

                    val density = resources.displayMetrics.density
                    params.setMargins(
                        (5 * density).toInt(),
                        (10 * density).toInt(),
                        (5 * density).toInt(),
                        0
                    )
                    ivProfileBar.setPadding(
                        (resources.displayMetrics.density * 5).toInt(),
                        (resources.displayMetrics.density * 8).toInt(),
                        (resources.displayMetrics.density * 5).toInt(),
                        0
                    )
                    ivProfileBar.layoutParams = params
                }
            }
        }
    }

    private fun findClosestUncheckedDay(): Int? {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val uncheckedDays = dailyCheckMap.filter { entry ->
            entry.value.any { it.status == "미점검" }
        }.keys
        if (uncheckedDays.isEmpty()) return today
        return uncheckedDays.minByOrNull { abs(it - today) }
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
        tvMonth.text = "${calendar.get(Calendar.YEAR)}년 ${calendar.get(Calendar.MONTH) + 1}월"
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
        if (!UserSession.isInviteDoneManager) showInviteCodeDialog()
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
                UserSession.isInviteDoneManager = true
                UserSession.isInviteSuccessManager = true
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
            UserSession.isInviteDoneManager = true
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val params = dialog.window?.attributes
        params?.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.attributes = params
    }
}