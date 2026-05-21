package com.example.smart_safety_management.rtsp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.example.smart_safety_management.R
import com.example.smart_safety_management.watch.SupabaseModule
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RTSP PoC frame sampler — plan v3.1 (feature_rtps_test).
 *
 * design doc : .planning/explorations/2026-05-21_rtsp_mobile_relay_architecture.md (Approach 5)
 * plan       : ~/.claude/plans/feature-rtps-test-shimmering-fiddle.md (v3.1)
 *
 * 흐름:
 *   1. ExoPlayer + RtspMediaSource 로 RTSP/TCP 풀링 (build.gradle.kts 의존성 그대로)
 *   2. ImageReader.surface 를 video output 으로 → cycle (5초) 마다 acquireLatestImage
 *   3. RGBA_8888 → Bitmap → JPEG (q=85)
 *   4. Supabase Storage `rtsp-poc/cam{id}/{ts}.jpg` upload (anon key, RLS 016_*.sql)
 *
 * 본부 PC 의 [ai_agent/rtsp_poc_pull.py] 가 polling 으로 download → YOLO → detection_events.
 *
 * R1 (이 service) 가 fail 하면 R3 LibVLC fallback 별 commit (plan v3.1 의
 * "R3 LibVLC Fallback Path" 섹션).
 *
 * 패턴 참조: LocationService.kt (Foreground Service + NotificationChannel),
 *           TbmWorkerScreen.kt:180-188 (.storage.from(...).upload(path=, data=, upsert=))
 */
@UnstableApi
class RtspPocService : Service() {

