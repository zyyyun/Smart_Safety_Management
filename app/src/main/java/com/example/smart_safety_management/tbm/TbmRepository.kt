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
import java.time.ZoneId

class TbmRepository(private val supabase: SupabaseClient) {

    fun todaySessionFlow(groupId: Int): Flow<List<TbmSessionRow>> = flow {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString()
        suspend fun fetch(): List<TbmSessionRow> =
            supabase.from("tbm_sessions").select {
                filter {
                    eq("group_id", groupId)
                    eq("session_date", today)
                }
                order("started_at", Order.DESCENDING)
            }.decodeList<TbmSessionRow>()

        emit(fetch())

        val channel = supabase.channel("tbm_sessions:group_$groupId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "tbm_sessions"
            filter("group_id", FilterOperator.EQ, groupId)
        }
        channel.subscribe()
        try {
            changes.collect { emit(fetch()) }
        } finally {
            channel.unsubscribe()
        }
    }

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

    suspend fun fetchTemplates(): List<TbmTemplateRow> =
        supabase.from("tbm_templates").select {
            order("template_id", Order.ASCENDING)
        }.decodeList<TbmTemplateRow>()

    suspend fun fetchActiveTemplates(): List<TbmTemplateRow> =
        supabase.from("tbm_templates").select {
            filter { eq("is_active", true) }
            order("template_id", Order.ASCENDING)
        }.decodeList<TbmTemplateRow>()

    suspend fun fetchGroupsForManager(managerUserId: String): List<GroupRow> {
        val ownedGroups = supabase.from("groups").select {
            filter { eq("manager_id", managerUserId) }
            order("group_id", Order.ASCENDING)
        }.decodeList<GroupRow>()
        if (ownedGroups.isNotEmpty()) return ownedGroups

        val profile = supabase.from("profiles").select {
            filter { eq("user_id", managerUserId) }
        }.decodeSingleOrNull<ProfileGroupRow>() ?: return emptyList()
        if (profile.userRole != "general_manager" || profile.groupId == null) return emptyList()

        return supabase.from("groups").select {
            filter { eq("group_id", profile.groupId) }
            order("group_id", Order.ASCENDING)
        }.decodeList<GroupRow>()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun todaySessionsFlow(groupIds: List<Int>): Flow<Map<Int, List<TbmSessionRow>>> = callbackFlow {
        val state = mutableMapOf<Int, List<TbmSessionRow>>().apply {
            for (gid in groupIds) put(gid, emptyList())
        }
        trySend(state.toMap())

        val jobs = groupIds.map { gid ->
            launch {
                todaySessionFlow(gid).collect { sessions ->
                    state[gid] = sessions
                    trySend(state.toMap())
                }
            }
        }
        awaitClose {
            jobs.forEach { it.cancel() }
        }
    }

    suspend fun updateChecklistItem(
        checklistId: Long,
        isChecked: Boolean? = null,
        note: String? = null,
    ) {
        if (isChecked == null && note == null) return
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
            if (note != null) set("note", note)
        }) {
            filter { eq("checklist_id", checklistId) }
        }
    }
}
