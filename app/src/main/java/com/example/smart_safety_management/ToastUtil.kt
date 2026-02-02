package com.example.smart_safety_management

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast

object ToastUtil {
    fun showShort(context: Context, message: String) {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.layout_custom_toast, null)
        
        val tvMessage = layout.findViewById<TextView>(R.id.tv_toast_message)
        tvMessage.text = message
        
        val toast = Toast(context.applicationContext)
        // 기본 토스트 위치와 유사하게 조정 (보통 64dp 정도가 표준입니다)
        val yOffset = (64 * context.resources.displayMetrics.density).toInt()
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, yOffset)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.show()
    }

    fun showLong(context: Context, message: String) {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.layout_custom_toast, null)
        
        val tvMessage = layout.findViewById<TextView>(R.id.tv_toast_message)
        tvMessage.text = message
        
        val toast = Toast(context.applicationContext)
        val yOffset = (64 * context.resources.displayMetrics.density).toInt()
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, yOffset)
        toast.duration = Toast.LENGTH_LONG
        toast.view = layout
        toast.show()
    }
}
