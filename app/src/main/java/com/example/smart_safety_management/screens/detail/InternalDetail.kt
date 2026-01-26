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
import com.example.smart_safety_management.ui.theme.Pretendard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalDetailScreen(
    item: LiveCardItem,
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
    val sectionTitleColor = Color(0xFF676F76) // 피그마 기준

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
                    scrolledContainerColor = bg, // ✅ 스크롤 시 생기는 “줄” 제거 핵심
                    titleContentColor = text,
                    navigationIconContentColor = text,
                    actionIconContentColor = sub
                )
            )
        },
        modifier = modifier
    ) { inner ->
        // ✅ 전체 Column에 좌우 24를 주지 말고, 필요한 요소만 side padding 주기
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
            PreviewCard(
                imageRes = item.overviewThumb,
                border = border,
                modifier = Modifier
                    .padding(horizontal = side)
                    .fillMaxWidth()
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
            PreviewCard(
                imageRes = item.siteThumb,
                border = border,
                modifier = Modifier
                    .padding(horizontal = side)
                    .fillMaxWidth()
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

            // ✅ 오른쪽 끝이 “벽까지” 가도록: end padding = 0.dp
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = side, end = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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
