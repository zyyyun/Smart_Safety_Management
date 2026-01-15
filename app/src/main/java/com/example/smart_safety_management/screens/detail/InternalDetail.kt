package com.example.smart_safety_management.screens.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val bg = c.bg
    val border = c.border
    val text = c.text
    val sub = c.sub

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
                // ✅ 핵심 변경: 상단바 배경도 전체 배경(bg)과 동일한 "진한 검정"으로 통일
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                "이벤트 내용",
                fontSize = 14.sp,
                color = sub,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, border, RoundedCornerShape(10.dp))
                    // ✅ 다크에서 흰 박스가 튀면 c.surface로 바꾸는 게 자연스러움
                    .background(c.surface)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("위치", fontSize = 14.sp, color = sub)
                Spacer(Modifier.weight(1f))
                Text(item.location, fontSize = 15.sp, color = text)
            }

            Spacer(Modifier.height(12.dp))
            Text("전경", fontSize = 14.sp, color = text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            PreviewCard(imageRes = item.overviewThumb, border = border, bg = c.surface)

            Spacer(Modifier.height(12.dp))
            Text("현장", fontSize = 14.sp, color = text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            PreviewCard(imageRes = item.siteThumb, border = border, bg = c.surface)

            Spacer(Modifier.height(12.dp))
            Text("현장캡쳐", fontSize = 14.sp, color = text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(item.captureThumbs) { res ->
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, border, RoundedCornerShape(14.dp))
                            .background(c.surface) // ✅ 다크 대응
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

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PreviewCard(
    imageRes: Int,
    border: Color,
    bg: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .background(bg) // ✅ 다크 대응 (기존 Color.White 제거)
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}
