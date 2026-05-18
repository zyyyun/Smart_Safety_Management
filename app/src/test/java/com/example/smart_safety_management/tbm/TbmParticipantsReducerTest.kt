package com.example.smart_safety_management.tbm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 9 / 09-03 TBM-02 — TbmParticipantsReducer 의 Insert/Update/Delete 머지 동작 단위 검증.
 *
 * Phase 7 WatchRealtimeRepositoryTest (SafetyAlertReducer) 패턴 1:1 미러.
 * SupabaseClient mock 없이 applyDirect (pure function) 만 검증.
 * 실제 Realtime channel.subscribe()/unsubscribe() lifecycle 은 instrumented test 또는
 * 실 환경 PoC (Plan 09-04) 에서 수행.
 *
 * JUnit4 (org.junit.Test) 사용 — Phase 7 일관.
 */
class TbmParticipantsReducerTest {

    private fun row(
        id: Long,
        uid: String,
        signedAt: String = "2026-05-18T09:00:00Z",
    ) = TbmParticipantRow(
        participantId = id,
        sessionId = 1L,
        userId = uid,
        signedAt = signedAt,
        signatureUrl = null,
        method = "signature",
    )

    @Test
    fun test_insert_intoEmpty_yieldsSingleton() {
        val result = TbmParticipantsReducer.applyDirect(
            current = emptyList(),
            kind = ChangeKind.INSERT,
            row = row(1L, "a"),
        )
        assertEquals(1, result.size)
        assertEquals(1L, result[0].participantId)
    }

    @Test
    fun test_insert_duplicateId_updatesInPlace() {
        // 같은 participant_id 가 들어오면 (드물지만 retry 시나리오) 교체. 중복 X.
        val initial = listOf(row(1L, "a"))
        val replaced = row(1L, "a", signedAt = "2026-05-18T09:01:00Z")
        val result = TbmParticipantsReducer.applyDirect(initial, ChangeKind.INSERT, replaced)

        assertEquals(1, result.size)
        assertEquals("2026-05-18T09:01:00Z", result[0].signedAt)
    }

    @Test
    fun test_update_replacesById_preservesUnrelated() {
        val initial = listOf(row(1L, "a"), row(2L, "b"))
        val updated = row(1L, "a", signedAt = "2026-05-18T09:02:00Z")
        val result = TbmParticipantsReducer.applyDirect(initial, ChangeKind.UPDATE, updated)

        assertEquals(2, result.size)
        val r1 = result.first { it.participantId == 1L }
        assertEquals("2026-05-18T09:02:00Z", r1.signedAt)
        // 다른 row 무영향
        val r2 = result.first { it.participantId == 2L }
        assertEquals("2026-05-18T09:00:00Z", r2.signedAt)
    }

    @Test
    fun test_insert_appendsNewId() {
        val initial = listOf(row(1L, "a"))
        val result = TbmParticipantsReducer.applyDirect(initial, ChangeKind.INSERT, row(2L, "b"))
        assertEquals(2, result.size)
        assertNotNull(result.firstOrNull { it.participantId == 1L })
        assertNotNull(result.firstOrNull { it.participantId == 2L })
    }

    @Test
    fun test_delete_removesById() {
        val initial = listOf(row(1L, "a"), row(2L, "b"))
        val result = TbmParticipantsReducer.applyDirect(initial, ChangeKind.DELETE, row(1L, "a"))
        assertEquals(1, result.size)
        assertEquals(2L, result[0].participantId)
        assertNull(result.firstOrNull { it.participantId == 1L })
    }

    @Test
    fun test_update_unknownId_isNoop() {
        val initial = listOf(row(1L, "a"), row(2L, "b"))
        val unknown = row(99L, "c")
        val result = TbmParticipantsReducer.applyDirect(initial, ChangeKind.UPDATE, unknown)

        // 같은 사이즈, 원본 보존
        assertEquals(2, result.size)
        assertNull(result.firstOrNull { it.participantId == 99L })
    }
}
