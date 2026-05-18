package com.example.smart_safety_management.tbm

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/**
 * Phase 9 / 09-03 TBM-02 — supabase-kt 2.2.0 Realtime 구독 + Dynamic session_id 2-stage 패턴.
 *
 * 채널 (research §Pattern 2 + C2 anti-pattern):
 *   Stage A: tbm_sessions (group_id eq filter) — 오늘 세션 존재 여부 + sessionId 획득
 *   Stage B: tbm_participants (session_id eq filter) — 참여자 갱신
 *   Stage B: tbm_checklists (session_id eq filter) — 체크리스트 갱신
 *
 * **tbm_templates 채널 미구독** (research C2 anti-pattern, line 805) — seed-only,
 * 트래픽 거의 없음. PostgREST 1회 fetch + 캐시 (fetchTemplates 함수).
 *
 * Phase 7 WatchRealtimeRepository 의 3 채널 패턴 직접 미러 — 단 dynamic session_id
 * 의 2-stage 가 추가됨. sessionId 가 nullable 일 때 Stage B 채널은 구독 X.
 *
 * lifecycle: Compose collectLatest 가 cancellation → finally 의 channel.unsubscribe()
 * 호출 → Realtime WSS slot 해소.
 */
class TbmRepository(private val supabase: SupabaseClient) {

    /**
     * Stage A — 오늘 group_id 의 TBM 세션 1건 (UNIQUE group_id + session_date).
     * 초기 PostgREST fetch (오늘 세션 row) + Realtime tbm_sessions filter eq group_id.
     */
    fun todaySessionFlow(groupId: Int): Flow<TbmSessionRow?> = flow {
        val today = LocalDate.now().toString()
        val initial = supabase.from("tbm_sessions").select {
            filter {
                eq("group_id", groupId)
                eq("session_date", today)
            }
            limit(1)
        }.decodeSingleOrNull<TbmSessionRow>()
        emit(initial)

        val channel = supabase.channel("tbm_sessions:group_$groupId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "tbm_sessions"
            filter("group_id", FilterOperator.EQ, groupId)
        }
        channel.subscribe()
        try {
            changes.collect { _ ->
                // 변경 발생 시 오늘 row 재조회 (UNIQUE 보장으로 단건).
                val refreshed = supabase.from("tbm_sessions").select {
                    filter {
                        eq("group_id", groupId)
                        eq("session_date", today)
                    }
                    limit(1)
                }.decodeSingleOrNull<TbmSessionRow>()
                emit(refreshed)
            }
        } finally {
            channel.unsubscribe()
        }
    }

    /**
     * Stage B — 세션의 참여자 리스트 (Realtime 구독 + reducer).
     * sessionId 가 변경되면 caller 가 새 collect 시작 (collectAsState key 로 sessionId).
     */
    fun participantsFlow(sessionId: Long): Flow<List<TbmParticipantRow>> = flow {
        val initial = supabase.from("tbm_participants").select {
            filter { eq("session_id", sessionId) }
            order("signed_at", Order.ASCENDING)
        }.decodeList<TbmParticipantRow>()
        emit(initial)
        var current = initial

        val channel = supabase.channel("tbm_participants:session_$sessionId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "tbm_participants"
            filter("session_id", FilterOperator.EQ, sessionId)
        }
        channel.subscribe()
        try {
            changes.collect { action ->
                current = TbmParticipantsReducer.apply(current, action)
                emit(current)
            }
        } finally {
            channel.unsubscribe()
        }
    }

    /**
     * Stage B — 세션의 체크리스트 리스트 (Realtime 구독 + reducer).
     * manager 가 is_checked 토글 시 worker 측에서도 실시간 갱신.
     */
    fun checklistsFlow(sessionId: Long): Flow<List<TbmChecklistRow>> = flow {
        val initial = supabase.from("tbm_checklists").select {
            filter { eq("session_id", sessionId) }
            order("item_idx", Order.ASCENDING)
        }.decodeList<TbmChecklistRow>()
        emit(initial)
        var current = initial

        val channel = supabase.channel("tbm_checklists:session_$sessionId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "tbm_checklists"
            filter("session_id", FilterOperator.EQ, sessionId)
        }
        channel.subscribe()
        try {
            changes.collect { action ->
                current = when (action) {
                    is PostgresAction.Insert -> {
                        val row = action.decodeRecord<TbmChecklistRow>()
                        TbmChecklistsReducer.applyDirect(current, ChangeKind.INSERT, row)
                    }
                    is PostgresAction.Update -> {
                        val row = action.decodeRecord<TbmChecklistRow>()
                        TbmChecklistsReducer.applyDirect(current, ChangeKind.UPDATE, row)
                    }
                    is PostgresAction.Delete -> {
                        val key = action.oldRecord["checklist_id"]
                            ?.toString()
                            ?.trim('"')
                            ?.toLongOrNull()
                        if (key != null) current.filterNot { it.checklistId == key } else current
                    }
                    else -> current
                }
                emit(current)
            }
        } finally {
            channel.unsubscribe()
        }
    }

    /**
     * tbm_templates seed-only — PostgREST 1회 fetch (research C2 anti-pattern, 채널 구독 X).
     */
    suspend fun fetchTemplates(): List<TbmTemplateRow> =
        supabase.from("tbm_templates").select {
            order("template_id", Order.ASCENDING)
        }.decodeList<TbmTemplateRow>()
}
