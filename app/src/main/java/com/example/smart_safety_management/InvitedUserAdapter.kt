package com.example.smart_safety_management

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class InvitedUserAdapter(
    private val items: MutableList<InviteContactItem>,
    private val onReinviteClick: (InviteContactItem) -> Unit
) : RecyclerView.Adapter<InvitedUserAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.nameText)
        val phoneText: TextView = view.findViewById(R.id.phoneText)
        val btnReinvite: TextView = view.findViewById(R.id.btnReinvite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invited_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = item.name
        holder.phoneText.text = item.phoneNumber
        
        holder.btnReinvite.setOnClickListener {
            onReinviteClick(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<InviteContactItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
