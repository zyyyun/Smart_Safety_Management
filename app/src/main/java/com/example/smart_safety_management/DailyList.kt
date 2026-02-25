package com.example.smart_safety_management

import android.content.res.Configuration
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.activity
import coil.compose.AsyncImage
import com.example.smart_safety_management.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.zIndex

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DailyListScreen(
    defaultDate: String? = null,
    defaultLocation: String = "",
    defaultRiskFactor: String = "",
    defaultSafetyMeasure: String = "",
    defaultAttachedPhotos: List<String> = emptyList(),
    checkId: String? = null,
    onComplete: (String, String, String, String, List<String>, String, Int) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    val activity = LocalContext.current as? ComponentActivity
    
    // LocalDate 사용 (기본값: 오늘)
    var date by remember { mutableStateOf(defaultDate ?: LocalDate.now().toString()) }
    var location by remember { mutableStateOf(defaultLocation) }
    var riskFactor by remember { mutableStateOf(defaultRiskFactor) }
    var safetyMeasure by remember { mutableStateOf(defaultSafetyMeasure) }
    var attachedPhotos by remember { mutableStateOf(defaultAttachedPhotos) }

    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    val isFormComplete = date.isNotBlank() &&
            location.isNotBlank() &&
            riskFactor.isNotBlank() &&
            safetyMeasure.isNotBlank()

    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        attachedPhotos = attachedPhotos + uris.map { it.toString() }
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Smart_Safety_ManagementTheme {
        val labelColor = if (MaterialTheme.colors.isLight) TextGray60 else TextGray
        val calendarIconTint = if (MaterialTheme.colors.isLight) Color.Unspecified else GrayBorder
        val fieldBgColor = if (MaterialTheme.colors.isLight) Color.White else TextGray20
        val borderColor = if (MaterialTheme.colors.isLight) GrayBorder else TextDark
        val textColor = if (MaterialTheme.colors.isLight) TextGray else TextGray60
        val fieldTextColor = if (MaterialTheme.colors.isLight) TextGray20 else TextGray5
        Scaffold(
            backgroundColor = MaterialTheme.colors.onPrimary,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "일일안전점검 작성",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colors.onSurface,
                            fontFamily = Pretendard,
                            modifier = Modifier.offset(x = (-24).dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { activity?.finish() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.backicon),
                                contentDescription = "Back",
                                tint = MaterialTheme.colors.onSurface
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp,
                    modifier = Modifier.height(40.dp)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "작성일",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp,color = fieldTextColor),
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.calendar2),
                                contentDescription = "Select date",
                                tint = calendarIconTint
                            )
                        }
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "위치",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal=8.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp,color = fieldTextColor),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "위험요인",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = riskFactor,
                    onValueChange = { riskFactor = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp,color = fieldTextColor),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "안전대책",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = safetyMeasure,
                    onValueChange = { safetyMeasure = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = fieldTextColor),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "현장사진",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(120.dp)
                            .background(fieldBgColor, shape = RoundedCornerShape(8.dp))
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.camera_icon),
                                contentDescription = "사진첨부",
                                tint = if (MaterialTheme.colors.isLight) Color.Unspecified else TextGray30
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "사진첨부",
                                color = if (MaterialTheme.colors.isLight) TextLight else TextGray30,
                                fontSize = 18.sp,
                                fontFamily = Pretendard
                            )
                        }
                    }

                    // 역순으로 보여주되, 삭제를 위해 원본 index를 들고 있음
                    attachedPhotos.asReversed().forEachIndexed { reversedIndex, photoUri ->
                        // asReversed()에서의 reversedIndex를 원본 index로 변환
                        val originalIndex = attachedPhotos.lastIndex - reversedIndex

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(fieldBgColor, shape = RoundedCornerShape(8.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = Uri.parse(photoUri),
                                contentDescription = "Attached Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            // ✅ 우측 상단 X 버튼
                            IconButton(
                                onClick = {
                                    attachedPhotos = attachedPhotos.toMutableList().also { it.removeAt(originalIndex) }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .zIndex(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete photo",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = {
                        if (isFormComplete && !isLoading) {
                            isLoading = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val userId = UserSession.userId ?: ""
                                val writerIdBody = RequestBody.create("text/plain".toMediaTypeOrNull(), userId)
                                val locationBody = RequestBody.create("text/plain".toMediaTypeOrNull(), location)
                                val hazardBody = RequestBody.create("text/plain".toMediaTypeOrNull(), riskFactor)
                                val countermeasureBody = RequestBody.create("text/plain".toMediaTypeOrNull(), safetyMeasure)
                                // val checkDateBody = RequestBody.create("text/plain".toMediaTypeOrNull(), date) // check_date는 null로 저장하기 위해 전송하지 않음
                                
                                if (checkId == null) {
                                    // 생성 모드
                                    val imageParts = attachedPhotos.mapNotNull { uriString ->
                                        val uri = Uri.parse(uriString)
                                        uriToFile(context, uri)?.let { file ->
                                            val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
                                            MultipartBody.Part.createFormData("images", file.name, requestFile)
                                        }
                                    }

                                    RetrofitClient.instance.createDailyCheck(
                                        writerIdBody, locationBody, hazardBody, countermeasureBody, null, imageParts
                                    ).enqueue(object : Callback<CreateDailyCheckResponse> {
                                        override fun onResponse(call: Call<CreateDailyCheckResponse>, response: Response<CreateDailyCheckResponse>) {
                                            isLoading = false
                                            if (response.isSuccessful) {
                                                val body = response.body()
                                                val newCheckId = body?.checkId?.toString() ?: ""
                                                val serverImageUrls = body?.imageUrls ?: emptyList()
                                                val dayInt = date.split("-").getOrNull(2)?.toIntOrNull() ?: 0
                                                onComplete(date, location, riskFactor, safetyMeasure, serverImageUrls, newCheckId, dayInt)
                                            } else {
                                                Toast.makeText(context, "저장 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        override fun onFailure(call: Call<CreateDailyCheckResponse>, t: Throwable) {
                                            isLoading = false
                                            Toast.makeText(context, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                                } else {
                                    // 수정 모드
                                    val checkIdBody = RequestBody.create("text/plain".toMediaTypeOrNull(), checkId)
                                    
                                    // 기존 이미지(URL)와 새 이미지(URI) 분리
                                    val keptUrls = attachedPhotos.filter { it.startsWith("http") }
                                    val newUris = attachedPhotos.filter { !it.startsWith("http") }

                                    val keptImageParts = keptUrls.map { url ->
                                        MultipartBody.Part.createFormData("kept_image_urls", url)
                                    }

                                    val newImageParts = newUris.mapNotNull { uriString ->
                                        val uri = Uri.parse(uriString)
                                        uriToFile(context, uri)?.let { file ->
                                            val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
                                            MultipartBody.Part.createFormData("new_images", file.name, requestFile)
                                        }
                                    }

                                    RetrofitClient.instance.updateDailyCheck(
                                        checkIdBody, locationBody, hazardBody, countermeasureBody, null, keptImageParts, newImageParts
                                    ).enqueue(object : Callback<CreateDailyCheckResponse> {
                                        override fun onResponse(call: Call<CreateDailyCheckResponse>, response: Response<CreateDailyCheckResponse>) {
                                            isLoading = false
                                            if (response.isSuccessful) {
                                                val body = response.body()
                                                val serverImageUrls = body?.imageUrls ?: emptyList()
                                                val dayInt = date.split("-").getOrNull(2)?.toIntOrNull() ?: 0
                                                onComplete(date, location, riskFactor, safetyMeasure, serverImageUrls, checkId, dayInt)
                                            } else {
                                                Toast.makeText(context, "수정 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        override fun onFailure(call: Call<CreateDailyCheckResponse>, t: Throwable) {
                                            isLoading = false
                                            Toast.makeText(context, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isFormComplete && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary,
                        disabledBackgroundColor = if (MaterialTheme.colors.isLight) TextGray5 else TextGray20,
                        disabledContentColor = textColor
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                    Text(
                        text = "작성완료",
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Pretendard,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                        letterSpacing = (-0.3).sp,
                    )
                    }
                }
            }
        }
        
        if (showDatePicker) {
            val currentDate = try {
                LocalDate.parse(date)
            } catch (e: Exception) {
                LocalDate.now()
            }
            CustomDatePickerDialog(
                initialDate = currentDate,
                onDismiss = { showDatePicker = false },
                onDateSelected = { selectedDate ->
                    date = selectedDate.toString() // yyyy-MM-dd 형식
                    showDatePicker = false
                }
            )
        }
    }
}
