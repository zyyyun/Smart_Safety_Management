package com.example.smart_safety_management

import DailyCheckItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DailyCheckAdapter(
    private var items: List<DailyCheckItem>
) : RecyclerView.Adapter<DailyCheckAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tv_title)
        val desc: TextView = v.findViewById(R.id.tv_desc)
        val status: TextView = v.findViewById(R.id.tv_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_check, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.desc.text = item.desc
        holder.status.text = item.status

        // 부모 뷰(CardView)와 상태 레이아웃 가져오기
        val cardView = holder.itemView as androidx.cardview.widget.CardView
        val statusLayout = holder.itemView.findViewById<android.widget.LinearLayout>(R.id.layout_status)
        val statusIcon = holder.itemView.findViewById<android.widget.ImageView>(R.id.img_status)

        if (item.status == "점검완료") {
            // 1. 카드 전체 배경색 (회색)
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#F4F5F6"))

            // 2. 제목(title)과 설명(desc) 색상을 회색으로 변경
            holder.title.setTextColor(android.graphics.Color.parseColor("#B1B8BE"))
            holder.desc.setTextColor(android.graphics.Color.parseColor("#B1B8BE"))

            // 3. 상태 배지 배경 (초록색 배경 drawable)
            statusLayout.setBackgroundResource(R.drawable.bg_status_checked)

            // 4. 텍스트 및 아이콘 색상 (초록색)
            holder.status.setTextColor(android.graphics.Color.parseColor("#04BC93"))
            statusIcon.setImageResource(R.drawable.checked) // 완료용 아이콘
            statusIcon.setColorFilter(android.graphics.Color.parseColor("#04BC93"))

        } else {
            // 1. 카드 전체 배경색 (연한 주황 - 기존 유지)
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#1FFB923C"))

            // 2. 제목과 설명 원래 색상으로 복구
            holder.title.setTextColor(android.graphics.Color.parseColor("#000000"))
            holder.desc.setTextColor(android.graphics.Color.parseColor("#33363D"))

            // 3. 상태 배지 배경 (기존 주황 배경 drawable)
            statusLayout.setBackgroundResource(R.drawable.bg_status_unchecked)

            // 4. 텍스트 및 아이콘 색상 (주황색)
            holder.status.setTextColor(android.graphics.Color.parseColor("#FB923C"))
            statusIcon.setImageResource(R.drawable.orange_bell)
            statusIcon.clearColorFilter()
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<DailyCheckItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
