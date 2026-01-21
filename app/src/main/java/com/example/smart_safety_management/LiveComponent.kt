package com.example.smart_safety_management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*

// ✅ 라이브 상태 인디케이터 (상단용)
@Composable
fun LiveIndicator(
    isLive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = if (isLive) StatusRed.copy(alpha = 0.8f) else Color.Gray,
                shape = RoundedCornerShape(50.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        // 빨간 점
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Color.White, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "LIVE",
            fontFamily = Pretendard,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ✅ 재생바 및 컨트롤 버튼 (하단용)
@Composable
fun LivePlaybackController(
    sliderPosition: Float,
    onSliderValueChange: (Float) -> Unit,
    timeText: String,
    isPlaying: Boolean = true,
    onPlayPauseClick: () -> Unit = {},
    onVolumeClick: () -> Unit = {},
    onFullscreenClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 왼쪽: 재생/정지 버튼
        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.live_pause else R.drawable.live_start),
                contentDescription = "Play/Pause",
                tint = Color.White
            )
        }

        // 2. 중앙: 재생바 (Slider) - 너비를 유연하게 조절
        Slider(
            value = sliderPosition,
            onValueChange = onSliderValueChange,
            colors = SliderDefaults.colors(
                activeTrackColor = MainOrange,
                inactiveTrackColor = Color.White,
                thumbColor = Color.Transparent,
                disabledThumbColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f)
        )

        // 3. 오른쪽: 재생 시간
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = Pretendard
        )

        // 4. 오른쪽: 볼륨 버튼
        IconButton(
            onClick = onVolumeClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.speaker_high),
                contentDescription = "Volume",
                tint = Color.White
            )
        }

        // 5. 오른쪽: 전체화면 버튼
        IconButton(
            onClick = onFullscreenClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.fullscreen),
                contentDescription = "Fullscreen",
                tint = Color.White
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun LiveComponentPreview() {
    var pos by remember { mutableStateOf(0.5f) }
    var playing by remember { mutableStateOf(true) }
    
    Smart_Safety_ManagementTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.8f))
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween // 위아래 끝으로 배치
        ) {
            // 상단 배치 예시
            LiveIndicator(isLive = true)
            
            // 하단 배치 예시
            LivePlaybackController(
                sliderPosition = pos,
                onSliderValueChange = { pos = it },
                timeText = "05:20",
                isPlaying = playing,
                onPlayPauseClick = { playing = !playing }
            )
        }
    }
}
