package com.example.smart_safety_management

import android.app.DatePickerDialog
import android.content.res.Configuration
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*
import java.util.Calendar

// ✅ Preview는 파라미터 없는 Composable이 필요해서 래퍼를 하나 둠
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light")
@Composable
fun DailyListWorkerScreenPreview() {
    DailyListWorkerScreen(
        onComplete = { _, _, _, _ -> }
    )
}

@Composable
fun DailyListWorkerScreen(
    onComplete: (date: String, location: String, riskFactor: String, safetyMeasure: String) -> Unit
) {
    val activity = LocalContext.current as? ComponentActivity
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    var date by remember { mutableStateOf("$year-${month + 1}-$day") }

    // ✅ 추가: 위치 / 조치내용
    var location by remember { mutableStateOf("") }
    var safetyMeasure by remember { mutableStateOf("") }

    var riskFactor by remember { mutableStateOf("") }
    var attachedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }


    // ✅ 완료조건: 4개 입력 + 사진 1장 이상 (원하면 조건 바꿔도 됨)
    val isFormComplete =
        date.isNotBlank() &&
                location.isNotBlank() &&
                riskFactor.isNotBlank() &&
                safetyMeasure.isNotBlank() &&
                attachedPhotos.isNotEmpty()

    val context = LocalContext.current

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                date = "$selectedYear-${selectedMonth + 1}-$selectedDayOfMonth"
            },
            year, month, day
        )
    }

    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val labelColor = if (isLight) TextGray60 else TextGray
        val calendarIconTint = if (isLight) Color.Unspecified else GrayBorder
        val fieldBgColor = if (isLight) Color.White else TextGray20
        val borderColor = if (isLight) GrayBorder else TextDark
        val textColor = if (isLight) TextGray else TextGray60
        val placeholder = if (isLight) TextLight else TextGray30
        val fieldTextColor = if (isLight) TextGray20 else TextGray5

        Scaffold(
            backgroundColor = MaterialTheme.colors.onPrimary,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "일일안전점검 보고",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onSurface,
                            fontSize = 24.sp,
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
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                // ✅ 작성일
                Text(
                    text = "작성일",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = fieldTextColor),
                    trailingIcon = {
                        IconButton(onClick = { datePickerDialog.show() }) {
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

                // ✅ 위치 (추가)
                Text(
                    text = "위치",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    placeholder = {
                        Text(
                            text = "예) A구역 3열",
                            fontFamily = Pretendard,
                            fontSize = 18.sp,
                            color = placeholder
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = fieldTextColor),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ✅ 점검결과(기존 riskFactor)
                Text(
                    text = "점검결과",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = riskFactor,
                    onValueChange = { riskFactor = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    placeholder = {
                        Text(
                            text = "점검 내용을 작성해주세요.",
                            fontFamily = Pretendard,
                            fontSize = 18.sp,
                            color = placeholder
                        )
                    },
                    singleLine = false,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = fieldTextColor),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ✅ 조치내용 (추가)
                Text(
                    text = "조치내용",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = safetyMeasure,
                    onValueChange = { safetyMeasure = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    placeholder = {
                        Text(
                            text = "조치 내용을 작성해주세요.",
                            fontFamily = Pretendard,
                            fontSize = 18.sp,
                            color = placeholder
                        )
                    },
                    singleLine = false,
                    textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = fieldTextColor),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ✅ 사진
                Text(
                    text = "사진",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 4.dp)
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
                            .padding(horizontal = 4.dp)
                            .size(120.dp)
                            .background(fieldBgColor, shape = RoundedCornerShape(8.dp))
                            .clickable {
                                attachedPhotos = attachedPhotos + "photoUri_${System.currentTimeMillis()}"
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

                    attachedPhotos.reversed().forEach { photoUri ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(fieldBgColor, shape = RoundedCornerShape(8.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Photo,
                                contentDescription = "Attached Photo: $photoUri",
                                tint = if (MaterialTheme.colors.isLight) Color.Gray else TextGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // ✅ 핵심 수정: onComplete() -> onComplete(값4개)
                Button(
                    onClick = {
                        if (isFormComplete) {
                            onComplete(
                                date,
                                location,
                                riskFactor,
                                safetyMeasure
                            )

                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isFormComplete,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary,
                        disabledBackgroundColor = if (MaterialTheme.colors.isLight) TextGray5 else TextGray20,
                        disabledContentColor = textColor
                    )
                ) {
                    Text(
                        text = "작성완료",
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Pretendard,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                        letterSpacing = (-0.3).sp
                    )
                }
            }
        }
    }
}
