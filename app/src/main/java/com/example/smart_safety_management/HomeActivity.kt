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
import com.google.android.material.appbar.MaterialToolbar

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
        fillCalendarDummy()
    }

    /**
     * 달력 GridLayout에 1~31 더미 날짜 채우기
     */
    private fun fillCalendarDummy() {
        val grid = findViewById<GridLayout>(R.id.calendar_grid)
        grid.removeAllViews()

        for (day in 1..31) {
            val tv = TextView(this).apply {
                text = day.toString()
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 14, 0, 14)
            }
            grid.addView(tv)
        }
    }
}
