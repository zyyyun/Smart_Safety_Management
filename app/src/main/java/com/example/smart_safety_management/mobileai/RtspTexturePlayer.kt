package com.example.smart_safety_management.mobileai

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

@Composable
fun RtspMobileDetectionPlayer(
    url: String,
    cameraId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    val defaultState = remember { MutableStateFlow(MobileFireDetectionState()) }
    val coordinator = remember(textureView, cameraId) {
        val view = textureView
        if (view != null && cameraId > 0) {
            MobileFireDetectionCoordinator(
                cameraId = cameraId,
                sampler = TextureViewFrameSampler(view),
                detector = MobileFireDetectionEngine(context),
                uploader = MobileFireEventRepository()
            )
        } else {
            null
        }
    }
    val collectedState by (coordinator?.state ?: defaultState).collectAsState()
    val badgeState = if (
        coordinator != null &&
        collectedState.status == MobileFireDetectionStatus.OFF
    ) {
        collectedState.copy(status = MobileFireDetectionStatus.WARMING_UP)
    } else {
        collectedState
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                TextureView(viewContext).also { view ->
                    textureView = view
                }
            },
            modifier = Modifier.matchParentSize()
        )

        MobileFireDetectionBadge(
            state = badgeState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )
    }

    DisposableEffect(url, textureView) {
        val view = textureView
        if (view == null) {
            onDispose { }
        } else {
            val libVlc = LibVLC(context, listOf("--rtsp-tcp", "--network-caching=300"))
            val media = Media(libVlc, Uri.parse(url)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":rtsp-tcp")
                addOption(":network-caching=300")
            }
            val player = MediaPlayer(libVlc).apply {
                setMedia(media)
                vlcVout.setVideoView(view)
                vlcVout.attachViews()
                play()
            }
            media.release()

            onDispose {
                runCatching { player.stop() }
                runCatching { player.vlcVout.detachViews() }
                runCatching { player.release() }
                runCatching { libVlc.release() }
            }
        }
    }

    DisposableEffect(coordinator) {
        onDispose {
            coordinator?.close()
        }
    }

    LaunchedEffect(coordinator) {
        delay(3_000L)
        coordinator?.start(this)
    }
}

@Composable
fun MobileFireDetectionBadge(
    state: MobileFireDetectionState,
    modifier: Modifier = Modifier
) {
    val label = when (state.status) {
        MobileFireDetectionStatus.OFF -> "모바일 감지 꺼짐"
        MobileFireDetectionStatus.WARMING_UP -> "모바일 감지 준비 중"
        MobileFireDetectionStatus.RUNNING -> "모바일 감지 실행 중"
        MobileFireDetectionStatus.DETECTED -> "화재 감지"
        MobileFireDetectionStatus.COOLDOWN -> "모바일 감지 대기"
        MobileFireDetectionStatus.ERROR -> "감지 오류"
    }
    val background = when (state.status) {
        MobileFireDetectionStatus.DETECTED -> Color(0xFFE53935).copy(alpha = 0.88f)
        MobileFireDetectionStatus.ERROR -> Color(0xFFB00020).copy(alpha = 0.88f)
        else -> Color.Black.copy(alpha = 0.48f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = background,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1
        )
    }
}
