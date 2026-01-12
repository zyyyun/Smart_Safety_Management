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
import androidx.recyclerview.widget.RecyclerView

class DailyCheckAdapter(
    private var items: List<DailyCheckItem>
) : RecyclerView.Adapter<DailyCheckAdapter.VH>() {

    /*
     * 현재 화면에 표시 중인 툴팁 PopupWindow
     * 요구사항상 "미점검 중 맨 위 하나"만 필요하므로 하나만 관리한다.
     */
    private var tooltipPopup: TooltipPopup? = null

    /*
     * 툴팁을 표시할 아이템의 position
     * 리스트에서 첫 번째로 등장하는 "미점검" 항목의 위치를 저장한다.
     */
    private var tooltipPosition: Int = RecyclerView.NO_POSITION

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

        // 기본 텍스트 바인딩
        holder.title.text = item.title
        holder.desc.text = item.desc
        holder.statusText.text = item.status

        // 상태에 따라 카드, 텍스트, 배지 UI를 분기 처리
        if (item.status == "점검완료") {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#F4F5F6"))
            holder.title.setTextColor(Color.parseColor("#B1B8BE"))
            holder.desc.setTextColor(Color.parseColor("#B1B8BE"))

            holder.statusLayout.setBackgroundResource(R.drawable.bg_status_checked)
            holder.statusText.setTextColor(Color.parseColor("#04BC93"))
            holder.statusIcon.setImageResource(R.drawable.checked)
            holder.statusIcon.setColorFilter(Color.parseColor("#04BC93"))
        } else {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#1FFB923C"))
            holder.title.setTextColor(Color.parseColor("#000000"))
            holder.desc.setTextColor(Color.parseColor("#33363D"))

            holder.statusLayout.setBackgroundResource(R.drawable.bg_status_unchecked)
            holder.statusText.setTextColor(Color.parseColor("#FB923C"))
            holder.statusIcon.setImageResource(R.drawable.orange_bell)
            holder.statusIcon.clearColorFilter()
        }

        /*
         * 툴팁 표시 조건
         * 1) 현재 아이템이 tooltipPosition과 같고
         * 2) 상태가 "미점검"인 경우에만
         * 상태 배지 바로 위에 항상 툴팁을 표시한다.
         */
        if (position == tooltipPosition && item.status == "미점검") {
            if (tooltipPopup == null) {
                tooltipPopup = TooltipPopup(holder.itemView.context)
            }
            tooltipPopup?.showAlways(holder.statusLayout)
        }
    }

    override fun getItemCount(): Int = items.size

    /*
     * 리스트에서 가장 위에 있는 "미점검" 항목의 위치를 찾는다.
     * 없으면 -1(NO_POSITION)을 반환한다.
     */
    private fun findFirstUncheckedPosition(): Int {
        return items.indexOfFirst { it.status == "미점검" }
    }

    /*
     * 외부에서 리스트가 갱신될 때 호출
     * - 기존 툴팁 제거
     * - 새로운 리스트 기준으로 첫 미점검 위치 계산
     * - 전체 리바인딩
     */
    fun updateList(newItems: List<DailyCheckItem>) {
        tooltipPopup?.dismiss()
        tooltipPopup = null

        items = newItems
        tooltipPosition = findFirstUncheckedPosition()

        notifyDataSetChanged()
    }

    /*
     * RecyclerView 스크롤 시 호출
     * PopupWindow는 뷰 계층 밖에 떠 있으므로
     * 스크롤에 맞춰 위치를 다시 계산해준다.
     */
    fun updateTooltipPosition() {
        tooltipPopup?.updatePosition()
    }

    /*
     * ViewHolder가 재사용될 때 호출
     * 툴팁 대상 View가 사라지는 상황을 방지하기 위해 정리한다.
     */
    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        if (holder.adapterPosition == tooltipPosition) {
            tooltipPopup?.dismiss()
            tooltipPopup = null
        }
    }

    /*
     * Adapter를 RecyclerView에 처음 연결한 직후 호출
     * 최초 툴팁 표시 위치를 계산하기 위한 초기화 메서드
     */
    fun initTooltip() {
        tooltipPosition = findFirstUncheckedPosition()
        notifyDataSetChanged()
    }
}
