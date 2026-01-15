package com.example.smart_safety_management

import DailyCheckItem
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class DailyCheckAdapter(
    private var items: List<DailyCheckItem>
) : RecyclerView.Adapter<DailyCheckAdapter.VH>() {

    private var tooltipPopup: TooltipPopup? = null
    private var tooltipPosition: Int = RecyclerView.NO_POSITION
    
    // 사용자가 탭하거나 스크롤하여 툴팁을 끈 경우 다시 표시하지 않기 위한 플래그
    private var isTooltipDismissed = false

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tv_title)
        val desc: TextView = v.findViewById(R.id.tv_desc)
        val statusText: TextView = v.findViewById(R.id.tv_status)
        val statusLayout: LinearLayout = v.findViewById(R.id.layout_status)
        val statusIcon: ImageView = v.findViewById(R.id.img_status)
        val cardView: CardView = v as CardView
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

        if (item.status == "점검완료") {
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context,R.color.gray_50_gray_900))
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.gray_500_gray_650))
            holder.desc.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.gray_500_gray_650))

            holder.statusLayout.setBackgroundResource(R.drawable.bg_status_checked)
            holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.teal_500))
            holder.statusIcon.setImageResource(R.drawable.checked)
            holder.statusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context,R.color.teal_500))
        } else {
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.orange_400_alpha_12_orange_400_alpha_36))
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black_white))
            holder.desc.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.gray_800_gray_200))

            holder.statusLayout.setBackgroundResource(R.drawable.bg_status_unchecked)
            holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context,R.color.orange_500_black))
            holder.statusIcon.setImageResource(R.drawable.orange_bell)
            holder.statusIcon.clearColorFilter()
        }

        // 사용자가 끄지 않았고, 첫 번째 미점검 항목인 경우 툴팁 표시
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
        // 툴팁을 완전히 끄는 것이 아니라, 리스트 갱신을 위해 현재 팝업만 제거합니다.
        tooltipPopup?.dismiss()
        tooltipPopup = null

        items = newItems
        tooltipPosition = findFirstUncheckedPosition()

        notifyDataSetChanged()
    }

    fun updateTooltipPosition() {
        tooltipPopup?.updatePosition()
    }


    // 툴팁을 즉시 제거하고 다시 표시되지 않도록 설정합니다.
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
