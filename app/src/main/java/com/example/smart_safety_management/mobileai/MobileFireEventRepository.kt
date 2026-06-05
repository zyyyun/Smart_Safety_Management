package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import android.util.Base64
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
    private val fcmTokenProvider: suspend () -> String? = { fetchFirebaseMessagingToken() },
    private val serviceProvider: () -> SignUpService = { RetrofitClient.instance }
) : MobileFireUploader {
    override suspend fun upload(cameraId: Int, frame: Bitmap, confidence: Float): Int? {
        val userId = userIdProvider()?.trim()
        require(!userId.isNullOrEmpty()) { "userId is required" }

        val fcmToken = fcmTokenProvider()?.trim()
        require(!fcmToken.isNullOrEmpty()) { "fcmToken is required" }

        val request = CreateMobileFireEventRequest(
            cameraId = cameraId,
            userId = userId,
            fcmToken = fcmToken,
            accuracy = confidence.coerceIn(0f, 1f).toDouble(),
            jpegBase64 = bitmapToJpegBase64(frame)
        )

        return suspendCancellableCoroutine { continuation ->
            val call = serviceProvider().createMobileFireEvent(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback<CreateMobileFireEventResponse> {
                override fun onResponse(
                    call: Call<CreateMobileFireEventResponse>,
                    response: Response<CreateMobileFireEventResponse>
                ) {
                    if (response.isSuccessful) {
                        continuation.resume(response.body()?.eventId)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException("mobile fire upload failed: HTTP ${response.code()}")
                        )
                    }
                }

                override fun onFailure(call: Call<CreateMobileFireEventResponse>, t: Throwable) {
                    continuation.resumeWithException(t)
                }
            })
        }
    }

    companion object {
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
    }
}
