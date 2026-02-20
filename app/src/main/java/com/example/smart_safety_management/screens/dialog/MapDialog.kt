package com.example.smart_safety_management.screens.dialog

import android.content.Context
import android.location.Geocoder
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.example.smart_safety_management.CCTVItemResponse
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.R
import com.example.smart_safety_management.RetrofitClient
import com.example.smart_safety_management.UserSession
import com.example.smart_safety_management.WorkplaceLocationResponse
import com.example.smart_safety_management.screens.realtime.TagPillCompact
import com.example.smart_safety_management.screens.realtime.normalizeCamId
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import com.kakao.vectormap.LatLng
import com.example.smart_safety_management.KakaoMapPin
import com.example.smart_safety_management.KakaoMapView

private suspend fun geocode(context: Context, address: String): Pair<Double, Double>? {
    return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.KOREA)
            val addresses = geocoder.getFromLocationName(address, 1)
            if (!addresses.isNullOrEmpty()) {
                addresses[0].latitude to addresses[0].longitude
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapDialog(
    cams: List<LiveCardItem>,
    cctvList: List<CCTVItemResponse>,
    item: LiveCardItem?,
    onDismiss: () -> Unit,
    onMoveCamera: (camId: String) -> Unit
) {
    val c = LocalSafeColors.current
    val isDark = c.isDark
    val context = LocalContext.current

    // 전달받은 item의 ID를 그대로 사용 (없으면 첫 번째 카메라)
    val initialCamId = item?.camId ?: cams.firstOrNull()?.camId ?: "CAM 00"

    // 마커 ID(CAM 01)와 서버 데이터 ID(CAM 1) 매칭을 위해 normalize 적용
    var selectedCamId by remember(cams) { mutableStateOf(normalizeCamId(initialCamId)) }
    val selected = cams.firstOrNull { normalizeCamId(it.camId) == selectedCamId } ?: cams.firstOrNull()

    // 지도 상태
    var cameraPoints by remember { mutableStateOf<Map<String, Pair<Double, Double>>>(emptyMap()) }
    var workplacePoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val infoBg = if (isDark) Color(0xFF1E2124) else Color.White
    val infoLabel = c.sub

    val infoValue = if (isDark) Color.White else c.sub
    val actionColor = if (isDark) Color.Black else Color.White
    val dialogBg = if (isDark) Color(0xFF1E2124) else Color.White

    // 사이즈
    val dialogW = 348.dp
    val dialogH = 660.dp
    val mapW = 316.dp
    val mapH = 340.dp

    LaunchedEffect(Unit) {
        val userId = UserSession.userId ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getWorkplaceLocation(userId).execute()
                if (response.isSuccessful) {
                    val body = response.body()
                    val addr = body?.address ?: body?.roadAddress
                    if (!addr.isNullOrBlank()) {
                        val point = geocode(context, addr)
                        if (point != null) {
                            workplacePoint = point
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(cctvList) {
        val points = mutableMapOf<String, Pair<Double, Double>>()
        cctvList.forEach { cctv ->
            val addr = cctv.installationAddress
            if (!addr.isNullOrBlank()) {
                val point = geocode(context, addr)
                if (point != null) {
                    points[normalizeCamId(cctv.name)] = point
                }
            }
        }
        cameraPoints = points
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
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
                            val pins = cameraPoints.map { (camId, latLon) ->
                                val (lat, lon) = latLon
                                val isSelected = camId == selectedCamId

                                val resId = when {
                                    isDark && isSelected -> R.drawable.cctv_b_dark
                                    isDark && !isSelected -> R.drawable.cctv_dark
                                    !isDark && isSelected -> R.drawable.cctv_b
                                    else -> R.drawable.cctv
                                }

                                KakaoMapPin(
                                    id = camId,
                                    lat = lat,
                                    lon = lon,
                                    iconRes = resId
                                )
                            }

                            // 중심점: workplace 있으면 workplace, 없으면 선택 카메라, 없으면 첫 카메라
                            val centerLatLng: LatLng? = run {
                                workplacePoint?.let { (lat, lon) -> LatLng.from(lat, lon) }
                                    ?: cameraPoints[selectedCamId]?.let { (lat, lon) -> LatLng.from(lat, lon) }
                                    ?: cameraPoints.values.firstOrNull()?.let { (lat, lon) -> LatLng.from(lat, lon) }
                            }

                            if (centerLatLng != null) {
                                KakaoMapView(
                                    lat = centerLatLng.latitude,
                                    lon = centerLatLng.longitude,
                                    modifier = Modifier.fillMaxSize(),
                                    targetLatLng = centerLatLng,
                                    dimUnselectedPins = false,
                                    pins = pins,
                                    selectedId = selectedCamId,
                                    onPinClick = { clickedId -> selectedCamId = clickedId },
                                    centerOnSelectedPin = (workplacePoint == null)
                                )

                            }

                        }

                        /* ---------- 정보 카드 (선택된 카메라 정보 표시) ---------- */
                        if (selected != null) {
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
                                        right = selected.camId,
                                        leftColor = infoLabel,
                                        rightColor = infoValue
                                    )
                                    InfoRow(
                                        left = "발생위치",
                                        right = "${selected.location} ${selected.place}",
                                        leftColor = infoLabel,
                                        rightColor = infoValue
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "탐지모델",
                                            color = infoLabel,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = Pretendard
                                        )
                                        if (selected.tags.isNotEmpty()) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                selected.tags.forEach { tag ->
                                                    TagPillCompact(text = tag)
                                                }
                                            }
                                        } else {
                                            Text(text = "-", color = infoValue, fontSize = 16.sp)
                                        }
                                    }

                                    Button(
                                        onClick = { onMoveCamera(selected.camId) },
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Pretendard,
            letterSpacing = (-0.2).sp
        )

        Text(
            text = right,
            color = rightColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Pretendard,
            letterSpacing = (-0.1).sp
        )
    }
}
