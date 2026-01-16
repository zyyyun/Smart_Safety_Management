package com.example.smart_safety_management

import DailyCheckItem
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
import kotlin.math.abs

private const val PREF_NAME = "onboarding_prefs"
private const val KEY_INVITE_DONE = "invite_code_done"
private const val KEY_INVITE_SUCCESS = "invite_code_success"

private val dailyCheckMap = mapOf(
    7 to listOf(
        DailyCheckItem("B구역 1열", "정리미흡으로 안전사고 발생 우려", "미점검"),
        DailyCheckItem("C구역 3열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
    ),
    12 to listOf(
        DailyCheckItem("A구역 1열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
    ),
    15 to listOf(
        DailyCheckItem("A구역 4열", "정리미흡으로 안전사고 발생 우려", "미점검"),
        DailyCheckItem("A구역 4열", "정리미흡으로 안전사고 발생 우려", "미점검"),
        DailyCheckItem("D구역 2열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
        DailyCheckItem("D구역 1열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
        DailyCheckItem("D구역 2열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
    ),
    25 to listOf(
        DailyCheckItem("C구역 2열", "정리미흡으로 안전사고 발생 우려", "미점검")
    ),
    26 to listOf(
        DailyCheckItem("A구역 4열", "정리미흡으로 인적사고 발생 우려", "미점검")
    )
)

class HomeActivity : AppCompatActivity() {

    private lateinit var dailyAdapter: DailyCheckAdapter
    private var selectedDay: Int? = null

    private var isInviteDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_home)

        // 테스트를 위해 초기 상태가 필요하다면 아래 주석을 한 번만 풀고 실행했다가 다시 주석처리하세요.
        resetInviteForTest()
        
        checkInviteCodeDialog()

        val topBar = findViewById<View>(R.id.top_bar)
        val btnAlarm = topBar.findViewById<ImageButton>(R.id.btn_alarm)
        val alarmDot = findViewById<View>(R.id.view_alarm_dot)
        val btnSetting = topBar.findViewById<ImageButton>(R.id.btn_setting)

        val hasUnreadNotice = true
        alarmDot.visibility = if (hasUnreadNotice) View.VISIBLE else View.GONE

        btnAlarm.setOnClickListener {
            val intent = Intent(this, NoticeActivity::class.java)
            startActivity(intent)
        }

        btnSetting.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.layout_monthly_action).setOnClickListener {
            val intent = Intent(this, MonthlyListActivity::class.java)
            startActivity(intent)
        }

        val rv = findViewById<RecyclerView>(R.id.rv_daily_check)
        rv.layoutManager = LinearLayoutManager(this)
        dailyAdapter = DailyCheckAdapter(emptyList())
        rv.adapter = dailyAdapter

        // 홈 화면 진입 시 실제 날짜와 가장 가까운 미점검 항목이 있는 날짜를 찾아서 선택합니다.
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

        findViewById<View>(R.id.btn_add).setOnClickListener {
            val intent = Intent(this, DailyListActivity::class.java)
            startActivity(intent)
        }

        fillCalendarReal()
        // 초기 선택된 날짜에 맞는 리스트를 표시합니다.
        updateDailyCheckList(selectedDay)

        // 하단바 설정
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.nav_home // 현재 홈 화면이므로 홈 아이콘 활성화

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // 이미 홈 화면이므로 아무것도 하지 않음
                    true
                }
                R.id.nav_ai -> {
                    val intent = Intent(this, AIEventActivity::class.java)
                    startActivity(intent)
                    Toast.makeText(this, "AI 감지 화면으로 이동", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_live -> {
                    // 실시간 상황 화면으로 이동
                    // startActivity(Intent(this, LiveActivity::class.java))
                    Toast.makeText(this, "실시간 상황 화면으로 이동", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_history -> {
                    // 이력 화면으로 이동
                    // startActivity(Intent(this, HistoryActivity::class.java))
                    Toast.makeText(this, "이력 화면으로 이동", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_location -> {
                    // 위치 정보 화면으로 이동
                    // startActivity(Intent(this, LocationActivity::class.java))
                    Toast.makeText(this, "위치 정보 화면으로 이동", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 오늘 날짜 기준으로 미점검 항목이 있는 날 중 가장 가까운 날짜를 반환합니다.
     */
    private fun findClosestUncheckedDay(): Int? {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        
        // 미점검 항목이 있는 날짜들만 필터링
        val uncheckedDays = dailyCheckMap.filter { entry ->
            entry.value.any { it.status == "미점검" }
        }.keys

        if (uncheckedDays.isEmpty()) return today // 미점검이 없으면 오늘 날짜 반환

        // 오늘과 가장 가까운 날짜 찾기 (절대값 기준)
        return uncheckedDays.minByOrNull { abs(it - today) }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            // 터치 시 툴팁을 제거
            dailyAdapter.dismissTooltip()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun updateDailyCheckList(day: Int?) {
        val list = dailyCheckMap[day] ?: emptyList()
        dailyAdapter.updateList(list)

        val totalCount = list.size
        val uncheckedCount = list.count { it.status == "미점검" }
        
        val progressText = "$uncheckedCount/$totalCount"
        val spannable = SpannableString(progressText)

        val orangeColor = ContextCompat.getColor(this, R.color.orange500) // 미점검 개수
        val grayColor = ContextCompat.getColor(this, R.color.gray600) // 전체 개수
        
        val separatorIndex = progressText.indexOf("/")
        
        if (separatorIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(orangeColor), 0, separatorIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(grayColor), separatorIndex, progressText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        findViewById<TextView>(R.id.tv_progress).text = spannable
        adjustRecyclerMinHeight()
    }

    private fun adjustRecyclerMinHeight() {
        val rv = findViewById<RecyclerView>(R.id.rv_daily_check)
        val spacer = findViewById<View>(R.id.rv_spacer)
        val scroll = findViewById<NestedScrollView>(R.id.home_scroll)

        rv.post {
            val rvBottom = rv.bottom
            val scrollBottom = scroll.bottom
            val gap = scrollBottom - rvBottom
            spacer.layoutParams.height = if (gap > 0) gap else 0
            spacer.requestLayout()
        }
    }

    private fun hasUncheckedItem(day: Int): Boolean {
        val list = dailyCheckMap[day] ?: return false
        return list.any { it.status == "미점검" }
    }

    private fun fillCalendarReal() {
        val grid = findViewById<GridLayout>(R.id.calendar_grid)
        val tvMonth = findViewById<TextView>(R.id.tv_month)

        grid.removeAllViews()

        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        tvMonth.text = "${year}년 ${month + 1}월"
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val prevCalendar = calendar.clone() as Calendar
        prevCalendar.add(Calendar.MONTH, -1)
        val prevMaxDay = prevCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 1 until firstDayOfWeek) {
            val prevDay = prevMaxDay - (firstDayOfWeek - 1 - i)
            addCalendarDay(grid, prevDay, false)
        }

        for (day in 1..daysInMonth) {
            addCalendarDay(grid, day, true)
        }

        val totalCells = (firstDayOfWeek - 1) + daysInMonth
        val nextDaysToShow = (7 - (totalCells % 7)) % 7
        for (day in 1..nextDaysToShow) {
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
            tv.setTextColor(Color.parseColor("#58616A"))
            if (day == selectedDay) {
                tv.background = circleDrawable("#FF5722")
                tv.setTextColor(Color.WHITE)
            }
        } else {
            tv.setTextColor(Color.parseColor("#BEC5CC"))
        }

        val alarmDot = if (isCurrentMonth) {
            ImageView(this).apply {
                setImageResource(R.drawable.ellipse_alram)
                visibility = if (hasUncheckedItem(day)) View.VISIBLE else View.INVISIBLE
                val size = (resources.displayMetrics.density * 6).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    topMargin = (resources.displayMetrics.density * 4).toInt()
                }
            }
        } else {
            View(this).apply {
                val size = (resources.displayMetrics.density * 6).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    topMargin = (resources.displayMetrics.density * 4).toInt()
                }
            }
        }

        dayFrame.addView(tv)
        dayContainer.addView(dayFrame)
        dayContainer.addView(alarmDot)
        grid.addView(dayContainer)
    }

    private fun circleDrawable(color: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(color))
        }
    }

    private fun checkInviteCodeDialog() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val isInviteDone = prefs.getBoolean(KEY_INVITE_DONE, false)
        // 이미 성공했거나 팝업을 본 적이 있다면 띄우지 않음
        if (!isInviteDone) showInviteCodeDialog()
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

        dialog.setOnDismissListener {
            isInviteDialogShowing = false
        }

        btnSubmit.setOnClickListener {
            val inputCode = etInviteCode.text.toString().trim()
            if (inputCode.isEmpty()) {
                tvError.visibility = View.VISIBLE
                etInviteCode.setBackgroundResource(R.drawable.bg_edittext_error)
                return@setOnClickListener
            }
            if (inputCode == "1234") {
                // 성공적으로 입력함
                saveInviteDone(success = true)
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
            // 건너뜀 (다시는 안 뜨지만, 설정에서는 보여야 함)
            saveInviteDone(success = false)
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val params = dialog.window?.attributes
        params?.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.attributes = params
    }

    private fun saveInviteDone(success: Boolean) {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
        prefs.putBoolean(KEY_INVITE_DONE, true) // 팝업 완료 기록
        if (success) {
            prefs.putBoolean(KEY_INVITE_SUCCESS, true) // 성공 기록
        }
        prefs.apply()
    }

    private fun resetInviteForTest() {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().clear().apply()
    }
}
