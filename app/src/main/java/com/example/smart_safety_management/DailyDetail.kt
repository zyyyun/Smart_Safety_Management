    package com.example.smart_safety_management

    import android.app.Activity
    import android.content.Intent
    import android.content.res.Configuration
    import androidx.compose.foundation.BorderStroke
    import androidx.compose.foundation.Image
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.horizontalScroll
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.BasicTextField
    import androidx.compose.foundation.verticalScroll
    import androidx.compose.material.*
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.painterResource
    import androidx.compose.ui.text.TextStyle
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.tooling.preview.Preview
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import com.example.smart_safety_management.ui.theme.*
    import android.net.Uri
    import coil.compose.AsyncImage
    import android.widget.Toast
    import retrofit2.Call
    import retrofit2.Callback
    import retrofit2.Response


    @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark")
    @Composable
    fun DailyDetailScreenPreview() {
        DailyDetailScreen(
            date = "2026-05-07",
            location = "C구역 2열",
            riskFactor = "정리 미흡으로 인한 안전사고 발생 우려",
            safetyMeasure = "자재 정리 및 주변 시설물 점검",
            day = 22,
            itemId = "sample-id",
            onBackClick = {},
            onEditClick = {}      // ✅ 추가
        )
    }


    @Composable
    fun DailyDetailScreen(
        date: String,
        location: String,
        riskFactor: String,
        safetyMeasure: String,
        day: Int,
        itemId: String,
        photoUris: List<String> = emptyList(),
        onBackClick: () -> Unit = {},
        onEditClick: () -> Unit,
        photoResIds: List<Int> = emptyList()
    ) {
        val activity = LocalContext.current as? Activity
        val context = LocalContext.current

        // ✅ 삭제 API 호출 및 결과 처리
        fun deleteCheck() {
            val checkIdInt = itemId.toIntOrNull()
            if (checkIdInt == null) {
                Toast.makeText(context, "잘못된 ID입니다.", Toast.LENGTH_SHORT).show()
                return
            }

            val request = DeleteDailyCheckRequest(checkIdInt)
            RetrofitClient.instance.deleteDailyCheck(request).enqueue(object : Callback<DeleteDailyCheckResponse> {
                override fun onResponse(call: Call<DeleteDailyCheckResponse>, response: Response<DeleteDailyCheckResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        val result = Intent().apply {
                            putExtra("action", "delete")
                            putExtra("day", day)
                            putExtra("itemId", itemId)
                        }
                        activity?.setResult(Activity.RESULT_OK, result)
                        activity?.finish()
                    } else {
                        Toast.makeText(context, "삭제 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<DeleteDailyCheckResponse>, t: Throwable) {
                    Toast.makeText(context, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

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
                    Text(
                        text = "작성일",
                        fontSize = 16.sp,
                        color = labelColor,
                        fontFamily = Pretendard,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = date,
                        onValueChange = { },
                        readOnly = true,
                        textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = fontColor),
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
                        fontSize = 16.sp,
                        color = labelColor,
                        fontFamily = Pretendard,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = location,
                        onValueChange = { },
                        readOnly = true,
                        textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = fontColor),
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
                        fontSize = 16.sp,
                        color = labelColor,
                        fontFamily = Pretendard,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = riskFactor,
                        onValueChange = { },
                        readOnly = true,
                        textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = fontColor),
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
                        fontSize = 16.sp,
                        color = labelColor,
                        fontFamily = Pretendard,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = safetyMeasure,
                        onValueChange = { },
                        readOnly = true,
                        textStyle = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, color = fontColor),
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

                    // ✅ 현장사진
                    Text(
                        text = "현장사진",
                        fontSize = 16.sp,
                        color = labelColor,
                        fontFamily = Pretendard,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (photoUris.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            photoUris.forEachIndexed { index, uriStr ->
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
                                if (index < photoUris.lastIndex) Spacer(modifier = Modifier.width(12.dp))
                            }
                        }
                    } else {
                        Text(
                            text = "첨부된 사진이 없습니다.",
                            fontSize = 14.sp,
                            color = if (MaterialTheme.colors.isLight) TextGray30 else TextGray60,
                            fontFamily = Pretendard
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Spacer(modifier = Modifier.height(40.dp))

// ✅ 수정하기 버튼 (원래처럼 주황 버튼)
                    Button(
                        onClick = { onEditClick() },
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

// ✅ 삭제하기
                    TextButton(
                        onClick = { deleteCheck() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "삭제하기",
                            color = if (MaterialTheme.colors.isLight) TextGray60 else TextGray30,
                            fontFamily = Pretendard
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                }
                }
            }
        }
