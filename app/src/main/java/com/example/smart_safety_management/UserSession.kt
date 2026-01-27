package com.example.smart_safety_management

object UserSession {
    var userRole: UserRole = UserRole.MANAGER

    var userId: String? = null
    var userName: String = "안정우"
    var userPhone: String? = null
    var userEmail: String? = null
    var profileImageUri: String? = null

    // 관리자용 초대코드 상태
    var isInviteDoneManager: Boolean = false
    var isInviteSuccessManager: Boolean = false

    // 근로자용 초대코드 상태
    var isInviteDoneWorker: Boolean = false
    var isInviteSuccessWorker: Boolean = false
}