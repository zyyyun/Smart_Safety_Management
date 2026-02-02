package com.example.smart_safety_management

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NoticeActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var emptyLayout: View
    private lateinit var readAll: TextView
    private lateinit var unreadCountText: TextView
    private lateinit var adapter: NoticeAdapter
    private val noticeList = mutableListOf<NoticeItem>()

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
        rv = findViewById(R.id.rv_notice)
        emptyLayout = findViewById(R.id.layout_empty)
        readAll = findViewById(R.id.tv_read_all)
        unreadCountText = findViewById(R.id.tv_unread_count)

        rv.layoutManager = LinearLayoutManager(this)

        // ===============================
        // RecyclerView 연결
        // ===============================
        adapter = NoticeAdapter(noticeList) { clickedItem ->
            // 알림 하나 읽힐 때마다 실행됨
            markNotificationAsRead(clickedItem.id)
            updateReadAllState(noticeList, readAll, unreadCountText)
        }
        rv.adapter = adapter

        // ===============================
        // "모두 읽음" 클릭
        // ===============================
        readAll.setOnClickListener {
            if (!noticeList.any { !it.isRead }) return@setOnClickListener
            markAllNotificationsAsRead()
        }

        // 데이터 로드
        fetchNotifications()
    }

    private fun fetchNotifications() {
        val userId = UserSession.userId ?: return

        RetrofitClient.instance.getNotifications(userId).enqueue(object : Callback<GetNotificationsResponse> {
            override fun onResponse(call: Call<GetNotificationsResponse>, response: Response<GetNotificationsResponse>) {
                if (response.isSuccessful) {
                    val notifications = response.body()?.notifications ?: emptyList()
                    noticeList.clear()
                    noticeList.addAll(notifications.map { dto ->
                        NoticeItem(
                            id = dto.id,
                            title = dto.title,
                            content = dto.content,
                            time = dto.createdAt, // 시간 포맷팅이 필요할 수 있음
                            isRead = dto.isRead
                        )
                    })
                    adapter.notifyDataSetChanged()
                    updateUIState()
                } else {
                    Toast.makeText(this@NoticeActivity, "알림을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<GetNotificationsResponse>, t: Throwable) {
                Log.e("NoticeActivity", "Error fetching notifications", t)
                Toast.makeText(this@NoticeActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun markNotificationAsRead(notificationId: Int) {
        val userId = UserSession.userId ?: return
        val request = MarkNotificationReadRequest(userId, notificationId)

        RetrofitClient.instance.markNotificationsRead(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (!response.isSuccessful) {
                    Log.e("NoticeActivity", "Failed to mark notification as read")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("NoticeActivity", "Error marking notification as read", t)
            }
        })
    }

    private fun markAllNotificationsAsRead() {
        val userId = UserSession.userId ?: return
        val request = MarkNotificationReadRequest(userId) // notificationId null이면 모두 읽음

        RetrofitClient.instance.markNotificationsRead(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    noticeList.forEach { it.isRead = true }
                    adapter.notifyDataSetChanged()
                    updateReadAllState(noticeList, readAll, unreadCountText)
                } else {
                    Toast.makeText(this@NoticeActivity, "모두 읽음 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("NoticeActivity", "Error marking all read", t)
                Toast.makeText(this@NoticeActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUIState() {
        if (noticeList.isEmpty()) {
            rv.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
        }
        updateReadAllState(noticeList, readAll, unreadCountText)
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
