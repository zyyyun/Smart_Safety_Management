package com.example.smart_safety_management

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class PeopleAdapter(private var items: List<PeopleItem>) :
    RecyclerView.Adapter<PeopleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkBox)
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtPhone: TextView = view.findViewById(R.id.txtPhone)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    fun updateList(newList: List<PeopleItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_people_management, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtName.text = item.name
        holder.txtPhone.text = item.phone
        holder.txtStatus.text = item.role
        holder.checkbox.isChecked = item.isChecked

        if (item.role == "관리자") {
            holder.txtStatus.setBackgroundResource(R.drawable.bg_badge_blue_outline)
            holder.txtStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.blue500))
        } else {
            holder.txtStatus.setBackgroundResource(R.drawable.bg_badge_gray_outline)
            holder.txtStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.gray600))
        }

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            item.isChecked = isChecked
        }
        
        holder.btnDelete.setOnClickListener {
            // 삭제 로직 (필요 시 구현)
        }
    }

    override fun getItemCount() = items.size
}