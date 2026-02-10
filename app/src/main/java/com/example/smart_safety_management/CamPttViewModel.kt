package com.example.smart_safety_management

import android.media.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class CamPttViewModel(
    private val api: PttApi = RetrofitClient.pttApi
) : ViewModel() {

    var isRecording by mutableStateOf(false)
        private set

    private var audioRecord: AudioRecord? = null
    private val pcmStream = ByteArrayOutputStream()

    private val sampleRate = 16000

    // 🎤 녹음 시작
    fun startRecording() {

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        pcmStream.reset()

        isRecording = true
        audioRecord?.startRecording()

        Thread {
            val buffer = ByteArray(bufferSize)

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    pcmStream.write(buffer, 0, read)
                }
            }
        }.start()
    }

    // 🛑 녹음 종료 + 업로드
    fun stopAndUpload(managerId: String, workerId: String) {

        isRecording = false

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val pcmData = pcmStream.toByteArray()
        val wavData = pcmToWav(pcmData)

        uploadVoice(managerId, workerId, wavData)
    }

    private fun uploadVoice(
        managerId: String,
        workerId: String,
        wavData: ByteArray
    ) {
        viewModelScope.launch {

            try {

                val audioBody = wavData.toRequestBody("audio/wav".toMediaType())

                val audioPart = MultipartBody.Part.createFormData(
                    "audio",
                    "voice.wav",
                    audioBody
                )

                val response = api.uploadVoice(
                    managerId = managerId.toRequestBody("text/plain".toMediaType()),
                    workerId = workerId.toRequestBody("text/plain".toMediaType()),
                    durationMs = "0".toRequestBody("text/plain".toMediaType()),
                    audio = audioPart
                )

                println("🔥 업로드 성공: ${response.voiceId}")

            } catch (e: Exception) {
                e.printStackTrace()
                println("🔥 업로드 실패")
            }
        }
    }

    // PCM → WAV 변환
    private fun pcmToWav(pcmData: ByteArray): ByteArray {

        val header = ByteArray(44)

        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2

        fun writeInt(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
            header[offset + 2] = ((value shr 16) and 0xff).toByte()
            header[offset + 3] = ((value shr 24) and 0xff).toByte()
        }

        fun writeShort(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
        }

        "RIFF".toByteArray().copyInto(header, 0)
        writeInt(4, totalDataLen)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)

        writeInt(16, 16)
        writeShort(20, 1)
        writeShort(22, 1)
        writeInt(24, sampleRate)
        writeInt(28, byteRate)
        writeShort(32, 2)
        writeShort(34, 16)

        "data".toByteArray().copyInto(header, 36)
        writeInt(40, pcmData.size)

        return header + pcmData
    }
}
