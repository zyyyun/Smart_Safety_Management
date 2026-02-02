package com.example.smart_safety_management.screens.dialog

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
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.R
import com.example.smart_safety_management.screens.realtime.TagPillCompact
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapDialog(
    item: LiveCardItem?,
    onDismiss: () -> Unit,
    onMoveCamera: (camId: String) -> Unit   // ✅ 변경
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
    val isDark = c.isDark

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

    val infoBg = if (isDark) Color(0xFF1E2124) else Color.White
    val infoLabel = c.sub

    // ✅ 요청 반영: 다크일 때 값(오른쪽) 흰색, 버튼 텍스트/아이콘 검정
    val infoValue = if (isDark) Color.White else c.sub
    val actionColor = if (isDark) Color.Black else Color.White
    val dialogBg = if (isDark) Color(0xFF1E2124) else Color.White


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
                colors = CardDefaults.cardColors(containerColor = dialogBg),
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
                                val isSelectedMarker = m.camId == selectedCamId
                                val w = if (isSelectedMarker) selW else baseW
                                val h = if (isSelectedMarker) selH else baseH

                                // ✅ Icon에서 쓸 리소스를 먼저 결정 (문법 오류 해결)
                                val markerRes = when {
                                    isDark && isSelectedMarker -> R.drawable.cctv_b_dark
                                    isDark && !isSelectedMarker -> R.drawable.cctv_dark
                                    !isDark && isSelectedMarker -> R.drawable.cctv_b      // 라이트 리소스가 없으면 dark로 임시 대체 가능
                                    else -> R.drawable.cctv                               // 라이트 리소스가 없으면 dark로 임시 대체 가능
                                }

                                Icon(
                                    painter = painterResource(id = markerRes),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier
                                        .offset(
                                            x = if (isSelectedMarker) m.x - dx else m.x,
                                            y = if (isSelectedMarker) m.y - dy else m.y
                                        )
                                        .size(width = w, height = h)
                                        .clickable { selectedCamId = m.camId }
                                )
                            }

                        }

                        /* ---------- 정보 카드 ---------- */
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = infoBg),
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
                                    leftColor = infoLabel,
                                    rightColor = infoValue // ✅ 다크: 흰색
                                )

                                InfoRow(
                                    left = "발생위치",
                                    right = selected.location,
                                    leftColor = infoLabel,
                                    rightColor = infoValue // ✅ 다크: 흰색
                                )

                                Text(
                                    text = "탐지모델",
                                    color = infoLabel,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    selected.tags.forEach { tag ->
                                        TagPillCompact(
                                            text = tag,
                                            isRisk = isDark
                                        )
                                    }

                                }

                                Button(
                                    onClick = { onMoveCamera(selected.camId) }, // ✅ 선택된 CAM 전달
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
                                            color = actionColor,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = Pretendard,
                                            fontSize = 18.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.camera),
                                            contentDescription = null,
                                            tint = actionColor,
                                            modifier = Modifier.size(20.dp)
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
        Text(
            text = left,
            color = leftColor,
            fontSize = 16.sp,                 // ✅ 16
            fontWeight = FontWeight.Medium,
            fontFamily = Pretendard,          // ✅ Pretendard Medium
            letterSpacing = (-0.2).sp
        )

        Text(
            text = right,
            color = rightColor,
            fontSize = 16.sp,                 // ✅ 16
            fontWeight = FontWeight.Medium,
            fontFamily = Pretendard,          // ✅ Pretendard Medium
            letterSpacing = (-0.1).sp
        )
    }
}
