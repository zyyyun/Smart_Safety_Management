package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.smart_safety_management.ui.theme.*
import android.net.Uri
import coil.compose.AsyncImage

@Composable
fun DailyDetailWorkerScreen(
    onBackClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onReportClick: () -> Unit = {},

    // ✅ 상세 데이터는 파라미터로만 받기
    date: String,
    location: String,
    riskFactor: String,
    safetyMeasure: String,

    photoUris: List<String> = emptyList()

) {
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
                            fontSize = 24.sp,
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
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 작성일
                LabelAndValue(label = "작성일", value = date, labelColor = labelColor, valueColor = fontColor, dividerColor = borderColor)

                Spacer(modifier = Modifier.height(24.dp))

                // 위치
                LabelAndValue(label = "위치", value = location, labelColor = labelColor, valueColor = fontColor, dividerColor = borderColor)

                Spacer(modifier = Modifier.height(24.dp))

                // 위험요인
                LabelAndValue(label = "위험요인", value = riskFactor, labelColor = labelColor, valueColor = fontColor, dividerColor = borderColor)

                Spacer(modifier = Modifier.height(24.dp))

                // 안전대책 (비어있으면 그냥 빈칸)
                LabelAndValue(label = "안전대책", value = safetyMeasure, labelColor = labelColor, valueColor = fontColor, dividerColor = borderColor)

                Spacer(modifier = Modifier.height(25.dp))

                // ✅ 현장사진: photoUris 있을 때만 보여줌 (content:// Uri)
                if (photoUris.isNotEmpty()) {
                    Text(
                        text = "현장사진",
                        fontSize = 16.sp,
                        color = labelColor,
                        fontFamily = Pretendard,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        photoUris.take(2).forEachIndexed { index, uriStr ->
                            Surface(
                                modifier = Modifier.size(120.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (MaterialTheme.colors.isLight) Color(0xFFF4F5F6) else TextGray20,
                                border = BorderStroke(1.dp, borderColor)
                            ) {
                                AsyncImage(
                                    model = Uri.parse(uriStr),
                                    contentDescription = "현장사진 ${index + 1}",
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (index == 0 && photoUris.size > 1) Spacer(modifier = Modifier.width(12.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }


                // ✅ 점검 보고하기 버튼
                Button(
                    onClick = onReportClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Text(
                        text = "점검 보고하기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Pretendard,
                        letterSpacing = (-0.3).sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ✅ 수정하기 버튼
                Button(
                    onClick = onEditClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
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

                Spacer(modifier = Modifier.height(18.dp))

                // ✅ 삭제하기 (텍스트 버튼)
                TextButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "삭제하기",
                        color = if (MaterialTheme.colors.isLight) TextGray60 else TextGray30,
                        fontFamily = Pretendard,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun LabelAndValue(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    dividerColor: Color
) {
    Text(
        text = label,
        fontSize = 16.sp,
        color = labelColor,
        fontFamily = Pretendard,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    BasicTextField(
        value = value,
        onValueChange = { },
        readOnly = true,
        textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = valueColor),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Column {
                inner()
                Spacer(modifier = Modifier.height(25.dp))
                Divider(color = dividerColor, thickness = 1.dp)
            }
        }
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light")
@Composable
fun DailyDetailWorkerScreenPreview() {
    DailyDetailWorkerScreen(
        date = "2026-01-15",
        location = "DD",
        riskFactor = "DD",
        safetyMeasure = "",
        photoUris = emptyList()
    )
}
