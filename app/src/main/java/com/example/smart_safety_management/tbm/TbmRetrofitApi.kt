package com.example.smart_safety_management.tbm

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Phase 9 / 09-03 TBM-02 — Edge Function `/functions/v1/notifications` 호출용 Retrofit interface.
 *
 * URL 은 dynamic — Plan 09-02 가 배포한 운영 endpoint:
 *   `${BuildConfig.SUPABASE_URL}/functions/v1/notifications`
 *
 * action 은 body 의 첫 필드로 라우팅 (Plan 09-02 4 cases):
 *   - tbm-start    (manager → 세션 생성)
 *   - tbm-checkin  (worker → 참여 확정)
 *   - tbm-end      (manager → 세션 종료)
 *   - tbm-missed   (pg_cron 자동 호출, Android 미사용)
 *
 * 헤더:
 *   apikey        : BuildConfig.SUPABASE_ANON_KEY
 *   Authorization : "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
 *
 * Phase 7 NotificationsFunctionsApi (watch-ack / watch-pair) 패턴 1:1 미러.
 */
interface TbmFunctionsApi {

    @POST
    suspend fun callTbmStart(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body body: TbmStartRequest,
    ): Response<TbmStartResponse>

    @POST
    suspend fun callTbmCheckin(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body body: TbmCheckinRequest,
    ): Response<TbmCheckinResponse>

    @POST
    suspend fun callTbmEnd(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body body: TbmEndRequest,
    ): Response<TbmEndResponse>

    @POST
    suspend fun callOpsCreate(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body body: OpsCreateRequest,
    ): Response<OpsResponse>

    @POST
    suspend fun callOpsUpdate(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body body: OpsUpdateRequest,
    ): Response<OpsResponse>

    @POST
    suspend fun callOpsToggle(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body body: OpsToggleRequest,
    ): Response<OpsResponse>
}
