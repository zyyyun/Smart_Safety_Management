package com.example.smart_safety_management

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
    private var items: List<DailyCheckItem>,
    private val onOpenDetail: (day: Int, item: DailyCheckItem) -> Unit // ✅ 추가: HomeActivity가 처리
) : RecyclerView.Adapter<DailyCheckAdapter.VH>() {

    private var tooltipPopup: TooltipPopup? = null
    private var tooltipPosition: Int = RecyclerView.NO_POSITION
    private var isTooltipDismissedPermanently = false

    // ✅ 현재 리스트가 “몇일(day)”의 리스트인지 저장
    private var currentDay: Int = -1

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

        if (UserSession.userRole == UserRole.MANAGER) {
            holder.statusIcon.visibility = View.VISIBLE
        } else {
            holder.statusIcon.visibility = View.GONE
        }

        if (item.status == "점검완료") {
            holder.cardView.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.gray50_gray900)
            )
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.gray500_gray650))
            holder.desc.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.gray500_gray650))

            holder.statusLayout.isClickable = false
            holder.statusLayout.isFocusable = false
            holder.statusLayout.setBackgroundResource(R.drawable.bg_status_checked)

            holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.teal500))
            holder.statusIcon.setImageResource(R.drawable.checked)
            holder.statusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.teal500))

            holder.cardView.strokeWidth = 0
        } else {
            holder.cardView.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.orange400alpha12_orange400alpha36)
            )
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black_white))
            holder.desc.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.gray800_gray200))

            holder.statusLayout.isClickable = true
            holder.statusLayout.isFocusable = true
            holder.statusLayout.setBackgroundResource(R.drawable.bg_status_unchecked)

            holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.orange500_black))
            holder.statusIcon.setImageResource(R.drawable.orange_bell)
            holder.statusIcon.setColorFilter(
                ContextCompat.getColor(holder.itemView.context, R.color.orange500_black)
            )

            holder.cardView.strokeWidth =
                (1.142 * holder.itemView.context.resources.displayMetrics.density).toInt()
            holder.cardView.strokeColor =
                ContextCompat.getColor(holder.itemView.context, R.color.orange400alpha12_orange400alpha36)

            // ✅ 미점검 버튼 클릭
            holder.statusLayout.setOnClickListener {
                isTooltipDismissedPermanently = true
                tooltipPopup?.dismiss()
                tooltipPopup = null

                // ✅ Adapter가 화면 이동하지 말고, HomeActivity에게 “열어줘” 요청
                if (currentDay != -1) {
                    onOpenDetail(currentDay, item)
                }
            }
        }

        // 툴팁 노출
        if (!isTooltipDismissedPermanently && position == tooltipPosition && item.status == "미점검") {
            holder.itemView.post {
                if (tooltipPopup == null) {
                    tooltipPopup = TooltipPopup(holder.itemView.context)
                }
                tooltipPopup?.showAlways(holder.statusLayout)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    private fun findFirstUncheckedPosition(): Int {
        return items.indexOfFirst { it.status == "미점검" }
    }

    // ✅ day도 같이 받도록 변경
    fun updateList(day: Int?, newItems: List<DailyCheckItem>) {
        tooltipPopup?.dismiss()
        tooltipPopup = null

        currentDay = day ?: -1
        items = newItems
        tooltipPosition = findFirstUncheckedPosition()

        notifyDataSetChanged()
    }

    fun updateTooltipPosition() {
        tooltipPopup?.updatePosition()
    }

    fun dismissTooltip() {
        tooltipPopup?.dismiss()
        tooltipPopup = null
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
    }

    fun initTooltip() {
        if (!isTooltipDismissedPermanently) {
            tooltipPosition = findFirstUncheckedPosition()
            notifyDataSetChanged()
        }
    }
}
