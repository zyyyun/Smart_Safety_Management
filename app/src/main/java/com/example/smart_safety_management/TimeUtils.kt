package com.example.smart_safety_management

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    fun formatTimeAgo(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return ""

        // 서버에서 오는 포맷 (YYYY-MM-DD HH:mm)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
        try {
            val date = sdf.parse(dateString) ?: return dateString
            val now = System.currentTimeMillis()
            val diff = now - date.time

            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            val months = days / 30
            val years = days / 365

            return when {
                seconds < 60 -> "방금 전"
                minutes < 60 -> "${minutes}분 전"
                hours < 24 -> "${hours}시간 전"
                days < 30 -> "${days}일 전"
                months < 12 -> "${months}달 전"
                else -> "${years}년 전"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return dateString
        }
    }
}
