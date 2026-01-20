package com.example.smart_safety_management

import java.io.Serializable

data class InviteContactItem(
    val name: String,
    val phoneNumber: String,
    var isSelected: Boolean = false
) : Serializable
