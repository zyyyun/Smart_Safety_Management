package com.example.smart_safety_management.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 7 / 07-03 BRIDGE-01 — SafetyAlertReducer 의 Insert/Update/Delete 머지 동작 단위 검증.
 *
 * 본 테스트는 SupabaseClient mock 없이 reducer 의 결정성만 검증.
 * 실제 Realtime channel.subscribe()/unsubscribe() lifecycle 검증은 instrumented test 또는
 * 실 환경 PoC (Wave 4) 에서 수행 — 여기서는 reducer 의 입력→출력 매핑만.
 *
 * (advisor 권장 #5: hand-rolled fake SupabaseClient 대신 pure reducer 추출).
 */
class WatchRealtimeRepositoryTest {

    private fun row(id: Long, ack: String? = null, resolved: String? = null) = SafetyAlertRow(
        alertId = id,
        deviceId = 1,
        alertType = "TACHY",
        severity = "WARNING",
        raisedAt = "2026-05-14T10:00:00Z",
        resolvedAt = resolved,
        ackAt = ack,
    )

    @Test
    fun test_insert_prependsToList() {
        val current = listOf(row(1), row(2))
        val newRow = row(3)
        val next = SafetyAlertReducer.applyDirect(current, ChangeKind.INSERT, newRow)

        assertEquals(3, next.size)
        assertEquals(3L, next[0].alertId)  // prepend (newest first)
        assertEquals(1L, next[1].alertId)
        assertEquals(2L, next[2].alertId)
    }

    @Test
    fun test_insert_duplicateId_replacesInPlace() {
        // 같은 alert_id 가 들어오면 (드물지만 retry 시나리오) 교체. 중복 X.
        val current = listOf(row(1), row(2))
        val replaced = row(1, ack = "2026-05-14T11:00:00Z")
        val next = SafetyAlertReducer.applyDirect(current, ChangeKind.INSERT, replaced)

        assertEquals(2, next.size)
        val r1 = next.first { it.alertId == 1L }
        assertEquals("2026-05-14T11:00:00Z", r1.ackAt)
    }

    @Test
    fun test_update_replacesByAlertId() {
        // ack_at 채워지는 시나리오 — Edge Function 'watch-ack' 가 UPDATE 발생.
        val current = listOf(row(1), row(2, ack = null))
        val updated = row(2, ack = "2026-05-14T10:30:00Z")
        val next = SafetyAlertReducer.applyDirect(current, ChangeKind.UPDATE, updated)

        assertEquals(2, next.size)
        val r2 = next.first { it.alertId == 2L }
        assertEquals("2026-05-14T10:30:00Z", r2.ackAt)
        // 다른 row 무영향
        val r1 = next.first { it.alertId == 1L }
        assertNull(r1.ackAt)
    }

    @Test
    fun test_update_unknownId_isNoop() {
        val current = listOf(row(1), row(2))
        val unknown = row(99, ack = "2026-05-14T10:30:00Z")
        val next = SafetyAlertReducer.applyDirect(current, ChangeKind.UPDATE, unknown)

        // 같은 사이즈, 모두 ack=null
        assertEquals(2, next.size)
        next.forEach { assertNull(it.ackAt) }
    }

    @Test
    fun test_delete_removesByAlertId() {
        // safety_alerts 는 v1.0 에서 delete 사용 X 이지만 reducer 는 안전 가드 보유.
        val current = listOf(row(1), row(2), row(3))
        val next = SafetyAlertReducer.applyDirect(current, ChangeKind.DELETE, row(2))

        assertEquals(2, next.size)
        assertNotNull(next.firstOrNull { it.alertId == 1L })
        assertNotNull(next.firstOrNull { it.alertId == 3L })
        assertNull(next.firstOrNull { it.alertId == 2L })
    }

    @Test
    fun test_emptyList_insert_returnsSingleton() {
        val next = SafetyAlertReducer.applyDirect(emptyList(), ChangeKind.INSERT, row(1))
        assertEquals(1, next.size)
        assertEquals(1L, next[0].alertId)
    }
}
