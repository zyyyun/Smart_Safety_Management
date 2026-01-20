package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*

// ✅ 카메라 데이터 모델
data class CCTVCameraData(
    val id: String,
    val name: String,
    val location: String,
    val events: List<String>,
    val image: Int
)

// ✅ 카메라 더미 데이터 리스트
val mockCCTVList = listOf(
    CCTVCameraData("1", "CAM01", "A구역 1열", listOf("안전모 미착용", "운반사고", "통로사고"), R.drawable.cctvcam1),
    CCTVCameraData("2", "CAM02", "B구역 3열", listOf("협착사고", "운반사고", "쓰러짐", "통로사고"), R.drawable.cctvcam2),
    CCTVCameraData("3", "CAM03", "C구역 1열", listOf("쓰러짐", "화재사고", "통로사고"), R.drawable.cctvcam3),
    CCTVCameraData("4", "CAM04", "D구역 1열", listOf("안전모 미착용", "협착사고"), R.drawable.cctvcam4)
)

// ✅ 감지 이벤트 카테고리 Enum
enum class CCTVEventType(val label: String) {
    ALL("전체"),
    HELMET_NOT_WEARING("안전모 미착용"),
    AISLE_ACCIDENT("통로사고"),
    COLLISION_ACCIDENT("충돌사고"),
    SAFETY_LINE_VIOLATION("안전선 침범"),
    TRANSPORT_ACCIDENT("운반사고"),
    FIRE_ACCIDENT("화재사고"),
    PINCH_ACCIDENT("협착사고"),
    FALL_DOWN("쓰러짐");

