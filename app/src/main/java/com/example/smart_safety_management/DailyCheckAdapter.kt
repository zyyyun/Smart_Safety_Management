package com.example.smart_safety_management

import DailyCheckItem
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class DailyCheckAdapter(
    private var items: List<DailyCheckItem>
) : RecyclerView.Adapter<DailyCheckAdapter.VH>() {

    private var tooltipPopup: TooltipPopup? = null
    private var tooltipPosition: Int = RecyclerView.NO_POSITION
    
    private var isTooltipDismissed = false

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tv_title)
        val desc: TextView = v.findViewById(R.id.tv_desc)
        val statusText: TextView = v.findViewById(R.id.tv_status)
        val statusLayout: LinearLayout = v.findViewById(R.id.layout_status)
        val statusIcon: ImageView = v.findViewById(R.id.img_status)
        val cardView: MaterialCardView = v as MaterialCardView
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
        holder.statusText.text = item.status

        // 역할에 따른 아이콘 표시 여부 설정 (관리자일 때만 보임)
        if (UserSession.userRole == UserRole.MANAGER) {
            holder.statusIcon.visibility = View.VISIBLE
        } else {
            holder.statusIcon.visibility = View.GONE
        }

        if (item.status == "점검완료") {
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context,R.color.gray50_gray900))
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.gray500_gray650))
            holder.desc.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.gray500_gray650))

            // 점검완료: 클릭 불가, 정적 배경 사용
            holder.statusLayout.isClickable = false
            holder.statusLayout.isFocusable = false
            holder.statusLayout.setBackgroundResource(R.drawable.bg_status_checked)
            
            holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.teal500))
            holder.statusIcon.setImageResource(R.drawable.checked)
            holder.statusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context,R.color.teal500))
            
            // 점검 완료 시 테두리 제거 (투명 처리)
            holder.cardView.strokeWidth = 0
        } else {
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.orange400alpha12_orange400alpha36))
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black_white))
            holder.desc.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.gray800_gray200))

            // 미점검: 클릭 가능
            holder.statusLayout.isClickable = true
            holder.statusLayout.isFocusable = true
            // 셀렉터 적용
            holder.statusLayout.setBackgroundResource(R.drawable.bg_status_unchecked)
            
            holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.orange500_black))
            holder.statusIcon.setImageResource(R.drawable.orange_bell)
            holder.statusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.orange500_black))
            
            // 미점검 시 테두리 추가
            holder.cardView.strokeWidth = (1.142 * holder.itemView.context.resources.displayMetrics.density).toInt()
            holder.cardView.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.orange400alpha20_orange400)

            // 클릭 리스너 구현
            holder.statusLayout.setOnClickListener {
                val context = holder.itemView.context
                val intent = Intent(context, DailyDetailActivity::class.java)
                context.startActivity(intent)
            }
        }

        if (!isTooltipDismissed && position == tooltipPosition && item.status == "미점검") {
            if (tooltipPopup == null) {
                tooltipPopup = TooltipPopup(holder.itemView.context)
            }
            tooltipPopup?.showAlways(holder.statusLayout)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun findFirstUncheckedPosition(): Int {
        return items.indexOfFirst { it.status == "미점검" }
    }

    fun updateList(newItems: List<DailyCheckItem>) {
        tooltipPopup?.dismiss()
        tooltipPopup = null

        items = newItems
        tooltipPosition = findFirstUncheckedPosition()

        notifyDataSetChanged()
    }

    fun updateTooltipPosition() {
        tooltipPopup?.updatePosition()
    }

    fun dismissTooltip() {
        isTooltipDismissed = true
        tooltipPopup?.dismiss()
        tooltipPopup = null
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        if (holder.adapterPosition == tooltipPosition) {
            tooltipPopup?.dismiss()
            tooltipPopup = null
        }
    }

    fun initTooltip() {
        isTooltipDismissed = false
        tooltipPosition = findFirstUncheckedPosition()
        notifyDataSetChanged()
    }
}