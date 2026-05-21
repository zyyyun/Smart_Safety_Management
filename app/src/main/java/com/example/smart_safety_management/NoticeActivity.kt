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
        Log.d("NoticeActivity", "onCreate ENTER session.userId=${UserSession.userId}")
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
            // ✅ 알림 클릭 시 리스트에서 제거하고 서버에서도 삭제
            deleteNotification(clickedItem)
        }
        rv.adapter = adapter

        // ===============================
        // "모두 읽음" 클릭 -> 모두 삭제
        // ===============================
        readAll.setOnClickListener {
            if (noticeList.isEmpty()) return@setOnClickListener
            deleteAllNotifications()
        }

        // 데이터 로드
        fetchNotifications()
    }

    private fun fetchNotifications() {
        val userId = UserSession.userId ?: return
        Log.d("NoticeActivity", "fetchNotifications user_id=$userId")

        RetrofitClient.instance.getNotifications(userId).enqueue(object : Callback<GetNotificationsResponse> {
            override fun onResponse(call: Call<GetNotificationsResponse>, response: Response<GetNotificationsResponse>) {
                if (response.isSuccessful) {
                    val notifications = response.body()?.notifications ?: emptyList()
                    Log.d("NoticeActivity", "got ${notifications.size} notifications")
                    noticeList.clear()
                    noticeList.addAll(notifications.map { dto ->
                        NoticeItem(
                            id = dto.id,
                            title = dto.title,
                            content = dto.content,
                            time = TimeUtils.formatTimeAgo(dto.createdAt),
                            isRead = dto.isRead
                        )
                    })
                    adapter.notifyDataSetChanged()
                    updateUIState()
                } else {
                    val errBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                    Log.e("NoticeActivity", "HTTP ${response.code()} for user_id=$userId body=$errBody")
                    Toast.makeText(
                        this@NoticeActivity,
                        "알림을 불러오지 못했습니다. (HTTP ${response.code()})",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

            override fun onFailure(call: Call<GetNotificationsResponse>, t: Throwable) {
                Log.e("NoticeActivity", "Error fetching notifications for user_id=$userId", t)
                Toast.makeText(
                    this@NoticeActivity,
                    "네트워크 오류: ${t.javaClass.simpleName} ${t.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        })
    }

    private fun deleteNotification(item: NoticeItem) {
        val userId = UserSession.userId ?: return
        val request = MarkNotificationReadRequest(userId, item.id)

        // UI에서 즉시 제거
        val position = noticeList.indexOf(item)
        if (position != -1) {
            noticeList.removeAt(position)
            adapter.notifyItemRemoved(position)
            updateUIState()
        }

        // 서버에 삭제 요청
        RetrofitClient.instance.markNotificationsRead(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (!response.isSuccessful) {
                    Log.e("NoticeActivity", "Failed to delete notification")
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("NoticeActivity", "Error deleting notification", t)
            }
        })
    }

    private fun deleteAllNotifications() {
        val userId = UserSession.userId ?: return
        val request = MarkNotificationReadRequest(userId) // notificationId null이면 모두 삭제

        // UI 즉시 초기화
        noticeList.clear()
        adapter.notifyDataSetChanged()
        updateUIState()

        // 서버에 전체 삭제 요청
        RetrofitClient.instance.markNotificationsRead(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (!response.isSuccessful) {
                    Toast.makeText(this@NoticeActivity, "삭제 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@NoticeActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUIState() {
        if (noticeList.isEmpty()) {
            rv.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
            readAll.visibility = View.GONE
            unreadCountText.visibility = View.GONE
        } else {
            rv.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
            readAll.visibility = View.VISIBLE
            
            val unreadCount = noticeList.count { !it.isRead }
            if (unreadCount > 0) {
                unreadCountText.text = unreadCount.toString()
                unreadCountText.visibility = View.VISIBLE
                readAll.setTextColor(Color.parseColor("#F97316"))
            } else {
                unreadCountText.visibility = View.GONE
                readAll.setTextColor(Color.parseColor("#4DF97316"))
            }
        }
    }
}
