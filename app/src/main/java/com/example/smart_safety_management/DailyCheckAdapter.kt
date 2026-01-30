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
    private val onOpenDetail: (day: Int, item: DailyCheckItem) -> Unit,          // ✅ 카드 클릭(상세)
    private val onRequestNotify: (day: Int, item: DailyCheckItem) -> Unit        // ✅ 관리자 미점검 클릭(알림)
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

        val isManager = UserSession.userRole == UserRole.MANAGER

        // ✅ 관리자만 종 아이콘 보이게
        holder.statusIcon.visibility = if (isManager) View.VISIBLE else View.GONE

        // ✅ 1) 카드 전체 클릭 -> 상세 열기 (미점검/점검완료 상관없이)
        holder.cardView.setOnClickListener {
            if (currentDay != -1) onOpenDetail(currentDay, item)
        }

        if (item.status == "점검완료") {
            holder.cardView.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.gray50_gray900)
            )
            holder.title.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.gray500_gray650)
            )
            holder.desc.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.gray500_gray650)
            )

            holder.statusLayout.isClickable = false
            holder.statusLayout.isFocusable = false
            holder.statusLayout.setBackgroundResource(R.drawable.bg_status_checked)

            holder.statusText.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.teal500))
            holder.statusIcon.setImageResource(R.drawable.checked)
            holder.statusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.teal500))

            holder.cardView.strokeWidth = 0

        } else {
            // 미점검 UI
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

            // ✅ 2) 미점검 “상태 버튼(칩)” 클릭 동작 분기
            holder.statusLayout.setOnClickListener {
                // 툴팁은 기존처럼 닫기
                isTooltipDismissedPermanently = true
                tooltipPopup?.dismiss()
                tooltipPopup = null

                if (currentDay == -1) return@setOnClickListener

                if (isManager) {
                    // ✅ 관리자: 미점검 버튼 누르면 근로자에게 알림 보내기 (상세는 안 열기)
                    onRequestNotify(currentDay, item)
                } else {
                    // ✅ 근로자: 기존처럼 상세 열기
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

    fun initTooltip() {
        if (!isTooltipDismissedPermanently) {
            tooltipPosition = findFirstUncheckedPosition()
            notifyDataSetChanged()
        }
    }
}
