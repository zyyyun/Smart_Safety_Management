package com.example.smart_safety_management

import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.toSize
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.smart_safety_management.ui.theme.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ActionDetailScreen(
    eventId: Int,
    onBackClick: () -> Unit = {},
    initialExpanded: Boolean = false
) {
    var actionType by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var attachedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }

    var eventDetail by remember { mutableStateOf<DetectionEventDetailResponse?>(null) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.size > 5) {
            ToastUtil.showShort(context, "사진은 최대 5장까지 첨부 가능합니다.")
            attachedPhotos = uris.take(5).map { it.toString() }
        } else {
            attachedPhotos = uris.map { it.toString() }
        }
    }

    LaunchedEffect(eventId) {
        RetrofitClient.instance.getDetectionEventDetail(eventId).enqueue(object : Callback<DetectionEventDetailResponse> {
            override fun onResponse(call: Call<DetectionEventDetailResponse>, response: Response<DetectionEventDetailResponse>) {
                if (response.isSuccessful) {
                    eventDetail = response.body()
                    Log.d("ActionDetail", "Event Detail Loaded: $eventDetail")
                } else {
                    Log.e("ActionDetail", "Failed to load event detail: ${response.code()}")
                }
            }
            override fun onFailure(call: Call<DetectionEventDetailResponse>, t: Throwable) {
                Log.e("ActionDetail", "Network error: ${t.message}")
            }
        })
    }
    
    // 스크롤 상태 관리
    val scrollState = rememberScrollState()
    
    // 드롭다운 확장 상태 관리
    var expanded by remember { mutableStateOf(initialExpanded) }
    val options = listOf("조치공유", "조치필요", "즉시조치")

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
        val dropboxBorder = if (isLight) TextGray5 else TextGray20
        val selectAlpha = if (isLight) 0.12f else 0.36f

        // 1. 이벤트 아이콘 설정 (위험, 경고, 주의에 따라 변경)
        val eventIconRes = when (eventDetail?.riskLevel?.lowercase()) {
            "high", "위험", "danger" -> R.drawable.danger_icon
            "medium", "경고", "warning" -> R.drawable.warning_icon
            "low", "주의", "caution" -> R.drawable.caution_icon
            else -> R.drawable.warning_icon
        }

        // 2. 아이콘 종류에 따른 "감지 이벤트" 밸류 텍스트 색상 설정
        // 여기에 원하는 색상(예: Red, Orange 등)을 직접 지정하시면 됩니다.
        val eventValueColor = when (eventIconRes) {
            R.drawable.danger_icon -> Color(0xFFEF4444)  // 위험 아이콘일 때 색상
            R.drawable.warning_icon -> Color(0xFFF97316) // 경고 아이콘일 때 색상
            R.drawable.caution_icon -> Color(0xFFFFB114) // 주의 아이콘일 때 색상
            else -> textColor
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "조치요청서 작성",
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
                // 스크롤바가 포함된 Column
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScrollbar(scrollState) // 커스텀 스크롤바 적용
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
                                    painter = painterResource(id = eventIconRes), // 변수 적용
                                    contentDescription = "이벤트 아이콘",
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .offset(x = (-5).dp, y = 5.dp),
                                    tint = Color.Unspecified
                                )
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = eventDetail?.installArea ?: "-",
                                        color = textColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        fontFamily = Pretendard
                                    )
                                    Text(
                                        text = "${eventDetail?.eventName ?: "이벤트"}가 감지되었습니다.",
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
                                "감지 이벤트" to (eventDetail?.eventName ?: "-"),
                                "발생 시간" to (eventDetail?.detectedAt ?: "-"),
                                "장치명" to (eventDetail?.deviceName ?: "-"),
                                "발생위치" to (eventDetail?.installArea ?: "-")
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
                                    
                                    // 3. "감지 이벤트" 라벨일 경우에만 전용 색상 적용
                                    val displayColor = if (label == "감지 이벤트") eventValueColor else textColor
                                    
                                    Text(
                                        text = value,
                                        color = displayColor,
                                        fontSize = 16.sp,
                                        fontFamily = Pretendard,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "조치 유형",
                        fontWeight = FontWeight.Medium,
                        color = CategoryColor,
                        fontFamily = Pretendard,
                        fontSize = 16.sp,
                        modifier = Modifier.offset(x = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(15.dp))
                    
                    // 조치 유형 드롭다운 박스
                    var dropdownSize by remember { mutableStateOf(Size.Zero) }
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).onGloballyPositioned { coordinates ->
                        dropdownSize = coordinates.size.toSize()
                    }) {
                        val selectedIconRes = when (actionType) {
                            "조치공유" -> R.drawable.priority1
                            "조치필요" -> R.drawable.priority2
                            "즉시조치" -> R.drawable.priority3
                            else -> null
                        }

                        OutlinedTextField(
                            value = if (actionType.isEmpty()) "조치 유형 선택" else actionType,
                            onValueChange = { },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().height(59.dp),
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
                                        if (selectedIconRes != null) {
                                            Icon(
                                                painter = painterResource(id = selectedIconRes),
                                                contentDescription = null,
                                                tint = Color.Unspecified
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        Icon(
                                            painter = painterResource(id = R.drawable.dropbox),
                                            contentDescription = null,
                                            tint = if (expanded) MainOrange else borderColor,
                                        )
                                    }
                                }
                            },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = MainOrange,
                                unfocusedBorderColor = if (expanded) dropboxBorder else borderColor,
                                backgroundColor = btnBackColor
                            )
                        )

                        // 투명한 클릭 영역
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { expanded = !expanded }
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .width(with(LocalDensity.current) { dropdownSize.width.toDp() })
                                .background(btnBackColor, shape = RoundedCornerShape(8.dp))
                                .border(1.dp, dropboxBorder, shape = RoundedCornerShape(8.dp))
                        ) {
                            options.forEach { selection ->
                                val itemIconRes = when (selection) {
                                    "조치공유" -> R.drawable.priority1
                                    "조치필요" -> R.drawable.priority2
                                    "즉시조치" -> R.drawable.priority3
                                    else -> null
                                }

                                DropdownMenuItem(
                                    onClick = {
                                        actionType = selection
                                        expanded = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (actionType == selection) MainOrange.copy(alpha = selectAlpha) else Color.Transparent)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (itemIconRes != null) {
                                            Icon(
                                                painter = painterResource(id = itemIconRes),
                                                contentDescription = null,
                                                tint = Color.Unspecified
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        Text(
                                            text = selection,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = Pretendard,
                                            color = inputTextColor
                                        )
                                    }
                                }
                            }
                        }
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
                    Spacer(modifier = Modifier.height(15.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(59.dp),
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = Pretendard, color = inputTextColor),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = MainOrange,
                            unfocusedBorderColor = borderColor,
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
                    Spacer(modifier = Modifier.height(15.dp))

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },

                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(180.dp),
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = Pretendard, color = inputTextColor),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = MainOrange,
                            unfocusedBorderColor = borderColor,
                            backgroundColor = btnBackColor
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "사진 첨부",
                        fontWeight = FontWeight.Medium,
                        color = CategoryColor,
                        fontFamily = Pretendard,
                        fontSize = 16.sp,
                        modifier = Modifier.offset(x = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(15.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 사진 추가 버튼
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(btnBackColor, shape = RoundedCornerShape(8.dp))
                                .clickable {
                                    launcher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                                drawRoundRect(
                                    color = borderColor,
                                    style = Stroke(width = 1.dp.toPx(), pathEffect = pathEffect),
                                    cornerRadius = CornerRadius(8.dp.toPx())
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(id = R.drawable.camera_icon),
                                    contentDescription = "사진첨부",
                                    tint = if (isLight) Color.Unspecified else TextGray30
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "사진첨부",
                                    color = photoColor,
                                    fontSize = 18.sp,
                                    fontFamily = Pretendard
                                )
                            }
                        }

                        // 첨부된 사진들 표시 영역
                        attachedPhotos.forEach { photoUri ->
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .background(btnBackColor, shape = RoundedCornerShape(8.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                GlideImage(
                                    model = photoUri,
                                    contentDescription = "Attached Photo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // 전송 버튼
                    Button(
                        onClick = {
                            if (actionType.isEmpty() || title.isEmpty() || content.isEmpty()) {
                                ToastUtil.showShort(context, "모든 항목을 입력해주세요.")
                            } else {
                                val eventIdBody = RequestBody.create("text/plain".toMediaTypeOrNull(), eventId.toString())
                                val requesterIdBody = RequestBody.create("text/plain".toMediaTypeOrNull(), (UserSession.userId ?: ""))
                                val typeBody = RequestBody.create("text/plain".toMediaTypeOrNull(), actionType)
                                val titleBody = RequestBody.create("text/plain".toMediaTypeOrNull(), title)
                                val detailsBody = RequestBody.create("text/plain".toMediaTypeOrNull(), content)

                                val imageParts = attachedPhotos.mapNotNull { uriString ->
                                    val uri = Uri.parse(uriString)
                                    uriToFile(context, uri)?.let { file ->
                                        val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
                                        MultipartBody.Part.createFormData("images", file.name, requestFile)
                                    }
                                }

                                RetrofitClient.instance.createActionRequest(
                                    eventIdBody, requesterIdBody, typeBody, titleBody, detailsBody, imageParts
                                ).enqueue(object : Callback<Void> {
                                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                                        if (response.isSuccessful) {
                                            ToastUtil.showShort(context, "조치 요청이 완료되었습니다.")
                                            onBackClick()
                                        } else {
                                            ToastUtil.showShort(context, "요청 실패: ${response.code()}")
                                        }
                                    }
                                    override fun onFailure(call: Call<Void>, t: Throwable) {
                                        ToastUtil.showShort(context, "네트워크 오류: ${t.message}")
                                    }
                                })
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MainOrange)
                    ) {
                        Text(
                            text = "작성 완료",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Pretendard
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

// 점선 테두리를 위한 Modifier 확장 함수
fun Modifier.dashedBorder(width: Dp, color: Color, cornerRadius: Dp) = drawWithContent {
    drawContent()
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    )
    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius = CornerRadius(cornerRadius.toPx())
    )
}

// 스크롤바 Modifier
fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
    paddingRight: Dp = 4.dp
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue > 0) {
        val scrollbarHeight = size.height * (size.height / (state.maxValue + size.height))
        val scrollbarOffsetY = state.value * (size.height / (state.maxValue + size.height))
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - paddingRight.toPx(), scrollbarOffsetY),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2)
        )
    }
}

fun uriToFile(context: android.content.Context, uri: Uri): File? {
    return try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val tempFile = File.createTempFile("upload", ".jpg", context.cacheDir)
        val outputStream = FileOutputStream(tempFile)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
