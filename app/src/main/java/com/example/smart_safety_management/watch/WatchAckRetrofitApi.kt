package com.example.smart_safety_management.watch

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Phase 7 / 07-03 — Edge Function `/functions/v1/notifications` 호출용 Retrofit interface.
 *
 * URL 은 dynamic — Wave 2 (07-02) 가 배포한 운영 endpoint:
 *   `${BuildConfig.SUPABASE_URL}/functions/v1/notifications`
 *
 * action 은 body 의 첫 필드로 라우팅 (case 'watch-ack' / 'watch-pair' — 07-02-SUMMARY).
 *
 * 헤더:
 *   apikey        : BuildConfig.SUPABASE_ANON_KEY
 *   Authorization : "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
 *
 * 응답: WatchAckResponse / WatchPairResponse — Gson converter 가 decode (RetrofitClient 가
 * GsonConverterFactory 사용 — 본 plan 에 별도 client builder helper 가 SafetyAlertsScreen
 * + PairWatchSection 안에 있음).
 */
interface NotificationsFunctionsApi {

    @POST
    suspend fun callWatchAck(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body body: WatchAckRequest,
    ): Response<WatchAckResponse>

    @POST
    suspend fun callWatchPair(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body body: WatchPairRequest,
    ): Response<WatchPairResponse>
}
