package com.example.smart_safety_management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

@Composable
fun DailyDetailScreen(onBackClick: () -> Unit = {}) {
    // 상세 화면에 표시될 초기값 (예시 데이터)
    var date by remember { mutableStateOf("2025-05-07") }
    var location by remember { mutableStateOf("C구역 2열") }
    var riskFactor by remember { mutableStateOf("쓰러짐 감지 및 위험 요소 발견") }
    var safetyMeasure by remember { mutableStateOf("안전 고리 고정 및 현장 정리 완료") }

    Smart_Safety_ManagementTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "일일안전점검",
                            fontWeight = FontWeight.Bold,
                            color = Color(0XFF131416),
                            fontFamily = Pretendard,
                            modifier = Modifier.offset(x = (-24).dp)
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
                    elevation = 0.dp,
                    modifier = Modifier.height(36.dp)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 작성일
                Text(
                    text = "작성일",
                    fontSize = 14.sp,
                    color = Color(0xFF58616A),
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = date,
                    onValueChange = { date = it },
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Column {
                            innerTextField()
                            Spacer(modifier = Modifier.height(25.dp))
                            Divider(color = Color(0xFFCDD1D5), thickness = 1.dp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 위치
                Text(
                    text = "위치",
                    fontSize = 14.sp,
                    color = Color(0xFF58616A),
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = location,
                    onValueChange = { location = it },
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Column {
                            innerTextField()
                            Spacer(modifier = Modifier.height(25.dp))
                            Divider(color = Color(0xFFCDD1D5), thickness = 1.dp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 위험요인
                Text(
                    text = "위험요인",
                    fontSize = 14.sp,
                    color = Color(0xFF58616A),
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = riskFactor,
                    onValueChange = { riskFactor = it },
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Column {
                            innerTextField()
                            Spacer(modifier = Modifier.height(25.dp))
                            Divider(color = Color(0xFFCDD1D5), thickness = 1.dp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 안전대책
                Text(
                    text = "안전대책",
                    fontSize = 14.sp,
                    color = Color(0xFF58616A),
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = safetyMeasure,
                    onValueChange = { safetyMeasure = it },
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Column {
                            innerTextField()
                            Spacer(modifier = Modifier.height(25.dp))
                            Divider(color = Color(0xFFCDD1D5), thickness = 1.dp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(25.dp))

                // 현장사진
                Text(
                    text = "현장사진",
                    fontSize = 14.sp,
                    color = Color(0xFF58616A),
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF4F5F6),
                        border = BorderStroke(1.dp, Color(0xFFCDD1D5))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.factory),
                            contentDescription = "현장사진 1",
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF4F5F6),
                        border = BorderStroke(1.dp, Color(0xFFCDD1D5))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.factory2),
                            contentDescription = "현장사진 2",
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // 수정하기 버튼
                Button(
                    onClick = { /* 수정하기 로직 */ },
                    modifier = Modifier
                        .width(360.dp )
                        .height(50.dp)
                        .align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF97316)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Text(
                        text = "수정하기",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Pretendard
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 삭제하기 버튼
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(20.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(Color.White)
                        .clickable { /* 삭제하기 로직 */ },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "삭제하기",
                        color = Color(0xFF8A949E),
                        fontSize = 14.sp,
                        fontFamily = Pretendard
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DailyDetailScreenPreview() {
    DailyDetailScreen()
}
