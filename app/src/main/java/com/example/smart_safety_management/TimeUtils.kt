package com.example.smart_safety_management

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    fun formatTimeAgo(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return ""

        // 서버에서 오는 포맷 (YYYY-MM-DD HH:mm)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
        
        // ✅ 핵심: 서버 데이터(UTC)를 한국 기기 시간과 비교하기 위해 타임존을 UTC로 설정
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        
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

            // 미래 시간일 경우 (서버-클라 미세한 오차)
            if (diff < 0) return "방금 전"

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
