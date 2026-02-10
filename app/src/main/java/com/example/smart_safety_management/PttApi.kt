package com.example.smart_safety_management

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PttApi {

    /**
     * 음성(WAV) 업로드
     * 서버 엔드포인트 예: POST https://.../ptt/voice
     *
     * multipart/form-data:
     * - managerId: text
     * - workerId: text
     * - durationMs: text
     * - audio: file (audio/wav)
     */
    @Multipart
    @POST("ptt/voice")
    suspend fun uploadVoice(
        @Part("managerId") managerId: RequestBody,
        @Part("workerId") workerId: RequestBody,
        @Part("durationMs") durationMs: RequestBody,
        @Part audio: MultipartBody.Part
    ): UploadVoiceResponse
}

data class UploadVoiceResponse(
    val voiceId: String,
    val storedUrl: String? = null,
    val createdAt: String? = null
)
