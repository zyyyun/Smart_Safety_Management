package com.example.smart_safety_management

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class ActionDetailViewModel(application: Application) : AndroidViewModel(application) {
    var eventDetail by mutableStateOf<DetectionEventDetailResponse?>(null)
        private set

    var actionType by mutableStateOf("")
    var title by mutableStateOf("")
    var content by mutableStateOf("")
    var attachedPhotos by mutableStateOf<List<String>>(emptyList())

    var isLoading by mutableStateOf(false)
        private set

    var submissionSuccess by mutableStateOf(false)
    var toastMessage by mutableStateOf<String?>(null)

    fun clearToastMessage() {
        toastMessage = null
    }

    fun loadEventDetail(eventId: Int) {
        viewModelScope.launch {
            RetrofitClient.instance.getDetectionEventDetail(eventId).enqueue(object : Callback<DetectionEventDetailResponse> {
                override fun onResponse(call: Call<DetectionEventDetailResponse>, response: Response<DetectionEventDetailResponse>) {
                    if (response.isSuccessful) {
                        eventDetail = response.body()
                        Log.d("ActionDetail", "Event Detail Loaded: $eventDetail")
                    } else {
                        Log.e("ActionDetail", "Failed to load event detail: ${response.code()}")
                    }
                }
                override fun onFailure(call: Call<DetectionEventDetailResponse>, t: Throwable) {
                    Log.e("ActionDetail", "Network error: ${t.message}")
                }
            })
        }
    }

    fun updateAttachedPhotos(uris: List<Uri>, context: Context) {
        if (uris.size > 5) {
            ToastUtil.showShort(context, "사진은 최대 5장까지 첨부 가능합니다.")
            attachedPhotos = uris.take(5).map { it.toString() }
        } else {
            attachedPhotos = uris.map { it.toString() }
        }
    }

    fun submitActionRequest(eventId: Int) {
        if (actionType.isEmpty() || title.isEmpty() || content.isEmpty()) {
            toastMessage = "모든 항목을 입력해주세요."
            return
        }

        isLoading = true
        viewModelScope.launch {
            // I/O 작업을 백그라운드 스레드(Dispatchers.IO)에서 수행
            val imageParts = withContext(Dispatchers.IO) {
                attachedPhotos.mapNotNull { uriString ->
                    val uri = Uri.parse(uriString)
                    uriToFile(getApplication(), uri)?.let { file ->
                        val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
                        MultipartBody.Part.createFormData("images", file.name, requestFile)
                    }
                }
            }

            val eventIdBody = RequestBody.create("text/plain".toMediaTypeOrNull(), eventId.toString())
            val requesterIdBody = RequestBody.create("text/plain".toMediaTypeOrNull(), (UserSession.userId ?: ""))
            val typeBody = RequestBody.create("text/plain".toMediaTypeOrNull(), actionType)
            val titleBody = RequestBody.create("text/plain".toMediaTypeOrNull(), title)
            val detailsBody = RequestBody.create("text/plain".toMediaTypeOrNull(), content)

            RetrofitClient.instance.createActionRequest(
                eventIdBody, requesterIdBody, typeBody, titleBody, detailsBody, imageParts
            ).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    isLoading = false
                    if (response.isSuccessful) {
                        toastMessage = "조치 요청이 완료되었습니다."
                        submissionSuccess = true
                    } else {
                        toastMessage = "요청 실패: ${response.code()}"
                    }
                }
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    isLoading = false
                    toastMessage = "네트워크 오류: ${t.message}"
                }
            })
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("upload", ".jpg", context.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}