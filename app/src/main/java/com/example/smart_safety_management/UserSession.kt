package com.example.smart_safety_management

object UserSession {
    var userRole: UserRole = UserRole.WORKER
        set(value) {
            field = value
            // 역할이 바뀔 때 해당 역할의 기본 이름으로 설정
            userName = if (value == UserRole.MANAGER) "안정우" else "이강인"
        }

    var userName: String = "이강인"

    // 관리자용 초대코드 상태
    var isInviteDoneManager: Boolean = false
    var isInviteSuccessManager: Boolean = false

    // 근로자용 초대코드 상태
    var isInviteDoneWorker: Boolean = false
    var isInviteSuccessWorker: Boolean = false
}