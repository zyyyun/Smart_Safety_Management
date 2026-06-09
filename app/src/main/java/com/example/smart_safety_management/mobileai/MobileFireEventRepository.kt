package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.smart_safety_management.CreateMobileFireEventRequest
import com.example.smart_safety_management.CreateMobileFireEventResponse
import com.example.smart_safety_management.RetrofitClient
import com.example.smart_safety_management.SignUpService
import com.example.smart_safety_management.UserSession
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface MobileFireUploader {
    suspend fun upload(cameraId: Int, frame: Bitmap, confidence: Float): Int?
}

class MobileFireEventRepository(
    private val userIdProvider: () -> String? = { UserSession.userId },
    private val authTokenProvider: () -> String? = { UserSession.authToken },
    private val fcmTokenProvider: suspend () -> String? = { fetchFirebaseMessagingToken() },
    private val serviceProvider: () -> SignUpService = { RetrofitClient.instance }
) : MobileFireUploader {
    override suspend fun upload(cameraId: Int, frame: Bitmap, confidence: Float): Int? {
        val userId = userIdProvider()?.trim()
        require(!userId.isNullOrEmpty()) { "userId is required" }

        val authorization = bearerAuthHeader(authTokenProvider()?.trim())
        require(!authorization.isNullOrEmpty()) { "authenticated user token is required" }

        val fcmToken = fcmTokenProvider()?.trim()
        require(!fcmToken.isNullOrEmpty()) { "fcmToken is required" }

        val request = CreateMobileFireEventRequest(
            cameraId = cameraId,
            userId = userId,
            fcmToken = fcmToken,
            accuracy = confidence.coerceIn(0f, 1f).toDouble(),
            jpegBase64 = bitmapToJpegBase64(frame)
        )

        Log.i(TAG, "request cameraId=$cameraId userId=$userId confidence=${request.accuracy}")
        return suspendCancellableCoroutine { continuation ->
            val call = serviceProvider().createMobileFireEvent(authorization, request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback<CreateMobileFireEventResponse> {
                override fun onResponse(
                    call: Call<CreateMobileFireEventResponse>,
                    response: Response<CreateMobileFireEventResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.i(TAG, "response_success cameraId=$cameraId eventId=${response.body()?.eventId}")
                        continuation.resume(response.body()?.eventId)
                    } else {
                        Log.e(TAG, "response_failed cameraId=$cameraId http=${response.code()}")
                        continuation.resumeWithException(
                            IllegalStateException("mobile fire upload failed: HTTP ${response.code()}")
                        )
                    }
                }

                override fun onFailure(call: Call<CreateMobileFireEventResponse>, t: Throwable) {
                    Log.e(TAG, "request_failed cameraId=$cameraId error=${t.message}", t)
                    continuation.resumeWithException(t)
                }
            })
        }
    }

    companion object {
        private const val TAG = "MobileFireUpload"

        fun bitmapToJpegBase64(bitmap: Bitmap, quality: Int = 85): String {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(0, 100), output)
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }

        private suspend fun fetchFirebaseMessagingToken(): String? {
            return suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(task.result)
                    } else {
                        continuation.resumeWithException(
                            task.exception ?: IllegalStateException("Firebase Messaging token unavailable")
                        )
                    }
                }
            }
        }

        fun bearerAuthHeader(token: String?): String? {
            val trimmed = token?.trim()
            if (trimmed.isNullOrEmpty()) return null
            return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
                trimmed
            } else {
                "Bearer $trimmed"
            }
        }
    }
}
