package com.example.smart_safety_management.watch

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/**
 * Phase 7 / 07-03 BRIDGE-02a — ack idempotency 동작.
 *
 * Edge Function 'watch-ack' (07-02) 의 응답 계약:
 *   - 첫 호출 (정상): 200 + WatchAckResponse(ok=true, ack_at="...")
 *   - 두 번째 호출 (이미 ack): 404 + 'already acknowledged' (idempotency 가드)
 *
 * SafetyAlertsScreen 의 ack 함수는 두 케이스 모두 사용자 친화적 메시지로 처리
 * (예외 throw X). 본 테스트는 그 분기 로직을 단위 검증.
 */
class WatchAckIdempotencyTest {

    /**
     * 실제 SafetyAlertsScreen 의 ack 분기 로직과 동일한 함수 — Composable 의존성 제거.
     * 두 곳에 동일 분기를 두면 회귀 가능 — 향후 SafetyAlertsScreen 이 본 함수를 호출하도록
     * refactor 가능 (현재는 inline 으로 유지, scope creep 방지).
     */
    private fun classifyAckResponse(resp: Response<WatchAckResponse>): String = when {
        resp.isSuccessful && resp.body()?.ok == true -> "확인됨"
        resp.code() == 404 -> "이미 확인됨"
        else -> "오류 (${resp.code()})"
    }

    @Test
    fun test_firstAck_returnsAcknowledged() = runBlocking {
        var callCount = 0
        val fakeApi = object : NotificationsFunctionsApi {
            override suspend fun callWatchAck(
                url: String, apiKey: String, auth: String, body: WatchAckRequest
            ): Response<WatchAckResponse> {
                callCount++
                return Response.success(
                    WatchAckResponse(ok = true, ack_at = "2026-05-14T10:00:00Z", alert_id = body.alert_id)
                )
            }
            override suspend fun callWatchPair(
                url: String, apiKey: String, auth: String, body: WatchPairRequest
            ) = throw NotImplementedError()
            override suspend fun callWatchReading(
                url: String, apiKey: String, auth: String, body: WatchReadingRequest
            ) = throw NotImplementedError()
        }

        val resp = fakeApi.callWatchAck(
            url = "http://test/functions/v1/notifications",
            apiKey = "anon",
            auth = "Bearer anon",
            body = WatchAckRequest(alert_id = 1, user_id = "testuser1"),
        )
        assertEquals("확인됨", classifyAckResponse(resp))
        assertEquals(1, callCount)
    }

    @Test
    fun test_secondAck_returnsAlreadyAcknowledged_noException() = runBlocking {
        // Idempotency 시나리오: 두 번째 호출에 fakeApi 가 404 + 'already acknowledged' 반환
        val fakeApi = object : NotificationsFunctionsApi {
            private var callCount = 0
            override suspend fun callWatchAck(
                url: String, apiKey: String, auth: String, body: WatchAckRequest
            ): Response<WatchAckResponse> {
                callCount++
                return if (callCount == 1) {
                    Response.success(WatchAckResponse(ok = true, ack_at = "2026-05-14T10:00:00Z", alert_id = body.alert_id))
                } else {
                    val errorBody = """{"error":"already acknowledged"}""".toResponseBody("application/json".toMediaType())
                    Response.error(404, errorBody)
                }
            }
            override suspend fun callWatchPair(
                url: String, apiKey: String, auth: String, body: WatchPairRequest
            ) = throw NotImplementedError()
            override suspend fun callWatchReading(
                url: String, apiKey: String, auth: String, body: WatchReadingRequest
            ) = throw NotImplementedError()
        }

        val req = WatchAckRequest(alert_id = 1, user_id = "testuser1")
        val first = fakeApi.callWatchAck("http://t/notif", "anon", "Bearer anon", req)
        val second = fakeApi.callWatchAck("http://t/notif", "anon", "Bearer anon", req)

        assertEquals("확인됨", classifyAckResponse(first))
        assertEquals("이미 확인됨", classifyAckResponse(second))
        // 예외 throw X — assert 까지 도달했다는 사실이 검증
    }

    @Test
    fun test_5xxError_returnsErrorMessage() = runBlocking {
        val fakeApi = object : NotificationsFunctionsApi {
            override suspend fun callWatchAck(
                url: String, apiKey: String, auth: String, body: WatchAckRequest
            ): Response<WatchAckResponse> {
                return Response.error(
                    500,
                    """{"error":"internal"}""".toResponseBody("application/json".toMediaType())
                )
            }
            override suspend fun callWatchPair(
                url: String, apiKey: String, auth: String, body: WatchPairRequest
            ) = throw NotImplementedError()
            override suspend fun callWatchReading(
                url: String, apiKey: String, auth: String, body: WatchReadingRequest
            ) = throw NotImplementedError()
        }

        val resp = fakeApi.callWatchAck("http://t", "anon", "Bearer anon",
            WatchAckRequest(alert_id = 99, user_id = "testuser1"))
        assertEquals("오류 (500)", classifyAckResponse(resp))
    }
}
