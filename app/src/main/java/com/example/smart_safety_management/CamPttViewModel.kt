package com.example.smart_safety_management

import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class CamPttViewModel(
    private val api: PttApi = RetrofitClient.pttApi
) : ViewModel() {

    companion object {
        private const val TAG = "PTT"
    }

    var isRecording by mutableStateOf(false)
        private set

    var isUploading by mutableStateOf(false)
        private set

    var statusText by mutableStateOf("대기")
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    private var recorder: MediaRecorder? = null
    private var outFile: File? = null
    private var startMs: Long = 0L

    /**
     * context 필요: cacheDir에 임시 파일 생성하려고
     */

    private fun canOpenMic(): Boolean {
        return try {
            val sampleRate = 44100
            val channel = android.media.AudioFormat.CHANNEL_IN_MONO
            val format = android.media.AudioFormat.ENCODING_PCM_16BIT
            val minBuf = android.media.AudioRecord.getMinBufferSize(sampleRate, channel, format)
            if (minBuf <= 0) return false

            val ar = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                channel,
                format,
                minBuf
            )
            ar.startRecording()
            val ok = ar.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING
            ar.stop()
            ar.release()
            ok
        } catch (_: Exception) {
            false
        }
    }

    fun startRecording(context: android.content.Context) {
        if (isRecording || isUploading) return

        if (!canOpenMic()) {
            fail("마이크 입력을 열 수 없어요(시스템 마이크 차단/점유 가능)")
            return
        }

        // 혹시 남아있는 recorder가 있으면 정리
        safeRelease()

        lastError = null
        statusText = "녹음 시작 중..."

        val file = File(context.cacheDir, "ptt_${System.currentTimeMillis()}.m4a")
        outFile = file

        var r: MediaRecorder? = null
        try {
            r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            // ✅ 최소 설정 (Pixel에서 가장 잘 됨)
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            // ❌ 아래는 일단 전부 빼고 테스트
            // r.setAudioSamplingRate(...)
            // r.setAudioChannels(...)
            // r.setAudioEncodingBitRate(...)

            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()

            recorder = r
            isRecording = true
            startMs = System.currentTimeMillis()
            statusText = "녹음 중... (손을 떼면 전송)"

        } catch (e: Exception) {
            val msg = "녹음 시작 실패: ${e.javaClass.simpleName}: ${e.message}"
            lastError = msg
            statusText = msg
            Log.e(TAG, msg, e)

            try { r?.reset() } catch (_: Exception) {}
            try { r?.release() } catch (_: Exception) {}
            recorder = null
            outFile = null
            isRecording = false
        }
    }


    fun stopAndUpload(managerId: String, workerId: String) {
        if (!isRecording) return

        isRecording = false
        statusText = "녹음 종료 중..."

        val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)

        try {
            recorder?.apply {
                try { stop() } catch (e: Exception) {
                    // stop()은 짧게 녹음하면 예외가 날 수 있음
                    Log.w(TAG, "stop() failed", e)
                }
                try { release() } catch (_: Exception) {}
            }
        } finally {
            recorder = null
        }

        val file = outFile
        outFile = null

        if (file == null || !file.exists() || file.length() <= 0) {
            fail("녹음 파일이 없어요(너무 짧게 눌렀거나 녹음 실패)")
            return
        }

        statusText = "전송 중..."
        isUploading = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                Log.d(TAG, "file bytes=${bytes.size}, durationMs=$durationMs")

                val body = bytes.toRequestBody("audio/mp4".toMediaType())
                val audioPart = MultipartBody.Part.createFormData(
                    name = "audio",
                    filename = file.name,
                    body = body
                )

                val res = api.uploadVoice(
                    managerId = managerId.toRequestBody("text/plain".toMediaType()),
                    workerId = workerId.toRequestBody("text/plain".toMediaType()),
                    durationMs = durationMs.toString().toRequestBody("text/plain".toMediaType()),
                    audio = audioPart
                )

                Log.d(TAG, "Upload success voiceId=${res.voiceId}")
                statusText = "전송 완료"
                lastError = null

            } catch (e: Exception) {
                fail("전송 실패: ${e.message}")
                Log.e(TAG, "upload failed", e)
            } finally {
                isUploading = false
                // 임시 파일 삭제
                try { file.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun fail(msg: String) {
        lastError = msg
        statusText = msg
        Log.e(TAG, msg)
    }

    private fun safeRelease() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        outFile = null
        isRecording = false
    }

    override fun onCleared() {
        super.onCleared()
        safeRelease()
    }
}
