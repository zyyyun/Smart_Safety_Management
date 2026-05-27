package com.example.smart_safety_management.screens.detail

import android.util.Log
import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.RetrofitClient
import com.example.smart_safety_management.CCTVStreamInfoResponse
import com.example.smart_safety_management.GetCameraCapturesResponse
import com.example.smart_safety_management.CameraCaptureDTO
import com.example.smart_safety_management.R
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlinx.coroutines.delay
import com.example.smart_safety_management.LivePlaybackController

/**
 * ✅ 지금은 CCTV/시그널링이 없으니:
 * - 전경/현장은 "이미지 + LIVE 배지 + 연결대기"로 보여줌
 * - 나중에 CCTV/WebRTC 도입 시:
 *   1) LiveCardItem에 streamId(전경/현장) 필드를 추가하고 값 주기
 *   2) 아래 TODO(시그널링 붙일 위치)만 구현하면 자동으로 WebRTC 화면으로 전환되게 설계
 *
 * ⚠️ LiveCardItem에 아래 필드를 "나중용"으로 추가해두면 제일 깔끔함:
 *   val overviewStreamId: String? = null
 *   val siteStreamId: String? = null
 */

/**
 * Issue 1 (전경/현장 강제 split) 회귀 가드용 pure helper.
 *
 * 같은 카메라(overview==site) 일 때 '현장' 섹션 중복 렌더 방지.
 * - siteUrl null/blank/== overviewUrl → false (현장 섹션 숨김)
 * - 그 외 → true
 *
 * 양쪽 URL 모두 trim 후 비교 — 공백·CRLF 차이로 인한 오탐 방지.
 */
