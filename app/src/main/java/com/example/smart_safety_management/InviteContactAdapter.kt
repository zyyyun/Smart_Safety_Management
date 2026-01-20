package com.example.smart_safety_management

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox

class InviteContactAdapter(
    private var items: MutableList<InviteContactItem>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<InviteContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layout: View = view.findViewById(R.id.itemLayout)
        val checkBox: MaterialCheckBox = view.findViewById(R.id.checkBox)
        val nameText: TextView = view.findViewById(R.id.nameText)
        val phoneText: TextView = view.findViewById(R.id.phoneText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invite_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = item.name
        holder.phoneText.text = item.phoneNumber
        
        // Update background color based on selection
        updateBackground(holder, item.isSelected)

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = item.isSelected
        
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            item.isSelected = isChecked
            updateBackground(holder, isChecked)
            onSelectionChanged(getSelectedCount())
        }

        holder.itemView.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked
        }
    }

    private fun updateBackground(holder: ViewHolder, isSelected: Boolean) {
        val colorRes = if (isSelected) R.color.gray50_gray900 else R.color.base
        holder.layout.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, colorRes))
    }

    override fun getItemCount() = items.size

    fun getSelectedCount(): Int {
        return items.count { it.isSelected }
    }

    fun selectAll(isSelected: Boolean) {
        items.forEach { it.isSelected = isSelected }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }

    fun clearSelection() {
        items.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onSelectionChanged(0)
    }
    
    fun updateData(newItems: List<InviteContactItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }
}
