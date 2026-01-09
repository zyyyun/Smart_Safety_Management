package com.example.smart_safety_management

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import java.util.Calendar

@Preview
@Composable
fun DailyListScreen() {
    // Calendar 인스턴스를 먼저 생성하여 기본 날짜로 사용
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    var date by remember { mutableStateOf("$year-${month + 1}-$day") }
    var location by remember { mutableStateOf("") }
    var riskFactor by remember { mutableStateOf("") }
    var safetyMeasure by remember { mutableStateOf("") }
    var attachedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }

    // 모든 필드가 채워졌는지 확인하는 조건
    val isFormComplete = date.isNotBlank() &&
            location.isNotBlank() &&
            riskFactor.isNotBlank() &&
            safetyMeasure.isNotBlank() &&
            attachedPhotos.isNotEmpty()

    // DatePickerDialog를 위한 Context
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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "일일안전점검 작성",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black, // M2에서는 컨텐츠 색상을 직접 지정해야 합니다.
                            modifier = Modifier.offset(x = (-16).dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* Handle back navigation */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.backicon),
                                contentDescription = "Back",
                                tint = Color.Black // M2에서는 아이콘 색상(tint)을 직접 지정해야 합니다.
                            )
                        }
                    },
                    backgroundColor = Color.White // M3의 containerColor 대신 backgroundColor 사용
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = "작성일", fontSize = 14.sp, color = Color(0xFF58616A))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {

                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.calendar2),
                                contentDescription = "Select date",
                                tint = Color(0xFF33363D)
                            )
                        }
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        unfocusedBorderColor = Color(0xFFCDD1D5)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "위치", fontSize = 14.sp, color = Color(0xFF58616A))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        unfocusedBorderColor = Color(0xFFCDD1D5)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "위험요인", fontSize = 14.sp, color = Color(0xFF58616A))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = riskFactor,
                    onValueChange = { riskFactor = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        unfocusedBorderColor = Color(0xFFCDD1D5)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "안전대책", fontSize = 14.sp, color = Color(0xFF58616A))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = safetyMeasure,
                    onValueChange = { safetyMeasure = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        unfocusedBorderColor = Color(0xFFCDD1D5)
                    )

                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "현장사진", fontSize = 14.sp, color = Color(0xFF58616A))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // '사진첨부' 버튼을 항상 왼쪽에 고정
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clickable {
                                // 새 사진 URI 추가 시뮬레이션
                                attachedPhotos = attachedPhotos + "photoUri_${System.currentTimeMillis()}"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                            drawRoundRect(
                                color = Color.Gray,
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
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "사진첨부",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // 버튼 오른쪽에 첨부된 사진들을 표시 (가장 최근 사진이 버튼 바로 옆)
                    attachedPhotos.reversed().forEach { photoUri ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // 실제 앱에서는 Coil이나 Glide를 사용하여 이미지 표시
                            Icon(Icons.Filled.Photo, contentDescription = "Attached Photo: $photoUri", tint = Color.Gray)
                        }
                    }
                }

                // This spacer will push the button to the bottom
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { /* 작성 완료 로직 */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isFormComplete, // 조건에 따라 버튼 활성화
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFF97316), // 활성화 시 주황색 배경
                        contentColor = Color.White,            // 활성화 시 흰색 글씨
                        disabledBackgroundColor = Color(0xFFF4F5F6), // 비활성화 시 회색 배경
                        disabledContentColor = Color(0xFF8A949E)          // 비활성화 시 진한 회색 글씨
                    )
                ) {
                    Text(
                        text = "작성 완료",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
