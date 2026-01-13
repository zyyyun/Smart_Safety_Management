package com.example.smart_safety_management

import NoticeItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView

class NoticeAdapter(
    private val items: MutableList<NoticeItem>,
    private val onItemReadChanged: () -> Unit // 콜백
) : RecyclerView.Adapter<NoticeAdapter.ViewHolder>() {

    // ===============================
    // ViewHolder
    // ===============================
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_title)
        val content: TextView = view.findViewById(R.id.tv_content)
        val time: TextView = view.findViewById(R.id.tv_time)
        val icon: ImageView = view.findViewById(R.id.iv_notice_icon)
        val root: View = view
    }

    // ===============================
    // View 생성
    // ===============================
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_notice, parent, false)
        return ViewHolder(view)
    }

    // ===============================
    // 아이템 개수
    // ===============================
    override fun getItemCount(): Int = items.size

    // ===============================
    // 데이터 바인딩
    // ===============================
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.title.text = item.title
        holder.content.text = item.content
        holder.time.text = item.time

        // 알림 아이콘 분기
        when (item.title) {
            "AI 이벤트 감지" -> {
                holder.icon.setImageResource(R.drawable.orange_bell)
            }
            "안전감시단" -> {
                holder.icon.setImageResource(R.drawable.ic_orange_manager)
            }
            else -> {
                holder.icon.setImageResource(R.drawable.orange_bell)
            }
        }

        // 읽음 / 안읽음 배경 처리
        holder.root.setBackgroundResource(
            if (item.isRead)
                R.drawable.bg_notice_read
            else
                R.drawable.bg_notice_unread
        )

        // 클릭 시 읽음 처리
        holder.root.setOnClickListener {
            if (!item.isRead) {
                item.isRead = true
                notifyItemChanged(position)

                // Activity에 알림
                onItemReadChanged()
            }
        }
    }
}