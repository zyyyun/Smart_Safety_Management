package com.example.smart_safety_management.camera

// 2026-05-21 — Sprint A.2 (확장): HomeActivity profile_bar 좌측 카메라 미니카드.
//
// 디자인은 watch/WatchMiniCardComposable.kt 의 EmptyWatchMiniCard 와 동일 패턴 유지
// (height 56dp, Color(0x22FFFFFF), RoundedCornerShape 12dp, Row 10dp/6dp/8dp).
// 다른 점: 상태 dot 대신 비디오카메라 아이콘 사용 (페어링 상태가 아닌 액션 의도).
//
// 탭 → CameraPairingActivity 진입 (manager 전용).

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraMiniCard(onCardTap: () -> Unit) {
    Card(
        modifier = Modifier
            .height(56.dp)
            .clickable { onCardTap() },
        colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "카메라 추가",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = "카메라 추가",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "QR 페어링",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
