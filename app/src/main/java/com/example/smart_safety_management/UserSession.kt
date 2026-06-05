package com.example.smart_safety_management

import android.content.Context
import android.content.SharedPreferences

object UserSession {
    private const val PREF_NAME = "user_session"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_PHONE = "user_phone"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_PROFILE_IMAGE = "profile_image_uri"
    private const val KEY_GROUP_ID = "group_id"
    private const val KEY_INVITE_CODE = "invite_code" // 추가
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_AUTH_TOKEN = "auth_token"
    
    private const val KEY_INVITE_CHECKED_PREFIX = "invite_checked_"

    var userRole: UserRole = UserRole.MANAGER
    var userId: String? = null
    var userName: String = ""
    var userPhone: String? = null
    var userEmail: String? = null
    var profileImageUri: String? = null
    var groupId: String? = null
    var inviteCode: String? = null // 현재 사용자의 초대코드 (관리자용)
    var authToken: String? = null
    
    var isInviteChecked: Boolean = false

    fun saveSession(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putString(KEY_USER_PHONE, userPhone)
            putString(KEY_USER_EMAIL, userEmail)
            putString(KEY_USER_ROLE, userRole.name)
            putString(KEY_PROFILE_IMAGE, profileImageUri)
            putString(KEY_GROUP_ID, groupId)
            putString(KEY_INVITE_CODE, inviteCode) // 저장
            putString(KEY_AUTH_TOKEN, authToken)
            putBoolean(KEY_IS_LOGGED_IN, true)
            
            userId?.let {
                putBoolean(KEY_INVITE_CHECKED_PREFIX + it, isInviteChecked)
            }
            apply()
        }
    }

    fun loadSession(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            userId = prefs.getString(KEY_USER_ID, null)
            userName = prefs.getString(KEY_USER_NAME, "") ?: ""
            userPhone = prefs.getString(KEY_USER_PHONE, null)
            userEmail = prefs.getString(KEY_USER_EMAIL, null)
            groupId = prefs.getString(KEY_GROUP_ID, null)
            inviteCode = prefs.getString(KEY_INVITE_CODE, null) // 로드
            authToken = prefs.getString(KEY_AUTH_TOKEN, null)
            val roleStr = prefs.getString(KEY_USER_ROLE, UserRole.MANAGER.name)
            userRole = if (roleStr == UserRole.WORKER.name) UserRole.WORKER else UserRole.MANAGER
            profileImageUri = prefs.getString(KEY_PROFILE_IMAGE, null)
            
            userId?.let {
                isInviteChecked = prefs.getBoolean(KEY_INVITE_CHECKED_PREFIX + it, false)
            }
        }
        return isLoggedIn
    }

    fun clearSession(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply() 
        
        userId = null
        userName = ""
        userPhone = null
        userEmail = null
        userRole = UserRole.MANAGER
        profileImageUri = null
        groupId = null
        inviteCode = null
        authToken = null
        isInviteChecked = false
    }
}
