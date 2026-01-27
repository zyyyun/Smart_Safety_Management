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
import com.example.smart_safety_management.screens.location.LocationActivity
import com.example.smart_safety_management.screens.realtime.RealTimeActivity
import kotlin.math.abs
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            val safetyMeasure = data.getStringExtra("safetyMeasure") ?: "" // 지금은 저장 안 쓰면 일단 놔둬도 됨

            // "YYYY-MM-DD" 형태라고 가정
            val day = dateStr.split("-").getOrNull(2)?.toIntOrNull() ?: return@registerForActivityResult

            // ✅ DailyCheckItem (id,title,desc,status) 버전에 맞춤
            val newItem = DailyCheckItem(
                title = location,        // 위치를 title로
                desc = riskFactor,       // 위험요인을 desc로
                status = "미점검"        // 상태
            )

            // ✅ 해당 날짜 리스트에 추가
            val list = dailyCheckMap.getOrPut(day) { mutableListOf() }
            list.add(0, newItem)

            selectedDay = day

            // ✅ 달력 점 + 리스트 갱신
            fillCalendarReal()
            updateDailyCheckList(selectedDay)

            Toast.makeText(this, "작성 완료!", Toast.LENGTH_SHORT).show()
        }


    // ✅ 삭제 결과 받는 Launcher (addDailyLauncher 바로 아래에 추가)
    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            val action = data.getStringExtra("action") ?: return@registerForActivityResult
            if (action != "delete") return@registerForActivityResult

            val day = data.getIntExtra("day", -1)
            val itemId = data.getStringExtra("itemId") ?: ""
            if (day == -1 || itemId.isBlank()) return@registerForActivityResult

            val list = dailyCheckMap[day] ?: return@registerForActivityResult

            // ✅ id로 삭제
            list.removeAll { it.id == itemId }

            // ✅ UI 갱신
            selectedDay = day
            fillCalendarReal()
            updateDailyCheckList(selectedDay)

            Toast.makeText(this, "삭제 완료!", Toast.LENGTH_SHORT).show()
        }

    private fun initUI() {
        updateProfileName()
        checkInviteCodeDialog()

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
            addDailyLauncher.launch(Intent(this, DailyListActivity::class.java))
        }

        val rv = findViewById<RecyclerView>(R.id.rv_daily_check)
        rv.layoutManager = LinearLayoutManager(this)
        dailyAdapter = DailyCheckAdapter(emptyList()) { day, item ->
            val intent = Intent(this, DailyDetailActivity::class.java).apply {
                // 삭제에 필요한 값
                putExtra("day", day)
                putExtra("itemId", item.id)

                // (선택) 상세에 보여줄 데이터도 같이 넘기고 싶으면
                putExtra("title", item.title)
                putExtra("desc", item.desc)
                putExtra("status", item.status)
            }
            detailLauncher.launch(intent)   // ✅ 핵심: startActivity 말고 launcher로!
        }
        rv.adapter = dailyAdapter


        selectedDay = findClosestUncheckedDay()
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
        updateProfileName()
    }

    private fun updateProfileName() {
        val profileBar = findViewById<View>(R.id.profile_bar)
        profileBar.findViewById<TextView>(R.id.tv_user_name).text = UserSession.userName
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

