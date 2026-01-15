package com.example.safe.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safe.LiveCardItem
import com.example.safe.R
import com.example.safe.screens.detail.InternalDetailScreen
import com.example.safe.screens.dialog.MapDialog
import com.example.safe.screens.location.LocationScreen
import com.example.safe.screens.realtime.RealTimeBottomBar
import com.example.safe.screens.realtime.RealTimeScreen
import com.example.safe.ui.theme.LocalSafeColors
import com.example.safe.ui.theme.SafeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var selectedTab by remember { mutableIntStateOf(2) }

    var selectedItem by remember { mutableStateOf<LiveCardItem?>(null) }
    val isDetail = selectedItem != null

    var showMap by remember { mutableStateOf(false) }

    // ✅ 임시 다크모드 토글
    var isDark by remember { mutableStateOf(false) }

    SafeTheme(darkTheme = isDark, dynamicColor = false) {
        val c = LocalSafeColors.current

        Scaffold(
            containerColor = c.bg,
            topBar = {
                if (!isDetail && selectedTab != 4) {
                    TopAppBar(
                        title = {
                            Icon(
                                painter = painterResource(
                                    id = if (isDark) R.drawable.logo_container_b else R.drawable.logo_container
                                ),
                                contentDescription = "로고",
                                modifier = Modifier.height(24.dp),
                                tint = Color.Unspecified
                            )
                        },
                        actions = {
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

                            IconButton(onClick = { showMap = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.map),
                                    contentDescription = "지도",
                                    tint = c.sub
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = c.topBar)
                    )
                }
            },
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
                        // AppRoot 내부 when(selectedTab)
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
