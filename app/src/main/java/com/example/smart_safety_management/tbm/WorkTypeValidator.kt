package com.example.smart_safety_management.tbm

object WorkTypeValidator {
    val DEFAULT_ALLOWED: Set<String> = setOf("forklift", "chemical", "hot_work")

    fun normalize(input: String): String = input.lowercase().trim()

    fun isKnownSeed(workType: String): Boolean = normalize(workType) in DEFAULT_ALLOWED

    fun isValid(workType: String, templates: List<TbmTemplateRow>): Boolean {
        val normalized = normalize(workType)
        return templates.any { it.isActive && normalize(it.workType) == normalized }
    }

    fun displayName(workType: String, templates: List<TbmTemplateRow>): String =
        templates.firstOrNull { normalize(it.workType) == normalize(workType) }?.title
            ?: workType.replace('_', ' ')
}
