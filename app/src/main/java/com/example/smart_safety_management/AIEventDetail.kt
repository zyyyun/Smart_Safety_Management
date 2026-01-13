package com.example.smart_safety_management

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.smart_safety_management.ui.theme.Pretendard
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

@Composable
fun AIEventDetailScreen(
    onBackClick: () -> Unit = {},
    initialActionCompleted: Boolean = false
) {
    var isActionCompleted by remember { mutableStateOf(initialActionCompleted) }
    // 오탐처리 다이얼로그 상태 관리
    var showFalseDetectionDialog by remember { mutableStateOf(false) }

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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp) // 요청하신 8dp 수평 패딩 추가
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "이벤트 내용",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF58616A),
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // 이벤트 내용 영역
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(Color(0xFFFFFFFF), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFCDD1D5), shape = RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
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
                                            modifier = Modifier.offset(y = (4).dp)
                                        )
                                    }
                                }
                                
                                Divider(
                                    color = Color(0xFFF4F5F6),
                                    thickness = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

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

                                if (!isActionCompleted) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showFalseDetectionDialog = true }, // 오탐처리 버튼 클릭 시 다이얼로그 표시
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
                                            onClick = { isActionCompleted = true },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFFFF7A00),
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
                                } else {
                                    Button(
                                        onClick = { },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color(0xFFF97316),
                                            contentColor = Color(0xFFFFFFFF),
                                            disabledBackgroundColor = Color(0xFFF97316),
                                            disabledContentColor = Color(0xFFFFFFFF)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                                        enabled = false
                                    ) {
                                        Text(
                                            text = "조치요청 처리되었습니다",
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

                        Spacer(modifier = Modifier.height(18.dp))

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

                        Spacer(modifier = Modifier.height(18.dp))

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

                        Spacer(modifier = Modifier.height(18.dp))

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

        // 다이얼로그 표시 로직
        if (showFalseDetectionDialog) {
            FalseDetectionDialog(
                onDismiss = { showFalseDetectionDialog = false },
                onConfirm = { 
                    showFalseDetectionDialog = false
                    // 오탐처리 확정 시 필요한 추가 로직 작성 가능
                }
            )
        }
    }
}

@Composable
fun FalseDetectionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        FalseDetectionDialogContent(onDismiss, onConfirm)
    }
}

@Composable
fun FalseDetectionDialogContent(onCancel: () -> Unit = {}, onConfirm: () -> Unit = {}) {
    Surface(
        modifier = Modifier.size(width = 350.dp, height = 250.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.false_alarm),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    textAlign = TextAlign.Center,
                    text = "해당 감지 이벤트를 \n오탐처리 하시겠습니까?",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 30.sp,
                    color = Color(0xFF131416)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.size(width = 144.dp, height = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFE6E8EA),
                        contentColor = Color(0xFF33363D)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Text(
                        text = "취소",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Pretendard
                    )
                }

                Button(
                    onClick = { onConfirm() },
                    modifier = Modifier.size(width = 144.dp, height = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFFF7A00),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Text(
                        text = "확인",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Pretendard
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "조치 전")
@Composable
fun AIEventDetailScreenPreview() {
    AIEventDetailScreen(initialActionCompleted = false)
}

@Preview( showBackground = true, name = "조치 후")
@Composable
fun AIEventDetailScreenPreview2() {
    AIEventDetailScreen(initialActionCompleted = true)
}
@Preview(showBackground = true, name = "다이얼로그 프리뷰")
@Composable
fun FalseDetectionDialogPreview() {
    Smart_Safety_ManagementTheme {
        FalseDetectionDialogContent()
    }
}
