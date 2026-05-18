package com.example.smart_safety_management.tbm

/**
 * Phase 9 / 09-03 TBM-02 — work_type 5종 enum 검증 + normalize.
 *
 * tbm_templates.work_type CHECK 컨벤션:
 *   - 'fire'     화재 위험 작업
 *   - 'electric' 전기 작업
 *   - 'height'   고소 작업
 *   - 'heavy'    중량물 취급
 *   - 'general'  일반 작업
 *
 * isValid 는 case-sensitive — caller 가 normalize() 먼저 호출해야 함.
 * Phase 7 MacAddressValidator 패턴 1:1 미러.
 */
object WorkTypeValidator {
    val ALLOWED: Set<String> = setOf("fire", "electric", "height", "heavy", "general")

    fun isValid(workType: String): Boolean = workType in ALLOWED

    fun normalize(input: String): String = input.lowercase().trim()
}
