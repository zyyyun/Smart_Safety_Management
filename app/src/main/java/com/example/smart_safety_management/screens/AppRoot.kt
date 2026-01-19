package com.example.smart_safety_management.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.R
import com.example.smart_safety_management.screens.detail.InternalDetailScreen
import com.example.smart_safety_management.screens.dialog.MapDialog
import com.example.smart_safety_management.screens.location.LocationScreen
import com.example.smart_safety_management.screens.realtime.RealTimeBottomBar
import com.example.smart_safety_management.screens.realtime.RealTimeScreen
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var selectedTab by remember { mutableIntStateOf(2) }

    var selectedItem by remember { mutableStateOf<LiveCardItem?>(null) }
    val isDetail = selectedItem != null

    var showMap by remember { mutableStateOf(false) }

    // ✅ 임시 다크모드 토글
    var isDark by remember { mutableStateOf(false) }

    Smart_Safety_ManagementTheme(darkTheme = isDark) {
        val c = LocalSafeColors.current

        Scaffold(
            containerColor = c.bg,

            topBar = {
                if (!isDetail && selectedTab != 4) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White) // ✅ 상단 전체 흰색
                    ) {
                        // ✅ 상태바 영역 흰색
                        Spacer(
                            Modifier
                                .windowInsetsTopHeight(WindowInsets.statusBars)
                                .fillMaxWidth()
                                .background(Color.White)
                        )

                        // ✅ 상단 타이틀/버튼 영역
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(Color.White)
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "실시간상황",
                                style = TextStyle(
                                    fontFamily = ClipartKorea,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 18.sp,
                                    lineHeight = 27.sp,
                                    letterSpacing = (-0.18).sp
                                ),
                                color = c.text
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(c.surface)
                                        .border(1.dp, c.border, RoundedCornerShape(10.dp))
                                        .clickable { isDark = !isDark }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isDark) "라이트모드" else "다크모드",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.text
                                    )
                                }

                                Spacer(Modifier.width(8.dp))

                                IconButton(
                                    onClick = { showMap = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.map),
                                        contentDescription = "지도",
                                        tint = c.sub,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // ❌ 여기 Divider가 "실시간상황과 공간별 사이 줄"로 보이는 원인이야
                        // Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)
                    }
                }
            }
            ,

            bottomBar = {
                if (!isDetail) {
                    RealTimeBottomBar(
                        selected = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
            }
        ) { innerPadding ->

            val contentModifier = when {
                isDetail -> Modifier.fillMaxSize()

                selectedTab == 4 -> Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                        top = 0.dp,
                        end = innerPadding.calculateEndPadding(LayoutDirection.Ltr),
                        bottom = innerPadding.calculateBottomPadding()
                    )

                else -> Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            }

            Box(modifier = contentModifier) {
                if (!isDetail) {
                    when (selectedTab) {
                        4 -> LocationScreen(
                            bottomBarHeight = innerPadding.calculateBottomPadding(),
                            isDark = isDark
                        )

                        else -> RealTimeScreen(onCardClick = { item -> selectedItem = item })
                    }
                } else {
                    InternalDetailScreen(
                        item = selectedItem!!,
                        onBack = { selectedItem = null },
                        onMapClick = { showMap = true }
                    )
                }

                if (showMap) {
                    MapDialog(
                        item = selectedItem,
                        onDismiss = { showMap = false },
                        onMoveCamera = { showMap = false }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAppRoot() {
    AppRoot()
}
