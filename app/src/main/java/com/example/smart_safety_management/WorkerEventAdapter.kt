package com.example.smart_safety_management

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class WorkerEventAdapter(
    private var items: List<Pair<EventData, EventStatus>>,
    private val onItemClick: ((EventData) -> Unit)? = null
) : RecyclerView.Adapter<WorkerEventAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_event)
        val ivIcon: ImageView = view.findViewById(R.id.iv_event_icon)
        val tvLocation: TextView = view.findViewById(R.id.tv_event_location)
        val tvContent: TextView = view.findViewById(R.id.tv_event_content)
        val tvStatus: TextView = view.findViewById(R.id.tv_event_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_worker_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (event, status) = items[position]
        val context = holder.itemView.context

        holder.tvLocation.text = event.location
        holder.tvContent.text = event.content
        holder.tvStatus.text = event.occurrenceTime

        val isPending = status == EventStatus.PENDING

        // 클릭 상태 초기화
        holder.card.setOnClickListener(null)
        holder.card.isClickable = false
        holder.card.isFocusable = false
        holder.card.isEnabled = false

        // 조치대기(PENDING)일 때만 클릭 활성화
        if (isPending) {
            holder.card.isClickable = true
            holder.card.isFocusable = true
            holder.card.isEnabled = true

            holder.card.setOnClickListener {
                onItemClick?.invoke(event)
            }
        }
        
        // 아이콘 리소스 설정
        val iconRes = when (event.accidentType) {
            "위험" -> R.drawable.danger_icon
            "경고" -> R.drawable.warning_icon
            "주의" -> R.drawable.caution_icon
            else -> 0
        }
        if (iconRes != 0) holder.ivIcon.setImageResource(iconRes)

        // 상태 및 타입에 따른 색상 설정
        if (isPending) {
            val bgAndStrokeColorRes = when (event.accidentType) {
                "위험" -> R.color.red500alpha12_red500alpha36
                "경고" -> R.color.orange400alpha12_orange400alpha36
                "주의" -> R.color.amber500alpha12_amber500alpha36
                else -> R.color.gray50_gray900
            }
            val color = ContextCompat.getColor(context, bgAndStrokeColorRes)
            holder.card.setCardBackgroundColor(color)
            holder.card.strokeColor = color
            
            // 텍스트 및 아이콘 색상 복원 (진하게)
            holder.tvLocation.setTextColor(ContextCompat.getColor(context, R.color.gray900_gray50))
            holder.tvContent.setTextColor(ContextCompat.getColor(context, R.color.gray600))
            holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.gray700_gray400))
            holder.ivIcon.clearColorFilter()
            holder.ivIcon.alpha = 1.0f
        } else {
            // 조치 완료 및 오탐 처리 상태
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.gray50_gray900))
            holder.card.strokeColor = ContextCompat.getColor(context, R.color.gray100_gray950)
            
            // 글씨색 및 아이콘 색상 변경
            val completedTextColor = ContextCompat.getColor(context, R.color.gray500_gray650)
            holder.tvLocation.setTextColor(completedTextColor)
            holder.tvContent.setTextColor(completedTextColor)
            holder.tvStatus.setTextColor(completedTextColor)
            holder.ivIcon.setColorFilter(completedTextColor)
            holder.ivIcon.alpha = 1.0f // 투명도 대신 지정된 색상을 사용
        }
    }

    override fun getItemCount() = items.size
}
