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
import android.widget.EditText
import android.widget.FrameLayout
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

        // 빈 칸 채우기 (1일 시작 요일 맞추기)
        for (i in 1 until firstDayOfWeek) {
            grid.addView(TextView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            })
        }

        // 날짜 채우기
        for (day in 1..daysInMonth) {

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

                setOnClickListener {
                    selectedDay = day
                    fillCalendarReal()
                    updateDailyCheckList(selectedDay)
                }
            }

            // 날짜 색 지정
            tv.setTextColor(Color.parseColor("#58616A"))

            // 알림 점
            val alarmDot = ImageView(this).apply {
                setImageResource(R.drawable.ellipse_alram)
                visibility = if (hasUncheckedItem(day)) View.VISIBLE else View.INVISIBLE

                val size = (resources.displayMetrics.density * 6).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    topMargin = (resources.displayMetrics.density * 4).toInt()
                }
            }

            // 선택된 날짜 스타일
            if (day == selectedDay) {
                tv.background = circleDrawable("#FF5722")
                tv.setTextColor(Color.WHITE)
            }

            // 여기 핵심
            dayFrame.addView(tv)
            dayContainer.addView(dayFrame)
            dayContainer.addView(alarmDot)

            grid.addView(dayContainer)
        }
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
