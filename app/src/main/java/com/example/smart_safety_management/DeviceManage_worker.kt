package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*

sealed class DeviceData {
    // 공통 속성
    abstract val batteryLevel: Int // 충전 상태 (%)

    // 1. 스마트 헬멧
    data class SmartHelmet(
        override val batteryLevel: Int,
        val detectionCount: Int, // 미착용 감지 횟수
        val iconRes: Int = R.drawable.home // 헬멧 아이콘
    ) : DeviceData()

    // 2. 스마트 워치
    data class SmartWatch(
        override val batteryLevel: Int,
        val temperature: Float,  // 체온
        val heartRate: Int,      // 심박수
        val iconRes: Int = R.drawable.watch // 워치 아이콘
    ) : DeviceData()
}


@Composable
fun DeviceManageWorkerScreen(
    onBackClick: () -> Unit = {}
) {
    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val devices = remember {
            listOf(
                DeviceData.SmartHelmet(batteryLevel = 50, detectionCount = 10),
                DeviceData.SmartWatch(batteryLevel = 85, temperature = 36.5f, heartRate = 72)
            )
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = "기기 관리", 
                            fontWeight = FontWeight.Bold, 
                            color = if (isLight) Color.Black else TextGray5, 
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
                                tint = if (isLight) Color.Black else TextGray5
                            ) 
                        } 
                    },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp,
                    // ✅ TopAppBar의 높이를 44.dp로 조정하여 텍스트 상하 여백을 약 10.dp로 맞춤
                    modifier = Modifier.height(44.dp)
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colors.onPrimary
            ) {
                // ✅ 부모 Column에 verticalScroll이 있으므로 내부에는 LazyColumn 대신 forEach를 사용합니다.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 36.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LabelText("현황")
                    
                    devices.forEach { device ->
                        WorkerDeviceBox(device = device)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { /* 클릭 시 동작 */ }, // onClick 파라미터는 필수입니다
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 14.dp)
                            .height(52.dp),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.register),
                            contentDescription = "등록 아이콘",
                            tint = MaterialTheme.colors.onPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // 🌟 버튼 내부에는 반드시 컴포저블(예: Text)이 들어가야 에러가 나지 않습니다.
                        Text(text = "기기등록", fontSize = 18.sp
                        ,fontFamily = Pretendard
                        ,color = MaterialTheme.colors.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkerDeviceBox(device: DeviceData) {
    val isLight = MaterialTheme.colors.isLight
    val mainTextColor = if (isLight) TextDark else GrayBorder
    val borderColor = if (isLight) GrayBorder else TextDark
    val iconRes = when (device) {
        is DeviceData.SmartHelmet -> device.iconRes
        is DeviceData.SmartWatch -> device.iconRes
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(color = MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, shape = RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 헤더 (아이콘 + 이름)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MainOrange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (device is DeviceData.SmartHelmet) "스마트 헬멧" else "스마트 워치",
                    fontSize = 18.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    color = mainTextColor
                )
            }

            // 2. 상세 정보 영역
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (device) {
                    is DeviceData.SmartHelmet -> {
                        LabelText("미착용 감지")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${device.detectionCount}회",
                            fontFamily = Pretendard,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MainOrange
                        )
                    }
                    is DeviceData.SmartWatch -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LabelText("체온")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${device.temperature}°",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusBlue,
                                fontFamily = Pretendard
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            LabelText("심박수")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${device.heartRate} BPM",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusBlue,
                                fontFamily = Pretendard
                            )
                        }
                    }
                }
            }

            // 3. 배터리 상태
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LabelText("충전상태")
                Text(
                    text = "${device.batteryLevel}%",
                    fontSize = 18.sp,
                    fontFamily = Pretendard,
                    color = mainTextColor,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = device.batteryLevel / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (device is DeviceData.SmartHelmet) MainOrange else StatusBlue,
                backgroundColor = if (isLight) Lightgray else GrayBackground
            )

        }
    }

}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun DeviceManageWorkerScreenPreview() {
    DeviceManageWorkerScreen()
}