    companion object {
        val allLabels = values().map { it.label }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CCTVManagementScreen(
    onBackClick: () -> Unit = {}
) {
    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val editColor = TextMedium
        val titleColor = if (isLight) Color.Black else TextGray5
        val dividerColor = if (isLight) Lightgray else GrayBackground
        val frameDividerColor = if (isLight) TextGray5 else TextGray20

        // ✅ 상태 관리
        var isEditMode by remember { mutableStateOf(false) }
        var selectedArea by remember { mutableStateOf("전체 구역") }
        val selectedEvents = remember { mutableStateListOf<String>("전체") }
        val selectedCameras = remember { mutableStateListOf<String>() }

        // ✅ 설치 구역 + 감지 이벤트 통합 필터링 로직
        val filteredCCTVList = remember(selectedEvents.toList(), selectedArea) {
            mockCCTVList.filter { camera ->
                // 1. 구역 필터링
                val matchesArea = if (selectedArea == "전체 구역") true 
                                 else camera.location.contains(selectedArea.split(" ")[0])

                // 2. 이벤트 필터링
                val matchesEvent = if (selectedEvents.contains("전체")) true
                                  else camera.events.any { it in selectedEvents }

                matchesArea && matchesEvent
            }
        }

        Scaffold(
            backgroundColor = MaterialTheme.colors.onPrimary,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "CCTV 관리",
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
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
                                tint = titleColor
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { 
                            isEditMode = !isEditMode 
                            if (!isEditMode) selectedCameras.clear()
                        }) {
                            Text(
                                text = if (isEditMode) "완료" else "편집",
                                color = if (isEditMode) MainOrange else editColor,
                                fontFamily = Pretendard,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 36.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ✅ 편집 모드가 아닐 때만 필터 설정 표시
                AnimatedVisibility(
                    visible = !isEditMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LabelText("설치구역")
                            CCTVCustomDropdown(
                                options = listOf("전체 구역", "A구역", "B구역", "C구역", "D구역"),
                                selectedOption = selectedArea,
                                onOptionSelected = { selectedArea = it },
                                isLight = isLight
                            )
                        }
                        
                        Divider(color = dividerColor, thickness = 1.dp)

                        LabelText("감지 이벤트")

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CCTVEventType.allLabels.forEach { eventLabel ->
                                val isSelected = selectedEvents.contains(eventLabel)
                                CCTVEventSelectButton(
                                    text = eventLabel,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (eventLabel == "전체") {
                                            selectedEvents.clear()
                                            selectedEvents.add("전체")
                                        } else {
                                            selectedEvents.remove("전체")
                                            if (isSelected) {
                                                selectedEvents.remove(eventLabel)
                                                if (selectedEvents.isEmpty()) selectedEvents.add("전체")
                                            } else {
                                                selectedEvents.add(eventLabel)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        Divider(color = dividerColor, thickness = 1.dp)
                    }
                }
                
                // ✅ 편집 모드일 때 나타나는 액션 바
                if (isEditMode) {
                    EditModeActionBar(
                        isLight = isLight,
                        onSelectAll = {
                            selectedCameras.clear()
                            selectedCameras.addAll(filteredCCTVList.map { it.id })
                        }
                    )
                }

                LabelText("카메라 목록")
                
                Column {
                    filteredCCTVList.forEachIndexed { index, camera ->
                        CamFrame(
                            camera = camera,
                            isEditMode = isEditMode,
                            isSelected = selectedCameras.contains(camera.id),
                            onSelect = { isSelected ->
                                if (isSelected) selectedCameras.add(camera.id)
                                else selectedCameras.remove(camera.id)
                            }
                        )
                        
                        if (index < filteredCCTVList.size - 1) {
                            Divider(
                                color = frameDividerColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    }
                    if (filteredCCTVList.isEmpty()) {
                        Text(
                            text = "해당하는 카메라가 없습니다.",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = editColor,
                            fontFamily = Pretendard
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CamFrame(
    camera: CCTVCameraData,
    isEditMode: Boolean,
    isSelected: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val isLight = MaterialTheme.colors.isLight
    val buttonColor = if (isLight) TextGray else TextGray60
    val titleColor = if (isLight) TextDark else GrayBorder
    val subTextColor = if (isLight) TextGray60 else TextGray
    val frameBgColor = if (isLight) TextGray5 else TextGray20

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().graphicsLayer(clip = false)
    ) {
        if (isEditMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelect,
                colors = CheckboxDefaults.colors(checkedColor = MainOrange)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(157.dp)
                .background(color = MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(12.dp))
                .graphicsLayer(clip = false)
                .clickable { if (isEditMode) onSelect(!isSelected) }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().graphicsLayer(clip = false)
            ) {
                // 왼쪽: 이미지 박스 (100x100, 12dp 라운드 적용, 패딩 없이 모서리 밀착)
                Image(
                    painter = painterResource(id = camera.image),
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                // 오른쪽: 정보 영역 (start 패딩 16.dp만 적용)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().graphicsLayer(clip = false),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            // 카메라 이름
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.cctvcam),
                                    contentDescription = null,
                                    tint = titleColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = camera.name,
                                    color = titleColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Pretendard
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // 위치 정보
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.cctvlocation),
                                    contentDescription = null,
                                    tint = titleColor,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = camera.location,
                                    color = titleColor,
                                    fontSize = 14.sp,
                                    fontFamily = Pretendard
                                )
                            }

                            // ✅ 감지 이벤트 라벨 및 목록 (위치 텍스트 아래 16dp 간격)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "감지 이벤트",
                                color = titleColor,
                                fontSize = 14.sp,
                                fontFamily = Pretendard
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                camera.events.forEach { e ->
                                    Box(
                                        modifier = Modifier
                                            .height(21.dp)
                                            .background(color = frameBgColor, shape = RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = e, color = subTextColor, fontSize = 12.sp, fontFamily = Pretendard)
                                    }
                                }
                            }
                        }
                        if (!isEditMode) {
                            Icon(
                                painter = painterResource(id = R.drawable.option),
                                contentDescription = null,
                                tint = buttonColor,
                                modifier = Modifier.offset(x = (-5).dp, y = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditModeActionBar(isLight: Boolean, onSelectAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.clickable { onSelectAll() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.Transparent, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "전체 선택",
                fontFamily = Pretendard,
                fontSize = 16.sp,
                color = if (isLight) TextGray20 else TextGray5
            )
        }

        Button(
            onClick = { /* 삭제 로직 */ },
            colors = ButtonDefaults.buttonColors(backgroundColor = StatusRed),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.elevation(0.dp, 0.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = "삭제", color = Color.White, fontFamily = Pretendard, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CCTVCustomDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    isLight: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val bgColor = if (isLight) Color.White else TextGray20
    val borderColor = if (isLight) GrayBorder else TextDark

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(color = bgColor, shape = RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = borderColor ,shape = RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedOption,
                fontFamily = Pretendard,
                fontSize = 16.sp,
                color = if (isLight) TextGray20 else TextGray5
            )
            Icon(
                painter = painterResource(id = R.drawable.dropbox),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(14.dp, 9.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(bgColor)
        ) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    onOptionSelected(option)
                    expanded = false
                }) {
                    Text(
                        text = option,
                        color = if (isLight) TextGray20 else TextGray5,
                        fontFamily = Pretendard,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LabelText(text: String) {
    val isLight = MaterialTheme.colors.isLight
    val color = if (isLight) TextGray60 else TextGray
    Text(
        text = text,
        fontFamily = Pretendard,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun CCTVEventSelectButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isLight = MaterialTheme.colors.isLight
    val notBgColor = if (isLight) TextGray5 else TextGray20
    val notTextColor = if (isLight) TextGray else TextGray60
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(
                color = if (isSelected) MainOrange else notBgColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = Pretendard,
            fontSize = 16.sp,
            color = if (isSelected) Color.White else notTextColor,
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CCTVManagementScreenPreview() {
    CCTVManagementScreen()
}
