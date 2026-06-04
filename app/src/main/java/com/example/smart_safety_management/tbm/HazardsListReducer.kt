package com.example.smart_safety_management.tbm

/**
 * Phase 12 — Compose state immutable list pattern (P-NEW-3).
 * TbmStartSection 의 hazards/controls/key_actions 사용자 수정 (add/edit/delete) 을 pure function 으로 표현.
 *
 * 사용 패턴:
 *   var hazards by remember { mutableStateOf(template.hazards) }
 *   ...
 *   hazards = HazardsListReducer.addHazard(hazards, "추가 위험")
 *   hazards = HazardsListReducer.removeById(hazards, "h2")
 *
 * Immutable list 보장 — Compose recomposition 안정.
 *
 * 규칙:
 *   - add: 자동 id 생성 (`h${idx+1}` 패턴, 기존 id 와 충돌 회피)
 *   - edit: id 일치 row 만 text 갱신, 다른 row 무변경
 *   - remove: id 일치 row 제거
 *   - dup id (외부 addByEntity 호출 시) 무시
 *   - is_custom 자동 표기 (사용자가 추가/수정 시 true)
 */
object HazardsListReducer {
    fun addHazard(list: List<TbmTemplateHazard>, text: String): List<TbmTemplateHazard> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return list
        val nextId = nextAvailableId(list.map { it.id }, "h")
        return list + TbmTemplateHazard(id = nextId, text = trimmed, isCustom = true)
    }

    fun addHazardByEntity(list: List<TbmTemplateHazard>, entity: TbmTemplateHazard): List<TbmTemplateHazard> {
        if (list.any { it.id == entity.id }) return list  // dup id reject
        return list + entity
    }

    fun editHazardText(list: List<TbmTemplateHazard>, id: String, newText: String): List<TbmTemplateHazard> {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return list
        return list.map { if (it.id == id) it.copy(text = trimmed, isCustom = true) else it }
    }

    fun removeHazardById(list: List<TbmTemplateHazard>, id: String): List<TbmTemplateHazard> =
        list.filterNot { it.id == id }

    fun addControl(
        list: List<TbmTemplateControl>,
        text: String,
        hazardId: String? = null,
        level: String = "control",
    ): List<TbmTemplateControl> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return list
        val nextId = nextAvailableId(list.map { it.id }, "c")
        return list + TbmTemplateControl(
            id = nextId,
            hazardId = hazardId,
            level = level,
            text = trimmed,
            isCustom = true,
        )
    }

    fun editControlText(list: List<TbmTemplateControl>, id: String, newText: String): List<TbmTemplateControl> {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return list
        return list.map { if (it.id == id) it.copy(text = trimmed, isCustom = true) else it }
    }

    fun removeControlById(list: List<TbmTemplateControl>, id: String): List<TbmTemplateControl> =
        list.filterNot { it.id == id }

    fun addAction(list: List<TbmTemplateAction>, text: String): List<TbmTemplateAction> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return list
        val nextId = nextAvailableId(list.map { it.id }, "a")
        return list + TbmTemplateAction(id = nextId, text = trimmed, isCustom = true)
    }

    fun removeActionById(list: List<TbmTemplateAction>, id: String): List<TbmTemplateAction> =
        list.filterNot { it.id == id }

    /**
     * Generate next id following pattern `<prefix><n>` where n is max existing numeric suffix + 1.
     * Non-conforming ids (no numeric suffix) are skipped.
     */
    private fun nextAvailableId(existing: List<String>, prefix: String): String {
        val maxN = existing
            .filter { it.startsWith(prefix) }
            .mapNotNull { it.removePrefix(prefix).toIntOrNull() }
            .maxOrNull() ?: 0
        return "$prefix${maxN + 1}"
    }
}
