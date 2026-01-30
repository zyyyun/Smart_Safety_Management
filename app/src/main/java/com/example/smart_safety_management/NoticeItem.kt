package com.example.smart_safety_management

data class NoticeItem(
    val id: Int,
    val title: String,
    val content: String,
    val time: String,
    var isRead: Boolean
)
