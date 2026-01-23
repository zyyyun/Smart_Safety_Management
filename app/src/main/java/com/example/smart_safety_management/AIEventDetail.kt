package com.example.smart_safety_management

import android.content.res.Configuration
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
import com.example.smart_safety_management.ui.theme.*

@Composable
fun AIEventDetailScreen(
    onBackClick: () -> Unit = {},
    onRequestAction: () -> Unit = {}, // 조치 요청 콜백 추가
    initialActionCompleted: Boolean = false,
) {
    var isActionCompleted by remember { mutableStateOf(initialActionCompleted) }
    var showFalseDetectionDialog by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0.5f) }
    var playing by remember { mutableStateOf(true) }

    Smart_Safety_ManagementTheme {

        val isLight = MaterialTheme.colors.isLight
        val CategoryColor = if (isLight) TextGray60 else TextGray
        val locationColor = if (isLight) TextGray20 else TextGray5
        val labelColor = if (isLight) TextDark else GrayBorder
        val valueColor = locationColor
        val buttonBackground = if (isLight) Lightgray else GrayBackground
        val borderColor = if (isLight) GrayBorder else TextDark
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "AI 이벤트 감지",
                            fontWeight = FontWeight.Bold,
                            color = if (isLight) Color.Black else TextGray5,
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
                                tint = if (isLight) Color.Black else TextGray5
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colors.onPrimary
            ) {
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
                            .padding(horizontal = 8.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "이벤트 내용",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(
                                    color = MaterialTheme.colors.onPrimary,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp, 
                                    color = borderColor,
                                    shape = RoundedCornerShape(12.dp)
                                )
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
                                            color = locationColor,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard
                                        )
                                        Text(
                                            text = "쓰러짐이 감지되었습니다.",
                                            color = CategoryColor,
                                            fontSize = 14.sp,
                                            fontFamily = Pretendard,
                                            modifier = Modifier.offset(y = (4).dp)
                                        )
                                    }
                                }
                                
                                Divider(
                                    color = if (isLight) TextGray5 else Color.White.copy(alpha = 0.05f),
                                    thickness = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val detailItems = listOf(
                                    "감지 이벤트" to "쓰러짐",
                                    "발생 시간" to "2025-05-07 16:05:20",
                                    "장치명" to "CAM03",
                                    "발생위치" to "D구역 1열",
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
                                            color = labelColor,
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = value,
                                            color = valueColor,
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
                                            onClick = { showFalseDetectionDialog = true },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = buttonBackground,
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                                        ) {
                                            Text(
                                                text = "오탐처리",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = Pretendard,
                                                color = locationColor
                                            )
                                        }

                                        Button(
                                            onClick = { onRequestAction() }, // 조치 요청 콜백 실행
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MainOrange,
                                                contentColor = Color.White           
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                                        ) {
                                            Text(
                                                text = "조치요청",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = Pretendard,
                                                color = MaterialTheme.colors.onPrimary
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
                                            backgroundColor = MainOrange,
                                            contentColor = Color.White,
                                            disabledBackgroundColor = MainOrange,
                                            disabledContentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                                        enabled = false
                                    ) {
                                        Text(
                                            text = "조치요청 처리되었습니다",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = Pretendard,
                                            color = MaterialTheme.colors.onPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "이벤트 캡처",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
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
                            text = "발생위치",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Image(
                            painter = painterResource(id = R.drawable.map_sample),
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
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(

                            modifier = Modifier
                                .fillMaxWidth()
                        ) {

                            Image(
                                painter = painterResource(id = R.drawable.event),
                                contentDescription = "실시간 화면",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth
                            )

                            // 3. 상단 LIVE 인디케이터 배치
                            LiveIndicator(
                                isLive = true,
                                modifier = Modifier
                                    .align(Alignment.TopEnd) // 왼쪽 상단에 배치
                                    .padding(12.dp)            // 이미지 안쪽 여백
                            )

                            // 4. 하단 재생바 및 컨트롤러 배치
                            LivePlaybackController(
                                sliderPosition = pos,
                                onSliderValueChange = { pos = it },
                                timeText = "05:20",
                                isPlaying = playing,
                                onPlayPauseClick = { playing = !playing },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter) // 하단 중앙에 배치
                                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)) // 이미지 모서리에 맞춰 하단 깎기
                            )
                        }

                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }

        if (showFalseDetectionDialog) {
            FalseDetectionDialog(
                onDismiss = { showFalseDetectionDialog = false },
                onConfirm = { 
                    showFalseDetectionDialog = false
                }
            )
        }
    }
}

@Composable
fun FalseDetectionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val isLight = MaterialTheme.colors.isLight
        val backgroundColor = if (isLight) Color.White else GrayBackground
        val buttonBackground = if (isLight) TextGray5 else TextGray20
        val textColor = if (isLight) TextGray60 else TextGray
        
        Surface(
            modifier = Modifier.size(width = 350.dp, height = 250.dp),
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor
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
                        color = MaterialTheme.colors.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.size(width = 144.dp, height = 52.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = buttonBackground,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Text(
                            text = "취소",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Pretendard,
                            color = textColor
                        )
                    }

                    Button(
                        onClick = { onConfirm() },
                        modifier = Modifier.size(width = 144.dp, height = 52.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MainOrange,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Text(
                            text = "확인",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Pretendard,
                            color = MaterialTheme.colors.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun AIEventDetailScreenPreview() {
    AIEventDetailScreen()
}
