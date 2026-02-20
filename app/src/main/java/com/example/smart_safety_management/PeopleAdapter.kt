package com.example.smart_safety_management

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class PeopleAdapter(
    private var items: List<PeopleItem>,
    private val isManager: Boolean,
    private val onDelete: (PeopleItem) -> Unit
) : RecyclerView.Adapter<PeopleAdapter.ViewHolder>() {

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

        if (isManager) {
            holder.checkbox.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
        } else {
            holder.checkbox.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        }

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
            showDeleteDialog(holder.itemView.context, item)
        }
    }

    private fun showDeleteDialog(context: android.content.Context, item: PeopleItem) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.view_confirm_action_dialog)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val layoutParams = dialog.window?.attributes
        layoutParams?.width = (context.resources.displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.attributes = layoutParams

        val tvTitle = dialog.findViewById<TextView>(R.id.tv_title_message)
        val tvMessage = dialog.findViewById<TextView>(R.id.tv_message)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)
        val btnDelete = dialog.findViewById<Button>(R.id.btn_exit)

        tvTitle.text = "[${item.name}]을 삭제합니다"
        tvMessage.text = "해당 참여자를 삭제하시겠습니까?"
        btnCancel.text = "취소"
        btnDelete.text = "삭제"

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            onDelete(item)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun getItemCount() = items.size
}