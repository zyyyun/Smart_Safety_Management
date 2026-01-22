package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.smart_safety_management.ui.theme.*
import kotlinx.coroutines.launch

// ✅ 조치 데이터 모델
data class ActionWorkerData(
    val actionType: String,
    val title: String,
    val content: String
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ActionDetailWorkerScreen(
    onBackClick: () -> Unit = {},
    initialExpanded: Boolean = false // 바텀 시트 초기 확장 여부
) {
    // ✅ 더미 데이터 정의
    val dummyData = ActionWorkerData(
        actionType = "즉시조치",
        title = "근로자 쓰러짐 발생 조치 필요",
        content = "D구역 1열에서 근로자 1명 쓰러짐"
    )

    // 라이브 컴포넌트와 사용하기위한 상태 변수
    var pos by remember { mutableStateOf(0.5f) }
    var playing by remember { mutableStateOf(true) }

    // 더미데이터
    var actionType by remember { mutableStateOf(dummyData.actionType) }
    var title by remember { mutableStateOf(dummyData.title) }
    var content by remember { mutableStateOf(dummyData.content) }
    var attachedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // 작성 완료 다이얼로그 상태
    var showActionCompletedDialog by remember { mutableStateOf(false) }
    
    // 스크롤 상태 관리
    val scrollState = rememberScrollState()
    val sheetScrollState = rememberScrollState() // 바텀 시트 전용 스크롤 상태
    
    // 드롭다운 확장 상태 관리
    var dropdownExpanded by remember { mutableStateOf(false) }
    val options = listOf("조치공유", "조치필요", "즉시조치")

    // 바텀 시트 상태 관리
    val sheetState = rememberModalBottomSheetState(
        initialValue = if (initialExpanded) ModalBottomSheetValue.Expanded else ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val coroutineScope = rememberCoroutineScope()

    Smart_Safety_ManagementTheme {
        val theme = MaterialTheme.colors.onPrimary
        val isLight = MaterialTheme.colors.isLight
        val CategoryColor = if (isLight) TextGray60 else TextGray
        val textColor = if (isLight) TextGray20 else TextGray5
        val labelColor = if (isLight) TextDark else GrayBorder
        val borderColor = if (isLight) GrayBorder else TextDark
        val inputTextColor = if (isLight) TextGray20 else TextGray5
        val photoColor = if (isLight) TextLight else TextGray30
        val btnBackColor = if (isLight) Color.White else TextGray20
        val detailBtnColor = if (isLight) Lightgray else GrayBackground

        ModalBottomSheetLayout(
            sheetState = sheetState,
            sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            sheetBackgroundColor = MaterialTheme.colors.onPrimary,
            sheetContent = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(712.dp)
                        .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.Start, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(sheetScrollState) 
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "상세 보기",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = Pretendard,
                                color = textColor
                            )
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    sheetState.hide()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = textColor
                                )
                            }
                        }

                        LabelText("이벤트 캡처")
                        Image(
                            painter = painterResource(id = R.drawable.workeraction),
                            contentDescription = "이벤트 캡처",
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            contentScale = ContentScale.FillWidth
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LabelText("발생 위치")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.workermap),
                                contentDescription = "발생 위치",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                            Image(
                                painter = painterResource(id = if (isLight) R.drawable.worker_orange else R.drawable.worker_orange_dark),
                                contentDescription = "마커",
                                modifier = Modifier.scale(1.67f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LabelText("실시간 화면")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            // 2. 배경 이미지 (기억하라고 하신 코드)
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
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "조치 결과 등록",
                                fontWeight = FontWeight.Bold,
                                color = textColor,
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
                                    tint = textColor
                                )
                            }
                        },
                        backgroundColor = theme,
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
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScrollbar(scrollState)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "이벤트 내용",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(15.dp))

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(12.dp))
                                .border(1.dp, borderColor, shape = RoundedCornerShape(12.dp))
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
                                            color = textColor,
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
                                    "발생 위치" to "D구역 1열"
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
                                            color = textColor,
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            sheetState.show()
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp,bottom = 16.dp,start = 16.dp,end = 16.dp)
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        ,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = detailBtnColor
                                    )
                                ) {
                                    Text(
                                        text = "상세보기",
                                        fontFamily = Pretendard,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = inputTextColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "조치 유형",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            OutlinedTextField(
                                value = if (actionType.isEmpty()) "조치 유형 선택" else actionType,
                                onValueChange = { },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                textStyle = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = Pretendard,
                                    color = inputTextColor
                                ),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    Box(
                                        modifier = Modifier.fillMaxHeight(),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(end = 12.dp)
                                        ) {
                                            // ✅ 선택된 아이콘 제거 완료
                                            Icon(
                                                painter = painterResource(id = R.drawable.dropbox),
                                                contentDescription = null,
                                                tint = Color.Unspecified
                                            )
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    unfocusedBorderColor = borderColor,
                                    focusedBorderColor = MainOrange,
                                    textColor = inputTextColor,
                                    backgroundColor = btnBackColor
                                )
                            )
                            
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier
                                    .width(this@BoxWithConstraints.maxWidth)
                                    .background(btnBackColor)
                            ) {
                                options.forEach { option ->
                                    DropdownMenuItem(
                                        onClick = {
                                            actionType = option
                                            dropdownExpanded = false
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = option,
                                                fontFamily = Pretendard,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = inputTextColor
                                            )
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { dropdownExpanded = true }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "제목",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .height(60.dp),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = TextStyle(fontSize = 16.sp, fontFamily = Pretendard, color = inputTextColor),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                unfocusedBorderColor = borderColor,
                                focusedBorderColor = MainOrange,
                                backgroundColor = btnBackColor
                            )
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "내용",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = TextStyle(fontSize = 16.sp, fontFamily = Pretendard, color = inputTextColor),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                unfocusedBorderColor = borderColor,
                                focusedBorderColor = MainOrange,
                                backgroundColor = btnBackColor
                            )
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "사진",
                            fontWeight = FontWeight.Medium,
                            color = CategoryColor,
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            modifier = Modifier.offset(x = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clickable {
                                        attachedPhotos = attachedPhotos + "photoUri_${System.currentTimeMillis()}"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                                    drawRoundRect(
                                        color = btnBackColor,
                                        cornerRadius = CornerRadius(8.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = borderColor,
                                        style = Stroke(width = 1.dp.toPx(), pathEffect = pathEffect),
                                        cornerRadius = CornerRadius(8.dp.toPx())
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.camera_icon),
                                        contentDescription = "사진첨부",
                                        tint = photoColor
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "사진첨부",
                                        color = photoColor,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            attachedPhotos.reversed().forEach { photoUri ->
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Photo, contentDescription = "Attached Photo: $photoUri", tint = if (isLight) Color.Gray else TextGray)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(48.dp))

                        Button(
                            onClick = { 
                                showActionCompletedDialog = true 
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                                .padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MainOrange,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Text(
                                text = "작성완료",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                fontFamily = Pretendard,
                                color = MaterialTheme.colors.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }

        if (showActionCompletedDialog) {
            ActionCompletedDialog(
                onDismiss = { showActionCompletedDialog = false },
                onConfirm = { 
                    showActionCompletedDialog = false
                    onBackClick()
                }
            )
        }
    }
}

@Composable
fun ActionCompletedDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
                        painter = painterResource(id = R.drawable.check),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        textAlign = TextAlign.Center,
                        text = "조치완료",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                        color = MaterialTheme.colors.onSurface
                    )
                    Text(
                        textAlign = TextAlign.Center,
                        text = "조치요청 완료처리 하시겠습니까?",
                        fontFamily = Pretendard,
                        color = textColor,
                        fontSize = 16.sp
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

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode", heightDp = 1000)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode", heightDp = 1000)
@Composable
fun ActionDetailWorkerScreenPreview() {
    ActionDetailWorkerScreen()
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode - Dialog", heightDp = 1000)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode - Dialog", heightDp = 1000)
@Composable
fun ActionDetailWorkerDialogPreview() {
    ActionDetailWorkerScreen(initialExpanded = true)
}
