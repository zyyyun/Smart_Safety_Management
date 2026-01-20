package com.example.smart_safety_management

data class PeopleItem(
    val id: Int,
    val name: String,
    val phone: String,
    val role: String, // "관리자" 또는 "근로자"
    var isChecked: Boolean = false
)