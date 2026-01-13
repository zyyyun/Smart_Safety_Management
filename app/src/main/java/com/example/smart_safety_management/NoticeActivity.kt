package com.example.smart_safety_management

import NoticeItem
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NoticeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notice)

        // ===============================
        // 뒤로가기
        // ===============================
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // ===============================
        // View 참조
        // ===============================
        val rv = findViewById<RecyclerView>(R.id.rv_notice)
        val emptyLayout = findViewById<View>(R.id.layout_empty)
        val readAll = findViewById<TextView>(R.id.tv_read_all)
        val unreadCountText = findViewById<TextView>(R.id.tv_unread_count)

        rv.layoutManager = LinearLayoutManager(this)

        // ===============================
        // 임시 알림 리스트 (상태 선택)
        // ===============================

        // ① 새 알림 있음
        val noticeList = mutableListOf(
            NoticeItem("AI 이벤트 감지", "D구역 1열 쓰러짐 감지", "1분 전", false),
            NoticeItem("AI 이벤트 감지", "D구역 2열 쓰러짐 감지", "1분 전", false),
            NoticeItem("안전감시단", "[손흥민] D구역 1열 쓰러짐 감지", "1분 전", true),
            NoticeItem("AI 이벤트 감지", "D구역 4열 화재 감지", "5일 전", true),
        )

        // ② 알림은 있으나 모두 읽음
        /*
        val noticeList = mutableListOf(
            NoticeItem("AI 이벤트 감지", "D구역 1열 쓰러짐 감지", "1일 전", true),
            NoticeItem("안전 점검", "A구역 2열 점검 완료", "2일 전", true)
        )
        */

        // ③ 알림 없음
        /*val noticeList = mutableListOf<NoticeItem>()*/

        // ===============================
        // RecyclerView 연결
        // ===============================
        val adapter = NoticeAdapter(noticeList) {
            // 알림 하나 읽힐 때마다 실행됨
            updateReadAllState(noticeList, readAll, unreadCountText)
        }
        rv.adapter = adapter

        // ===============================
        // 상태 분기 (리스트 / 빈 화면)
        // ===============================
        if (noticeList.isEmpty()) {
            rv.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
        }

        // ===============================
        // "모두 읽음" 상태 설정
        // ===============================
        updateReadAllState(noticeList, readAll, unreadCountText)

        // ===============================
        // "모두 읽음" 클릭
        // ===============================
        readAll.setOnClickListener {
            if (!noticeList.any { !it.isRead }) return@setOnClickListener

            noticeList.forEach { it.isRead = true }
            adapter.notifyDataSetChanged()

            updateReadAllState(noticeList, readAll, unreadCountText)
        }
    }

    // ===============================
    // 새 알림 여부로만 상태 판단
    // ===============================
    private fun updateReadAllState(
        noticeList: List<NoticeItem>,
        readAll: TextView,
        unreadCountText: TextView
    ) {
        // 알림 자체가 없으면 숨김
        if (noticeList.isEmpty()) {
            readAll.visibility = View.GONE
            unreadCountText.visibility = View.GONE
            return
        }

        // 알림이 하나라도 있으면 보이게
        readAll.visibility = View.VISIBLE

        val unreadCount = noticeList.count { !it.isRead }

        if (unreadCount > 0) {
            // 새 알림 있음 → 주황 + 클릭 가능
            readAll.setTextColor(Color.parseColor("#F97316"))
            readAll.isEnabled = true
            readAll.isClickable = true

            unreadCountText.text = unreadCount.toString()
            unreadCountText.visibility = View.VISIBLE
        } else {
            // 새 알림 없음 → 회색 + 클릭 불가
            readAll.setTextColor(Color.parseColor("#4DF97316"))
            readAll.isEnabled = false
            readAll.isClickable = false

            unreadCountText.visibility = View.GONE
        }
    }
}

