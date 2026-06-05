package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import android.graphics.Color
import com.example.smart_safety_management.CreateMobileFireEventRequest
import com.example.smart_safety_management.CreateMobileFireEventResponse
import com.example.smart_safety_management.SignUpService
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class MobileFireEventRepositoryTest {
    @Test
    fun bitmapToJpegBase64EncodesSolidBitmap() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }

        val encoded = MobileFireEventRepository.bitmapToJpegBase64(bitmap)

        assertTrue(encoded.length > 100)
    }

    @Test
    fun uploadSendsUserIdAndFcmTokenInRequest() = runTest {
        var capturedRequest: CreateMobileFireEventRequest? = null
        val service = serviceCapturingRequest { request ->
            capturedRequest = request
            Response.success(CreateMobileFireEventResponse(eventId = 77, captureId = null, captureImageUrl = null))
        }
        val repository = MobileFireEventRepository(
            userIdProvider = { "worker-1" },
            fcmTokenProvider = { "fcm-abc" },
            serviceProvider = { service }
        )
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.YELLOW)
        }

        val eventId = repository.upload(cameraId = 9, frame = bitmap, confidence = 1.5f)

        assertEquals(77, eventId)
        assertEquals(9, capturedRequest?.cameraId)
        assertEquals("worker-1", capturedRequest?.userId)
        assertEquals("fcm-abc", capturedRequest?.fcmToken)
        assertEquals(1.0, capturedRequest?.accuracy ?: -1.0, 0.0)
        assertTrue((capturedRequest?.jpegBase64?.length ?: 0) > 100)
    }

    private fun serviceCapturingRequest(
        responder: (CreateMobileFireEventRequest) -> Response<CreateMobileFireEventResponse>
    ): SignUpService {
        return Proxy.newProxyInstance(
            SignUpService::class.java.classLoader,
            arrayOf(SignUpService::class.java)
        ) { _, method, args ->
            if (method.name == "createMobileFireEvent") {
                val request = args?.first() as CreateMobileFireEventRequest
                CompletedCall(responder(request))
            } else {
                throw UnsupportedOperationException(method.name)
            }
        } as SignUpService
    }

    private class CompletedCall<T>(
        private val response: Response<T>
    ) : Call<T> {
        override fun enqueue(callback: Callback<T>) {
            callback.onResponse(this, response)
        }

        override fun clone(): Call<T> = CompletedCall(response)
        override fun execute(): Response<T> = response
        override fun isExecuted(): Boolean = false
        override fun cancel() = Unit
        override fun isCanceled(): Boolean = false
        override fun request(): Request = Request.Builder().url("http://localhost/").build()
        override fun timeout(): Timeout = Timeout.NONE
    }
}
