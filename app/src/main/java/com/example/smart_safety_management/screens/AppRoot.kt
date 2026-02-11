package com.example.smart_safety_management.screens

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.CCTVItemResponse
import com.example.smart_safety_management.GetCCTVListResponse
import com.example.smart_safety_management.LiveCardItem
import com.example.smart_safety_management.R
import com.example.smart_safety_management.RetrofitClient
import com.example.smart_safety_management.UserSession
import com.example.smart_safety_management.screens.detail.InternalDetailScreen
import com.example.smart_safety_management.screens.dialog.MapDialog
import com.example.smart_safety_management.screens.location.LocationScreen
import com.example.smart_safety_management.screens.realtime.normalizeCamId
import com.example.smart_safety_management.screens.realtime.RealTimeBottomBar
import com.example.smart_safety_management.screens.realtime.RealTimeScreen
import com.example.smart_safety_management.ui.theme.ClipartKorea
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var selectedTab by remember { mutableIntStateOf(2) }

    var selectedItem by remember { mutableStateOf<LiveCardItem?>(null) }
    val isDetail = selectedItem != null

    var showMap by remember { mutableStateOf(false) }

    // ✅ 다크모드 토글
    var isDark by remember { mutableStateOf(false) }

    // ✅ 서버 데이터 상태 관리
    var allCards by remember { mutableStateOf<List<LiveCardItem>>(emptyList()) }
    var cctvDataList by remember { mutableStateOf<List<CCTVItemResponse>>(emptyList()) }
    val context = LocalContext.current

    // ✅ 서버에서 카메라 리스트 가져오기
    LaunchedEffect(Unit) {
        val userId = UserSession.userId
        if (userId != null) {
            RetrofitClient.instance.getCCTVList(null, null, userId).enqueue(object : Callback<GetCCTVListResponse> {
                override fun onResponse(call: Call<GetCCTVListResponse>, response: Response<GetCCTVListResponse>) {
                    if (response.isSuccessful) {
                        val list = response.body()?.cctvList ?: emptyList()
                        cctvDataList = list // ✅ 원본 데이터 저장 (주소 정보 포함)
                        allCards = list.map { dto ->
                            val resId = R.drawable.thumb_site
                            val baseUrl = "http://10.0.2.2:3000" // 서버 주소 (필요시 수정)
                            val fullUrl = if (!dto.imageUrl.isNullOrEmpty()) baseUrl + dto.imageUrl else null

                            LiveCardItem(
                                camId = dto.name,
                                place = dto.environmentType ?: "내부",
                                tags = dto.events,
                                thumbRes = resId,
                                imageUrl = fullUrl,
                                location = dto.location,
                                overviewThumb = resId,
                                siteThumb = resId,
                                captureThumbs = listOf(resId, resId, resId),
                                isLive = true
                            )
                        }
                    }
                }
                override fun onFailure(call: Call<GetCCTVListResponse>, t: Throwable) {}
            })
        }
    }

    Smart_Safety_ManagementTheme(darkTheme = isDark) {
        val c = LocalSafeColors.current

        // ✅ 핵심: 라이트 모드일 때 Scaffold 배경을 "완전 흰색"으로 고정
        // (이걸 안 하면 c.bg(#F4F6F9 같은 회백색)이 위에 비쳐서 헤더가 탁해 보임)
        val scaffoldBg = if (c.isDark) c.bg else Color.White

        Scaffold(
            containerColor = scaffoldBg,

            topBar = {
                if (!isDetail && selectedTab != 4) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White) // ✅ 상단 전체 순백 고정
                    ) {
                        // ✅ 상태바 영역도 흰색
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
                    }
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
                        4 -> LocationScreen(
                            bottomBarHeight = innerPadding.calculateBottomPadding(),
                            isDark = isDark
                        )

                        else -> RealTimeScreen(
                            cards = allCards, // ✅ 데이터 전달
                            onCardClick = { item -> selectedItem = item },
                            onMapClick = { showMap = true }
                        )
                    }
                } else {
                    // ✅ 선택된 아이템에 해당하는 상세 데이터 찾기
                    val selectedCctvData = cctvDataList.find { it.name == selectedItem?.camId }
                    InternalDetailScreen(
                        item = selectedItem!!,
                        cameraId = selectedCctvData?.id ?: 0,
                        overviewUrl = selectedCctvData?.liveUrl,
                        siteUrl = selectedCctvData?.liveUrlDetail,
                        onBack = { selectedItem = null },
                        onMapClick = { showMap = true }
                    )
                }

                if (showMap) {
                    MapDialog(
                        cams = allCards, // ✅ 데이터 전달
                        cctvList = cctvDataList, // ✅ 주소 정보가 포함된 원본 리스트 전달
                        item = selectedItem,
                        onDismiss = { showMap = false },
                        onMoveCamera = { camId ->
                            // ✅ 카메라 이동 로직 구현
                            val targetId = normalizeCamId(camId)
                            val target = allCards.firstOrNull { normalizeCamId(it.camId) == targetId }
                            if (target != null) {
                                selectedItem = target
                            }
                            showMap = false
                        }
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
