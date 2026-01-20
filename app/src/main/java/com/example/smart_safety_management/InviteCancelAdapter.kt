package com.example.smart_safety_management

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox

class InviteCancelAdapter(
    private var items: MutableList<InviteContactItem>,
    private val onSelectionChanged: (Int) -> Unit,
    private val onCancelClick: (InviteContactItem) -> Unit
) : RecyclerView.Adapter<InviteCancelAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: MaterialCheckBox = view.findViewById(R.id.checkBox)
        val nameText: TextView = view.findViewById(R.id.nameText)
        val phoneText: TextView = view.findViewById(R.id.phoneText)
        val btnCancelOne: TextView = view.findViewById(R.id.btnCancelOne)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invite_cancel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = item.name
        holder.phoneText.text = item.phoneNumber
        
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = item.isSelected
        
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            item.isSelected = isChecked
            onSelectionChanged(getSelectedCount())
        }

        holder.itemView.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked
        }

        holder.btnCancelOne.setOnClickListener {
            onCancelClick(item)
        }
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

    fun deleteSelected() {
        items.removeAll { it.isSelected }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }

    fun updateData(newItems: List<InviteContactItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }
}
