package com.example.smart_safety_management

import android.app.DatePickerDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*
import java.util.Calendar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.util.Log
import android.widget.Toast
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import androidx.compose.material.icons.filled.Close


@Composable
fun DailyListScreen(
    defaultDate: String,

    // ✅ 추가: 수정모드에서 날짜도 프리필하려면 필요
    initialDate: String = "",

    initialLocation: String = "",
    initialRiskFactor: String = "",
    initialSafetyMeasure: String = "",
    initialPhotoUris: List<String> = emptyList(),
    onComplete: (String, String, String, String, List<String>) -> Unit
) {
    val activity = LocalContext.current as? ComponentActivity
    val context = LocalContext.current

    // ✅ 핵심: initialDate가 있으면 그걸로 시작, 없으면 defaultDate
    val startDate = remember(defaultDate, initialDate) {
        if (initialDate.isNotBlank()) initialDate else defaultDate
    }
    var dateStr by remember(startDate) { mutableStateOf(startDate) }

    var location by remember(initialLocation) { mutableStateOf(initialLocation) }
    var riskFactor by remember(initialRiskFactor) { mutableStateOf(initialRiskFactor) }
    var safetyMeasure by remember(initialSafetyMeasure) { mutableStateOf(initialSafetyMeasure) }
    var attachedPhotos by remember(initialPhotoUris) { mutableStateOf(initialPhotoUris) }
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            Toast.makeText(context, "선택된 사진이 없어요", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        val newOnes = uris
            .map { it.toString() }
            .distinct()
            .filterNot { it in attachedPhotos }

        attachedPhotos = attachedPhotos + newOnes
    }

    val isFormComplete =
        dateStr.isNotBlank() &&
                location.isNotBlank() &&
                riskFactor.isNotBlank() &&
                safetyMeasure.isNotBlank() &&
                attachedPhotos.isNotEmpty()

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
                // 작성일
                Text(
                    "작성일",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(
                        fontFamily = Pretendard,
                        fontSize = 18.sp,
                        color = fieldTextColor
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val (iy, im, id) = parseYmdOrToday(dateStr)
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        dateStr = String.format("%04d-%02d-%02d", y, m + 1, d)
                                    },
                                    iy,
                                    im - 1,
                                    id
                                ).show()
                            }
                        ) {
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

                // 위치
                Text(
                    "위치",
                    fontSize = 16.sp,
                    color = labelColor,
                    fontFamily = Pretendard,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(
                        fontFamily = Pretendard,
                        fontSize = 18.sp,
                        color = fieldTextColor
                    ),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 위험요인
                Text(
                    "위험요인",
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
                    textStyle = TextStyle(
                        fontFamily = Pretendard,
                        fontSize = 18.sp,
                        color = fieldTextColor
                    ),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 안전대책
                Text(
                    "안전대책",
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
                    textStyle = TextStyle(
                        fontFamily = Pretendard,
                        fontSize = 18.sp,
                        color = fieldTextColor
                    ),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        unfocusedBorderColor = borderColor,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        backgroundColor = fieldBgColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 현장사진
                Text(
                    "현장사진",
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
                                pickImagesLauncher.launch("image/*")

                            }
                        ,

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

                    attachedPhotos.reversed().forEach { uriStr ->
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(fieldBgColor, shape = RoundedCornerShape(8.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = uriStr,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // ✅ 삭제 X 버튼
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .background(fieldBgColor, RoundedCornerShape(999.dp))
                                    .clickable {
                                        attachedPhotos = attachedPhotos.filterNot { it == uriStr }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "remove",
                                    tint = MaterialTheme.colors.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }


                }

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        if (isFormComplete) {
                            onComplete(dateStr, location, riskFactor, safetyMeasure, attachedPhotos)
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

private fun parseYmdOrToday(dateStr: String): Triple<Int, Int, Int> {
    val parts = dateStr.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull()
    val m = parts.getOrNull(1)?.toIntOrNull()
    val d = parts.getOrNull(2)?.toIntOrNull()

    return if (y != null && m != null && d != null) {
        Triple(y, m, d)
    } else {
        val cal = Calendar.getInstance()
        Triple(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