internal fun shouldShowSiteSection(overviewUrl: String?, siteUrl: String?): Boolean {
    val site = siteUrl?.trim().orEmpty()
    if (site.isEmpty()) return false
    val overview = overviewUrl?.trim().orEmpty()
    return site != overview
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalDetailScreen(
    item: LiveCardItem,
    cameraId: Int,
    overviewUrl: String? = null,
    siteUrl: String? = null,
    onBack: () -> Unit,
    onMapClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current

    val bg = if (c.isDark) c.bg else Color.White
    val surface = if (c.isDark) c.surface else Color.White

    val border = c.border
    val text = c.text
    val sub = c.sub

    val side = 24.dp
    val sectionTitleColor = Color(0xFF676F76)

    // 지금 당장은 null로 두면 "연결대기" UI로 보임
    val overviewStreamId: String? = (item as? HasCctvStreamIds)?.overviewStreamId
    val siteStreamId: String? = (item as? HasCctvStreamIds)?.siteStreamId

    // API를 통해 가져온 최신 URL 상태 관리
    var finalOverviewUrl by remember { mutableStateOf(overviewUrl) }
    var finalSiteUrl by remember { mutableStateOf(siteUrl) }

    // 서버에서 가져온 스냅샷 리스트 상태
    var captureImages by remember { mutableStateOf<List<CameraCaptureDTO>>(emptyList()) }

    // 화면 진입 시 상세 정보(URL 등) 다시 조회
    LaunchedEffect(cameraId) {
        if (cameraId != 0) {
            Log.d("InternalDetail", "CCTV 스트림 정보 요청: ID=$cameraId")
            RetrofitClient.instance.getCCTVStreamInfo(cameraId.toString()).enqueue(object : Callback<CCTVStreamInfoResponse> {
                override fun onResponse(call: Call<CCTVStreamInfoResponse>, response: Response<CCTVStreamInfoResponse>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        Log.d("InternalDetail", "URL 수신: 전경=${body?.liveUrl}, 현장=${body?.liveUrlDetail}")
                        finalOverviewUrl = body?.liveUrl
                        finalSiteUrl = body?.liveUrlDetail
                    }
                }
                override fun onFailure(call: Call<CCTVStreamInfoResponse>, t: Throwable) {}
            })

            // 최근 스냅샷 3개 조회
            RetrofitClient.instance.getCameraCaptures(cameraId).enqueue(object : Callback<GetCameraCapturesResponse> {
                override fun onResponse(call: Call<GetCameraCapturesResponse>, response: Response<GetCameraCapturesResponse>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        captureImages = body?.captures ?: emptyList()
                        Log.d("InternalDetail", "스냅샷 로드 완료: ${captureImages.size}개")
                    }
                }
                override fun onFailure(call: Call<GetCameraCapturesResponse>, t: Throwable) {
                    Log.e("InternalDetail", "스냅샷 로드 실패", t)
                }
            })
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${item.camId.removePrefix("CAM ")}) ${item.place} - ${item.tags.joinToString(", ")}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Pretendard,
                        color = text
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "back",
                            tint = text,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onMapClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.map),
                            contentDescription = "map",
                            tint = sub,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bg,
                    scrolledContainerColor = bg,
                    titleContentColor = text,
                    navigationIconContentColor = text,
                    actionIconContentColor = sub
                )
            )
        },
        modifier = modifier
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(bg)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = "이벤트 내용",
                fontSize = 18.sp,
                color = sub,
                fontWeight = FontWeight.Medium,
                fontFamily = Pretendard,
                modifier = Modifier.padding(horizontal = side)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .padding(horizontal = side)
                    .fillMaxWidth()
                    .height(54.5.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, border, RoundedCornerShape(10.dp))
                    .background(surface)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "위치",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = Pretendard,
                    color = sub
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = item.location,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = Pretendard,
                    color = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "전경",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Pretendard,
                color = sectionTitleColor,
                modifier = Modifier.padding(horizontal = side)
            )
            Spacer(Modifier.height(16.dp))

            SmartPreviewCard(
                imageRes = R.drawable.aaa,
                border = border,
                modifier = Modifier
                    .padding(horizontal = side)
                    .fillMaxWidth(),
                isLive = true,
                imageUrl = finalOverviewUrl,
                streamId = overviewStreamId,
                label = "전경"
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "현장",
                fontSize = 18.sp,
                color = sectionTitleColor,
                fontWeight = FontWeight.Medium,
                fontFamily = Pretendard,
                modifier = Modifier.padding(horizontal = side)
            )
            Spacer(Modifier.height(16.dp))

            SmartPreviewCard(
                imageRes = R.drawable.bbb,
                border = border,
                modifier = Modifier
                    .padding(horizontal = side)
                    .fillMaxWidth(),
                isLive = true,
                imageUrl = finalSiteUrl,
                streamId = siteStreamId,
                label = "현장"
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "현장캡쳐",
                fontSize = 18.sp,
                color = sectionTitleColor,
                fontWeight = FontWeight.Medium,
                fontFamily = Pretendard,
                modifier = Modifier.padding(horizontal = side)
            )
            Spacer(Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = side, end = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (captureImages.isNotEmpty()) {
                    items(captureImages) { capture ->
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, border, RoundedCornerShape(14.dp))
                                .background(surface)
                                .clickable { }
                        ) {
                            val baseUrl = RetrofitClient.BASE_URL.removeSuffix("/")
                            val fullUrl = if (capture.imageUrl.startsWith("/")) baseUrl + capture.imageUrl else capture.imageUrl

                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(fullUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Snapshot ${capture.capturedAt}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    items(item.captureThumbs) { res ->
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, border, RoundedCornerShape(14.dp))
                                .background(surface)
                                .clickable { }
                        ) {
                            Image(
                                painter = painterResource(id = res),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * ✅ 이미지 카드 + LIVE + (연결대기 / 연결준비됨 / WebRTC 표시) "자동 스위치"
 *
 * 지금은 CCTV가 없으니:
 * - streamId == null -> 이미지 + "연결대기"
 *
 * 나중에 CCTV/WebRTC 도입 시:
 * - streamId != null -> 여기 TODO 지점에서 WebRTC 화면을 실제로 띄우게 구현
 */
@Composable
private fun SmartPreviewCard(
    imageRes: Int,
    border: Color,
    modifier: Modifier = Modifier,
    isLive: Boolean = true,
    imageUrl: String? = null,
    streamId: String?,
    label: String
) {
    val c = LocalSafeColors.current
    val bg = if (c.isDark) c.surface else Color.White

    val isVideo = isPlayableStreamUrl(imageUrl)

    Box(
        modifier = modifier
            .height(210.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .background(bg)
    ) {
        if (isVideo) {
            VideoPlayer(url = imageUrl!!.trim(), modifier = Modifier.fillMaxSize())
        } else {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = imageRes),
                    error = painterResource(id = imageRes),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (streamId.isNullOrBlank()) {
                // CCTV 아직 없음: 연결대기 UI
                ConnectionPill(
                    text = "연결대기",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            } else {
                // CCTV/WebRTC 정보는 있음: "연결 준비됨" UI (지금은 placeholder)
                ConnectionPill(
                    text = "$label 연결 준비됨",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            }
        }

        // LIVE 배지
        if (isLive) {
            LiveBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

/** 하단 상태 pill */
@Composable
private fun ConnectionPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * ✅ LIVE 뱃지(기존 그대로)
 */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val dotAlpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = modifier
            .size(width = 51.dp, height = 22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFE54F48)),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(8.dp))

            Image(
                painter = painterResource(id = R.drawable.dot),
                contentDescription = null,
                modifier = Modifier.alpha(dotAlpha.value)
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = "LIVE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Pretendard,
                maxLines = 1
            )
        }
    }
}
interface HasCctvStreamIds {
    val overviewStreamId: String?
    val siteStreamId: String?
}

private fun isPlayableStreamUrl(url: String?): Boolean {
    val normalized = url?.trim()?.lowercase() ?: return false
    return normalized.startsWith("rtsp://") ||
        normalized.startsWith("rtsps://") ||
        normalized.contains(".m3u8") ||
        normalized.contains(".mp4")
}

@Composable
fun VideoPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // UI State
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playbackError by remember(url) { mutableStateOf<String?>(null) }
    val isRtsp = remember(url) {
        url.startsWith("rtsp://", ignoreCase = true) ||
            url.startsWith("rtsps://", ignoreCase = true)
    }

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            repeatMode = if (isRtsp) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = error.localizedMessage ?: "라이브 연결 실패"
                    Log.e("InternalDetail", "Live playback failed: $url", error)
                }
            })
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        if (playbackError != null) {
            ConnectionPill(
                text = "라이브 연결 실패",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        } else if (isRtsp) {
            ConnectionPill(
                text = "RTSP 라이브",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        }

        if (!isRtsp) {
            val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
            val timeText = formatTime(currentPosition)

            LivePlaybackController(
                sliderPosition = progress,
                onSliderValueChange = { newVal ->
                    val newPos = (newVal * duration).toLong()
                    exoPlayer.seekTo(newPos)
                    currentPosition = newPos
                },
                timeText = timeText,
                isPlaying = isPlaying,
                onPlayPauseClick = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    isPlaying = !isPlaying
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
