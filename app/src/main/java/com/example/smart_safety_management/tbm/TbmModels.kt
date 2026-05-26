package com.example.smart_safety_management.tbm

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TbmTemplateHazard(
    val id: String,
    val text: String,
    @SerialName("is_custom") @SerializedName("is_custom") val isCustom: Boolean = false,
)

@Serializable
data class TbmTemplateControl(
    val id: String,
    @SerialName("hazard_id") @SerializedName("hazard_id") val hazardId: String? = null,
    val level: String = "control",
    val text: String,
    @SerialName("is_custom") @SerializedName("is_custom") val isCustom: Boolean = false,
)

/**
 * 핵심 안전조치 1 항목. 017 schema 의 key_actions JSONB 는 `[{"id":"a1","text":"..."}]` object array.
 * Fix 2026-05-26: 이전 keyActions: List<String> 은 runtime deserialization 실패 (P0 bug from /gsd-verify-work).
 */
@Serializable
data class TbmTemplateAction(
    val id: String,
    val text: String,
    @SerialName("is_custom") @SerializedName("is_custom") val isCustom: Boolean = false,
)

@Serializable
data class TbmSessionRow(
    @SerialName("session_id") val sessionId: Long,
    @SerialName("group_id") val groupId: Int,
    @SerialName("session_date") val sessionDate: String,
    @SerialName("work_scope") val workScope: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("expected_end_at") val expectedEndAt: String,
    @SerialName("leader_user_id") val leaderUserId: String,
    @SerialName("work_type") val workType: String,
    val location: String? = null,
    val notes: String? = null,
    @SerialName("missed_alert_at") val missedAlertAt: String? = null,
    @SerialName("hazards_snapshot") val hazardsSnapshot: List<TbmTemplateHazard> = emptyList(),
    @SerialName("controls_snapshot") val controlsSnapshot: List<TbmTemplateControl> = emptyList(),
    @SerialName("key_hazard_id") val keyHazardId: String? = null,
    @SerialName("feedback_notes") val feedbackNotes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TbmTemplateRow(
    @SerialName("template_id") val templateId: Int,
    @SerialName("work_type") val workType: String,
    val title: String,
    val description: String? = null,
    val hazards: List<TbmTemplateHazard> = emptyList(),
    val controls: List<TbmTemplateControl> = emptyList(),
    @SerialName("key_actions") val keyActions: List<TbmTemplateAction> = emptyList(),
    val checks: List<String> = emptyList(),
    @SerialName("target_detector") val targetDetector: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_custom") val isCustom: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TbmChecklistRow(
    @SerialName("checklist_id") val checklistId: Long,
    @SerialName("session_id") val sessionId: Long,
    @SerialName("item_idx") val itemIdx: Int,
    @SerialName("item_text") val itemText: String,
    @SerialName("is_checked") val isChecked: Boolean = false,
    val note: String? = null,
    @SerialName("checked_at") val checkedAt: String? = null,
)

@Serializable
data class TbmParticipantRow(
    @SerialName("participant_id") val participantId: Long,
    @SerialName("session_id") val sessionId: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("signed_at") val signedAt: String,
    @SerialName("signature_url") val signatureUrl: String? = null,
    val method: String = "signature",
)

enum class ChangeKind { INSERT, UPDATE, DELETE }

@Serializable
data class GroupRow(
    @SerialName("group_id") val groupId: Int,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("manager_id") val managerId: String,
)

@Serializable
data class TbmStartRequest(
    val action: String = "tbm-start",
    @SerialName("leader_user_id") @SerializedName("leader_user_id") val leaderUserId: String,
    @SerialName("group_id") @SerializedName("group_id") val groupId: Int,
    @SerialName("work_type") @SerializedName("work_type") val workType: String,
    @SerialName("work_scope") @SerializedName("work_scope") val workScope: String,
    @SerialName("expected_end_at") @SerializedName("expected_end_at") val expectedEndAt: String,
    val location: String? = null,
    val notes: String? = null,
    val hazards: List<TbmTemplateHazard> = emptyList(),
    val controls: List<TbmTemplateControl> = emptyList(),
)

@Serializable
data class TbmStartResponse(
    val ok: Boolean? = null,
    @SerialName("session_id") @SerializedName("session_id") val sessionId: Long? = null,
    @SerialName("work_scope") @SerializedName("work_scope") val workScope: String? = null,
    @SerialName("checklist_count") @SerializedName("checklist_count") val checklistCount: Int? = null,
    @SerialName("notified_count") @SerializedName("notified_count") val notifiedCount: Int? = null,
    val error: String? = null,
)

@Serializable
data class TbmCheckinRequest(
    val action: String = "tbm-checkin",
    @SerialName("session_id") @SerializedName("session_id") val sessionId: Long,
    @SerialName("user_id") @SerializedName("user_id") val userId: String,
    @SerialName("signature_url") @SerializedName("signature_url") val signatureUrl: String? = null,
)

@Serializable
data class TbmCheckinResponse(
    val ok: Boolean? = null,
    @SerialName("participant_id") @SerializedName("participant_id") val participantId: Long? = null,
    @SerialName("signed_at") @SerializedName("signed_at") val signedAt: String? = null,
    val idempotent: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class TbmEndRequest(
    val action: String = "tbm-end",
    @SerialName("session_id") @SerializedName("session_id") val sessionId: Long,
    @SerialName("leader_user_id") @SerializedName("leader_user_id") val leaderUserId: String,
    @SerialName("key_hazard_id") @SerializedName("key_hazard_id") val keyHazardId: String? = null,
    @SerialName("feedback_notes") @SerializedName("feedback_notes") val feedbackNotes: String? = null,
)

@Serializable
data class TbmEndResponse(
    val ok: Boolean? = null,
    @SerialName("ended_at") @SerializedName("ended_at") val endedAt: String? = null,
    @SerialName("participant_count") @SerializedName("participant_count") val participantCount: Int? = null,
    val error: String? = null,
)

@Serializable
data class OpsCreateRequest(
    val action: String = "ops-create",
    @SerialName("user_id") @SerializedName("user_id") val userId: String,
    @SerialName("work_type") @SerializedName("work_type") val workType: String,
    val title: String,
    val description: String? = null,
    val hazards: List<TbmTemplateHazard>,
    val controls: List<TbmTemplateControl>,
    @SerialName("key_actions") @SerializedName("key_actions") val keyActions: List<TbmTemplateAction> = emptyList(),
    val checks: List<String> = emptyList(),
    @SerialName("target_detector") @SerializedName("target_detector") val targetDetector: String? = null,
    @SerialName("is_active") @SerializedName("is_active") val isActive: Boolean = true,
)

@Serializable
data class OpsUpdateRequest(
    val action: String = "ops-update",
    @SerialName("user_id") @SerializedName("user_id") val userId: String,
    @SerialName("template_id") @SerializedName("template_id") val templateId: Int,
    val title: String? = null,
    val description: String? = null,
    val hazards: List<TbmTemplateHazard>? = null,
    val controls: List<TbmTemplateControl>? = null,
    @SerialName("key_actions") @SerializedName("key_actions") val keyActions: List<TbmTemplateAction>? = null,
    val checks: List<String>? = null,
    @SerialName("target_detector") @SerializedName("target_detector") val targetDetector: String? = null,
    @SerialName("is_active") @SerializedName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class OpsToggleRequest(
    val action: String = "ops-toggle",
    @SerialName("user_id") @SerializedName("user_id") val userId: String,
    @SerialName("template_id") @SerializedName("template_id") val templateId: Int,
    @SerialName("is_active") @SerializedName("is_active") val isActive: Boolean,
)

@Serializable
data class OpsResponse(
    val ok: Boolean? = null,
    @SerialName("template_id") @SerializedName("template_id") val templateId: Int? = null,
    @SerialName("work_type") @SerializedName("work_type") val workType: String? = null,
    @SerialName("is_active") @SerializedName("is_active") val isActive: Boolean? = null,
    @SerialName("is_custom") @SerializedName("is_custom") val isCustom: Boolean? = null,
    val error: String? = null,
)
