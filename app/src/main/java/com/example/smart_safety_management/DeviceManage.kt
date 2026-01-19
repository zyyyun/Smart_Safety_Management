package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.smart_safety_management.ui.theme.*


data class MainCategory (
    val title : String,
    val count : Int,
    val icon : Int ?  = null ,
    val unit : String ? = "대",
    val iconColor : Color ? = null
)
data class SubCategory (
    val title : String,
    val icon : Int ? = null,
)

// 배터리 현황 데이터 모델
data class DeviceBatteryData(
    val name: String,
    val role: String,
    val isGpsConnected: Boolean,
    val battery: Int,
    val watchBattery : Int
)

// 메인 카테고리 리스트
val MainCategoryList = listOf(
    MainCategory ( title = "전체직원", count =  80, unit = "명"),
    MainCategory ( "GPS 미수신", 4, R.drawable.gps, iconColor = Color(0xFFEF4444)),
    MainCategory ( "배터리 부족", 3, R.drawable.battery, iconColor = Color(0xFFF97316)),
    MainCategory("배터리 부족", 6, R.drawable.watch, iconColor = Color(0xFF3B82F6))
)
// 서브 카테고리 리스트
val SubCategoryList = listOf(
    SubCategory("전체"),
    SubCategory("GPS 미수신", R.drawable.gps),
    SubCategory("배터리 부족", R.drawable.battery),
    SubCategory("배터리 부족", R.drawable.watch)
)

// 배터리 현황 더미 데이터
val BatteryStatusList = listOf(
    DeviceBatteryData("안정우", "관리자", true, 85, 25),
    DeviceBatteryData("김철수", "작업자", false, 15, 10),
    DeviceBatteryData("이영희", "작업자", true, 45, 40),
    DeviceBatteryData("박민수", "작업자", true, 20, 95)
)

