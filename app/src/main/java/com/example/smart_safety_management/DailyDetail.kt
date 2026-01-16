package com.example.smart_safety_management

import android.content.res.Configuration
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
import com.example.smart_safety_management.ui.theme.*

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark")
//@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light")
fun DailyDetailScreen(onBackClick: () -> Unit = {}) {
    // 상세 화면에 표시될 초기값 (예시 데이터)
    var date by remember { mutableStateOf("2026-05-07") }
    var location by remember { mutableStateOf("C구역 2열") }
    var riskFactor by remember { mutableStateOf("정리 미흡으로 인한 안전사고 발생 우려") }
    var safetyMeasure by remember { mutableStateOf("자재 정리 및 주변 시설물 점검") }

    Smart_Safety_ManagementTheme {
        val labelColor = if (MaterialTheme.colors.isLight) TextGray60 else TextGray
        val borderColor = if (MaterialTheme.colors.isLight) Lightgray else GrayBackground
        val mainTextColor = MaterialTheme.colors.onSurface
        val fontColor = if (MaterialTheme.colors.isLight) TextGray20 else TextGray5

        Scaffold(
            backgroundColor = MaterialTheme.colors.onPrimary,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "일일안전점검",
                            fontWeight = FontWeight.Bold,
                            color = mainTextColor,
                            fontFamily = Pretendard,
                            modifier = Modifier.offset(x = (-24).dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.backicon),
                                contentDescription = "Back",
                                tint = mainTextColor
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp,
                    modifier = Modifier.height(45.dp)
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
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = date,
                    onValueChange = { date = it },
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, color = fontColor),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Column {
                            innerTextField()
                            Spacer(modifier = Modifier.height(25.dp))
                            Divider(color = borderColor, thickness = 1.dp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 위치
                Text(
                    text = "위치",
                    fontSize = 14.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = location,
                    onValueChange = { location = it },
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, color = fontColor),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Column {
                            innerTextField()
                            Spacer(modifier = Modifier.height(25.dp))
                            Divider(color = borderColor, thickness = 1.dp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 위험요인
                Text(
                    text = "위험요인",
                    fontSize = 14.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = riskFactor,
                    onValueChange = { riskFactor = it },
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 16.sp,
                        color = fontColor),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Column {
                            innerTextField()
                            Spacer(modifier = Modifier.height(25.dp))
                            Divider(color = borderColor, thickness = 1.dp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 안전대책
                Text(
                    text = "안전대책",
                    fontSize = 14.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = safetyMeasure,
                    onValueChange = { safetyMeasure = it },
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, color = fontColor),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Column {
                            innerTextField()
                            Spacer(modifier = Modifier.height(25.dp))
                            Divider(color = borderColor, thickness = 1.dp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(25.dp))

                // 현장사진
                Text(
                    text = "현장사진",
                    fontSize = 14.sp,
                    color = labelColor,
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
                        color = if (MaterialTheme.colors.isLight) Color(0xFFF4F5F6) else TextGray20,
                        border = BorderStroke(1.dp, borderColor)
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
                        color = if (MaterialTheme.colors.isLight) Color(0xFFF4F5F6) else TextGray20,
                        border = BorderStroke(1.dp, borderColor)
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
                        .fillMaxWidth()
                        .height(55.dp)
                        .align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Text(
                        text = "수정하기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Pretendard,
                        letterSpacing = (-0.3).sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 삭제하기 버튼
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .align(Alignment.CenterHorizontally)
                        .clickable { /* 삭제하기 로직 */ },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "삭제하기",
                        color = if (MaterialTheme.colors.isLight) TextGray else TextGray60,
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
