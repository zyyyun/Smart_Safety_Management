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
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<DailyCheckItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
