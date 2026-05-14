package com.example.smart_safety_management.watch

/**
 * Phase 7 / 07-03 BRIDGE-03 — MAC 주소 정규식 검증.
 * 패턴: ^([0-9A-F]{2}:){5}[0-9A-F]{2}$
 * 입력은 대소문자 모두 허용 — 내부에서 uppercase + trim 으로 정규화 후 검사.
 * Edge Function 'watch-pair' (07-02) 가 같은 정규식으로 재검증 (T-7-03 client 우회 차단).
 */
object MacAddressValidator {
    private val MAC_REGEX = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    fun isValid(mac: String): Boolean = MAC_REGEX.matches(normalize(mac))

    fun normalize(mac: String): String = mac.uppercase().trim()
}
