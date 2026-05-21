package com.example.smart_safety_management.tbm

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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

    /**
     * 2026-05-20 Change 1 — admin 이 선택할 그룹 목록.
     *
     * v1.0 PoC: 모든 groups row 반환 (RLS gate 는 v1.1 로 연기 — plan Risk #1 참조).
     * managerUserId 는 향후 (groups.manager_id = managerUserId) 필터를 위해 받아두지만
     * 현재는 사용 안 함. 호출자가 admin 가정 (TbmDashboardActivity 진입점).
     */
    suspend fun fetchGroupsForManager(
        @Suppress("UNUSED_PARAMETER") managerUserId: String,
    ): List<GroupRow> =
        supabase.from("groups").select {
            order("group_id", Order.ASCENDING)
        }.decodeList<GroupRow>()

    /**
     * 2026-05-20 Change 1 — N 그룹의 오늘 세션을 Map 으로 합쳐서 emit.
     *
     * 기존 todaySessionFlow(gid) N 개를 callbackFlow 안에서 동시 구독하고, 자식이
     * 새 값 emit 할 때마다 합본 Map 을 재emit. Compose collectAsState 한 번으로 N 개
     * 그룹의 세션 존재 여부 + sessionId 변화를 추적.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun todaySessionsFlow(groupIds: List<Int>): Flow<Map<Int, TbmSessionRow?>> = callbackFlow {
        val state = mutableMapOf<Int, TbmSessionRow?>().apply {
            for (gid in groupIds) put(gid, null)
        }
        // 초기 state 한 번 emit (모두 null) — UI 가 빈 컬럼 즉시 표시.
        trySend(state.toMap())

        // 자식 flow N개 동시 launch — 각 flow 가 자체 channel.unsubscribe() finally 처리.
        val jobs = groupIds.map { gid ->
            launch {
                todaySessionFlow(gid).collect { session ->
                    state[gid] = session
                    trySend(state.toMap())
                }
            }
        }
        awaitClose {
            jobs.forEach { it.cancel() }
        }
    }

    /**
     * 2026-05-20 Change 2 — tbm_checklists 의 is_checked / note 단일 row PATCH.
     *
     * 014_tbm_checklists_write_policy.sql 의 UPDATE 정책 (anon, authenticated USING true)
     * 으로 RLS 통과. Edge Function 경유 없이 supabase-kt 직접 PATCH.
     *
     * isChecked / note 가 null 이면 해당 컬럼은 update 에서 제외 (partial update).
     * 체크리스트 정형 row 는 isChecked 만, "추가 작업 사항" row 는 note + isChecked 둘 다.
     *
     * is_checked=true 일 때 checked_at 은 클라이언트 시계 (KST → ISO 8601 with offset) 로
     * 갱신, false 면 null 로 clear. PostgREST 는 "now()" literal 함수 호출 직접
     * 지원 안 함 → 클라이언트에서 ISO 8601 string 송신.
     */
    suspend fun updateChecklistItem(
        checklistId: Long,
        isChecked: Boolean? = null,
        note: String? = null,
    ) {
        if (isChecked == null && note == null) return  // 변경 없음 — no-op
        supabase.from("tbm_checklists").update({
            if (isChecked != null) {
                set("is_checked", isChecked)
                set(
                    "checked_at",
                    if (isChecked) ExpectedEndAtValidator.formatForServer(
                        ExpectedEndAtValidator.nowKst()
                    ) else null
                )
            }
            if (note != null) {
                set("note", note)
            }
        }) {
            filter { eq("checklist_id", checklistId) }
        }
    }
}