    companion object {
        private const val TAG = "RtspPocService"

        const val EXTRA_CAMERA_ID = "camera_id"
        const val EXTRA_RTSP_URL = "rtsp_url"

        private const val CHANNEL_ID = "rtsp_poc_channel"
        private const val CHANNEL_NAME = "RTSP PoC"
        private const val NOTIF_ID = 4711
        private const val BUCKET = "rtsp-poc"

        // PoC default — 메모리의 Drift X3 RTSP URL (Phase 8 RTSP-02 commit 48f09ac 검증값)
        private const val DEFAULT_RTSP_URL = "rtsp://192.168.0.13/live"
        private const val DEFAULT_CAMERA_ID = 1

        // ImageReader 해상도. 1080p 카메라라도 720p 로 down-sample → JPEG ~100-200KB.
        private const val FRAME_WIDTH = 1280
        private const val FRAME_HEIGHT = 720

        private const val CYCLE_INTERVAL_MS = 5000L
        private const val FIRST_FRAME_TIMEOUT_MS = 10_000L

        /** HomeActivity 토글 동기화용. start/stop 검사 + 중복 onStartCommand 가드. */
        val isRunning = AtomicBoolean(false)
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var player: ExoPlayer? = null
    private var imageReader: ImageReader? = null
    private var playerThread: HandlerThread? = null
    private var playerHandler: Handler? = null

    private var cameraId: Int = DEFAULT_CAMERA_ID
    private var rtspUrl: String = DEFAULT_RTSP_URL

    private var cycleCount: Int = 0
    private var uploadCount: Int = 0
    private var lastError: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "이미 실행 중 — 새 onStartCommand 무시")
            return START_NOT_STICKY
        }

        cameraId = intent?.getIntExtra(EXTRA_CAMERA_ID, DEFAULT_CAMERA_ID) ?: DEFAULT_CAMERA_ID
        rtspUrl = intent?.getStringExtra(EXTRA_RTSP_URL) ?: DEFAULT_RTSP_URL

        Log.i(TAG, "onStartCommand cam=$cameraId url=$rtspUrl")
        startForegroundWithNotification(initial = true)
        startPipeline()

        // PoC 라 죽으면 그냥 종료 (START_NOT_STICKY). 자동 재시작 없음.
        return START_NOT_STICKY
    }

    // ────────────────────────────────────────
    // Foreground notification
    // ────────────────────────────────────────
    private fun startForegroundWithNotification(initial: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notif = buildNotification(initial = initial)

        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } catch (e: Exception) {
                Log.w(TAG, "startForeground DATA_SYNC 실패 → no-type fallback: ${e.message}")
                startForeground(NOTIF_ID, notif)
            }
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(initial: Boolean): Notification {
        val text =
            if (initial) "RTSP cam$cameraId 시작 중…"
            else "cycle=$cycleCount upload=$uploadCount" +
                (lastError?.let { " ⚠️ $it" } ?: "")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RTSP PoC cam$cameraId")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID, buildNotification(initial = false))
    }

    // ────────────────────────────────────────
    // ExoPlayer + ImageReader pipeline
    // ────────────────────────────────────────
    private fun startPipeline() {
        playerThread = HandlerThread("RtspPocPlayer").also { it.start() }
        playerHandler = Handler(playerThread!!.looper)

        playerHandler!!.post {
            try {
                imageReader = ImageReader.newInstance(
                    FRAME_WIDTH,
                    FRAME_HEIGHT,
                    PixelFormat.RGBA_8888,
                    /* maxImages = */ 2,
                )

                val mediaSourceFactory = RtspMediaSource.Factory().apply {
                    // snapshot.py:31 과 동일 정책 — TCP 강제 (UDP 라우터 차단 우회 + relay 호환)
                    setForceUseRtpTcp(true)
                }

                player = ExoPlayer.Builder(this)
                    .setLooper(playerThread!!.looper)
                    .build()
                    .apply {
                        setVideoSurface(imageReader!!.surface)
                        setMediaSource(
                            mediaSourceFactory.createMediaSource(MediaItem.fromUri(rtspUrl))
                        )
                        addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                Log.e(
                                    TAG,
                                    "ExoPlayer error: ${error.errorCodeName} ${error.message}",
                                    error,
                                )
                                lastError = "Player ${error.errorCodeName}"
                                updateNotification()
                            }

                            override fun onRenderedFirstFrame() {
                                Log.i(TAG, "first frame rendered")
                            }
                        })
                        playWhenReady = true
                        prepare()
                    }

                Log.i(TAG, "pipeline 시작 cam=$cameraId url=$rtspUrl")
            } catch (e: Exception) {
                Log.e(TAG, "pipeline 초기화 실패", e)
                lastError = "Init: ${e.message?.take(80)}"
                updateNotification()
                stopSelf()
            }
        }

        // cycle loop (IO scope, ExoPlayer thread 와 분리)
        serviceScope.launch {
            // first frame 도착 대기 (timeout 10s)
            val frameReady = awaitFirstFrame()
            if (!frameReady) {
                Log.e(TAG, "first frame timeout (${FIRST_FRAME_TIMEOUT_MS}ms) — 종료")
                lastError = "First frame timeout"
                updateNotification()
                stopSelf()
                return@launch
            }

            while (isRunning.get()) {
                try {
                    captureAndUploadOneFrame()
                } catch (e: Exception) {
                    Log.e(TAG, "cycle 예외", e)
                    lastError = "Cycle: ${e.message?.take(60)}"
                    updateNotification()
                }
                delay(CYCLE_INTERVAL_MS)
            }
        }
    }

    private suspend fun awaitFirstFrame(): Boolean {
        var waited = 0L
        while (waited < FIRST_FRAME_TIMEOUT_MS && isRunning.get()) {
            val img = imageReader?.acquireLatestImage()
            if (img != null) {
                img.close()
                return true
            }
            delay(500L)
            waited += 500L
        }
        return false
    }

    private fun captureAndUploadOneFrame() {
        val reader = imageReader ?: return
        val img = reader.acquireLatestImage() ?: run {
            Log.w(TAG, "acquireLatestImage null — skip")
            return
        }
        cycleCount += 1

        val bitmap: Bitmap = try {
            // ImageReader RGBA_8888 → Bitmap (rowStride padding 처리)
            val plane = img.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * img.width
            val widthWithPadding = img.width + rowPadding / pixelStride
            val raw = Bitmap.createBitmap(widthWithPadding, img.height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)
            if (rowPadding > 0) {
                val cropped = Bitmap.createBitmap(raw, 0, 0, img.width, img.height)
                raw.recycle()
                cropped
            } else {
                raw
            }
        } catch (e: Exception) {
            Log.w(TAG, "bitmap 변환 실패", e)
            img.close()
            return
        } finally {
            img.close()
        }

        // JPEG compress
        val jpegBytes = ByteArrayOutputStream(64 * 1024).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
        bitmap.recycle()

        if (jpegBytes.isEmpty()) {
            Log.w(TAG, "JPEG empty")
            return
        }

        // Supabase Storage upload (suspend) — IO scope launch
        serviceScope.launch {
            try {
                val ts = System.currentTimeMillis()
                val path = "cam$cameraId/$ts.jpg"
                val client = SupabaseModule.client(this@RtspPocService)
                client.storage.from(BUCKET).upload(path = path, data = jpegBytes, upsert = false)
                uploadCount += 1
                lastError = null
                Log.i(
                    TAG,
                    "upload OK cycle=$cycleCount size=${jpegBytes.size} path=$path",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Storage upload 실패", e)
                lastError = "Upload: ${e.message?.take(60)}"
            }
            updateNotification()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        isRunning.set(false)
        playerHandler?.post {
            try {
                player?.release()
            } catch (e: Exception) {
                Log.w(TAG, "player.release fail: ${e.message}")
            }
            player = null
            try {
                imageReader?.close()
            } catch (e: Exception) {
                Log.w(TAG, "imageReader.close fail: ${e.message}")
            }
            imageReader = null
        }
        serviceJob.cancel()
        playerThread?.quitSafely()
        playerThread = null
        playerHandler = null
    }
}
