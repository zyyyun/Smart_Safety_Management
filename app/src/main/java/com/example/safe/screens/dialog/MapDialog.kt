package com.example.safe.screens.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.safe.LiveCardItem
import com.example.safe.R
import com.example.safe.screens.realtime.TagPillCompact
import com.example.safe.ui.theme.LocalSafeColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapDialog(
    item: LiveCardItem?,
    onDismiss: () -> Unit,
    onMoveCamera: () -> Unit
) {
    val cams = remember {
        listOf(
            LiveCardItem(
                "CAM 0", "도로", listOf("충돌", "안전모 미착용"),
                R.drawable.thumb_site, "A구역 외부 도로",
                R.drawable.thumb_road, R.drawable.thumb_site,
                listOf(R.drawable.thumb_worker, R.drawable.thumb_workers, R.drawable.thumb_worker),
                true
            ),
            LiveCardItem(
                "CAM 1", "내부", listOf("화재", "통로", "운반", "협착사고"),
                R.drawable.thumb_workers, "A구역 1열 내부",
                R.drawable.frame_a, R.drawable.frame_b,
                listOf(R.drawable.rectangle_a, R.drawable.rectangle_b, R.drawable.rectangle_c),
                true
            ),
            LiveCardItem(
                "CAM 2", "도로", listOf("안전모 미착용", "통로", "운반"),
                R.drawable.thumb_road, "B구역 외부 도로",
                R.drawable.thumb_road, R.drawable.thumb_site,
                listOf(R.drawable.thumb_site, R.drawable.thumb_worker, R.drawable.thumb_workers),
                true
            ),
            LiveCardItem(
                "CAM 3", "도로", listOf("충돌", "안전모 미착용"),
                R.drawable.thumb_worker, "C구역 외부 도로",
                R.drawable.thumb_road, R.drawable.thumb_site,
                listOf(R.drawable.thumb_worker, R.drawable.thumb_workers, R.drawable.thumb_site),
                true
            ),
        )
    }

    val c = LocalSafeColors.current

    val initialCamId = remember(item?.camId) {
        when (item?.camId) {
            "CAM 00" -> "CAM 0"
            "CAM 01" -> "CAM 1"
            "CAM 02" -> "CAM 2"
            "CAM 03" -> "CAM 3"
            else -> item?.camId ?: "CAM 1"
        }
    }

    var selectedCamId by remember { mutableStateOf(initialCamId) }
    val selected = cams.firstOrNull { it.camId == selectedCamId } ?: cams[0]

    val infoBg = c.surface
    val infoText = c.sub

    // 사이즈(확대 버전)
    val dialogW = 348.dp
    val dialogH = 660.dp
    val mapW = 316.dp
    val mapH = 340.dp

    // 마커 사이즈
    val baseW = 35.dp
    val baseH = 46.dp
    val selW = 70.dp
    val selH = 92.dp
    val dx = (selW - baseW) / 2
    val dy = (selH - baseH)

    data class Marker(val camId: String, val x: Dp, val y: Dp)
    val markers = remember {
        listOf(
            Marker("CAM 0", 42.68.dp, 202.04.dp),
            Marker("CAM 1", 129.73.dp, 138.15.dp),
            Marker("CAM 2", 63.68.dp, 73.04.dp),
            Marker("CAM 3", 230.68.dp, 8.dp),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        // ✅ 바깥 영역 살짝만 어둡게
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0.22f)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 바깥 클릭 닫기
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onDismiss() }
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = c.surface),
                border = BorderStroke(1.dp, c.border),
                modifier = Modifier.size(width = dialogW, height = dialogH)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        /* ---------- 지도 ---------- */
                        Box(
                            modifier = Modifier
                                .size(width = mapW, height = mapH)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFEAECEF))
                        ) {
                            Image(
                                painter = painterResource(R.drawable.mapmaker),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            markers.forEach { m ->
                                val isSelected = m.camId == selectedCamId
                                val w = if (isSelected) selW else baseW
                                val h = if (isSelected) selH else baseH

                                Icon(
                                    painter = painterResource(
                                        id = if (isSelected) R.drawable.cctv_b else R.drawable.cctv
                                    ),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier
                                        .offset(
                                            x = if (isSelected) m.x - dx else m.x,
                                            y = if (isSelected) m.y - dy else m.y
                                        )
                                        .size(width = w, height = h)
                                        .clickable { selectedCamId = m.camId }
                                )
                            }
                        }

                        /* ---------- 정보 카드 ---------- */
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = infoBg), // ✅ 더 진하게
                            border = BorderStroke(1.dp, c.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                InfoRow(
                                    left = "카메라 번호",
                                    right = String.format(
                                        "%02d",
                                        selected.camId.removePrefix("CAM ").trim().toInt()
                                    ),
                                    leftColor = infoText,   // ✅ 더 어두운 회색
                                    rightColor = infoText   // ✅ 더 어두운 회색
                                )

                                InfoRow(
                                    left = "발생위치",
                                    right = selected.location,
                                    leftColor = infoText,
                                    rightColor = infoText
                                )

                                Text(
                                    text = "탐지모델",
                                    color = infoText,        // ✅ 더 어두운 회색
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // ✅ TagPillCompact도 같은 톤으로 보이게 isRisk=true 사용
                                    selected.tags.forEach { tag ->
                                        TagPillCompact(text = tag, isRisk = true)
                                    }
                                }

                                Button(
                                    onClick = onMoveCamera,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF7A00)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "카메라 바로가기",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.camera),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    /* ---------- 닫기 버튼 ---------- */
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp)
                            .size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "닫기",
                            tint = c.sub,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    left: String,
    right: String,
    leftColor: Color,
    rightColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(left, color = leftColor, fontSize = 14.sp)
        Text(right, color = rightColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
