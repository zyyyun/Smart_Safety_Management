package com.example.smart_safety_management

data class DailyCheckItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val desc: String,
    val safetyMeasure: String = "",
    val photoUris: List<String> = emptyList(),
    val status: String
)
