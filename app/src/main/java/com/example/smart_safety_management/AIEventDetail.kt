package com.example.smart_safety_management

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

@Composable
fun AIEventDetailScreen(onBackClick: () -> Unit = {}) {
    Smart_Safety_ManagementTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "AI 이벤트 감지",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontFamily = Pretendard,
                            modifier = Modifier.offset(x = (-20).dp),
                            fontSize = 24.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.backicon),
                                contentDescription = "Back",
                                tint = Color.Black
                            )
                        }
                    },
                    backgroundColor = Color.White,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = Color.White
            ) {
                // 스크롤 가능한 Column
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "이벤트 내용",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF58616A),
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 이벤트 내용 영역 (가로 길이를 확장하기 위해 padding 제거)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(Color(0xFFFFFFFF), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFCDD1D5), shape = RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 상단 헤더
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.warning_icon),
                                        contentDescription = "경고",
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .offset(x = (-5).dp, y = 5.dp),
                                        tint = Color.Unspecified
                                    )
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = "C구역 2열",
                                            color = Color.Black,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard
                                        )
                                        Text(
                                            text = "쓰러짐이 감지되었습니다.",
                                            color = Color(0xFF58616A),
                                            fontSize = 14.sp,
                                            fontFamily = Pretendard,
                                            modifier = Modifier.offset(y = (4).dp) // y축 위치 조절을 위해 추가
                                        )
                                    }
                                }
                                
                                Divider(
                                    color = Color(0xFFF4F5F6),
                                    thickness = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 상세 정보 필드들 (사용자 설정 색상 0xFF33363D 유지)
                                val detailItems = listOf(
                                    "감지 이벤트" to "쓰러짐",
                                    "발생 시간" to "2025-05-07 16:05:20",
                                    "장치명" to "CAM03",
                                    "발생 위치" to "D구역 1열",
                                    "정확도" to "80%"
                                )

                                detailItems.forEach { (label, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color(0xFF33363D),
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = value,
                                            color = Color(0xFF33363D),
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 하단 버튼 영역
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color(0xFFE6E8EA),
                                            contentColor = Color(0xFF33363D)    
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                                    ) {
                                        Text(
                                            text = "오탐처리",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = Pretendard
                                        )
                                    }

                                    Button(
                                        onClick = { },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color(0xFFF97316),
                                            contentColor = Color.White           
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                                    ) {
                                        Text(
                                            text = "조치요청",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = Pretendard
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "이벤트 캡처",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF58616A),
                            fontFamily = Pretendard,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Image(
                            painter = painterResource(id = R.drawable.event),
                            contentDescription = "이벤트 캡처",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "발생 위치",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF58616A),
                            fontFamily = Pretendard,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Image(
                            painter = painterResource(id = R.drawable.map),
                            contentDescription = "발생 위치 지도",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "실시간 화면",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF58616A),
                            fontFamily = Pretendard,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Image(
                            painter = painterResource(id = R.drawable.cam),
                            contentDescription = "실시간 화면",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth
                        )

                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AIEventDetailScreenPreview() {
    AIEventDetailScreen()
}
