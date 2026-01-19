package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*


data class MainCategory (
    val title : String,
    val count : Int,
    val icon : Int ?  = null ,
    val unit : String ? = "대"
)

// 메인 카테고리 리스트
val MainCAtegoryList = listOf(
    MainCategory ( title = "전체직원", count =  80, unit = "명"),
    MainCategory ( "GPS 미수신", 4, R.drawable.gps),
    MainCategory ( "배터리 부족", 3, R.drawable.battery),
    MainCategory("배터리 부족",6,R.drawable.watch)
)



@Composable
fun DeviceManageScreen(
    onBackClick: () -> Unit = {}
) {
    // 검색어 상태 관리
    var searchQuery by remember { mutableStateOf("") }



    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val textColor = if(isLight) TextGray20 else TextGray5
        val borderColor = if (isLight) GrayBorder else TextDark
        val placeholderColor = if (isLight) TextLight else TextGray30
        val dividerColor = if (isLight) Lightgray else GrayBackground
        val bgColor = if (isLight ) Color.White else TextGray20

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "장치 관리",
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontFamily = Pretendard,
                            modifier = Modifier.offset(x = (-24).dp),
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
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
                color = MaterialTheme.colors.onPrimary
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 23.dp, end = 23.dp ,top = 23.dp, bottom = 23.dp)
                ) {
                    // 검색바 구현
                    Box(
                        modifier = Modifier
                            .height(52.dp)
                            .background(color = MaterialTheme.colors.surface, shape = RoundedCornerShape(8.dp))
                            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "이름 검색",
                                    color = placeholderColor,
                                    fontSize = 17.sp,
                                    fontFamily = Pretendard
                                )
                            },
                            textStyle = TextStyle(
                                color = textColor,
                                fontSize = 16.sp,
                                fontFamily = Pretendard
                            ),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.search),
                                    contentDescription = null,
                                    tint = TextMedium,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = bgColor,
                                cursorColor = MainOrange,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 경계선 추가
                    Divider(
                        color = dividerColor,
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(81.dp)
                            .border(
                                width = 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(color = bgColor)
                            .padding(15.dp)

                    )
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun DeviceManageScreenPreview() {
    DeviceManageScreen()
}
