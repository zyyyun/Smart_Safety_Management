package com.example.smart_safety_management.tbm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phase 9 / 09-03 TBM-02 — supabase-kt 2.2.0 의 Realtime/PostgREST 디코딩 모델.
 * 013_tbm_schema.sql 스키마 그대로. 컬럼명은 snake_case → SerialName 매핑.
 *
 * Phase 7 WatchModels.kt 패턴 1:1 미러.
 *
 * 4 테이블:
 *   - tbm_sessions    — 일자 × 작업장 × 리더 × 작업유형 (UNIQUE group_id + session_date)
 *   - tbm_templates   — 작업유형별 체크리스트 템플릿 (JSONB)
 *   - tbm_checklists  — 세션별 체크 항목 + 체크 상태
 *   - tbm_participants — 참여 작업자 + 서명 + 체크인 시각 (UNIQUE session + user)
 */

@Serializable
data class TbmSessionRow(
    @SerialName("session_id") val sessionId: Long,
    @SerialName("group_id") val groupId: Int,
    @SerialName("session_date") val sessionDate: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("expected_end_at") val expectedEndAt: String,
    @SerialName("leader_user_id") val leaderUserId: String,
    @SerialName("work_type") val workType: String,
    val location: String? = null,
    val notes: String? = null,
    @SerialName("missed_alert_at") val missedAlertAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TbmTemplateRow(
    @SerialName("template_id") val templateId: Int,
    @SerialName("work_type") val workType: String,
    val title: String,
    val checklist: List<String>,
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

/**
 * Pure reducer 의 변경 종류 — Phase 7 SafetyAlertReducer 의 ChangeKind 와 동일 의미.
 * tbm/ 패키지 안에서는 자체 enum 사용 (watch/ 와 격리, SC #4 코드 경로 분리).
 */
enum class ChangeKind { INSERT, UPDATE, DELETE }

// ─────────────────────────────────────────────────────────────────────────────
// Retrofit payload — Edge Function `/functions/v1/notifications` 4 actions
// (Plan 09-02 의 tbm-start / tbm-checkin / tbm-end / tbm-missed)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TbmStartRequest(
    val action: String = "tbm-start",
    @SerialName("leader_user_id") val leaderUserId: String,
    @SerialName("group_id") val groupId: Int,
    @SerialName("work_type") val workType: String,
    @SerialName("expected_end_at") val expectedEndAt: String,
    val location: String? = null,
    val notes: String? = null,
)

@Serializable
data class TbmStartResponse(
    val ok: Boolean? = null,
    @SerialName("session_id") val sessionId: Long? = null,
    @SerialName("checklist_count") val checklistCount: Int? = null,
    @SerialName("notified_count") val notifiedCount: Int? = null,
    val error: String? = null,
)

@Serializable
data class TbmCheckinRequest(
    val action: String = "tbm-checkin",
    @SerialName("session_id") val sessionId: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("signature_url") val signatureUrl: String? = null,
)

@Serializable
data class TbmCheckinResponse(
    val ok: Boolean? = null,
    @SerialName("participant_id") val participantId: Long? = null,
    @SerialName("signed_at") val signedAt: String? = null,
    val idempotent: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class TbmEndRequest(
    val action: String = "tbm-end",
    @SerialName("session_id") val sessionId: Long,
    @SerialName("leader_user_id") val leaderUserId: String,
)

@Serializable
data class TbmEndResponse(
    val ok: Boolean? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("participant_count") val participantCount: Int? = null,
    val error: String? = null,
)
