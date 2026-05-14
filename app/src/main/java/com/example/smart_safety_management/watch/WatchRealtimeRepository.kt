package com.example.smart_safety_management.watch

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Phase 7 / 07-03 BRIDGE-01 — supabase-kt 2.2.0 Realtime 3 채널 구독.
 *
 * 채널 (filter: device_id eq paired_device_id):
 *   1. device_watches  (Update → HR/temp/battery snapshot 갱신)
 *   2. wear_state_events (Insert → 가장 최근 wear-state 라벨)
 *   3. safety_alerts (Insert/Update → 카드 마지막 알림 + SafetyAlertsScreen 리스트)
 *
 * raw_events 는 구독 X — 5–6Hz 트래픽 모바일 과부하 (07-CONTEXT D-01).
 *
 * lifecycle: Compose collectLatest 가 cancellation → finally 의 channel.unsubscribe()
 * 호출 → Realtime WSS slot 해소.
 *
 * fallback (D-01 polling): WatchCardComposable 의 LaunchedEffect 가 realtime.status
 * 검사 후 SUBSCRIBED 가 아닐 때 직접 polling 호출 (본 repository 외부에서 처리).
 */
class WatchRealtimeRepository(private val supabase: SupabaseClient) {

    fun deviceWatchFlow(deviceId: Int): Flow<DeviceWatchSnapshot> = flow {
        // 초기 fetch — Realtime 은 미래 변경만 push, 현재 snapshot 은 select 로.
        supabase.from("device_watches").select {
            filter { eq("device_id", deviceId) }
            order("updated_at", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<DeviceWatchSnapshot>()?.let { emit(it) }

        val channel = supabase.channel("device_watches:$deviceId")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "device_watches"
            filter("device_id", FilterOperator.EQ, deviceId)
        }
        channel.subscribe()
        try {
            changes.collect { action -> emit(action.decodeRecord<DeviceWatchSnapshot>()) }
        } finally {
            channel.unsubscribe()
        }
    }

    fun lastWearStateFlow(deviceId: Int): Flow<WearStateEventRow> = flow {
        supabase.from("wear_state_events").select {
            filter { eq("device_id", deviceId) }
            order("ts", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<WearStateEventRow>()?.let { emit(it) }

        val channel = supabase.channel("wear_state_events:$deviceId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "wear_state_events"
            filter("device_id", FilterOperator.EQ, deviceId)
        }
        channel.subscribe()
        try {
            changes.collect { action -> emit(action.decodeRecord<WearStateEventRow>()) }
        } finally {
            channel.unsubscribe()
        }
    }

    /**
     * BRIDGE-01 의 "마지막 알림 1건" + SafetyAlertsScreen 의 리스트.
     * 50건 초기 fetch + Insert/Update 로 incremental update.
     * reducer 자체는 [SafetyAlertReducer] 에 분리 — 단위 테스트 가능.
     */
    fun safetyAlertsFlow(deviceId: Int): Flow<List<SafetyAlertRow>> = flow {
        val initial = supabase.from("safety_alerts").select {
            filter { eq("device_id", deviceId) }
            order("raised_at", Order.DESCENDING)
            limit(50)
        }.decodeList<SafetyAlertRow>()
        emit(initial)
        var current = initial

        val channel = supabase.channel("safety_alerts:$deviceId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "safety_alerts"
            filter("device_id", FilterOperator.EQ, deviceId)
        }
        channel.subscribe()
        try {
            changes.collect { action ->
                current = SafetyAlertReducer.apply(current, action)
                emit(current)
            }
        } finally {
            channel.unsubscribe()
        }
    }
}

/**
 * Pure reducer — 단위 테스트 (WatchRealtimeRepositoryTest) 가 직접 검증.
 * SupabaseClient mock 없이 Insert/Update 머지 동작만 검증.
 */
object SafetyAlertReducer {
    fun apply(current: List<SafetyAlertRow>, action: PostgresAction): List<SafetyAlertRow> = when (action) {
        is PostgresAction.Insert -> {
            val row = action.decodeRecord<SafetyAlertRow>()
            // 같은 alert_id 가 이미 있으면 교체 (드물지만 안전), 아니면 prepend
            if (current.any { it.alertId == row.alertId }) {
                current.map { if (it.alertId == row.alertId) row else it }
            } else {
                listOf(row) + current
            }
        }
        is PostgresAction.Update -> {
            val updated = action.decodeRecord<SafetyAlertRow>()
            current.map { if (it.alertId == updated.alertId) updated else it }
        }
        is PostgresAction.Delete -> {
            // safety_alerts 는 삭제 사용 X (resolved_at 으로만 closing) — 안전 가드.
            // oldRecord 는 PK 만 보장 — REPLICA IDENTITY DEFAULT 라 alert_id 만 추출.
            val key = runCatching {
                action.decodeOldRecord<SafetyAlertRow>().alertId
            }.getOrNull()
            if (key != null) current.filterNot { it.alertId == key } else current
        }
        else -> current
    }

    /**
     * Test-friendly reducer — `apply()` 가 PostgresAction (decodeRecord 가 SupabaseClient 의
     * serializer 컨텍스트에 의존) 을 받아야 하므로 unit test 는 이쪽 simple variant 사용.
     */
    fun applyDirect(current: List<SafetyAlertRow>, kind: ChangeKind, row: SafetyAlertRow): List<SafetyAlertRow> = when (kind) {
        ChangeKind.INSERT ->
            if (current.any { it.alertId == row.alertId }) {
                current.map { if (it.alertId == row.alertId) row else it }
            } else {
                listOf(row) + current
            }
        ChangeKind.UPDATE ->
            current.map { if (it.alertId == row.alertId) row else it }
        ChangeKind.DELETE ->
            current.filterNot { it.alertId == row.alertId }
    }
}

enum class ChangeKind { INSERT, UPDATE, DELETE }
