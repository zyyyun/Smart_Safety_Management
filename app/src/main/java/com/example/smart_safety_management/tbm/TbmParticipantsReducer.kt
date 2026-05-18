package com.example.smart_safety_management.tbm

import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord

/**
 * Phase 9 / 09-03 TBM-02 — TbmParticipantsReducer (Realtime INSERT/UPDATE/DELETE → state).
 *
 * Phase 7 SafetyAlertReducer 패턴 1:1 미러.
 *
 * - apply(PostgresAction): SupabaseClient serializer context 필요 — 실 환경 호출 경로
 * - applyDirect(ChangeKind, row): pure function — unit test 가 직접 호출 (mock 불필요)
 */
object TbmParticipantsReducer {

    fun apply(current: List<TbmParticipantRow>, action: PostgresAction): List<TbmParticipantRow> = when (action) {
        is PostgresAction.Insert -> {
            val row = action.decodeRecord<TbmParticipantRow>()
            if (current.any { it.participantId == row.participantId }) {
                current.map { if (it.participantId == row.participantId) row else it }
            } else {
                current + listOf(row)
            }
        }
        is PostgresAction.Update -> {
            val updated = action.decodeRecord<TbmParticipantRow>()
            current.map { if (it.participantId == updated.participantId) updated else it }
        }
        is PostgresAction.Delete -> {
            // oldRecord 는 PK 만 보장 (REPLICA IDENTITY DEFAULT) — participant_id 추출.
            val key = action.oldRecord["participant_id"]
                ?.toString()
                ?.trim('"')
                ?.toLongOrNull()
            if (key != null) current.filterNot { it.participantId == key } else current
        }
        else -> current
    }

    /**
     * Test-friendly reducer — Phase 7 SafetyAlertReducer.applyDirect 패턴.
     * mock SupabaseClient 없이 pure mapping 만 검증.
     */
    fun applyDirect(
        current: List<TbmParticipantRow>,
        kind: ChangeKind,
        row: TbmParticipantRow,
    ): List<TbmParticipantRow> = when (kind) {
        ChangeKind.INSERT ->
            if (current.any { it.participantId == row.participantId }) {
                current.map { if (it.participantId == row.participantId) row else it }
            } else {
                current + listOf(row)
            }
        ChangeKind.UPDATE ->
            current.map { if (it.participantId == row.participantId) row else it }
        ChangeKind.DELETE ->
            current.filterNot { it.participantId == row.participantId }
    }
}

/**
 * tbm_checklists 의 Realtime reducer — manager 가 체크 표시할 때 갱신.
 * 작업자는 read-only 라 INSERT 만 받지만 manager UPDATE 시 색상 갱신 필요.
 */
object TbmChecklistsReducer {

    fun applyDirect(
        current: List<TbmChecklistRow>,
        kind: ChangeKind,
        row: TbmChecklistRow,
    ): List<TbmChecklistRow> = when (kind) {
        ChangeKind.INSERT ->
            if (current.any { it.checklistId == row.checklistId }) {
                current.map { if (it.checklistId == row.checklistId) row else it }
            } else {
                current + listOf(row)
            }
        ChangeKind.UPDATE ->
            current.map { if (it.checklistId == row.checklistId) row else it }
        ChangeKind.DELETE ->
            current.filterNot { it.checklistId == row.checklistId }
    }
}
