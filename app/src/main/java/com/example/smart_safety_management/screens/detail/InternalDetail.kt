package com.example.smart_safety_management.screens.detail

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.R
import com.example.smart_safety_management.ui.theme.LocalSafeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalDetailScreen(
    item: LiveCardItem,
    onBack: () -> Unit,
    onMapClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current

    // ✅ 다크/라이트에 따라 상세 화면 톤 분기
    val bg = if (c.isDark) c.bg else Color.White
    val surface = if (c.isDark) c.surface else Color.White

    val border = c.border
    val text = c.text
    val sub = c.sub

    // ✅ 전경/현장 카드 폭
    val cardW = 350.dp

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
                        fontWeight = FontWeight.SemiBold,
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
                .padding(horizontal = 24.dp) // ✅ 좌우 24
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = "이벤트 내용",
                fontSize = 14.sp,
                color = sub,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(16.dp)) // ✅ 이벤트 내용 ↔ 위치 카드 16

            // ✅ 위치 카드 (피그마 327*52에 1.048 적용)
            Row(
                modifier = Modifier
                    .width(342.7.dp)     // 327 × 1.048
                    .height(54.5.dp)     // 52 × 1.048
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, border, RoundedCornerShape(10.dp))
                    .background(surface)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("위치", fontSize = 14.sp, color = sub)
                Spacer(Modifier.weight(1f))
                Text(item.location, fontSize = 15.sp, color = text)
            }

            Spacer(Modifier.height(24.dp))

            // ✅ 전경 (텍스트 + 카드: cardW 폭 덩어리 중앙 배치)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.width(cardW)) {
                    Text("전경", fontSize = 14.sp, color = text, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    PreviewCard(imageRes = item.overviewThumb, border = border)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ✅ 현장
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.width(cardW)) {
                    Text("현장", fontSize = 14.sp, color = text, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    PreviewCard(imageRes = item.siteThumb, border = border)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ✅ 현장캡쳐: 텍스트 + 썸네일 LazyRow를 "같은 cardW 폭" 안에 넣어 시작선 맞춤
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.width(cardW)) {
                    Text("현장캡쳐", fontSize = 14.sp, color = text, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(item.captureThumbs) { res ->
                            Box(
                                modifier = Modifier
                                    .requiredSize(120.dp) // ✅ 무조건 120×120
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
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PreviewCard(
    imageRes: Int,
    border: Color,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current
    val bg = if (c.isDark) c.surface else Color.White

    Box(
        modifier = modifier
            .width(350.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .background(bg)
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