@Composable
fun DeviceManageScreen(
    onBackClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryObj by remember { mutableStateOf(SubCategoryList[0]) }
    // ✅ 알람 다이얼로그 상태 관리
    var showAlarmDialog by remember { mutableStateOf(false) }

    val filteredBatteryList = BatteryStatusList.filter { data ->
        val matchesSearch = data.name.contains(searchQuery, ignoreCase = true)
        val matchesCategory = when (selectedCategoryObj.title) {
            "GPS 미수신" -> !data.isGpsConnected
            "배터리 부족" -> {
                if (selectedCategoryObj.icon == R.drawable.battery) data.battery < 30
                else data.watchBattery < 30
            }
            else -> true
        }
        matchesSearch && matchesCategory
    }

    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val textColor = if(isLight) TextGray20 else TextGray5
        val borderColor = if (isLight) GrayBorder else TextDark
        val placeholderColor = if (isLight) TextLight else TextGray30
        val dividerColor = if (isLight) Lightgray else GrayBackground
        val categoryColor = if (isLight) TextGray60 else TextGray
        val subTextColor = if (isLight) TextDark else GrayBorder
        val bgColor = if (isLight) Color.White else TextGray20

        // ✅ 알람 다이얼로그 표시
        if (showAlarmDialog) {
            AlarmRequestDialog(onDismissRequest = { showAlarmDialog = false })
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "기기 관리", fontWeight = FontWeight.Bold, color = if (isLight) Color.Black else TextGray5, fontFamily = Pretendard, modifier = Modifier.offset(x = (-24).dp), fontSize = 24.sp) },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(painter = painterResource(id = R.drawable.backicon), contentDescription = "Back", tint = if (isLight) Color.Black else TextGray5) } },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            Surface(modifier = Modifier.fillMaxWidth().padding(paddingValues), color = MaterialTheme.colors.onPrimary) {
                Column(modifier = Modifier.fillMaxSize().padding(start = 23.dp, end = 23.dp, top = 23.dp).verticalScroll(rememberScrollState())) {
                    // 1. 검색바
                    Box(modifier = Modifier.height(52.dp).background(color = MaterialTheme.colors.surface, shape = RoundedCornerShape(8.dp)).border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        TextField(
                            value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(text = "이름 검색", color = placeholderColor, fontSize = 17.sp, fontFamily = Pretendard) },
                            textStyle = TextStyle(color = textColor, fontSize = 16.sp, fontFamily = Pretendard),
                            leadingIcon = { Icon(painter = painterResource(id = R.drawable.search), contentDescription = null, tint = TextMedium, modifier = Modifier.size(20.dp)) },
                            trailingIcon = { if (searchQuery.isNotEmpty()) { IconButton(onClick = { searchQuery = "" }) { Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(20.dp)) } } },
                            colors = TextFieldDefaults.textFieldColors(backgroundColor = bgColor, cursorColor = MainOrange, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = dividerColor, thickness = 1.dp, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. 메인 카테고리
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp) ) {
                        MainCategoryList.forEach { category ->
                            val isSelected = if (category.title == "전체직원") selectedCategoryObj.title == "전체" else selectedCategoryObj.title == category.title && selectedCategoryObj.icon == category.icon
                            MainCategoryItem(category = category, isSelected = isSelected, onClick = { selectedCategoryObj = if (category.title == "전체직원") SubCategoryList[0] else SubCategoryList.find { it.title == category.title && it.icon == category.icon } ?: SubCategoryList[0] }, borderColor = borderColor, bgColor = bgColor, textColor = textColor, categoryColor = categoryColor, subTextColor = subTextColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. 서브 카테고리 필터
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubCategoryList.forEach { sub ->
                            SubCategoryItem(category = sub, isSelected = selectedCategoryObj == sub, onClick = { selectedCategoryObj = sub }, borderColor = borderColor, bgColor = bgColor, selectedColor = MainOrange, textColor = textColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = "배터리 현황", fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = categoryColor)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. 필터링된 리스트 (알람 클릭 연결)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (filteredBatteryList.isEmpty()) {
                            Text(text = "검색 결과가 없습니다.", modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center, color = subTextColor, fontFamily = Pretendard)
                        } else {
                            filteredBatteryList.forEach { data ->
                                BatteryItem(data = data, onAlarmClick = { showAlarmDialog = true })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(35.dp))
                }
            }
        }
    }
}

@Composable
fun MainCategoryItem(category: MainCategory, isSelected: Boolean, onClick: () -> Unit, borderColor: Color, bgColor: Color, textColor: Color, categoryColor: Color, subTextColor : Color) {
    val finalBorderColor = if (isSelected) MainOrange else borderColor
    val finalBgColor = if (isSelected) MainOrange.copy(alpha = 0.05f) else bgColor
    Box(modifier = Modifier.height(81.dp).background(color = finalBgColor, shape = RoundedCornerShape(8.dp)).border(width = if (isSelected) 2.dp else 1.dp, color = finalBorderColor, shape = RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(15.dp)) {
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (category.icon != null) {
                    Box(modifier = Modifier.size(18.dp).background(color = category.iconColor?.copy(alpha = 0.1f) ?: Color.Transparent, shape = CircleShape), contentAlignment = Alignment.Center) { Icon(painter = painterResource(id = category.icon), contentDescription = null, tint = Color.Unspecified) }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(text = category.title, color = subTextColor, fontSize = 14.sp, fontFamily = Pretendard, fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = category.count.toString(), color = textColor, style = TextStyle(fontSize = 28.sp, fontFamily = Pretendard, fontWeight = FontWeight.Bold, platformStyle = PlatformTextStyle(includeFontPadding = false)))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = category.unit ?: "대", color = subTextColor, fontSize = 14.sp, fontFamily = Pretendard, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

@Composable
fun SubCategoryItem(category: SubCategory, isSelected: Boolean, onClick: () -> Unit, borderColor: Color, selectedColor: Color, textColor: Color, bgColor : Color) {
    val isLight = MaterialTheme.colors.isLight
    val notSelectTextColor = if (isLight) TextGray else TextGray60
    val backgroundColor = if (isLight) TextGray5 else TextGray20
    Button(onClick = onClick, modifier = Modifier.height(28.dp), shape = RoundedCornerShape(50.dp), colors = ButtonDefaults.buttonColors(backgroundColor = if (isSelected) selectedColor else backgroundColor, contentColor = if (isSelected) Color.White else notSelectTextColor), elevation = ButtonDefaults.elevation(0.dp, 0.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (category.icon != null) { Icon(painter = painterResource(id = category.icon), contentDescription = null, tint = if (isSelected) Color.White else notSelectTextColor, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(12.dp)) }
            Text(text = category.title, fontFamily = Pretendard, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, letterSpacing = (-0.5).sp)
        }
    }
}

@Composable
fun BatteryItem(data: DeviceBatteryData, onAlarmClick: () -> Unit) {
    val isLight = MaterialTheme.colors.isLight
    val textColor = if (isLight) TextGray20 else TextGray5
    val subTextColor = if (isLight) TextGray60 else TextGray
    val borderColor = if (isLight) GrayBorder else TextDark
    val bgColor = if (isLight) Color.White else TextGray20
    val mainTextColor = if (isLight) TextDark else GrayBorder
    val dividerColor = if (isLight) Lightgray else GrayBackground

    val isHelmetLow = data.battery < 30
    val isWatchLow = data.watchBattery < 30
    val helmetColor = if (isHelmetLow) StatusRed else MainOrange
    val helmetTextColor = if (isHelmetLow) StatusRed else mainTextColor
    val watchColor = if (isWatchLow) StatusRed else StatusBlue
    val watchTextColor = if (isWatchLow) StatusRed else mainTextColor

    Box(modifier = Modifier.width(330.dp).height(160.dp).background(color = bgColor, shape = RoundedCornerShape(12.dp)).border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp)).padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(id = R.drawable.avatar), contentDescription = null, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = data.name, color = mainTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = data.role, color = subTextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = Pretendard)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(painter = painterResource(id = R.drawable.gps), contentDescription = null, tint = if (data.isGpsConnected) StatusGreenDark else StatusRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (data.isGpsConnected) "GPS 정상" else "GPS 미수신", color = if (data.isGpsConnected) StatusGreenDark else StatusRed, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = Pretendard)
                    }
                }
                Button(onClick = onAlarmClick, colors = ButtonDefaults.buttonColors(backgroundColor = MainOrange, contentColor = MaterialTheme.colors.onPrimary), shape = RoundedCornerShape(8.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.alarm), contentDescription = null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.scale(0.75f))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "알람전송", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = Pretendard, letterSpacing = (-0.3).sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = dividerColor, thickness = 1.dp, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(painter = painterResource(id = R.drawable.battery), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "스마트헬멧", color = subTextColor, fontSize = 12.sp, fontFamily = Pretendard)
                        Spacer(modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isHelmetLow) { Icon(painter = painterResource(id = R.drawable.lowbattery), contentDescription = null, tint = Color.Unspecified); Spacer(modifier = Modifier.width(4.dp)) }
                            Text(text = "${data.battery}%", color = helmetTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(progress = data.battery / 100f, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = helmetColor, backgroundColor = dividerColor)
                }
                Divider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp).height(30.dp).width(1.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(painter = painterResource(id = R.drawable.watch), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "스마트워치", color = subTextColor, fontSize = 12.sp, fontFamily = Pretendard)
                        Spacer(modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isWatchLow) { Icon(painter = painterResource(id = R.drawable.lowbattery), contentDescription = null, tint = Color.Unspecified); Spacer(modifier = Modifier.width(4.dp)) }
                            Text(text = "${data.watchBattery}%", color = watchTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(progress = data.watchBattery / 100f, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = watchColor, backgroundColor = dividerColor)
                }
            }
        }
    }
}

// ✅ MonthlyList.kt에서 이식된 다이얼로그 코드
@Composable
fun AlarmRequestDialog(onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        val cardBgColor = if (MaterialTheme.colors.isLight) Color.White else GrayBackground
        val textColor = if (MaterialTheme.colors.isLight) TextGray20 else TextGray5
        val subTextColor = if (MaterialTheme.colors.isLight) TextGray60 else TextGray
        Card(
            modifier = Modifier.width(330.dp).height(259.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = 0.dp,
            backgroundColor = cardBgColor
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
                Icon(painter = painterResource(id = R.drawable.bell_icon), null, tint = MainOrange, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "알림 발송 완료", fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, fontFamily = Pretendard, color = textColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "해당 인원에게 알림을 발송하였습니다.", color = subTextColor, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center, fontFamily = Pretendard)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismissRequest, modifier = Modifier.width(290.dp).height(55.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp), colors = ButtonDefaults.buttonColors(backgroundColor = MainOrange, contentColor = MaterialTheme.colors.onPrimary), shape = RoundedCornerShape(12.dp)) {
                    Text(text = "확인", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = Pretendard)
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
