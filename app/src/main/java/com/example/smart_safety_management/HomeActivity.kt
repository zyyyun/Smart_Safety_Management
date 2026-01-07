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

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_home)

        // ===============================
        // RecyclerView 설정
        // ===============================
        val rv = findViewById<RecyclerView>(R.id.rv_daily_check)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = DailyCheckAdapter(
            listOf(
                DailyCheckItem("A구역 4일", "정리미흡으로 인적사고 발생 우려", "미점검"),
                DailyCheckItem("D구역 2일", "정리미흡으로 인적사고 발생 우려", "점검완료"),
                DailyCheckItem("D구역 1일", "정리미흡으로 인적사고 발생 우려", "점검완료")
            )
        )

        // ===============================
        // Tooltip 처리
        // ===============================
        val tooltip = findViewById<View>(R.id.tooltip_container)
        val scroll = findViewById<NestedScrollView>(R.id.home_scroll)

        // 스크롤 시작 시 툴팁 숨김
        scroll.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY != oldScrollY) {
                tooltip.visibility = View.GONE
            }
        }

        // 툴팁 클릭 시 숨김
        tooltip.setOnClickListener {
            tooltip.visibility = View.GONE
        }

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
    }
    private var selectedDay: Int? = null

    private fun fillCalendarReal() {
        val grid = findViewById<GridLayout>(R.id.calendar_grid)
        val tvMonth = findViewById<TextView>(R.id.tv_month)

        grid.removeAllViews()

        val calendar = Calendar.getInstance()

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) // 0부터 시작
        val today = calendar.get(Calendar.DAY_OF_MONTH)

        // 상단 "2026년 1월" 오늘 날짜 월 표시
        tvMonth.text = "${year}년 ${month + 1}월"

        // 이번 달 1일로 이동
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=일요일
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 빈 칸 채우기 (1일 시작 요일 맞추기)
        for (i in 1 until firstDayOfWeek) {
            val empty = TextView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            }
            grid.addView(empty)
        }

        // 날짜 채우기
        for (day in 1..daysInMonth) {
            val tv = TextView(this).apply {
                text = day.toString()
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 10)

                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }

                setOnClickListener {
                    selectedDay = day
                    fillCalendarReal()
                }
            }

            // 선택된 날짜
            if (day == selectedDay) {
                tv.background = circleDrawable("#FF5722")
                tv.setTextColor(Color.WHITE)
            }

            grid.addView(tv)
        }
    }

    private fun circleDrawable(color: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(color))
        }
    }

}
