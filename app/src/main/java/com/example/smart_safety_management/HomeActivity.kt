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
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged

private const val PREF_NAME = "onboarding_prefs"
private const val KEY_INVITE_DONE = "invite_code_done"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_home)

        // 초대코드 창 리셋
        resetInviteForTest()

        // 초대코드 창 띄우기 여부 확인
        checkInviteCodeDialog()

        // 알림 버튼
        val topBar = findViewById<View>(R.id.top_bar)
        val btnAlarm = topBar.findViewById<ImageButton>(R.id.btn_alarm)

        btnAlarm.setOnClickListener {
            val intent = Intent(this, NoticeActivity::class.java)
            startActivity(intent)
        }

        // 월별로 가기 버튼
        findViewById<View>(R.id.layout_monthly_action).setOnClickListener {
            val intent = Intent(this, MonthlyListActivity::class.java)
            startActivity(intent)
        }

        // ===============================
        // RecyclerView 설정
        // ===============================
        val rv = findViewById<RecyclerView>(R.id.rv_daily_check)
        rv.layoutManager = LinearLayoutManager(this)
        dailyAdapter = DailyCheckAdapter(emptyList())
        rv.adapter = dailyAdapter

        // RecyclerView 설정 직후 최초 툴팁 위치 계산
        dailyAdapter.initTooltip()

        // [수정] NestedScrollView에서 스크롤 이벤트를 받아 툴팁 위치를 갱신합니다.
        val scroll = findViewById<NestedScrollView>(R.id.home_scroll)
        scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ ->
            dailyAdapter.updateTooltipPosition()
        })

        // ===============================
        // "추가" 버튼
        // ===============================
        findViewById<View>(R.id.btn_add).setOnClickListener {
            Toast.makeText(this, "추가 클릭", Toast.LENGTH_SHORT).show()
        }

        // ===============================
        // 달력 더미 데이터 채우기
        // ===============================
        fillCalendarReal()

        // 날짜 선택 안 된 초기 상태도 리스트 처리
        updateDailyCheckList(null)
    }

    /** 날짜 클릭 시 리스트 갱신 */
    private fun updateDailyCheckList(day: Int?) {
        val list = dailyCheckMap[day] ?: emptyList()
        dailyAdapter.updateList(list)

        // 리스트 개수 갱신 (미점검 개수 / 총 개수)
        val totalCount = list.size
        val uncheckedCount = list.count { it.status == "미점검" }
        
        val progressText = "$uncheckedCount/$totalCount"
        val spannable = SpannableString(progressText)
        
        // 미점검 수 (주황색: #F97316)
        val orangeColor = Color.parseColor("#F97316")
        val grayColor = Color.parseColor("#6D7882")
        
        val separatorIndex = progressText.indexOf("/")
        
        if (separatorIndex != -1) {
            // 미점검 수 부분 색상 적용
            spannable.setSpan(
                ForegroundColorSpan(orangeColor),
                0,
                separatorIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            // 슬래시와 총 개수 부분 색상 적용
            spannable.setSpan(
                ForegroundColorSpan(grayColor),
                separatorIndex,
                progressText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        findViewById<TextView>(R.id.tv_progress).text = spannable

        // 리스트 높이 체크해서 부족하면 늘리기
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
        val month = calendar.get(Calendar.MONTH) // 0부터 시작

        // 상단 "2026년 1월" 오늘 날짜 월 표시
        tvMonth.text = "${year}년 ${month + 1}월"

        // 이번 달 1일로 이동
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=일요일
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // --- 1. 이전 달 날짜 채우기 (첫째 주 빈칸) ---
        val prevCalendar = calendar.clone() as Calendar
        prevCalendar.add(Calendar.MONTH, -1)
        val prevMaxDay = prevCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 1 until firstDayOfWeek) {
            val prevDay = prevMaxDay - (firstDayOfWeek - 1 - i)
            addCalendarDay(grid, prevDay, false)
        }

        // --- 2. 이번 달 날짜 채우기 ---
        for (day in 1..daysInMonth) {
            addCalendarDay(grid, day, true)
        }

        // --- 3. 다음 달 날짜 채우기 (마지막 주 빈칸) ---
        val totalCells = (firstDayOfWeek - 1) + daysInMonth
        val nextDaysToShow = (7 - (totalCells % 7)) % 7
        for (day in 1..nextDaysToShow) {
            addCalendarDay(grid, day, false)
        }
    }

    private fun addCalendarDay(grid: GridLayout, day: Int, isCurrentMonth: Boolean) {
        // 날짜 + 점 컨테이너
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

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            if (isCurrentMonth) {
                setOnClickListener {
                    selectedDay = day
                    fillCalendarReal()
                    updateDailyCheckList(selectedDay)
                }
            }
        }

        // 날짜 색 지정
        if (isCurrentMonth) {
            tv.setTextColor(Color.parseColor("#58616A"))
            // 선택된 날짜 스타일
            if (day == selectedDay) {
                tv.background = circleDrawable("#FF5722")
                tv.setTextColor(Color.WHITE)
            }
        } else {
            // 지난달/다음달 날짜는 회색
            tv.setTextColor(Color.parseColor("#BEC5CC"))
        }

        // 알림 점
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
            // 다른 달 날짜인 경우 점 자리에 빈 공간만 차지하도록 함 (정렬 맞추기용)
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

        if (!isInviteDone) {
            showInviteCodeDialog()
        }
    }

    private fun showInviteCodeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.invite_code, null)

        val etInviteCode = dialogView.findViewById<EditText>(R.id.et_invite_code)
        val btnSubmit = dialogView.findViewById<View>(R.id.btn_submit)
        val tvError = dialogView.findViewById<View>(R.id.tv_error)

        val tvSkip = dialogView.findViewById<TextView>(R.id.tv_skip_invite_code)

        // 초대코드없이 진행 텍스트 밑줄 추가
        tvSkip.paintFlags =
            tvSkip.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false) // ❗ 뒤로가기 + 바깥 터치 막기
            .create()

        btnSubmit.setOnClickListener {
            val inputCode = etInviteCode.text.toString().trim()

            // 비어있을 때
            if (inputCode.isEmpty()) {
                tvError.visibility = View.VISIBLE
                etInviteCode.setBackgroundResource(R.drawable.bg_edittext_error)
                return@setOnClickListener
            }

            // 코드 검증
            if (inputCode == "1234") { // 임시 성공 코드
                saveInviteDone()
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

        // 초대코드 없이 진행
        tvSkip.setOnClickListener {
            saveInviteDone()   // 다시 안 뜨게 처리
            dialog.dismiss()   // 창 닫기
        }

        dialog.show()

        // 다이얼로그 배경을 투명하게 설정하여 둥근 모서리가 보이도록 함
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 창의 너비를 화면의 85% 수준으로 조정
        val params = dialog.window?.attributes
        params?.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.attributes = params
    }

    private fun saveInviteDone() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_INVITE_DONE, true)
            .apply()
    }

    // 초대코드 창 테스트용 리셋
    private fun resetInviteForTest() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
