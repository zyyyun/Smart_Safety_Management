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
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    var userRole: UserRole = UserRole.MANAGER
    var userId: String? = null
    var userName: String = ""
    var userPhone: String? = null
    var userEmail: String? = null
    var profileImageUri: String? = null

    var isInviteDoneManager: Boolean = false
    var isInviteSuccessManager: Boolean = false
    var isInviteDoneWorker: Boolean = false
    var isInviteSuccessWorker: Boolean = false

    fun saveSession(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putString(KEY_USER_PHONE, userPhone)
            putString(KEY_USER_EMAIL, userEmail)
            putString(KEY_USER_ROLE, userRole.name)
            putString(KEY_PROFILE_IMAGE, profileImageUri)
            putBoolean(KEY_IS_LOGGED_IN, true)
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
            val roleStr = prefs.getString(KEY_USER_ROLE, UserRole.MANAGER.name)
            userRole = if (roleStr == UserRole.WORKER.name) UserRole.WORKER else UserRole.MANAGER
            profileImageUri = prefs.getString(KEY_PROFILE_IMAGE, null)
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
        isInviteDoneManager = false
        isInviteSuccessManager = false
        isInviteDoneWorker = false
        isInviteSuccessWorker = false
    }
}
